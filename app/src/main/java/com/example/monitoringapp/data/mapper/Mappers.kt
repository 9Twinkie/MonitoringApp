package com.example.monitoringapp.data.mapper

import com.example.monitoringapp.data.local.entity.IncidentEntity
import com.example.monitoringapp.data.model.IncidentDto
import com.example.monitoringapp.data.model.MetricPointDto
import com.example.monitoringapp.data.model.MetricSeriesDto
import com.example.monitoringapp.data.model.PrometheusOverviewDto
import com.example.monitoringapp.data.model.PrometheusTargetDto
import com.example.monitoringapp.data.model.PrometheusTargetSampleDto
import com.example.monitoringapp.data.model.UpMetricDto
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.model.IncidentWorkflow
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.domain.model.MetricSeries
import com.example.monitoringapp.domain.model.PrometheusTarget
import com.example.monitoringapp.utils.ExporterObjectFilter
import com.example.monitoringapp.utils.IncidentDisplayHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun IncidentDto.toDomain(): Incident = IncidentWorkflow.normalize(
    Incident(
        id = id,
        title = resolveAlertTitle(),
        description = resolveDescription(),
        host = resolveHost(),
        status = IncidentStatus.fromRaw(status ?: incidentStatus ?: state),
        severity = resolveSeverity(),
        metricName = resolvePromql(),
        metricExpr = resolveExpr(),
        metricValue = resolveMetricValue(),
        firedAt = resolveFiredAt(),
        createdAt = createdAt ?: timestamp,
        rule = rule?.trim()?.takeIf { it.isNotEmpty() && !IncidentDisplayHelper.looksLikeExpr(it) },
        threshold = threshold?.toFloat(),
        chartPoints = chartPointsFromIncidentDto(this),
        assignedEngineerUsername = resolveAssignee(),
        canAccept = canAccept == true || canTake == true,
        canConfirm = canConfirm == true || canComplete == true,
        canClose = canClose == true,
        closeComment = resolveCloseComment(),
        closedByUsername = closedByUsername ?: closedByUsernameSnake ?: closedBy,
        trackerIssueKey = trackerIssueKey?.trim()?.takeIf { it.isNotBlank() },
        prometheusAlertActive = prometheusAlertActive,
        siteAddress = resolveSiteAddress()
    )
)

private fun IncidentDto.resolveSiteAddress(): String? =
    sequenceOf(siteAddress, siteAddressSnake)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() }

private fun IncidentDto.resolveAlertTitle(): String =
    sequenceOf(alertName, alertNameSnake, metricName, name, title)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() && !IncidentDisplayHelper.looksLikeExpr(it) }
        ?.let { IncidentDisplayHelper.prettifyAlertName(it) }
        ?: IncidentDisplayHelper.friendlyTitleFromMetric(metricName)
        ?: extractJobFromMetric(resolvePromql())?.let { job -> "Алерт: $job" }
        ?: host?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Инцидент #$id"

private fun IncidentDto.resolveDescription(): String? =
    sequenceOf(
        description,
        summary,
        prometheusDescription,
        prometheusDescriptionSnake,
        prometheusSummary,
        prometheusSummarySnake
    )
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() }

private fun IncidentDto.resolveExpr(): String? =
    sequenceOf(promql, expr, metricExpr, rule)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() && IncidentDisplayHelper.looksLikeExpr(it) }
        ?: metricName?.trim()?.takeIf { IncidentDisplayHelper.looksLikeExpr(it) }

private fun IncidentDto.resolvePromql(): String? =
    resolveExpr()
        ?: promql?.trim()?.takeIf { it.isNotEmpty() }
        ?: metricName?.trim()?.takeIf { it.isNotEmpty() }

private fun IncidentDto.resolveMetricValue(): String? =
    sequenceOf(metricValue, value, currentValue)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() && !IncidentDisplayHelper.looksLikeExpr(it) }

