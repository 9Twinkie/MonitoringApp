package com.example.monitoringapp.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

object JwtUtils {

    private val json = Json { ignoreUnknownKeys = true }

    fun extractRole(accessToken: String): String? {
        val payload = decodePayload(accessToken) ?: return null
        return runCatching {
            val parsed = json.decodeFromString(JwtPayload.serializer(), payload)
            parsed.role?.takeIf { it.isNotBlank() }
                ?: parsed.roles?.firstOrNull { it.isNotBlank() }
                ?: parsed.authorities?.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    fun isExpired(accessToken: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val payload = decodePayload(accessToken) ?: return true
        val exp = runCatching {
            json.decodeFromString(JwtPayload.serializer(), payload).exp
        }.getOrNull() ?: return false
        return exp <= nowEpochSeconds
    }

    private fun decodePayload(token: String): String? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val decoded = Base64.getUrlDecoder().decode(parts[1])
        return decoded.decodeToString()
    }

    @Serializable
    private data class JwtPayload(
        val role: String? = null,
        val exp: Long? = null,
        val roles: List<String>? = null,
        @SerialName("authorities") val authorities: List<String>? = null
    )
}
