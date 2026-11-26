package com.example.di

import com.example.data.remote.api.AuthApiService
import com.example.data.remote.api.BasketApiService
import com.example.data.remote.api.InventoryApiService
import com.example.data.remote.api.NotificationApiService
import com.example.data.remote.api.OrderApiService
import com.example.data.remote.api.ProductApiService
import com.example.data.remote.api.SupplierApiService
import com.example.data.remote.api.UserApiService
import com.example.data.remote.config.ApiConfig
import com.example.data.remote.interceptor.ApiLoggingInterceptor
import com.example.data.remote.interceptor.AuthInterceptor
import com.example.data.remote.interceptor.JwtTokenAuthenticator
import com.example.data.remote.interceptor.TokenProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Centralized Retrofit, OkHttp, and Moshi network module for PharmaOS.
 *
 * Security & Reliability Guarantees:
 * - Automatic Bearer token injection via [AuthInterceptor]
 * - Single-flight JWT access token refresh & refresh-token rotation via [JwtTokenAuthenticator]
 * - Separate HTTP 401 (session expired / refresh failure) vs HTTP 403 (permission denied without logout)
 * - Scrubbed logging via [ApiLoggingInterceptor] across debug and release builds
 * - Dedicated refresh-only OkHttpClient without recursive Authenticator
 */
class NetworkModule(
    private val tokenProvider: TokenProvider,
    initialBaseUrl: String = ApiConfig.getDefaultBaseUrl(),
    private val extraInterceptors: List<Interceptor> = emptyList()
) {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val sessionExpiredEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val permissionDeniedEvents = MutableSharedFlow<String>(extraBufferCapacity = 2)

    @Volatile
    private var currentBaseUrl: String = ApiConfig.normalizeBaseUrl(initialBaseUrl)

    private val loggingInterceptor = ApiLoggingInterceptor()

    /**
     * Dedicated refresh-only HTTP client (no Authenticator) to avoid recursive 401 loops.
     */
    private val refreshOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            extraInterceptors.forEach { addInterceptor(it) }
        }
        .addInterceptor(loggingInterceptor)
        .build()

    private val jwtAuthenticator = JwtTokenAuthenticator(
        tokenProvider = tokenProvider,
        refreshHttpClient = refreshOkHttpClient,
        moshi = moshi,
        baseUrlProvider = { currentBaseUrl },
        onSessionExpired = { reason ->
            sessionExpiredEvents.tryEmit(reason)
        }
    )

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenProvider))
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // HTTP 403 Forbidden is an authorization denial, NEVER an expired session logout.
            if (response.code == 403) {
                permissionDeniedEvents.tryEmit("Access denied: you do not have permission for this action.")
            }
            response
        })
        .authenticator(jwtAuthenticator)
        .apply {
            extraInterceptors.forEach { addInterceptor(it) }
        }
        .addInterceptor(loggingInterceptor)
        .build()

    @Volatile
    private var retrofit: Retrofit = buildRetrofit(currentBaseUrl)

    private fun buildRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Synchronized
    fun updateBaseUrl(newBaseUrl: String) {
        val normalized = ApiConfig.normalizeBaseUrl(newBaseUrl)
        if (normalized != currentBaseUrl) {
            currentBaseUrl = normalized
            retrofit = buildRetrofit(normalized)
        }
    }

    fun getCurrentBaseUrl(): String = currentBaseUrl

    fun isBackendConfigured(): Boolean = ApiConfig.isBackendUrlConfigured(currentBaseUrl)

    fun getRetrofit(): Retrofit = retrofit

    fun authApiService(): AuthApiService = retrofit.create(AuthApiService::class.java)
    fun userApiService(): UserApiService = retrofit.create(UserApiService::class.java)
    fun productApiService(): ProductApiService = retrofit.create(ProductApiService::class.java)
    fun inventoryApiService(): InventoryApiService = retrofit.create(InventoryApiService::class.java)
    fun basketApiService(): BasketApiService = retrofit.create(BasketApiService::class.java)
    fun orderApiService(): OrderApiService = retrofit.create(OrderApiService::class.java)
    fun supplierApiService(): SupplierApiService = retrofit.create(SupplierApiService::class.java)
    fun notificationApiService(): NotificationApiService = retrofit.create(NotificationApiService::class.java)
    fun workspaceApiService(): com.example.data.remote.api.WorkspaceApiService =
        retrofit.create(com.example.data.remote.api.WorkspaceApiService::class.java)
}
