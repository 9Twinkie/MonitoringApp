package com.example.monitoringapp.service

import com.example.monitoringapp.data.model.WsNotificationDto
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.utils.NotificationHelper
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentAlertCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationHelper: NotificationHelper,
    private val json: Json
) {

    private val knownActiveIds = mutableSetOf<Long>()
    private val lastNotifiedAt = mutableMapOf<Long, Long>()
    private var baselineEstablished = false

    fun resetSession() {
        knownActiveIds.clear()
        lastNotifiedAt.clear()
        baselineEstablished = false
    }

    fun processAfterRefresh(
        incidents: List<Incident>,
        wsRaw: String? = null
    ) {
        if (!authRepository.receivesPushAlerts()) return

        val active = incidents.filter { it.status != IncidentStatus.CLOSED }
        val activeIds = active.map { it.id }.toSet()

        wsRaw?.let { raw ->
            parseWs(raw)?.let { dto ->
                if (isIncidentEvent(dto)) {
                    val incident = dto.incidentId?.let { id -> active.find { it.id == id } }
                        ?: active.maxByOrNull { it.id }
                    if (incident != null && notifyIfAllowed(incident, message = wsText(dto))) {
                        knownActiveIds.add(incident.id)
                        baselineEstablished = true
                        knownActiveIds.clear()
                        knownActiveIds.addAll(activeIds)
                        return
                    }
                }
            }
        }

        if (!baselineEstablished) {
            knownActiveIds.clear()
            knownActiveIds.addAll(activeIds)
            baselineEstablished = true
            return
        }

        val newIncidents = active.filter { it.id !in knownActiveIds }
        newIncidents.forEach { incident ->
            notifyIfAllowed(incident, message = null)
        }

        knownActiveIds.clear()
        knownActiveIds.addAll(activeIds)
    }

    private fun notifyIfAllowed(incident: Incident, message: String?): Boolean {
        if (!notificationHelper.hasPostPermission()) return false
        val now = System.currentTimeMillis()
        val last = lastNotifiedAt[incident.id] ?: 0L
        if (now - last < NOTIFY_COOLDOWN_MS) return false
        lastNotifiedAt[incident.id] = now

        val title = "Новый инцидент"
        val body = buildBody(incident, message)
        notificationHelper.showIncidentAlert(
            notificationId = (NOTIFICATION_ID_BASE + incident.id).toInt(),
            title = title,
            body = body
        )
        return true
    }

    private fun buildBody(incident: Incident, wsMessage: String?): String {
        wsMessage?.takeIf { it.isNotBlank() }?.let { return it }
        val severity = when (incident.severity) {
            IncidentSeverity.CRITICAL -> "Критический"
            IncidentSeverity.WARNING -> "Предупреждение"
            IncidentSeverity.INFO -> "Информация"
            IncidentSeverity.UNKNOWN -> "Инцидент"
        }
        val metric = incident.metricName?.takeIf { it.isNotBlank() }
            ?: incident.title
        return "$severity: $metric"
    }

    private fun parseWs(raw: String): WsNotificationDto? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "ping" || trimmed == "pong") return null
        return runCatching {
            json.decodeFromString(WsNotificationDto.serializer(), trimmed)
        }.getOrNull()
    }

    private fun isIncidentEvent(dto: WsNotificationDto): Boolean {
        val type = dto.type?.trim()?.lowercase()
        if (type != null && type in IGNORED_WS_TYPES) return false
        if (dto.incidentId != null) return true
        return type != null && type in INCIDENT_WS_TYPES
    }

    private fun wsText(dto: WsNotificationDto): String? =
        dto.message?.takeIf { it.isNotBlank() }
            ?: dto.title?.takeIf { it.isNotBlank() }
            ?: dto.metricName?.takeIf { it.isNotBlank() }

    companion object {
        private const val NOTIFY_COOLDOWN_MS = 60_000L
        private const val NOTIFICATION_ID_BASE = 2_000

        private val IGNORED_WS_TYPES = setOf(
            "ping", "pong", "heartbeat", "keepalive", "connected",
            "connection", "ack", "subscribe", "subscribed", "welcome"
        )

        private val INCIDENT_WS_TYPES = setOf(
            "incident", "alert", "critical", "incident_created",
            "new_incident", "incident_new", "alarm"
        )
    }
}
