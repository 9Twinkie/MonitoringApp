package com.example.monitoringapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("refreshToken") val refreshToken: String? = null,
    val token: String? = null,
    val username: String? = null
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken") val refreshToken: String
)

@Serializable
data class IncidentDto(
    val id: Long,
    val title: String? = null,
    val host: String? = null,
    val status: String? = null,
    val severity: String? = null,
    @SerialName("metricName") val metricName: String? = null,
    @SerialName("metricValue") val metricValue: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    val timestamp: String? = null,
    val rule: String? = null,
    val threshold: Double? = null,
    val operator: String? = null,
    @SerialName("chartData") val chartData: List<MetricPointDto>? = null,
    val history: List<MetricPointDto>? = null,
    @SerialName("metricHistory") val metricHistory: List<MetricPointDto>? = null,
    val points: List<MetricPointDto>? = null
)

@Serializable
data class MetricPointDto(
    val timestamp: Long? = null,
    val time: Long? = null,
    val t: Long? = null,
    val value: Float? = null,
    val v: Float? = null
) {
    fun resolvedTimestamp(): Long? {
        val raw = timestamp ?: time ?: t ?: return null
        return if (raw < 10_000_000_000L) raw * 1000 else raw
    }

    fun resolvedValue(): Float? = value ?: v
}

/** Ответ GET /incidents/{id}/chart */
@Serializable
data class IncidentChartResponseDto(
    @SerialName("incidentId") val incidentId: Long? = null,
    @SerialName("metricName") val metricName: String? = null,
    val promql: String? = null,
    val threshold: Double? = null,
    val hours: Int? = null,
    val step: String? = null,
    val points: List<MetricPointDto> = emptyList(),
    val chartData: List<MetricPointDto> = emptyList(),
    val data: List<MetricPointDto> = emptyList(),
    val history: List<MetricPointDto> = emptyList(),
    val values: List<List<String>> = emptyList()
)

@Serializable
data class PrometheusQueryRangeDto(
    val status: String? = null,
    val points: List<MetricPointDto> = emptyList(),
    val chartData: List<MetricPointDto> = emptyList(),
    val data: PrometheusMatrixDataDto? = null
)

@Serializable
data class PrometheusMatrixDataDto(
    val result: List<PrometheusMatrixSeriesDto> = emptyList()
)

@Serializable
data class PrometheusMatrixSeriesDto(
    val values: List<List<String>> = emptyList()
)

@Serializable
data class MetricSeriesDto(
    val name: String,
    val points: List<MetricPointDto> = emptyList(),
    val threshold: Float? = null
)

@Serializable
data class PrometheusTargetDto(
    val name: String? = null,
    val instance: String? = null,
    val job: String? = null,
    val up: Boolean? = null,
    val status: String? = null,
    val health: String? = null
)

@Serializable
data class PrometheusTargetSampleDto(
    val scrapePool: String? = null,
    val job: String? = null,
    val scrapeUrl: String? = null,
    val health: String? = null,
    val lastError: String? = null
)

@Serializable
data class PrometheusTargetsBlockDto(
    val total: Int? = null,
    val up: Int? = null,
    val down: Int? = null,
    val unknown: Int? = null,
    val samples: List<PrometheusTargetSampleDto> = emptyList()
)

@Serializable
data class UpMetricDto(
    val labels: String? = null,
    val value: String? = null
)

@Serializable
data class PrometheusMetricsBlockDto(
    val totalCount: Int? = null,
    val sampleNames: List<String> = emptyList()
)

/** Ответ GET /monitoring/prometheus/overview (Spring Boot). */
@Serializable
data class PrometheusOverviewDto(
    val prometheusUrl: String? = null,
    val reachable: Boolean? = null,
    val error: String? = null,
    val upMetrics: List<UpMetricDto> = emptyList(),
    val targets: PrometheusTargetsBlockDto? = null,
    val metrics: PrometheusMetricsBlockDto? = null,
    // Упрощённый/legacy формат (если бэкенд отдаёт плоский JSON)
    val series: List<MetricSeriesDto> = emptyList(),
    val servers: List<PrometheusTargetDto> = emptyList(),
    val hosts: List<PrometheusTargetDto> = emptyList(),
    val items: List<PrometheusTargetDto> = emptyList(),
    @SerialName("totalTargets") val totalTargets: Int? = null,
    @SerialName("healthyTargets") val healthyTargets: Int? = null,
    @SerialName("upCount") val upCount: Int? = null,
    @SerialName("downCount") val downCount: Int? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null
)

@Serializable
data class WsNotificationDto(
    val type: String? = null,
    val message: String? = null,
    @SerialName("incidentId") val incidentId: Long? = null,
    @SerialName("metricName") val metricName: String? = null,
    val severity: String? = null,
    val host: String? = null,
    val title: String? = null
)
