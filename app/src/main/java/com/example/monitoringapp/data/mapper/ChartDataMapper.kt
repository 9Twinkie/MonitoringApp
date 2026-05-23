package com.example.monitoringapp.data.mapper

import com.example.monitoringapp.data.model.IncidentChartResponseDto
import com.example.monitoringapp.data.model.MetricPointDto
import com.example.monitoringapp.data.model.PrometheusQueryRangeDto
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint

object ChartDataMapper {

    fun fromPointDtos(dtos: List<MetricPointDto>?): List<MetricPoint> =
        dtos?.mapNotNull { it.toDomain() }.orEmpty()

    fun fromChartResponse(dto: IncidentChartResponseDto): List<MetricPoint> {
        val direct = fromPointDtos(dto.points)
            .ifEmpty { fromPointDtos(dto.chartData) }
            .ifEmpty { fromPointDtos(dto.data) }
            .ifEmpty { fromPointDtos(dto.history) }
        if (direct.isNotEmpty()) return direct
        return dto.values.mapNotNull { pair -> pair.toMetricPoint() }
    }

    fun chartDataFromResponse(dto: IncidentChartResponseDto): IncidentChartData =
        IncidentChartData(
            points = fromChartResponse(dto),
            threshold = dto.threshold?.toFloat(),
            promql = dto.promql ?: dto.metricName
        )

    fun fromPrometheusRange(dto: PrometheusQueryRangeDto): List<MetricPoint> {
        val direct = fromPointDtos(dto.points)
            .ifEmpty { fromPointDtos(dto.chartData) }
        if (direct.isNotEmpty()) return direct

        val matrix = dto.data?.result.orEmpty()
        if (matrix.isEmpty()) return emptyList()

        return matrix.flatMap { series ->
            series.values.mapNotNull { pair -> pair.toMetricPoint() }
        }.sortedBy { it.timestamp }
    }

    fun buildQuery(metricName: String?): String? {
        if (metricName.isNullOrBlank()) return null
        val trimmed = metricName.trim()
        if (trimmed.contains("{") || trimmed.contains("(")) return trimmed
        return trimmed
    }

    fun buildQueryFromLabels(labels: String?): String? {
        if (labels.isNullOrBlank()) return null
        val job = Regex("""job="?([^",\s]+)"?""").find(labels)?.groupValues?.getOrNull(1)
        val instance = Regex("""instance="?([^",\s]+)"?""").find(labels)?.groupValues?.getOrNull(1)
        return when {
            job != null && instance != null -> "up{job=\"$job\",instance=\"$instance\"}"
            job != null -> "up{job=\"$job\"}"
            labels.contains("up") -> "up"
            else -> null
        }
    }

    private fun List<String>.toMetricPoint(): MetricPoint? {
        if (size < 2) return null
        val tsRaw = this[0].toDoubleOrNull() ?: return null
        val tsMs = if (tsRaw < 10_000_000_000L) (tsRaw * 1000).toLong() else tsRaw.toLong()
        val value = this[1].toFloatOrNull() ?: return null
        return MetricPoint(timestamp = tsMs, value = value)
    }
}
