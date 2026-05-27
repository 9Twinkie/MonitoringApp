package com.example.monitoringapp.domain.model

enum class UserRole {
    ENGINEER,
    ADMIN,
    OTHER;

    val isAdmin: Boolean get() = this == ADMIN

    /** Push для инженера и администратора; только «гость» без роли — без push. */
    fun receivesPushAlerts(): Boolean = this == ENGINEER || this == ADMIN

    fun toApiRole(): String = when (this) {
        ADMIN -> "ADMIN"
        ENGINEER -> "ENGINEER"
        OTHER -> "ENGINEER"
    }

    companion object {
        fun fromRaw(raw: String?): UserRole {
            val value = raw?.trim()?.uppercase() ?: return OTHER
            val normalized = value.removePrefix("ROLE_")
            return when (normalized) {
                "ENGINEER", "MOBILE_ENGINEER", "USER" -> ENGINEER
                "ADMIN", "ADMINISTRATOR" -> ADMIN
                else -> OTHER
            }
        }
    }
}
