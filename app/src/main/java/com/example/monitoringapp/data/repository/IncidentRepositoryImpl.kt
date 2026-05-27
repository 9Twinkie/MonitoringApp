package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.entity.IncidentEntity
import com.example.monitoringapp.data.local.dao.PendingActionDao
import com.example.monitoringapp.data.local.entity.PendingActionEntity
import com.example.monitoringapp.data.model.CloseIncidentRequest
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.data.mapper.ChartRangeMapper
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.data.mapper.toDomain
import com.example.monitoringapp.data.mapper.toEntity
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.data.remote.NetworkMonitor
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.IncidentWorkflow
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.ApiErrorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val incidentDao: IncidentDao,
    private val pendingActionDao: PendingActionDao,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
    private val json: Json
) : IncidentRepository {

    override val isOfflineFlow: Flow<Boolean> =
        networkMonitor.isOnline.map { online -> !online }

    override val incidentsFlow: Flow<List<Incident>> =
        incidentDao.observeAll().map { entities ->
            entities.map { IncidentWorkflow.normalize(it.toDomain(json)) }
        }

    override suspend fun refreshIncidents(): Result<List<Incident>> {
        if (!networkMonitor.checkOnline()) {
            val cached = incidentDao.observeAll().first().map { it.toDomain(json) }
            return Result.success(cached)
        }
        return try {
            val previous = incidentDao.observeAll().first().associateBy { it.id }
            val remote = api.getIncidents().map { dto ->
                val remoteIncident = IncidentWorkflow.normalize(dto.toDomain())
                val localEntity = previous[dto.id]
                if (localEntity == null) {
                    remoteIncident
                } else {
                    IncidentWorkflow.mergePreferAdvanced(
                        localEntity.toDomain(json),
                        remoteIncident
                    )
                }
            }
            val entities = remote.map { it.toEntity(json) }
            if (!incidentsDashboardDataChanged(previous.values.toList(), remote)) {
                return Result.success(remote)
            }
            incidentDao.replaceAll(entities)
            Result.success(remote)
        } catch (e: Exception) {
            val cached = incidentDao.observeAll().first().map { it.toDomain(json) }
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    override suspend fun getIncident(id: Long): Incident? {
        return incidentDao.getById(id)?.toDomain(json)?.let { IncidentWorkflow.normalize(it) }
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
        val range = ChartTimeRange.fromMinutes(rangeMinutes)

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
        val range = ChartTimeRange.fromMinutes(rangeMinutes)
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

    override suspend fun closeIncident(id: Long, comment: String?): Result<Unit> =
        performAction(id, ACTION_CLOSE, comment) {
            val text = comment?.trim()?.takeIf { it.isNotBlank() }
            api.closeIncident(
                id,
                CloseIncidentRequest(comment = text, resolutionComment = text)
            )
        }

    override suspend fun acceptIncident(id: Long): Result<Unit> =
        performAction(id, ACTION_ACCEPT) { api.acceptIncident(id) }

    override suspend fun syncPendingActions(): Result<Unit> = runCatching {
        if (!networkMonitor.checkOnline()) return@runCatching
        val pending = pendingActionDao.getAll()
        for (action in pending) {
            when (action.action) {
                ACTION_CONFIRM -> api.confirmIncident(action.incidentId)
                ACTION_ACCEPT -> api.acceptIncident(action.incidentId)
                ACTION_CLOSE -> api.closeIncident(action.incidentId)
            }
            pendingActionDao.delete(action.id)
        }
        refreshIncidents()
    }

    private suspend fun performAction(
        incidentId: Long,
        action: String,
        comment: String? = null,
        block: suspend () -> Unit
    ): Result<Unit> {
        if (!networkMonitor.checkOnline()) {
            pendingActionDao.insert(
                PendingActionEntity(incidentId = incidentId, action = action)
            )
            patchLocalAfterAction(incidentId, action, comment)
            return Result.success(Unit)
        }

        val apiError = runCatching { block() }.exceptionOrNull()
        patchLocalAfterAction(incidentId, action, comment)
        runCatching { refreshIncidents() }
        patchLocalAfterAction(incidentId, action, comment)

        if (apiError == null) return Result.success(Unit)

        if (apiError is HttpException && apiError.code() in AUTH_RELATED_CODES) {
            val patched = incidentDao.getById(incidentId)?.toDomain(json)
            if (patched != null && mutationLooksApplied(patched, action)) {
                return Result.success(Unit)
            }
            val remote = runCatching {
                IncidentWorkflow.normalize(api.getIncident(incidentId).toDomain())
            }.getOrNull()
            if (remote != null && mutationLooksApplied(remote, action)) {
                incidentDao.insertAll(listOf(remote.toEntity(json)))
                return Result.success(Unit)
            }
            if (incidentDao.getById(incidentId) != null) {
                return Result.success(Unit)
            }
        }

        return Result.failure(IllegalStateException(ApiErrorMapper.toMessage(apiError), apiError))
    }

    private fun mutationLooksApplied(incident: Incident, action: String): Boolean = when (action) {
        ACTION_ACCEPT -> incident.status.isInProgress() || incident.hasAssignee()
        ACTION_CONFIRM -> incident.status == IncidentStatus.CONFIRMED || incident.canClose
        ACTION_CLOSE -> incident.status == IncidentStatus.CLOSED
        else -> false
    }

    private suspend fun patchLocalAfterAction(
        incidentId: Long,
        action: String,
        comment: String? = null
    ) {
        val entity = incidentDao.getById(incidentId) ?: return
        val username = authRepository.getUsername()
        val patched = when (action) {
            ACTION_ACCEPT -> entity.copy(
                status = IncidentStatus.IN_PROGRESS.name,
                assignedEngineerUsername = username ?: entity.assignedEngineerUsername,
                canAccept = false,
                canConfirm = true,
                canClose = false
            )
            ACTION_CONFIRM -> entity.copy(
                status = IncidentStatus.CONFIRMED.name,
                canAccept = false,
                canConfirm = false,
                canClose = true
            )
            ACTION_CLOSE -> entity.copy(
                status = IncidentStatus.CLOSED.name,
                canAccept = false,
                canConfirm = false,
                canClose = false,
                closeComment = comment?.trim()?.takeIf { it.isNotBlank() } ?: entity.closeComment,
                closedByUsername = username ?: entity.closedByUsername
            )
            else -> return
        }
        incidentDao.insertAll(listOf(patched))
    }

    private fun incidentsDashboardDataChanged(
        previous: List<IncidentEntity>,
        remote: List<Incident>
    ): Boolean {
        if (previous.size != remote.size) return true
        val prevById = previous.associateBy { it.id }
        return remote.any { incident ->
            val entity = prevById[incident.id] ?: return true
            entity.status != incident.status.name ||
                entity.severity != incident.severity.name ||
                entity.host != incident.host ||
                entity.title != incident.title ||
                entity.description != incident.description ||
                entity.metricName != incident.metricName
        }
    }

    companion object {
        const val ACTION_CONFIRM = "CONFIRM"
        const val ACTION_CLOSE = "CLOSE"
        const val ACTION_ACCEPT = "ACCEPT"
        private val AUTH_RELATED_CODES = setOf(401, 403)
    }
}
