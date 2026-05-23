package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val tokenStorage: TokenStorage
) : SessionRepository {

    override val baseUrlFlow: Flow<String> = tokenStorage.baseUrlFlow

    override fun getBaseUrl(): String = tokenStorage.getBaseUrl()

    override fun setBaseUrl(url: String) = tokenStorage.setBaseUrl(url)

    override fun getAccessToken(): String? = tokenStorage.getAccessToken()

    override fun getRefreshToken(): String? = tokenStorage.getRefreshToken()

    override fun saveTokens(access: String, refresh: String?) {
        val username = tokenStorage.getUsername() ?: ""
        tokenStorage.saveSession(access, refresh, username, tokenStorage.getUserRole())
    }

    override fun clearSession() = tokenStorage.clear()
}
