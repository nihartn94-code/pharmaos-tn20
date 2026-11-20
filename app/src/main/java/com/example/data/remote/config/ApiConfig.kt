package com.example.data.remote.config

import com.example.BuildConfig

/**
 * Supported backend runtime targets for PharmaOS Spring Boot API.
 *
 * IMPORTANT: No private developer IP or live production URL is hardcoded here.
 * All defaults are explicit placeholders until the Spring Boot backend URL is confirmed.
 */
enum class BackendEnvironment(
    val displayName: String,
    val defaultBaseUrl: String,
    val isLocalHttpAllowed: Boolean,
    val description: String
) {
    EMULATOR_LOCAL(
        displayName = "Android Emulator (Localhost)",
        defaultBaseUrl = "http://10.0.2.2:8080/",
        isLocalHttpAllowed = true,
        description = "Routes Android Emulator traffic to host machine Spring Boot port 8080."
    ),
    PHYSICAL_DEVICE_LAN(
        displayName = "Physical Device (LAN Placeholder)",
        defaultBaseUrl = "http://YOUR_LOCAL_LAN_IP:8080/",
        isLocalHttpAllowed = true,
        description = "Replace YOUR_LOCAL_LAN_IP with the Spring Boot host LAN IP on the same Wi-Fi."
    ),
    PRODUCTION(
        displayName = "Production Backend (HTTPS)",
        defaultBaseUrl = "https://BACKEND_URL_NOT_CONFIGURED.pharmaos.invalid/",
        isLocalHttpAllowed = false,
        description = "HTTPS-only hosted production Spring Boot & PostgreSQL backend."
    ),
    CUSTOM(
        displayName = "Custom Backend URL",
        defaultBaseUrl = "",
        isLocalHttpAllowed = false,
        description = "Explicitly entered backend URL during integration testing."
    )
}

/**
 * Explicitly separates authoritative Spring Boot backend HTTP authentication
 * from local-only Room database testing mode.
 */
enum class AuthExecutionMode(
    val label: String,
    val description: String
) {
    PRODUCTION_BACKEND_HTTP(
        label = "Backend HTTP API",
        description = "Authoritative login & registration via Spring Boot REST API. Never falls back to local credentials."
    ),
    DEVELOPMENT_LOCAL_MODE(
        label = "Development Local Mode",
        description = "Local Room database testing mode (offline/emulator dev accounts such as manufacturer@pharmaos.com)."
    )
}

/**
 * Centralized API configuration constants & URL validation helpers.
 */
object ApiConfig {

    const val API_VERSION_PREFIX = "api/v1/"

    const val PLACEHOLDER_UNCONFIGURED_BASE_URL = "https://BACKEND_URL_NOT_CONFIGURED.pharmaos.invalid/"
    const val EMULATOR_DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
    const val PHYSICAL_DEVICE_PLACEHOLDER_URL = "http://YOUR_LOCAL_LAN_IP:8080/"

    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    /**
     * Default build-time URL from BuildConfig if defined, otherwise explicit unconfigured placeholder.
     */
    fun getDefaultBaseUrl(): String {
        val buildConfigUrl = try {
            BuildConfig::class.java.getField("PHARMAOS_API_BASE_URL").get(null) as? String
        } catch (_: Exception) {
            null
        }
        return normalizeBaseUrl(buildConfigUrl ?: PLACEHOLDER_UNCONFIGURED_BASE_URL)
    }

    /**
     * Ensures trailing slash for Retrofit base URL compatibility.
     */
    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return PLACEHOLDER_UNCONFIGURED_BASE_URL
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * Returns true only when [url] is an actionable backend base URL and NOT an unconfigured placeholder.
     */
    fun isBackendUrlConfigured(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = url.trim().lowercase()
        if (normalized.contains("not_configured") ||
            normalized.contains("your_local_lan_ip") ||
            normalized.contains(".invalid")
        ) {
            return false
        }
        return normalized.startsWith("https://") || isAllowedLocalHttpUrl(normalized)
    }

    /**
     * Checks if a cleartext HTTP URL targets an explicitly permitted local development host.
     */
    fun isAllowedLocalHttpUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://")) return false
        val hostPart = lower.removePrefix("http://").substringBefore("/").substringBefore(":")
        return hostPart == "10.0.2.2" ||
            hostPart == "localhost" ||
            hostPart == "127.0.0.1" ||
            hostPart.startsWith("192.168.") ||
            hostPart.startsWith("10.")
    }

    /**
     * Validates that production environments strictly enforce HTTPS.
     */
    fun isSecureForProduction(url: String): Boolean {
        return url.trim().lowercase().startsWith("https://") && isBackendUrlConfigured(url)
    }
}
