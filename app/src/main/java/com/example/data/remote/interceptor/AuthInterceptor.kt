package com.example.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Provides the active JWT access token without blocking the main thread.
 */
interface TokenProvider {
    fun getAccessToken(): String?
}

/**
 * OkHttp Interceptor that injects standard JSON headers and the Authorization Bearer token
 * on protected API requests.
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        val isPublicAuthEndpoint = path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/google") ||
            path.endsWith("/auth/refresh")

        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("X-Client-Platform", "Android-PharmaOS")

        if (!isPublicAuthEndpoint && originalRequest.header("Authorization") == null) {
            val token = tokenProvider.getAccessToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
