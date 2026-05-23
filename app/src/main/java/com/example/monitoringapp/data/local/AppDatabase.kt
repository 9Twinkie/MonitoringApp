package com.example.monitoringapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.dao.MetricCacheDao
import com.example.monitoringapp.data.local.dao.PendingActionDao
import com.example.monitoringapp.data.local.entity.IncidentEntity
import com.example.monitoringapp.data.local.entity.MetricCacheEntity
import com.example.monitoringapp.data.local.entity.PendingActionEntity

@Database(
    entities = [
        IncidentEntity::class,
        MetricCacheEntity::class,
        PendingActionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun metricCacheDao(): MetricCacheDao
    abstract fun pendingActionDao(): PendingActionDao
}
