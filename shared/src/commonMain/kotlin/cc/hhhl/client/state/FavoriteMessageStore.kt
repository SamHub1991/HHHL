package cc.hhhl.client.state

import cc.hhhl.client.cache.ChatMessageCacheCodec
import cc.hhhl.client.model.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class FavoriteMessageConversationType(val label: String) {
    Room("聊天室"),
    User("私聊"),
}

data class FavoriteMessage(
    val id: String,
    val accountId: String,
    val conversationType: FavoriteMessageConversationType,
    val conversationId: String,
    val conversationTitle: String,
    val message: ChatMessage,
    val savedAtEpochMillis: Long,
    val savedAtLabel: String = "刚刚",
)

interface FavoriteMessageStore {
    suspend fun read(accountId: String): List<FavoriteMessage>

    suspend fun save(accountId: String, messages: List<FavoriteMessage>)

    suspend fun clearAccount(accountId: String)
}

object NoopFavoriteMessageStore : FavoriteMessageStore {
    override suspend fun read(accountId: String): List<FavoriteMessage> = emptyList()

    override suspend fun save(accountId: String, messages: List<FavoriteMessage>) = Unit

    override suspend fun clearAccount(accountId: String) = Unit
}

class InMemoryFavoriteMessageStore : FavoriteMessageStore {
    private val snapshots = mutableMapOf<String, List<FavoriteMessage>>()
    private val mutex = Mutex()

    override suspend fun read(accountId: String): List<FavoriteMessage> {
        return mutex.withLock { snapshots[accountId].orEmpty() }
    }

    override suspend fun save(accountId: String, messages: List<FavoriteMessage>) {
        mutex.withLock { snapshots[accountId] = messages.trimmedFavoriteMessages() }
    }

    override suspend fun clearAccount(accountId: String) {
        mutex.withLock { snapshots.remove(accountId) }
    }
}

object FavoriteMessageStoreCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(messages: List<FavoriteMessage>): String {
        return json.encodeToString(
            FavoriteMessageEnvelope(
                messages = messages.trimmedFavoriteMessages().map { it.toCachedFavoriteMessage() },
            ),
        )
    }

    fun decode(payload: String?): List<FavoriteMessage> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<FavoriteMessageEnvelope>(payload)
                .messages
                .mapNotNull { it.toDomainFavoriteMessage() }
                .trimmedFavoriteMessages()
        }.getOrDefault(emptyList())
    }
}

fun favoriteMessageId(
    accountId: String,
    conversationType: FavoriteMessageConversationType,
    conversationId: String,
    messageId: String,
): String {
    return listOf(accountId, conversationType.name, conversationId, messageId)
        .joinToString("|") { it.replace("%", "%25").replace("|", "%7C") }
}

fun List<FavoriteMessage>.trimmedFavoriteMessages(): List<FavoriteMessage> {
    return sortedByDescending { it.savedAtEpochMillis }
        .distinctBy { it.id }
        .take(MAX_FAVORITE_MESSAGES)
}

@Serializable
private data class FavoriteMessageEnvelope(
    val version: Int = 1,
    val messages: List<CachedFavoriteMessage> = emptyList(),
)

@Serializable
private data class CachedFavoriteMessage(
    val id: String,
    val accountId: String,
    val conversationType: String,
    val conversationId: String,
    val conversationTitle: String,
    val messagePayload: String,
    val savedAtEpochMillis: Long,
    val savedAtLabel: String = "刚刚",
)

private fun FavoriteMessage.toCachedFavoriteMessage(): CachedFavoriteMessage {
    return CachedFavoriteMessage(
        id = id,
        accountId = accountId,
        conversationType = conversationType.name,
        conversationId = conversationId,
        conversationTitle = conversationTitle,
        messagePayload = ChatMessageCacheCodec.encodeMessages(listOf(message)),
        savedAtEpochMillis = savedAtEpochMillis,
        savedAtLabel = savedAtLabel,
    )
}

private fun CachedFavoriteMessage.toDomainFavoriteMessage(): FavoriteMessage? {
    val type = runCatching { FavoriteMessageConversationType.valueOf(conversationType) }.getOrNull()
        ?: return null
    val message = ChatMessageCacheCodec.decodeMessages(messagePayload).firstOrNull() ?: return null
    if (id.isBlank() || accountId.isBlank()) return null
    return FavoriteMessage(
        id = id,
        accountId = accountId,
        conversationType = type,
        conversationId = conversationId,
        conversationTitle = conversationTitle,
        message = message,
        savedAtEpochMillis = savedAtEpochMillis,
        savedAtLabel = savedAtLabel,
    )
}

private const val MAX_FAVORITE_MESSAGES = 500
