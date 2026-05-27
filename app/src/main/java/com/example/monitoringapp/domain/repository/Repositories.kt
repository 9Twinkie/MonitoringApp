package com.example.monitoringapp.domain.repository

import com.example.monitoringapp.domain.model.AdminUser
import com.example.monitoringapp.domain.model.CreateUserParams
import com.example.monitoringapp.domain.model.DashboardSummary
import com.example.monitoringapp.domain.model.Incident
import com.example.monitoringapp.domain.model.MetricOverview
import com.example.monitoringapp.domain.model.IncidentChartData
import com.example.monitoringapp.domain.model.MetricPoint
import com.example.monitoringapp.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun refreshProfile(): Result<Unit>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getUsername(): String?
    fun getUserId(): Long?
    fun getUserRole(): UserRole
    fun receivesPushAlerts(): Boolean
}

interface UserRepository {
    suspend fun listUsers(): Result<List<AdminUser>>
    suspend fun createUser(params: CreateUserParams): Result<AdminUser>
    suspend fun deleteUser(id: Long): Result<Unit>
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
    suspend fun closeIncident(id: Long, comment: String? = null): Result<Unit>
    suspend fun acceptIncident(id: Long): Result<Unit>
    suspend fun syncPendingActions(): Result<Unit>
}

interface MetricsRepository {
    suspend fun getOverview(): Result<MetricOverview>
    suspend fun getCachedOverview(): MetricOverview?
    suspend fun buildDashboardSummary(overview: MetricOverview? = null): DashboardSummary
    suspend fun loadMetricRange(query: String, rangeMinutes: Int = 60): Result<List<MetricPoint>>
    suspend fun searchMetricNames(query: String, limit: Int = 20): Result<List<String>>
    suspend fun suggestPromql(query: String, limit: Int = 20): Result<List<String>>
    suspend fun suggestLabelValues(label: String, prefix: String, limit: Int = 20): Result<List<String>>
    suspend fun searchMetricSuggestions(query: String, limit: Int = 20): Result<List<String>>
    fun getLastChartQuery(): String?
    fun setLastChartQuery(query: String?)
    fun getLastChartRangeMinutes(): Int
    fun setLastChartRangeMinutes(minutes: Int)
}

interface FavoriteRepository {
    val favoritesFlow: Flow<List<com.example.monitoringapp.domain.model.FavoriteTarget>>
    val notifyFavoritesOnlyFlow: Flow<Boolean>
    val dashboardFavoritesOnlyFlow: Flow<Boolean>
    suspend fun toggleFavorite(target: com.example.monitoringapp.domain.model.FavoriteTarget)
    suspend fun isFavorite(targetKey: String): Boolean
    fun setNotifyFavoritesOnly(enabled: Boolean)
    fun setDashboardFavoritesOnly(enabled: Boolean)
    fun shouldNotifyForIncident(
        incident: Incident,
        favorites: List<com.example.monitoringapp.domain.model.FavoriteTarget>
    ): Boolean
    fun isFavoriteIncident(
        incident: Incident,
        favorites: List<com.example.monitoringapp.domain.model.FavoriteTarget>
    ): Boolean
}
