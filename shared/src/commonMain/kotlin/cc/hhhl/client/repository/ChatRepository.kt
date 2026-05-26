package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatApi
import cc.hhhl.client.api.ChatMessageCreateResult
import cc.hhhl.client.api.ChatMessageLoadResult
import cc.hhhl.client.api.ChatMessageReactionResult
import cc.hhhl.client.api.ChatRoomMemberLoadResult
import cc.hhhl.client.api.ChatRoomLoadResult
import cc.hhhl.client.api.SharkeyChatApi
import cc.hhhl.client.api.apiDateSortKey
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember

open class ChatRepository(
    private val tokenProvider: () -> String?,
    private val api: ChatApi = SharkeyChatApi(),
) {
    open suspend fun refresh(): ChatRepositoryResult {
        return load(currentRooms = emptyList(), untilId = null)
    }

    open suspend fun loadMore(currentRooms: List<ChatRoom>): ChatRepositoryResult {
        return load(
            currentRooms = currentRooms,
            untilId = currentRooms.lastOrNull()?.membershipId,
        )
    }

    open suspend fun refreshMessages(roomId: String): ChatMessageRepositoryResult {
        return loadMessages(
            roomId = roomId,
            currentMessages = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreMessages(
        roomId: String,
        currentMessages: List<ChatMessage>,
    ): ChatMessageRepositoryResult {
        return loadMessages(
            roomId = roomId,
            currentMessages = currentMessages,
            untilId = currentMessages.firstOrNull()?.id,
        )
    }

    open suspend fun refreshMembers(roomId: String): ChatRoomMemberRepositoryResult {
        return loadMembers(
            roomId = roomId,
            currentMembers = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreMembers(
        roomId: String,
        currentMembers: List<ChatRoomMember>,
    ): ChatRoomMemberRepositoryResult {
        return loadMembers(
            roomId = roomId,
            currentMembers = currentMembers,
            untilId = currentMembers.lastOrNull()?.membershipId,
        )
    }

    open suspend fun sendMessage(
        roomId: String,
        text: String,
        fileId: String? = null,
    ): ChatMessageRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择聊天室")
        val cleanText = text.trim()
        val cleanFileId = fileId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanText.isBlank() && cleanFileId == null) {
            return ChatMessageRepositoryResult.Error("请输入消息")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (val result = api.createRoomMessage(token, cleanRoomId, cleanText, cleanFileId)) {
            is ChatMessageCreateResult.Success -> ChatMessageRepositoryResult.Created(result.message)
            ChatMessageCreateResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageCreateResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageCreateResult.ServerError -> result.toMessageRepositoryResult()
        }
    }

    open suspend fun reactToMessage(
        messageId: String,
        reaction: String,
    ): ChatMessageRepositoryResult {
        return updateMessageReaction(
            messageId = messageId,
            reaction = reaction,
            react = true,
        )
    }

    open suspend fun unreactToMessage(
        messageId: String,
        reaction: String,
    ): ChatMessageRepositoryResult {
        return updateMessageReaction(
            messageId = messageId,
            reaction = reaction,
            react = false,
        )
    }

    private suspend fun load(
        currentRooms: List<ChatRoom>,
        untilId: String?,
    ): ChatRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRepositoryResult.Unauthorized

        return when (val result = api.loadJoiningRooms(token, DEFAULT_PAGE_SIZE, untilId)) {
            is ChatRoomLoadResult.Success -> ChatRepositoryResult.Success(
                rooms = (currentRooms + result.rooms).distinctBy { it.id },
                endReached = result.rooms.isEmpty(),
            )
            ChatRoomLoadResult.Unauthorized -> ChatRepositoryResult.Unauthorized
            is ChatRoomLoadResult.NetworkError -> {
                ChatRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomLoadResult.ServerError -> result.toRepositoryResult()
        }
    }

    private suspend fun loadMessages(
        roomId: String,
        currentMessages: List<ChatMessage>,
        untilId: String?,
    ): ChatMessageRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择聊天室")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (
            val result = api.loadRoomMessages(
                token = token,
                roomId = cleanRoomId,
                limit = DEFAULT_MESSAGE_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is ChatMessageLoadResult.Success -> ChatMessageRepositoryResult.Success(
                messages = (currentMessages + result.messages).dedupeChronologicalMessages(),
                endReached = result.messages.isEmpty(),
            )
            ChatMessageLoadResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageLoadResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageLoadResult.ServerError -> result.toMessageRepositoryResult()
        }
    }

    private suspend fun loadMembers(
        roomId: String,
        currentMembers: List<ChatRoomMember>,
        untilId: String?,
    ): ChatRoomMemberRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatRoomMemberRepositoryResult.Error("请选择聊天室")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMemberRepositoryResult.Unauthorized

        return when (
            val result = api.loadRoomMembers(
                token = token,
                roomId = cleanRoomId,
                limit = DEFAULT_MEMBER_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is ChatRoomMemberLoadResult.Success -> ChatRoomMemberRepositoryResult.Success(
                members = (currentMembers + result.members).distinctBy { it.membershipId },
                endReached = result.members.isEmpty(),
            )
            ChatRoomMemberLoadResult.Unauthorized -> ChatRoomMemberRepositoryResult.Unauthorized
            is ChatRoomMemberLoadResult.NetworkError -> {
                ChatRoomMemberRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomMemberLoadResult.ServerError -> result.toMemberRepositoryResult()
        }
    }

    private suspend fun updateMessageReaction(
        messageId: String,
        reaction: String,
        react: Boolean,
    ): ChatMessageRepositoryResult {
        val cleanMessageId = messageId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择消息")
        val cleanReaction = reaction.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择回应")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        val result = if (react) {
            api.reactToMessage(token, cleanMessageId, cleanReaction)
        } else {
            api.unreactToMessage(token, cleanMessageId, cleanReaction)
        }

        return when (result) {
            ChatMessageReactionResult.Success -> ChatMessageRepositoryResult.ReactionUpdated
            ChatMessageReactionResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageReactionResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageReactionResult.ServerError -> result.toMessageRepositoryResult()
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
        const val DEFAULT_MESSAGE_PAGE_SIZE = 40
        const val DEFAULT_MEMBER_PAGE_SIZE = 30
    }
}

private fun ChatRoomLoadResult.ServerError.toRepositoryResult(): ChatRepositoryResult {
    return ChatRepositoryResult.Error(message)
}

private fun ChatMessageLoadResult.ServerError.toMessageRepositoryResult(): ChatMessageRepositoryResult {
    return ChatMessageRepositoryResult.Error(message)
}

private fun ChatMessageCreateResult.ServerError.toMessageRepositoryResult(): ChatMessageRepositoryResult {
    return ChatMessageRepositoryResult.Error(message)
}

private fun ChatMessageReactionResult.ServerError.toMessageRepositoryResult(): ChatMessageRepositoryResult {
    return ChatMessageRepositoryResult.Error(message)
}

private fun ChatRoomMemberLoadResult.ServerError.toMemberRepositoryResult(): ChatRoomMemberRepositoryResult {
    return ChatRoomMemberRepositoryResult.Error(message)
}

private fun List<ChatMessage>.dedupeChronologicalMessages(): List<ChatMessage> {
    return distinctBy { it.id }.sortedWith(
        compareBy<ChatMessage> { apiDateSortKey(it.createdAt, it.createdAtLabel) }
            .thenBy { it.id },
    )
}

sealed interface ChatRepositoryResult {
    data class Success(
        val rooms: List<ChatRoom>,
        val endReached: Boolean = false,
    ) : ChatRepositoryResult

    data object Unauthorized : ChatRepositoryResult

    data class Error(val message: String) : ChatRepositoryResult
}

sealed interface ChatMessageRepositoryResult {
    data class Success(
        val messages: List<ChatMessage>,
        val endReached: Boolean = false,
    ) : ChatMessageRepositoryResult

    data class Created(val message: ChatMessage) : ChatMessageRepositoryResult

    data object ReactionUpdated : ChatMessageRepositoryResult

    data object Unauthorized : ChatMessageRepositoryResult

    data class Error(val message: String) : ChatMessageRepositoryResult
}

sealed interface ChatRoomMemberRepositoryResult {
    data class Success(
        val members: List<ChatRoomMember>,
        val endReached: Boolean = false,
    ) : ChatRoomMemberRepositoryResult

    data object Unauthorized : ChatRoomMemberRepositoryResult

    data class Error(val message: String) : ChatRoomMemberRepositoryResult
}
