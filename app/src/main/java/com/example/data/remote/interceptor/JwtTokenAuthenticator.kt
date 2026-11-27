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
import java.io.IOException

/**
 * OkHttp [Authenticator] implementing single-flight JWT access token refresh & refresh-token rotation.
 *
 * Security & Reliability Guarantees:
 * - Synchronized single-flight refresh prevents thundering-herd parallel refresh calls.
 * - Distinguishes authentication rejections (HTTP 401/403/400 refresh invalid/revoked -> clear
 *   tokens and notify [onSessionExpired]) from transient network/timeout/HTTP 5xx/429 failures
 *   (preserves current tokens & session so temporary outages never unexpectedly log users out).
 * - Guards token writes with monotonic `sessionGeneration` so an in-flight refresh CANNOT restore
 *   an invalidated session after logout or account switch.
 * - Strictly limits retries to at most 1 retry per failed request (`MAX_AUTH_RETRIES = 1`) to
 *   prevent infinite 401 loops.
 * - Skips public authentication endpoints (`/auth/login`, `/auth/register`, `/auth/google`,
 *   `/auth/refresh`, `/auth/logout`).
 * - HTTP 403 Forbidden on normal API endpoints is never handled here and never logs the user out.
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

    private sealed class RefreshOutcome {
        data class Success(val auth: AuthResponseDto) : RefreshOutcome()
        data class AuthRejected(val httpCode: Int, val reason: String) : RefreshOutcome()
        data class TransientFailure(val reason: String) : RefreshOutcome()
    }

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val originalRequest = response.request
        val path = originalRequest.url.encodedPath

        // Never attempt refresh on public auth endpoints or logout
        if (isAuthLifecycleEndpoint(path)) {
            return null
        }

        val capturedGeneration = tokenProvider.getSessionGeneration()

        // Strict retry-loop guard: abort if already retried once or prior response chain >= 2
        val previousRetryCount = originalRequest.header(HEADER_AUTH_RETRY)?.toIntOrNull() ?: 0
        if (previousRetryCount >= MAX_AUTH_RETRIES || responseCount(response) >= MAX_AUTH_RETRIES + 1) {
            if (tokenProvider.getSessionGeneration() == capturedGeneration) {
                expireSession("Your session has expired. Please sign in again.")
            }
            return null
        }

        val failedBearerToken = originalRequest.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        synchronized(refreshLock) {
            // If user logged out or switched accounts while waiting on the lock, abort without modifying tokens
            if (tokenProvider.getSessionGeneration() != capturedGeneration) {
                return null
            }

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

            when (val outcome = executeRefreshTokenCall(currentRefreshToken)) {
                is RefreshOutcome.Success -> {
                    val refreshed = outcome.auth
                    val nextRefreshToken = refreshed.refreshToken?.takeIf { it.isNotBlank() } ?: currentRefreshToken
                    // Atomically persist only if logout / account switch did not occur during the network call
                    val committed = tokenProvider.updateTokensIfSessionMatches(
                        expectedGeneration = capturedGeneration,
                        accessToken = refreshed.accessToken,
                        refreshToken = nextRefreshToken
                    )
                    if (!committed) {
                        return null
                    }
                    return originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.accessToken}")
                        .header(HEADER_AUTH_RETRY, (previousRetryCount + 1).toString())
                        .build()
                }
                is RefreshOutcome.AuthRejected -> {
                    // Only expire the session if it is still the same session generation
                    if (tokenProvider.getSessionGeneration() == capturedGeneration) {
                        expireSession(outcome.reason)
                    }
                    return null
                }
                is RefreshOutcome.TransientFailure -> {
                    // Do NOT clear tokens or log the user out on temporary network/timeout/5xx outages
                    return null
                }
            }
        }
    }

    private fun executeRefreshTokenCall(refreshToken: String): RefreshOutcome {
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
                when {
                    resp.code == 401 || resp.code == 403 || resp.code == 400 -> {
                        RefreshOutcome.AuthRejected(
                            httpCode = resp.code,
                            reason = "Your session has expired or was revoked. Please sign in again."
                        )
                    }
                    resp.code >= 500 || resp.code == 429 || resp.code == 408 -> {
                        RefreshOutcome.TransientFailure(
                            "Authentication server temporarily unavailable (HTTP ${resp.code})."
                        )
                    }
                    !resp.isSuccessful -> {
                        RefreshOutcome.TransientFailure("Unexpected refresh response (HTTP ${resp.code}).")
                    }
                    else -> {
                        val rawBody = resp.body?.string().orEmpty()
                        if (rawBody.isBlank()) {
                            return RefreshOutcome.TransientFailure("Empty token refresh response from server.")
                        }
                        val parsed = parseRefreshPayload(rawBody)
                        if (parsed != null && parsed.accessToken.isNotBlank()) {
                            RefreshOutcome.Success(parsed)
                        } else {
                            RefreshOutcome.TransientFailure("Malformed token refresh payload.")
                        }
                    }
                }
            }
        } catch (io: IOException) {
            RefreshOutcome.TransientFailure("Network error while refreshing session: ${io.message}")
        } catch (t: Throwable) {
            RefreshOutcome.TransientFailure("Temporary error while refreshing session.")
        }
    }

    private fun parseRefreshPayload(rawBody: String): AuthResponseDto? {
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

        val directAdapter = moshi.adapter(AuthResponseDto::class.java)
        return try {
            directAdapter.fromJson(rawBody)?.takeIf { it.accessToken.isNotBlank() }
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