/** Время срабатывания алерта в Prometheus (не время создания записи в БД). */
private fun IncidentDto.resolveFiredAt(): String? =
    sequenceOf(firedAt, startsAt, startAt, activeAt, timestamp)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() }

private fun IncidentDto.resolveHost(): String =
    host?.trim()?.takeIf { it.isNotEmpty() }
        ?: extractJobFromMetric(resolvePromql())
        ?: extractJobFromMetric(metricName?.takeIf { IncidentDisplayHelper.looksLikeExpr(it) })
        ?: "—"

private fun IncidentDto.resolveSeverity(): IncidentSeverity =
    IncidentSeverity.fromRaw(
        severity ?: prometheusSeverity ?: prometheusSeveritySnake
    )

private fun IncidentDto.resolveCloseComment(): String? =
    sequenceOf(closeComment, closeCommentSnake, resolutionComment, resolutionCommentSnake, comment)
        .map { it?.trim() }
        .firstOrNull { !it.isNullOrBlank() }

private fun IncidentDto.resolveAssignee(): String? =
    sequenceOf(
        assignedEngineerUsername,
        assignedEngineer,
        assigneeUsername,
        assignedTo,
        engineerUsername
    ).firstOrNull { !it.isNullOrBlank() }

fun chartPointsFromIncidentDto(dto: IncidentDto): List<MetricPoint> =
    ChartDataMapper.fromPointDtos(dto.chartData)
        .ifEmpty { ChartDataMapper.fromPointDtos(dto.history) }
        .ifEmpty { ChartDataMapper.fromPointDtos(dto.metricHistory) }
        .ifEmpty { ChartDataMapper.fromPointDtos(dto.points) }

