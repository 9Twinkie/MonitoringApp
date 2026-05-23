package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.dao.PendingActionDao
import com.example.monitoringapp.data.local.entity.PendingActionEntity
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.data.mapper.ChartRangeMapper
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.data.mapper.toDomain
import com.example.monitoringapp.data.mapper.toEntity
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.data.remote.NetworkMonitor
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val incidentDao: IncidentDao,
    private val pendingActionDao: PendingActionDao,
    private val networkMonitor: NetworkMonitor,
    private val json: Json
) : IncidentRepository {

    override val isOfflineFlow: Flow<Boolean> =
        networkMonitor.isOnline.map { online -> !online }

    override val incidentsFlow: Flow<List<Incident>> =
        incidentDao.observeAll().map { entities ->
            entities.map { it.toDomain(json) }
        }

    override suspend fun refreshIncidents(): Result<List<Incident>> {
        if (!networkMonitor.checkOnline()) {
            val cached = incidentDao.observeAll().first().map { it.toDomain(json) }
            return Result.success(cached)
        }
        return try {
            val remote = api.getIncidents().map { it.toDomain() }
            incidentDao.clear()
            incidentDao.insertAll(remote.map { it.toEntity(json) })
            Result.success(remote)
        } catch (e: Exception) {
            val cached = incidentDao.observeAll().first().map { it.toDomain(json) }
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    override suspend fun getIncident(id: Long): Incident? {
        return incidentDao.getById(id)?.toDomain(json)
    }

    override suspend fun loadChartPoints(incident: Incident): Result<List<MetricPoint>> =
        loadIncidentChart(incident).map { it.points }

    override suspend fun loadIncidentChart(incident: Incident, rangeMinutes: Int): Result<IncidentChartData> {
        if (incident.chartPoints.isNotEmpty()) {
            return Result.success(
                IncidentChartData(
                    points = ChartRangeMapper.filterByRange(incident.chartPoints, rangeMinutes),
                    threshold = incident.threshold,
                    promql = incident.metricName
                )
            )
        }
        if (!networkMonitor.checkOnline()) {
            return Result.success(IncidentChartData(emptyList(), incident.threshold, incident.metricName))
        }
        return runCatching {
            fetchIncidentChart(incident, rangeMinutes)
        }
    }

    private suspend fun fetchIncidentChart(incident: Incident, rangeMinutes: Int): IncidentChartData {
        val range = ChartTimeRange.entries.firstOrNull { it.minutes == rangeMinutes }
            ?: ChartTimeRange.HOUR

        runCatching {
            api.getIncidentChart(
                incident.id,
                hours = range.apiHours,
                minutes = range.apiMinutes,
                step = range.step
            )
        }
            .getOrNull()
            ?.let { ChartDataMapper.chartDataFromResponse(it) }
            ?.takeIf { it.points.isNotEmpty() }
            ?.let {
                return it.copy(points = ChartRangeMapper.filterByRange(it.points, rangeMinutes))
            }

        runCatching { api.getIncident(incident.id) }
            .getOrNull()
            ?.toDomain()
            ?.let { detail ->
                if (detail.chartPoints.isNotEmpty()) {
                    return IncidentChartData(
                        points = ChartRangeMapper.filterByRange(detail.chartPoints, rangeMinutes),
                        threshold = detail.threshold ?: incident.threshold,
                        promql = detail.metricName
                    )
                }
            }

        val query = ChartDataMapper.buildQuery(incident.metricName)
            ?: return IncidentChartData(emptyList(), incident.threshold, incident.metricName)
        val points = fetchRange(query, rangeMinutes)
        return IncidentChartData(
            points = points,
            threshold = incident.threshold,
            promql = query
        )
    }

    private suspend fun fetchRange(query: String, rangeMinutes: Int): List<MetricPoint> {
        val range = ChartTimeRange.entries.firstOrNull { it.minutes == rangeMinutes }
            ?: ChartTimeRange.HOUR
        requestRange(query, range)?.let { return it }
        if (rangeMinutes < 60) {
            requestRange(query, ChartTimeRange.HOUR)?.let {
                return ChartRangeMapper.filterByRange(it, rangeMinutes)
            }
        }
        return emptyList()
    }

    private suspend fun requestRange(query: String, range: ChartTimeRange): List<MetricPoint>? {
        val filtered: (List<MetricPoint>) -> List<MetricPoint> = { points ->
            ChartRangeMapper.filterByRange(points, range.minutes)
        }
        parseRangeResponse(
            runCatching {
                api.getPrometheusRange(
                    query,
                    hours = range.apiHours,
                    minutes = range.apiMinutes,
                    step = range.step
                )
            }.getOrNull()
        )?.let { return filtered(it) }
        parseRangeResponse(
            runCatching {
                api.getPrometheusQueryRange(
                    query,
                    hours = range.apiHours,
                    minutes = range.apiMinutes,
                    step = range.step
                )
            }.getOrNull()
        )?.let { return filtered(it) }
        parseRangeResponse(
            runCatching {
                api.getMetricsRange(
                    query,
                    hours = range.apiHours,
                    minutes = range.apiMinutes,
                    step = range.step
                )
            }.getOrNull()
        )?.let { return filtered(it) }
        return null
    }

    private fun parseRangeResponse(dto: com.example.monitoringapp.data.model.PrometheusQueryRangeDto?): List<MetricPoint>? =
        dto?.let { ChartDataMapper.fromPrometheusRange(it) }?.takeIf { it.isNotEmpty() }

    override suspend fun confirmIncident(id: Long): Result<Unit> =
        performAction(id, ACTION_CONFIRM) { api.confirmIncident(id) }

    override suspend fun closeIncident(id: Long): Result<Unit> =
        performAction(id, ACTION_CLOSE) { api.closeIncident(id) }

    override suspend fun acceptIncident(id: Long): Result<Unit> =
        performAction(id, ACTION_ACCEPT) { api.confirmIncident(id) }

    override suspend fun syncPendingActions(): Result<Unit> = runCatching {
        if (!networkMonitor.checkOnline()) return@runCatching
        val pending = pendingActionDao.getAll()
        for (action in pending) {
            when (action.action) {
                ACTION_CONFIRM, ACTION_ACCEPT -> api.confirmIncident(action.incidentId)
                ACTION_CLOSE -> api.closeIncident(action.incidentId)
            }
            pendingActionDao.delete(action.id)
        }
        refreshIncidents()
    }

    private suspend fun performAction(
        incidentId: Long,
        action: String,
        block: suspend () -> Unit
    ): Result<Unit> {
        if (!networkMonitor.checkOnline()) {
            pendingActionDao.insert(
                PendingActionEntity(incidentId = incidentId, action = action)
            )
            return Result.success(Unit)
        }
        return try {
            block()
            refreshIncidents()
            Result.success(Unit)
        } catch (e: Exception) {
            pendingActionDao.insert(
                PendingActionEntity(incidentId = incidentId, action = action)
            )
            Result.success(Unit)
        }
    }

    companion object {
        const val ACTION_CONFIRM = "CONFIRM"
        const val ACTION_CLOSE = "CLOSE"
        const val ACTION_ACCEPT = "ACCEPT"
    }
}
