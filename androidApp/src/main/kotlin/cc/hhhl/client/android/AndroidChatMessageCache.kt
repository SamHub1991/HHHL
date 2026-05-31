package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.cache.ChatMessageCache
import cc.hhhl.client.cache.ChatMessageCacheCodec
import cc.hhhl.client.cache.ChatMessageCacheKey
import cc.hhhl.client.model.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidChatMessageCache(context: Context) : ChatMessageCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private var snapshots: Map<ChatMessageCacheKey, List<ChatMessage>>? = null
    private var snapshotsPayload: String? = null
    private val mutex = Mutex()

    override suspend fun read(key: ChatMessageCacheKey): List<ChatMessage> {
        return mutex.withLock {
            loadedSnapshots()[key].orEmpty()
        }
    }

    override suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return mutex.withLock {
            loadedSnapshots().filterKeys { it.accountId == accountId }
        }
    }

    override suspend fun write(key: ChatMessageCacheKey, messages: List<ChatMessage>) {
        mutex.withLock {
            val nextSnapshots = (loadedSnapshots() + (key to messages))
                .trimmed()
            val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
            snapshots = nextSnapshots
            snapshotsPayload = nextPayload
            preferences.edit()
                .putString(KEY_SNAPSHOTS, nextPayload)
                .apply()
        }
    }

    override suspend fun isComplete(key: ChatMessageCacheKey): Boolean {
        return mutex.withLock {
            key.storageKey in completeStorageKeys()
        }
    }

    override suspend fun markComplete(key: ChatMessageCacheKey) {
        mutex.withLock {
            preferences.edit()
                .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageKeys() + key.storageKey)
                .apply()
        }
    }

    override suspend fun delete(key: ChatMessageCacheKey) {
        mutex.withLock {
            val nextSnapshots = loadedSnapshots() - key
            val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
            snapshots = nextSnapshots
            snapshotsPayload = nextPayload
            preferences.edit()
                .putString(KEY_SNAPSHOTS, nextPayload)
                .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageKeys() - key.storageKey)
                .apply()
        }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock {
            val nextSnapshots = loadedSnapshots().filterKeys { it.accountId != accountId }
            val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
            snapshots = nextSnapshots
            snapshotsPayload = nextPayload
            preferences.edit()
                .putString(KEY_SNAPSHOTS, nextPayload)
                .putStringSet(
                    KEY_COMPLETE_SNAPSHOTS,
                    completeStorageKeys().filterNot { it.startsWith("${accountId.encodeStorageKeyPart()}|") }.toSet(),
                )
                .apply()
        }
    }

    private fun loadedSnapshots(): Map<ChatMessageCacheKey, List<ChatMessage>> {
        val payload = preferences.getString(KEY_SNAPSHOTS, null)
        val currentSnapshots = snapshots
        if (currentSnapshots != null && payload == snapshotsPayload) return currentSnapshots
        return readSnapshots(payload).also {
            snapshots = it
            snapshotsPayload = payload
        }
    }

    private fun readSnapshots(payload: String?): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return ChatMessageCacheCodec.decode(payload).trimmed()
    }

    private fun completeStorageKeys(): Set<String> {
        return preferences.getStringSet(KEY_COMPLETE_SNAPSHOTS, emptySet()).orEmpty()
    }

    private fun Map<ChatMessageCacheKey, List<ChatMessage>>.trimmed(): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return entries
            .sortedByDescending { (_, messages) ->
                messages.maxOfOrNull { it.createdAt.ifBlank { it.createdAtLabel } }.orEmpty()
            }
            .take(MAX_CONVERSATIONS)
            .associate { (key, messages) -> key to messages }
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_chat_message_cache"
        const val KEY_SNAPSHOTS = "snapshots"
        const val KEY_COMPLETE_SNAPSHOTS = "complete_snapshots"
        const val MAX_CONVERSATIONS = 160
    }
}

private val ChatMessageCacheKey.storageKey: String
    get() = listOf(accountId, type.name, conversationId).joinToString("|") { it.encodeStorageKeyPart() }

private fun String.encodeStorageKeyPart(): String {
    return replace("%", "%25").replace("|", "%7C")
}
