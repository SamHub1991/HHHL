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

    override suspend fun read(cacheEntry: ChatMessageCacheKey): List<ChatMessage> {
        return mutex.withLock {
            synchronized(STORE_LOCK) {
                loadedSnapshots()[cacheEntry].orEmpty()
            }
        }
    }

    override suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return mutex.withLock {
            synchronized(STORE_LOCK) {
                loadedSnapshots().filterKeys { it.accountId == accountId }
            }
        }
    }

    override suspend fun write(cacheEntry: ChatMessageCacheKey, messages: List<ChatMessage>) {
        mutex.withLock {
            synchronized(STORE_LOCK) {
                val nextSnapshots = (loadedSnapshots() + (cacheEntry to messages))
                    .trimmed()
                val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
                snapshots = nextSnapshots
                snapshotsPayload = nextPayload
                preferences.edit()
                    .putString(KEY_SNAPSHOTS, nextPayload)
                    .commit()
            }
        }
    }

    override suspend fun isComplete(cacheEntry: ChatMessageCacheKey): Boolean {
        return mutex.withLock {
            synchronized(STORE_LOCK) {
                cacheEntry.storageEntry in completeStorageEntries()
            }
        }
    }

    override suspend fun markComplete(cacheEntry: ChatMessageCacheKey) {
        mutex.withLock {
            synchronized(STORE_LOCK) {
                preferences.edit()
                    .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() + cacheEntry.storageEntry)
                    .commit()
            }
        }
    }

    override suspend fun delete(cacheEntry: ChatMessageCacheKey) {
        mutex.withLock {
            synchronized(STORE_LOCK) {
                val nextSnapshots = loadedSnapshots() - cacheEntry
                val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
                snapshots = nextSnapshots
                snapshotsPayload = nextPayload
                preferences.edit()
                    .putString(KEY_SNAPSHOTS, nextPayload)
                    .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() - cacheEntry.storageEntry)
                    .commit()
            }
        }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock {
            synchronized(STORE_LOCK) {
                val nextSnapshots = loadedSnapshots().filterKeys { it.accountId != accountId }
                val nextPayload = ChatMessageCacheCodec.encode(nextSnapshots)
                snapshots = nextSnapshots
                snapshotsPayload = nextPayload
                preferences.edit()
                    .putString(KEY_SNAPSHOTS, nextPayload)
                    .putStringSet(
                        KEY_COMPLETE_SNAPSHOTS,
                        completeStorageEntries().filterNot { it.startsWith("${accountId.encodeStorageEntryPart()}|") }.toSet(),
                    )
                    .commit()
            }
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

    private fun completeStorageEntries(): Set<String> {
        return preferences.getStringSet(KEY_COMPLETE_SNAPSHOTS, emptySet()).orEmpty()
    }

    private fun Map<ChatMessageCacheKey, List<ChatMessage>>.trimmed(): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return entries
            .sortedByDescending { (_, messages) ->
                messages.maxOfOrNull { it.createdAt.ifBlank { it.createdAtLabel } }.orEmpty()
            }
            .take(MAX_CONVERSATIONS)
            .associate { (entry, messages) -> entry to messages }
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_chat_message_cache"
        const val KEY_SNAPSHOTS = "snapshots"
        const val KEY_COMPLETE_SNAPSHOTS = "complete_snapshots"
        const val MAX_CONVERSATIONS = 160
        val STORE_LOCK = Any()
    }
}

private val ChatMessageCacheKey.storageEntry: String
    get() = listOf(accountId, type.name, conversationId).joinToString("|") { it.encodeStorageEntryPart() }

private fun String.encodeStorageEntryPart(): String {
    return replace("%", "%25").replace("|", "%7C")
}
