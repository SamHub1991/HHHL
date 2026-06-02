package cc.hhhl.client.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cc.hhhl.client.ai.AiSnapshot
import cc.hhhl.client.ai.AiStore
import cc.hhhl.client.ai.AiStoreCodec
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAiStore(context: Context) : AiStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        STORE_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(accountId: String): AiSnapshot = synchronized(STORE_LOCK) {
        readLocked(accountId)
    }

    override fun write(accountId: String, snapshot: AiSnapshot) = synchronized(STORE_LOCK) {
        writeLocked(accountId, snapshot)
    }

    override fun clearAccount(accountId: String) {
        synchronized(STORE_LOCK) {
            preferences.edit()
                .remove(keyFor(accountId))
                .commit()
        }
    }

    override fun update(accountId: String, transform: (AiSnapshot) -> AiSnapshot): AiSnapshot = synchronized(STORE_LOCK) {
        val updated = transform(readLocked(accountId))
        writeLocked(accountId, updated)
        updated
    }

    private fun readLocked(accountId: String): AiSnapshot {
        val storageName = keyFor(accountId)
        val encryptedPayload = preferences.getString(storageName, null) ?: return AiSnapshot()
        decrypt(encryptedPayload)?.let { payload ->
            return AiStoreCodec.decode(payload)
        }
        if (encryptedPayload.startsWith(ENCRYPTED_PREFIX)) return AiSnapshot()

        val legacySnapshot = AiStoreCodec.decode(encryptedPayload)
        if (encryptedPayload.isNotBlank()) {
            writeLocked(accountId, legacySnapshot)
        }
        return legacySnapshot
    }

    private fun writeLocked(accountId: String, snapshot: AiSnapshot) {
        val payload = encrypt(AiStoreCodec.encode(snapshot))
        preferences.edit()
            .putString(keyFor(accountId), payload)
            .commit()
    }

    private fun keyFor(accountId: String): String = "ai:$accountId"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return ENCRYPTED_PREFIX +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            DELIMITER +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? {
        if (!payload.startsWith(ENCRYPTED_PREFIX)) return null
        val encryptedPayload = payload.removePrefix(ENCRYPTED_PREFIX)
        val parts = encryptedPayload.split(DELIMITER, limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
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
        const val STORE_NAME = "hhhl_ai"
        const val KEY_ALIAS = "hhhl_ai_settings_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val ENCRYPTED_PREFIX = "gcm:v1:"
        val STORE_LOCK = Any()
        const val DELIMITER = ":"
    }
}
