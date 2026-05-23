package com.example.monitoringapp.data.api

import com.example.monitoringapp.data.model.IncidentChartResponseDto
import com.example.monitoringapp.data.model.IncidentDto
import com.example.monitoringapp.data.model.LoginRequest
import com.example.monitoringapp.data.model.LoginResponse
import com.example.monitoringapp.data.model.PrometheusOverviewDto
import com.example.monitoringapp.data.model.PrometheusQueryRangeDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MonitoringApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("incidents")
    suspend fun getIncidents(): List<IncidentDto>

    @GET("incidents/{id}")
    suspend fun getIncident(@Path("id") id: Long): IncidentDto

    @GET("incidents/{id}/chart")
    suspend fun getIncidentChart(
        @Path("id") id: Long,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): IncidentChartResponseDto

    @GET("monitoring/prometheus/range")
    suspend fun getPrometheusRange(
        @Query("query") query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    @GET("monitoring/prometheus/query_range")
    suspend fun getPrometheusQueryRange(
        @Query("query") query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    /** Legacy-путь на бэкенде (оставлен для совместимости). */
    @GET("monitoring/metrics/range")
    suspend fun getMetricsRange(
        @Query("query") query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    @POST("incidents/{id}/confirm")
    suspend fun confirmIncident(@Path("id") id: Long)

    @POST("incidents/{id}/close")
    suspend fun closeIncident(@Path("id") id: Long)

    @GET("monitoring/prometheus/overview")
    suspend fun getMetricsOverview(): PrometheusOverviewDto
}
