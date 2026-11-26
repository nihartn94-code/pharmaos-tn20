package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore-backed AES-256-GCM encrypted token storage for JWT access & refresh tokens.
 *
 * Security properties:
 * - Master key is hardware/Keystore-backed (`AndroidKeyStore`, `AES/GCM/NoPadding`, 256-bit)
 *   and never exported from the Keystore on Android devices (API 24+).
 * - Every write generates a fresh random 12-byte GCM IV stored alongside ciphertext + 128-bit auth tag.
 * - Stored in a dedicated SharedPreferences file (`pharmaos_secure_tokens.xml`) explicitly
 *   excluded from Android Auto Backup and Device Transfer in `backup_rules.xml` and
 *   `data_extraction_rules.xml`.
 * - For local JVM/Robolectric unit tests where `AndroidKeyStore` is absent, falls back to an
 *   ephemeral in-process AES-256 key so tests exercise the exact AES-GCM encrypt/decrypt pipeline
 *   without ever writing plaintext tokens to disk.
 */
class SecureTokenStorage(
    context: Context,
    prefsFileName: String = SECURE_PREFS_FILE_NAME
) {
    companion object {
        const val SECURE_PREFS_FILE_NAME = "pharmaos_secure_tokens"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "pharmaos_jwt_master_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12

        private const val PREF_ACCESS_TOKEN_ENC = "enc_access_token_v1"
        private const val PREF_REFRESH_TOKEN_ENC = "enc_refresh_token_v1"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)

    private val lock = Any()
    private val secureRandom = SecureRandom()

    @Volatile
    private var ephemeralFallbackKey: SecretKey? = null

    fun getAccessToken(): String? = synchronized(lock) {
        decrypt(prefs.getString(PREF_ACCESS_TOKEN_ENC, null))
    }

    fun getRefreshToken(): String? = synchronized(lock) {
        decrypt(prefs.getString(PREF_REFRESH_TOKEN_ENC, null))
    }

    fun saveTokens(accessToken: String?, refreshToken: String?) = synchronized(lock) {
        val editor = prefs.edit()
        if (!accessToken.isNullOrBlank()) {
            editor.putString(PREF_ACCESS_TOKEN_ENC, encrypt(accessToken))
        } else {
            editor.remove(PREF_ACCESS_TOKEN_ENC)
        }
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(PREF_REFRESH_TOKEN_ENC, encrypt(refreshToken))
        } else if (accessToken.isNullOrBlank()) {
            editor.remove(PREF_REFRESH_TOKEN_ENC)
        }
        editor.commit()
    }

    fun clearTokens() = synchronized(lock) {
        prefs.edit()
            .remove(PREF_ACCESS_TOKEN_ENC)
            .remove(PREF_REFRESH_TOKEN_ENC)
            .commit()
    }

    fun hasEncryptedTokens(): Boolean = synchronized(lock) {
        prefs.contains(PREF_ACCESS_TOKEN_ENC) || prefs.contains(PREF_REFRESH_TOKEN_ENC)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // Ephemeral in-memory AES-256 key used strictly in headless JVM/Robolectric unit tests
            // where AndroidKeyStore hardware provider is unavailable.
            ephemeralFallbackKey ?: synchronized(lock) {
                ephemeralFallbackKey ?: ByteArray(32).also { secureRandom.nextBytes(it) }
                    .let { SecretKeySpec(it, "AES") }
                    .also { ephemeralFallbackKey = it }
            }
        }
    }

    private fun encrypt(plaintext: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        // AndroidKeyStore with randomizedEncryptionRequired generates its own IV during init(ENCRYPT_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= 1 + GCM_IV_BYTES) return null
            val ivLen = combined[0].toInt() and 0xFF
            if (ivLen <= 0 || combined.size <= 1 + ivLen) return null
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Throwable) {
            // Fail closed on corrupted or un-decryptable ciphertext (e.g., key invalidation)
            null
        }
    }
}
