package com.example.data.remote.util

import com.example.data.remote.dto.ApiErrorDto
import com.example.data.remote.dto.ApiResponseDto
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Categorized network & serialization failure causes.
 */
enum class NetworkFailureType {
    TIMEOUT,
    NO_INTERNET,
    SERVER_UNAVAILABLE,
    INVALID_JSON,
    UNEXPECTED
}

/**
 * Typed HTTP/network response wrapper handling all status codes and IO failures explicitly.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(
        val data: T,
        val httpCode: Int = 200
    ) : NetworkResult<T>()

    data class HttpError(
        val code: Int,
        val rawBody: String?,
        val apiError: ApiErrorDto?,
        val message: String
    ) : NetworkResult<Nothing>()

    data class NetworkFailure(
        val type: NetworkFailureType,
        val message: String,
        val cause: Throwable? = null
    ) : NetworkResult<Nothing>()

    data class NotConfigured(
        val message: String
    ) : NetworkResult<Nothing>()

    fun toException(): ApiException {
        return when (this) {
            is HttpError -> ApiException(message = message, httpCode = code, apiError = apiError)
            is NetworkFailure -> ApiException(message = message, failureType = type, cause = cause)
            is NotConfigured -> ApiException(message = message, isNotConfigured = true)
            is Success -> IllegalStateException("Cannot convert Success to ApiException") as Nothing
        }
    }

    fun toResult(): Result<T> {
        return when (this) {
            is Success -> Result.success(data)
            else -> Result.failure(toException())
        }
    }
}

/**
 * Typed exception surfaced to ViewModels / UI with HTTP status code and failure classification.
 */
class ApiException(
    override val message: String,
    val httpCode: Int? = null,
    val apiError: ApiErrorDto? = null,
    val failureType: NetworkFailureType? = null,
    val isNotConfigured: Boolean = false,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Executes a Retrofit HTTP call safely on the caller's dispatcher, handling:
 * - HTTP 200 (OK)
 * - HTTP 201 (Created)
 * - HTTP 204 (No Content)
 * - HTTP 400 (Bad Request)
 * - HTTP 401 (Unauthorized)
 * - HTTP 403 (Forbidden)
 * - HTTP 404 (Not Found)
 * - HTTP 409 (Conflict)
 * - HTTP 422 (Unprocessable Entity)
 * - HTTP 500+ (Internal Server / Gateway Error)
 * - SocketTimeoutException (Connection Timeout)
 * - UnknownHostException (No Internet / DNS Failure)
 * - ConnectException (Server Unavailable)
 * - JsonDataException / JsonEncodingException (Invalid JSON Response)
 */
suspend fun <T> safeApiCall(
    moshi: Moshi,
    apiCall: suspend () -> Response<T>
): NetworkResult<T> {
    return try {
        val response = apiCall()
        val code = response.code()

        if (response.isSuccessful) {
            val body = response.body()
            if (code == 204 || body != null) {
                @Suppress("UNCHECKED_CAST")
                NetworkResult.Success(data = (body ?: Unit) as T, httpCode = code)
            } else {
                NetworkResult.NetworkFailure(
                    type = NetworkFailureType.INVALID_JSON,
                    message = "Server returned HTTP $code with an empty response body."
                )
            }
        } else {
            val rawErrorBody = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsedError = parseErrorBody(moshi, rawErrorBody)
            val fallbackMessage = defaultMessageForHttpCode(code)
            val finalMessage = parsedError?.toUserFriendlyMessage(fallbackMessage) ?: fallbackMessage

            NetworkResult.HttpError(
                code = code,
                rawBody = rawErrorBody,
                apiError = parsedError,
                message = finalMessage
            )
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: SocketTimeoutException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.TIMEOUT,
            message = "Connection timed out while waiting for the PharmaOS server.",
            cause = e
        )
    } catch (e: UnknownHostException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.NO_INTERNET,
            message = "Unable to resolve server host. Please check your internet connection and backend URL.",
            cause = e
        )
    } catch (e: ConnectException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.SERVER_UNAVAILABLE,
            message = "PharmaOS backend server is unavailable or refused the connection.",
            cause = e
        )
    } catch (e: JsonDataException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.INVALID_JSON,
            message = "Received unexpected JSON format from server: ${e.message ?: "schema mismatch"}",
            cause = e
        )
    } catch (e: JsonEncodingException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.INVALID_JSON,
            message = "Received malformed JSON from server.",
            cause = e
        )
    } catch (e: IOException) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.SERVER_UNAVAILABLE,
            message = "Network I/O error: ${e.localizedMessage ?: "Connection interrupted"}",
            cause = e
        )
    } catch (e: Exception) {
        NetworkResult.NetworkFailure(
            type = NetworkFailureType.UNEXPECTED,
            message = e.localizedMessage ?: "Unexpected error during network request.",
            cause = e
        )
    }
}

/**
 * Unwraps [ApiResponseDto] inside [safeApiCall] and returns the inner [data] payload cleanly.
 */
suspend fun <T> safeApiEnvelopeCall(
    moshi: Moshi,
    apiCall: suspend () -> Response<ApiResponseDto<T>>
): NetworkResult<T> {
    return when (val result = safeApiCall(moshi, apiCall)) {
        is NetworkResult.Success -> {
            val envelope = result.data
            if (!envelope.success) {
                NetworkResult.HttpError(
                    code = result.httpCode,
                    rawBody = null,
                    apiError = ApiErrorDto(status = result.httpCode, message = envelope.message),
                    message = envelope.message ?: "Request rejected by backend."
                )
            } else if (envelope.data != null) {
                NetworkResult.Success(data = envelope.data, httpCode = result.httpCode)
            } else {
                NetworkResult.NetworkFailure(
                    type = NetworkFailureType.INVALID_JSON,
                    message = envelope.message ?: "Server returned success without expected data payload."
                )
            }
        }
        is NetworkResult.HttpError -> result
        is NetworkResult.NetworkFailure -> result
        is NetworkResult.NotConfigured -> result
    }
}

internal fun parseErrorBody(moshi: Moshi, rawBody: String?): ApiErrorDto? {
    if (rawBody.isNullOrBlank()) return null
    return try {
        moshi.adapter(ApiErrorDto::class.java).fromJson(rawBody)
    } catch (_: Exception) {
        null
    }
}

internal fun defaultMessageForHttpCode(code: Int): String = when (code) {
    400 -> "Bad request (HTTP 400): Please verify your submitted details."
    401 -> "Unauthorized (HTTP 401): Invalid credentials or expired session token."
    403 -> "Forbidden (HTTP 403): You do not have permission for this operation."
    404 -> "Resource not found (HTTP 404): Requested endpoint or record does not exist."
    409 -> "Conflict (HTTP 409): A duplicate record or conflicting state already exists."
    422 -> "Validation failed (HTTP 422): One or more fields failed server validation."
    500 -> "Internal server error (HTTP 500): Spring Boot backend encountered an unexpected error."
    502, 503, 504 -> "Backend server temporarily unavailable (HTTP $code)."
    else -> "HTTP error $code returned by backend server."
}
