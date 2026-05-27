package com.example.monitoringapp.domain.model

data class MetricPoint(
    val timestamp: Long,
    val value: Float
)

data class MetricSeries(
    val name: String,
    val points: List<MetricPoint>,
    val threshold: Float? = null
)

data class PrometheusTarget(
    val name: String,
    val host: String,
    val isUp: Boolean
)

data class MetricOverview(
    val series: List<MetricSeries>,
    val targets: List<PrometheusTarget> = emptyList(),
    val totalTargets: Int? = null,
    val upTargets: Int? = null,
    val downTargets: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class MonitoringObject(
    val name: String,
    val host: String,
    val status: String,
    val metricSummary: String,
    val isHealthy: Boolean,
    val targetKey: String = name,
    val worstSeverity: IncidentSeverity? = null,
    val openIncidents: Int = 0,
    val isFavorite: Boolean = false
)

data class DashboardSummary(
    val criticalCount: Int,
    val serversOnline: Int,
    val serversTotal: Int,
    val objects: List<MonitoringObject>
)
