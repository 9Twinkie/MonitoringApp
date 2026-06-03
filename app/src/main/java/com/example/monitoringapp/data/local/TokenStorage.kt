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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    fun getTrackerLogin(): String? = prefs.getString(Constants.KEY_TRACKER_LOGIN, null)?.trim()?.takeIf { it.isNotBlank() }
    /** Логин для отображения в UI (как в Трекере): tracker_login, иначе username. */
    fun getDisplayLogin(): String? = getTrackerLogin() ?: getUsername()
    fun isLoggedIn(): Boolean = prefs.getBoolean(Constants.KEY_LOGGED_IN, false) &&
        !getAccessToken().isNullOrBlank()
    fun getBaseUrl(): String = _baseUrl.value
    fun getUserRole(): String? = prefs.getString(Constants.KEY_USER_ROLE, null)
    fun getUserId(): Long? {
        if (!prefs.contains(Constants.KEY_USER_ID)) return null
        val id = prefs.getLong(Constants.KEY_USER_ID, -1L)
        return id.takeIf { it >= 0 }
    }
    fun updateProfile(userId: Long?, username: String?, role: String?, trackerLogin: String? = null) {
        prefs.edit().apply {
            userId?.let { putLong(Constants.KEY_USER_ID, it) }
            username?.let { putString(Constants.KEY_USERNAME, it) }
            role?.let { putString(Constants.KEY_USER_ROLE, it) }
            if (trackerLogin != null) {
                val trimmed = trackerLogin.trim()
                if (trimmed.isNotEmpty()) {
                    putString(Constants.KEY_TRACKER_LOGIN, trimmed)
                } else {
                    remove(Constants.KEY_TRACKER_LOGIN)
                }
            }
        }.apply()
    }

    fun saveSession(
        accessToken: String,
        refreshToken: String?,
        username: String,
        role: String? = null
    ) {
        prefs.edit()
            .putString(Constants.KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(Constants.KEY_REFRESH_TOKEN, refreshToken?.trim())
            .putString(Constants.KEY_USERNAME, username.trim())
            .putString(Constants.KEY_USER_ROLE, role?.trim())
            .putBoolean(Constants.KEY_LOGGED_IN, true)
            .commit()
    }

    fun setBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        val previousHost = hostPortKey(getBaseUrl())
        prefs.edit().putString(Constants.KEY_BASE_URL, normalized).commit()
        _baseUrl.value = normalized
        val newHost = hostPortKey(normalized)
        if (previousHost != newHost) {
            clearSessionKeepBaseUrl(normalized)
        }
    }

    private fun clearSessionKeepBaseUrl(baseUrl: String) {
        prefs.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .remove(Constants.KEY_USERNAME)
            .remove(Constants.KEY_USER_ROLE)
            .remove(Constants.KEY_USER_ID)
            .remove(Constants.KEY_TRACKER_LOGIN)
            .putBoolean(Constants.KEY_LOGGED_IN, false)
            .putString(Constants.KEY_BASE_URL, baseUrl)
            .commit()
    }

    private fun hostPortKey(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        return "${httpUrl.host}:${httpUrl.port}"
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
            .remove(Constants.KEY_TRACKER_LOGIN)
            .putBoolean(Constants.KEY_LOGGED_IN, false)
            .apply()
    }

    private fun getBaseUrlInternal(): String {
        val storedRaw = prefs.getString(Constants.KEY_BASE_URL, null)
        val storedNormalized = storedRaw?.let { normalizeBaseUrl(it) }

        val resolved = normalizeBaseUrl(
            storedRaw ?: BuildConfig.DEFAULT_BASE_URL
        )

        val migrated = migrateBaseUrl(resolved)
        if (storedNormalized != null && migrated != storedNormalized) {
            prefs.edit().putString(Constants.KEY_BASE_URL, migrated).apply()
        }
        return migrated
    }

    private fun migrateBaseUrl(url: String): String {
        val target = normalizeBaseUrl(BuildConfig.DEFAULT_BASE_URL)
        return when (url) {
            "http://10.196.16.46:8080",
            "http://10.196.16.228:8080",
            "http://10.24.179.228:8080" -> target
            else -> url
        }
    }

    private fun isNotifyFavoritesOnlyInternal(): Boolean =
        prefs.getBoolean(Constants.KEY_NOTIFY_FAVORITES_ONLY, false)

    private fun isDashboardFavoritesOnlyInternal(): Boolean =
        prefs.getBoolean(Constants.KEY_DASHBOARD_FAVORITES_ONLY, false)

    private fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        value = value.trimEnd('/')
        val parsed = value.toHttpUrlOrNull() ?: return value
        val hasExplicitPort = Regex(":\\d+").containsMatchIn(value.substringAfter("://"))
        if (parsed.scheme == "http" && parsed.port == 80 && !hasExplicitPort) {
            return parsed.newBuilder().port(8080).build().toString().trimEnd('/')
        }
        return value
    }
}
