package com.example.monitoringapp.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.monitoringapp.BuildConfig
import com.example.monitoringapp.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _baseUrl = MutableStateFlow(getBaseUrlInternal())
    val baseUrlFlow: StateFlow<String> = _baseUrl.asStateFlow()

    private val _notifyFavoritesOnly = MutableStateFlow(isNotifyFavoritesOnlyInternal())
    val notifyFavoritesOnlyFlow: StateFlow<Boolean> = _notifyFavoritesOnly.asStateFlow()

    private val _dashboardFavoritesOnly = MutableStateFlow(isDashboardFavoritesOnlyInternal())
    val dashboardFavoritesOnlyFlow: StateFlow<Boolean> = _dashboardFavoritesOnly.asStateFlow()
    fun getAccessToken(): String? = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(Constants.KEY_REFRESH_TOKEN, null)
    fun getUsername(): String? = prefs.getString(Constants.KEY_USERNAME, null)
    fun isLoggedIn(): Boolean = prefs.getBoolean(Constants.KEY_LOGGED_IN, false) &&
        !getAccessToken().isNullOrBlank()
    fun getBaseUrl(): String = _baseUrl.value
    fun getUserRole(): String? = prefs.getString(Constants.KEY_USER_ROLE, null)
    fun getUserId(): Long? {
        if (!prefs.contains(Constants.KEY_USER_ID)) return null
        val id = prefs.getLong(Constants.KEY_USER_ID, -1L)
        return id.takeIf { it >= 0 }
    }
    fun updateProfile(userId: Long?, username: String?, role: String?) {
        prefs.edit().apply {
            userId?.let { putLong(Constants.KEY_USER_ID, it) }
            username?.let { putString(Constants.KEY_USERNAME, it) }
            role?.let { putString(Constants.KEY_USER_ROLE, it) }
        }.apply()
    }

    fun saveSession(
        accessToken: String,
        refreshToken: String?,
        username: String,
        role: String? = null
    ) {
        prefs.edit()
            .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
            .putString(Constants.KEY_USERNAME, username)
            .putString(Constants.KEY_USER_ROLE, role)
            .putBoolean(Constants.KEY_LOGGED_IN, true)
            .apply()
    }

    fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        prefs.edit().putString(Constants.KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    fun isNotifyFavoritesOnly(): Boolean = _notifyFavoritesOnly.value

    fun setNotifyFavoritesOnly(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_NOTIFY_FAVORITES_ONLY, enabled).apply()
        _notifyFavoritesOnly.value = enabled
    }

    fun isDashboardFavoritesOnly(): Boolean = _dashboardFavoritesOnly.value

    fun setDashboardFavoritesOnly(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_DASHBOARD_FAVORITES_ONLY, enabled).apply()
        _dashboardFavoritesOnly.value = enabled
    }

    fun getLastMetricsQuery(): String? =
        prefs.getString(Constants.KEY_METRICS_LAST_QUERY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLastMetricsQuery(query: String?) {
        prefs.edit().apply {
            if (query.isNullOrBlank()) {
                remove(Constants.KEY_METRICS_LAST_QUERY)
            } else {
                putString(Constants.KEY_METRICS_LAST_QUERY, query.trim())
            }
        }.apply()
    }

    fun getLastMetricsRangeMinutes(): Int =
        prefs.getInt(Constants.KEY_METRICS_RANGE_MINUTES, 60)

    fun setLastMetricsRangeMinutes(minutes: Int) {
        prefs.edit().putInt(Constants.KEY_METRICS_RANGE_MINUTES, minutes).apply()
    }

    fun clear() {
        prefs.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .remove(Constants.KEY_USERNAME)
            .remove(Constants.KEY_USER_ROLE)
            .remove(Constants.KEY_USER_ID)
            .putBoolean(Constants.KEY_LOGGED_IN, false)
            .apply()
    }

    private fun getBaseUrlInternal(): String =
        normalizeBaseUrl(
            prefs.getString(Constants.KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL)
                ?: BuildConfig.DEFAULT_BASE_URL
        )

    private fun isNotifyFavoritesOnlyInternal(): Boolean =
        prefs.getBoolean(Constants.KEY_NOTIFY_FAVORITES_ONLY, false)

    private fun isDashboardFavoritesOnlyInternal(): Boolean =
        prefs.getBoolean(Constants.KEY_DASHBOARD_FAVORITES_ONLY, false)

    private fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        return value.trimEnd('/')
    }
}
