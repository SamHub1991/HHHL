package cc.hhhl.client.api

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.model.AvatarDecoration
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface ChatStreamingApi {
    fun streamRoomMessages(
        token: String,
        roomId: String,
    ): Flow<ChatStreamingEvent>

    fun streamUserMessages(
        token: String,
        userId: String,
    ): Flow<ChatStreamingEvent>

    fun streamMessages(
        token: String,
        roomIds: List<String>,
        userIds: List<String>,
    ): Flow<ChatStreamingEvent> = flowOf(ChatStreamingEvent.Closed)
}

sealed interface ChatStreamingEvent {
    data object Connecting : ChatStreamingEvent

    data object Connected : ChatStreamingEvent

    data object Unauthorized : ChatStreamingEvent

    data class MessageReceived(
        val message: ChatMessage,
        val source: ChatStreamingMessageSource = ChatStreamingMessageSource(),
    ) : ChatStreamingEvent

    data class MessageDeleted(
        val messageId: String,
        val source: ChatStreamingMessageSource = ChatStreamingMessageSource(),
    ) : ChatStreamingEvent

    data class Error(val message: String) : ChatStreamingEvent

    data object Closed : ChatStreamingEvent
}

data class ChatStreamingMessageSource(
    val roomId: String? = null,
    val userId: String? = null,
)

class SharkeyChatStreamingApi(
    private val baseUrl: String = SharkeyChatApi.DEFAULT_BASE_URL,
    private val client: HttpClient = defaultChatStreamingClient(),
    private val json: Json = defaultChatStreamingJson,
) : ChatStreamingApi {
    override fun streamRoomMessages(
        token: String,
        roomId: String,
    ): Flow<ChatStreamingEvent> = flow {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        if (cleanToken.isEmpty()) {
            emit(ChatStreamingEvent.Unauthorized)
            emit(ChatStreamingEvent.Closed)
            return@flow
        }
        if (cleanRoomId.isEmpty()) {
            emit(ChatStreamingEvent.Error("请选择聊天室"))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        emit(ChatStreamingEvent.Connecting)
        val session = try {
            client.webSocketSession {
                url(streamingUrl(cleanToken))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接失败"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        try {
            emit(ChatStreamingEvent.Connected)
            session.send(Frame.Text(roomConnectPayload(cleanRoomId)))
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyStreamingChatEvents(frame.readText(), json).forEach { emit(it) }
                }
            }
            emit(ChatStreamingEvent.Closed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接中断"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
        } finally {
            runCatching { session.close() }
        }
    }

    override fun streamUserMessages(
        token: String,
        userId: String,
    ): Flow<ChatStreamingEvent> = flow {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) {
            emit(ChatStreamingEvent.Unauthorized)
            emit(ChatStreamingEvent.Closed)
            return@flow
        }
        if (cleanUserId.isEmpty()) {
            emit(ChatStreamingEvent.Error("请选择用户"))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        emit(ChatStreamingEvent.Connecting)
        val session = try {
            client.webSocketSession {
                url(streamingUrl(cleanToken))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接失败"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        try {
            emit(ChatStreamingEvent.Connected)
            session.send(Frame.Text(userConnectPayload(cleanUserId)))
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyStreamingChatEvents(frame.readText(), json).forEach { emit(it) }
                }
            }
            emit(ChatStreamingEvent.Closed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接中断"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
        } finally {
            runCatching { session.close() }
        }
    }

    override fun streamMessages(
        token: String,
        roomIds: List<String>,
        userIds: List<String>,
    ): Flow<ChatStreamingEvent> = flow {
        val cleanToken = token.trim()
        val cleanRoomIds = roomIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        val cleanUserIds = userIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (cleanToken.isEmpty()) {
            emit(ChatStreamingEvent.Unauthorized)
            emit(ChatStreamingEvent.Closed)
            return@flow
        }
        if (cleanRoomIds.isEmpty() && cleanUserIds.isEmpty()) {
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        emit(ChatStreamingEvent.Connecting)
        val session = try {
            client.webSocketSession {
                url(streamingUrl(cleanToken))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接失败"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        try {
            emit(ChatStreamingEvent.Connected)
            cleanRoomIds.forEach { roomId -> session.send(Frame.Text(roomConnectPayload(roomId))) }
            cleanUserIds.forEach { userId -> session.send(Frame.Text(userConnectPayload(userId))) }
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyStreamingChatEvents(frame.readText(), json).forEach { emit(it) }
                }
            }
            emit(ChatStreamingEvent.Closed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接中断"
            emit(if (message.isUnauthorizedStreamingTransportError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
        } finally {
            runCatching { session.close() }
        }
    }

    private fun streamingUrl(token: String): String {
        val builder = URLBuilder(baseUrl.trim().trimEnd('/'))
        builder.protocol = when (builder.protocol) {
            URLProtocol.HTTPS -> URLProtocol.WSS
            URLProtocol.HTTP -> URLProtocol.WS
            else -> builder.protocol
        }
        builder.appendPathSegments("streaming")
        builder.parameters.append("i", token)
        return builder.buildString()
    }

    private fun roomConnectPayload(roomId: String): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "connect")
                put(
                    "body",
                    buildJsonObject {
                        put("channel", "chatRoom")
                        put("id", "chat-room-$roomId")
                        put("pong", true)
                        put(
                            "params",
                            buildJsonObject {
                                put("roomId", roomId)
                            },
                        )
                    },
                )
            },
        )
    }

    private fun userConnectPayload(userId: String): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "connect")
                put(
                    "body",
                    buildJsonObject {
                        put("channel", "chatUser")
                        put("id", "chat-user-$userId")
                        put("pong", true)
                        put(
                            "params",
                            buildJsonObject {
                                put("otherId", userId)
                            },
                        )
                    },
                )
            },
        )
    }
}

