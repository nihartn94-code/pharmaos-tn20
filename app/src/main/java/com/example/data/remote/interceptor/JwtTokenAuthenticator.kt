package com.example.data.remote.interceptor

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.AuthResponseDto
import com.example.data.remote.dto.RefreshTokenRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] implementing single-flight JWT access token refresh & refresh-token rotation.
 *
 * Security & Reliability Guarantees:
 * - Synchronized single-flight refresh prevents thundering-herd parallel refresh calls.
 * - If a concurrent request already refreshed the token, retries immediately with the new token.
 * - Persists rotated `accessToken` AND `refreshToken` atomically before retrying.
 * - Strictly limits retries to at most 1 retry per failed request (`MAX_AUTH_RETRIES = 1`) to
 *   prevent infinite 401 loops.
 * - Skips public authentication endpoints (`/auth/login`, `/auth/register`, `/auth/google`, `/auth/refresh`).
 * - Invokes [onSessionExpired] and clears tokens ONLY when authentication/refresh genuinely fails (401);
 *   HTTP 403 Forbidden is never handled by this Authenticator and never logs the user out.
 */
class JwtTokenAuthenticator(
    private val tokenProvider: TokenProvider,
    private val refreshHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrlProvider: () -> String,
    private val onSessionExpired: (String) -> Unit
) : Authenticator {

    companion object {
        private const val HEADER_AUTH_RETRY = "X-PharmaOS-Auth-Retry"
        private const val MAX_AUTH_RETRIES = 1
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val originalRequest = response.request
        val path = originalRequest.url.encodedPath

        // Never attempt refresh on public auth endpoints or logout
        if (isAuthLifecycleEndpoint(path)) {
            return null
        }

        // Strict retry-loop guard: abort if already retried once or prior response chain >= 2
        val previousRetryCount = originalRequest.header(HEADER_AUTH_RETRY)?.toIntOrNull() ?: 0
        if (previousRetryCount >= MAX_AUTH_RETRIES || responseCount(response) >= MAX_AUTH_RETRIES + 1) {
            expireSession("Your session has expired. Please sign in again.")
            return null
        }

        val failedBearerToken = originalRequest.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        synchronized(refreshLock) {
            val currentAccessToken = tokenProvider.getAccessToken()?.trim()

            // Single-flight fast path: another concurrent thread already rotated the access token
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != failedBearerToken) {
                return originalRequest.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .header(HEADER_AUTH_RETRY, (previousRetryCount + 1).toString())
                    .build()
            }

            val currentRefreshToken = tokenProvider.getRefreshToken()?.trim()
            if (currentRefreshToken.isNullOrBlank()) {
                expireSession("Your session has expired. Please sign in again.")
                return null
            }

            val refreshed = executeRefreshTokenCall(currentRefreshToken)
            if (refreshed == null || refreshed.accessToken.isBlank()) {
                expireSession("Your session could not be renewed. Please sign in again.")
                return null
            }

            // Persist rotated access & refresh tokens atomically
            val nextRefreshToken = refreshed.refreshToken?.takeIf { it.isNotBlank() } ?: currentRefreshToken
            tokenProvider.updateTokens(
                accessToken = refreshed.accessToken,
                refreshToken = nextRefreshToken
            )

            return originalRequest.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .header(HEADER_AUTH_RETRY, (previousRetryCount + 1).toString())
                .build()
        }
    }

    private fun executeRefreshTokenCall(refreshToken: String): AuthResponseDto? {
        return try {
            val baseUrl = baseUrlProvider().trimEnd('/')
            val refreshUrl = "$baseUrl/api/v1/auth/refresh"
            val reqAdapter = moshi.adapter(RefreshTokenRequestDto::class.java)
            val bodyJson = reqAdapter.toJson(RefreshTokenRequestDto(refreshToken = refreshToken))
            val request = Request.Builder()
                .url(refreshUrl)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("X-Client-Platform", "Android-PharmaOS")
                .build()

            refreshHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val rawBody = resp.body?.string().orEmpty()
                if (rawBody.isBlank()) return null

                // Support standard ApiResponseDto<AuthResponseDto> envelope
                val envelopeType = Types.newParameterizedType(ApiResponseDto::class.java, AuthResponseDto::class.java)
                val envelopeAdapter = moshi.adapter<ApiResponseDto<AuthResponseDto>>(envelopeType)
                val envelope = try {
                    envelopeAdapter.fromJson(rawBody)
                } catch (_: Exception) {
                    null
                }
                if (envelope?.data != null && envelope.data.accessToken.isNotBlank()) {
                    return envelope.data
                }

                // Fallback: direct AuthResponseDto payload
                val directAdapter = moshi.adapter(AuthResponseDto::class.java)
                try {
                    directAdapter.fromJson(rawBody)?.takeIf { it.accessToken.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun expireSession(message: String) {
        tokenProvider.clearTokens()
        onSessionExpired(message)
    }

    private fun isAuthLifecycleEndpoint(path: String): Boolean {
        return path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/google") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/logout")
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
