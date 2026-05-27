package com.example.monitoringapp.data.api

import com.example.monitoringapp.data.model.AdminUserDto
import com.example.monitoringapp.data.model.AuthMeDto
import com.example.monitoringapp.data.model.CloseIncidentRequest
import com.example.monitoringapp.data.model.CreateUserRequest
import com.example.monitoringapp.data.model.IncidentChartResponseDto
import com.example.monitoringapp.data.model.IncidentDto
import com.example.monitoringapp.data.model.LoginRequest
import com.example.monitoringapp.data.model.LoginResponse
import com.example.monitoringapp.data.model.PrometheusOverviewDto
import com.example.monitoringapp.data.model.PrometheusQueryRangeDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MonitoringApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/me")
    suspend fun getAuthMe(): AuthMeDto

    @GET("admin/users")
    suspend fun getAdminUsersRaw(): ResponseBody

    @POST("admin/users")
    suspend fun createAdminUserRaw(@Body request: CreateUserRequest): ResponseBody

    @DELETE("admin/users/{id}")
    suspend fun deleteAdminUser(@Path("id") id: Long)

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
        @Query(value = "query", encoded = true) query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    @GET("monitoring/prometheus/query_range")
    suspend fun getPrometheusQueryRange(
        @Query(value = "query", encoded = true) query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    /** Legacy-путь на бэкенде (оставлен для совместимости). */
    @GET("monitoring/metrics/range")
    suspend fun getMetricsRangeRaw(
        @Query(value = "query", encoded = true) query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): ResponseBody

    @GET("monitoring/metrics/range")
    suspend fun getMetricsRange(
        @Query(value = "query", encoded = true) query: String,
        @Query("hours") hours: Int? = null,
        @Query("minutes") minutes: Int? = null,
        @Query("step") step: String = "15s"
    ): PrometheusQueryRangeDto

    @GET("monitoring/metrics/names")
    suspend fun getMetricNamesRaw(
        @Query(value = "match", encoded = true) match: String? = null,
        @Query(value = "q", encoded = true) q: String? = null,
        @Query("limit") limit: Int? = 20
    ): ResponseBody

    @GET("monitoring/metrics/suggest")
    suspend fun getMetricSuggestRaw(
        @Query(value = "q", encoded = true) q: String,
        @Query(value = "query", encoded = true) query: String? = null,
        @Query("limit") limit: Int? = 20
    ): ResponseBody

    @GET("monitoring/metrics/labels/{label}/values")
    suspend fun getLabelValuesRaw(
        @Path("label") label: String,
        @Query("match") match: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int? = 50
    ): ResponseBody

    @POST("incidents/{id}/accept")
    suspend fun acceptIncident(@Path("id") id: Long)

    @POST("incidents/{id}/confirm")
    suspend fun confirmIncident(@Path("id") id: Long)

    @POST("incidents/{id}/close")
    suspend fun closeIncident(
        @Path("id") id: Long,
        @Body request: CloseIncidentRequest = CloseIncidentRequest()
    )

    @GET("monitoring/prometheus/overview")
    suspend fun getMetricsOverviewRaw(): ResponseBody
}
