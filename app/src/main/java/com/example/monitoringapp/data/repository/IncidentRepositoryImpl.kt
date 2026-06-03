package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.entity.IncidentEntity
import com.example.monitoringapp.data.local.dao.PendingActionDao
import com.example.monitoringapp.data.local.entity.PendingActionEntity
import com.example.monitoringapp.data.model.IncidentDto
import com.example.monitoringapp.data.mapper.ChartDataMapper
import com.example.monitoringapp.data.mapper.ChartRangeMapper
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.data.mapper.IncidentListJsonParser
import com.example.monitoringapp.data.mapper.IncidentMutationValidator
import com.example.monitoringapp.data.mapper.toDomain
import com.example.monitoringapp.data.mapper.toEntity
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.IncidentWorkflow
import com.example.monitoringapp.data.remote.NetworkMonitor
import com.example.monitoringapp.utils.ApiErrorMapper
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.IncidentDisplayHelper
import com.example.monitoringapp.utils.CloseIncidentPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val incidentDao: IncidentDao,
    private val pendingActionDao: PendingActionDao,
    private val networkMonitor: NetworkMonitor,
    private val tokenStorage: TokenStorage,
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
            val body = fetchIncidentsBody()
            val dtos = IncidentListJsonParser.parseList(json, body)
            val remote = dtos.map { dto ->
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
            if (e is HttpException && e.code() in AUTH_RELATED_CODES) {
                return Result.failure(IllegalStateException(ApiErrorMapper.toMessage(e), e))
            }
            val cached = incidentDao.observeAll().first().map { it.toDomain(json) }
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    override suspend fun clearLocalCache() {
        incidentDao.clear()
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

        val query = IncidentDisplayHelper.chartQuery(incident)
            ?: return IncidentChartData(emptyList(), incident.threshold, null)
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

    override suspend fun closeIncident(id: Long, comment: String?): Result<Unit> =
        performMutation(id, ACTION_CLOSE, comment) {
            val dto = api.closeIncidentWithComment(
                id,
                CloseIncidentPayload.request(comment)
            )
            IncidentMutationValidator.validateCloseResponse(dto)
            dto
        }

    override suspend fun acceptIncident(id: Long): Result<Unit> =
        performMutation(id, ACTION_TAKE) {
            val dto = api.takeIncident(id)
            IncidentMutationValidator.validateTakeResponse(dto)
            dto
        }

    override suspend fun syncPendingActions(): Result<Unit> = runCatching {
        if (!networkMonitor.checkOnline()) return@runCatching
        val pending = pendingActionDao.getAll()
        for (action in pending) {
            when (action.action) {
                ACTION_TAKE, ACTION_ACCEPT, ACTION_CONFIRM -> {
                    val dto = api.takeIncident(action.incidentId)
                    IncidentMutationValidator.validateTakeResponse(dto)
                    upsertFromDto(dto)
                }
                ACTION_CLOSE -> {
                    val dto = api.closeIncidentWithComment(
                        action.incidentId,
                        CloseIncidentPayload.request(action.comment)
                    )
                    IncidentMutationValidator.validateCloseResponse(dto)
                    persistMutationResult(dto, action.comment)
                }
            }
            pendingActionDao.delete(action.id)
        }
        refreshIncidents()
    }

    private suspend fun performMutation(
        incidentId: Long,
        action: String,
        comment: String? = null,
        block: suspend () -> IncidentDto
    ): Result<Unit> {
        if (!networkMonitor.checkOnline()) {
            pendingActionDao.insert(
                PendingActionEntity(
                    incidentId = incidentId,
                    action = action,
                    comment = comment?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            return Result.failure(
                IllegalStateException("Нет сети. Действие сохранено и будет отправлено позже.")
            )
        }

        return try {
            val dto = block()
            persistMutationResult(
                dto,
                closeComment = if (action == ACTION_CLOSE) comment else null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(ApiErrorMapper.toMessage(e), e))
        }
    }

    private suspend fun persistMutationResult(dto: IncidentDto, closeComment: String? = null) {
        upsertFromDto(dto, closeComment)
        runCatching { api.getIncident(dto.id) }
            .getOrNull()
            ?.let { upsertFromDto(it, closeComment) }
    }

    private suspend fun upsertFromDto(dto: IncidentDto, requestedCloseComment: String? = null) {
        var domain = dto.toDomain()
        if (domain.status == IncidentStatus.CLOSED) {
            domain = domain.copy(
                closeComment = domain.closeComment?.trim()?.takeIf { it.isNotBlank() }
                    ?: requestedCloseComment?.let { CloseIncidentPayload.storageComment(it) },
                closedByUsername = domain.closedByUsername ?: tokenStorage.getDisplayLogin()
            )
        }
        val entity = IncidentWorkflow.normalize(domain).toEntity(json)
        incidentDao.insertAll(listOf(entity))
    }

    private suspend fun fetchIncidentsBody(): String =
        runCatching { api.getIncidentsRaw(scope = "all").string() }
            .getOrElse { api.getAlertsRaw(scope = "all").string() }

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
                entity.metricName != incident.metricName ||
                entity.prometheusAlertActive != incident.prometheusAlertActive ||
                entity.siteAddress != incident.siteAddress ||
                entity.closeComment != incident.closeComment ||
                entity.closedByUsername != incident.closedByUsername
        }
    }

    companion object {
        const val ACTION_TAKE = "TAKE"
        const val ACTION_CLOSE = "CLOSE"
        /** Legacy значения в очереди офлайн-действий. */
        const val ACTION_ACCEPT = "ACCEPT"
        const val ACTION_CONFIRM = "CONFIRM"
        private val AUTH_RELATED_CODES = setOf(401, 403)
    }
}
