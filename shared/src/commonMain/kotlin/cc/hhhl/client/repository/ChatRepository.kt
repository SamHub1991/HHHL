package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatApi
import cc.hhhl.client.api.ChatMessageCreateResult
import cc.hhhl.client.api.ChatMessageDeleteResult
import cc.hhhl.client.api.ChatMessageLoadResult
import cc.hhhl.client.api.ChatMessageReactionResult
import cc.hhhl.client.api.ChatRoomActionResult
import cc.hhhl.client.api.ChatRoomInvitationLoadResult
import cc.hhhl.client.api.ChatRoomMemberLoadResult
import cc.hhhl.client.api.ChatRoomLoadResult
import cc.hhhl.client.api.ChatRoomMutationResult
import cc.hhhl.client.api.ChatUserHistoryLoadResult
import cc.hhhl.client.api.SharkeyChatApi
import cc.hhhl.client.api.apiDateSortKey
import cc.hhhl.client.cache.ChatMessageCache
import cc.hhhl.client.cache.ChatMessageCacheConversationType
import cc.hhhl.client.cache.ChatMessageCacheKey
import cc.hhhl.client.cache.NoopChatMessageCache
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomInvitation
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.User
import cc.hhhl.client.model.isServerChatMessageId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class ChatRepository(
    private val tokenProvider: () -> String?,
    private val currentUserIdProvider: () -> String? = { null },
    private val cacheAccountIdProvider: () -> String? = currentUserIdProvider,
    private val api: ChatApi = SharkeyChatApi(),
    private val messageCache: ChatMessageCache = NoopChatMessageCache,
) {
    private val messageCacheMutex = Mutex()
    private var roomUnreadCountCache: Map<String, CachedUnreadCount> = emptyMap()
    private var userUnreadCountCache: Map<String, CachedUnreadCount> = emptyMap()

    open suspend fun refresh(): ChatRepositoryResult {
        return load(currentRooms = emptyList(), untilId = null)
    }

    open suspend fun loadMore(currentRooms: List<ChatRoom>): ChatRepositoryResult {
        return load(
            currentRooms = currentRooms,
            untilId = currentRooms.lastOrNull()?.stableMembershipPageKey(),
        )
    }

    open suspend fun refreshOwnedRooms(): ChatRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRepositoryResult.Unauthorized
        return when (val result = api.loadOwnedRooms(token, DEFAULT_PAGE_SIZE)) {
            is ChatRoomLoadResult.Success -> ChatRepositoryResult.Success(result.rooms, endReached = result.rooms.isEmpty())
            ChatRoomLoadResult.Unauthorized -> ChatRepositoryResult.Unauthorized
            is ChatRoomLoadResult.NetworkError -> ChatRepositoryResult.Error("无法连接服务器：${result.message}")
            is ChatRoomLoadResult.ServerError -> result.toRepositoryResult()
        }
    }

    open suspend fun refreshInvitationInbox(): ChatRoomInvitationRepositoryResult {
        return loadInvitations(inbox = true)
    }

    open suspend fun refreshInvitationOutbox(): ChatRoomInvitationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomInvitationRepositoryResult.Unauthorized
        val ownedRooms = when (val ownedResult = api.loadOwnedRooms(token, DEFAULT_PAGE_SIZE)) {
            is ChatRoomLoadResult.Success -> ownedResult.rooms
            ChatRoomLoadResult.Unauthorized -> return ChatRoomInvitationRepositoryResult.Unauthorized
            is ChatRoomLoadResult.NetworkError -> {
                return ChatRoomInvitationRepositoryResult.Error("无法连接服务器：${ownedResult.message}")
            }
            is ChatRoomLoadResult.ServerError -> {
                return ChatRoomInvitationRepositoryResult.Error(ownedResult.message)
            }
        }
        if (ownedRooms.isEmpty()) {
            return ChatRoomInvitationRepositoryResult.Success(emptyList())
        }

        val invitations = mutableListOf<ChatRoomInvitation>()
        for (room in ownedRooms) {
            when (val result = api.loadInvitationOutbox(token, room.id, DEFAULT_PAGE_SIZE)) {
                is ChatRoomInvitationLoadResult.Success -> invitations += result.invitations
                ChatRoomInvitationLoadResult.Unauthorized -> return ChatRoomInvitationRepositoryResult.Unauthorized
                is ChatRoomInvitationLoadResult.NetworkError -> {
                    return ChatRoomInvitationRepositoryResult.Error("无法连接服务器：${result.message}")
                }
                is ChatRoomInvitationLoadResult.ServerError -> {
                    return ChatRoomInvitationRepositoryResult.Error(result.message)
                }
            }
        }
        return ChatRoomInvitationRepositoryResult.Success(invitations.distinctBy { it.id })
    }

    open suspend fun ignoreInvitation(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized
        return when (val result = api.ignoreRoomInvitation(token, cleanRoomId)) {
            ChatRoomActionResult.Success -> ChatRoomMutationRepositoryResult.ActionCompleted("已忽略邀请")
            ChatRoomActionResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
            is ChatRoomActionResult.NetworkError -> ChatRoomMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            is ChatRoomActionResult.ServerError -> ChatRoomMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshUserConversations(): ChatUserConversationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatUserConversationRepositoryResult.Unauthorized

        return when (val result = api.loadUserHistory(token, DEFAULT_HISTORY_INDEX_PAGE_SIZE)) {
            is ChatUserHistoryLoadResult.Success -> {
                val currentUserId = currentUserIdProvider()
                cacheUserHistoryMessages(result.messages, currentUserId)
                val conversations = result.messages
                    .toUserConversations(currentUserId)
                    .withResolvedUserUnreadCounts(token, currentUserId)
                ChatUserConversationRepositoryResult.Success(
                    conversations = conversations,
                )
            }
            ChatUserHistoryLoadResult.Unauthorized -> ChatUserConversationRepositoryResult.Unauthorized
            is ChatUserHistoryLoadResult.NetworkError -> {
                ChatUserConversationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatUserHistoryLoadResult.ServerError -> {
                ChatUserConversationRepositoryResult.Error(result.message)
            }
        }
    }

    open suspend fun refreshMessages(roomId: String): ChatMessageRepositoryResult {
        return loadMessages(
            roomId = roomId,
            currentMessages = emptyList(),
            untilId = null,
        )
    }

    open suspend fun restoreCachedMessages(roomId: String): ChatMessageRepositoryResult {
        return ChatMessageRepositoryResult.Success(
            messages = readCachedMessages(ChatMessageCacheConversationType.Room, roomId),
            endReached = false,
        )
    }

    open suspend fun refreshUserMessages(userId: String): ChatMessageRepositoryResult {
        return loadUserMessages(
            userId = userId,
            currentMessages = emptyList(),
            untilId = null,
        )
    }

    open suspend fun restoreCachedUserMessages(userId: String): ChatMessageRepositoryResult {
        return ChatMessageRepositoryResult.Success(
            messages = readCachedMessages(ChatMessageCacheConversationType.User, userId),
            endReached = false,
        )
    }

    open suspend fun cacheRoomMessage(roomId: String, message: ChatMessage) {
        appendCachedMessage(ChatMessageCacheConversationType.Room, roomId, message)
    }

    open suspend fun cacheUserMessage(userId: String, message: ChatMessage) {
        appendCachedMessage(ChatMessageCacheConversationType.User, userId, message)
    }

    open suspend fun deleteCachedUserMessages(userId: String) {
        val key = cacheKey(ChatMessageCacheConversationType.User, userId) ?: return
        messageCacheMutex.withLock {
            messageCache.delete(key)
        }
    }

    open suspend fun loadMoreUserMessages(
        userId: String,
        currentMessages: List<ChatMessage>,
    ): ChatMessageRepositoryResult {
        return loadUserMessages(
            userId = userId,
            currentMessages = currentMessages,
            untilId = currentMessages.firstOrNull()?.id,
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
            untilId = currentMembers.lastOfficialMembershipId(),
        )
    }

    open suspend fun createRoom(
        name: String,
        description: String,
        joinMode: String = "inviteOnly",
    ): ChatRoomMutationRepositoryResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return ChatRoomMutationRepositoryResult.ValidationError("请输入聊天室名称")
        }
        val cleanJoinMode = joinMode.cleanRepositoryChatRoomJoinMode()
            ?: return ChatRoomMutationRepositoryResult.ValidationError("请选择有效的加入方式")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return when (val result = api.createRoom(token, cleanName, description.trim(), cleanJoinMode)) {
            is ChatRoomMutationResult.Success -> ChatRoomMutationRepositoryResult.RoomSaved(result.room)
            ChatRoomMutationResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
            is ChatRoomMutationResult.NetworkError -> {
                ChatRoomMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomMutationResult.ServerError -> result.toMutationRepositoryResult()
        }
    }

    open suspend fun showRoom(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) {
            return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return when (val result = api.showRoom(token, cleanRoomId)) {
            is ChatRoomMutationResult.Success -> ChatRoomMutationRepositoryResult.RoomSaved(result.room)
            ChatRoomMutationResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
            is ChatRoomMutationResult.NetworkError -> {
                ChatRoomMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomMutationResult.ServerError -> result.toMutationRepositoryResult()
        }
    }

    open suspend fun updateRoom(
        roomId: String,
        name: String? = null,
        description: String? = null,
        joinMode: String? = null,
    ): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null && cleanName == null) {
            return ChatRoomMutationRepositoryResult.ValidationError("请输入聊天室名称")
        }
        val cleanDescription = description?.trim()
        val cleanJoinMode = joinMode?.cleanRepositoryChatRoomJoinMode()
            ?: joinMode?.let { return ChatRoomMutationRepositoryResult.ValidationError("请选择有效的加入方式") }
        if (cleanName == null && cleanDescription == null && cleanJoinMode == null) {
            return ChatRoomMutationRepositoryResult.ValidationError("没有要保存的聊天室设置")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return when (val result = api.updateRoom(token, cleanRoomId, cleanName, cleanDescription, cleanJoinMode)) {
            is ChatRoomMutationResult.Success -> ChatRoomMutationRepositoryResult.RoomSaved(result.room)
            ChatRoomMutationResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
            is ChatRoomMutationResult.NetworkError -> {
                ChatRoomMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomMutationResult.ServerError -> result.toMutationRepositoryResult()
        }
    }

    open suspend fun updateRoomManagement(
        roomId: String,
        messageRetentionDays: Int?,
    ): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        if (messageRetentionDays != null && messageRetentionDays !in 1..3650) {
            return ChatRoomMutationRepositoryResult.ValidationError("消息保留天数需要在 1 到 3650 之间")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return when (val result = api.updateRoomManagement(token, cleanRoomId, messageRetentionDays)) {
            is ChatRoomMutationResult.Success -> ChatRoomMutationRepositoryResult.RoomSaved(result.room)
            ChatRoomMutationResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
            is ChatRoomMutationResult.NetworkError -> {
                ChatRoomMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomMutationResult.ServerError -> result.toMutationRepositoryResult()
        }
    }

    open suspend fun deleteAllRoomMessages(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        val result = roomIdAction(
            roomId = cleanRoomId,
            success = ChatRoomMutationRepositoryResult.RoomMessagesCleared(cleanRoomId),
            action = { token, cleanRoomId -> api.deleteAllRoomMessages(token, cleanRoomId) },
        )
        if (result is ChatRoomMutationRepositoryResult.RoomMessagesCleared) {
            deleteCachedRoomMessages(cleanRoomId)
        }
        return result
    }

    open suspend fun inviteRoomMember(
        roomId: String,
        userId: String,
    ): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) {
            return ChatRoomMutationRepositoryResult.ValidationError("请输入用户 ID")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return api.inviteRoomMember(token, cleanRoomId, cleanUserId).toMutationRepositoryResult(
            success = ChatRoomMutationRepositoryResult.ActionCompleted("已发送邀请"),
        )
    }

    open suspend fun joinRoom(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        return roomIdAction(
            roomId = cleanRoomId,
            success = ChatRoomMutationRepositoryResult.ActionCompleted("已加入聊天室"),
            action = { token, cleanRoomId -> api.joinRoom(token, cleanRoomId) },
        )
    }

    open suspend fun leaveRoom(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        return roomIdAction(
            roomId = cleanRoomId,
            success = ChatRoomMutationRepositoryResult.RoomRemoved(cleanRoomId),
            action = { token, cleanRoomId -> api.leaveRoom(token, cleanRoomId) },
        )
    }

    open suspend fun deleteRoom(roomId: String): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        return roomIdAction(
            roomId = cleanRoomId,
            success = ChatRoomMutationRepositoryResult.RoomRemoved(cleanRoomId),
            action = { token, cleanRoomId -> api.deleteRoom(token, cleanRoomId) },
        )
    }

    open suspend fun muteRoom(
        roomId: String,
        muted: Boolean,
    ): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.trim()
        return roomIdAction(
            roomId = cleanRoomId,
            success = ChatRoomMutationRepositoryResult.RoomMuted(cleanRoomId, muted),
            action = { token, cleanRoomId -> api.muteRoom(token, cleanRoomId, muted) },
        )
    }

    open suspend fun sendMessage(
        roomId: String,
        text: String,
        fileId: String? = null,
        fileIds: List<String> = emptyList(),
        replyId: String? = null,
        quoteId: String? = null,
    ): ChatMessageRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择聊天室")
        val cleanText = text.trim()
        val cleanFileIds = (fileIds + listOfNotNull(fileId))
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
        if (cleanText.isBlank() && cleanFileIds.isEmpty()) {
            return ChatMessageRepositoryResult.Error("请输入消息")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (
            val result = api.createRoomMessage(
                token = token,
                roomId = cleanRoomId,
                text = cleanText,
                fileId = cleanFileIds.firstOrNull(),
                fileIds = cleanFileIds,
                replyId = replyId,
                quoteId = quoteId,
            )
        ) {
            is ChatMessageCreateResult.Success -> {
                appendCachedMessage(ChatMessageCacheConversationType.Room, cleanRoomId, result.message)
                ChatMessageRepositoryResult.Created(result.message)
            }
            ChatMessageCreateResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageCreateResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageCreateResult.ServerError -> result.toMessageRepositoryResult()
        }
    }

    open suspend fun sendUserMessage(
        userId: String,
        text: String,
        fileId: String? = null,
        replyId: String? = null,
        quoteId: String? = null,
    ): ChatMessageRepositoryResult {
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择用户")
        val cleanText = text.trim()
        val cleanFileId = fileId?.trim()?.takeIf(String::isNotEmpty)
        if (cleanText.isBlank() && cleanFileId == null) {
            return ChatMessageRepositoryResult.Error("请输入消息")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (
            val result = api.createUserMessage(
                token = token,
                userId = cleanUserId,
                text = cleanText,
                fileId = cleanFileId,
                replyId = replyId,
                quoteId = quoteId,
            )
        ) {
            is ChatMessageCreateResult.Success -> {
                appendCachedMessage(ChatMessageCacheConversationType.User, cleanUserId, result.message)
                ChatMessageRepositoryResult.Created(result.message)
            }
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

    open suspend fun deleteMessage(
        messageId: String,
        roomId: String? = null,
        userId: String? = null,
    ): ChatMessageRepositoryResult {
        val cleanMessageId = messageId.trim().takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择消息")
        if (!isServerChatMessageId(cleanMessageId)) {
            return ChatMessageRepositoryResult.Error("这条消息还没有服务器 ID，无法同步删除")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (val result = api.deleteMessage(token, cleanMessageId)) {
            ChatMessageDeleteResult.Success -> {
                removeCachedMessage(ChatMessageCacheConversationType.Room, roomId, cleanMessageId)
                removeCachedMessage(ChatMessageCacheConversationType.User, userId, cleanMessageId)
                ChatMessageRepositoryResult.Deleted(cleanMessageId)
            }
            ChatMessageDeleteResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageDeleteResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageDeleteResult.ServerError -> result.toMessageRepositoryResult()
        }
    }

    private suspend fun load(
        currentRooms: List<ChatRoom>,
        untilId: String?,
    ): ChatRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRepositoryResult.Unauthorized

        return when (val result = api.loadJoiningRooms(token, DEFAULT_PAGE_SIZE, untilId)) {
            is ChatRoomLoadResult.Success -> {
                val resolvedRooms = result.rooms.withResolvedRoomUnreadCounts(token)
                ChatRepositoryResult.Success(
                    rooms = currentRooms.appendDistinctBy(resolvedRooms) { it.stableRoomMergeKey() },
                    endReached = result.rooms.isEmpty(),
                )
            }
            ChatRoomLoadResult.Unauthorized -> ChatRepositoryResult.Unauthorized
            is ChatRoomLoadResult.NetworkError -> {
                ChatRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatRoomLoadResult.ServerError -> result.toRepositoryResult()
        }
    }

    private suspend fun loadInvitations(inbox: Boolean): ChatRoomInvitationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomInvitationRepositoryResult.Unauthorized
        val result = if (inbox) {
            api.loadInvitationInbox(token, DEFAULT_PAGE_SIZE)
        } else {
            return ChatRoomInvitationRepositoryResult.Error("聊天室邀请发件箱需要指定聊天室")
        }
        return when (result) {
            is ChatRoomInvitationLoadResult.Success -> ChatRoomInvitationRepositoryResult.Success(result.invitations)
            ChatRoomInvitationLoadResult.Unauthorized -> ChatRoomInvitationRepositoryResult.Unauthorized
            is ChatRoomInvitationLoadResult.NetworkError -> ChatRoomInvitationRepositoryResult.Error("无法连接服务器：${result.message}")
            is ChatRoomInvitationLoadResult.ServerError -> ChatRoomInvitationRepositoryResult.Error(result.message)
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
            is ChatMessageLoadResult.Success -> {
                val messages = currentMessages.mergeChronologicalMessages(result.messages)
                val initialLoad = if (untilId == null && currentMessages.isEmpty()) {
                    messages.withCachedHistoryBridge(
                        type = ChatMessageCacheConversationType.Room,
                        conversationId = cleanRoomId,
                        initialEndReached = result.messages.isEmpty(),
                    ) { cursor ->
                        api.loadRoomMessages(
                            token = token,
                            roomId = cleanRoomId,
                            limit = DEFAULT_HISTORY_INDEX_PAGE_SIZE,
                            untilId = cursor,
                        )
                    }
                } else {
                    CachedMessageLoad(
                        displayMessages = messages,
                        cacheMessages = messages,
                        endReached = result.messages.isEmpty(),
                        mergeExistingCache = true,
                    )
                }
                writeCachedMessages(
                    type = ChatMessageCacheConversationType.Room,
                    conversationId = cleanRoomId,
                    messages = initialLoad.cacheMessages,
                    mergeExisting = initialLoad.mergeExistingCache,
                )
                ChatMessageRepositoryResult.Success(
                    messages = initialLoad.displayMessages,
                    endReached = initialLoad.endReached,
                )
            }
            ChatMessageLoadResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageLoadResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageLoadResult.ServerError -> {
                val cachedMessages = readCachedMessages(ChatMessageCacheConversationType.Room, cleanRoomId)
                if (cachedMessages.isNotEmpty()) {
                    ChatMessageRepositoryResult.Success(
                        messages = currentMessages.mergeChronologicalMessages(cachedMessages),
                        endReached = false,
                    )
                } else {
                    result.toMessageRepositoryResult()
                }
            }
        }
    }

    private suspend fun List<ChatRoom>.withResolvedRoomUnreadCounts(token: String): List<ChatRoom> {
        if (none { it.unreadCount > 0 }) return this
        var resolvedCount = 0
        return map { room ->
            if (room.unreadCount <= 0 || resolvedCount >= MAX_UNREAD_COUNT_RESOLUTION_PER_REFRESH) {
                room
            } else {
                resolvedCount += 1
                room.copy(unreadCount = resolveRoomUnreadCount(token, room))
            }
        }
    }

    private suspend fun resolveRoomUnreadCount(
        token: String,
        room: ChatRoom,
    ): Int {
        val marker = room.unreadMarker()
        roomUnreadCountCache[room.id]?.takeIf { marker.isNotBlank() && it.marker == marker }?.let {
            return maxOf(room.unreadCount, it.count)
        }
        return when (
            val result = api.loadRoomMessages(
                token = token,
                roomId = room.id,
                limit = DEFAULT_UNREAD_COUNT_MESSAGE_LIMIT,
                untilId = null,
            )
        ) {
            is ChatMessageLoadResult.Success -> {
                val count = result.messages.count { !it.isRead }.coerceAtLeast(room.unreadCount)
                if (marker.isNotBlank()) {
                    roomUnreadCountCache = roomUnreadCountCache + (room.id to CachedUnreadCount(marker, count))
                }
                count
            }
            else -> room.unreadCount
        }
    }

    private suspend fun loadUserMessages(
        userId: String,
        currentMessages: List<ChatMessage>,
        untilId: String?,
    ): ChatMessageRepositoryResult {
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Error("请选择用户")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized

        return when (
            val result = api.loadUserMessages(
                token = token,
                userId = cleanUserId,
                limit = DEFAULT_MESSAGE_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is ChatMessageLoadResult.Success -> {
                val messages = currentMessages.mergeChronologicalMessages(result.messages)
                val initialLoad = if (untilId == null && currentMessages.isEmpty()) {
                    messages.withCachedHistoryBridge(
                        type = ChatMessageCacheConversationType.User,
                        conversationId = cleanUserId,
                        initialEndReached = result.messages.isEmpty(),
                    ) { cursor ->
                        api.loadUserMessages(
                            token = token,
                            userId = cleanUserId,
                            limit = DEFAULT_HISTORY_INDEX_PAGE_SIZE,
                            untilId = cursor,
                        )
                    }
                } else {
                    CachedMessageLoad(
                        displayMessages = messages,
                        cacheMessages = messages,
                        endReached = result.messages.isEmpty(),
                        mergeExistingCache = true,
                    )
                }
                writeCachedMessages(
                    type = ChatMessageCacheConversationType.User,
                    conversationId = cleanUserId,
                    messages = initialLoad.cacheMessages,
                    mergeExisting = initialLoad.mergeExistingCache,
                )
                ChatMessageRepositoryResult.Success(
                    messages = initialLoad.displayMessages,
                    endReached = initialLoad.endReached,
                )
            }
            ChatMessageLoadResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageLoadResult.NetworkError -> {
                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is ChatMessageLoadResult.ServerError -> {
                val cachedMessages = readCachedMessages(ChatMessageCacheConversationType.User, cleanUserId)
                if (cachedMessages.isNotEmpty()) {
                    ChatMessageRepositoryResult.Success(
                        messages = currentMessages.mergeChronologicalMessages(cachedMessages),
                        endReached = false,
                    )
                } else {
                    result.toMessageRepositoryResult()
                }
            }
        }
    }

    private suspend fun List<ChatUserConversation>.withResolvedUserUnreadCounts(
        token: String,
        currentUserId: String?,
    ): List<ChatUserConversation> {
        if (none { it.unreadCount > 0 }) return this
        val currentId = currentUserId?.trim().orEmpty()
        var resolvedCount = 0
        return map { conversation ->
            if (conversation.unreadCount <= 0 || resolvedCount >= MAX_UNREAD_COUNT_RESOLUTION_PER_REFRESH) {
                conversation
            } else {
                resolvedCount += 1
                conversation.copy(
                    unreadCount = resolveUserUnreadCount(token, conversation, currentId),
                )
            }
        }
    }

    private suspend fun resolveUserUnreadCount(
        token: String,
        conversation: ChatUserConversation,
        currentUserId: String,
    ): Int {
        val userId = conversation.user.id
        val marker = conversation.latestMessage?.unreadMarker().orEmpty()
        userUnreadCountCache[userId]?.takeIf { marker.isNotBlank() && it.marker == marker }?.let {
            return maxOf(conversation.unreadCount, it.count)
        }
        return when (
            val result = api.loadUserMessages(
                token = token,
                userId = userId,
                limit = DEFAULT_UNREAD_COUNT_MESSAGE_LIMIT,
                untilId = null,
            )
        ) {
            is ChatMessageLoadResult.Success -> {
                val count = result.messages.count { message ->
                    currentUserId.isNotBlank() &&
                        message.fromUser.id != currentUserId &&
                        !message.isRead
                }.coerceAtLeast(conversation.unreadCount)
                if (marker.isNotBlank()) {
                    userUnreadCountCache = userUnreadCountCache + (userId to CachedUnreadCount(marker, count))
                }
                count
            }
            else -> conversation.unreadCount
        }
    }

    open suspend fun searchMessages(
        query: String,
        roomId: String? = null,
        userId: String? = null,
        serverUntilId: String? = null,
        currentResults: List<ChatMessage> = emptyList(),
        currentConversationMessages: List<ChatMessage> = emptyList(),
    ): ChatMessageRepositoryResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ChatMessageRepositoryResult.Error("请输入搜索关键词")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatMessageRepositoryResult.Unauthorized
        val cleanRoomId = roomId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanUserId = userId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanRoomId == null && cleanUserId == null) {
            return ChatMessageRepositoryResult.Error("请选择聊天会话")
        }
        val type = if (cleanRoomId != null) {
            ChatMessageCacheConversationType.Room
        } else {
            ChatMessageCacheConversationType.User
        }
        val conversationId = cleanRoomId ?: cleanUserId.orEmpty()
        seedSearchCache(type, conversationId, currentConversationMessages)
        val cachedResults = searchCachedMessages(
            query = cleanQuery,
            roomId = cleanRoomId,
            userId = cleanUserId,
            currentResults = currentResults,
        )

        return when (
            val result = api.searchMessages(
                token = token,
                query = cleanQuery,
                limit = DEFAULT_MESSAGE_SEARCH_PAGE_SIZE,
                untilId = serverUntilId,
                roomId = cleanRoomId,
                userId = cleanUserId,
            )
        ) {
            is ChatMessageLoadResult.Success -> {
                val mergedMessages = currentResults
                    .mergeChronologicalMessages(cachedResults)
                    .mergeChronologicalMessages(result.messages)
                ChatMessageRepositoryResult.Success(
                    messages = mergedMessages,
                    endReached = cachedResults.isEmpty() && result.messages.isEmpty(),
                    nextUntilId = result.messages.minWithOrNull(chatMessageChronologicalComparator)?.id
                        ?: serverUntilId,
                )
            }
            ChatMessageLoadResult.Unauthorized -> ChatMessageRepositoryResult.Unauthorized
            is ChatMessageLoadResult.NetworkError -> {
                if (cachedResults.isNotEmpty()) {
                    ChatMessageRepositoryResult.Success(
                        messages = currentResults.mergeChronologicalMessages(cachedResults),
                        endReached = cachedResults.size < DEFAULT_MESSAGE_SEARCH_PAGE_SIZE,
                        nextUntilId = serverUntilId,
                    )
                } else {
                    ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
                }
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
                members = currentMembers.appendDistinctBy(result.members) { it.stableMemberMergeKey() },
                endReached = result.members.size < DEFAULT_MEMBER_PAGE_SIZE,
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

    private suspend fun roomIdAction(
        roomId: String,
        success: ChatRoomMutationRepositoryResult,
        action: suspend (String, String) -> ChatRoomActionResult,
    ): ChatRoomMutationRepositoryResult {
        val cleanRoomId = roomId.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.ValidationError("请选择聊天室")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ChatRoomMutationRepositoryResult.Unauthorized

        return action(token, cleanRoomId).toMutationRepositoryResult(success)
    }

    private suspend fun writeCachedMessages(
        type: ChatMessageCacheConversationType,
        conversationId: String,
        messages: List<ChatMessage>,
        mergeExisting: Boolean = true,
    ) {
        val key = cacheKey(type, conversationId) ?: return
        messageCacheMutex.withLock {
            val cleanMessages = messages.dedupeSortedMessages()
            if (cleanMessages.isEmpty()) {
                return@withLock
            }
            val cachedMessages = messageCache.read(key).dedupeSortedMessages()
            val mergedMessages = if (mergeExisting) {
                if (cachedMessages.isNotEmpty() && !cleanMessages.overlapsByMessageId(cachedMessages)) {
                    return@withLock
                }
                cachedMessages.mergeReplacingRepositoryMessages(cleanMessages)
            } else {
                cleanMessages
            }
            messageCache.write(key, mergedMessages)
        }
    }

    private suspend fun List<ChatMessage>.withCachedHistoryBridge(
        type: ChatMessageCacheConversationType,
        conversationId: String,
        initialEndReached: Boolean,
        loadBefore: suspend (untilId: String) -> ChatMessageLoadResult,
    ): CachedMessageLoad {
        val initialMessages = dedupeSortedMessages()
        if (initialMessages.isEmpty()) {
            return CachedMessageLoad(
                displayMessages = initialMessages,
                cacheMessages = initialMessages,
                endReached = initialEndReached,
                mergeExistingCache = true,
            )
        }
        val key = cacheKey(type, conversationId)
            ?: return CachedMessageLoad(
                displayMessages = initialMessages,
                cacheMessages = initialMessages,
                endReached = initialEndReached,
                mergeExistingCache = true,
            )
        val cachedMessages = messageCacheMutex.withLock {
            messageCache.read(key).dedupeSortedMessages()
        }
        if (cachedMessages.isEmpty()) {
            return CachedMessageLoad(
                displayMessages = initialMessages,
                cacheMessages = initialMessages,
                endReached = initialEndReached,
                mergeExistingCache = true,
            )
        }

        var bridgedMessages = initialMessages
        if (bridgedMessages.overlapsByMessageId(cachedMessages)) {
            val mergedMessages = cachedMessages.mergeReplacingRepositoryMessages(bridgedMessages)
            return CachedMessageLoad(
                displayMessages = mergedMessages.takeLast(MAX_RESTORED_MESSAGES_PER_CONVERSATION),
                cacheMessages = mergedMessages,
                endReached = initialEndReached,
                mergeExistingCache = true,
            )
        }

        var reachedEnd = initialEndReached
        var fetchedPages = 0
        while (!reachedEnd && fetchedPages < MAX_REFRESH_GAP_BRIDGE_PAGES) {
            val cursor = bridgedMessages.firstOrNull()?.id?.takeIf(::isServerChatMessageId)
                ?: break
            when (val page = loadBefore(cursor)) {
                is ChatMessageLoadResult.Success -> {
                    fetchedPages += 1
                    if (page.messages.isEmpty()) {
                        reachedEnd = true
                    } else {
                        val nextMessages = bridgedMessages.mergeChronologicalMessages(page.messages)
                        if (nextMessages.size == bridgedMessages.size) {
                            reachedEnd = true
                        } else {
                            bridgedMessages = nextMessages
                            if (bridgedMessages.overlapsByMessageId(cachedMessages)) {
                                val mergedMessages = cachedMessages.mergeReplacingRepositoryMessages(bridgedMessages)
                                return CachedMessageLoad(
                                    displayMessages = mergedMessages.takeLast(MAX_RESTORED_MESSAGES_PER_CONVERSATION),
                                    cacheMessages = mergedMessages,
                                    endReached = false,
                                    mergeExistingCache = true,
                                )
                            }
                        }
                    }
                }
                ChatMessageLoadResult.Unauthorized,
                is ChatMessageLoadResult.NetworkError,
                is ChatMessageLoadResult.ServerError,
                    -> break
            }
        }

        return CachedMessageLoad(
            displayMessages = bridgedMessages.takeLast(MAX_RESTORED_MESSAGES_PER_CONVERSATION),
            cacheMessages = bridgedMessages,
            endReached = reachedEnd,
            mergeExistingCache = true,
        )
    }

    private suspend fun readCachedMessages(
        type: ChatMessageCacheConversationType,
        conversationId: String,
    ): List<ChatMessage> {
        val key = cacheKey(type, conversationId) ?: return emptyList()
        return messageCache.read(key).dedupeSortedMessages().takeLast(MAX_RESTORED_MESSAGES_PER_CONVERSATION)
    }

    private suspend fun appendCachedMessage(
        type: ChatMessageCacheConversationType,
        conversationId: String,
        message: ChatMessage,
    ) {
        val key = cacheKey(type, conversationId) ?: return
        messageCacheMutex.withLock {
            val messages = messageCache.read(key)
                .withChronologicalRepositoryMessage(message)
            messageCache.write(key, messages)
        }
    }

    private suspend fun ensureCachedMessageHistory(
        token: String,
        type: ChatMessageCacheConversationType,
        conversationId: String,
        currentMessages: List<ChatMessage>,
    ): ChatMessageRepositoryResult? {
        val key = cacheKey(type, conversationId) ?: return null
        return messageCacheMutex.withLock {
            val cachedMessages = messageCache.read(key)
            val seedMessages = cachedMessages.mergeChronologicalMessages(currentMessages)
            if (seedMessages != cachedMessages) {
                messageCache.write(key, seedMessages)
            }
            if (messageCache.isComplete(key)) {
                null
            } else {
                var indexedMessages = seedMessages
                var untilId = indexedMessages.firstOrNull()?.id
                var failure: ChatMessageRepositoryResult? = null
                var complete = false
                while (!complete && failure == null) {
                    val result = when (type) {
                        ChatMessageCacheConversationType.Room -> api.loadRoomMessages(
                            token = token,
                            roomId = conversationId,
                            limit = DEFAULT_HISTORY_INDEX_PAGE_SIZE,
                            untilId = untilId,
                        )
                        ChatMessageCacheConversationType.User -> api.loadUserMessages(
                            token = token,
                            userId = conversationId,
                            limit = DEFAULT_HISTORY_INDEX_PAGE_SIZE,
                            untilId = untilId,
                        )
                    }
                    when (result) {
                        is ChatMessageLoadResult.Success -> {
                            if (result.messages.isEmpty()) {
                                messageCache.markComplete(key)
                                complete = true
                            } else {
                                val mergedMessages = indexedMessages.mergeChronologicalMessages(result.messages)
                                if (mergedMessages.size == indexedMessages.size) {
                                    messageCache.markComplete(key)
                                    complete = true
                                } else {
                                    indexedMessages = mergedMessages
                                    messageCache.write(key, indexedMessages)
                                    untilId = indexedMessages.firstOrNull()?.id
                                }
                            }
                        }
                        ChatMessageLoadResult.Unauthorized -> failure = ChatMessageRepositoryResult.Unauthorized
                        is ChatMessageLoadResult.NetworkError -> {
                            failure = if (indexedMessages.isNotEmpty()) {
                                null
                            } else {
                                ChatMessageRepositoryResult.Error("无法连接服务器：${result.message}")
                            }
                            complete = failure == null
                        }
                        is ChatMessageLoadResult.ServerError -> failure = result.toMessageRepositoryResult()
                    }
                }
                failure
            }
        }
    }

    private suspend fun seedSearchCache(
        type: ChatMessageCacheConversationType,
        conversationId: String,
        currentMessages: List<ChatMessage>,
    ) {
        if (currentMessages.isEmpty()) return
        val key = cacheKey(type, conversationId) ?: return
        messageCacheMutex.withLock {
            val cachedMessages = messageCache.read(key)
            val seedMessages = cachedMessages.mergeChronologicalMessages(currentMessages)
            if (seedMessages != cachedMessages) {
                messageCache.write(key, seedMessages)
            }
        }
    }

    private suspend fun removeCachedMessage(
        type: ChatMessageCacheConversationType,
        conversationId: String?,
        messageId: String,
    ) {
        val key = conversationId?.let { cacheKey(type, it) } ?: return
        messageCacheMutex.withLock {
            val messages = messageCache.read(key).filterNot { it.id == messageId }
            if (messages.isEmpty()) {
                messageCache.delete(key)
            } else {
                messageCache.write(key, messages)
            }
        }
    }

    private suspend fun deleteCachedRoomMessages(roomId: String) {
        val key = cacheKey(ChatMessageCacheConversationType.Room, roomId) ?: return
        messageCacheMutex.withLock {
            messageCache.delete(key)
        }
    }

    private suspend fun cacheUserHistoryMessages(
        messages: List<ChatMessage>,
        currentUserId: String?,
    ) {
        val currentId = currentUserId?.trim().orEmpty()
        for (message in messages) {
            val peerId = message.peerUser(currentId)?.id?.takeIf { it.isNotBlank() } ?: continue
            appendCachedMessage(ChatMessageCacheConversationType.User, peerId, message)
        }
    }

    private suspend fun searchCachedMessages(
        query: String,
        roomId: String?,
        userId: String?,
        currentResults: List<ChatMessage>,
    ): List<ChatMessage> {
        val accountId = cacheAccountIdProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val key = when {
            roomId != null -> ChatMessageCacheKey(accountId, ChatMessageCacheConversationType.Room, roomId)
            userId != null -> ChatMessageCacheKey(accountId, ChatMessageCacheConversationType.User, userId)
            else -> null
        }
        val cachedMessages = if (key != null) {
            messageCache.read(key)
        } else {
            messageCache.readAccount(accountId).values.flatten()
        }
        if (cachedMessages.isEmpty()) return emptyList()

        val currentIds = currentResults.mapTo(HashSet<String>(currentResults.size)) { it.id }
        val oldestLoadedMessage = currentResults.firstOrNull()
        val seenIds = HashSet<String>(DEFAULT_MESSAGE_SEARCH_PAGE_SIZE + currentIds.size)
        val matchingMessages = ArrayList<ChatMessage>(DEFAULT_MESSAGE_SEARCH_PAGE_SIZE)
        for (message in cachedMessages.asReversed()) {
            if (message.id in currentIds || !seenIds.add(message.id)) continue
            if (
                oldestLoadedMessage != null &&
                chatMessageChronologicalComparator.compare(message, oldestLoadedMessage) >= 0
            ) {
                continue
            }
            if (!message.matchesSearchQuery(query)) continue
            matchingMessages += message
            if (matchingMessages.size >= DEFAULT_MESSAGE_SEARCH_PAGE_SIZE) break
        }
        return matchingMessages.asReversed()
    }

    private fun cacheKey(
        type: ChatMessageCacheConversationType,
        conversationId: String,
    ): ChatMessageCacheKey? {
        val accountId = cacheAccountIdProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cleanConversationId = conversationId.trim().takeIf { it.isNotEmpty() } ?: return null
        return ChatMessageCacheKey(
            accountId = accountId,
            type = type,
            conversationId = cleanConversationId,
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
        const val DEFAULT_MESSAGE_PAGE_SIZE = 40
        const val DEFAULT_UNREAD_COUNT_MESSAGE_LIMIT = 100
        const val DEFAULT_HISTORY_INDEX_PAGE_SIZE = 100
        const val DEFAULT_MESSAGE_SEARCH_PAGE_SIZE = 30
        const val DEFAULT_MEMBER_PAGE_SIZE = 100
        const val MAX_UNREAD_COUNT_RESOLUTION_PER_REFRESH = 6
        const val MAX_RESTORED_MESSAGES_PER_CONVERSATION = 240
        const val MAX_REFRESH_GAP_BRIDGE_PAGES = 6
    }
}

private data class CachedMessageLoad(
    val displayMessages: List<ChatMessage>,
    val cacheMessages: List<ChatMessage>,
    val endReached: Boolean,
    val mergeExistingCache: Boolean,
)

suspend fun ChatRepository.resolveRealtimeMessage(
    message: ChatMessage,
    directUserId: String? = null,
): ChatMessage {
    if (!message.requiresRealtimeAttentionResolution()) return message
    val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
    val refreshedMessages = when {
        cleanDirectUserId != null -> when (val result = refreshUserMessages(cleanDirectUserId)) {
            is ChatMessageRepositoryResult.Success -> result.messages
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            is ChatMessageRepositoryResult.Error,
            ChatMessageRepositoryResult.ReactionUpdated,
            ChatMessageRepositoryResult.Unauthorized,
                -> emptyList()
        }
        message.roomId.isNotBlank() -> when (val result = refreshMessages(message.roomId)) {
            is ChatMessageRepositoryResult.Success -> result.messages
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            is ChatMessageRepositoryResult.Error,
            ChatMessageRepositoryResult.ReactionUpdated,
            ChatMessageRepositoryResult.Unauthorized,
                -> emptyList()
        }
        else -> emptyList()
    }
    return refreshedMessages.firstOrNull { candidate ->
        candidate.matchesRealtimeMessage(message, cleanDirectUserId)
    } ?: message
}

fun ChatMessage.requiresRealtimeAttentionResolution(): Boolean {
    return replyUnavailable || quoteUnavailable
}

private fun List<ChatRoomMember>.lastOfficialMembershipId(): String? {
    return lastOrNull { member -> !member.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX) }
        ?.membershipId
        ?.takeIf { it.isNotBlank() }
}

private fun ChatRoom.stableRoomMergeKey(): String {
    return id.ifBlank { membershipId.ifBlank { name } }
}

private fun ChatRoom.stableMembershipPageKey(): String? {
    return membershipId.ifBlank { id }.takeIf { it.isNotBlank() }
}

private fun String.cleanRepositoryChatRoomJoinMode(): String? {
    return when (trim()) {
        "inviteOnly", "invite", "invitation" -> "inviteOnly"
        "open", "public" -> "open"
        "closed", "close" -> "closed"
        else -> null
    }
}

private fun ChatRoomMember.stableMemberMergeKey(): String {
    return membershipId.ifBlank { user.id.ifBlank { roomId } }
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

private fun ChatMessageDeleteResult.ServerError.toMessageRepositoryResult(): ChatMessageRepositoryResult {
    return ChatMessageRepositoryResult.Error(message)
}

private fun ChatRoomMemberLoadResult.ServerError.toMemberRepositoryResult(): ChatRoomMemberRepositoryResult {
    return ChatRoomMemberRepositoryResult.Error(message)
}

private fun ChatRoomMutationResult.ServerError.toMutationRepositoryResult(): ChatRoomMutationRepositoryResult {
    return ChatRoomMutationRepositoryResult.Error(message)
}

private fun ChatRoomActionResult.toMutationRepositoryResult(
    success: ChatRoomMutationRepositoryResult,
): ChatRoomMutationRepositoryResult {
    return when (this) {
        ChatRoomActionResult.Success -> success
        ChatRoomActionResult.Unauthorized -> ChatRoomMutationRepositoryResult.Unauthorized
        is ChatRoomActionResult.NetworkError -> ChatRoomMutationRepositoryResult.Error("无法连接服务器：$message")
        is ChatRoomActionResult.ServerError -> ChatRoomMutationRepositoryResult.Error(message)
    }
}

private val chatMessageChronologicalComparator = compareBy<ChatMessage> {
    apiDateSortKey(it.createdAt, it.createdAtLabel)
}.thenBy { it.id }

private fun List<ChatMessage>.mergeChronologicalMessages(incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return this
    val cleanIncoming = incoming.withStableRepositoryMessageIds().dedupeSortedMessages()
    if (isEmpty()) return cleanIncoming
    val stableCurrent = withStableRepositoryMessageIds()

    val existingIds = HashSet<String>(stableCurrent.size + cleanIncoming.size)
    stableCurrent.forEach { existingIds += it.id }
    val newMessages = cleanIncoming.filterNot { it.id in existingIds }
    if (newMessages.isEmpty()) return if (stableCurrent == this) this else stableCurrent

    return when {
        chatMessageChronologicalComparator.compare(newMessages.last(), stableCurrent.first()) <= 0 -> newMessages + stableCurrent
        chatMessageChronologicalComparator.compare(newMessages.first(), stableCurrent.last()) >= 0 -> stableCurrent + newMessages
        else -> stableCurrent.mergeSortedMessages(newMessages)
    }
}

private fun List<ChatMessage>.withChronologicalRepositoryMessage(message: ChatMessage): List<ChatMessage> {
    val stableMessages = withStableRepositoryMessageIds()
    val stableMessage = message.withStableRepositoryMessageId()
    return if (stableMessages.any { it.id == stableMessage.id }) {
        stableMessages.map { if (it.id == stableMessage.id) stableMessage else it }.sortedWith(chatMessageChronologicalComparator)
    } else {
        stableMessages.mergeChronologicalMessages(listOf(stableMessage))
    }
}

private fun List<ChatMessage>.mergeReplacingRepositoryMessages(incoming: List<ChatMessage>): List<ChatMessage> {
    val stableCurrent = withStableRepositoryMessageIds()
    val stableIncoming = incoming.withStableRepositoryMessageIds()
    if (incoming.isEmpty()) return dedupeSortedMessages()
    if (stableCurrent.isEmpty()) return stableIncoming.dedupeSortedMessages()
    val incomingById = stableIncoming.associateBy { it.id }
    val existingIds = stableCurrent.mapTo(HashSet(stableCurrent.size)) { it.id }
    return (stableCurrent.map { message -> incomingById[message.id] ?: message } + stableIncoming.filterNot { incomingMessage ->
        incomingMessage.id in existingIds
    }).dedupeSortedMessages()
}

private fun List<ChatMessage>.overlapsByMessageId(other: List<ChatMessage>): Boolean {
    if (isEmpty() || other.isEmpty()) return false
    val ids = mapTo(HashSet(size)) { it.id }
    return other.any { it.id.isNotBlank() && it.id in ids }
}

private fun List<ChatMessage>.dedupeSortedMessages(): List<ChatMessage> {
    val stableMessages = withStableRepositoryMessageIds()
    if (stableMessages.size <= 1) return stableMessages
    val seenIds = HashSet<String>(size)
    var previous: ChatMessage? = null
    for (message in stableMessages) {
        if (!seenIds.add(message.id)) {
            return stableMessages.distinctBy { it.id }.sortedWith(chatMessageChronologicalComparator)
        }
        val previousMessage = previous
        if (previousMessage != null && chatMessageChronologicalComparator.compare(message, previousMessage) < 0) {
            return stableMessages.distinctBy { it.id }.sortedWith(chatMessageChronologicalComparator)
        }
        previous = message
    }
    return stableMessages
}

private fun List<ChatMessage>.mergeSortedMessages(newMessages: List<ChatMessage>): List<ChatMessage> {
    if (newMessages.isEmpty()) return this
    val merged = ArrayList<ChatMessage>(size + newMessages.size)
    var currentIndex = 0
    var incomingIndex = 0
    while (currentIndex < size && incomingIndex < newMessages.size) {
        if (chatMessageChronologicalComparator.compare(this[currentIndex], newMessages[incomingIndex]) <= 0) {
            merged += this[currentIndex]
            currentIndex += 1
        } else {
            merged += newMessages[incomingIndex]
            incomingIndex += 1
        }
    }
    while (currentIndex < size) {
        merged += this[currentIndex]
        currentIndex += 1
    }
    while (incomingIndex < newMessages.size) {
        merged += newMessages[incomingIndex]
        incomingIndex += 1
    }
    return merged
}

private fun List<ChatMessage>.withStableRepositoryMessageIds(): List<ChatMessage> {
    if (isEmpty()) return this
    val seenIds = HashMap<String, Int>(size)
    var changed = false
    val stableMessages = mapIndexed { index, message ->
        val hasServerId = message.id.isNotBlank()
        val baseId = if (hasServerId) message.id else message.repositoryFallbackMessageId(index)
        val seenCount = seenIds[baseId] ?: 0
        seenIds[baseId] = seenCount + 1
        val stableId = if (hasServerId || seenCount == 0) baseId else "${baseId}#dup-$seenCount"
        if (stableId == message.id) {
            message
        } else {
            changed = true
            message.copy(id = stableId)
        }
    }
    return if (changed) stableMessages else this
}

private fun ChatMessage.withStableRepositoryMessageId(): ChatMessage {
    return if (id.isNotBlank()) this else copy(id = repositoryFallbackMessageId(0))
}

private fun ChatMessage.repositoryFallbackMessageId(index: Int): String {
    val seed = listOf(roomId, toUserId.orEmpty(), fromUser.id, createdAt, createdAtLabel, text, file?.id.orEmpty(), index.toString())
        .joinToString(separator = "\u0000")
    return "local-chat-${seed.stableRepositoryChatHash()}"
}

private fun String.stableRepositoryChatHash(): String {
    var hash = 1125899906842597L
    for (char in this) {
        hash = 31L * hash + char.code
    }
    return hash.toULong().toString(36)
}

private fun ChatMessage.matchesSearchQuery(query: String): Boolean {
    val cleanQuery = query.lowercase()
    return listOfNotNull(
        text,
        createdAt,
        createdAtLabel,
        fromUser.displayName,
        fromUser.username,
        toUser?.displayName,
        toUser?.username,
        file?.name,
        file?.comment,
        reply?.text,
        reply?.fromUser?.displayName,
        reply?.fromUser?.username,
        reply?.file?.name,
        reply?.file?.comment,
        quote?.text,
        quote?.fromUser?.displayName,
        quote?.fromUser?.username,
        quote?.file?.name,
        quote?.file?.comment,
    ).any { it.lowercase().contains(cleanQuery) }
}

private fun List<ChatMessage>.toUserConversations(currentUserId: String?): List<ChatUserConversation> {
    val currentId = currentUserId?.trim().orEmpty()
    val byPeer = LinkedHashMap<String, ChatUserConversation>()
    val unreadCountsByPeer = LinkedHashMap<String, Int>()
    for (message in sortedWith(chatMessageChronologicalComparator).asReversed()) {
        val peer = message.peerUser(currentId) ?: continue
        if (
            currentId.isNotBlank() &&
            message.fromUser.id != currentId &&
            !message.isRead
        ) {
            unreadCountsByPeer[peer.id] = unreadCountsByPeer[peer.id].coercePositive() + 1
        }
        if (byPeer.containsKey(peer.id)) continue
        byPeer[peer.id] = ChatUserConversation(user = peer, latestMessage = message)
    }
    return byPeer.values.map { conversation ->
        conversation.copy(unreadCount = unreadCountsByPeer[conversation.user.id].coercePositive())
    }
}

private fun ChatMessage.peerUser(currentUserId: String): User? {
    if (currentUserId.isBlank()) {
        return toUser ?: fromUser.takeIf { it.id.isNotBlank() }
    }
    if (fromUser.id == currentUserId) {
        return toUser ?: toUserId?.takeIf { it.isNotBlank() }?.let {
            User(
                id = it,
                displayName = it,
                username = it,
                avatarInitial = it.chatUserInitial(),
            )
        }
    }
    return fromUser.takeIf { it.id.isNotBlank() }
}

private fun ChatRoom.unreadMarker(): String {
    return latestMessageMarker.ifBlank { latestMessageAtLabel }
}

private fun ChatMessage.unreadMarker(): String {
    return id.ifBlank { createdAt.ifBlank { createdAtLabel } }
}

private fun ChatMessage.matchesRealtimeMessage(
    incoming: ChatMessage,
    directUserId: String?,
): Boolean {
    if (id.isNotBlank() && id == incoming.id) return true
    if (!matchesRealtimeConversation(incoming, directUserId)) return false
    if (unreadMarker() == incoming.unreadMarker()) return true
    return createdAt == incoming.createdAt &&
        createdAt.isNotBlank() &&
        fromUser.id == incoming.fromUser.id &&
        text == incoming.text &&
        file?.id.orEmpty() == incoming.file?.id.orEmpty()
}

private fun ChatMessage.matchesRealtimeConversation(
    incoming: ChatMessage,
    directUserId: String?,
): Boolean {
    val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        cleanDirectUserId != null -> roomId.isBlank() &&
            incoming.roomId.isBlank() &&
            (fromUser.id == cleanDirectUserId || toUserId == cleanDirectUserId || toUser?.id == cleanDirectUserId)
        roomId.isNotBlank() -> roomId == incoming.roomId
        else -> true
    }
}

private fun Int?.coercePositive(): Int = this?.coerceAtLeast(0) ?: 0

private data class CachedUnreadCount(
    val marker: String,
    val count: Int,
)

private fun String.chatUserInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

sealed interface ChatRepositoryResult {
    data class Success(
        val rooms: List<ChatRoom>,
        val endReached: Boolean = false,
    ) : ChatRepositoryResult

    data object Unauthorized : ChatRepositoryResult

    data class Error(val message: String) : ChatRepositoryResult
}

sealed interface ChatUserConversationRepositoryResult {
    data class Success(
        val conversations: List<ChatUserConversation>,
    ) : ChatUserConversationRepositoryResult

    data object Unauthorized : ChatUserConversationRepositoryResult

    data class Error(val message: String) : ChatUserConversationRepositoryResult
}

sealed interface ChatMessageRepositoryResult {
    data class Success(
        val messages: List<ChatMessage>,
        val endReached: Boolean = false,
        val nextUntilId: String? = null,
    ) : ChatMessageRepositoryResult

    data class Created(val message: ChatMessage) : ChatMessageRepositoryResult

    data object ReactionUpdated : ChatMessageRepositoryResult

    data class Deleted(val messageId: String) : ChatMessageRepositoryResult

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

sealed interface ChatRoomInvitationRepositoryResult {
    data class Success(
        val invitations: List<ChatRoomInvitation>,
    ) : ChatRoomInvitationRepositoryResult

    data object Unauthorized : ChatRoomInvitationRepositoryResult

    data class Error(val message: String) : ChatRoomInvitationRepositoryResult
}

sealed interface ChatRoomMutationRepositoryResult {
    data class RoomSaved(val room: ChatRoom) : ChatRoomMutationRepositoryResult

    data class RoomRemoved(val roomId: String) : ChatRoomMutationRepositoryResult

    data class RoomMessagesCleared(val roomId: String) : ChatRoomMutationRepositoryResult

    data class RoomMuted(
        val roomId: String,
        val muted: Boolean,
    ) : ChatRoomMutationRepositoryResult

    data class ActionCompleted(val message: String) : ChatRoomMutationRepositoryResult

    data object Unauthorized : ChatRoomMutationRepositoryResult

    data class ValidationError(val message: String) : ChatRoomMutationRepositoryResult

    data class Error(val message: String) : ChatRoomMutationRepositoryResult
}
