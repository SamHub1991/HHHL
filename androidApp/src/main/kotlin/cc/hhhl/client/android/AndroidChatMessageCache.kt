package cc.hhhl.client.android

import android.content.Context
import cc.hhhl.client.cache.ChatMessageCache
import cc.hhhl.client.cache.ChatMessageCacheCodec
import cc.hhhl.client.cache.ChatMessageCacheConversationType
import cc.hhhl.client.cache.ChatMessageCacheKey
import cc.hhhl.client.model.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidChatMessageCache(context: Context) : ChatMessageCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val snapshots = LinkedHashMap<ChatMessageCacheKey, List<ChatMessage>>()
    private val mutex = Mutex()

    override suspend fun read(key: ChatMessageCacheKey): List<ChatMessage> {
        return mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            snapshots[key] ?: readConversationMessagesLocked(key).also { messages ->
                if (messages.isNotEmpty()) snapshots[key] = messages
            }
        }
    }

    override suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            val cleanAccountId = accountId.trim()
            if (cleanAccountId.isEmpty()) return@withLock emptyMap()
            val keys = conversationKeysLocked()
                .filter { key -> key.accountId == cleanAccountId }
            keys.associateWith { key ->
                snapshots[key] ?: readConversationMessagesLocked(key).also { messages ->
                    if (messages.isNotEmpty()) snapshots[key] = messages
                }
            }.filterValues { messages -> messages.isNotEmpty() }
        }
    }

    override suspend fun write(key: ChatMessageCacheKey, messages: List<ChatMessage>) {
        mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            val trimmedMessages = messages.takeLast(MAX_MESSAGES_PER_CONVERSATION)
            snapshots[key] = trimmedMessages
            preferences.edit()
                .putString(key.snapshotPreferenceKey, ChatMessageCacheCodec.encodeMessages(trimmedMessages))
                .putString(KEY_SNAPSHOT_ORDER, snapshotOrderWithMostRecentLocked(key.storageEntry))
                .apply()
            trimConversationKeysLocked()
        }
    }

    override suspend fun isComplete(key: ChatMessageCacheKey): Boolean {
        return mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            key.storageEntry in completeStorageEntries()
        }
    }

    override suspend fun markComplete(key: ChatMessageCacheKey) {
        mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            preferences.edit()
                .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() + key.storageEntry)
                .apply()
        }
    }

    override suspend fun delete(key: ChatMessageCacheKey) {
        mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            snapshots.remove(key)
            preferences.edit()
                .remove(key.snapshotPreferenceKey)
                .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() - key.storageEntry)
                .putString(KEY_SNAPSHOT_ORDER, snapshotOrderWithoutLocked(key.storageEntry))
                .apply()
        }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock {
            dropLegacyMonolithicCacheLocked()
            val cleanAccountId = accountId.trim()
            if (cleanAccountId.isEmpty()) return@withLock
            val removedKeys = conversationKeysLocked()
                .filter { key -> key.accountId == cleanAccountId }
            if (removedKeys.isEmpty()) return@withLock
            removedKeys.forEach { key -> snapshots.remove(key) }
            val removedEntries = removedKeys.mapTo(HashSet(removedKeys.size)) { key -> key.storageEntry }
            val editor = preferences.edit()
            removedKeys.forEach { key -> editor.remove(key.snapshotPreferenceKey) }
            editor
                .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() - removedEntries)
                .putString(KEY_SNAPSHOT_ORDER, snapshotOrderWithoutLocked(removedEntries))
                .apply()
        }
    }

    private fun readConversationMessagesLocked(key: ChatMessageCacheKey): List<ChatMessage> {
        return ChatMessageCacheCodec.decodeMessages(preferences.getString(key.snapshotPreferenceKey, null))
            .takeLast(MAX_MESSAGES_PER_CONVERSATION)
    }

    private fun conversationKeysLocked(): List<ChatMessageCacheKey> {
        return preferences.all.keys
            .asSequence()
            .mapNotNull { key -> key.removePrefixOrNull(KEY_SNAPSHOT_PREFIX) }
            .mapNotNull { entry -> entry.toChatMessageCacheKey() }
            .toList()
    }

    private fun snapshotOrderWithMostRecentLocked(storageEntry: String): String {
        val entries = snapshotOrderLocked()
            .filterNot { entry -> entry == storageEntry }
            .toMutableList()
        entries += storageEntry
        return entries.joinToString("\n")
    }

    private fun snapshotOrderWithoutLocked(storageEntry: String): String {
        return snapshotOrderWithoutLocked(setOf(storageEntry))
    }

    private fun snapshotOrderWithoutLocked(storageEntries: Set<String>): String {
        return snapshotOrderLocked()
            .filterNot { entry -> entry in storageEntries }
            .joinToString("\n")
    }

    private fun snapshotOrderLocked(): List<String> {
        return preferences.getString(KEY_SNAPSHOT_ORDER, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    private fun trimConversationKeysLocked() {
        val keysByEntry = conversationKeysLocked().associateBy { key -> key.storageEntry }
        if (keysByEntry.size <= MAX_CONVERSATIONS) return
        val orderedEntries = snapshotOrderLocked()
        val unorderedEntries = keysByEntry.keys - orderedEntries.toSet()
        val entriesByRemovalPriority = unorderedEntries.toList() + orderedEntries
        val removedEntries = entriesByRemovalPriority
            .take((keysByEntry.size - MAX_CONVERSATIONS).coerceAtLeast(0))
            .toSet()
        if (removedEntries.isEmpty()) return
        val editor = preferences.edit()
        removedEntries.forEach { entry ->
            keysByEntry[entry]?.let { key ->
                snapshots.remove(key)
                editor.remove(key.snapshotPreferenceKey)
            }
        }
        editor
            .putStringSet(KEY_COMPLETE_SNAPSHOTS, completeStorageEntries() - removedEntries)
            .putString(KEY_SNAPSHOT_ORDER, snapshotOrderWithoutLocked(removedEntries))
            .apply()
    }

    private fun dropLegacyMonolithicCacheLocked() {
        if (!preferences.contains(KEY_LEGACY_SNAPSHOTS)) return
        preferences.edit()
            .remove(KEY_LEGACY_SNAPSHOTS)
            .apply()
    }

    private fun completeStorageEntries(): Set<String> {
        return preferences.getStringSet(KEY_COMPLETE_SNAPSHOTS, emptySet()).orEmpty()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhhl_chat_message_cache"
        const val KEY_LEGACY_SNAPSHOTS = "snapshots"
        const val KEY_COMPLETE_SNAPSHOTS = "complete_snapshots"
        const val KEY_SNAPSHOT_ORDER = "snapshot_order"
        const val KEY_SNAPSHOT_PREFIX = "snapshot:"
        const val MAX_CONVERSATIONS = 64
        const val MAX_MESSAGES_PER_CONVERSATION = 320
    }
}

private val ChatMessageCacheKey.storageEntry: String
    get() = listOf(accountId, type.name, conversationId).joinToString("|") { it.encodeStorageEntryPart() }

private val ChatMessageCacheKey.snapshotPreferenceKey: String
    get() = "snapshot:$storageEntry"

private fun String.toChatMessageCacheKey(): ChatMessageCacheKey? {
    val parts = split("|")
    if (parts.size != 3) return null
    val accountId = parts[0].decodeStorageEntryPart().takeIf { it.isNotBlank() } ?: return null
    val type = runCatching { ChatMessageCacheConversationType.valueOf(parts[1].decodeStorageEntryPart()) }.getOrNull()
        ?: return null
    val conversationId = parts[2].decodeStorageEntryPart().takeIf { it.isNotBlank() } ?: return null
    return ChatMessageCacheKey(accountId = accountId, type = type, conversationId = conversationId)
}

private fun String.removePrefixOrNull(prefix: String): String? {
    return if (startsWith(prefix)) removePrefix(prefix) else null
}

private fun String.encodeStorageEntryPart(): String {
    return replace("%", "%25").replace("|", "%7C")
}

private fun String.decodeStorageEntryPart(): String {
    return replace("%7C", "|").replace("%25", "%")
}
