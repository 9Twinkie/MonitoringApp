package com.example.monitoringapp.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.monitoringapp.R
import com.example.monitoringapp.data.remote.NotificationEventBus
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.domain.repository.SessionRepository
import com.example.monitoringapp.ui.main.MainActivity
import com.example.monitoringapp.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class WebSocketForegroundService : Service() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var incidentRepository: IncidentRepository
    @Inject lateinit var eventBus: NotificationEventBus
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var alertCoordinator: IncidentAlertCoordinator
    @Inject lateinit var favoriteRepository: FavoriteRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var alertsSessionStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!authRepository.receivesPushAlerts()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!alertsSessionStarted) {
            alertCoordinator.resetSession()
            alertsSessionStarted = true
        }
        startForeground(Constants.NOTIFICATION_ID_WS, buildForegroundNotification())
        connect()
        startIncidentPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        alertsSessionStarted = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startIncidentPolling() {
        serviceScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refreshAndCheckIncidents(wsRaw = null)
            }
        }
    }

    private fun connect() {
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        val token = sessionRepository.getAccessToken()
        if (token.isNullOrBlank()) {
            stopSelf()
            return
        }
        val httpBase = sessionRepository.getBaseUrl()
        val wsBase = httpBase
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val url = wsBase.trimEnd('/') + Constants.WS_PATH

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                serviceScope.launch {
                    incidentRepository.syncPendingActions()
                    refreshAndCheckIncidents(wsRaw = null)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                eventBus.tryEmit(text)
                serviceScope.launch {
                    refreshAndCheckIncidents(wsRaw = text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect()
            }
        })
    }

    private suspend fun refreshAndCheckIncidents(wsRaw: String?) {
        val incidents = incidentRepository.refreshIncidents().getOrNull() ?: return
        val favorites = favoriteRepository.favoritesFlow.first()
        alertCoordinator.processAfterRefresh(incidents, wsRaw, favorites)
    }

    private fun scheduleReconnect() {
        reconnectAttempt++
        val delayMs = min(60_000L, 1_000L * (1 shl min(reconnectAttempt, 6)))
        serviceScope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.ws_notification_title))
            .setContentText(getString(R.string.ws_notification_text))
            .setSmallIcon(R.drawable.ic_alerts)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 15_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, WebSocketForegroundService::class.java))
        }
    }
}
