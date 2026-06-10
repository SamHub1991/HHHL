package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.ChatMessage
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

interface MainStreamingApi {
    fun streamMain(token: String): Flow<MainStreamingEvent>

    fun streamMain(token: String, options: MainStreamingOptions): Flow<MainStreamingEvent> = streamMain(token)
}

data class MainStreamingOptions(
    val timelineKinds: List<TimelineKind> = defaultMainStreamingTimelineKinds,
    val channelIds: List<String> = emptyList(),
)

sealed interface MainStreamingEvent {
    data object Connecting : MainStreamingEvent

    data object Connected : MainStreamingEvent

    data object Unauthorized : MainStreamingEvent

    data object UnreadNotification : MainStreamingEvent

    data class NotificationReceived(
        val notification: NotificationItem,
    ) : MainStreamingEvent

    data object ReadAllNotifications : MainStreamingEvent

    data object NewChatMessage : MainStreamingEvent

    data class ChatMessageReceived(
        val message: ChatMessage,
    ) : MainStreamingEvent

    data class ChatMessageDeleted(
        val messageId: String,
        val source: ChatStreamingMessageSource = ChatStreamingMessageSource(),
    ) : MainStreamingEvent

    data class TimelineNote(
        val kind: TimelineKind,
        val note: Note? = null,
        val timelineSource: String = kind.name,
    ) : MainStreamingEvent

    data class Error(val message: String) : MainStreamingEvent

    data object Closed : MainStreamingEvent
}

class SharkeyMainStreamingApi(
    private val baseUrl: String = SharkeyChatApi.DEFAULT_BASE_URL,
    private val client: HttpClient = defaultMainStreamingClient(),
    private val json: Json = defaultMainStreamingJson,
) : MainStreamingApi {
    override fun streamMain(token: String): Flow<MainStreamingEvent> = streamMain(
        token = token,
        options = MainStreamingOptions(),
    )

    override fun streamMain(token: String, options: MainStreamingOptions): Flow<MainStreamingEvent> = flow {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            emit(MainStreamingEvent.Unauthorized)
            emit(MainStreamingEvent.Closed)
            return@flow
        }

        emit(MainStreamingEvent.Connecting)
        val session = try {
            client.webSocketSession {
                url(streamingUrl(cleanToken))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接失败"
            emit(if (message.isUnauthorizedStreamingTransportError()) MainStreamingEvent.Unauthorized else MainStreamingEvent.Error(message))
            emit(MainStreamingEvent.Closed)
            return@flow
        }

        try {
            emit(MainStreamingEvent.Connected)
            streamingConnectPayloads(options).forEach { payload ->
                session.send(Frame.Text(payload))
            }
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyMainStreamingEvents(frame.readText(), json).forEach { emit(it) }
                }
            }
            emit(MainStreamingEvent.Closed)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "实时连接中断"
            emit(if (message.isUnauthorizedStreamingTransportError()) MainStreamingEvent.Unauthorized else MainStreamingEvent.Error(message))
            emit(MainStreamingEvent.Closed)
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

    private fun streamingConnectPayloads(options: MainStreamingOptions): List<String> {
        return buildList {
            add(connectPayload(channel = "main", id = MAIN_STREAM_ID))
            options.timelineKinds
                .distinct()
                .forEach { kind ->
                    kind.streamingChannelName?.let { channel ->
                        add(connectPayload(channel = channel, id = kind.streamingStreamId))
                    }
                }
            options.channelIds
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .forEach { channelId ->
                    add(
                        connectPayload(
                            channel = "channel",
                            id = channelId.channelStreamingStreamId(),
                            params = buildJsonObject {
                                put("channelId", channelId)
                            },
                        ),
                    )
                }
        }
    }

    private fun connectPayload(
        channel: String,
        id: String,
        params: JsonObject? = null,
    ): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "connect")
                put(
                    "body",
                    buildJsonObject {
                        put("channel", channel)
                        put("id", id)
                        put("pong", true)
                        params?.let { put("params", it) }
                    },
                )
            },
        )
    }
}

internal fun parseSharkeyMainStreamingEvent(
    raw: String,
    json: Json = defaultMainStreamingJson,
): MainStreamingEvent? {
    return parseSharkeyMainStreamingEvents(raw, json).firstOrNull()
}

