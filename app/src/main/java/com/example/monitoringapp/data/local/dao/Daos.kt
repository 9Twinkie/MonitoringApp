package com.example.monitoringapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.monitoringapp.data.local.entity.IncidentEntity
import com.example.monitoringapp.data.local.entity.MetricCacheEntity
import com.example.monitoringapp.data.local.entity.PendingActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<IncidentEntity>)

    @Query("DELETE FROM incidents")
    suspend fun clear()
}

@Dao
interface MetricCacheDao {
    @Query("SELECT * FROM metric_cache WHERE id = 1 LIMIT 1")
    suspend fun get(): MetricCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: MetricCacheEntity)
}

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions")
    fun observeCount(): Flow<Int>
}
