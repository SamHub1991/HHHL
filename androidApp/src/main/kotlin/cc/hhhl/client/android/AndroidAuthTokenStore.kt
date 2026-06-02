package cc.hhhl.client.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.auth.AuthTokenStore
import cc.hhhl.client.auth.decodeAccountSessions
import cc.hhhl.client.auth.encodeAccountSessions
import cc.hhhl.client.auth.legacyAccountSessionId
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAuthTokenStore(context: Context) : AuthTokenStore {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        appContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun readToken(): String? {
        val payload = readStoredString(KEY_TOKEN) ?: return null
        decryptStoredString(payload)?.let { return it }
        if (payload.looksEncryptedPayload()) return null

        val legacyToken = payload.trim().takeIf { it.isNotEmpty() } ?: return null
        writeEncryptedString(KEY_TOKEN, legacyToken)
        return legacyToken
    }

    override suspend fun saveToken(token: String) {
        writeEncryptedString(KEY_TOKEN, token)
    }

    override suspend fun clearToken() {
        clearLegacyToken()
    }

    override suspend fun readAccountSessions(): List<AccountSession> {
        val sessionsPayload = readStoredString(KEY_ACCOUNTS)
        if (!sessionsPayload.isNullOrBlank()) {
            val decodedPayload = decryptStoredString(sessionsPayload)
            when {
                decodedPayload != null -> decodeStoredAccountSessions(decodedPayload)?.let { return it }
                !sessionsPayload.looksEncryptedPayload() -> decodeStoredAccountSessions(sessionsPayload)?.let { sessions ->
                    saveAccountSessions(sessions)
                    return sessions
                }
            }
        }

        val legacyToken = readToken()?.trim().orEmpty()
        if (legacyToken.isEmpty()) return emptyList()

        val migrated = listOf(
            AccountSession(
                id = legacyAccountSessionId(legacyToken),
                user = null,
                token = legacyToken,
                current = true,
            ),
        )
        saveAccountSessions(migrated)
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(KEY_TOKEN)
                .commit()
        }
        return migrated
    }

    override suspend fun saveAccountSessions(sessions: List<AccountSession>) {
        if (sessions.isEmpty()) {
            clearAccountSessions()
            return
        }
        val payload = encodeAccountSessions(sessions)
        writeEncryptedString(KEY_ACCOUNTS, payload)
    }

    override suspend fun clearAccountSessions() {
        clearAllAccountPreferences()
    }

    private fun clearLegacyToken() {
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(KEY_TOKEN)
                .commit()
        }
    }

    private fun clearAllAccountPreferences() {
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(KEY_ACCOUNTS)
                .remove(KEY_TOKEN)
                .commit()
        }
    }

    private fun readStoredString(key: String): String? = synchronized(STORE_LOCK) {
        preferences.getString(key, null)
    }

    private fun decodeStoredAccountSessions(payload: String): List<AccountSession>? {
        return runCatching { decodeAccountSessions(payload) }.getOrNull()
    }

    private fun decryptStoredString(payload: String): String? {
        val parts = payload.split(DELIMITER, limit = 2)
        if (parts.size != 2) return null

        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun writeEncryptedString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            DELIMITER +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)

        synchronized(STORE_LOCK) {
            preferences.edit()
                .putString(key, payload)
                .commit()
        }
    }

    private fun String.looksEncryptedPayload(): Boolean {
        val parts = split(DELIMITER, limit = 2)
        if (parts.size != 2) return false
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            iv.isNotEmpty() && encrypted.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val STORE_NAME = "hhhl_auth"
        const val KEY_TOKEN = "session_token"
        const val KEY_ACCOUNTS = "account_sessions"
        const val KEY_ALIAS = "hhhl_auth_token_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        val STORE_LOCK = Any()
        const val DELIMITER = ":"
    }
}
