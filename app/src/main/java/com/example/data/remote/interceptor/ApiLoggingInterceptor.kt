package com.example.data.remote.interceptor

import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Configures HTTP request/response logging with automatic Authorization header redaction
 * outside of debug builds.
 */
class ApiLoggingInterceptor(
    private val isVerboseLoggingEnabled: Boolean = BuildConfig.DEBUG
) : Interceptor {

    private val delegate: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (isVerboseLoggingEnabled) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
