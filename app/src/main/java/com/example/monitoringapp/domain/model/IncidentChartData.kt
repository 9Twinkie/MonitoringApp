package com.example.monitoringapp.domain.model

data class IncidentChartData(
    val points: List<MetricPoint> = emptyList(),
    val threshold: Float? = null,
    val promql: String? = null
)
