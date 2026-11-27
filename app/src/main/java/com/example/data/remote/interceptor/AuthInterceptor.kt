package com.example.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Thread-safe token contract used by [AuthInterceptor] and [JwtTokenAuthenticator]
 * without blocking the main thread.
 */
interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String? = null
    fun getSessionGeneration(): Long = 0L
    fun updateTokens(accessToken: String?, refreshToken: String?) {}
    fun updateTokensIfSessionMatches(
        expectedGeneration: Long,
        accessToken: String?,
        refreshToken: String?
    ): Boolean {
        if (getSessionGeneration() != expectedGeneration) return false
        updateTokens(accessToken, refreshToken)
        return true
    }
    fun clearTokens() {}
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
