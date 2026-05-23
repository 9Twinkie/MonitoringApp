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
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.domain.model.MetricSeries
import com.example.monitoringapp.domain.model.PrometheusTarget
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun IncidentDto.toDomain(): Incident = Incident(
    id = id,
    title = title ?: metricName ?: host ?: "Инцидент #$id",
    host = host ?: extractJobFromMetric(metricName) ?: "—",
    status = IncidentStatus.fromRaw(status),
    severity = IncidentSeverity.fromRaw(severity),
    metricName = metricName,
    metricValue = metricValue,
    createdAt = createdAt ?: timestamp,
    rule = rule,
    threshold = threshold?.toFloat(),
    chartPoints = chartPointsFromIncidentDto(this)
)

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
        health?.equals("UP", ignoreCase = true) == true
    return PrometheusTarget(name = displayName, host = host, isUp = isUp)
}

fun PrometheusTargetSampleDto.toDomain(): PrometheusTarget {
    val displayName = job?.takeIf { it.isNotBlank() }
        ?: scrapePool?.takeIf { it.isNotBlank() }
        ?: "target"
    val host = scrapeUrl?.takeIf { it.isNotBlank() } ?: displayName
    val isUp = health?.equals("up", ignoreCase = true) == true ||
        health?.equals("healthy", ignoreCase = true) == true
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
    val mappedTargets = when {
        fromSamples.isNotEmpty() -> fromSamples
        legacyTargets.isNotEmpty() -> legacyTargets
        else -> emptyList()
    }

    val up = block?.up ?: upCount ?: healthyTargets
        ?: mappedTargets.count { it.isUp }.takeIf { mappedTargets.isNotEmpty() }
    val down = block?.down ?: downCount
    val total = block?.total ?: totalTargets
        ?: if (up != null && down != null) up + down else null
        ?: mappedTargets.size.takeIf { it > 0 }

    val metricSeries = when {
        series.isNotEmpty() -> series.map { it.toDomain() }
        upMetrics.isNotEmpty() -> upMetrics.mapNotNull { it.toSeries() }
        else -> emptyList()
    }

    return MetricOverview(
        series = metricSeries,
        targets = mappedTargets,
        totalTargets = total,
        upTargets = up,
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
    host = host,
    status = status.name,
    severity = severity.name,
    metricName = metricName,
    metricValue = metricValue,
    createdAt = createdAt,
    rule = rule,
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

    return Incident(
        id = id,
        title = title,
        host = host,
        status = IncidentStatus.fromRaw(status),
        severity = IncidentSeverity.fromRaw(severity),
        metricName = metricName,
        metricValue = metricValue,
        createdAt = createdAt,
        rule = rule,
        chartPoints = points
    )
}
