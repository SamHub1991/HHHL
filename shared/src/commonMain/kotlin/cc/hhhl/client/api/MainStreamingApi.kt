package cc.hhhl.client.api

import cc.hhhl.client.model.Note
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface MainStreamingApi {
    fun streamMain(token: String): Flow<MainStreamingEvent>
}

sealed interface MainStreamingEvent {
    data object Connecting : MainStreamingEvent

    data object Connected : MainStreamingEvent

    data object Unauthorized : MainStreamingEvent

    data object UnreadNotification : MainStreamingEvent

    data object ReadAllNotifications : MainStreamingEvent

    data object NewChatMessage : MainStreamingEvent

    data class TimelineNote(
        val kind: TimelineKind,
        val note: Note? = null,
    ) : MainStreamingEvent

    data class Error(val message: String) : MainStreamingEvent

    data object Closed : MainStreamingEvent
}

class SharkeyMainStreamingApi(
    private val baseUrl: String = SharkeyChatApi.DEFAULT_BASE_URL,
    private val client: HttpClient = defaultMainStreamingClient(),
    private val json: Json = defaultMainStreamingJson,
) : MainStreamingApi {
    override fun streamMain(token: String): Flow<MainStreamingEvent> = flow {
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
            streamingConnectPayloads().forEach { payload ->
                session.send(Frame.Text(payload))
            }
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    parseSharkeyMainStreamingEvent(frame.readText(), json)?.let { emit(it) }
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

    private fun streamingConnectPayloads(): List<String> {
        return listOf(
            connectPayload(channel = "main", id = MAIN_STREAM_ID),
            connectPayload(channel = "homeTimeline", id = TimelineKind.Home.streamingStreamId),
            connectPayload(channel = "hybridTimeline", id = TimelineKind.Social.streamingStreamId),
            connectPayload(channel = "localTimeline", id = TimelineKind.Local.streamingStreamId),
            connectPayload(channel = "globalTimeline", id = TimelineKind.Global.streamingStreamId),
        )
    }

    private fun connectPayload(channel: String, id: String): String {
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
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val rootType = root.string("type") ?: return null
    if (rootType == "connected") return MainStreamingEvent.Connected
    if (rootType != "channel") return null

    val body = root.obj("body") ?: return null
    val channelId = body.string("id").orEmpty()
    val eventType = body.string("type")
    channelId.toTimelineKindOrNull()?.let { kind ->
        return if (eventType == "note") {
            MainStreamingEvent.TimelineNote(
                kind = kind,
                note = body.obj("body")?.toStreamingNoteOrNull(json),
            )
        } else {
            null
        }
    }
    if (channelId.isNotBlank() && channelId != MAIN_STREAM_ID) return null
    return when (eventType) {
        "notification" -> MainStreamingEvent.UnreadNotification
        "unreadNotification" -> MainStreamingEvent.UnreadNotification
        "readAllNotifications" -> MainStreamingEvent.ReadAllNotifications
        "readAllUnreadNotifications" -> MainStreamingEvent.ReadAllNotifications
        "newChatMessage" -> MainStreamingEvent.NewChatMessage
        "chatMessage" -> MainStreamingEvent.NewChatMessage
        else -> null
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

private val defaultMainStreamingJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
