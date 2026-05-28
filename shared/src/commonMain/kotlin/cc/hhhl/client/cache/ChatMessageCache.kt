package cc.hhhl.client.cache

import cc.hhhl.client.api.toLocalCompactDateLabel
import cc.hhhl.client.model.AvatarDecoration
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ChatMessageCacheConversationType {
    Room,
    User,
}

data class ChatMessageCacheKey(
    val accountId: String,
    val type: ChatMessageCacheConversationType,
    val conversationId: String,
)

interface ChatMessageCache {
    suspend fun read(key: ChatMessageCacheKey): List<ChatMessage>

    suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>>

    suspend fun write(key: ChatMessageCacheKey, messages: List<ChatMessage>)

    suspend fun isComplete(key: ChatMessageCacheKey): Boolean

    suspend fun markComplete(key: ChatMessageCacheKey)

    suspend fun delete(key: ChatMessageCacheKey)

    suspend fun clearAccount(accountId: String)
}

object NoopChatMessageCache : ChatMessageCache {
    override suspend fun read(key: ChatMessageCacheKey): List<ChatMessage> = emptyList()

    override suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>> = emptyMap()

    override suspend fun write(key: ChatMessageCacheKey, messages: List<ChatMessage>) = Unit

    override suspend fun isComplete(key: ChatMessageCacheKey): Boolean = false

    override suspend fun markComplete(key: ChatMessageCacheKey) = Unit

    override suspend fun delete(key: ChatMessageCacheKey) = Unit

    override suspend fun clearAccount(accountId: String) = Unit
}

class InMemoryChatMessageCache : ChatMessageCache {
    private val snapshots = LinkedHashMap<ChatMessageCacheKey, List<ChatMessage>>()
    private val completeKeys = mutableSetOf<ChatMessageCacheKey>()
    private val mutex = Mutex()

    override suspend fun read(key: ChatMessageCacheKey): List<ChatMessage> {
        return mutex.withLock {
            snapshots[key].orEmpty()
        }
    }

    override suspend fun readAccount(accountId: String): Map<ChatMessageCacheKey, List<ChatMessage>> {
        return mutex.withLock {
            snapshots.filterKeys { it.accountId == accountId }
        }
    }

    override suspend fun write(key: ChatMessageCacheKey, messages: List<ChatMessage>) {
        mutex.withLock {
            snapshots.remove(key)
            snapshots[key] = messages.takeLast(MAX_CHAT_CACHE_MESSAGES_PER_CONVERSATION)
            trimChatCacheLocked()
        }
    }

    override suspend fun isComplete(key: ChatMessageCacheKey): Boolean {
        return mutex.withLock {
            key in completeKeys
        }
    }

    override suspend fun markComplete(key: ChatMessageCacheKey) {
        mutex.withLock {
            if (key in snapshots) completeKeys.add(key)
        }
    }

    override suspend fun delete(key: ChatMessageCacheKey) {
        mutex.withLock {
            snapshots.remove(key)
            completeKeys.remove(key)
        }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock {
            val removedKeys = snapshots.keys.filter { it.accountId == accountId }
            removedKeys.forEach { snapshots.remove(it) }
            completeKeys.removeAll { it.accountId == accountId }
        }
    }

    private fun trimChatCacheLocked() {
        while (snapshots.size > MAX_CHAT_CACHE_CONVERSATIONS) {
            val oldestKey = snapshots.keys.firstOrNull() ?: return
            snapshots.remove(oldestKey)
            completeKeys.remove(oldestKey)
        }
    }
}

object ChatMessageCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(snapshots: Map<ChatMessageCacheKey, List<ChatMessage>>): String {
        return json.encodeToString(
            ChatMessageCacheEnvelope(
                snapshots = snapshots.mapKeys { (key, _) -> key.storageKey }
                    .mapValues { (_, messages) ->
                        messages.takeLast(MAX_CHAT_CACHE_MESSAGES_PER_CONVERSATION).map { it.toCachedMessage() }
                    },
            ),
        )
    }

    fun decode(payload: String?): Map<ChatMessageCacheKey, List<ChatMessage>> {
        if (payload.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<ChatMessageCacheEnvelope>(payload)
                .snapshots
                .mapNotNull { (key, messages) ->
                    key.toChatMessageCacheKey()?.let { cacheKey ->
                        cacheKey to messages.takeLast(MAX_CHAT_CACHE_MESSAGES_PER_CONVERSATION)
                            .map { it.toDomainMessage() }
                    }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}

private val ChatMessageCacheKey.storageKey: String
    get() = listOf(accountId, type.name, conversationId).joinToString(KEY_SEPARATOR) { it.encodeKeyPart() }

private fun String.toChatMessageCacheKey(): ChatMessageCacheKey? {
    val parts = split(KEY_SEPARATOR)
    if (parts.size != 3) return null
    val accountId = parts[0].decodeKeyPart().takeIf { it.isNotBlank() } ?: return null
    val type = runCatching { ChatMessageCacheConversationType.valueOf(parts[1].decodeKeyPart()) }.getOrNull()
        ?: return null
    val conversationId = parts[2].decodeKeyPart().takeIf { it.isNotBlank() } ?: return null
    return ChatMessageCacheKey(
        accountId = accountId,
        type = type,
        conversationId = conversationId,
    )
}

private fun String.encodeKeyPart(): String {
    return replace("%", "%25").replace(KEY_SEPARATOR, "%7C")
}

private fun String.decodeKeyPart(): String {
    return replace("%7C", KEY_SEPARATOR).replace("%25", "%")
}

@Serializable
private data class ChatMessageCacheEnvelope(
    val version: Int = 1,
    val snapshots: Map<String, List<CachedChatMessage>> = emptyMap(),
)

@Serializable
private data class CachedChatMessage(
    val id: String,
    val roomId: String,
    val fromUser: CachedChatUser,
    val text: String,
    val createdAtLabel: String,
    val createdAt: String = "",
    val toUserId: String? = null,
    val toUser: CachedChatUser? = null,
    val isRead: Boolean = true,
    val file: CachedChatDriveFile? = null,
    val reactions: List<CachedChatMessageReaction> = emptyList(),
    val reactionCount: Int = reactions.sumOf { it.count },
    val reply: CachedChatMessageReference? = null,
    val quote: CachedChatMessageReference? = null,
    val replyUnavailable: Boolean = false,
    val quoteUnavailable: Boolean = false,
)

@Serializable
private data class CachedChatMessageReference(
    val id: String,
    val fromUser: CachedChatUser? = null,
    val text: String,
    val file: CachedChatDriveFile? = null,
    val unavailable: Boolean = false,
)

@Serializable
private data class CachedChatMessageReaction(
    val reaction: String,
    val count: Int,
    val users: List<CachedChatUser> = emptyList(),
)

@Serializable
private data class CachedChatUser(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarInitial: String,
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val host: String? = null,
    val avatarUrl: String? = null,
    val avatarDecorations: List<CachedAvatarDecoration> = emptyList(),
    val bannerUrl: String? = null,
)

@Serializable
private data class CachedAvatarDecoration(
    val url: String,
    val angle: Float = 0f,
    val flipH: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

@Serializable
private data class CachedChatDriveFile(
    val id: String,
    val name: String,
    val type: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val comment: String? = null,
    val size: Long = 0L,
    val isSensitive: Boolean = false,
    val createdAtLabel: String = "",
    val folderId: String? = null,
)

private fun ChatMessage.toCachedMessage(): CachedChatMessage {
    return CachedChatMessage(
        id = id,
        roomId = roomId,
        fromUser = fromUser.toCachedUser(),
        text = text,
        createdAtLabel = createdAtLabel,
        createdAt = createdAt,
        toUserId = toUserId,
        toUser = toUser?.toCachedUser(),
        isRead = isRead,
        file = file?.toCachedDriveFile(),
        reactions = reactions.map { it.toCachedReaction() },
        reactionCount = reactionCount,
        reply = reply?.toCachedReference(),
        quote = quote?.toCachedReference(),
        replyUnavailable = replyUnavailable,
        quoteUnavailable = quoteUnavailable,
    )
}

private fun CachedChatMessage.toDomainMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        roomId = roomId,
        fromUser = fromUser.toDomainUser(),
        text = text,
        createdAtLabel = createdAt.takeIf { it.isNotBlank() }?.toLocalCompactDateLabel() ?: createdAtLabel,
        createdAt = createdAt,
        toUserId = toUserId,
        toUser = toUser?.toDomainUser(),
        isRead = isRead,
        file = file?.toDomainDriveFile(),
        reactions = reactions.map { it.toDomainReaction() },
        reactionCount = reactionCount,
        reply = reply?.toDomainReference(),
        quote = quote?.toDomainReference(),
        replyUnavailable = replyUnavailable,
        quoteUnavailable = quoteUnavailable,
    )
}

private fun ChatMessageReference.toCachedReference(): CachedChatMessageReference {
    return CachedChatMessageReference(
        id = id,
        fromUser = fromUser?.toCachedUser(),
        text = text,
        file = file?.toCachedDriveFile(),
        unavailable = unavailable,
    )
}

private fun CachedChatMessageReference.toDomainReference(): ChatMessageReference {
    return ChatMessageReference(
        id = id,
        fromUser = fromUser?.toDomainUser(),
        text = text,
        file = file?.toDomainDriveFile(),
        unavailable = unavailable,
    )
}

private fun ChatMessageReaction.toCachedReaction(): CachedChatMessageReaction {
    return CachedChatMessageReaction(
        reaction = reaction,
        count = count,
        users = users.map { it.toCachedUser() },
    )
}

private fun CachedChatMessageReaction.toDomainReaction(): ChatMessageReaction {
    return ChatMessageReaction(
        reaction = reaction,
        count = count,
        users = users.map { it.toDomainUser() },
    )
}

private fun User.toCachedUser(): CachedChatUser {
    return CachedChatUser(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = avatarInitial,
        bio = bio,
        followersCount = followersCount,
        followingCount = followingCount,
        notesCount = notesCount,
        isFollowing = isFollowing,
        host = host,
        avatarUrl = avatarUrl,
        avatarDecorations = avatarDecorations.map { it.toCachedDecoration() },
        bannerUrl = bannerUrl,
    )
}

private fun CachedChatUser.toDomainUser(): User {
    return User(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = avatarInitial,
        bio = bio,
        followersCount = followersCount,
        followingCount = followingCount,
        notesCount = notesCount,
        isFollowing = isFollowing,
        host = host,
        avatarUrl = avatarUrl,
        avatarDecorations = avatarDecorations.map { it.toDomainDecoration() },
        bannerUrl = bannerUrl,
    )
}

private fun AvatarDecoration.toCachedDecoration(): CachedAvatarDecoration {
    return CachedAvatarDecoration(
        url = url,
        angle = angle,
        flipH = flipH,
        offsetX = offsetX,
        offsetY = offsetY,
    )
}

private fun CachedAvatarDecoration.toDomainDecoration(): AvatarDecoration {
    return AvatarDecoration(
        url = url,
        angle = angle,
        flipH = flipH,
        offsetX = offsetX,
        offsetY = offsetY,
    )
}

private fun DriveFile.toCachedDriveFile(): CachedChatDriveFile {
    return CachedChatDriveFile(
        id = id,
        name = name,
        type = type,
        url = url,
        thumbnailUrl = thumbnailUrl,
        comment = comment,
        size = size,
        isSensitive = isSensitive,
        createdAtLabel = createdAtLabel,
        folderId = folderId,
    )
}

private fun CachedChatDriveFile.toDomainDriveFile(): DriveFile {
    return DriveFile(
        id = id,
        name = name,
        type = type,
        url = url,
        thumbnailUrl = thumbnailUrl,
        comment = comment,
        size = size,
        isSensitive = isSensitive,
        createdAtLabel = createdAtLabel,
        folderId = folderId,
    )
}

private const val KEY_SEPARATOR = "|"
private const val MAX_CHAT_CACHE_CONVERSATIONS = 64
private const val MAX_CHAT_CACHE_MESSAGES_PER_CONVERSATION = 500
