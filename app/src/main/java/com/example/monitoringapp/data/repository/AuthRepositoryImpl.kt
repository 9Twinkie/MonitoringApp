package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.data.model.LoginRequest
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.utils.JwtUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(username, password))
        val access = response.accessToken ?: response.token
            ?: throw IllegalStateException("Токен не получен от сервера")
        val role = JwtUtils.extractRole(access)
        tokenStorage.saveSession(access, response.refreshToken, username, role)
    }

    override fun logout() {
        tokenStorage.clear()
    }

    override fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    override fun getUsername(): String? = tokenStorage.getUsername()

    override fun getUserRole(): UserRole = UserRole.fromRaw(tokenStorage.getUserRole())

    override fun receivesPushAlerts(): Boolean = getUserRole().receivesPushAlerts()
}
