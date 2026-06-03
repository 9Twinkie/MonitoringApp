package com.example.monitoringapp.service

import com.example.monitoringapp.data.model.WsNotificationDto
import com.example.monitoringapp.domain.model.FavoriteTarget
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.IncidentSeverity
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.utils.IncidentDisplayHelper
import com.example.monitoringapp.utils.NotificationHelper
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentAlertCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
    private val notificationHelper: NotificationHelper,
    private val json: Json
) {

    private val knownActiveIds = mutableSetOf<Long>()
    private var baselineEstablished = false
    private var myInProgressSnapshots: Map<Long, Incident> = emptyMap()

    fun resetSession() {
        knownActiveIds.clear()
        baselineEstablished = false
        myInProgressSnapshots = emptyMap()
    }

    fun processAfterRefresh(
        incidents: List<Incident>,
        wsRaw: String? = null,
        favorites: List<FavoriteTarget> = emptyList()
    ) {
        if (!authRepository.receivesPushAlerts()) return

        val username = authRepository.getUsername()
        val active = incidents.filter { it.status != IncidentStatus.CLOSED }
        val activeIds = active.map { it.id }.toSet()
        val closedIds = incidents.filter { it.status == IncidentStatus.CLOSED }.map { it.id }.toSet()
        knownActiveIds.removeAll(closedIds)

        wsRaw?.let { raw ->
            handleWsEvent(raw, incidents, active, favorites, username)
        }

        if (!baselineEstablished) {
            knownActiveIds.addAll(activeIds)
            myInProgressSnapshots = snapshotMyInProgress(incidents, username)
            baselineEstablished = true
            return
        }

        val autoResolved = IncidentAutoResolveNotifier.detectAutoResolved(
            myInProgressSnapshots,
            incidents,
            username
        )
        autoResolved.forEach { incident ->
            notifyAutoResolved(incident, wsMessage = null)
            myInProgressSnapshots = myInProgressSnapshots - incident.id
            knownActiveIds.remove(incident.id)
        }

        myInProgressSnapshots = snapshotMyInProgress(incidents, username)

        if (activeIds.isEmpty()) return

        val newIncidents = active.filter { it.id !in knownActiveIds }
        newIncidents.forEach { incident ->
            if (notifyIfAllowed(incident, favorites, message = null)) {
                knownActiveIds.add(incident.id)
            }
        }
    }

    private fun snapshotMyInProgress(
        incidents: List<Incident>,
        username: String?
    ): Map<Long, Incident> =
        incidents
            .filter { IncidentAutoResolveNotifier.isInWorkForEngineer(it, username) }
            .associateBy { it.id }

    private fun handleWsEvent(
        raw: String,
        incidents: List<Incident>,
        active: List<Incident>,
        favorites: List<FavoriteTarget>,
        username: String?
    ) {
        val dto = parseWs(raw) ?: return
        val type = dto.type?.trim()?.lowercase()

        if (IncidentAutoResolveNotifier.isResolvedWsType(type)) {
            val incidentId = dto.incidentId ?: return
            if (incidentId !in myInProgressSnapshots) return
            val incident = incidents.find { it.id == incidentId }
                ?: myInProgressSnapshots[incidentId]
                ?: return
            if (notifyAutoResolved(incident, wsText(dto))) {
                myInProgressSnapshots = myInProgressSnapshots - incidentId
                knownActiveIds.remove(incidentId)
            }
            return
        }

        if (!isIncidentEvent(dto)) return

        val incidentId = dto.incidentId ?: return
        if (incidentId in knownActiveIds) return

        val incident = active.find { it.id == incidentId } ?: return
        if (notifyIfAllowed(incident, favorites, message = wsText(dto))) {
            knownActiveIds.add(incident.id)
            baselineEstablished = true
        }
    }

    private fun notifyAutoResolved(incident: Incident, wsMessage: String?): Boolean {
        if (!notificationHelper.hasPostPermission()) return false
        val metricLabel = wsMessage?.takeIf { it.isNotBlank() }
            ?: metricLabel(incident)
        notificationHelper.showIncidentResolvedAlert(incident.id, metricLabel)
        return true
    }

    private fun notifyIfAllowed(
        incident: Incident,
        favorites: List<FavoriteTarget>,
        message: String?
    ): Boolean {
        if (!favoriteRepository.shouldNotifyForIncident(incident, favorites)) return false
        if (!notificationHelper.hasPostPermission()) return false

        val isFavorite = favoriteRepository.isFavoriteIncident(incident, favorites)
        val title = if (isFavorite) "Избранный объект" else "Новый инцидент"
        val body = buildBody(incident, message)
        notificationHelper.showIncidentAlert(
            notificationId = (NOTIFICATION_ID_BASE + incident.id).toInt(),
            title = title,
            body = body
        )
        return true
    }

    private fun metricLabel(incident: Incident): String =
        IncidentDisplayHelper.promql(incident)
            .takeIf { it != "—" }
            ?: incident.title

    private fun buildBody(incident: Incident, wsMessage: String?): String {
        wsMessage?.takeIf { it.isNotBlank() }?.let { return it }
        val severity = when (incident.severity) {
            IncidentSeverity.CRITICAL -> "Критический"
            IncidentSeverity.WARNING -> "Предупреждение"
            IncidentSeverity.INFO -> "Низкий"
            IncidentSeverity.UNKNOWN -> "Низкий"
        }
        return "$severity: ${metricLabel(incident)}"
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
        if (IncidentAutoResolveNotifier.isResolvedWsType(type)) return false
        if (dto.incidentId != null) return true
        return type != null && type in INCIDENT_WS_TYPES
    }

    private fun wsText(dto: WsNotificationDto): String? =
        dto.message?.takeIf { it.isNotBlank() }
            ?: dto.title?.takeIf { it.isNotBlank() }
            ?: dto.metricName?.takeIf { it.isNotBlank() }

    companion object {
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
