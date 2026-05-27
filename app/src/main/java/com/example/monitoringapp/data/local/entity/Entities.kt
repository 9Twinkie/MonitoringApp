package com.example.monitoringapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String? = null,
    val host: String,
    val status: String,
    val severity: String,
    val metricName: String?,
    val metricExpr: String? = null,
    val metricValue: String?,
    val firedAt: String? = null,
    val createdAt: String?,
    val rule: String?,
    val assignedEngineerUsername: String? = null,
    val canAccept: Boolean = false,
    val canConfirm: Boolean = false,
    val canClose: Boolean = false,
    val closeComment: String? = null,
    val closedByUsername: String? = null,
    val chartJson: String?,
    val cachedAt: Long
)

@Entity(tableName = "metric_cache")
data class MetricCacheEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val cachedAt: Long
)

@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val incidentId: Long,
    val action: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val targetKey: String,
    val displayName: String,
    val host: String,
    val addedAt: Long = System.currentTimeMillis()
)
