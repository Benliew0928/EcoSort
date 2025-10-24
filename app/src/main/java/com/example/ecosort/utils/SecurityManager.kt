package com.example.ecosort.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurityManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "EcoSortKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    // ==================== PASSWORD HASHING ====================

    fun hashPassword(password: String): String {
        val salt = generateSalt()
        val hash = hashWithSalt(password, salt)
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 2) return false

            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val hash = Base64.decode(parts[1], Base64.NO_WRAP)
            val testHash = hashWithSalt(password, salt)

            MessageDigest.isEqual(hash, testHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(32)
        random.nextBytes(salt)
        return salt
    }

    private fun hashWithSalt(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    // ==================== ENCRYPTION ====================

    fun encryptData(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptData(encryptedData: String): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

        val iv = ByteArray(12)
        val encrypted = ByteArray(combined.size - 12)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            createKey()
        }
    }

    private fun createKey(): SecretKey {
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
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // ==================== TOKEN GENERATION ====================

    fun generateSessionToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ==================== VALIDATION ====================

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() }
    }

    fun isValidUsername(username: String): Boolean {
        return username.matches(Regex("^[a-zA-Z0-9_]{3,20}$"))
    }

    // ==================== ADMIN PASSKEY MANAGEMENT ====================

    private const val ADMIN_PASSKEY_PREF = "admin_passkey"
    private const val DEFAULT_ADMIN_PASSKEY = "8888"

    fun verifyAdminPasskey(inputPasskey: String): Boolean {
        val storedPasskey = getStoredAdminPasskey()
        return inputPasskey == storedPasskey
    }

    private fun getStoredAdminPasskey(): String {
        return try {
            val context = getApplicationContext()
            if (context != null) {
                val prefs = context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
                val encryptedPasskey = prefs.getString(ADMIN_PASSKEY_PREF, null)
                if (encryptedPasskey != null) {
                    decryptData(encryptedPasskey) ?: DEFAULT_ADMIN_PASSKEY
                } else {
                    DEFAULT_ADMIN_PASSKEY
                }
            } else {
                DEFAULT_ADMIN_PASSKEY
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Error getting admin passkey: ${e.message}")
            DEFAULT_ADMIN_PASSKEY
        }
    }

    fun setAdminPasskey(newPasskey: String): Boolean {
        return try {
            val context = getApplicationContext()
            if (context != null) {
                val encryptedPasskey = encryptData(newPasskey)
                if (encryptedPasskey.isNotEmpty()) {
                    val prefs = context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString(ADMIN_PASSKEY_PREF, encryptedPasskey).apply()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Error setting admin passkey: ${e.message}")
            false
        }
    }

    fun isAdminPasskeyCustomized(): Boolean {
        return try {
            val context = getApplicationContext()
            if (context != null) {
                val prefs = context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
                prefs.contains(ADMIN_PASSKEY_PREF)
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Error checking admin passkey: ${e.message}")
            false
        }
    }

    fun resetAdminPasskeyToDefault(): Boolean {
        return try {
            val context = getApplicationContext()
            if (context != null) {
                val prefs = context.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove(ADMIN_PASSKEY_PREF).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Error resetting admin passkey: ${e.message}")
            false
        }
    }

    private fun getApplicationContext(): android.content.Context? {
        return try {
            com.example.ecosort.EcoSortApplication.getContext()
        } catch (e: Exception) {
            android.util.Log.e("SecurityManager", "Error getting application context: ${e.message}")
            null
        }
    }
}

// Extension functions for convenience
fun String.hashPassword(): String = SecurityManager.hashPassword(this)
fun String.verifyPassword(hash: String): Boolean = SecurityManager.verifyPassword(this, hash)