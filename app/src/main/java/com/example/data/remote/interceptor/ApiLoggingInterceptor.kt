package com.example.data.remote.interceptor

import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Configures HTTP request/response logging with strict credential & JWT scrubbing
 * across BOTH debug and release builds so tokens and passwords never leak to logcat.
 */
class ApiLoggingInterceptor(
    private val isVerboseLoggingEnabled: Boolean = BuildConfig.DEBUG,
    private val logSink: (String) -> Unit = { sanitized ->
        HttpLoggingInterceptor.Logger.DEFAULT.log(sanitized)
    }
) : Interceptor {

    companion object {
        private val SENSITIVE_JSON_FIELD_REGEX = Regex(
            pattern = """("(?:accessToken|refreshToken|token|idToken|password|currentPassword|newPassword|secret)"\s*:\s*")([^"]*)(")""",
            option = RegexOption.IGNORE_CASE
        )
        private val BEARER_TOKEN_REGEX = Regex(
            pattern = """Bearer\s+[A-Za-z0-9\-._~+/]+=*""",
            option = RegexOption.IGNORE_CASE
        )
        private val JWT_PATTERN_REGEX = Regex(
            pattern = """eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"""
        )

        /**
         * Scrubs JWTs, Bearer headers, and password/token JSON fields from any log line.
         */
        fun sanitizeLogLine(message: String): String {
            var sanitized = SENSITIVE_JSON_FIELD_REGEX.replace(message) { match ->
                "${match.groupValues[1]}[REDACTED]${match.groupValues[3]}"
            }
            sanitized = BEARER_TOKEN_REGEX.replace(sanitized, "Bearer [REDACTED]")
            sanitized = JWT_PATTERN_REGEX.replace(sanitized, "[REDACTED_JWT]")
            return sanitized
        }
    }

    private val delegate: HttpLoggingInterceptor = HttpLoggingInterceptor { rawMessage ->
        logSink(sanitizeLogLine(rawMessage))
    }.apply {
        level = if (isVerboseLoggingEnabled) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        redactHeader("X-Refresh-Token")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
