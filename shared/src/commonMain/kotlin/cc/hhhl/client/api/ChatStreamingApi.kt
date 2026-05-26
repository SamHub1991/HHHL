package cc.hhhl.client.api

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
}

sealed interface ChatStreamingEvent {
    data object Connecting : ChatStreamingEvent

    data object Connected : ChatStreamingEvent

    data object Unauthorized : ChatStreamingEvent

    data class MessageReceived(val message: ChatMessage) : ChatStreamingEvent

    data class Error(val message: String) : ChatStreamingEvent

    data object Closed : ChatStreamingEvent
}

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
            emit(if (message.isUnauthorizedStreamingError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
            emit(ChatStreamingEvent.Closed)
            return@flow
        }

        try {
            emit(ChatStreamingEvent.Connected)
            session.send(Frame.Text(roomConnectPayload(cleanRoomId)))
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyStreamingChatEvent(frame.readText(), json)?.let { emit(it) }
                }
            }
            emit(ChatStreamingEvent.Closed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接中断"
            emit(if (message.isUnauthorizedStreamingError()) ChatStreamingEvent.Unauthorized else ChatStreamingEvent.Error(message))
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
}

internal fun parseSharkeyStreamingChatEvent(
    raw: String,
    json: Json = defaultChatStreamingJson,
): ChatStreamingEvent? {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    if (root.string("type") != "channel") return null
    val body = root.obj("body") ?: return null
    if (body.string("type") != "message") return null
    val messageElement = body["body"] ?: return null
    val message = runCatching {
        json.decodeFromJsonElement<StreamingChatMessageDto>(messageElement).toDomainMessage()
    }.getOrNull() ?: return null
    return ChatStreamingEvent.MessageReceived(message)
}

@Serializable
private data class StreamingChatMessageDto(
    val id: String,
    val createdAt: String,
    val fromUser: StreamingChatUserDto,
    val toRoomId: String,
    val text: String? = null,
    val file: StreamingChatDriveFileDto? = null,
    val reactions: List<StreamingChatMessageReactionDto> = emptyList(),
) {
    fun toDomainMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = toRoomId,
            fromUser = fromUser.toDomainUser(),
            text = text.orEmpty(),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            createdAt = createdAt,
            file = file?.toDomainFile(),
            reactions = reactions
                .groupingBy { it.reaction }
                .eachCount()
                .map { (reaction, count) -> ChatMessageReaction(reaction, count) }
                .sortedByDescending { it.count },
        )
    }
}

@Serializable
private data class StreamingChatDriveFileDto(
    val id: String,
    val name: String,
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
    val reaction: String,
    val user: StreamingChatUserDto? = null,
)

@Serializable
private data class StreamingChatUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val avatarUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonElement)?.jsonPrimitive?.contentOrNull
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun String.isUnauthorizedStreamingError(): Boolean {
    return contains("401") ||
        contains("Unauthorized", ignoreCase = true) ||
        contains("Invalid token", ignoreCase = true) ||
        contains("authentication", ignoreCase = true)
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
    explicitNulls = false
}
