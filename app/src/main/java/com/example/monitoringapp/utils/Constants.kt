package com.example.monitoringapp.utils

object Constants {
    const val PREFS_NAME = "monitoring_secure_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_USERNAME = "username"
    const val KEY_BASE_URL = "base_url"
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_USER_ROLE = "user_role"
    const val KEY_USER_ID = "user_id"
    const val KEY_NOTIFY_FAVORITES_ONLY = "notify_favorites_only"
    const val KEY_DASHBOARD_FAVORITES_ONLY = "dashboard_favorites_only"
    const val KEY_METRICS_LAST_QUERY = "metrics_last_query"
    const val KEY_METRICS_RANGE_MINUTES = "metrics_range_minutes"

    const val WS_PATH = "/ws/notifications"
    const val NOTIFICATION_CHANNEL_ID = "monitoring_ws_channel"
    const val ALERT_CHANNEL_ID = "monitoring_alerts_channel"
    const val NOTIFICATION_ID_WS = 1001
    const val NOTIFICATION_ID_ALERT = 1002

    const val ACTION_WS_EVENT = "com.example.monitoringapp.WS_EVENT"
    const val EXTRA_WS_MESSAGE = "ws_message"

    const val WORK_SYNC_PENDING = "sync_pending_actions"
}
