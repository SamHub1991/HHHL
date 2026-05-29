package cc.hhhl.client.state

import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.apiDateSortKey
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.ChatUnreadStore
import cc.hhhl.client.cache.NoopChatUnreadStore
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX
import cc.hhhl.client.model.ChatRoomInvitation
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.model.commonReactionOptions
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.presentation.truncateRichTextPreviewText
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRoomMemberRepositoryResult
import cc.hhhl.client.repository.ChatRoomMutationRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ChatUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val ownedRooms: List<ChatRoom> = emptyList(),
    val roomInvitationInbox: List<ChatRoomInvitation> = emptyList(),
    val roomInvitationOutbox: List<ChatRoomInvitation> = emptyList(),
    val userConversations: List<ChatUserConversation> = emptyList(),
    val selectedRoom: ChatRoom? = null,
    val selectedUserConversation: ChatUserConversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val messageSearchResults: List<ChatMessage> = emptyList(),
    val messageSearchQuery: String = "",
    val messageSearchServerUntilId: String? = null,
    val members: List<ChatRoomMember> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingRoomExtras: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isSearchingMessages: Boolean = false,
    val isLoadingMoreMessageSearch: Boolean = false,
    val isLoadingMembers: Boolean = false,
    val isLoadingMoreMembers: Boolean = false,
    val isManagingRoom: Boolean = false,
    val isSendingMessage: Boolean = false,
    val endReached: Boolean = false,
    val userConversationsEndReached: Boolean = true,
    val messagesEndReached: Boolean = false,
    val messageSearchEndReached: Boolean = true,
    val membersEndReached: Boolean = false,
    val showingMembers: Boolean = false,
    val messageDraft: String = "",
    val replyingMessage: ChatMessageQuote? = null,
    val quotedMessage: ChatMessageQuote? = null,
    val attachedFile: DriveFile? = null,
    val attachments: List<ChatComposerAttachment> = emptyList(),
    val isUploadingMedia: Boolean = false,
    val reactionOptions: List<String> = commonReactionOptions,
    val pendingMessageReactionIds: Set<String> = emptySet(),
    val pendingMessageDeleteIds: Set<String> = emptySet(),
    val specialCareUserIds: Set<String> = emptySet(),
    val specialCareToast: SpecialCareChatToast? = null,
    val specialCareJumpMessageId: String? = null,
    val selectedRoomUnreadCount: Int = 0,
    val selectedUserUnreadCount: Int = 0,
    val unreadJumpMessageId: String? = null,
    val roomAttentionKinds: Map<String, ChatAttentionKind> = emptyMap(),
    val userConversationAttentionKinds: Map<String, ChatAttentionKind> = emptyMap(),
    val chatAvailable: Boolean = false,
    val isStreamingMessages: Boolean = false,
    val streamingErrorMessage: String? = null,
    val errorMessage: String? = null,
    val messageErrorMessage: String? = null,
    val messageSearchErrorMessage: String? = null,
    val memberErrorMessage: String? = null,
    val roomManagementMessage: String? = null,
    val requiresRelogin: Boolean = false,
    val pinnedRoomIds: Set<String> = emptySet(),
    val pinnedUserConversationIds: Set<String> = emptySet(),
)

enum class ChatComposerAttachmentKind {
    Photo,
    File,
}

data class ChatComposerAttachment(
    val id: String,
    val kind: ChatComposerAttachmentKind,
    val file: DriveFile,
)

enum class ChatAttentionKind(
    val label: String,
    val shortLabel: String,
    val icon: String,
) {
    SpecialCare("特别关心", "特别关心", "💗"),
    Mention("有人 @ 你", "@ 你", "@"),
    Reply("有人回复你", "回复", "↩"),
    Quote("有人引用你", "引用", "❞"),
}

data class SpecialCareChatToast(
    val messageId: String,
    val roomId: String,
    val chatUserId: String? = null,
    val userId: String,
    val displayName: String,
    val previewText: String,
    val createdAtLabel: String = "",
    val avatarUrl: String? = null,
    val kind: ChatAttentionKind = ChatAttentionKind.SpecialCare,
)

