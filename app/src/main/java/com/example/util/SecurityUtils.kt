package com.example.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object SecurityUtils {

    private const val AES_GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * Encrypts a plaintext string using AES/GCM/NoPadding with a derived key from password.
     */
    fun encrypt(plainText: String, passwordKey: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(passwordKey.toByteArray(StandardCharsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            // Combined format: IV (12 bytes) + CipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * Decrypts an AES/GCM base64 encoded string using passwordKey.
     */
    fun decrypt(cipherBase64: String, passwordKey: String): String {
        return try {
            val combined = Base64.decode(cipherBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return cipherBase64

            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)

            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(passwordKey.toByteArray(StandardCharsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            cipherBase64
        }
    }

    /**
     * Generates a strong random password.
     */
    fun generateStrongPassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()-_=+[]{}<>?"

        val charPool = StringBuilder(lower)
        if (includeUppercase) charPool.append(upper)
        if (includeNumbers) charPool.append(numbers)
        if (includeSymbols) charPool.append(symbols)

        val poolStr = charPool.toString()
        val random = java.security.SecureRandom()
        val result = StringBuilder()

        for (i in 0 until length) {
            val idx = random.nextInt(poolStr.length)
            result.append(poolStr[idx])
        }
        return result.toString()
    }

    /**
     * Scores a password from 1 (Weak) to 4 (Very Strong).
     */
    fun evaluateStrength(password: String): Int {
        if (password.length < 6) return 1
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 1 -> 1
            score == 2 -> 2
            score in 3..4 -> 3
            else -> 4
        }
    }
}