internal fun parseSharkeyStreamingChatEvent(
    raw: String,
    json: Json = defaultChatStreamingJson,
): ChatStreamingEvent? {
    return parseSharkeyStreamingChatEvents(raw, json).firstOrNull()
}

internal fun parseSharkeyStreamingChatEvents(
    raw: String,
    json: Json = defaultChatStreamingJson,
): List<ChatStreamingEvent> {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    if (root.string("type") != "channel") return emptyList()
    val body = root.obj("body") ?: return emptyList()
    val eventType = body.string("type")
    val source = body.string("id").toChatStreamingMessageSource()
    val messageElement = body["body"] ?: return emptyList()
    if (eventType.isChatStreamingMessageDeletedEventType()) {
        val resolvedSource = messageElement.chatMessageDeletionSource(source)
        return messageElement.chatMessageDeletionIds()
            .map { messageId -> ChatStreamingEvent.MessageDeleted(messageId = messageId, source = resolvedSource) }
    }
    if (!eventType.isChatStreamingMessageEventType()) return emptyList()
    return messageElement.chatMessageElements()
        .mapNotNull { element -> parseSharkeyStreamingChatMessage(element, source, json) }
        .map { message -> ChatStreamingEvent.MessageReceived(message = message, source = source) }
}

private fun JsonElement.chatMessageElements(): List<JsonElement> {
    return when (this) {
        is JsonArray -> this.toList()
        is JsonObject -> {
            val wrappedMessages = listOfNotNull(
                this["message"],
                this["chatMessage"],
                this["messagingMessage"],
                this["messages"],
                this["chatMessages"],
                this["messagingMessages"],
                this["unreadChatMessages"],
                this["unreadMessagingMessages"],
            ).flatMap { element -> element.chatMessageElements() }
            wrappedMessages.ifEmpty { listOf(this) }
        }
        else -> listOf(this)
    }
}

internal fun JsonElement.chatMessageDeletionIds(): List<String> {
    return when (this) {
        is JsonArray -> flatMap { element -> element.chatMessageDeletionIds() }
        is JsonObject -> {
            val directIds = listOfNotNull(
                string("id"),
                string("messageId"),
                string("chatMessageId"),
                string("messagingMessageId"),
                string("deletedMessageId"),
                string("deletedChatMessageId"),
            )
            val nestedIds = listOfNotNull(
                this["message"],
                this["chatMessage"],
                this["messagingMessage"],
                this["deletedMessage"],
                this["deletedChatMessage"],
                this["deletedMessagingMessage"],
            ).flatMap { element -> element.chatMessageDeletionIds() }
            val arrayIds = listOfNotNull(
                this["ids"],
                this["messageIds"],
                this["chatMessageIds"],
                this["messagingMessageIds"],
                this["deletedMessageIds"],
            ).flatMap { element -> element.chatMessageDeletionIds() }
            (directIds + nestedIds + arrayIds)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
        else -> jsonPrimitive.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::listOf)
            .orEmpty()
    }
}