private fun extractJobFromMetric(metricName: String?): String? {
    if (metricName.isNullOrBlank()) return null
    val jobMatch = Regex("""job="([^"]+)"""").find(metricName)
    return jobMatch?.groupValues?.getOrNull(1)
}

fun MetricPointDto.toDomain(): MetricPoint? {
    val v = resolvedValue() ?: return null
    return MetricPoint(timestamp = resolvedTimestamp() ?: 0L, value = v)
}

fun PrometheusTargetDto.toDomain(): PrometheusTarget {
    val displayName = name?.takeIf { it.isNotBlank() }
        ?: job?.takeIf { it.isNotBlank() }
        ?: instance?.takeIf { it.isNotBlank() }
        ?: "target"
    val host = instance?.takeIf { it.isNotBlank() } ?: name ?: "—"
    val isUp = up == true ||
        status?.equals("UP", ignoreCase = true) == true ||
        status?.equals("ONLINE", ignoreCase = true) == true ||
        health?.equals("UP", ignoreCase = true) == true ||
        health?.equals("up", ignoreCase = true) == true ||
        health?.equals("healthy", ignoreCase = true) == true
    return PrometheusTarget(name = displayName, host = host, isUp = isUp)
}

fun PrometheusTargetSampleDto.toDomain(): PrometheusTarget {
    val displayName = job?.takeIf { it.isNotBlank() }
        ?: scrapePool?.takeIf { it.isNotBlank() }
        ?: "target"
    val host = scrapeUrl?.takeIf { it.isNotBlank() } ?: displayName
    val isUp = when {
        health?.equals("up", ignoreCase = true) == true -> true
        health?.equals("healthy", ignoreCase = true) == true -> true
        health?.equals("UP", ignoreCase = true) == true -> true
        health?.equals("down", ignoreCase = true) == true -> false
        !lastError.isNullOrBlank() -> false
        else -> false
    }
    return PrometheusTarget(name = displayName, host = host, isUp = isUp)
}

fun UpMetricDto.toSeries(): MetricSeries? {
    val label = labels?.takeIf { it.isNotBlank() } ?: return null
    val value = value?.toFloatOrNull() ?: return null
    val name = extractJobFromMetric(label) ?: label
    return MetricSeries(
        name = name,
        points = listOf(MetricPoint(timestamp = System.currentTimeMillis(), value = value)),
        threshold = 0.5f
    )
}

fun PrometheusOverviewDto.toDomain(): MetricOverview {
    val block = targets
    val fromSamples = block?.samples.orEmpty().map { it.toDomain() }
    val legacyTargets = (servers + hosts + items)
        .distinctBy { it.instance ?: it.name ?: it.job }
        .map { it.toDomain() }
    val mappedTargets = ExporterObjectFilter.filterTargets(
        when {
            fromSamples.isNotEmpty() -> fromSamples
            legacyTargets.isNotEmpty() -> legacyTargets
            else -> emptyList()
        }
    )

    val up = block?.up ?: upCount ?: healthyTargets
        ?: mappedTargets.count { it.isUp }.takeIf { mappedTargets.isNotEmpty() }
    val down = block?.down ?: downCount
    val total = mappedTargets.size.takeIf { it > 0 }
        ?: block?.total ?: totalTargets

    // series/upMetrics/sampleNames — для вкладки «Графики», не для дашборда
    val metricSeries = when {
        series.isNotEmpty() -> series.map { it.toDomain() }
        upMetrics.isNotEmpty() -> upMetrics.mapNotNull { it.toSeries() }
        else -> emptyList()
    }

    return MetricOverview(
        series = metricSeries,
        targets = mappedTargets,
        totalTargets = total,
        upTargets = mappedTargets.count { it.isUp }.takeIf { mappedTargets.isNotEmpty() } ?: up,
        downTargets = down,
        updatedAt = updatedAt ?: System.currentTimeMillis()
    )
}

fun MetricSeriesDto.toDomain(): MetricSeries = MetricSeries(
    name = name,
    points = points.mapNotNull { it.toDomain() },
    threshold = threshold
)

fun Incident.toEntity(json: Json): IncidentEntity = IncidentEntity(
    id = id,
    title = title,
    description = description,
    host = host,
    status = status.name,
    severity = severity.name,
    metricName = metricName,
    metricExpr = metricExpr,
    metricValue = metricValue,
    firedAt = firedAt,
    createdAt = createdAt,
    rule = rule,
    assignedEngineerUsername = assignedEngineerUsername,
    canAccept = canAccept,
    canConfirm = canConfirm,
    canClose = canClose,
    closeComment = closeComment,
    closedByUsername = closedByUsername,
    trackerIssueKey = trackerIssueKey,
    prometheusAlertActive = prometheusAlertActive,
    siteAddress = siteAddress,
    chartJson = if (chartPoints.isEmpty()) null else json.encodeToString(
        ListSerializer(MetricPointDto.serializer()),
        chartPoints.map { MetricPointDto(timestamp = it.timestamp, value = it.value) }
    ),
    cachedAt = System.currentTimeMillis()
)

fun IncidentEntity.toDomain(json: Json): Incident {
    val points = chartJson?.let { raw ->
        runCatching {
            json.decodeFromString(ListSerializer(MetricPointDto.serializer()), raw)
                .mapNotNull { it.toDomain() }
        }.getOrDefault(emptyList())
    } ?: emptyList()

    return IncidentWorkflow.normalize(
        Incident(
            id = id,
            title = title,
            description = description,
            host = host,
            status = IncidentStatus.fromRaw(status),
            severity = IncidentSeverity.fromRaw(severity),
            metricName = metricName,
            metricExpr = metricExpr,
            metricValue = metricValue,
            firedAt = firedAt,
            createdAt = createdAt,
            rule = rule,
            assignedEngineerUsername = assignedEngineerUsername,
            canAccept = canAccept,
            canConfirm = canConfirm,
            canClose = canClose,
            closeComment = closeComment,
            closedByUsername = closedByUsername,
            trackerIssueKey = trackerIssueKey,
            prometheusAlertActive = prometheusAlertActive,
            siteAddress = siteAddress,
            chartPoints = points
        )
    )
}
