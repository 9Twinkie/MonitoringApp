package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.dao.MetricCacheDao
import com.example.monitoringapp.data.local.entity.MetricCacheEntity
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.data.mapper.ChartRangeMapper
import com.example.monitoringapp.data.mapper.MetricSearchJsonParser
import com.example.monitoringapp.data.mapper.PrometheusOverviewJsonParser
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.data.mapper.toDomain
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.data.remote.NetworkMonitor
import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.MonitoringObject
import com.example.monitoringapp.domain.repository.MetricsRepository
import com.example.monitoringapp.utils.ExporterObjectFilter
import com.example.monitoringapp.utils.PromqlSearchHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val metricCacheDao: MetricCacheDao,
    private val incidentDao: IncidentDao,
    private val networkMonitor: NetworkMonitor,
    private val tokenStorage: TokenStorage,
    private val json: Json
) : MetricsRepository {

    override fun getLastChartQuery(): String? = tokenStorage.getLastMetricsQuery()

    override fun setLastChartQuery(query: String?) {
        tokenStorage.setLastMetricsQuery(query)
    }

    override fun getLastChartRangeMinutes(): Int = tokenStorage.getLastMetricsRangeMinutes()

    override fun setLastChartRangeMinutes(minutes: Int) {
        tokenStorage.setLastMetricsRangeMinutes(minutes)
    }

    override suspend fun getOverview(): Result<MetricOverview> {
        if (!networkMonitor.checkOnline()) {
            getCachedOverview()?.let { return Result.success(it) }
            return Result.failure(IllegalStateException("Нет сети"))
        }
        return runCatching {
            val body = api.getMetricsOverviewRaw().string()
            if (body.isBlank()) error("Пустой ответ overview")
            metricCacheDao.save(
                MetricCacheEntity(
                    json = body,
                    cachedAt = System.currentTimeMillis()
                )
            )
            parseOverviewBody(body)
        }.recoverCatching {
            getCachedOverview()?.let { cached -> return Result.success(cached) }
            throw it
        }
    }

    private fun parseOverviewBody(body: String): MetricOverview {
        PrometheusOverviewJsonParser.parse(json, body)?.let { return it }
        return json.decodeFromString<com.example.monitoringapp.data.model.PrometheusOverviewDto>(body)
            .toDomain()
    }

    override suspend fun getCachedOverview(): MetricOverview? {
        val entity = metricCacheDao.get() ?: return null
        return runCatching { parseOverviewBody(entity.json) }.getOrNull()
    }

    override suspend fun buildDashboardSummary(overview: MetricOverview?): DashboardSummary {
        val incidents = incidentDao.observeAll().first()
        val critical = incidents.count {
            it.severity.equals(IncidentSeverity.CRITICAL.name, ignoreCase = true) &&
                !it.status.equals(IncidentStatus.CLOSED.name, ignoreCase = true)
        }

        val sources = linkedSetOf<MetricOverview>()
        overview?.let { sources.add(it) }
        getCachedOverview()?.let { sources.add(it) }

        for (source in sources) {
            val objects = buildObjects(source)
            if (objects.isNotEmpty()) {
                return summaryFromObjects(objects, critical)
            }
        }

        // Только Prometheus-объекты. Инциденты — на вкладке «Алерты», не здесь.
        return DashboardSummary(
            criticalCount = critical,
            serversOnline = 0,
            serversTotal = 0,
            objects = emptyList()
        )
    }

    private fun summaryFromObjects(
        objects: List<MonitoringObject>,
        criticalCount: Int
    ): DashboardSummary {
        val online = objects.count { it.isHealthy }
        return DashboardSummary(
            criticalCount = criticalCount,
            serversOnline = online,
            serversTotal = objects.size,
            objects = objects
        )
    }

    private fun buildObjects(overview: MetricOverview): List<MonitoringObject> {
        return ExporterObjectFilter.filterTargets(overview.targets).map { target ->
            MonitoringObject(
                name = target.name,
                host = target.host,
                status = if (target.isUp) "UP" else "DOWN",
                metricSummary = if (target.isUp) "online" else "offline",
                isHealthy = target.isUp
            )
        }
    }

    override suspend fun loadMetricRange(query: String, rangeMinutes: Int): Result<List<MetricPoint>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        return runCatching {
            val range = ChartTimeRange.fromMinutes(rangeMinutes)
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

    override suspend fun searchMetricNames(query: String, limit: Int): Result<List<String>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        val trimmed = query.trim()
        if (trimmed.length < 2) return Result.success(emptyList())
        return runCatching {
            val body = api.getMetricNamesRaw(match = trimmed, q = trimmed, limit = limit).string()
            MetricSearchJsonParser.parse(json, body)
        }
    }

    override suspend fun suggestPromql(query: String, limit: Int): Result<List<String>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return runCatching {
            val body = api.getMetricSuggestRaw(q = trimmed, query = trimmed, limit = limit).string()
            MetricSearchJsonParser.parse(json, body)
        }
    }

    override suspend fun suggestLabelValues(label: String, prefix: String, limit: Int): Result<List<String>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        return runCatching {
            val body = api.getLabelValuesRaw(
                label = label,
                match = prefix.ifBlank { null },
                q = prefix.ifBlank { null },
                limit = limit
            ).string()
            MetricSearchJsonParser.parse(json, body)
        }
    }

    override suspend fun searchMetricSuggestions(query: String, limit: Int): Result<List<String>> {
        if (!networkMonitor.checkOnline()) {
            return Result.failure(IllegalStateException("Нет сети"))
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        PromqlSearchHelper.detectLabelValueCompletion(trimmed)?.let { completion ->
            return suggestLabelValues(completion.label, completion.valuePrefix, limit).map { values ->
                values.map { value ->
                    PromqlSearchHelper.applyLabelValue(trimmed, completion.label, value)
                }
            }
        }

        return runCatching {
            coroutineScope {
                val namesDeferred = async {
                    searchMetricNames(trimmed, limit).getOrElse { emptyList() }
                }
                val suggestDeferred = async {
                    suggestPromql(trimmed, limit).getOrElse { emptyList() }
                }
                (suggestDeferred.await() + namesDeferred.await())
                    .distinct()
                    .take(limit)
            }
        }
    }
}
