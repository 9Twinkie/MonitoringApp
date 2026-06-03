package com.example.monitoringapp.di

import com.example.monitoringapp.data.api.AuthInterceptor
import com.example.monitoringapp.data.api.DynamicBaseUrlInterceptor
import com.example.monitoringapp.data.api.MonitoringApi
import com.example.monitoringapp.data.api.TerminalApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("http://127.0.0.1/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): MonitoringApi =
        retrofit.create(MonitoringApi::class.java)

    @Provides @Singleton
    fun provideTerminalApi(retrofit: Retrofit): TerminalApi =
        retrofit.create(TerminalApi::class.java)
}
