package cc.hhhl.client.api

import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.User
import cc.hhhl.client.model.DriveFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ChatApi {
    suspend fun loadJoiningRooms(
        token: String,
        limit: Int,
        untilId: String? = null,
    ): ChatRoomLoadResult

    suspend fun loadRoomMessages(
        token: String,
        roomId: String,
        limit: Int,
        untilId: String? = null,
    ): ChatMessageLoadResult

    suspend fun loadRoomMembers(
        token: String,
        roomId: String,
        limit: Int,
        untilId: String? = null,
    ): ChatRoomMemberLoadResult

    suspend fun createRoomMessage(
        token: String,
        roomId: String,
        text: String,
        fileId: String? = null,
    ): ChatMessageCreateResult

    suspend fun reactToMessage(
        token: String,
        messageId: String,
        reaction: String,
    ): ChatMessageReactionResult

    suspend fun unreactToMessage(
        token: String,
        messageId: String,
        reaction: String,
    ): ChatMessageReactionResult
}

sealed interface ChatRoomLoadResult {
    data class Success(val rooms: List<ChatRoom>) : ChatRoomLoadResult

    data object Unauthorized : ChatRoomLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatRoomLoadResult

    data class NetworkError(val message: String) : ChatRoomLoadResult
}

sealed interface ChatMessageLoadResult {
    data class Success(val messages: List<ChatMessage>) : ChatMessageLoadResult

    data object Unauthorized : ChatMessageLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatMessageLoadResult

    data class NetworkError(val message: String) : ChatMessageLoadResult
}

sealed interface ChatRoomMemberLoadResult {
    data class Success(val members: List<ChatRoomMember>) : ChatRoomMemberLoadResult

    data object Unauthorized : ChatRoomMemberLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatRoomMemberLoadResult

    data class NetworkError(val message: String) : ChatRoomMemberLoadResult
}

sealed interface ChatMessageCreateResult {
    data class Success(val message: ChatMessage) : ChatMessageCreateResult

    data object Unauthorized : ChatMessageCreateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatMessageCreateResult

    data class NetworkError(val message: String) : ChatMessageCreateResult
}

sealed interface ChatMessageReactionResult {
    data object Success : ChatMessageReactionResult

    data object Unauthorized : ChatMessageReactionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatMessageReactionResult

    data class NetworkError(val message: String) : ChatMessageReactionResult
}

class SharkeyChatApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultChatClient(),
) : ChatApi {
    override suspend fun loadJoiningRooms(
        token: String,
        limit: Int,
        untilId: String?,
    ): ChatRoomLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ChatRoomLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("chat", "rooms", "joining")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatJoiningRoomsRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> ChatRoomLoadResult.Success(
                    response.body<List<ChatRoomMembershipDto>>().map { it.toDomainRoom() },
                )
                HttpStatusCode.Unauthorized -> ChatRoomLoadResult.Unauthorized
                else -> ChatRoomLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRoomMessages(
        token: String,
        roomId: String,
        limit: Int,
        untilId: String?,
    ): ChatMessageLoadResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        if (cleanToken.isEmpty()) return ChatMessageLoadResult.Unauthorized
        if (cleanRoomId.isEmpty()) {
            return ChatMessageLoadResult.ServerError(400, "请选择聊天室")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "room-timeline")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRoomMessagesRequest(
                        i = cleanToken,
                        roomId = cleanRoomId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> ChatMessageLoadResult.Success(
                    response.body<List<ChatMessageDto>>().map { it.toDomainMessage() },
                )
                HttpStatusCode.Unauthorized -> ChatMessageLoadResult.Unauthorized
                else -> ChatMessageLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatMessageLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createRoomMessage(
        token: String,
        roomId: String,
        text: String,
        fileId: String?,
    ): ChatMessageCreateResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        val cleanText = text.trim()
        if (cleanToken.isEmpty()) return ChatMessageCreateResult.Unauthorized
        if (cleanRoomId.isEmpty()) {
            return ChatMessageCreateResult.ServerError(400, "请选择聊天室")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "create-to-room")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatCreateRoomMessageRequest(
                        i = cleanToken,
                        toRoomId = cleanRoomId,
                        text = cleanText.takeIf { it.isNotEmpty() },
                        fileId = fileId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> ChatMessageCreateResult.Success(
                    response.body<ChatMessageDto>().toDomainMessage(),
                )
                HttpStatusCode.Unauthorized -> ChatMessageCreateResult.Unauthorized
                else -> ChatMessageCreateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatMessageCreateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRoomMembers(
        token: String,
        roomId: String,
        limit: Int,
        untilId: String?,
    ): ChatRoomMemberLoadResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        if (cleanToken.isEmpty()) return ChatRoomMemberLoadResult.Unauthorized
        if (cleanRoomId.isEmpty()) {
            return ChatRoomMemberLoadResult.ServerError(400, "请选择聊天室")
        }

        return try {
            val response = client.post(apiUrl("chat", "rooms", "members")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRoomMembersRequest(
                        i = cleanToken,
                        roomId = cleanRoomId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> ChatRoomMemberLoadResult.Success(
                    response.body<List<ChatRoomMembershipDto>>().map { it.toDomainMember() },
                )
                HttpStatusCode.Unauthorized -> ChatRoomMemberLoadResult.Unauthorized
                else -> ChatRoomMemberLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomMemberLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun reactToMessage(
        token: String,
        messageId: String,
        reaction: String,
    ): ChatMessageReactionResult {
        return sendMessageReaction(
            endpoint = "react",
            token = token,
            messageId = messageId,
            reaction = reaction,
        )
    }

    override suspend fun unreactToMessage(
        token: String,
        messageId: String,
        reaction: String,
    ): ChatMessageReactionResult {
        return sendMessageReaction(
            endpoint = "unreact",
            token = token,
            messageId = messageId,
            reaction = reaction,
        )
    }

    private suspend fun sendMessageReaction(
        endpoint: String,
        token: String,
        messageId: String,
        reaction: String,
    ): ChatMessageReactionResult {
        val cleanToken = token.trim()
        val cleanMessageId = messageId.trim()
        val cleanReaction = reaction.trim()
        if (cleanToken.isEmpty()) return ChatMessageReactionResult.Unauthorized
        if (cleanMessageId.isEmpty() || cleanReaction.isEmpty()) {
            return ChatMessageReactionResult.ServerError(400, "请选择消息和回应")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatMessageReactionRequest(
                        i = cleanToken,
                        messageId = cleanMessageId,
                        reaction = cleanReaction,
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> ChatMessageReactionResult.Success
                HttpStatusCode.Unauthorized -> ChatMessageReactionResult.Unauthorized
                else -> ChatMessageReactionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatMessageReactionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private data class ChatJoiningRoomsRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class ChatRoomMessagesRequest(
    val i: String,
    val roomId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class ChatRoomMembersRequest(
    val i: String,
    val roomId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class ChatCreateRoomMessageRequest(
    val i: String,
    val toRoomId: String,
    val text: String? = null,
    val fileId: String? = null,
)

@Serializable
private data class ChatMessageReactionRequest(
    val i: String,
    val messageId: String,
    val reaction: String,
)

@Serializable
private data class ChatRoomMembershipDto(
    val id: String,
    val createdAt: String = "",
    val userId: String = "",
    val roomId: String,
    val user: ChatUserDto? = null,
    val room: ChatRoomDto? = null,
    val unreadCount: Int = 0,
) {
    fun toDomainRoom(): ChatRoom {
        val domainRoom = room?.toDomainRoom(membershipId = id, membershipUnreadCount = unreadCount)
        return domainRoom ?: ChatRoom(
            id = roomId,
            membershipId = id,
            name = "聊天室",
            description = "",
            joinMode = "",
            memberCount = 0,
            isMuted = false,
            owner = systemUser,
            unreadCount = unreadCount.coerceAtLeast(0),
        )
    }

    fun toDomainMember(): ChatRoomMember {
        val memberUser = user?.toDomainUser() ?: User(
            id = userId.ifBlank { "unknown" },
            displayName = userId.ifBlank { "成员" },
            username = userId.ifBlank { "unknown" },
            avatarInitial = userId.ifBlank { "成" }.avatarInitial(),
        )
        return ChatRoomMember(
            membershipId = id,
            roomId = roomId,
            user = memberUser,
            joinedAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class ChatRoomDto(
    val id: String,
    val owner: ChatUserDto? = null,
    val name: String = "聊天室",
    val description: String = "",
    val joinMode: String = "",
    val memberCount: Int = 0,
    val isMuted: Boolean = false,
    val unreadCount: Int = 0,
) {
    fun toDomainRoom(
        membershipId: String,
        membershipUnreadCount: Int = 0,
    ): ChatRoom {
        return ChatRoom(
            id = id,
            membershipId = membershipId,
            name = name,
            description = description,
            joinMode = joinMode,
            memberCount = memberCount,
            isMuted = isMuted,
            owner = owner?.toDomainUser() ?: systemUser,
            unreadCount = maxOf(unreadCount, membershipUnreadCount).coerceAtLeast(0),
        )
    }
}

@Serializable
private data class ChatMessageDto(
    val id: String,
    val createdAt: String,
    val fromUser: ChatUserDto,
    val toRoomId: String,
    val text: String? = null,
    val file: ChatDriveFileDto? = null,
    val reactions: List<ChatMessageReactionDto> = emptyList(),
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
private data class ChatDriveFileDto(
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
private data class ChatMessageReactionDto(
    val reaction: String,
    val user: ChatUserDto,
)

@Serializable
private data class ChatUserDto(
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

@Serializable
private data class ChatErrorEnvelope(
    val error: ChatErrorDto? = null,
)

@Serializable
private data class ChatErrorDto(
    val message: String? = null,
)

private val systemUser = User(
    id = "system",
    displayName = "系统",
    username = "system",
    avatarInitial = "系",
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun defaultChatClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
