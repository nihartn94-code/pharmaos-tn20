package com.example.util

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH = 16

    data class HashResult(
        val hashHex: String,
        val saltHex: String
    )

    fun hashPassword(password: String): HashResult {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return HashResult(
            hashHex = bytesToHex(hash),
            saltHex = bytesToHex(salt)
        )
    }

    fun verifyPassword(password: String, storedHashHex: String, storedSaltHex: String): Boolean {
        val salt = hexToBytes(storedSaltHex)
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val computedHashHex = bytesToHex(hash)
        return constantTimeEquals(computedHashHex, storedHashHex)
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, keyLength)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
