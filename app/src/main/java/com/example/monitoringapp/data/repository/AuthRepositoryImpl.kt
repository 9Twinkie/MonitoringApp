package com.example.monitoringapp.data.repository

import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.local.TokenStorage
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.model.LoginRequest
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.utils.ApiErrorMapper
import com.example.monitoringapp.utils.JwtUtils
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: MonitoringApi,
    private val tokenStorage: TokenStorage,
    private val incidentDao: IncidentDao
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<Unit> = try {
        val response = api.login(LoginRequest(username, password))
        val access = response.accessToken ?: response.token
            ?: throw IllegalStateException("Токен не получен от сервера")
        val role = JwtUtils.extractRole(access)
        tokenStorage.saveSession(access, response.refreshToken, username, role)
        runCatching { incidentDao.clear() }
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

    override suspend fun requireAdmin(): Result<Unit> {
        if (!isLoggedIn()) {
            return Result.failure(
                IllegalStateException("Сессия сброшена. Войдите снова как admin.")
            )
        }
        val token = tokenStorage.getAccessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Войдите в аккаунт администратора"))
        }
        refreshProfile().onFailure { return Result.failure(it) }
        if (!getUserRole().isAdmin) {
            val username = getUsername() ?: "?"
            return Result.failure(
                IllegalStateException(
                    "Сейчас вы вошли как $username (${getUserRole().name}). " +
                        "Создавать пользователей может только ADMIN — выйдите и войдите как admin."
                )
            )
        }
        return Result.success(Unit)
    }

    override fun isAccessTokenExpired(): Boolean {
        val token = tokenStorage.getAccessToken() ?: return true
        return JwtUtils.isExpired(token)
    }

    private suspend fun refreshProfileInternal() {
        val me = api.getAuthMe()
        val role = me.role?.takeIf { it.isNotBlank() }
            ?: me.authorities?.firstOrNull { it.isNotBlank() }
            ?: me.roles?.firstOrNull { it.isNotBlank() }
        tokenStorage.updateProfile(
            userId = me.id,
            username = me.username,
            role = role,
            trackerLogin = me.trackerLogin ?: me.trackerLoginSnake
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
        runCatching { runBlocking { incidentDao.clear() } }
    }

    override fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    override fun getUsername(): String? = tokenStorage.getUsername()

    override fun getUserId(): Long? = tokenStorage.getUserId()

    override fun getUserRole(): UserRole = UserRole.fromRaw(tokenStorage.getUserRole())

    override fun receivesPushAlerts(): Boolean = getUserRole().receivesPushAlerts()
}
