package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.dao.MetricCacheDao
import com.example.monitoringapp.data.local.entity.MetricCacheEntity
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.data.mapper.ChartRangeMapper
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.data.mapper.toDomain
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.data.remote.NetworkMonitor
import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.MetricSeries
import com.example.monitoringapp.domain.model.MonitoringObject
import com.example.monitoringapp.domain.repository.MetricsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val metricCacheDao: MetricCacheDao,
    private val incidentDao: IncidentDao,
    private val networkMonitor: NetworkMonitor,
    private val json: Json
) : MetricsRepository {

    override suspend fun getOverview(): Result<MetricOverview> {
        if (!networkMonitor.checkOnline()) {
            getCachedOverview()?.let { return Result.success(it) }
            return Result.failure(IllegalStateException("Нет сети"))
        }
        return runCatching {
            val dto = api.getMetricsOverview()
            val overview = dto.toDomain()
            metricCacheDao.save(
                MetricCacheEntity(
                    json = json.encodeToString(dto),
                    cachedAt = System.currentTimeMillis()
                )
            )
            overview
        }.recoverCatching {
            getCachedOverview()?.let { cached -> return Result.success(cached) }
            throw it
        }
    }

    override suspend fun getCachedOverview(): MetricOverview? {
        val entity = metricCacheDao.get() ?: return null
        return runCatching {
            json.decodeFromString<com.example.monitoringapp.data.model.PrometheusOverviewDto>(entity.json)
                .toDomain()
        }.getOrNull()
    }

    override suspend fun buildDashboardSummary(overview: MetricOverview?): DashboardSummary {
        val incidents = incidentDao.observeAll().first()
        val critical = incidents.count {
            it.severity.equals(IncidentSeverity.CRITICAL.name, ignoreCase = true) &&
                !it.status.equals(IncidentStatus.CLOSED.name, ignoreCase = true)
        }

        val prometheusOverview = overview ?: getCachedOverview()
        if (prometheusOverview != null && hasPrometheusData(prometheusOverview)) {
            return buildFromPrometheus(prometheusOverview, critical)
        }

        return buildFromIncidents(incidents, critical)
    }

    private fun hasPrometheusData(overview: MetricOverview): Boolean =
        overview.targets.isNotEmpty() ||
            overview.series.isNotEmpty() ||
            overview.totalTargets != null ||
            overview.upTargets != null

    private fun buildFromPrometheus(
        overview: MetricOverview,
        criticalCount: Int
    ): DashboardSummary {
        val objects = buildObjects(overview)
        val total = overview.totalTargets
            ?: objects.size.takeIf { it > 0 }
            ?: 0
        val online = overview.upTargets
            ?: objects.count { it.isHealthy }

        return DashboardSummary(
            criticalCount = criticalCount,
            serversOnline = online,
            serversTotal = total,
            objects = objects
        )
    }

    private fun buildObjects(overview: MetricOverview): List<MonitoringObject> {
        if (overview.targets.isNotEmpty()) {
            return overview.targets.map { target ->
                MonitoringObject(
                    name = target.name,
                    host = target.host,
                    status = if (target.isUp) "UP" else "DOWN",
                    metricSummary = if (target.isUp) "online" else "offline",
                    isHealthy = target.isUp
                )
            }
        }

        return overview.series.map { series -> seriesToObject(series) }
    }

    private fun seriesToObject(series: MetricSeries): MonitoringObject {
        val lastValue = series.points.lastOrNull()?.value
        val healthy = when {
            lastValue == null -> true
            series.threshold == null -> true
            else -> lastValue < series.threshold
        }
        return MonitoringObject(
            name = series.name,
            host = series.name,
            status = if (healthy) "OK" else "ALERT",
            metricSummary = lastValue?.let { "%.0f".format(it) } ?: "—",
            isHealthy = healthy
        )
    }

    private fun buildFromIncidents(
        incidents: List<com.example.monitoringapp.data.local.entity.IncidentEntity>,
        criticalCount: Int
    ): DashboardSummary {
        val objects = incidents.take(10).map {
            MonitoringObject(
                name = it.title,
                host = it.host,
                status = it.status,
                metricSummary = it.metricValue ?: "—",
                isHealthy = !it.severity.equals(IncidentSeverity.CRITICAL.name, ignoreCase = true)
            )
        }
        val healthy = objects.count { it.isHealthy }
        val total = objects.size
        return DashboardSummary(
            criticalCount = criticalCount,
            serversOnline = healthy,
            serversTotal = if (total > 0) total else 0,
            objects = objects
        )
    }

    override suspend fun loadMetricRange(query: String, rangeMinutes: Int): Result<List<MetricPoint>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        return runCatching {
            val range = ChartTimeRange.entries.firstOrNull { it.minutes == rangeMinutes }
                ?: ChartTimeRange.HOUR
            requestRange(query, range)?.let { return@runCatching it }
            if (rangeMinutes < 60) {
                requestRange(query, ChartTimeRange.HOUR)?.let {
                    return@runCatching ChartRangeMapper.filterByRange(it, rangeMinutes)
                }
            }
            emptyList()
        }
    }

    private suspend fun requestRange(query: String, range: ChartTimeRange): List<MetricPoint>? {
        val filtered: (List<MetricPoint>) -> List<MetricPoint> = { points ->
            ChartRangeMapper.filterByRange(points, range.minutes)
        }
        parseRange(
            runCatching {
                api.getPrometheusRange(
                    query,
                    hours = range.apiHours,
                    minutes = range.apiMinutes,
                    step = range.step
                )
            }.getOrNull()
        )?.let { return filtered(it) }
        parseRange(
            runCatching {
                api.getPrometheusQueryRange(
                    query,
                    hours = range.apiHours,
                    minutes = range.apiMinutes,
                    step = range.step
                )
            }.getOrNull()
        )?.let { return filtered(it) }
        parseRange(
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

    private fun parseRange(dto: com.example.monitoringapp.data.model.PrometheusQueryRangeDto?): List<MetricPoint>? =
        dto?.let { ChartDataMapper.fromPrometheusRange(it) }?.takeIf { it.isNotEmpty() }
}
