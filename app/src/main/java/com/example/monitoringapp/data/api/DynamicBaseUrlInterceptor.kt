package com.example.monitoringapp.data.api

import com.example.monitoringapp.data.local.TokenStorage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class DynamicBaseUrlInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredBase = tokenStorage.getBaseUrl().toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val rebuilt = original.url.newBuilder()
            .scheme(configuredBase.scheme)
            .host(configuredBase.host)
            .port(configuredBase.port)
            .build()

        if (rebuilt == original.url) {
            return chain.proceed(original)
        }
        return chain.proceed(original.newBuilder().url(rebuilt).build())
    }
}