internal fun JsonElement.chatMessageDeletionSource(
    fallback: ChatStreamingMessageSource = ChatStreamingMessageSource(),
): ChatStreamingMessageSource {
    val body = this as? JsonObject ?: return fallback
    val nestedSources = listOfNotNull(
        body["message"],
        body["chatMessage"],
        body["messagingMessage"],
        body["deletedMessage"],
        body["deletedChatMessage"],
        body["deletedMessagingMessage"],
    ).map { element -> element.chatMessageDeletionSource() }
    val bodyRoomId = listOfNotNull(
        body.string("roomId"),
        body.string("toRoomId"),
        body.string("chatRoomId"),
    ).firstOrNull { it.isNotBlank() }
    val bodyUserId = listOfNotNull(
        body.string("userId"),
        body.string("otherId"),
        body.string("toUserId"),
        body.string("fromUserId"),
        body.string("chatUserId"),
    ).firstOrNull { it.isNotBlank() }
    return ChatStreamingMessageSource(
        roomId = fallback.roomId
            ?: bodyRoomId
            ?: nestedSources.firstNotNullOfOrNull { source -> source.roomId },
        userId = fallback.userId
            ?: bodyUserId
            ?: nestedSources.firstNotNullOfOrNull { source -> source.userId },
    )
}

private fun String?.isChatStreamingMessageEventType(): Boolean {
    return when (this) {
        "message",
        "chatMessage",
        "newChatMessage",
        "unreadChatMessage",
        "unreadChatMessages",
        "messagingMessage",
        "unreadMessagingMessage",
            -> true
        else -> false
    }
}

internal fun String?.isChatStreamingMessageDeletedEventType(): Boolean {
    return when (this) {
        "delete",
        "deleted",
        "messageDelete",
        "messageDeleted",
        "chatMessageDelete",
        "chatMessageDeleted",
        "deletedMessage",
        "deletedChatMessage",
        "messagingMessageDelete",
        "messagingMessageDeleted",
        "deletedMessagingMessage",
            -> true
        else -> false
    }
}

internal fun parseSharkeyStreamingChatMessage(
    element: JsonElement,
    source: ChatStreamingMessageSource = ChatStreamingMessageSource(),
    json: Json = defaultChatStreamingJson,
): ChatMessage? {
    return runCatching {
        json.decodeFromJsonElement<StreamingChatMessageDto>(element)
            .takeIf { it.hasPayload() }
            ?.toDomainMessage(source)
    }.getOrNull()
}

@Serializable
private data class StreamingChatMessageDto(
    val id: String = "",
    val createdAt: String = "",
    val fromUser: StreamingChatUserDto? = null,
    val fromUserId: String = "",
    val toUser: StreamingChatUserDto? = null,
    val toUserId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("roomId")
    val toRoomId: String? = null,
    val text: String? = null,
    val file: StreamingChatDriveFileDto? = null,
    val reactions: List<StreamingChatMessageReactionDto> = emptyList(),
    val replyId: String? = null,
    val reply: StreamingChatMessageReferenceDto? = null,
    val quoteId: String? = null,
    val quote: StreamingChatMessageReferenceDto? = null,
    val isRead: Boolean = true,
) {
    fun hasPayload(): Boolean {
        return id.isNotBlank() ||
            createdAt.isNotBlank() ||
            fromUser != null ||
            fromUserId.isNotBlank() ||
            toUser != null ||
            !toUserId.isNullOrBlank() ||
            !toRoomId.isNullOrBlank() ||
            text != null ||
            file != null ||
            replyId != null ||
            quoteId != null
    }

    fun toDomainMessage(source: ChatStreamingMessageSource = ChatStreamingMessageSource()): ChatMessage {
        val user = fromUser?.toDomainUser() ?: User(
            id = fromUserId.ifBlank { "unknown" },
            displayName = fromUserId.ifBlank { "成员" },
            username = fromUserId.ifBlank { "unknown" },
            avatarInitial = fromUserId.ifBlank { "成" }.avatarInitial(),
        )
        val effectiveRoomId = toRoomId?.takeIf { it.isNotBlank() }
            ?: source.roomId?.takeIf { it.isNotBlank() }
            ?: ""
        val effectiveToUserId = toUserId?.takeIf { it.isNotBlank() }
            ?: source.userId?.takeIf { effectiveRoomId.isBlank() && it.isNotBlank() && it != user.id }
        return ChatMessage(
            id = stableStreamingChatMessageId(
                id = id,
                roomId = effectiveRoomId,
                toUserId = effectiveToUserId.orEmpty(),
                fromUserId = user.id,
                createdAt = createdAt,
                text = text.orEmpty(),
                fileId = file?.id.orEmpty(),
            ),
            roomId = effectiveRoomId,
            fromUser = user,
            text = text.orEmpty(),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            createdAt = createdAt,
            toUserId = effectiveToUserId,
            toUser = toUser?.toDomainUser(),
            isRead = isRead,
            file = file?.toDomainFile(),
            reactions = reactions
                .groupBy { it.reaction }
                .map { (reaction, items) ->
                    ChatMessageReaction(
                        reaction = reaction,
                        count = items.size,
                        users = items.mapNotNull { it.user?.toDomainUser() },
                    )
                }
                .sortedByDescending { it.count },
            reply = reply?.toDomainReference(),
            quote = quote?.toDomainReference(),
            replyUnavailable = replyId != null && reply == null,
            quoteUnavailable = quoteId != null && quote == null,
        )
    }
}

