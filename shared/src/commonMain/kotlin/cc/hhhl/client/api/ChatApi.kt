package cc.hhhl.client.api

import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.User
import cc.hhhl.client.model.AvatarDecoration
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

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

    suspend fun showRoom(
        token: String,
        roomId: String,
    ): ChatRoomMutationResult

    suspend fun loadUserHistory(
        token: String,
        limit: Int,
    ): ChatUserHistoryLoadResult

    suspend fun loadUserMessages(
        token: String,
        userId: String,
        limit: Int,
        untilId: String? = null,
    ): ChatMessageLoadResult

    suspend fun searchMessages(
        token: String,
        query: String,
        limit: Int,
        untilId: String? = null,
        roomId: String? = null,
        userId: String? = null,
    ): ChatMessageLoadResult

    suspend fun createRoom(
        token: String,
        name: String,
        description: String = "",
    ): ChatRoomMutationResult

    suspend fun createRoomMessage(
        token: String,
        roomId: String,
        text: String,
        fileId: String? = null,
        fileIds: List<String> = emptyList(),
        replyId: String? = null,
        quoteId: String? = null,
    ): ChatMessageCreateResult

    suspend fun createUserMessage(
        token: String,
        userId: String,
        text: String,
        fileId: String? = null,
        replyId: String? = null,
        quoteId: String? = null,
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

    suspend fun deleteMessage(
        token: String,
        messageId: String,
    ): ChatMessageDeleteResult

    suspend fun updateRoom(
        token: String,
        roomId: String,
        name: String,
        description: String,
    ): ChatRoomMutationResult

    suspend fun inviteRoomMember(
        token: String,
        roomId: String,
        userId: String,
    ): ChatRoomActionResult

    suspend fun joinRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult

    suspend fun leaveRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult

    suspend fun deleteRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult

    suspend fun muteRoom(
        token: String,
        roomId: String,
        muted: Boolean,
    ): ChatRoomActionResult
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

sealed interface ChatUserHistoryLoadResult {
    data class Success(val messages: List<ChatMessage>) : ChatUserHistoryLoadResult

    data object Unauthorized : ChatUserHistoryLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatUserHistoryLoadResult

    data class NetworkError(val message: String) : ChatUserHistoryLoadResult
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

sealed interface ChatMessageDeleteResult {
    data object Success : ChatMessageDeleteResult

    data object Unauthorized : ChatMessageDeleteResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatMessageDeleteResult

    data class NetworkError(val message: String) : ChatMessageDeleteResult
}

sealed interface ChatRoomMutationResult {
    data class Success(val room: ChatRoom) : ChatRoomMutationResult

    data object Unauthorized : ChatRoomMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatRoomMutationResult

    data class NetworkError(val message: String) : ChatRoomMutationResult
}

sealed interface ChatRoomActionResult {
    data object Success : ChatRoomActionResult

    data object Unauthorized : ChatRoomActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChatRoomActionResult

    data class NetworkError(val message: String) : ChatRoomActionResult
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

            if (response.isSharkeyUnauthorized()) return ChatRoomLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> {
                    val rooms = response.body<List<ChatRoomMembershipDto>>().map { it.toDomainRoom() }
                    ChatRoomLoadResult.Success(rooms.withHistoryUnreadFallback(cleanToken))
                }
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

            if (response.isSharkeyUnauthorized()) return ChatMessageLoadResult.Unauthorized
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
        fileIds: List<String>,
        replyId: String?,
        quoteId: String?,
    ): ChatMessageCreateResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        val cleanText = text.trim()
        val cleanFileIds = (fileIds + listOfNotNull(fileId))
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
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
                        fileId = cleanFileIds.firstOrNull(),
                        fileIds = cleanFileIds.takeIf { it.size > 1 },
                        replyId = replyId?.trim()?.takeIf { it.isNotEmpty() },
                        quoteId = quoteId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatMessageCreateResult.Unauthorized
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

    override suspend fun loadUserHistory(
        token: String,
        limit: Int,
    ): ChatUserHistoryLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ChatUserHistoryLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("chat", "history")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatHistoryRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        room = false,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatUserHistoryLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> ChatUserHistoryLoadResult.Success(
                    response.body<List<ChatMessageDto>>().map { it.toDomainMessage() },
                )
                HttpStatusCode.Unauthorized -> ChatUserHistoryLoadResult.Unauthorized
                else -> ChatUserHistoryLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatUserHistoryLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadUserMessages(
        token: String,
        userId: String,
        limit: Int,
        untilId: String?,
    ): ChatMessageLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return ChatMessageLoadResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return ChatMessageLoadResult.ServerError(400, "请选择用户")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "user-timeline")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatUserMessagesRequest(
                        i = cleanToken,
                        userId = cleanUserId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatMessageLoadResult.Unauthorized
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

    override suspend fun createUserMessage(
        token: String,
        userId: String,
        text: String,
        fileId: String?,
        replyId: String?,
        quoteId: String?,
    ): ChatMessageCreateResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        val cleanText = text.trim()
        val cleanFileId = fileId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanToken.isEmpty()) return ChatMessageCreateResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return ChatMessageCreateResult.ServerError(400, "请选择用户")
        }
        if (cleanText.isBlank() && cleanFileId == null) {
            return ChatMessageCreateResult.ServerError(400, "请输入消息")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "create-to-user")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatCreateUserMessageRequest(
                        i = cleanToken,
                        toUserId = cleanUserId,
                        text = cleanText.takeIf { it.isNotEmpty() },
                        fileId = cleanFileId,
                        replyId = replyId?.trim()?.takeIf { it.isNotEmpty() },
                        quoteId = quoteId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatMessageCreateResult.Unauthorized
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

    override suspend fun searchMessages(
        token: String,
        query: String,
        limit: Int,
        untilId: String?,
        roomId: String?,
        userId: String?,
    ): ChatMessageLoadResult {
        val cleanToken = token.trim()
        val cleanQuery = query.trim()
        if (cleanToken.isEmpty()) return ChatMessageLoadResult.Unauthorized
        if (cleanQuery.isEmpty()) {
            return ChatMessageLoadResult.ServerError(400, "请输入搜索关键词")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "search")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatMessageSearchRequest(
                        i = cleanToken,
                        query = cleanQuery,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        roomId = roomId?.trim()?.takeIf { it.isNotEmpty() },
                        userId = userId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatMessageLoadResult.Unauthorized
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

    override suspend fun createRoom(
        token: String,
        name: String,
        description: String,
    ): ChatRoomMutationResult {
        val cleanToken = token.trim()
        val cleanName = name.trim()
        if (cleanToken.isEmpty()) return ChatRoomMutationResult.Unauthorized
        if (cleanName.isEmpty()) {
            return ChatRoomMutationResult.ServerError(400, "请输入聊天室名称")
        }

        return try {
            val response = client.post(apiUrl("chat", "rooms", "create")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRoomCreateRequest(
                        i = cleanToken,
                        name = cleanName,
                        description = description.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatRoomMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> ChatRoomMutationResult.Success(
                    response.body<ChatRoomDto>().toDomainRoom(),
                )
                HttpStatusCode.Unauthorized -> ChatRoomMutationResult.Unauthorized
                else -> ChatRoomMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showRoom(
        token: String,
        roomId: String,
    ): ChatRoomMutationResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        if (cleanToken.isEmpty()) return ChatRoomMutationResult.Unauthorized
        if (cleanRoomId.isEmpty()) {
            return ChatRoomMutationResult.ServerError(400, "请选择聊天室")
        }

        return try {
            val response = client.post(apiUrl("chat", "rooms", "show")) {
                contentType(ContentType.Application.Json)
                setBody(ChatRoomIdRequest(i = cleanToken, roomId = cleanRoomId))
            }

            if (response.isSharkeyUnauthorized()) return ChatRoomMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> ChatRoomMutationResult.Success(
                    response.body<ChatRoomDto>().toDomainRoom(),
                )
                HttpStatusCode.Unauthorized -> ChatRoomMutationResult.Unauthorized
                else -> ChatRoomMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomMutationResult.NetworkError(error.message ?: "网络请求失败")
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

            if (response.isSharkeyUnauthorized()) return ChatRoomMemberLoadResult.Unauthorized
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

    override suspend fun deleteMessage(
        token: String,
        messageId: String,
    ): ChatMessageDeleteResult {
        val cleanToken = token.trim()
        val cleanMessageId = messageId.trim()
        if (cleanToken.isEmpty()) return ChatMessageDeleteResult.Unauthorized
        if (cleanMessageId.isEmpty()) {
            return ChatMessageDeleteResult.ServerError(400, "请选择消息")
        }

        return try {
            val response = client.post(apiUrl("chat", "messages", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(ChatMessageDeleteRequest(i = cleanToken, messageId = cleanMessageId))
            }

            if (response.isSharkeyUnauthorized()) return ChatMessageDeleteResult.Unauthorized
            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> ChatMessageDeleteResult.Success
                HttpStatusCode.Unauthorized -> ChatMessageDeleteResult.Unauthorized
                else -> ChatMessageDeleteResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatMessageDeleteResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateRoom(
        token: String,
        roomId: String,
        name: String,
        description: String,
    ): ChatRoomMutationResult {
        val cleanToken = token.trim()
        val cleanRoomId = roomId.trim()
        val cleanName = name.trim()
        if (cleanToken.isEmpty()) return ChatRoomMutationResult.Unauthorized
        if (cleanRoomId.isEmpty()) {
            return ChatRoomMutationResult.ServerError(400, "请选择聊天室")
        }
        if (cleanName.isEmpty()) {
            return ChatRoomMutationResult.ServerError(400, "请输入聊天室名称")
        }

        return try {
            val response = client.post(apiUrl("chat", "rooms", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRoomUpdateRequest(
                        i = cleanToken,
                        roomId = cleanRoomId,
                        name = cleanName,
                        description = description.trim(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChatRoomMutationResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> ChatRoomMutationResult.Success(
                    response.body<ChatRoomDto>().toDomainRoom(),
                )
                HttpStatusCode.Unauthorized -> ChatRoomMutationResult.Unauthorized
                else -> ChatRoomMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun inviteRoomMember(
        token: String,
        roomId: String,
        userId: String,
    ): ChatRoomActionResult {
        return sendRoomAction(
            endpoint = listOf("chat", "rooms", "invitations", "create"),
            request = ChatRoomInviteRequest(
                i = token.trim(),
                roomId = roomId.trim(),
                userId = userId.trim(),
            ),
            validate = {
                when {
                    token.trim().isEmpty() -> ChatRoomActionResult.Unauthorized
                    roomId.trim().isEmpty() -> ChatRoomActionResult.ServerError(400, "请选择聊天室")
                    userId.trim().isEmpty() -> ChatRoomActionResult.ServerError(400, "请输入用户 ID")
                    else -> null
                }
            },
        )
    }

    override suspend fun joinRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult {
        return sendRoomAction(
            endpoint = listOf("chat", "rooms", "join"),
            request = ChatRoomIdRequest(i = token.trim(), roomId = roomId.trim()),
            validate = { validateRoomIdAction(token, roomId) },
        )
    }

    override suspend fun leaveRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult {
        return sendRoomAction(
            endpoint = listOf("chat", "rooms", "leave"),
            request = ChatRoomIdRequest(i = token.trim(), roomId = roomId.trim()),
            validate = { validateRoomIdAction(token, roomId) },
        )
    }

    override suspend fun deleteRoom(
        token: String,
        roomId: String,
    ): ChatRoomActionResult {
        return sendRoomAction(
            endpoint = listOf("chat", "rooms", "delete"),
            request = ChatRoomIdRequest(i = token.trim(), roomId = roomId.trim()),
            validate = { validateRoomIdAction(token, roomId) },
        )
    }

    override suspend fun muteRoom(
        token: String,
        roomId: String,
        muted: Boolean,
    ): ChatRoomActionResult {
        return sendRoomAction(
            endpoint = listOf("chat", "rooms", "mute"),
            request = ChatRoomMuteRequest(i = token.trim(), roomId = roomId.trim(), mute = muted),
            validate = { validateRoomIdAction(token, roomId) },
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

            if (response.isSharkeyUnauthorized()) return ChatMessageReactionResult.Unauthorized
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

    private suspend inline fun <reified T> sendRoomAction(
        endpoint: List<String>,
        request: T,
        validate: () -> ChatRoomActionResult?,
    ): ChatRoomActionResult {
        validate()?.let { return it }

        return try {
            val response = client.post(apiUrl(*endpoint.toTypedArray())) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.isSharkeyUnauthorized()) return ChatRoomActionResult.Unauthorized
            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> ChatRoomActionResult.Success
                HttpStatusCode.Unauthorized -> ChatRoomActionResult.Unauthorized
                else -> ChatRoomActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChatRoomActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun validateRoomIdAction(
        token: String,
        roomId: String,
    ): ChatRoomActionResult? {
        return when {
            token.trim().isEmpty() -> ChatRoomActionResult.Unauthorized
            roomId.trim().isEmpty() -> ChatRoomActionResult.ServerError(400, "请选择聊天室")
            else -> null
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    private suspend fun List<ChatRoom>.withHistoryUnreadFallback(token: String): List<ChatRoom> {
        if (isEmpty()) return this

        val unreadRoomSummaries: Map<String, HistoryUnreadSummary> = try {
            val response = client.post(apiUrl("chat", "history")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatHistoryRequest(
                        i = token,
                        limit = HISTORY_UNREAD_FALLBACK_LIMIT,
                        room = true,
                    ),
                )
            }
            if (response.status != HttpStatusCode.OK) {
                emptyMap()
            } else {
                response.body<List<ChatHistoryMessageDto>>()
                    .asSequence()
                    .filter { it.isRead == false }
                    .fold(LinkedHashMap<String, HistoryUnreadSummary>()) { summaries, message ->
                        val roomId = message.toRoomId?.takeIf { it.isNotBlank() } ?: return@fold summaries
                        val current = summaries[roomId]
                        summaries[roomId] = HistoryUnreadSummary(
                            count = current?.count.coercePositive() + 1,
                            latestMarker = current?.latestMarker.orEmpty()
                                .ifBlank { message.unreadMarker() },
                        )
                        summaries
                    }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyMap()
        }

        if (unreadRoomSummaries.isEmpty()) return this
        return map { room ->
            val historyUnread = unreadRoomSummaries[room.id]
            val historyUnreadCount = historyUnread?.count.coercePositive()
            if (historyUnreadCount > room.unreadCount) {
                room.copy(
                    unreadCount = historyUnreadCount,
                    latestMessageMarker = room.latestMessageMarker.ifBlank {
                        historyUnread?.latestMarker.orEmpty()
                    },
                )
            } else {
                room
            }
        }
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
private data class ChatHistoryRequest(
    val i: String,
    val limit: Int,
    val room: Boolean = false,
)

@Serializable
private data class ChatUserMessagesRequest(
    val i: String,
    val userId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class ChatMessageSearchRequest(
    val i: String,
    val query: String,
    val limit: Int,
    val untilId: String? = null,
    val roomId: String? = null,
    val userId: String? = null,
)

@Serializable
private data class ChatRoomCreateRequest(
    val i: String,
    val name: String,
    val description: String? = null,
)

@Serializable
private data class ChatRoomUpdateRequest(
    val i: String,
    val roomId: String,
    val name: String,
    val description: String,
)

@Serializable
private data class ChatRoomIdRequest(
    val i: String,
    val roomId: String,
)

@Serializable
private data class ChatRoomInviteRequest(
    val i: String,
    val roomId: String,
    val userId: String,
)

@Serializable
private data class ChatRoomMuteRequest(
    val i: String,
    val roomId: String,
    val mute: Boolean,
)

@Serializable
private data class ChatCreateRoomMessageRequest(
    val i: String,
    val toRoomId: String,
    val text: String? = null,
    val fileId: String? = null,
    val fileIds: List<String>? = null,
    val replyId: String? = null,
    val quoteId: String? = null,
)

@Serializable
private data class ChatCreateUserMessageRequest(
    val i: String,
    val toUserId: String,
    val text: String? = null,
    val fileId: String? = null,
    val replyId: String? = null,
    val quoteId: String? = null,
)

@Serializable
private data class ChatMessageReactionRequest(
    val i: String,
    val messageId: String,
    val reaction: String,
)

@Serializable
private data class ChatMessageDeleteRequest(
    val i: String,
    val messageId: String,
)

@Serializable
private data class ChatRoomMembershipDto(
    val id: String,
    val createdAt: String = "",
    val userId: String = "",
    val roomId: String,
    val user: ChatUserDto? = null,
    val room: ChatRoomDto? = null,
    val lastMessage: ChatRoomLatestMessageDto? = null,
    val latestMessage: ChatRoomLatestMessageDto? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("lastMessageAt", "lastMessageCreatedAt", "latestMessageCreatedAt")
    val latestMessageAt: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("unread", "unreadMessagesCount")
    val unreadCount: Int = 0,
) {
    fun toDomainRoom(): ChatRoom {
        val membershipLatestMessageAtLabel = latestMessageTimeLabel()
        val domainRoom = room?.toDomainRoom(
            membershipId = id,
            membershipUnreadCount = unreadCount,
            latestMessageAtLabelOverride = membershipLatestMessageAtLabel,
            latestMessageMarkerOverride = latestMessageMarker(),
        )
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
            latestMessageAtLabel = membershipLatestMessageAtLabel,
            latestMessageMarker = latestMessageMarker(),
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

    private fun latestMessageTimeLabel(): String {
        return listOf(
            lastMessage?.createdAt,
            latestMessage?.createdAt,
            latestMessageAt,
        ).firstOrNull { !it.isNullOrBlank() }?.toLocalCompactDateLabel().orEmpty()
    }

    private fun latestMessageMarker(): String {
        return listOf(
            lastMessage?.id,
            latestMessage?.id,
            lastMessage?.createdAt,
            latestMessage?.createdAt,
            latestMessageAt,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }
}

@Serializable
private data class ChatHistoryMessageDto(
    val id: String = "",
    val createdAt: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("roomId")
    val toRoomId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("read")
    val isRead: Boolean? = null,
) {
    fun unreadMarker(): String {
        return id.ifBlank { createdAt }
    }
}

private data class HistoryUnreadSummary(
    val count: Int,
    val latestMarker: String,
)

private fun Int?.coercePositive(): Int = this?.coerceAtLeast(0) ?: 0

@Serializable
private data class ChatRoomDto(
    val id: String,
    val owner: ChatUserDto? = null,
    val name: String = "聊天室",
    val description: String = "",
    val joinMode: String = "",
    val memberCount: Int = 0,
    val isMuted: Boolean = false,
    val lastMessage: ChatRoomLatestMessageDto? = null,
    val latestMessage: ChatRoomLatestMessageDto? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("lastMessageAt", "lastMessageCreatedAt", "latestMessageCreatedAt")
    val latestMessageAt: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("unread", "unreadMessagesCount")
    val unreadCount: Int = 0,
) {
    fun toDomainRoom(
        membershipId: String,
        membershipUnreadCount: Int = 0,
        latestMessageAtLabelOverride: String = "",
        latestMessageMarkerOverride: String = "",
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
            latestMessageAtLabel = latestMessageAtLabelOverride.ifBlank { latestMessageTimeLabel() },
            latestMessageMarker = latestMessageMarkerOverride.ifBlank { latestMessageMarker() },
        )
    }

    fun toDomainRoom(): ChatRoom {
        return toDomainRoom(
            membershipId = id,
            membershipUnreadCount = unreadCount,
        )
    }

    private fun latestMessageTimeLabel(): String {
        return listOf(
            lastMessage?.createdAt,
            latestMessage?.createdAt,
            latestMessageAt,
        ).firstOrNull { !it.isNullOrBlank() }?.toLocalCompactDateLabel().orEmpty()
    }

    private fun latestMessageMarker(): String {
        return listOf(
            lastMessage?.id,
            latestMessage?.id,
            lastMessage?.createdAt,
            latestMessage?.createdAt,
            latestMessageAt,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }
}

@Serializable
private data class ChatRoomLatestMessageDto(
    val id: String = "",
    val createdAt: String = "",
)

private const val HISTORY_UNREAD_FALLBACK_LIMIT = 100

@Serializable
private data class ChatMessageDto(
    val id: String,
    val createdAt: String,
    val fromUser: ChatUserDto? = null,
    val fromUserId: String = "",
    val toUser: ChatUserDto? = null,
    val toUserId: String? = null,
    val toRoomId: String? = null,
    val text: String? = null,
    val file: ChatDriveFileDto? = null,
    val reactions: List<ChatMessageReactionDto> = emptyList(),
    val replyId: String? = null,
    val reply: ChatMessageReferenceDto? = null,
    val quoteId: String? = null,
    val quote: ChatMessageReferenceDto? = null,
    val isRead: Boolean = true,
) {
    fun toDomainMessage(): ChatMessage {
        val user = fromUser?.toDomainUser() ?: User(
            id = fromUserId.ifBlank { "unknown" },
            displayName = fromUserId.ifBlank { "成员" },
            username = fromUserId.ifBlank { "unknown" },
            avatarInitial = fromUserId.ifBlank { "成" }.avatarInitial(),
        )
        return ChatMessage(
            id = id,
            roomId = toRoomId.orEmpty(),
            fromUser = user,
            text = text.orEmpty(),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            createdAt = createdAt,
            toUserId = toUserId,
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
private data class ChatMessageReferenceDto(
    val id: String,
    val fromUser: ChatUserDto? = null,
    val text: String? = null,
    val file: ChatDriveFileDto? = null,
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
    val user: ChatUserDto? = null,
)

@Serializable
private data class ChatUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val avatarDecorations: List<ChatAvatarDecorationDto> = emptyList(),
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            avatarDecorations = avatarDecorations.mapNotNull { it.toDomainDecoration() },
        )
    }
}

@Serializable
private data class ChatAvatarDecorationDto(
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
