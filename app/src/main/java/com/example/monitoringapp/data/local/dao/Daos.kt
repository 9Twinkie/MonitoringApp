package com.example.monitoringapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.monitoringapp.data.local.entity.FavoriteEntity
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

    @Transaction
    suspend fun replaceAll(items: List<IncidentEntity>) {
        clear()
        insertAll(items)
    }
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

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE targetKey = :key LIMIT 1")
    suspend fun getByKey(key: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE targetKey = :key")
    suspend fun delete(key: String)
}
