package com.example.monitoringapp.domain.model

data class Incident(
    val id: Long,
    val title: String,
    /** Текст из annotations Prometheus (summary/description). */
    val description: String? = null,
    val host: String,
    val status: IncidentStatus,
    val severity: IncidentSeverity,
    val metricName: String?,
    /** PromQL expr правила (для UI и графиков). */
    val metricExpr: String? = null,
    val metricValue: String?,
    /** Время срабатывания алерта (Prometheus startsAt / firedAt). */
    val firedAt: String? = null,
    val createdAt: String?,
    val rule: String? = null,
    val threshold: Float? = null,
    val chartPoints: List<MetricPoint> = emptyList(),
    val assignedEngineerUsername: String? = null,
    val canAccept: Boolean = false,
    val canConfirm: Boolean = false,
    val canClose: Boolean = false,
    val closeComment: String? = null,
    val closedByUsername: String? = null,
    /** Ключ задачи в Yandex Tracker, напр. MONITORING-19. */
    val trackerIssueKey: String? = null,
    /**
     * Состояние алерта в Prometheus: true — горит, false — погас (инцидент может быть в работе),
     * null — не Prometheus-инцидент.
     */
    val prometheusAlertActive: Boolean? = null,
    /** Адрес объекта с бэкенда (monitoring.site.address). */
    val siteAddress: String? = null
) {
    fun hasAssignee(): Boolean = !assignedEngineerUsername.isNullOrBlank()

    fun isPrometheusIncident(): Boolean = prometheusAlertActive != null

    /** Алерт погас на Prometheus, инцидент ещё в работе у инженера. */
    fun isPrometheusAlertClearedInWork(): Boolean =
        prometheusAlertActive == false && matchesInProgressTab()

    /** Ключ Tracker только после взятия в работу. */
    fun visibleTrackerIssueKey(): String? {
        if (status == IncidentStatus.NEW || status == IncidentStatus.UNKNOWN) return null
        return trackerIssueKey?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Вкладка «Активные»: новые, без исполнителя. */
    fun matchesActiveTab(): Boolean {
        if (status == IncidentStatus.CLOSED) return false
        if (matchesInProgressTab()) return false
        return status == IncidentStatus.NEW || status == IncidentStatus.UNKNOWN
    }

    /** Вкладка «В работе»: статус in-progress или уже назначен исполнитель. */
    fun matchesInProgressTab(): Boolean {
        if (status == IncidentStatus.CLOSED) return false
        if (status.isAwaitingClose()) return true
        if (status.isInProgress()) return true
        if (hasAssignee()) return true
        return false
    }
}

enum class IncidentStatus {
    NEW, ACCEPTED, IN_PROGRESS, CONFIRMED, CLOSED, UNKNOWN;

    fun isInProgress(): Boolean = when (this) {
        ACCEPTED, IN_PROGRESS -> true
        else -> false
    }

    fun isAwaitingClose(): Boolean = this == CONFIRMED

    fun isOpen(): Boolean = this == NEW || this == UNKNOWN

    companion object {
        fun fromRaw(value: String?): IncidentStatus {
            val normalized = value
                ?.trim()
                ?.uppercase()
                ?.removePrefix("ROLE_")
                ?.replace('-', '_')
                ?.replace(' ', '_')
                ?: return UNKNOWN
            return when (normalized) {
                "NEW", "OPEN", "ACTIVE" -> NEW
                "ACCEPTED", "ACKNOWLEDGED", "ACKED" -> ACCEPTED
                "IN_PROGRESS", "IN_WORK", "INWORK", "WORKING", "TAKEN", "ASSIGNED",
                "PROCESSING", "AT_WORK", "IN_PROGRESSING" -> IN_PROGRESS
                "CONFIRMED", "RESOLVED_PENDING", "ACK" -> CONFIRMED
                "COMPLETED", "COMPLETE", "DONE_WORK" -> IN_PROGRESS
                "CLOSED", "RESOLVED", "DONE" -> CLOSED
                else -> UNKNOWN
            }
        }
    }
}

enum class IncidentSeverity {
    CRITICAL, WARNING, INFO, UNKNOWN;

    companion object {
        /**
         * API бэкенда: CRITICAL / MEDIUM (warning) / LOW (info).
         * Prometheus-лейблы: critical, warning, info, informational и т.д.
         * HIGH — legacy (ошибочно для informational) → INFO, не WARNING.
         */
        fun fromRaw(value: String?): IncidentSeverity {
            val normalized = normalize(value) ?: return UNKNOWN
            return when (normalized) {
                "CRITICAL", "CRIT", "FATAL", "EMERGENCY" -> CRITICAL
                "WARNING", "WARN", "MEDIUM", "MODERATE" -> WARNING
                "INFO", "LOW", "INFORMATIONAL", "INFORMATION", "NOTICE", "MINOR" -> INFO
                // Старые записи: HIGH ошибочно ставился вместо LOW/informational
                "HIGH" -> INFO
                else -> inferFromUnknown(normalized)
            }
        }

        private fun normalize(value: String?): String? {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return raw.uppercase()
                .replace('-', '_')
                .replace(' ', '_')
        }

        private fun inferFromUnknown(normalized: String): IncidentSeverity = when {
            normalized.contains("CRIT") || normalized.contains("FATAL") -> CRITICAL
            normalized.contains("WARN") || normalized.contains("MEDIUM") -> WARNING
            normalized.contains("INFO") ||
                normalized.contains("INFORMATION") ||
                normalized.contains("NOTICE") ||
                normalized.contains("MINOR") ||
                normalized.contains("LOW") -> INFO
            else -> UNKNOWN
        }
    }
}