@Serializable
private data class StreamingChatMessageReferenceDto(
    val id: String = "",
    val fromUser: StreamingChatUserDto? = null,
    val text: String? = null,
    val file: StreamingChatDriveFileDto? = null,
) {
    fun toDomainReference(): ChatMessageReference {
        return ChatMessageReference(
            id = id,
            fromUser = fromUser?.toDomainUser(),
            text = text.orEmpty(),
            file = file?.toDomainFile(),
        )
    }
}

@Serializable
private data class StreamingChatDriveFileDto(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val comment: String? = null,
    val size: Long = 0,
    val isSensitive: Boolean = false,
) {
    fun toDomainFile(): DriveFile {
        return DriveFile(
            id = id,
            name = name,
            type = type,
            url = url,
            thumbnailUrl = thumbnailUrl,
            comment = comment,
            size = size,
            isSensitive = isSensitive,
        )
    }
}

@Serializable
private data class StreamingChatMessageReactionDto(
    val reaction: String = "",
    val user: StreamingChatUserDto? = null,
)

@Serializable
private data class StreamingChatUserDto(
    val id: String = "",
    val username: String = "",
    val name: String? = null,
    val avatarUrl: String? = null,
    val avatarDecorations: List<StreamingAvatarDecorationDto> = emptyList(),
) {
    fun toDomainUser(): User {
        val stableUsername = username.ifBlank { id.ifBlank { "unknown" } }
        val displayName = name?.takeIf { it.isNotBlank() } ?: stableUsername
        return User(
            id = id.ifBlank { stableUsername },
            displayName = displayName,
            username = stableUsername,
            avatarInitial = displayName.avatarInitial(),
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            avatarDecorations = avatarDecorations.mapNotNull { it.toDomainDecoration() },
        )
    }
}

@Serializable
private data class StreamingAvatarDecorationDto(
    val url: String? = null,
    val angle: Float = 0f,
    val flipH: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun toDomainDecoration(): AvatarDecoration? {
        val cleanUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return AvatarDecoration(
            url = cleanUrl,
            angle = angle,
            flipH = flipH,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonElement)?.jsonPrimitive?.contentOrNull
}

private fun String?.toChatStreamingMessageSource(): ChatStreamingMessageSource {
    val cleanId = this?.trim().orEmpty()
    return when {
        cleanId.startsWith("chat-room-") -> ChatStreamingMessageSource(
            roomId = cleanId.removePrefix("chat-room-").takeIf { it.isNotBlank() },
        )
        cleanId.startsWith("chat-user-") -> ChatStreamingMessageSource(
            userId = cleanId.removePrefix("chat-user-").takeIf { it.isNotBlank() },
        )
        else -> ChatStreamingMessageSource()
    }
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun stableStreamingChatMessageId(
    id: String,
    roomId: String,
    toUserId: String,
    fromUserId: String,
    createdAt: String,
    text: String,
    fileId: String,
): String {
    val cleanId = id.trim()
    if (cleanId.isNotEmpty()) return cleanId
    val seed = listOf(roomId, toUserId, fromUserId, createdAt, text, fileId)
        .joinToString(separator = "\u0000")
    return "local-chat-${seed.stableStreamingChatHash()}"
}

private fun String.stableStreamingChatHash(): String {
    var hash = 1125899906842597L
    for (char in this) {
        hash = 31L * hash + char.code
    }
    return hash.toULong().toString(36)
}

internal fun String.isUnauthorizedStreamingTransportError(): Boolean {
    val cleanMessage = trim()
    if (cleanMessage.isBlank()) return false
    return unauthorizedStreamingStatusPattern.containsMatchIn(cleanMessage) ||
        cleanMessage.equals("Unauthorized", ignoreCase = true) ||
        cleanMessage.contains("Invalid token", ignoreCase = true)
}

private fun defaultChatStreamingClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(WebSockets)
        install(ContentNegotiation) {
            json(defaultChatStreamingJson)
        }
    }
}

private val defaultChatStreamingJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

private val unauthorizedStreamingStatusPattern = Regex("""(?<!\d)401(?!\d)""")
