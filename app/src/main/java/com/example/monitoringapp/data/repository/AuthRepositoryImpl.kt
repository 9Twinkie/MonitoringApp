package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.data.model.LoginRequest
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.utils.ApiErrorMapper
import com.example.monitoringapp.utils.JwtUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val tokenStorage: TokenStorage
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Unit> = try {
        val response = api.login(LoginRequest(username, password))
        val access = response.accessToken ?: response.token
            ?: throw IllegalStateException("Токен не получен от сервера")
        val role = JwtUtils.extractRole(access)
        tokenStorage.saveSession(access, response.refreshToken, username, role)
        runCatching { refreshProfileInternal() }
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(IllegalStateException(mapLoginError(error), error))
    }

    override suspend fun refreshProfile(): Result<Unit> = try {
        refreshProfileInternal()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(IllegalStateException(ApiErrorMapper.toMessage(error), error))
    }

    private suspend fun refreshProfileInternal() {
        val me = api.getAuthMe()
        val role = me.role?.takeIf { it.isNotBlank() }
            ?: me.authorities?.firstOrNull { it.isNotBlank() }
            ?: me.roles?.firstOrNull { it.isNotBlank() }
        tokenStorage.updateProfile(
            userId = me.id,
            username = me.username,
            role = role
        )
    }

    private fun mapLoginError(error: Throwable): String {
        val message = ApiErrorMapper.toMessage(error)
        return if (message.contains("401") || message.contains("повторный вход", ignoreCase = true)) {
            "Неверный логин или пароль"
        } else {
            message
        }
    }

    override fun logout() {
        tokenStorage.clear()
    }

    override fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    override fun getUsername(): String? = tokenStorage.getUsername()

    override fun getUserId(): Long? = tokenStorage.getUserId()

    override fun getUserRole(): UserRole = UserRole.fromRaw(tokenStorage.getUserRole())

    override fun receivesPushAlerts(): Boolean = getUserRole().receivesPushAlerts()
}
