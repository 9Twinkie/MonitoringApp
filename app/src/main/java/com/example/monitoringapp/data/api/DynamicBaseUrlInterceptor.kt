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
        val base = tokenStorage.getBaseUrl().toHttpUrlOrNull() ?: return chain.proceed(original)
        val newUrl = base.newBuilder()
            .encodedPath(original.url.encodedPath)
            .query(original.url.query)
            .build()
        val request = original.newBuilder().url(newUrl).build()
        return chain.proceed(request)
    }
}
