package com.example.monitoringapp.utils

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object JwtUtils {

    private val json = Json { ignoreUnknownKeys = true }

    fun extractRole(accessToken: String): String? {
        val payload = decodePayload(accessToken) ?: return null
        return runCatching {
            val parsed = json.decodeFromString(JwtPayload.serializer(), payload)
            parsed.role?.takeIf { it.isNotBlank() }
                ?: parsed.authorities?.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun decodePayload(token: String): String? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val decoded = Base64.decode(
            parts[1],
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return decoded.decodeToString()
    }

    @Serializable
    private data class JwtPayload(
        val role: String? = null,
        @SerialName("authorities") val authorities: List<String>? = null
    )
}
