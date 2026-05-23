package com.example.monitoringapp.domain.model

data class Incident(
    val id: Long,
    val title: String,
    val host: String,
    val status: IncidentStatus,
    val severity: IncidentSeverity,
    val metricName: String?,
    val metricValue: String?,
    val createdAt: String?,
    val rule: String?,
    val threshold: Float? = null,
    val chartPoints: List<MetricPoint> = emptyList()
)

enum class IncidentStatus {
    NEW, ACCEPTED, CONFIRMED, CLOSED, UNKNOWN;

    companion object {
        fun fromRaw(value: String?): IncidentStatus = when (value?.uppercase()) {
            "NEW", "OPEN", "ACTIVE" -> NEW
            "ACCEPTED", "ACKNOWLEDGED" -> ACCEPTED
            "CONFIRMED", "ACK" -> CONFIRMED
            "CLOSED", "RESOLVED" -> CLOSED
            else -> UNKNOWN
        }
    }
}

enum class IncidentSeverity {
    CRITICAL, WARNING, INFO, UNKNOWN;

    companion object {
        fun fromRaw(value: String?): IncidentSeverity = when (value?.uppercase()) {
            "CRITICAL", "CRIT" -> CRITICAL
            "WARNING", "WARN" -> WARNING
            "INFO", "LOW" -> INFO
            else -> UNKNOWN
        }
    }
}
