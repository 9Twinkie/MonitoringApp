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
    fun getAccessToken(): String? = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(Constants.KEY_REFRESH_TOKEN, null)
    fun getUsername(): String? = prefs.getString(Constants.KEY_USERNAME, null)
    fun isLoggedIn(): Boolean = prefs.getBoolean(Constants.KEY_LOGGED_IN, false) &&
        !getAccessToken().isNullOrBlank()
    fun getBaseUrl(): String = _baseUrl.value
    fun getUserRole(): String? = prefs.getString(Constants.KEY_USER_ROLE, null)
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
        val normalized = url.trimEnd('/')
        prefs.edit().putString(Constants.KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    fun clear() {
        prefs.edit()
            .remove(Constants.KEY_ACCESS_TOKEN)
            .remove(Constants.KEY_REFRESH_TOKEN)
            .remove(Constants.KEY_USERNAME)
            .remove(Constants.KEY_USER_ROLE)
            .putBoolean(Constants.KEY_LOGGED_IN, false)
            .apply()
    }

    private fun getBaseUrlInternal(): String =
        prefs.getString(Constants.KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL)
            ?: BuildConfig.DEFAULT_BASE_URL
}
