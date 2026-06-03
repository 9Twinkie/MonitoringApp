package com.example.monitoringapp.data.api

import com.example.monitoringapp.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isPublicAuth = path.endsWith("/auth/login") || path.endsWith("/auth/register")
        if (isPublicAuth) {
            return chain.proceed(
                request.newBuilder()
                    .removeHeader("Authorization")
                    .build()
            )
        }

        val token = tokenStorage.getAccessToken()?.trim()?.takeIf { it.isNotEmpty() }
        val builder = request.newBuilder()
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        } else {
            builder.removeHeader("Authorization")
        }
        return chain.proceed(builder.build())
    }
}