internal fun parseSharkeyMainStreamingEvents(
    raw: String,
    json: Json = defaultMainStreamingJson,
): List<MainStreamingEvent> {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    val rootType = root.string("type") ?: return emptyList()
    if (rootType == "connected") return listOf(MainStreamingEvent.Connected)
    if (rootType != "channel") return emptyList()

    val body = root.obj("body") ?: return emptyList()
    val channelId = body.string("id").orEmpty()
    val eventType = body.string("type")
    channelId.toTimelineKindOrNull()?.let { kind ->
        return listOfNotNull(
            if (eventType == "note") {
                MainStreamingEvent.TimelineNote(
                    kind = kind,
                    note = body.obj("body")?.toStreamingNoteOrNull(json),
                    timelineSource = kind.name,
                )
            } else {
                null
            },
        )
    }
    channelId.toStreamingChannelIdOrNull()?.let { streamChannelId ->
        return listOfNotNull(
            if (eventType == "note") {
                MainStreamingEvent.TimelineNote(
                    kind = TimelineKind.Home,
                    note = body.obj("body")
                        ?.toStreamingNoteOrNull(json)
                        ?.let { note -> note.copy(channelId = note.channelId.ifBlank { streamChannelId }) },
                    timelineSource = "Channel",
                )
            } else {
                null
            },
        )
    }
    if (channelId.isNotBlank() && channelId != MAIN_STREAM_ID) return emptyList()
    val notification = body.obj("body")?.toStreamingNotificationOrNull(json)
    if (eventType.isChatStreamingMessageDeletedEventType()) {
        val messageElement = body["body"] ?: return listOf(MainStreamingEvent.NewChatMessage)
        val source = messageElement.chatMessageDeletionSource()
        val messageIds = messageElement.chatMessageDeletionIds()
        return if (messageIds.isEmpty()) {
            listOf(MainStreamingEvent.NewChatMessage)
        } else {
            messageIds.map { messageId -> MainStreamingEvent.ChatMessageDeleted(messageId = messageId, source = source) }
        }
    }
    return when (eventType) {
        "notification" -> listOf(
            notification?.let { MainStreamingEvent.NotificationReceived(it) }
                ?: MainStreamingEvent.UnreadNotification,
        )
        "unreadNotification" -> listOf(
            notification?.let { MainStreamingEvent.NotificationReceived(it) }
                ?: MainStreamingEvent.UnreadNotification,
        )
        "unreadMention",
        "unreadSpecifiedNote",
        "unreadAntenna",
            -> listOf(MainStreamingEvent.UnreadNotification)
        "readAllNotifications" -> listOf(MainStreamingEvent.ReadAllNotifications)
        "readAllUnreadNotifications" -> listOf(MainStreamingEvent.ReadAllNotifications)
        "newChatMessage",
        "chatMessage",
        "unreadChatMessage",
        "unreadChatMessages",
        "messagingMessage",
        "unreadMessagingMessage",
        "readAllChatMessages",
        "readAllMessagingMessages",
            -> {
            val messageElement = body["body"] ?: return listOf(MainStreamingEvent.NewChatMessage)
            val messages = messageElement.mainStreamingChatMessageElements()
                .mapNotNull { element -> parseSharkeyStreamingChatMessage(element, json = json) }
            if (messages.isEmpty()) {
                listOf(MainStreamingEvent.NewChatMessage)
            } else {
                messages.map { message -> MainStreamingEvent.ChatMessageReceived(message) }
            }
        }
        else -> emptyList()
    }
}

private fun JsonElement.mainStreamingChatMessageElements(): List<JsonElement> {
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
            ).flatMap { element -> element.mainStreamingChatMessageElements() }
            wrappedMessages.ifEmpty { listOf(this) }
        }
        else -> listOf(this)
    }
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.toStreamingNoteOrNull(json: Json): Note? {
    return runCatching { json.decodeFromJsonElement<SharkeyNoteDto>(this).toDomainNote() }
        .getOrNull()
}

private fun JsonObject.toStreamingNotificationOrNull(json: Json): NotificationItem? {
    return runCatching { json.decodeFromJsonElement<NotificationDto>(this).toDomainNotification() }
        .getOrNull()
}

private fun defaultMainStreamingClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(WebSockets)
        install(ContentNegotiation) {
            json(defaultMainStreamingJson)
        }
    }
}

private const val MAIN_STREAM_ID = "main"

val defaultMainStreamingTimelineKinds: List<TimelineKind> = listOf(
    TimelineKind.Home,
    TimelineKind.Social,
    TimelineKind.Local,
    TimelineKind.Global,
)

private val TimelineKind.streamingChannelName: String?
    get() = when (this) {
        TimelineKind.Home -> "homeTimeline"
        TimelineKind.Social -> "hybridTimeline"
        TimelineKind.Local -> "localTimeline"
        TimelineKind.Global -> "globalTimeline"
        TimelineKind.Bubble,
        TimelineKind.Featured,
        TimelineKind.Mentions,
        -> null
    }

private val TimelineKind.streamingStreamId: String
    get() = when (this) {
        TimelineKind.Home -> "timeline-home"
        TimelineKind.Social -> "timeline-social"
        TimelineKind.Local -> "timeline-local"
        TimelineKind.Global -> "timeline-global"
        TimelineKind.Bubble -> "timeline-bubble"
        TimelineKind.Featured -> "timeline-featured"
        TimelineKind.Mentions -> "timeline-mentions"
    }

private fun String.toTimelineKindOrNull(): TimelineKind? {
    return TimelineKind.entries.firstOrNull { kind -> this == kind.streamingStreamId }
}

private fun String.channelStreamingStreamId(): String = "channel:$this"

private fun String.toStreamingChannelIdOrNull(): String? {
    return removePrefix("channel:").takeIf { it != this && it.isNotBlank() }
}

private val defaultMainStreamingJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
