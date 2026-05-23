package com.example.monitoringapp.domain.repository

import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Unit>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getUsername(): String?
    fun getUserRole(): UserRole
    fun receivesPushAlerts(): Boolean
}

interface SessionRepository {
    fun getBaseUrl(): String
    fun setBaseUrl(url: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(access: String, refresh: String?)
    fun clearSession()
    val baseUrlFlow: Flow<String>
}

interface IncidentRepository {
    val incidentsFlow: Flow<List<Incident>>
    val isOfflineFlow: Flow<Boolean>
    suspend fun refreshIncidents(): Result<List<Incident>>
    suspend fun getIncident(id: Long): Incident?
    suspend fun loadChartPoints(incident: Incident): Result<List<MetricPoint>>
    suspend fun loadIncidentChart(incident: Incident, rangeMinutes: Int = 60): Result<IncidentChartData>
    suspend fun confirmIncident(id: Long): Result<Unit>
    suspend fun closeIncident(id: Long): Result<Unit>
    suspend fun acceptIncident(id: Long): Result<Unit>
    suspend fun syncPendingActions(): Result<Unit>
}

interface MetricsRepository {
    suspend fun getOverview(): Result<MetricOverview>
    suspend fun getCachedOverview(): MetricOverview?
    suspend fun buildDashboardSummary(overview: MetricOverview? = null): DashboardSummary
    suspend fun loadMetricRange(query: String, rangeMinutes: Int = 60): Result<List<MetricPoint>>
}
