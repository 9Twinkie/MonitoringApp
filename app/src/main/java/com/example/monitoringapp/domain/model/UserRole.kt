package com.example.monitoringapp.domain.model

enum class UserRole {
    ENGINEER,
    ADMIN,
    OTHER;

    /** Push для инженера и администратора; только «гость» без роли — без push. */
    fun receivesPushAlerts(): Boolean = this == ENGINEER || this == ADMIN

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