class ChatStateHolder(
    private val repository: ChatRepository,
    private val driveFileRepository: DriveFileRepository? = null,
    private val streamingRepository: ChatStreamingRepository? = null,
    private val relationshipRepository: UserRelationshipRepository? = null,
    private val scope: CoroutineScope,
    private val accountIdProvider: () -> String? = { null },
    private val unreadStore: ChatUnreadStore = NoopChatUnreadStore,
    private val currentUserProvider: () -> User? = { null },
) {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState
    private var pendingMediaUploads: List<DriveFileUpload> = emptyList()
    private var streamingJob: Job? = null
    private var isRefreshingRoomsQuietly: Boolean = false
    private var isRefreshingUserConversationsQuietly: Boolean = false
    private var isRefreshingMessagesQuietly: Boolean = false
    private var isRefreshingSpecialCareMessagesQuietly: Boolean = false
    private var hasUserConversationSnapshot: Boolean = false
    private var hiddenUserConversationLatestMessageIds: Map<String, String?> = emptyMap()
    private var specialCareRoomLatestMessageIds: Map<String, String> = emptyMap()

    private fun currentAccountId(): String? = accountIdProvider()?.trim()?.takeIf { it.isNotEmpty() }

    private fun localUnreadSnapshot(): ChatUnreadSnapshot {
        return currentAccountId()?.let(unreadStore::load) ?: ChatUnreadSnapshot()
    }

    private fun saveLocalUnreadSnapshot(snapshot: ChatUnreadSnapshot) {
        currentAccountId()?.let { accountId -> unreadStore.save(accountId, snapshot) }
    }

    private fun clearLocalRoomUnread(roomId: String) {
        currentAccountId()?.let { accountId -> unreadStore.clearRoom(accountId, roomId) }
    }

    private fun clearLocalUserUnread(userId: String) {
        currentAccountId()?.let { accountId -> unreadStore.clearUser(accountId, userId) }
    }

    fun updateSpecialCareUsers(userIds: Set<String>) {
        val cleanUserIds = userIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        val current = state.value
        if (
            current.specialCareUserIds == cleanUserIds &&
            current.specialCareToast?.let { toast -> toast.kind != ChatAttentionKind.SpecialCare || toast.userId in cleanUserIds } != false
        ) {
            return
        }
        if (cleanUserIds != current.specialCareUserIds) {
            specialCareRoomLatestMessageIds = emptyMap()
        }
        mutableState.update {
            it.copy(
                specialCareUserIds = cleanUserIds,
                specialCareToast = it.specialCareToast?.takeIf { toast ->
                    toast.kind != ChatAttentionKind.SpecialCare || toast.userId in cleanUserIds
                },
            )
        }
    }

    fun dismissSpecialCareToast() {
        if (state.value.specialCareToast == null) return
        mutableState.update { it.copy(specialCareToast = null) }
    }

    fun openSpecialCareToast() {
        val current = state.value
        val toast = current.specialCareToast ?: return
        val targetUserId = toast.chatUserId?.takeIf { it.isNotBlank() }
        val targetConversation = targetUserId
            ?.let { userId -> current.userConversations.firstOrNull { it.user.id == userId } }
        if (targetUserId != null) {
            if (current.selectedUserConversation?.user?.id != targetUserId && targetConversation == null) {
                mutableState.update {
                    it.copy(
                        specialCareToast = null,
                        specialCareJumpMessageId = null,
                        errorMessage = "目标私聊不在当前列表，请刷新聊天",
                        messageErrorMessage = "目标私聊不在当前列表，请刷新聊天",
                    )
                }
                return
            }
            mutableState.update {
                it.copy(
                    specialCareToast = null,
                    specialCareJumpMessageId = toast.messageId,
                    showingMembers = false,
                )
            }
            if (current.selectedUserConversation?.user?.id != targetUserId && targetConversation != null) {
                selectUserConversation(targetConversation)
            }
            return
        }

        val targetRoom = current.rooms.firstOrNull { it.id == toast.roomId }
        if (current.selectedRoom?.id != toast.roomId && targetRoom == null) {
            mutableState.update {
                it.copy(
                    specialCareToast = null,
                    specialCareJumpMessageId = null,
                    errorMessage = "目标聊天室不在当前列表，请刷新聊天室",
                    messageErrorMessage = "目标聊天室不在当前列表，请刷新聊天室",
                )
            }
            return
        }
        mutableState.update {
            it.copy(
                specialCareToast = null,
                specialCareJumpMessageId = toast.messageId,
                showingMembers = false,
            )
        }
        if (current.selectedRoom?.id != toast.roomId && targetRoom != null) {
            selectRoom(targetRoom)
        }
    }

    fun consumeSpecialCareJump() {
        if (state.value.specialCareJumpMessageId == null) return
        mutableState.update { it.copy(specialCareJumpMessageId = null) }
    }

    fun consumeUnreadJump() {
        if (
            state.value.unreadJumpMessageId == null &&
            state.value.selectedRoomUnreadCount == 0 &&
            state.value.selectedUserUnreadCount == 0
        ) {
            return
        }
        mutableState.update {
            it.copy(
                selectedRoomUnreadCount = 0,
                selectedUserUnreadCount = 0,
                unreadJumpMessageId = null,
            )
        }
    }

    fun updateAvailability(chatAvailable: Boolean) {
        if (!chatAvailable) {
            stopMessageStreaming()
            isRefreshingUserConversationsQuietly = false
            hasUserConversationSnapshot = false
        }
        mutableState.update {
            if (!chatAvailable) {
                ChatUiState(
                    chatAvailable = false,
                    errorMessage = null,
                )
            } else {
                it.copy(
                    chatAvailable = true,
                    errorMessage = null,
                    messageErrorMessage = null,
                    memberErrorMessage = null,
                    streamingErrorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun refresh() {
        if (!state.value.chatAvailable) {
            mutableState.update {
                it.copy(errorMessage = "实例未启用聊天")
            }
            return
        }
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(
                isLoading = true,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val roomResult = repository.refresh()
            applyResult(roomResult, loadingMore = false)
            if (roomResult is ChatRepositoryResult.Success) {
                refreshRoomExtras()
                applyUserConversationResult(
                    result = repository.refreshUserConversations(),
                    markSelectedUserRead = state.value.selectedUserConversation != null,
                )
            }
        }
    }

    fun refreshRoomExtras() {
        val current = state.value
        if (!current.chatAvailable || current.isLoadingRoomExtras) return
        mutableState.update {
            it.copy(isLoadingRoomExtras = true, roomManagementMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val ownedResult = repository.refreshOwnedRooms()
            val inboxResult = repository.refreshInvitationInbox()
            val outboxResult = repository.refreshInvitationOutbox()
            applyRoomExtrasResult(ownedResult, inboxResult, outboxResult)
        }
    }

    fun refreshUserConversationsQuietly(markSelectedUserRead: Boolean = false) {
        val current = state.value
        if (isRefreshingUserConversationsQuietly || !current.chatAvailable || current.isLoading) return
        isRefreshingUserConversationsQuietly = true
        scope.launch {
            try {
                applyUserConversationResult(
                    result = repository.refreshUserConversations(),
                    markSelectedUserRead = markSelectedUserRead,
                )
            } finally {
                isRefreshingUserConversationsQuietly = false
            }
        }
    }

    fun loadMore() {
        val current = state.value
        if (
            !current.chatAvailable ||
            current.isLoading ||
            current.isLoadingMore ||
            current.endReached ||
            current.rooms.isEmpty()
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(repository.loadMore(current.rooms), loadingMore = true)
        }
    }

    fun selectRoom(room: ChatRoom) {
        if (!state.value.chatAvailable) {
            mutableState.update {
                it.copy(messageErrorMessage = "实例未启用聊天")
            }
            return
        }

        mutableState.update {
            val roomInList = it.rooms.any { existing -> existing.id == room.id }
            val nextRooms = if (roomInList) {
                it.rooms.markChatRoomRead(room.id)
            } else {
                (listOf(room.copy(unreadCount = 0)) + it.rooms)
                    .sortedByPinnedIds(it.pinnedRoomIds) { existing -> existing.id }
            }
            it.copy(
                rooms = nextRooms,
                selectedRoom = room.copy(unreadCount = 0),
                selectedUserConversation = null,
                roomAttentionKinds = it.roomAttentionKinds - room.id,
                messages = emptyList(),
                messageSearchResults = emptyList(),
                messageSearchQuery = "",
                messageSearchServerUntilId = null,
                members = emptyList(),
                selectedRoomUnreadCount = room.unreadCount.coerceAtLeast(0),
                selectedUserUnreadCount = 0,
                unreadJumpMessageId = null,
                messagesEndReached = false,
                messageSearchEndReached = true,
                membersEndReached = false,
                showingMembers = false,
                isLoadingMessages = false,
                isLoadingOlderMessages = false,
                isSearchingMessages = false,
                isLoadingMoreMessageSearch = false,
                isLoadingMembers = false,
                isLoadingMoreMembers = false,
                isSendingMessage = false,
                messageDraft = "",
                replyingMessage = null,
                quotedMessage = null,
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
                messageErrorMessage = null,
                messageSearchErrorMessage = null,
                memberErrorMessage = null,
                streamingErrorMessage = null,
                requiresRelogin = false,
            )
        }
        clearLocalRoomUnread(room.id)
        startMessageStreaming(room.id)
        restoreCachedRoomMessages(room.id)
        refreshMessages()
    }

    fun toggleRoomPinned(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        mutableState.update {
            val nextPinnedRoomIds = it.pinnedRoomIds.toggleMembership(cleanRoomId)
            it.copy(
                pinnedRoomIds = nextPinnedRoomIds,
                rooms = it.rooms.sortedByPinnedIds(nextPinnedRoomIds) { room -> room.id },
                requiresRelogin = false,
            )
        }
    }

    fun toggleUserConversationPinned(userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return
        mutableState.update {
            val nextPinnedUserConversationIds = it.pinnedUserConversationIds.toggleMembership(cleanUserId)
            it.copy(
                pinnedUserConversationIds = nextPinnedUserConversationIds,
                userConversations = it.userConversations.sortedByPinnedIds(nextPinnedUserConversationIds) { conversation ->
                    conversation.user.id
                },
                requiresRelogin = false,
            )
        }
    }

    fun deleteUserConversation(userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return
        val current = state.value
        val hiddenLatestMessageId = current.userConversations
            .firstOrNull { it.user.id == cleanUserId }
            ?.latestMessage
            ?.id
        hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds + (cleanUserId to hiddenLatestMessageId)
        val deletingSelectedConversation = current.selectedUserConversation?.user?.id == cleanUserId
        if (deletingSelectedConversation) {
            stopMessageStreaming()
        }
        mutableState.update {
            it.copy(
                userConversations = it.userConversations.filterNot { conversation -> conversation.user.id == cleanUserId },
                pinnedUserConversationIds = it.pinnedUserConversationIds - cleanUserId,
                userConversationAttentionKinds = it.userConversationAttentionKinds - cleanUserId,
                selectedUserConversation = it.selectedUserConversation?.takeIf { conversation ->
                    conversation.user.id != cleanUserId
                },
                messages = if (deletingSelectedConversation) emptyList() else it.messages,
                members = if (deletingSelectedConversation) emptyList() else it.members,
                selectedUserUnreadCount = if (deletingSelectedConversation) 0 else it.selectedUserUnreadCount,
                unreadJumpMessageId = if (deletingSelectedConversation) null else it.unreadJumpMessageId,
                isLoadingMessages = if (deletingSelectedConversation) false else it.isLoadingMessages,
                isLoadingOlderMessages = if (deletingSelectedConversation) false else it.isLoadingOlderMessages,
                isSendingMessage = if (deletingSelectedConversation) false else it.isSendingMessage,
                messageDraft = if (deletingSelectedConversation) "" else it.messageDraft,
                replyingMessage = if (deletingSelectedConversation) null else it.replyingMessage,
                quotedMessage = if (deletingSelectedConversation) null else it.quotedMessage,
                attachedFile = if (deletingSelectedConversation) null else it.attachedFile,
                attachments = if (deletingSelectedConversation) emptyList() else it.attachments,
                messageErrorMessage = null,
                streamingErrorMessage = if (deletingSelectedConversation) null else it.streamingErrorMessage,
                requiresRelogin = false,
            )
        }
        scope.launch {
            repository.deleteCachedUserMessages(cleanUserId)
        }
    }

    fun selectUserConversation(conversation: ChatUserConversation) {
        if (!state.value.chatAvailable) {
            mutableState.update {
                it.copy(messageErrorMessage = "实例未启用聊天")
            }
            return
        }

        hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds - conversation.user.id
        mutableState.update {
            it.copy(
                userConversations = it.userConversations
                    .ensureChatUserConversation(conversation)
                    .markChatUserConversationRead(conversation.user.id)
                    .sortedByPinnedIds(it.pinnedUserConversationIds) { item -> item.user.id },
                selectedRoom = null,
                selectedUserConversation = conversation.copy(unreadCount = 0),
                userConversationAttentionKinds = it.userConversationAttentionKinds - conversation.user.id,
                messages = emptyList(),
                messageSearchResults = emptyList(),
                messageSearchQuery = "",
                messageSearchServerUntilId = null,
                members = emptyList(),
                selectedRoomUnreadCount = 0,
                selectedUserUnreadCount = conversation.unreadCount.coerceAtLeast(0),
                unreadJumpMessageId = null,
                messagesEndReached = false,
                messageSearchEndReached = true,
                membersEndReached = false,
                showingMembers = false,
                isLoadingMessages = false,
                isLoadingOlderMessages = false,
                isSearchingMessages = false,
                isLoadingMoreMessageSearch = false,
                isLoadingMembers = false,
                isLoadingMoreMembers = false,
                isSendingMessage = false,
                messageDraft = "",
                replyingMessage = null,
                quotedMessage = null,
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
                messageErrorMessage = null,
                messageSearchErrorMessage = null,
                memberErrorMessage = null,
                streamingErrorMessage = null,
                requiresRelogin = false,
            )
        }
        clearLocalUserUnread(conversation.user.id)
        startUserMessageStreaming(conversation.user.id)
        restoreCachedUserMessages(conversation.user.id)
        refreshMessages()
    }

    fun openUserConversation(
        user: cc.hhhl.client.model.User,
        jumpMessageId: String? = null,
    ) {
        val cleanUserId = user.id.trim()
        if (cleanUserId.isEmpty()) return
        hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds - cleanUserId
        val existing = state.value.userConversations.firstOrNull { it.user.id == cleanUserId }
        selectUserConversation(existing ?: ChatUserConversation(user = user))
        jumpMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { messageId ->
            mutableState.update { it.copy(specialCareJumpMessageId = messageId) }
        }
    }

    fun openRoomById(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        if (!state.value.chatAvailable) {
            mutableState.update {
                it.copy(errorMessage = "实例未启用聊天", messageErrorMessage = "实例未启用聊天")
            }
            return
        }

        state.value.rooms.firstOrNull { it.id == cleanRoomId }?.let { room ->
            selectRoom(room)
            return
        }
        if (state.value.isManagingRoom) return

        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = "正在加入聊天室",
                errorMessage = null,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val shownRoom = when (val showResult = repository.showRoom(cleanRoomId)) {
                is ChatRoomMutationRepositoryResult.RoomSaved -> showResult.room
                ChatRoomMutationRepositoryResult.Unauthorized -> {
                    completeOpenRoomByIdWithError("登录已失效，请重新登录", requiresRelogin = true)
                    return@launch
                }
                is ChatRoomMutationRepositoryResult.ValidationError -> {
                    completeOpenRoomByIdWithError(showResult.message)
                    return@launch
                }
                is ChatRoomMutationRepositoryResult.Error -> {
                    completeOpenRoomByIdWithError(showResult.message)
                    return@launch
                }
                else -> placeholderRoom(cleanRoomId)
            }

            val canOpen = when (val joinResult = repository.joinRoom(cleanRoomId)) {
                is ChatRoomMutationRepositoryResult.ActionCompleted,
                is ChatRoomMutationRepositoryResult.RoomSaved -> true
                ChatRoomMutationRepositoryResult.Unauthorized -> {
                    completeOpenRoomByIdWithError("登录已失效，请重新登录", requiresRelogin = true)
                    false
                }
                is ChatRoomMutationRepositoryResult.ValidationError -> {
                    completeOpenRoomByIdWithError(joinResult.message)
                    false
                }
                is ChatRoomMutationRepositoryResult.Error -> {
                    if (joinResult.message.isAlreadyJoinedRoomMessage()) {
                        true
                    } else {
                        completeOpenRoomByIdWithError(joinResult.message)
                        false
                    }
                }
                else -> true
            }
            if (!canOpen) return@launch

            val refreshedRoom = when (val refreshResult = repository.refresh()) {
                is ChatRepositoryResult.Success -> {
                    applyResult(refreshResult, loadingMore = false)
                    refreshResult.rooms.firstOrNull { it.id == cleanRoomId }
                }
                else -> null
            }
            mutableState.update {
                it.copy(isManagingRoom = false, roomManagementMessage = "已加入聊天室", requiresRelogin = false)
            }
            selectRoom(refreshedRoom ?: shownRoom.withStableMembershipId())
        }
    }

    private fun completeOpenRoomByIdWithError(
        message: String,
        requiresRelogin: Boolean = false,
    ) {
        mutableState.update {
            it.copy(
                isManagingRoom = false,
                roomManagementMessage = message,
                errorMessage = message,
                messageErrorMessage = message,
                requiresRelogin = requiresRelogin,
            )
        }
    }

    private fun restoreCachedRoomMessages(roomId: String) {
        scope.launch {
            applyCachedRoomMessageResult(
                roomId = roomId,
                result = repository.restoreCachedMessages(roomId),
            )
        }
    }

    private fun restoreCachedUserMessages(userId: String) {
        scope.launch {
            applyCachedUserMessageResult(
                userId = userId,
                result = repository.restoreCachedUserMessages(userId),
            )
        }
    }

    fun closeRoom() {
        stopMessageStreaming()
        mutableState.update {
            it.copy(
                selectedRoom = null,
                selectedUserConversation = null,
                messages = emptyList(),
                members = emptyList(),
                selectedRoomUnreadCount = 0,
                selectedUserUnreadCount = 0,
                unreadJumpMessageId = null,
                isLoadingMessages = false,
                isLoadingOlderMessages = false,
                isLoadingMembers = false,
                isLoadingMoreMembers = false,
                isSendingMessage = false,
                messagesEndReached = false,
                membersEndReached = false,
                showingMembers = false,
                messageDraft = "",
                replyingMessage = null,
                quotedMessage = null,
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
                messageErrorMessage = null,
                memberErrorMessage = null,
                streamingErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun startMessageStreaming(roomId: String = state.value.selectedRoom?.id.orEmpty()) {
        val streamRepository = streamingRepository ?: return
        val cleanRoomId = roomId.trim()
        if (!state.value.chatAvailable || cleanRoomId.isEmpty()) return

        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamRepository.streamRoomMessages(cleanRoomId).collect { event ->
                applyRoomStreamingEvent(cleanRoomId, event)
            }
        }
    }

    fun startUserMessageStreaming(userId: String = state.value.selectedUserConversation?.user?.id.orEmpty()) {
        val streamRepository = streamingRepository ?: return
        val cleanUserId = userId.trim()
        if (!state.value.chatAvailable || cleanUserId.isEmpty()) return

        streamingJob?.cancel()
        streamingJob = scope.launch {
            streamRepository.streamUserMessages(cleanUserId).collect { event ->
                applyUserStreamingEvent(cleanUserId, event)
            }
        }
    }

    fun stopMessageStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        mutableState.update {
            it.copy(isStreamingMessages = false)
        }
    }

    fun refreshRoomsQuietly(markSelectedRoomRead: Boolean = false) {
        val current = state.value
        if (
            isRefreshingRoomsQuietly ||
            !current.chatAvailable ||
            current.isLoading ||
            current.isLoadingMore
        ) {
            return
        }
        isRefreshingRoomsQuietly = true
        scope.launch {
            try {
                applyBackgroundRoomRefreshResult(
                    result = repository.refresh(),
                    markSelectedRoomRead = markSelectedRoomRead,
                )
            } finally {
                isRefreshingRoomsQuietly = false
            }
        }
    }

    fun refreshSelectedMessagesQuietly() {
        val current = state.value
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id
        if (roomId == null && userId == null) return
        if (
            isRefreshingMessagesQuietly ||
            !current.chatAvailable ||
            current.isLoadingMessages ||
            current.isLoadingOlderMessages ||
            current.isSendingMessage
        ) {
            return
        }
        isRefreshingMessagesQuietly = true
        scope.launch {
            try {
                if (roomId != null) {
                    applyBackgroundRoomMessageRefreshResult(
                        roomId = roomId,
                        result = repository.refreshMessages(roomId),
                    )
                } else if (userId != null) {
                    applyBackgroundUserMessageRefreshResult(
                        userId = userId,
                        result = repository.refreshUserMessages(userId),
                    )
                }
            } finally {
                isRefreshingMessagesQuietly = false
            }
        }
    }

    fun refreshSpecialCareMessagesQuietly() {
        val current = state.value
        val specialCareUserIds = current.specialCareUserIds
        val currentUser = currentUserProvider()
        val canDetectDirectAttention = currentUser?.id?.isNotBlank() == true
        if (
            isRefreshingSpecialCareMessagesQuietly ||
            !current.chatAvailable ||
            (specialCareUserIds.isEmpty() && !canDetectDirectAttention)
        ) {
            return
        }
        isRefreshingSpecialCareMessagesQuietly = true
        scope.launch {
            try {
                when (val result = repository.refresh()) {
                    is ChatRepositoryResult.Success -> {
                        val currentRoomsById = current.rooms.associateBy { room -> room.id }
                        var nextSeenMessageIds = specialCareRoomLatestMessageIds
                            .filterKeys { roomId -> result.rooms.any { room -> room.id == roomId } }
                        var nextToast: SpecialCareChatToast? = null
                        for (room in result.rooms.take(SPECIAL_CARE_ROOM_SCAN_LIMIT)) {
                            val previousMessageId = nextSeenMessageIds[room.id]
                            val messages = when (val messagesResult = repository.refreshMessages(room.id)) {
                                is ChatMessageRepositoryResult.Success -> messagesResult.messages.ensureChronologicalMessages()
                                is ChatMessageRepositoryResult.Created,
                                is ChatMessageRepositoryResult.Deleted,
                                is ChatMessageRepositoryResult.Error,
                                ChatMessageRepositoryResult.ReactionUpdated,
                                ChatMessageRepositoryResult.Unauthorized,
                                -> null
                            } ?: continue
                            val latestMessageId = messages.lastOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                            val toast = messages.firstChatAttentionToastAfter(
                                previousMessageId = previousMessageId,
                                specialCareUserIds = specialCareUserIds,
                                currentUser = currentUser,
                            )
                            nextSeenMessageIds = nextSeenMessageIds + (room.id to latestMessageId)
                            val shouldNotifyFirstDetectedMessage = previousMessageId == null &&
                                room.hasNewUnreadAttentionComparedWith(currentRoomsById[room.id])
                            if (
                                (!shouldNotifyFirstDetectedMessage && previousMessageId == null) ||
                                toast == null ||
                                nextToast != null
                            ) {
                                continue
                            }
                            nextToast = toast
                        }
                        specialCareRoomLatestMessageIds = nextSeenMessageIds
                        nextToast?.let { toast ->
                            mutableState.update {
                                it.copy(
                                    specialCareToast = it.specialCareToast.withStableSpecialCareToast(toast),
                                    roomAttentionKinds = it.roomAttentionKinds.withAttentionToast(toast),
                                )
                            }
                        }
                    }
                    ChatRepositoryResult.Unauthorized -> mutableState.update {
                        it.copy(
                            errorMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is ChatRepositoryResult.Error -> Unit
                }
            } finally {
                isRefreshingSpecialCareMessagesQuietly = false
            }
        }
    }

    fun refreshMessages() {
        val current = state.value
        val room = current.selectedRoom
        val userConversation = current.selectedUserConversation
        if (room == null && userConversation == null) return
        if (state.value.isLoadingMessages) return

        mutableState.update {
            it.copy(
                isLoadingMessages = true,
                messagesEndReached = false,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            if (room != null) {
                applyRoomMessageResult(
                    roomId = room.id,
                    result = repository.refreshMessages(room.id),
                    loadingMore = false,
                )
            } else if (userConversation != null) {
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.refreshUserMessages(userConversation.user.id),
                    loadingMore = false,
                )
            }
        }
    }

    fun loadOlderMessages() {
        val current = state.value
        val room = current.selectedRoom
        val userConversation = current.selectedUserConversation
        if (room == null && userConversation == null) return
        if (
            current.isLoadingMessages ||
            current.isLoadingOlderMessages ||
            current.messages.isEmpty() ||
            current.messagesEndReached
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingOlderMessages = true,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            if (room != null) {
                applyRoomMessageResult(
                    roomId = room.id,
                    result = repository.loadMoreMessages(room.id, current.messages),
                    loadingMore = true,
                )
            } else if (userConversation != null) {
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.loadMoreUserMessages(userConversation.user.id, current.messages),
                    loadingMore = true,
                )
            }
        }
    }

    fun searchMessages(query: String) {
        val current = state.value
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            mutableState.update {
                it.copy(
                    messageSearchQuery = "",
                    messageSearchServerUntilId = null,
                    messageSearchResults = emptyList(),
                    messageSearchEndReached = true,
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        if (current.isSearchingMessages) return
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id
        if (roomId == null && userId == null) return

        mutableState.update {
            it.copy(
                messageSearchQuery = cleanQuery,
                messageSearchServerUntilId = null,
                messageSearchResults = emptyList(),
                messageSearchEndReached = false,
                isSearchingMessages = true,
                isLoadingMoreMessageSearch = false,
                messageSearchErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            try {
                applyMessageSearchResult(
                    query = cleanQuery,
                    roomId = roomId,
                    userId = userId,
                    result = withTimeoutOrNull(CHAT_MESSAGE_SEARCH_TIMEOUT_MS) {
                        repository.searchMessages(
                            query = cleanQuery,
                            roomId = roomId,
                            userId = userId,
                            currentConversationMessages = current.messages,
                        )
                    } ?: ChatMessageRepositoryResult.Error("搜索消息超时，请稍后重试"),
                    loadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyMessageSearchResult(
                    query = cleanQuery,
                    roomId = roomId,
                    userId = userId,
                    result = ChatMessageRepositoryResult.Error(error.toChatSearchErrorMessage()),
                    loadingMore = false,
                )
            }
        }
    }

    fun loadMoreMessageSearch() {
        val current = state.value
        val cleanQuery = current.messageSearchQuery.trim()
        if (
            cleanQuery.isBlank() ||
            current.isSearchingMessages ||
            current.isLoadingMoreMessageSearch ||
            current.messageSearchEndReached ||
            current.messageSearchResults.isEmpty()
        ) {
            return
        }
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id
        if (roomId == null && userId == null) return

        mutableState.update {
            it.copy(
                isLoadingMoreMessageSearch = true,
                messageSearchErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            try {
                applyMessageSearchResult(
                    query = cleanQuery,
                    roomId = roomId,
                    userId = userId,
                    result = withTimeoutOrNull(CHAT_MESSAGE_SEARCH_TIMEOUT_MS) {
                        repository.searchMessages(
                            query = cleanQuery,
                            roomId = roomId,
                            userId = userId,
                            serverUntilId = current.messageSearchServerUntilId,
                            currentResults = current.messageSearchResults,
                            currentConversationMessages = current.messages,
                        )
                    } ?: ChatMessageRepositoryResult.Error("搜索消息超时，请稍后重试"),
                    loadingMore = true,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyMessageSearchResult(
                    query = cleanQuery,
                    roomId = roomId,
                    userId = userId,
                    result = ChatMessageRepositoryResult.Error(error.toChatSearchErrorMessage()),
                    loadingMore = true,
                )
            }
        }
    }

    fun showMessages() {
        mutableState.update {
            it.copy(showingMembers = false, memberErrorMessage = null, requiresRelogin = false)
        }
    }

    fun showMembers() {
        val current = state.value
        val room = current.selectedRoom ?: return
        if (current.isLoadingMembers) return

        if (!current.showingMembers && current.members.hasOfficialChatRoomMembers()) {
            mutableState.update {
                it.copy(showingMembers = true, memberErrorMessage = null, requiresRelogin = false)
            }
            return
        }

        mutableState.update {
            it.copy(
                showingMembers = true,
                isLoadingMembers = true,
                membersEndReached = false,
                memberErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = repository.refreshMembers(room.id)
            applyMemberResult(
                roomId = room.id,
                result = result,
                loadingMore = false,
            )
        }
    }

    fun loadMoreMembers() {
        val current = state.value
        val room = current.selectedRoom ?: return
        if (
            !current.showingMembers ||
            current.isLoadingMembers ||
            current.isLoadingMoreMembers ||
            current.members.isEmpty() ||
            current.membersEndReached
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMoreMembers = true,
                memberErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyMemberResult(
                roomId = room.id,
                result = repository.loadMoreMembers(room.id, current.members),
                loadingMore = true,
            )
        }
    }

    fun createRoom(
        name: String,
        description: String,
    ) {
        if (!state.value.chatAvailable || state.value.isManagingRoom) return
        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.createRoom(name, description),
                selectSavedRoom = false,
                successMessage = "聊天室已创建",
            )
        }
    }

    fun joinInvitedRoom(invitation: ChatRoomInvitation) {
        if (!state.value.chatAvailable || state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }
        scope.launch {
            applyRoomMutationResult(
                result = repository.joinRoom(invitation.room.id),
                successMessage = "已加入聊天室",
            )
            refreshRoomExtras()
        }
    }

    fun ignoreRoomInvitation(roomId: String) {
        if (!state.value.chatAvailable || state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }
        scope.launch {
            applyRoomMutationResult(
                result = repository.ignoreInvitation(roomId),
                successMessage = "已忽略邀请",
            )
            refreshRoomExtras()
        }
    }

    fun updateSelectedRoom(
        name: String,
        description: String,
    ) {
        val room = state.value.selectedRoom ?: return
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = null,
                memberErrorMessage = null,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.updateRoom(room.id, name, description),
                selectSavedRoom = true,
                successMessage = "聊天室已更新",
            )
        }
    }

    fun inviteSelectedRoomMember(userId: String) {
        val room = state.value.selectedRoom ?: return
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = null,
                memberErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.inviteRoomMember(room.id, userId),
                successMessage = "已发送邀请",
            )
        }
    }

    fun leaveSelectedRoom() {
        val room = state.value.selectedRoom ?: return
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.leaveRoom(room.id),
                successMessage = "已退出聊天室",
            )
        }
    }

    fun deleteSelectedRoom() {
        val room = state.value.selectedRoom ?: return
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.deleteRoom(room.id),
                successMessage = "聊天室已删除",
            )
        }
    }

    fun muteSelectedRoom(muted: Boolean) {
        val room = state.value.selectedRoom ?: return
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyRoomMutationResult(
                result = repository.muteRoom(room.id, muted),
                successMessage = if (muted) "聊天室已静音" else "已取消静音",
            )
        }
    }

    fun updateMessageDraft(text: String) {
        val current = state.value
        if (current.messageDraft == text && current.messageErrorMessage == null && !current.requiresRelogin) {
            return
        }
        mutableState.update {
            it.copy(messageDraft = text, messageErrorMessage = null, requiresRelogin = false)
        }
    }

    fun quoteMessage(messageId: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val quote = message.toChatMessageQuote()
        val current = state.value
        if (current.quotedMessage == quote && current.messageErrorMessage == null && !current.requiresRelogin) {
            return
        }
        mutableState.update {
            it.copy(
                quotedMessage = quote,
                replyingMessage = null,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun replyMessage(messageId: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val reply = message.toChatMessageQuote()
        val current = state.value
        if (current.replyingMessage == reply && current.messageErrorMessage == null && !current.requiresRelogin) {
            return
        }
        mutableState.update {
            it.copy(
                replyingMessage = reply,
                quotedMessage = null,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun cancelQuotedMessage() {
        val current = state.value
        if (
            current.replyingMessage == null &&
            current.quotedMessage == null &&
            current.messageErrorMessage == null &&
            !current.requiresRelogin
        ) {
            return
        }
        mutableState.update {
            it.copy(
                replyingMessage = null,
                quotedMessage = null,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun reportMessage(messageId: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val reportRepository = relationshipRepository
        if (reportRepository == null) {
            mutableState.update {
                it.copy(messageErrorMessage = "当前客户端无法提交举报", requiresRelogin = false)
            }
            return
        }
        if (message.fromUser.id.isBlank()) {
            mutableState.update {
                it.copy(messageErrorMessage = "无法举报该消息", requiresRelogin = false)
            }
            return
        }
        mutableState.update {
            it.copy(messageErrorMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val comment = "https://dc.hhhl.cc/chat/messages/${message.id}\n-----\n客户端举报聊天消息"
            when (val result = reportRepository.reportUser(message.fromUser.id, comment)) {
                UserRelationshipRepositoryResult.Success -> mutableState.update {
                    it.copy(roomManagementMessage = "已提交举报", requiresRelogin = false)
                }
                UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(messageErrorMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is UserRelationshipRepositoryResult.Error -> mutableState.update {
                    it.copy(messageErrorMessage = result.message, requiresRelogin = false)
                }
                is UserRelationshipRepositoryResult.RelationLoaded -> Unit
            }
        }
    }

    fun uploadMedia(upload: DriveFileUpload) {
        if (state.value.isUploadingMedia) {
            pendingMediaUploads = pendingMediaUploads + upload
            return
        }
        uploadMediaNow(upload)
    }

    private fun uploadMediaNow(upload: DriveFileUpload) {
        val uploadRepository = driveFileRepository
        if (uploadRepository == null) {
            mutableState.update {
                it.copy(messageErrorMessage = "无法上传媒体")
            }
            return
        }

        mutableState.update {
            it.copy(
                isUploadingMedia = true,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = uploadRepository.upload(upload)) {
                is DriveFileRepositoryResult.Success -> mutableState.update {
                    val attachments = (it.attachments + result.file.toChatComposerAttachment())
                        .distinctBy { attachment -> attachment.id }
                    it.copy(
                        attachedFile = attachments.lastOrNull()?.file,
                        attachments = attachments,
                        isUploadingMedia = false,
                        messageErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isUploadingMedia = false,
                        messageErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update {
                    it.copy(
                        isUploadingMedia = false,
                        messageErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is DriveFileRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isUploadingMedia = false,
                        messageErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
            uploadPendingMediaIfAny()
        }
    }

    fun removeAttachedFile() {
        pendingMediaUploads = emptyList()
        mutableState.update {
            it.copy(
                attachedFile = null,
                attachments = emptyList(),
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun attachDriveFile(file: DriveFile) {
        pendingMediaUploads = emptyList()
        mutableState.update {
            val attachments = (it.attachments + file.toChatComposerAttachment())
                .distinctBy { attachment -> attachment.id }
            it.copy(
                attachedFile = attachments.lastOrNull()?.file,
                attachments = attachments,
                isUploadingMedia = false,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun reportMediaUploadError(message: String) {
        val cleanMessage = message.trim().takeIf { it.isNotEmpty() } ?: "无法读取所选文件"
        mutableState.update {
            it.copy(
                messageErrorMessage = cleanMessage,
                requiresRelogin = false,
            )
        }
    }

    private fun uploadPendingMediaIfAny() {
        val nextUpload = pendingMediaUploads.firstOrNull() ?: return
        pendingMediaUploads = pendingMediaUploads.drop(1)
        uploadMediaNow(nextUpload)
    }

    fun updateDefaultReaction(defaultReaction: String) {
        val clean = defaultReaction.takeIf { it.isNotBlank() } ?: return
        mutableState.update {
            it.copy(
                reactionOptions = (listOf(clean) + it.reactionOptions).distinct(),
                requiresRelogin = false,
            )
        }
    }

    fun updateReactionOptions(reactionOptions: List<String>) {
        val cleanOptions = reactionOptions
            .mapNotNull { reaction -> reaction.trim().takeIf { it.isNotEmpty() } }
            .distinct()
        if (cleanOptions.isEmpty()) return
        mutableState.update {
            val mergedOptions = (cleanOptions + it.reactionOptions).distinct()
            if (mergedOptions == it.reactionOptions) {
                it
            } else {
                it.copy(reactionOptions = mergedOptions, requiresRelogin = false)
            }
        }
    }

    fun sendMessage() {
        val current = state.value
        val room = current.selectedRoom
        val userConversation = current.selectedUserConversation
        if (room == null && userConversation == null) return
        val text = current.messageDraft.trim()
        val fileIds = current.sendableChatAttachmentFileIds()
        if (text.isBlank() && fileIds.isEmpty()) {
            mutableState.update {
                it.copy(messageErrorMessage = "请输入消息", requiresRelogin = false)
            }
            return
        }
        if (current.isSendingMessage) return

        mutableState.update {
            it.copy(
                isSendingMessage = true,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            if (room != null) {
                applyRoomMessageResult(
                    roomId = room.id,
                    result = repository.sendMessage(
                        roomId = room.id,
                        text = text,
                        fileIds = fileIds,
                        replyId = current.replyingMessage?.messageId,
                        quoteId = current.quotedMessage?.messageId,
                    ),
                    loadingMore = false,
                )
            } else if (userConversation != null) {
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.sendUserMessage(
                        userId = userConversation.user.id,
                        text = text,
                        fileId = fileIds.firstOrNull(),
                        replyId = current.replyingMessage?.messageId,
                        quoteId = current.quotedMessage?.messageId,
                    ),
                    loadingMore = false,
                )
            }
        }
    }

    fun reactToMessage(
        messageId: String,
        reaction: String = state.value.reactionOptions.firstOrNull() ?: "❤️",
    ) {
        updateMessageReaction(
            messageId = messageId,
            reaction = reaction,
            react = true,
        )
    }

    fun unreactToMessage(
        messageId: String,
        reaction: String,
    ) {
        updateMessageReaction(
            messageId = messageId,
            reaction = reaction,
            react = false,
        )
    }

    fun deleteMessage(messageId: String) {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isEmpty() || state.value.pendingMessageDeleteIds.contains(cleanMessageId)) return
        val current = state.value
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id
        if (roomId == null && userId == null) return

        mutableState.update {
            it.copy(
                pendingMessageDeleteIds = it.pendingMessageDeleteIds + cleanMessageId,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.deleteMessage(
                    messageId = cleanMessageId,
                    roomId = roomId,
                    userId = userId,
                )
            ) {
                is ChatMessageRepositoryResult.Deleted -> mutableState.update {
                    it.copy(
                        messages = it.messages.filterNot { message -> message.id == result.messageId },
                        messageSearchResults = it.messageSearchResults.filterNot { message -> message.id == result.messageId },
                        pendingMessageDeleteIds = it.pendingMessageDeleteIds - result.messageId,
                        messageErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        pendingMessageDeleteIds = it.pendingMessageDeleteIds - cleanMessageId,
                        messageErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ChatMessageRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        pendingMessageDeleteIds = it.pendingMessageDeleteIds - cleanMessageId,
                        messageErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is ChatMessageRepositoryResult.Success,
                is ChatMessageRepositoryResult.Created,
                ChatMessageRepositoryResult.ReactionUpdated,
                    -> mutableState.update {
                    it.copy(pendingMessageDeleteIds = it.pendingMessageDeleteIds - cleanMessageId)
                }
            }
        }
    }

    private fun updateMessageReaction(
        messageId: String,
        reaction: String,
        react: Boolean,
    ) {
        if (state.value.pendingMessageReactionIds.contains(messageId)) return

        mutableState.update {
            it.copy(
                pendingMessageReactionIds = it.pendingMessageReactionIds + messageId,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = if (react) {
                repository.reactToMessage(messageId, reaction)
            } else {
                repository.unreactToMessage(messageId, reaction)
            }
            applyReactionResult(
                messageId = messageId,
                reaction = reaction,
                react = react,
                result = result,
            )
        }
    }

    private fun applyResult(
        result: ChatRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatRepositoryResult.Success -> mutableState.update { current ->
                val localUnread = localUnreadSnapshot()
                val nextRooms = result.rooms
                    .mergeRoomUnreadCounts(
                        previous = current.rooms,
                        selectedRoomId = current.selectedRoom?.id,
                        localUnreadCounts = localUnread.roomCounts,
                        persistUnread = null,
                    )
                    .sortedByPinnedIds(current.pinnedRoomIds) { room -> room.id }
                current.copy(
                    rooms = nextRooms,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyRoomExtrasResult(
        ownedResult: ChatRepositoryResult,
        inboxResult: cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult,
        outboxResult: cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult,
    ) {
        mutableState.update { current ->
            var next = current.copy(isLoadingRoomExtras = false)
            when (ownedResult) {
                is ChatRepositoryResult.Success -> next = next.copy(ownedRooms = ownedResult.rooms)
                ChatRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is ChatRepositoryResult.Error -> next = next.copy(roomManagementMessage = ownedResult.message)
            }
            when (inboxResult) {
                is cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Success -> {
                    next = next.copy(roomInvitationInbox = inboxResult.invitations)
                }
                cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Error -> {
                    next = next.copy(roomManagementMessage = inboxResult.message)
                }
            }
            when (outboxResult) {
                is cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Success -> {
                    next = next.copy(roomInvitationOutbox = outboxResult.invitations)
                }
                cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult.Error -> {
                    next = next.copy(roomManagementMessage = outboxResult.message)
                }
            }
            next
        }
    }

    private fun applyBackgroundRoomRefreshResult(
        result: ChatRepositoryResult,
        markSelectedRoomRead: Boolean,
    ) {
        when (result) {
            is ChatRepositoryResult.Success -> mutableState.update { current ->
                val localUnread = localUnreadSnapshot()
                val selectedRoomId = current.selectedRoom?.id
                val remoteSelectedUnreadCount = selectedRoomId
                    ?.let { id -> result.rooms.firstOrNull { room -> room.id == id }?.unreadCount }
                    ?.coerceAtLeast(0)
                val refreshedRooms = result.rooms.mergeRoomUnreadCounts(
                    previous = current.rooms,
                    selectedRoomId = selectedRoomId.takeIf { markSelectedRoomRead },
                    localUnreadCounts = localUnread.roomCounts,
                    persistUnread = { counts ->
                        saveLocalUnreadSnapshot(
                            ChatUnreadSnapshot(
                                roomCounts = counts,
                                userCounts = localUnread.userCounts,
                            ),
                        )
                    },
                ).map { room ->
                    if (markSelectedRoomRead && room.id == selectedRoomId) {
                        clearLocalRoomUnread(room.id)
                        room.copy(unreadCount = 0)
                    } else {
                        room
                    }
                }
                val refreshedIds = refreshedRooms.mapTo(mutableSetOf()) { it.id }
                val nextRooms = refreshedRooms + current.rooms.filterNot { it.id in refreshedIds }
                val sortedRooms = nextRooms.sortedByPinnedIds(current.pinnedRoomIds) { room -> room.id }
                val selectedRoomUnreadCount = if (!markSelectedRoomRead && remoteSelectedUnreadCount != null) {
                    sortedRooms.firstOrNull { it.id == selectedRoomId }?.unreadCount ?: remoteSelectedUnreadCount
                } else {
                    current.selectedRoomUnreadCount
                }
                current.copy(
                    rooms = sortedRooms,
                    selectedRoom = current.selectedRoom?.let { selected ->
                        sortedRooms.firstOrNull { it.id == selected.id }?.let { room ->
                            if (markSelectedRoomRead) room.copy(unreadCount = 0) else room
                        } ?: selected
                    },
                    roomAttentionKinds = current.roomAttentionKinds.filterKeys { roomId ->
                        sortedRooms.any { room -> room.id == roomId }
                    }.let { attentionKinds ->
                        selectedRoomId
                            ?.takeIf { markSelectedRoomRead }
                            ?.let { roomId -> attentionKinds - roomId }
                            ?: attentionKinds
                    },
                    selectedRoomUnreadCount = selectedRoomUnreadCount,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatRepositoryResult.Error -> Unit
        }
    }

    private fun applyUserConversationResult(
        result: ChatUserConversationRepositoryResult,
        markSelectedUserRead: Boolean = false,
    ) {
        when (result) {
            is ChatUserConversationRepositoryResult.Success -> {
                val canNotifyNewMessages = hasUserConversationSnapshot
                val nextHiddenUserConversationLatestMessageIds =
                    hiddenUserConversationLatestMessageIds.filterHiddenUserConversationLatestMessageIds(
                        result.conversations,
                    )
                hiddenUserConversationLatestMessageIds = nextHiddenUserConversationLatestMessageIds
                mutableState.update { current ->
                    val localUnread = localUnreadSnapshot()
                    val currentSelectedUserId = current.selectedUserConversation?.user?.id
                    val selectedUserId = currentSelectedUserId.takeIf { markSelectedUserRead }
                    val hiddenUserIds = nextHiddenUserConversationLatestMessageIds.keys
                    val nextConversations = result.conversations
                        .mergeUserUnreadCounts(
                            previous = current.userConversations,
                            selectedUserId = selectedUserId,
                            localUnreadCounts = localUnread.userCounts,
                            persistUnread = { counts ->
                                saveLocalUnreadSnapshot(
                                    ChatUnreadSnapshot(
                                        roomCounts = localUnread.roomCounts,
                                        userCounts = counts,
                                    ),
                                )
                            },
                        )
                        .mergeChatUserConversations(current.userConversations)
                        .filterNot { it.user.id in hiddenUserIds }
                        .sortedByPinnedIds(current.pinnedUserConversationIds) { conversation -> conversation.user.id }
                    val attentionToast = if (canNotifyNewMessages) {
                        nextConversations.firstChatAttentionToastFrom(
                            previous = current.userConversations,
                            specialCareUserIds = current.specialCareUserIds,
                            currentUser = currentUserProvider(),
                        )
                    } else {
                        null
                    }
                    val nextAttentionKinds = current.userConversationAttentionKinds
                        .filterKeys { userId ->
                            nextConversations.any { conversation -> conversation.user.id == userId }
                        }
                        .let { attentionKinds ->
                            selectedUserId?.let { userId -> attentionKinds - userId } ?: attentionKinds
                        }
                        .withAttentionToast(attentionToast?.takeIf { toast -> toast.chatUserId != null })
                    current.copy(
                        userConversations = nextConversations,
                        selectedUserConversation = current.selectedUserConversation?.let { selected ->
                            nextConversations.firstOrNull { it.user.id == selected.user.id }
                                ?.let { if (it.user.id == selectedUserId) it.copy(unreadCount = 0) else it }
                                ?: selected
                        },
                        specialCareToast = current.specialCareToast.withStableSpecialCareToast(attentionToast),
                        userConversationAttentionKinds = nextAttentionKinds,
                        userConversationsEndReached = true,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                hasUserConversationSnapshot = true
            }
            ChatUserConversationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatUserConversationRepositoryResult.Error -> mutableState.update {
                it.copy(
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyRoomMessageResult(
        roomId: String,
        result: ChatMessageRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                val nextMessages = result.messages.ensureChronologicalMessages()
                val latestMessage = if (loadingMore) null else nextMessages.lastOrNull()
                val nextRooms = latestMessage?.let { message ->
                    it.rooms
                        .withChatRoomLatestMessage(roomId, message)
                        .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id }
                } ?: it.rooms
                it.copy(
                    rooms = nextRooms,
                    selectedRoom = latestMessage?.let { message -> it.selectedRoom?.withLatestMessageAt(message) }
                        ?: it.selectedRoom,
                    messages = nextMessages,
                    unreadJumpMessageId = if (!loadingMore) {
                        nextMessages.firstUnreadMessageId(it.selectedRoomUnreadCount)
                    } else {
                        it.unreadJumpMessageId
                    },
                    isLoadingMessages = false,
                    isLoadingOlderMessages = false,
                    messagesEndReached = result.endReached,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            is ChatMessageRepositoryResult.Created -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    rooms = it.rooms
                        .withChatRoomLatestMessage(roomId, result.message)
                        .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    selectedRoom = it.selectedRoom?.withLatestMessageAt(result.message),
                    messages = it.messages.withChronologicalMessage(result.message),
                    messageDraft = "",
                    replyingMessage = null,
                    quotedMessage = null,
                    attachedFile = null,
                    attachments = emptyList(),
                    isSendingMessage = false,
                    selectedRoomUnreadCount = 0,
                    unreadJumpMessageId = null,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.ReactionUpdated -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(requiresRelogin = false)
            }
            is ChatMessageRepositoryResult.Deleted -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    messages = it.messages.filterNot { message -> message.id == result.messageId },
                    messageSearchResults = it.messageSearchResults.filterNot { message -> message.id == result.messageId },
                    pendingMessageDeleteIds = it.pendingMessageDeleteIds - result.messageId,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isLoadingMessages = false,
                    isLoadingOlderMessages = false,
                    isSendingMessage = false,
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isLoadingMessages = if (loadingMore) it.isLoadingMessages else false,
                    isLoadingOlderMessages = false,
                    isSendingMessage = false,
                    messageErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyCachedRoomMessageResult(
        roomId: String,
        result: ChatMessageRepositoryResult,
    ) {
        if (result !is ChatMessageRepositoryResult.Success || result.messages.isEmpty()) return
        mutableState.update {
            if (
                it.selectedRoom?.id != roomId ||
                it.messages.isNotEmpty() ||
                it.isLoadingOlderMessages ||
                it.isSendingMessage
            ) {
                return@update it
            }
            val nextMessages = result.messages.ensureChronologicalMessages()
            it.copy(
                messages = nextMessages,
                unreadJumpMessageId = nextMessages.firstUnreadMessageId(it.selectedRoomUnreadCount),
            )
        }
    }

    private fun applyUserMessageResult(
        userId: String,
        result: ChatMessageRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                val nextMessages = result.messages.ensureChronologicalMessages()
                it.copy(
                    userConversations = it.userConversations
                        .markChatUserConversationRead(userId)
                        .sortedByPinnedIds(it.pinnedUserConversationIds) { conversation -> conversation.user.id },
                    selectedUserConversation = it.selectedUserConversation?.copy(unreadCount = 0),
                    messages = nextMessages,
                    unreadJumpMessageId = if (!loadingMore) {
                        nextMessages.firstUnreadMessageId(it.selectedUserUnreadCount)
                    } else {
                        it.unreadJumpMessageId
                    },
                    isLoadingMessages = false,
                    isLoadingOlderMessages = false,
                    messagesEndReached = result.endReached,
                    selectedUserUnreadCount = 0,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            is ChatMessageRepositoryResult.Created -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds - userId
                val nextMessages = it.messages.withChronologicalMessage(result.message)
                it.copy(
                    userConversations = it.userConversations
                        .withUserConversationLatest(userId, result.message)
                        .sortedByPinnedIds(it.pinnedUserConversationIds) { conversation -> conversation.user.id },
                    messages = nextMessages,
                    messageDraft = "",
                    replyingMessage = null,
                    quotedMessage = null,
                    attachedFile = null,
                    attachments = emptyList(),
                    isSendingMessage = false,
                    selectedUserUnreadCount = 0,
                    unreadJumpMessageId = null,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.ReactionUpdated -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(requiresRelogin = false)
            }
            is ChatMessageRepositoryResult.Deleted -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    messages = it.messages.filterNot { message -> message.id == result.messageId },
                    messageSearchResults = it.messageSearchResults.filterNot { message -> message.id == result.messageId },
                    pendingMessageDeleteIds = it.pendingMessageDeleteIds - result.messageId,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    isLoadingMessages = false,
                    isLoadingOlderMessages = false,
                    isSendingMessage = false,
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    isLoadingMessages = if (loadingMore) it.isLoadingMessages else false,
                    isLoadingOlderMessages = false,
                    isSendingMessage = false,
                    messageErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyCachedUserMessageResult(
        userId: String,
        result: ChatMessageRepositoryResult,
    ) {
        if (result !is ChatMessageRepositoryResult.Success || result.messages.isEmpty()) return
        mutableState.update {
            if (
                it.selectedUserConversation?.user?.id != userId ||
                it.messages.isNotEmpty() ||
                it.isLoadingOlderMessages ||
                it.isSendingMessage
            ) {
                return@update it
            }
            val nextMessages = result.messages.ensureChronologicalMessages()
            it.copy(
                messages = nextMessages,
                unreadJumpMessageId = nextMessages.firstUnreadMessageId(it.selectedUserUnreadCount),
            )
        }
    }

    private fun applyMessageSearchResult(
        query: String,
        roomId: String?,
        userId: String?,
        result: ChatMessageRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update { current ->
                val sameConversation = if (roomId != null) {
                    current.selectedRoom?.id == roomId
                } else {
                    current.selectedUserConversation?.user?.id == userId
                }
                if (!sameConversation || current.messageSearchQuery != query) return@update current
                current.copy(
                    messageSearchResults = result.messages.ensureChronologicalMessages(),
                    messageSearchServerUntilId = result.nextUntilId,
                    messageSearchEndReached = result.endReached,
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isSearchingMessages = if (loadingMore) it.isSearchingMessages else false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchServerUntilId = if (loadingMore) it.messageSearchServerUntilId else null,
                    messageSearchErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> mutableState.update {
                it.copy(
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                )
            }
        }
    }

    private fun applyBackgroundRoomMessageRefreshResult(
        roomId: String,
        result: ChatMessageRepositoryResult,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update { current ->
                val selectedRoom = current.selectedRoom ?: return@update current
                if (selectedRoom.id != roomId) return@update current
                val nextMessages = current.messages.mergeReplacingChronologicalMessages(result.messages)
                val latestMessage = nextMessages.lastOrNull()
                val unreadJumpMessageId = if (
                    current.unreadJumpMessageId == null &&
                    current.selectedRoomUnreadCount > 0
                ) {
                    nextMessages.firstUnreadMessageId(current.selectedRoomUnreadCount)
                } else {
                    current.unreadJumpMessageId
                }
                if (
                    nextMessages == current.messages &&
                    (latestMessage == null || latestMessage.createdAtLabel == selectedRoom.latestMessageAtLabel) &&
                    unreadJumpMessageId == current.unreadJumpMessageId &&
                    current.messageErrorMessage == null
                ) {
                    return@update current
                }
                current.copy(
                    rooms = latestMessage?.let { message ->
                        current.rooms
                            .withChatRoomLatestMessage(roomId, message)
                            .sortedByPinnedIds(current.pinnedRoomIds) { room -> room.id }
                    } ?: current.rooms,
                    selectedRoom = latestMessage?.let { message -> selectedRoom.withLatestMessageAt(message) }
                        ?: selectedRoom,
                    messages = nextMessages,
                    unreadJumpMessageId = unreadJumpMessageId,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error,
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
            -> Unit
        }
    }

    private fun applyBackgroundUserMessageRefreshResult(
        userId: String,
        result: ChatMessageRepositoryResult,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update { current ->
                if (current.selectedUserConversation?.user?.id != userId) return@update current
                val nextMessages = current.messages.mergeReplacingChronologicalMessages(result.messages)
                val unreadJumpMessageId = if (
                    current.unreadJumpMessageId == null &&
                    current.selectedUserUnreadCount > 0
                ) {
                    nextMessages.firstUnreadMessageId(current.selectedUserUnreadCount)
                } else {
                    current.unreadJumpMessageId
                }
                if (
                    nextMessages == current.messages &&
                    unreadJumpMessageId == current.unreadJumpMessageId &&
                    current.messageErrorMessage == null
                ) {
                    return@update current
                }
                current.copy(
                    messages = nextMessages,
                    userConversations = current.userConversations
                        .markChatUserConversationRead(userId)
                        .sortedByPinnedIds(current.pinnedUserConversationIds) { conversation -> conversation.user.id },
                    selectedUserConversation = current.selectedUserConversation?.copy(unreadCount = 0),
                    selectedUserUnreadCount = 0,
                    unreadJumpMessageId = unreadJumpMessageId,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error,
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
            -> Unit
        }
    }

    private fun applyMemberResult(
        roomId: String,
        result: ChatRoomMemberRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatRoomMemberRepositoryResult.Success -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                val activeMembers = if (loadingMore) {
                    emptyList()
                } else {
                    it.members.filter { member -> member.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX) }
                }
                it.copy(
                    members = result.members.mergeActiveChatMembers(activeMembers),
                    isLoadingMembers = false,
                    isLoadingMoreMembers = false,
                    membersEndReached = result.endReached,
                    memberErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatRoomMemberRepositoryResult.Unauthorized -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isLoadingMembers = false,
                    isLoadingMoreMembers = false,
                    memberErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatRoomMemberRepositoryResult.Error -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isLoadingMembers = if (loadingMore) it.isLoadingMembers else false,
                    isLoadingMoreMembers = false,
                    memberErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyRoomMutationResult(
        result: ChatRoomMutationRepositoryResult,
        selectSavedRoom: Boolean = false,
        successMessage: String,
    ) {
        when (result) {
            is ChatRoomMutationRepositoryResult.RoomSaved -> mutableState.update {
                val savedRoom = it.rooms.firstOrNull { room -> room.id == result.room.id }
                    ?.let { existing ->
                        result.room.copy(
                            membershipId = existing.membershipId,
                            unreadCount = existing.unreadCount,
                        )
                    }
                    ?: result.room
                it.copy(
                    rooms = (listOf(savedRoom) + it.rooms.filterNot { room -> room.id == savedRoom.id })
                        .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    selectedRoom = if (selectSavedRoom) savedRoom else it.selectedRoom,
                    isManagingRoom = false,
                    roomManagementMessage = successMessage,
                    errorMessage = null,
                    memberErrorMessage = null,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            is ChatRoomMutationRepositoryResult.RoomRemoved -> {
                stopMessageStreaming()
                mutableState.update {
                    it.copy(
                        rooms = it.rooms.filterNot { room -> room.id == result.roomId },
                        pinnedRoomIds = it.pinnedRoomIds - result.roomId,
                        selectedRoom = null,
                        messages = emptyList(),
                        members = emptyList(),
                        isManagingRoom = false,
                        showingMembers = false,
                        roomManagementMessage = successMessage,
                        selectedRoomUnreadCount = 0,
                        unreadJumpMessageId = null,
                        requiresRelogin = false,
                    )
                }
            }
            is ChatRoomMutationRepositoryResult.RoomMuted -> mutableState.update {
                it.copy(
                    rooms = it.rooms.map { room ->
                        if (room.id == result.roomId) room.copy(isMuted = result.muted) else room
                    }.sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    selectedRoom = it.selectedRoom?.let { room ->
                        if (room.id == result.roomId) room.copy(isMuted = result.muted) else room
                    },
                    isManagingRoom = false,
                    roomManagementMessage = successMessage,
                    requiresRelogin = false,
                )
            }
            is ChatRoomMutationRepositoryResult.ActionCompleted -> mutableState.update {
                it.copy(
                    isManagingRoom = false,
                    roomManagementMessage = result.message.ifBlank { successMessage },
                    memberErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatRoomMutationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isManagingRoom = false,
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatRoomMutationRepositoryResult.ValidationError -> mutableState.update {
                it.copy(
                    isManagingRoom = false,
                    roomManagementMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is ChatRoomMutationRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isManagingRoom = false,
                    roomManagementMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyReactionResult(
        messageId: String,
        reaction: String,
        react: Boolean,
        result: ChatMessageRepositoryResult,
    ) {
        when (result) {
            ChatMessageRepositoryResult.ReactionUpdated -> mutableState.update {
                it.copy(
                    messages = it.messages.withMessageReactionUpdated(messageId, reaction, react),
                    pendingMessageReactionIds = it.pendingMessageReactionIds - messageId,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    pendingMessageReactionIds = it.pendingMessageReactionIds - messageId,
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update {
                it.copy(
                    pendingMessageReactionIds = it.pendingMessageReactionIds - messageId,
                    messageErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            -> mutableState.update {
                it.copy(
                    pendingMessageReactionIds = it.pendingMessageReactionIds - messageId,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyRoomStreamingEvent(
        roomId: String,
        event: ChatStreamingEvent,
    ) {
        when (event) {
            ChatStreamingEvent.Connecting -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                if (it.isStreamingMessages && it.streamingErrorMessage == null) return@update it
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Connected -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                if (it.isStreamingMessages && it.streamingErrorMessage == null) return@update it
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Unauthorized -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatStreamingEvent.MessageReceived -> {
                if (event.message.roomId == roomId) {
                    scope.launch { repository.cacheRoomMessage(roomId, event.message) }
                }
                mutableState.update {
                    if (it.selectedRoom?.id != roomId || event.message.roomId != roomId) {
                        it
                    } else {
                        val nextMessages = it.messages.withChronologicalMessage(event.message)
                        if (nextMessages === it.messages && it.streamingErrorMessage == null) {
                            return@update it
                        }
                        val toast = event.message.toChatAttentionToastIfNeeded(
                            specialCareUserIds = it.specialCareUserIds,
                            currentUser = currentUserProvider(),
                        )
                        it.copy(
                            rooms = it.rooms
                                .bumpChatRoomForReceivedMessage(roomId, event.message)
                                .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                            selectedRoom = it.selectedRoom?.withLatestMessageAt(event.message),
                            messages = nextMessages,
                            members = it.members.withActiveChatMember(roomId, event.message.fromUser),
                            specialCareToast = it.specialCareToast.withStableSpecialCareToast(toast),
                            roomAttentionKinds = it.roomAttentionKinds.withAttentionToast(toast?.takeIf { incoming ->
                                incoming.chatUserId == null
                            }),
                            streamingErrorMessage = null,
                        )
                    }
                }
            }
            is ChatStreamingEvent.Error -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = userFacingStreamingErrorMessage(event.message),
                )
            }
            ChatStreamingEvent.Closed -> mutableState.update {
                if (it.selectedRoom?.id != roomId) return@update it
                if (!it.isStreamingMessages) return@update it
                it.copy(isStreamingMessages = false)
            }
        }
    }

    private fun applyUserStreamingEvent(
        userId: String,
        event: ChatStreamingEvent,
    ) {
        when (event) {
            ChatStreamingEvent.Connecting -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                if (it.isStreamingMessages && it.streamingErrorMessage == null) return@update it
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Connected -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                if (it.isStreamingMessages && it.streamingErrorMessage == null) return@update it
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Unauthorized -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatStreamingEvent.MessageReceived -> {
                if (event.message.belongsToUserConversation(userId)) {
                    scope.launch { repository.cacheUserMessage(userId, event.message) }
                    hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds - userId
                }
                mutableState.update {
                    if (it.selectedUserConversation?.user?.id != userId || !event.message.belongsToUserConversation(userId)) {
                        it
                    } else {
                        val nextMessages = it.messages.withChronologicalMessage(event.message)
                        if (nextMessages === it.messages && it.streamingErrorMessage == null) {
                            return@update it
                        }
                        val toast = event.message.toChatAttentionToastIfNeeded(
                            specialCareUserIds = it.specialCareUserIds,
                            currentUser = currentUserProvider(),
                            chatUserId = userId,
                        )
                        it.copy(
                            userConversations = it.userConversations
                                .withUserConversationLatest(userId, event.message)
                                .markChatUserConversationRead(userId)
                                .sortedByPinnedIds(it.pinnedUserConversationIds) { conversation -> conversation.user.id },
                            selectedUserConversation = it.selectedUserConversation?.copy(
                                latestMessage = event.message,
                                unreadCount = 0,
                            ),
                            messages = nextMessages,
                            specialCareToast = it.specialCareToast.withStableSpecialCareToast(toast),
                            userConversationAttentionKinds = it.userConversationAttentionKinds.withAttentionToast(toast),
                            streamingErrorMessage = null,
                        )
                    }
                }
            }
            is ChatStreamingEvent.Error -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = userFacingStreamingErrorMessage(event.message),
                )
            }
            ChatStreamingEvent.Closed -> mutableState.update {
                if (it.selectedUserConversation?.user?.id != userId) return@update it
                if (!it.isStreamingMessages) return@update it
                it.copy(isStreamingMessages = false)
            }
        }
    }
}

private fun List<ChatRoom>.markChatRoomRead(roomId: String): List<ChatRoom> {
    if (roomId.isBlank()) return this
    val index = indexOfFirst { room -> room.id == roomId }
    if (index < 0 || this[index].unreadCount == 0) return this
    val nextRooms = toMutableList()
    nextRooms[index] = this[index].copy(unreadCount = 0)
    return nextRooms
}

private fun List<ChatRoomMember>.withActiveChatMember(
    roomId: String,
    user: User,
): List<ChatRoomMember> {
    val cleanRoomId = roomId.trim()
    val cleanUserId = user.id.trim()
    if (cleanRoomId.isEmpty() || cleanUserId.isEmpty()) return this
    val activeUser = user.copy(onlineStatus = "active")
    val existingIndex = indexOfFirst { member -> member.user.id == cleanUserId }
    if (existingIndex >= 0) {
        val existing = this[existingIndex]
        val nextMember = existing.copy(user = existing.user.mergeActiveChatUser(activeUser))
        if (nextMember == existing) return this
        val nextMembers = toMutableList()
        nextMembers[existingIndex] = nextMember
        return nextMembers
    }
    return listOf(
        ChatRoomMember(
            membershipId = "$CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX$cleanRoomId:$cleanUserId",
            roomId = cleanRoomId,
            user = activeUser,
            joinedAtLabel = "活跃成员",
        ),
    ) + this
}

private fun List<ChatRoomMember>.dedupeActiveChatMembers(): List<ChatRoomMember> {
    if (size <= 1) return this
    val realUserIds = asSequence()
        .filterNot { member -> member.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX) }
        .map { member -> member.user.id }
        .filter { it.isNotBlank() }
        .toSet()
    if (realUserIds.isEmpty()) return distinctBy { member -> member.user.id.ifBlank { member.membershipId } }
    val seenKeys = HashSet<String>(size)
    val deduped = ArrayList<ChatRoomMember>(size)
    for (member in this) {
        val userId = member.user.id
        if (member.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX) && userId in realUserIds) continue
        val key = userId.ifBlank { member.membershipId }
        if (seenKeys.add(key)) deduped += member
    }
    return if (deduped.size == size) this else deduped
}

private fun List<ChatRoomMember>.mergeActiveChatMembers(activeMembers: List<ChatRoomMember>): List<ChatRoomMember> {
    if (activeMembers.isEmpty()) return dedupeActiveChatMembers()
    return (this + activeMembers).dedupeActiveChatMembers()
}

private fun List<ChatRoomMember>.hasOfficialChatRoomMembers(): Boolean {
    return any { member -> !member.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX) }
}

private fun User.mergeActiveChatUser(incoming: User): User {
    return copy(
        displayName = incoming.displayName.ifBlank { displayName },
        username = incoming.username.ifBlank { username },
        avatarInitial = incoming.avatarInitial.ifBlank { avatarInitial },
        avatarUrl = incoming.avatarUrl ?: avatarUrl,
        avatarDecorations = incoming.avatarDecorations.ifEmpty { avatarDecorations },
        host = incoming.host ?: host,
        onlineStatus = if (onlineStatus.equals("online", ignoreCase = true)) onlineStatus else "active",
    )
}

private fun List<ChatRoom>.mergeRoomUnreadCounts(
    previous: List<ChatRoom>,
    selectedRoomId: String?,
    localUnreadCounts: Map<String, Int>,
    persistUnread: ((Map<String, Int>) -> Unit)?,
): List<ChatRoom> {
    val previousById = previous.associateBy { it.id }
    var changedLocalCounts = false
    val nextLocalCounts = localUnreadCounts.toMutableMap()
    val merged = map { room ->
        if (room.id == selectedRoomId) {
            nextLocalCounts.remove(room.id)?.let { changedLocalCounts = true }
            return@map room.copy(unreadCount = 0)
        }
        val previousRoom = previousById[room.id]
        val localCount = nextLocalCounts[room.id].coercePositive()
        val remoteCount = room.unreadCount.coerceAtLeast(0)
        val previousCount = previousRoom?.unreadCount.coercePositive()
        val roomLatestMarker = room.unreadMarker()
        val previousLatestMarker = previousRoom?.unreadMarker().orEmpty()
        val latestChanged = previousRoom != null &&
            roomLatestMarker.isNotBlank() &&
            roomLatestMarker != previousLatestMarker
        val inferredCount = inferUnreadCount(
            remoteCount = remoteCount,
            localCount = localCount,
            previousCount = previousCount,
            latestChanged = latestChanged,
        )
        val nextCount = maxOf(remoteCount, localCount, inferredCount)
        if (nextCount > 0 && nextCount != localCount) {
            nextLocalCounts[room.id] = nextCount
            changedLocalCounts = true
        }
        room.copy(unreadCount = nextCount)
    }
    if (changedLocalCounts) {
        persistUnread?.invoke(nextLocalCounts.filterValues { it > 0 })
    }
    return merged
}

private fun List<ChatRoom>.bumpChatRoomForReceivedMessage(
    roomId: String,
    message: ChatMessage,
): List<ChatRoom> {
    if (roomId.isBlank()) return this
    val index = indexOfFirst { room -> room.id == roomId }
    if (index < 0) return this
    val room = this[index].withLatestMessageAt(message)
    return listOf(room) + filterIndexed { currentIndex, _ -> currentIndex != index }
}

private fun List<ChatRoom>.withChatRoomLatestMessage(
    roomId: String,
    message: ChatMessage,
): List<ChatRoom> {
    if (roomId.isBlank()) return this
    val index = indexOfFirst { room -> room.id == roomId }
    if (index < 0) return this
    val next = toMutableList()
    next[index] = this[index].withLatestMessageAt(message)
    return next
}

private fun ChatRoom.withLatestMessageAt(message: ChatMessage): ChatRoom {
    return copy(
        latestMessageAtLabel = message.createdAtLabel,
        latestMessageMarker = message.unreadMarker(),
    )
}

private fun List<ChatUserConversation>.markChatUserConversationRead(userId: String): List<ChatUserConversation> {
    if (userId.isBlank()) return this
    val index = indexOfFirst { conversation -> conversation.user.id == userId }
    if (index < 0 || this[index].unreadCount == 0) return this
    val next = toMutableList()
    next[index] = this[index].copy(unreadCount = 0)
    return next
}

private fun List<ChatUserConversation>.mergeUserUnreadCounts(
    previous: List<ChatUserConversation>,
    selectedUserId: String?,
    localUnreadCounts: Map<String, Int>,
    persistUnread: ((Map<String, Int>) -> Unit)?,
): List<ChatUserConversation> {
    val previousByUserId = previous.associateBy { it.user.id }
    var changedLocalCounts = false
    val nextLocalCounts = localUnreadCounts.toMutableMap()
    val merged = map { conversation ->
        val userId = conversation.user.id
        if (userId == selectedUserId) {
            nextLocalCounts.remove(userId)?.let { changedLocalCounts = true }
            return@map conversation.copy(unreadCount = 0)
        }
        val previousConversation = previousByUserId[userId]
        val localCount = nextLocalCounts[userId].coercePositive()
        val remoteCount = conversation.unreadCount.coerceAtLeast(0)
        val latestMessage = conversation.latestMessage
        val previousCount = previousConversation?.unreadCount.coercePositive()
        val latestChanged = previousConversation != null &&
            latestMessage != null &&
            latestMessage.id != previousConversation.latestMessage?.id &&
            !latestMessage.isRead
        val inferredCount = inferUnreadCount(
            remoteCount = remoteCount,
            localCount = localCount,
            previousCount = previousCount,
            latestChanged = latestChanged,
        )
        val nextCount = maxOf(remoteCount, localCount, inferredCount)
        if (nextCount > 0 && nextCount != localCount) {
            nextLocalCounts[userId] = nextCount
            changedLocalCounts = true
        }
        conversation.copy(unreadCount = nextCount)
    }
    if (changedLocalCounts) {
        persistUnread?.invoke(nextLocalCounts.filterValues { it > 0 })
    }
    return merged
}

private fun List<ChatUserConversation>.ensureChatUserConversation(
    conversation: ChatUserConversation,
): List<ChatUserConversation> {
    if (conversation.user.id.isBlank() || any { it.user.id == conversation.user.id }) return this
    return listOf(conversation) + this
}

private fun Int?.coercePositive(): Int = this?.coerceAtLeast(0) ?: 0

private fun inferUnreadCount(
    remoteCount: Int,
    localCount: Int,
    previousCount: Int,
    latestChanged: Boolean,
): Int {
    val baseCount = maxOf(localCount, previousCount)
    return if (latestChanged && remoteCount <= baseCount) baseCount + 1 else 0
}

private fun ChatRoom.unreadMarker(): String {
    return latestMessageMarker.ifBlank { latestMessageAtLabel }
}

private fun ChatRoom.hasNewUnreadAttentionComparedWith(previous: ChatRoom?): Boolean {
    if (unreadCount.coerceAtLeast(0) <= 0) return false
    if (previous == null) return false
    val currentMarker = unreadMarker()
    val previousMarker = previous.unreadMarker()
    return currentMarker.isNotBlank() && currentMarker != previousMarker
}

private fun ChatMessage.unreadMarker(): String {
    return id.ifBlank { createdAt.ifBlank { createdAtLabel } }
}

private fun List<ChatUserConversation>.withUserConversationLatest(
    userId: String,
    message: ChatMessage,
): List<ChatUserConversation> {
    if (userId.isBlank()) return this
    val index = indexOfFirst { conversation -> conversation.user.id == userId }
    if (index < 0) return this
    val updated = this[index].copy(latestMessage = message)
    return listOf(updated) + filterIndexed { currentIndex, _ -> currentIndex != index }
}

private fun List<ChatUserConversation>.mergeChatUserConversations(
    existing: List<ChatUserConversation>,
): List<ChatUserConversation> {
    if (isEmpty()) return existing
    val byUserId = LinkedHashMap<String, ChatUserConversation>(size + existing.size)
    for (conversation in this) {
        byUserId[conversation.user.id] = conversation
    }
    for (conversation in existing) {
        byUserId.putIfAbsent(conversation.user.id, conversation)
    }
    return byUserId.values.toList()
}

private fun Set<String>.toggleMembership(id: String): Set<String> {
    return if (id in this) this - id else this + id
}

private inline fun <T> List<T>.sortedByPinnedIds(
    pinnedIds: Set<String>,
    crossinline keySelector: (T) -> String,
): List<T> {
    if (pinnedIds.isEmpty() || size < 2) return this
    return withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<T>> { keySelector(it.value) in pinnedIds }
                .thenBy { it.index },
        )
        .map { it.value }
}

private fun Map<String, String?>.filterHiddenUserConversationLatestMessageIds(
    incomingConversations: List<ChatUserConversation>,
): Map<String, String?> {
    if (isEmpty() || incomingConversations.isEmpty()) return this
    val incomingLatestIds = incomingConversations.associate { conversation ->
        conversation.user.id to conversation.latestMessage?.id
    }
    return filter { (userId, hiddenLatestMessageId) ->
        val incomingLatestMessageId = incomingLatestIds[userId] ?: return@filter true
        incomingLatestMessageId == hiddenLatestMessageId
    }
}

private fun List<ChatUserConversation>.firstChatAttentionToastFrom(
    previous: List<ChatUserConversation>,
    specialCareUserIds: Set<String>,
    currentUser: User?,
): SpecialCareChatToast? {
    val previousLatestIds = previous.associate { it.user.id to it.latestMessage?.id }
    for (conversation in this) {
        val peerId = conversation.user.id
        val latestMessage = conversation.latestMessage ?: continue
        if (peerId !in previousLatestIds) continue
        val previousLatestId = previousLatestIds[peerId]
        if (latestMessage.id.isBlank() || previousLatestId == latestMessage.id) continue
        return latestMessage.toChatAttentionToastIfNeeded(
            specialCareUserIds = specialCareUserIds,
            currentUser = currentUser,
            chatUserId = peerId,
        ) ?: continue
    }
    return null
}

private fun List<ChatMessage>.firstChatAttentionToastAfter(
    previousMessageId: String?,
    specialCareUserIds: Set<String>,
    currentUser: User?,
): SpecialCareChatToast? {
    val startIndex = previousMessageId
        ?.takeIf { it.isNotBlank() }
        ?.let { previousId -> indexOfFirst { message -> message.id == previousId } }
        ?: -1
    val newMessages = if (startIndex >= 0) drop(startIndex + 1) else this
    for (message in newMessages) {
        return message.toChatAttentionToastIfNeeded(
            specialCareUserIds = specialCareUserIds,
            currentUser = currentUser,
        ) ?: continue
    }
    return null
}

private const val SPECIAL_CARE_ROOM_SCAN_LIMIT = 8

private fun ChatMessage.belongsToUserConversation(userId: String): Boolean {
    return fromUser.id == userId || toUserId == userId || toUser?.id == userId
}

private fun List<ChatMessage>.withMessageReactionUpdated(
    messageId: String,
    reaction: String,
    react: Boolean,
): List<ChatMessage> {
    val index = indexOfFirst { message -> message.id == messageId }
    if (index < 0) return this
    val updated = this[index].withReactionUpdated(reaction, react)
    if (updated == this[index]) return this
    val nextMessages = toMutableList()
    nextMessages[index] = updated
    return nextMessages
}

internal fun List<ChatMessage>.withChronologicalMessage(message: ChatMessage): List<ChatMessage> {
    for (index in indices) {
        if (this[index].id == message.id) {
            if (this[index] == message) return this
            val nextMessages = toMutableList()
            nextMessages[index] = message
            return nextMessages.ensureChronologicalMessages()
        }
    }
    if (isEmpty()) return listOf(message)
    val last = last()
    if (message.isSameOrAfter(last)) return this + message
    val first = first()
    if (!message.isSameOrAfter(first)) return listOf(message) + this

    val nextMessages = ArrayList<ChatMessage>(size + 1)
    var inserted = false
    for (existing in this) {
        if (!inserted && existing.isSameOrAfter(message)) {
            nextMessages += message
            inserted = true
        }
        nextMessages += existing
    }
    if (!inserted) nextMessages += message
    return nextMessages
}

private fun List<ChatMessage>.firstUnreadMessageId(unreadCount: Int): String? {
    val count = unreadCount.coerceAtLeast(0)
    if (count == 0 || isEmpty()) return null
    return takeLast(count.coerceAtMost(size)).firstOrNull()?.id
}

private fun ChatMessage.toChatAttentionToastIfNeeded(
    specialCareUserIds: Set<String>,
    currentUser: User?,
    chatUserId: String? = null,
): SpecialCareChatToast? {
    val currentUserId = currentUser?.id?.trim().orEmpty()
    val isFromSelf = currentUserId.isNotEmpty() && fromUser.id == currentUserId
    val kind = when {
        !isFromSelf && currentUserId.isNotEmpty() && text.mentionsChatUser(currentUser) -> ChatAttentionKind.Mention
        !isFromSelf && currentUserId.isNotEmpty() && reply?.fromUser?.id == currentUserId -> ChatAttentionKind.Reply
        !isFromSelf && currentUserId.isNotEmpty() && quote?.fromUser?.id == currentUserId -> ChatAttentionKind.Quote
        fromUser.id in specialCareUserIds -> ChatAttentionKind.SpecialCare
        else -> return null
    }
    return toChatAttentionToast(
        kind = kind,
        chatUserId = chatUserId,
    )
}

private fun ChatMessage.toChatAttentionToast(
    kind: ChatAttentionKind,
    chatUserId: String? = null,
): SpecialCareChatToast {
    return SpecialCareChatToast(
        messageId = id,
        roomId = roomId,
        chatUserId = chatUserId?.takeIf { it.isNotBlank() },
        userId = fromUser.id,
        displayName = fromUser.displayName.ifBlank { fromUser.username },
        previewText = chatMessageQuotePreviewText(this),
        createdAtLabel = createdAtLabel.ifBlank { "刚刚" },
        avatarUrl = fromUser.avatarUrl,
        kind = kind,
    )
}

private fun String.mentionsChatUser(user: User?): Boolean {
    val username = user?.username?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val host = user.host?.trim()?.takeIf { it.isNotEmpty() }
    var index = indexOf('@')
    while (index >= 0 && index < length - 1) {
        val parsed = parseMentionAt(index)
        if (parsed != null && parsed.username.equals(username, ignoreCase = true)) {
            if (host == null || parsed.host == null || parsed.host.equals(host, ignoreCase = true)) return true
        }
        index = indexOf('@', startIndex = index + 1)
    }
    return false
}

private data class ParsedMention(
    val username: String,
    val host: String?,
)

private fun String.parseMentionAt(atIndex: Int): ParsedMention? {
    if (atIndex !in indices || this[atIndex] != '@') return null
    val usernameStart = atIndex + 1
    if (usernameStart >= length || !this[usernameStart].isMentionPart()) return null
    var usernameEnd = usernameStart
    while (usernameEnd < length && this[usernameEnd].isMentionPart()) usernameEnd++
    val username = substring(usernameStart, usernameEnd).takeIf { it.isNotBlank() } ?: return null
    var host: String? = null
    if (usernameEnd < length && this[usernameEnd] == '@') {
        val hostStart = usernameEnd + 1
        var hostEnd = hostStart
        while (hostEnd < length && this[hostEnd].isMentionHostPart()) hostEnd++
        host = substring(hostStart, hostEnd).trim('.').takeIf { it.isNotBlank() }
    }
    return ParsedMention(username, host)
}

private fun Char.isMentionPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-'
}

private fun Char.isMentionHostPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '.'
}

internal fun SpecialCareChatToast?.withStableSpecialCareToast(
    incoming: SpecialCareChatToast?,
): SpecialCareChatToast? {
    if (incoming == null) return this
    if (this?.messageId == incoming.messageId) return this
    return incoming
}

private fun Map<String, ChatAttentionKind>.withAttentionToast(
    toast: SpecialCareChatToast?,
): Map<String, ChatAttentionKind> {
    if (toast == null) return this
    val key = toast.chatUserId?.takeIf { it.isNotBlank() } ?: toast.roomId.takeIf { it.isNotBlank() } ?: return this
    if (this[key] == toast.kind) return this
    return this + (key to toast.kind)
}

internal fun userFacingStreamingErrorMessage(message: String): String {
    val cleanMessage = message.trim()
    if (cleanMessage.isBlank()) return "实时连接已断开，请稍后重试"
    return when {
        cleanMessage.contains("timeout", ignoreCase = true) ||
            cleanMessage.contains("timed out", ignoreCase = true) -> "实时连接超时，请稍后重试"
        cleanMessage.contains("network", ignoreCase = true) ||
            cleanMessage.contains("connection", ignoreCase = true) ||
            cleanMessage.contains("connect", ignoreCase = true) -> "实时连接已断开，请稍后重试"
        else -> "实时连接暂时不可用，请稍后重试"
    }
}

fun DriveFile.toChatComposerAttachment(): ChatComposerAttachment {
    val kind = if (type.startsWith("image/", ignoreCase = true)) {
        ChatComposerAttachmentKind.Photo
    } else {
        ChatComposerAttachmentKind.File
    }
    return ChatComposerAttachment(
        id = id,
        kind = kind,
        file = this,
    )
}

fun ChatUiState.sendableChatAttachmentFileId(): String? {
    return sendableChatAttachmentFileIds().firstOrNull()
}

fun ChatUiState.sendableChatAttachmentFileIds(): List<String> {
    val ids = attachments.map { it.file.id } + listOfNotNull(attachedFile?.id)
    return ids.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
}

fun ChatUiState.hasSendableChatAttachment(): Boolean {
    return sendableChatAttachmentFileIds().isNotEmpty()
}

fun ChatUiState.primaryChatAttachmentFile(): DriveFile? {
    return attachments.firstOrNull()?.file ?: attachedFile
}

fun chatMessageSendText(
    draft: String,
    quote: ChatMessageQuote?,
): String {
    val cleanDraft = draft.trim()
    if (quote == null) return cleanDraft
    val quoteLine = "> ${quote.authorName}: ${quote.previewText}".trim()
    val quoteMarkerLine = "<!-- hhhl-chat-quote:${quote.messageId} -->"
    return if (cleanDraft.isBlank()) {
        "$quoteLine\n$quoteMarkerLine"
    } else {
        "$quoteLine\n$quoteMarkerLine\n\n$cleanDraft"
    }
}

fun ChatMessage.toChatMessageQuote(): ChatMessageQuote {
    return ChatMessageQuote(
        messageId = id,
        authorName = fromUser.displayName.ifBlank { fromUser.username },
        previewText = chatMessageQuotePreviewText(this),
    )
}

fun chatMessageQuotePreviewText(message: ChatMessage): String {
    val body = chatMessageBodyText(message)
        .lineSequence()
        .map { it.trim().trimStart('>') }
        .firstOrNull { it.isNotBlank() }
        ?.truncateRichTextPreviewText(CHAT_MESSAGE_QUOTE_PREVIEW_MAX_CHARS)
    return body
        ?: "附件"
}

private const val CHAT_MESSAGE_QUOTE_PREVIEW_MAX_CHARS = 80

private fun ChatMessage.withReactionUpdated(
    reaction: String,
    react: Boolean,
): ChatMessage {
    val current = reactions.associateBy { it.reaction }.toMutableMap()
    val currentCount = current[reaction]?.count ?: 0
    val nextCount = if (react) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)
    if (nextCount == 0) {
        current.remove(reaction)
    } else {
        current[reaction] = ChatMessageReaction(reaction, nextCount)
    }
    val nextReactions = current.values
        .filter { it.count > 0 }
        .sortedByDescending { it.count }
    return copy(
        reactions = nextReactions,
        reactionCount = nextReactions.sumOf { it.count },
    )
}

internal fun List<ChatMessage>.dedupeChronologicalMessages(): List<ChatMessage> {
    val byId = LinkedHashMap<String, ChatMessage>(size)
    for (message in this) {
        if (!byId.containsKey(message.id)) {
            byId[message.id] = message
        }
    }
    return byId.values.sortedByChatMessageOrder()
}

internal fun List<ChatMessage>.mergeReplacingChronologicalMessages(incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return ensureChronologicalMessages()
    val byId = LinkedHashMap<String, ChatMessage>(size + incoming.size)
    for (message in this) {
        byId[message.id] = message
    }
    for (message in incoming) {
        byId[message.id] = message
    }
    return byId.values.sortedByChatMessageOrder()
}

internal fun List<ChatMessage>.ensureChronologicalMessages(): List<ChatMessage> {
    if (size <= 1) return this
    val seen = HashSet<String>(size)
    var previousKey: String? = null
    var previousId: String? = null
    for (message in this) {
        if (!seen.add(message.id)) return dedupeChronologicalMessages()
        val currentKey = message.chatMessageSortKey()
        val lastKey = previousKey
        val lastId = previousId
        if (
            lastKey != null &&
            lastId != null &&
            (currentKey < lastKey || (currentKey == lastKey && message.id < lastId))
        ) {
            return dedupeChronologicalMessages()
        }
        previousKey = currentKey
        previousId = message.id
    }
    return this
}

private data class ChatMessageSortEntry(
    val message: ChatMessage,
    val sortKey: String,
)

private fun Iterable<ChatMessage>.sortedByChatMessageOrder(): List<ChatMessage> {
    return map { message -> ChatMessageSortEntry(message, message.chatMessageSortKey()) }
        .sortedWith(
            compareBy<ChatMessageSortEntry> { it.sortKey }
                .thenBy { it.message.id },
        )
        .map { it.message }
}

private fun ChatMessage.chatMessageSortKey(): String {
    return apiDateSortKey(createdAt, createdAtLabel)
}

private const val CHAT_MESSAGE_SEARCH_TIMEOUT_MS = 20_000L

private fun Throwable.toChatSearchErrorMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "搜索消息失败，请稍后重试"
}

private fun ChatMessage.isSameOrAfter(other: ChatMessage): Boolean {
    val currentKey = chatMessageSortKey()
    val otherKey = other.chatMessageSortKey()
    return currentKey > otherKey || (currentKey == otherKey && id >= other.id)
}

private fun placeholderRoom(roomId: String): ChatRoom {
    return ChatRoom(
        id = roomId,
        membershipId = roomId,
        name = "聊天室",
        description = "",
        joinMode = "open",
        memberCount = 0,
        isMuted = false,
        owner = User(
            id = "system",
            displayName = "聊天室",
            username = "system",
            avatarInitial = "聊",
        ),
    )
}

private fun ChatRoom.withStableMembershipId(): ChatRoom {
    return if (membershipId.isNotBlank()) this else copy(membershipId = id)
}

private fun String.isAlreadyJoinedRoomMessage(): Boolean {
    val normalized = lowercase()
    return "already" in normalized ||
        "joined" in normalized ||
        "加入" in this ||
        "已在" in this ||
        "成员" in this
}
