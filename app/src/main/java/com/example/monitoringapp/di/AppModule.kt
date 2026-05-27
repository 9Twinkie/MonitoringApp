package com.example.monitoringapp.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.monitoringapp.data.local.AppDatabase
import com.example.monitoringapp.data.local.dao.FavoriteDao
import com.example.monitoringapp.data.local.dao.IncidentDao
import com.example.monitoringapp.data.local.dao.MetricCacheDao
import com.example.monitoringapp.data.local.dao.PendingActionDao
import com.example.monitoringapp.data.repository.AuthRepositoryImpl
import com.example.monitoringapp.data.repository.FavoriteRepositoryImpl
import com.example.monitoringapp.data.repository.IncidentRepositoryImpl
import com.example.monitoringapp.data.repository.MetricsRepositoryImpl
import com.example.monitoringapp.data.repository.SessionRepositoryImpl
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.domain.repository.FavoriteRepository
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.domain.repository.MetricsRepository
import com.example.monitoringapp.domain.repository.SessionRepository
import com.example.monitoringapp.data.repository.UserRepositoryImpl
import com.example.monitoringapp.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindAuth(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindSession(impl: SessionRepositoryImpl): SessionRepository

    @Binds @Singleton
    abstract fun bindIncidents(impl: IncidentRepositoryImpl): IncidentRepository

    @Binds @Singleton
    abstract fun bindMetrics(impl: MetricsRepositoryImpl): MetricsRepository

    @Binds @Singleton
    abstract fun bindFavorites(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds @Singleton
    abstract fun bindUsers(impl: UserRepositoryImpl): UserRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "monitoring.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideIncidentDao(db: AppDatabase): IncidentDao = db.incidentDao()
    @Provides fun provideMetricCacheDao(db: AppDatabase): MetricCacheDao = db.metricCacheDao()
    @Provides fun providePendingActionDao(db: AppDatabase): PendingActionDao = db.pendingActionDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
