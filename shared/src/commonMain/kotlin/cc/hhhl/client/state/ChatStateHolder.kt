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
import cc.hhhl.client.model.isServerChatMessageId
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.presentation.truncateRichTextPreviewText
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult
import cc.hhhl.client.repository.ChatRoomMemberRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.NotificationRepositoryResult
import cc.hhhl.client.repository.requiresRealtimeAttentionResolution
import cc.hhhl.client.repository.resolveRealtimeMessage
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.coroutines.withContext
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
    val chatUserSearchQuery: String = "",
    val chatUserSearchResults: List<User> = emptyList(),
    val members: List<ChatRoomMember> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingRoomExtras: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isSearchingMessages: Boolean = false,
    val isLoadingMoreMessageSearch: Boolean = false,
    val isSearchingChatUsers: Boolean = false,
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
    val chatUserSearchErrorMessage: String? = null,
    val memberErrorMessage: String? = null,
    val roomManagementMessage: String? = null,
    val requiresRelogin: Boolean = false,
    val pinnedRoomIds: Set<String> = emptySet(),
    val pinnedUserConversationIds: Set<String> = emptySet(),
    val roomGroups: Map<String, String> = emptyMap(),
    // @功能相关状态
    val mentionPickerVisible: Boolean = false,
    val mentionSearchQuery: String = "",
    val recentChatMembers: List<ChatRecentMember> = emptyList(),
    val mentionTriggerPosition: Int = -1,
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

/** @成员选择列表的最大显示数量 */
private const val MAX_RECENT_MENTION_MEMBERS = 20

/** WebSocket 重试相关常量 */
private const val STREAMING_MAX_RETRIES = 3
private const val STREAMING_INITIAL_RETRY_DELAY_MS = 1000L
private const val STREAMING_MAX_RETRY_DELAY_MS = 30000L

/**
 * 最近聊天的群成员，用于@功能
 * @param user 用户信息
 * @param lastInteractionTime 最近互动时间（时间戳，毫秒）
 * @param interactionCount 互动次数
 */
data class ChatRecentMember(
    val user: User,
    val lastInteractionTime: Long,
    val interactionCount: Int,
)

private data class ChatMessageDeletionTarget(
    val roomId: String? = null,
    val directUserId: String? = null,
)

class ChatStateHolder(
    private val repository: ChatRepository,
    private val driveFileRepository: DriveFileRepository? = null,
    private val streamingRepository: ChatStreamingRepository? = null,
    private val relationshipRepository: UserRelationshipRepository? = null,
    private val discoverRepository: DiscoverRepository? = null,
    private val notificationRepository: NotificationRepository? = null,
    private val scope: CoroutineScope,
    private val accountIdProvider: () -> String? = { null },
    private val unreadStore: ChatUnreadStore = NoopChatUnreadStore,
    private val currentUserProvider: () -> User? = { null },
    private val workerDispatcher: CoroutineDispatcher? = null,
) {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState
    private var pendingMediaUploads: List<DriveFileUpload> = emptyList()
    private var mediaUploadRequestId = 0L
    private var streamingJob: Job? = null
    private var isRefreshingRoomsQuietly: Boolean = false
    private var isRefreshingUserConversationsQuietly: Boolean = false
    private var isRefreshingMessagesQuietly: Boolean = false
    private var isRefreshingSpecialCareMessagesQuietly: Boolean = false
    private var hasUserConversationSnapshot: Boolean = false
    private var hiddenUserConversationLatestMessageIds: Map<String, String?> = emptyMap()
    private var specialCareRoomLatestMessageIds: Map<String, String> = emptyMap()
    private var roomMessageRequestGenerations: Map<String, Int> = emptyMap()
    private var userMessageRequestGenerations: Map<String, Int> = emptyMap()
    private val workerMutex = Mutex()

    private fun currentAccountId(): String? = accountIdProvider()?.trim()?.takeIf { it.isNotEmpty() }

    private fun roomMessageRequestGeneration(roomId: String): Int {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return 0
        return roomMessageRequestGenerations[cleanRoomId] ?: 0
    }

    private fun bumpRoomMessageRequestGeneration(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        val nextGeneration = (roomMessageRequestGenerations[cleanRoomId] ?: 0) + 1
        roomMessageRequestGenerations = roomMessageRequestGenerations + (cleanRoomId to nextGeneration)
    }

    private fun matchesRoomMessageRequestGeneration(roomId: String, requestGeneration: Int?): Boolean {
        return requestGeneration == null || roomMessageRequestGeneration(roomId) == requestGeneration
    }

    private fun userMessageRequestGeneration(userId: String): Int {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return 0
        return userMessageRequestGenerations[cleanUserId] ?: 0
    }

    private fun bumpUserMessageRequestGeneration(userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return
        val nextGeneration = (userMessageRequestGenerations[cleanUserId] ?: 0) + 1
        userMessageRequestGenerations = userMessageRequestGenerations + (cleanUserId to nextGeneration)
    }

    private fun matchesUserMessageRequestGeneration(userId: String, requestGeneration: Int?): Boolean {
        return requestGeneration == null || userMessageRequestGeneration(userId) == requestGeneration
    }

    private fun ChatUiState.isCurrentMessageActionContext(
        roomId: String?,
        userId: String?,
    ): Boolean {
        return when {
            roomId != null -> selectedRoom?.id == roomId
            userId != null -> selectedUserConversation?.user?.id == userId
            else -> false
        }
    }

    private fun localUnreadSnapshot(): ChatUnreadSnapshot {
        return currentAccountId()?.let(unreadStore::load) ?: ChatUnreadSnapshot()
    }

    private fun saveLocalUnreadSnapshot(snapshot: ChatUnreadSnapshot) {
        currentAccountId()?.let { accountId -> unreadStore.save(accountId, snapshot) }
    }

    private suspend fun <T> runChatWorker(block: suspend () -> T): T {
        val dispatcher = workerDispatcher ?: return block()
        return workerMutex.withLock {
            withContext(dispatcher) { block() }
        }
    }

    private fun launchChatWorker(block: suspend () -> Unit): Job {
        return scope.launch { runChatWorker(block) }
    }

    private fun clearLocalRoomUnread(roomId: String) {
        currentAccountId()?.let { accountId -> unreadStore.clearRoom(accountId, roomId) }
    }

    private fun clearLocalUserUnread(userId: String) {
        currentAccountId()?.let { accountId -> unreadStore.clearUser(accountId, userId) }
    }

    private fun markLocalRoomRead(roomId: String, marker: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        currentAccountId()?.let { accountId -> unreadStore.markRoomRead(accountId, cleanRoomId, marker) }
    }

    private fun markLocalRoomRead(room: ChatRoom) {
        markLocalRoomRead(room.id, room.unreadMarker())
    }

    private fun markLocalUserRead(userId: String, marker: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty()) return
        currentAccountId()?.let { accountId -> unreadStore.markUserRead(accountId, cleanUserId, marker) }
    }

    private fun markLocalUserRead(conversation: ChatUserConversation) {
        markLocalUserRead(conversation.user.id, conversation.latestMessage?.unreadMarker().orEmpty())
    }

    private fun saveObservedLocalRoomUnread(roomId: String, unreadCount: Int) {
        val cleanRoomId = roomId.trim()
        val cleanUnreadCount = unreadCount.coerceAtLeast(0)
        if (cleanRoomId.isEmpty() || cleanUnreadCount <= 0) return
        val current = localUnreadSnapshot()
        if ((current.roomCounts[cleanRoomId] ?: 0) >= cleanUnreadCount) return
        saveLocalUnreadSnapshot(
            current.copy(roomCounts = current.roomCounts + (cleanRoomId to cleanUnreadCount)),
        )
    }

    private fun saveObservedLocalUserUnread(userId: String, unreadCount: Int) {
        val cleanUserId = userId.trim()
        val cleanUnreadCount = unreadCount.coerceAtLeast(0)
        if (cleanUserId.isEmpty() || cleanUnreadCount <= 0) return
        val current = localUnreadSnapshot()
        if ((current.userCounts[cleanUserId] ?: 0) >= cleanUnreadCount) return
        saveLocalUnreadSnapshot(
            current.copy(userCounts = current.userCounts + (cleanUserId to cleanUnreadCount)),
        )
    }

    private fun saveLocalChatPreferences(
        pinnedRoomIds: Set<String> = state.value.pinnedRoomIds,
        pinnedUserIds: Set<String> = state.value.pinnedUserConversationIds,
        roomGroups: Map<String, String> = state.value.roomGroups,
    ) {
        val current = localUnreadSnapshot()
        saveLocalUnreadSnapshot(
            current.copy(
                pinnedRoomIds = pinnedRoomIds,
                pinnedUserIds = pinnedUserIds,
                roomGroups = roomGroups,
            ),
        )
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

    fun dismissErrorMessage() {
        if (state.value.errorMessage == null) return
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun dismissMessageErrorMessage() {
        if (state.value.messageErrorMessage == null) return
        mutableState.update { it.copy(messageErrorMessage = null) }
    }

    fun dismissMessageSearchErrorMessage() {
        if (state.value.messageSearchErrorMessage == null) return
        mutableState.update { it.copy(messageSearchErrorMessage = null) }
    }

    fun dismissChatUserSearchErrorMessage() {
        if (state.value.chatUserSearchErrorMessage == null) return
        mutableState.update { it.copy(chatUserSearchErrorMessage = null) }
    }

    fun dismissMemberErrorMessage() {
        if (state.value.memberErrorMessage == null) return
        mutableState.update { it.copy(memberErrorMessage = null) }
    }

    fun dismissRoomManagementMessage() {
        if (state.value.roomManagementMessage == null) return
        mutableState.update { it.copy(roomManagementMessage = null) }
    }

    private fun rejectUnavailableChatManagement() {
        mutableState.update {
            it.copy(
                isManagingRoom = false,
                roomManagementMessage = "实例未启用聊天",
                errorMessage = "实例未启用聊天",
                messageErrorMessage = "实例未启用聊天",
                requiresRelogin = false,
            )
        }
    }

    fun dismissStreamingErrorMessage() {
        if (state.value.streamingErrorMessage == null) return
        mutableState.update { it.copy(streamingErrorMessage = null) }
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
        val localPreferences = if (chatAvailable) localUnreadSnapshot() else ChatUnreadSnapshot()
        if (!chatAvailable) {
            stopMessageStreaming()
            isRefreshingUserConversationsQuietly = false
            hasUserConversationSnapshot = false
            pendingMediaUploads = emptyList()
            mediaUploadRequestId += 1
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
                    pinnedRoomIds = localPreferences.pinnedRoomIds,
                    pinnedUserConversationIds = localPreferences.pinnedUserIds,
                    roomGroups = localPreferences.roomGroups,
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

        launchChatWorker {
            val roomResult = repository.refresh()
            applyResult(roomResult, loadingMore = false)
            if (roomResult is ChatRepositoryResult.Success) {
                refreshRoomExtras()
                applyUserConversationResult(
                    result = resolveUserConversationAttentionReferences(repository.refreshUserConversations()),
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
        launchChatWorker {
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
        launchChatWorker {
            try {
                applyUserConversationResult(
                    result = resolveUserConversationAttentionReferences(repository.refreshUserConversations()),
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

        launchChatWorker {
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

        pendingMediaUploads = emptyList()
        mediaUploadRequestId += 1
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
                chatUserSearchQuery = "",
                chatUserSearchResults = emptyList(),
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
                isSearchingChatUsers = false,
                isLoadingMembers = false,
                isLoadingMoreMembers = false,
                isSendingMessage = false,
                messageDraft = "",
                mentionPickerVisible = false,
                mentionSearchQuery = "",
                mentionTriggerPosition = -1,
                replyingMessage = null,
                quotedMessage = null,
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
                messageErrorMessage = null,
                messageSearchErrorMessage = null,
                chatUserSearchErrorMessage = null,
                memberErrorMessage = null,
                streamingErrorMessage = null,
                requiresRelogin = false,
            )
        }
        markLocalRoomRead(room)
        startMessageStreaming(room.id)
        restoreCachedRoomMessages(room.id)
        refreshMessages()
    }

    fun toggleRoomPinned(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        mutableState.update {
            val nextPinnedRoomIds = it.pinnedRoomIds.toggleMembership(cleanRoomId)
            saveLocalChatPreferences(pinnedRoomIds = nextPinnedRoomIds)
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
            saveLocalChatPreferences(pinnedUserIds = nextPinnedUserConversationIds)
            it.copy(
                pinnedUserConversationIds = nextPinnedUserConversationIds,
                userConversations = it.userConversations.sortedByPinnedIds(nextPinnedUserConversationIds) { conversation ->
                    conversation.user.id
                },
                requiresRelogin = false,
            )
        }
    }

    fun setRoomGroup(roomId: String, groupName: String) {
        val cleanRoomId = roomId.trim()
        val cleanGroupName = groupName.cleanChatRoomGroupName()
        if (cleanRoomId.isEmpty()) return
        mutableState.update {
            val nextGroups = if (cleanGroupName.isBlank()) {
                it.roomGroups - cleanRoomId
            } else {
                it.roomGroups + (cleanRoomId to cleanGroupName)
            }
            saveLocalChatPreferences(roomGroups = nextGroups)
            it.copy(
                roomGroups = nextGroups,
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
        clearLocalUserUnread(cleanUserId)
        mutableState.update {
            val nextPinnedUserConversationIds = it.pinnedUserConversationIds - cleanUserId
            if (nextPinnedUserConversationIds != it.pinnedUserConversationIds) {
                saveLocalChatPreferences(pinnedUserIds = nextPinnedUserConversationIds)
            }
            it.copy(
                userConversations = it.userConversations.filterNot { conversation -> conversation.user.id == cleanUserId },
                pinnedUserConversationIds = nextPinnedUserConversationIds,
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
        launchChatWorker {
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
        bumpUserMessageRequestGeneration(conversation.user.id)
        pendingMediaUploads = emptyList()
        mediaUploadRequestId += 1
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
                chatUserSearchQuery = "",
                chatUserSearchResults = emptyList(),
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
                isSearchingChatUsers = false,
                isLoadingMembers = false,
                isLoadingMoreMembers = false,
                isSendingMessage = false,
                messageDraft = "",
                mentionPickerVisible = false,
                mentionSearchQuery = "",
                mentionTriggerPosition = -1,
                replyingMessage = null,
                quotedMessage = null,
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
                messageErrorMessage = null,
                messageSearchErrorMessage = null,
                chatUserSearchErrorMessage = null,
                memberErrorMessage = null,
                streamingErrorMessage = null,
                requiresRelogin = false,
            )
        }
        markLocalUserRead(conversation)
        startUserMessageStreaming(conversation.user.id)
        restoreCachedUserMessages(conversation.user.id)
        refreshMessages()
    }

    fun openUserConversation(
        user: User,
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

        launchChatWorker {
            val shownRoom = when (val showResult = repository.showRoom(cleanRoomId)) {
                is ChatRoomMutationRepositoryResult.RoomSaved -> showResult.room
                is ChatRoomMutationRepositoryResult.RoomMessagesCleared,
                is ChatRoomMutationRepositoryResult.RoomRemoved,
                is ChatRoomMutationRepositoryResult.RoomMuted,
                is ChatRoomMutationRepositoryResult.ActionCompleted -> placeholderRoom(cleanRoomId)
                ChatRoomMutationRepositoryResult.Unauthorized -> {
                    completeOpenRoomByIdWithError("登录已失效，请重新登录", requiresRelogin = true)
                    return@launchChatWorker
                }
                is ChatRoomMutationRepositoryResult.ValidationError -> {
                    completeOpenRoomByIdWithError(showResult.message)
                    return@launchChatWorker
                }
                is ChatRoomMutationRepositoryResult.Error -> {
                    completeOpenRoomByIdWithError(showResult.message)
                    return@launchChatWorker
                }
            }

            val canOpen = when (val joinResult = repository.joinRoom(cleanRoomId)) {
                is ChatRoomMutationRepositoryResult.ActionCompleted,
                is ChatRoomMutationRepositoryResult.RoomSaved -> true
                is ChatRoomMutationRepositoryResult.RoomMessagesCleared,
                is ChatRoomMutationRepositoryResult.RoomRemoved,
                is ChatRoomMutationRepositoryResult.RoomMuted -> true
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
            }
            if (!canOpen) return@launchChatWorker

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
        val requestGeneration = roomMessageRequestGeneration(roomId)
        launchChatWorker {
            applyCachedRoomMessageResult(
                roomId = roomId,
                result = repository.restoreCachedMessages(roomId),
                requestGeneration = requestGeneration,
            )
        }
    }

    private fun restoreCachedUserMessages(userId: String) {
        val requestGeneration = userMessageRequestGeneration(userId)
        launchChatWorker {
            applyCachedUserMessageResult(
                userId = userId,
                result = repository.restoreCachedUserMessages(userId),
                requestGeneration = requestGeneration,
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
                mentionPickerVisible = false,
                mentionSearchQuery = "",
                mentionTriggerPosition = -1,
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
            collectWithRetry(
                flowProvider = { streamRepository.streamRoomMessages(cleanRoomId) },
                onEvent = { event ->
                    applyRoomStreamingEvent(cleanRoomId, event)
                    event.shouldTriggerStreamingRetry()
                },
            )
        }
    }

    fun startUserMessageStreaming(userId: String = state.value.selectedUserConversation?.user?.id.orEmpty()) {
        val streamRepository = streamingRepository ?: return
        val cleanUserId = userId.trim()
        if (!state.value.chatAvailable || cleanUserId.isEmpty()) return

        streamingJob?.cancel()
        streamingJob = scope.launch {
            collectWithRetry(
                flowProvider = { streamRepository.streamUserMessages(cleanUserId) },
                onEvent = { event ->
                    applyUserStreamingEvent(cleanUserId, event)
                    event.shouldTriggerStreamingRetry()
                },
            )
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
        launchChatWorker {
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
        launchChatWorker {
            try {
                if (roomId != null) {
                    val requestGeneration = roomMessageRequestGeneration(roomId)
                    applyBackgroundRoomMessageRefreshResult(
                        roomId = roomId,
                        result = repository.refreshMessages(roomId),
                        requestGeneration = requestGeneration,
                    )
                } else if (userId != null) {
                    val requestGeneration = userMessageRequestGeneration(userId)
                    applyBackgroundUserMessageRefreshResult(
                        userId = userId,
                        result = repository.refreshUserMessages(userId),
                        requestGeneration = requestGeneration,
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
        launchChatWorker {
            try {
                when (val result = loadRoomsForSpecialCareScan()) {
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
                                (
                                    currentRoomsById.isNotEmpty() &&
                                        (
                                            currentRoomsById[room.id] == null ||
                                        room.hasNewUnreadAttentionComparedWith(currentRoomsById[room.id])
                                        )
                                )
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

    fun recordRealtimeMessage(
        message: ChatMessage,
        directUserId: String? = null,
    ) {
        val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanRoomId = message.roomId.trim().takeIf { it.isNotEmpty() }
        if (cleanDirectUserId == null && cleanRoomId == null) return
        launchChatWorker {
            if (cleanDirectUserId != null) {
                repository.cacheUserMessage(cleanDirectUserId, message)
            } else if (cleanRoomId != null) {
                repository.cacheRoomMessage(cleanRoomId, message)
            }
        }
        mutableState.update { current ->
            val currentUser = currentUserProvider()
            val isFromCurrentUser = currentUser?.id?.trim()?.takeIf { it.isNotEmpty() } == message.fromUser.id
            val toast = message.toChatAttentionToastIfNeeded(
                specialCareUserIds = current.specialCareUserIds,
                currentUser = currentUser,
                chatUserId = cleanDirectUserId,
            )
            if (cleanDirectUserId != null) {
                val isSelected = current.selectedUserConversation?.user?.id == cleanDirectUserId
                val shouldIncrementUnread = !isSelected && !isFromCurrentUser
                val nextMessages = if (isSelected) {
                    current.messages.withChronologicalMessage(message)
                } else {
                    current.messages
                }
                val nextConversation = current.userConversations
                    .withRealtimeUserConversationMessage(
                        userId = cleanDirectUserId,
                        message = message,
                        incrementUnread = shouldIncrementUnread,
                    )
                if (shouldIncrementUnread) {
                    nextConversation
                        .firstOrNull { conversation -> conversation.user.id == cleanDirectUserId }
                        ?.unreadCount
                        ?.let { unreadCount -> saveObservedLocalUserUnread(cleanDirectUserId, unreadCount) }
                }
                current.copy(
                    userConversations = nextConversation
                        .sortedByPinnedIds(current.pinnedUserConversationIds) { conversation -> conversation.user.id },
                    selectedUserConversation = current.selectedUserConversation?.let { selected ->
                        if (selected.user.id == cleanDirectUserId) {
                            selected.copy(latestMessage = message, unreadCount = 0)
                        } else {
                            selected
                        }
                    },
                    messages = nextMessages,
                    specialCareToast = current.specialCareToast.withStableSpecialCareToast(toast),
                    userConversationAttentionKinds = current.userConversationAttentionKinds.withAttentionToast(toast),
                )
            } else {
                val roomId = cleanRoomId ?: return@update current
                val isSelected = current.selectedRoom?.id == roomId
                val shouldIncrementUnread = !isSelected && !isFromCurrentUser
                val nextMessages = if (isSelected) {
                    current.messages.withChronologicalMessage(message)
                } else {
                    current.messages
                }
                val nextRooms = current.rooms
                    .withRealtimeRoomMessage(
                        roomId = roomId,
                        message = message,
                        incrementUnread = shouldIncrementUnread,
                    )
                if (shouldIncrementUnread) {
                    nextRooms
                        .firstOrNull { room -> room.id == roomId }
                        ?.unreadCount
                        ?.let { unreadCount -> saveObservedLocalRoomUnread(roomId, unreadCount) }
                }
                current.copy(
                    rooms = nextRooms
                        .sortedByPinnedIds(current.pinnedRoomIds) { room -> room.id },
                    selectedRoom = current.selectedRoom?.let { selected ->
                        if (selected.id == roomId) selected.withLatestMessageAt(message) else selected
                    },
                    messages = nextMessages,
                    specialCareToast = current.specialCareToast.withStableSpecialCareToast(toast),
                    roomAttentionKinds = current.roomAttentionKinds.withAttentionToast(toast?.takeIf { incoming ->
                        incoming.chatUserId == null
                    }),
                )
            }
        }
        if (cleanDirectUserId != null) {
            if (
                state.value.selectedUserConversation?.user?.id == cleanDirectUserId &&
                message.belongsToUserConversation(cleanDirectUserId)
            ) {
                markLocalUserRead(cleanDirectUserId, message.unreadMarker())
            }
        } else if (cleanRoomId != null && state.value.selectedRoom?.id == cleanRoomId) {
            markLocalRoomRead(cleanRoomId, message.unreadMarker())
        }
    }

    fun recordRealtimeMessageDeletion(
        messageId: String,
        roomId: String? = null,
        directUserId: String? = null,
    ) {
        val cleanMessageId = messageId.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanRoomId = roomId?.trim()?.takeIf { it.isNotEmpty() }
        val cleanDirectUserId = directUserId?.trim()?.takeIf { it.isNotEmpty() }
        val target = state.value.inferRealtimeDeletionTarget(
            messageId = cleanMessageId,
            roomId = cleanRoomId,
            directUserId = cleanDirectUserId,
        )
        launchChatWorker {
            target.roomId?.let { repository.removeCachedRoomMessage(it, cleanMessageId) }
            target.directUserId?.let { repository.removeCachedUserMessage(it, cleanMessageId) }
        }
        mutableState.update { current ->
            current.withRealtimeMessageDeleted(
                messageId = cleanMessageId,
                target = target,
            )
        }
    }

    private suspend fun loadRoomsForSpecialCareScan(): ChatRepositoryResult {
        val initial = when (val result = repository.refresh()) {
            is ChatRepositoryResult.Success -> result
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> return result
        }
        var rooms = initial.rooms
        var endReached = initial.endReached
        while (!endReached && rooms.size < SPECIAL_CARE_ROOM_SCAN_LIMIT) {
            val next = when (val result = repository.loadMore(rooms)) {
                is ChatRepositoryResult.Success -> result
                ChatRepositoryResult.Unauthorized,
                is ChatRepositoryResult.Error,
                    -> return result
            }
            if (next.rooms.size <= rooms.size) {
                endReached = true
                continue
            }
            rooms = next.rooms
            endReached = next.endReached
        }
        val ownedRooms = when (val result = repository.refreshOwnedRooms()) {
            is ChatRepositoryResult.Success -> result.rooms
            ChatRepositoryResult.Unauthorized,
            is ChatRepositoryResult.Error,
                -> emptyList()
        }
        rooms = rooms.mergeOwnedChatRooms(ownedRooms)
        return ChatRepositoryResult.Success(rooms = rooms, endReached = endReached)
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

        launchChatWorker {
            if (room != null) {
                val requestGeneration = roomMessageRequestGeneration(room.id)
                applyRoomMessageResult(
                    roomId = room.id,
                    result = repository.refreshMessages(room.id),
                    loadingMore = false,
                    requestGeneration = requestGeneration,
                )
            } else if (userConversation != null) {
                val requestGeneration = userMessageRequestGeneration(userConversation.user.id)
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.refreshUserMessages(userConversation.user.id),
                    loadingMore = false,
                    requestGeneration = requestGeneration,
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

        launchChatWorker {
            if (room != null) {
                val requestGeneration = roomMessageRequestGeneration(room.id)
                applyRoomMessageResult(
                    roomId = room.id,
                    result = repository.loadMoreMessages(room.id, current.messages),
                    loadingMore = true,
                    requestGeneration = requestGeneration,
                )
            } else if (userConversation != null) {
                val requestGeneration = userMessageRequestGeneration(userConversation.user.id)
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.loadMoreUserMessages(userConversation.user.id, current.messages),
                    loadingMore = true,
                    requestGeneration = requestGeneration,
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

        launchChatWorker {
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

    fun searchChatUsers(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            mutableState.update {
                it.copy(
                    chatUserSearchQuery = "",
                    chatUserSearchResults = emptyList(),
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        val repository = discoverRepository
        if (repository == null) {
            mutableState.update {
                it.copy(
                    chatUserSearchQuery = cleanQuery,
                    chatUserSearchResults = emptyList(),
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = "当前界面暂不可搜索服务器用户",
                    requiresRelogin = false,
                )
            }
            return
        }
        val current = state.value
        if (current.isSearchingChatUsers && current.chatUserSearchQuery == cleanQuery) return
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id

        mutableState.update {
            it.copy(
                chatUserSearchQuery = cleanQuery,
                isSearchingChatUsers = true,
                chatUserSearchErrorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
            val result = try {
                withTimeoutOrNull(CHAT_USER_SEARCH_TIMEOUT_MS) {
                    repository.searchUsers(cleanQuery)
                } ?: DiscoverRepositoryResult.Error("搜索用户超时，请稍后重试")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                DiscoverRepositoryResult.Error(error.message ?: "搜索用户失败")
            }
            applyChatUserSearchResult(cleanQuery, roomId, userId, result)
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

        launchChatWorker {
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

        launchChatWorker {
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

        launchChatWorker {
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
        joinMode: String = "inviteOnly",
    ) {
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.createRoom(name, description, joinMode),
                selectSavedRoom = false,
                successMessage = "",
            )
        }
    }

    fun joinInvitedRoom(invitation: ChatRoomInvitation) {
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }
        launchChatWorker {
            applyRoomMutationResult(
                result = repository.joinRoom(invitation.room.id),
                successMessage = "已加入聊天室",
            )
            refreshRoomExtras()
        }
    }

    fun ignoreRoomInvitation(roomId: String) {
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }
        launchChatWorker {
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
        joinMode: String,
    ) {
        val room = state.value.selectedRoom ?: return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
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

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.updateRoom(room.id, name, description, joinMode),
                selectSavedRoom = true,
                successMessage = "聊天室已更新",
            )
        }
    }

    fun updateSelectedRoomManagement(messageRetentionDays: Int?) {
        val room = state.value.selectedRoom ?: return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
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

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.updateRoomManagement(room.id, messageRetentionDays),
                selectSavedRoom = true,
                successMessage = "管理设置已更新",
            )
        }
    }

    fun inviteSelectedRoomMember(userId: String) {
        val room = state.value.selectedRoom ?: return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(
                isManagingRoom = true,
                roomManagementMessage = null,
                memberErrorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.inviteRoomMember(room.id, userId),
                successMessage = "已发送邀请",
            )
        }
    }

    fun leaveSelectedRoom() {
        val room = state.value.selectedRoom ?: return
        leaveRoom(room.id)
    }

    fun leaveRoom(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.leaveRoom(cleanRoomId),
                successMessage = "已退出聊天室",
            )
        }
    }

    fun deleteSelectedRoom() {
        val room = state.value.selectedRoom ?: return
        deleteRoom(room.id)
    }

    fun clearSelectedRoomMessages() {
        val room = state.value.selectedRoom ?: return
        clearRoomMessages(room.id)
    }

    fun clearRoomMessages(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, messageErrorMessage = null, requiresRelogin = false)
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.deleteAllRoomMessages(cleanRoomId),
                successMessage = "聊天室消息已清空",
            )
        }
    }

    fun deleteRoom(roomId: String) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.deleteRoom(cleanRoomId),
                successMessage = "聊天室已删除",
            )
        }
    }

    fun muteSelectedRoom(muted: Boolean) {
        val room = state.value.selectedRoom ?: return
        muteRoom(room.id, muted)
    }

    fun muteRoom(roomId: String, muted: Boolean) {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) return
        if (!state.value.chatAvailable) {
            rejectUnavailableChatManagement()
            return
        }
        if (state.value.isManagingRoom) return
        mutableState.update {
            it.copy(isManagingRoom = true, roomManagementMessage = null, requiresRelogin = false)
        }

        launchChatWorker {
            applyRoomMutationResult(
                result = repository.muteRoom(cleanRoomId, muted),
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
        // 检测@触发
        detectMentionTrigger(text)
    }

    /**
     * 检测输入框中的@符号并触发成员选择器
     *
     * 优化点：
     * 1. 支持光标在文本中间的情况
     * 2. 正确处理退格删除@符号
     * 3. 避免邮箱地址误触发（检查@前是否有字母/数字）
     * 4. 处理换行符边界
     */
    private fun detectMentionTrigger(text: String) {
        val cursorPosition = text.length
        if (cursorPosition == 0) {
            closeMentionPicker()
            return
        }

        // 从光标位置向前查找最近的@符号
        var atIndex = -1
        for (i in cursorPosition - 1 downTo 0) {
            val char = text[i]
            if (char == '@') {
                // 如果@前面是字母或数字，可能是邮箱地址的一部分，跳过
                // 例如：user@email.com 不应该触发
                if (i > 0 && text[i - 1].isLetterOrDigit()) {
                    continue
                }
                // 其他情况（行首、空白、中文、标点等）都触发@mention
                atIndex = i
                break
            }
            // 如果遇到换行符，停止查找（@不能跨行）
            if (char == '\n') break
        }

        if (atIndex == -1) {
            closeMentionPicker()
            return
        }

        // 提取@后的搜索关键词
        val searchQuery = text.substring(atIndex + 1, cursorPosition)
        
        // 如果包含空格或点号，说明已经完成选择或输入了其他内容（避免邮箱地址误触发）
        if (searchQuery.contains(' ') || searchQuery.contains('.')) {
            closeMentionPicker()
            return
        }
        
        // 如果搜索关键词为空（刚输入@），显示所有成员
        // 如果有搜索关键词，显示过滤后的成员
        mutableState.update {
            it.copy(
                mentionPickerVisible = true,
                mentionSearchQuery = searchQuery,
                mentionTriggerPosition = atIndex,
            )
        }
    }

    /**
     * 关闭@成员选择器
     */
    fun closeMentionPicker() {
        val current = state.value
        if (!current.mentionPickerVisible && current.mentionSearchQuery.isEmpty() && current.mentionTriggerPosition == -1) {
            return
        }
        mutableState.update {
            it.copy(
                mentionPickerVisible = false,
                mentionSearchQuery = "",
                mentionTriggerPosition = -1,
            )
        }
    }

    /**
     * 加载最近聊天的群成员
     *
     * 成员来源优先级：
     * 1. 消息历史中最近互动的成员（按互动时间倒序）
     * 2. 群成员列表中尚未出现在上述结果中的成员（作为补充，确保新聊天室也能 @人）
     */
    fun loadRecentChatMembers() {
        val current = state.value
        val roomId = current.selectedRoom?.id ?: return
        val messages = current.messages
        val members = current.members
        val currentUserId = currentAccountId()

        launchChatWorker {
            val recentMembers = extractRecentChatMembers(messages, currentUserId)
            val mergedMembers = mergeRecentAndGroupMembers(recentMembers, members, currentUserId)
            mutableState.update {
                it.copy(recentChatMembers = mergedMembers)
            }
        }
    }

    /**
     * 从消息历史中提取最近互动的成员
     */
    private fun extractRecentChatMembers(
        messages: List<ChatMessage>,
        currentUserId: String?,
    ): List<ChatRecentMember> {
        val userInteractionMap = mutableMapOf<String, MutableList<Long>>()
        val userMap = mutableMapOf<String, User>()

        for (message in messages) {
            val senderId = message.fromUser.id
            if (senderId.isBlank() || senderId == currentUserId) continue

            val timestamp = parseMessageTimestamp(message.createdAt, message.createdAtLabel)
            if (timestamp == 0L) continue

            // 记录发送者的互动时间
            userInteractionMap.getOrPut(senderId) { mutableListOf() }.add(timestamp)
            userMap[senderId] = message.fromUser
        }

        // 转换为 ChatRecentMember 列表并按最近互动时间排序
        return userInteractionMap.map { (userId, timestamps) ->
            val user = userMap[userId] ?: return@map null
            val sortedTimestamps = timestamps.sortedDescending()
            ChatRecentMember(
                user = user,
                lastInteractionTime = sortedTimestamps.first(),
                interactionCount = timestamps.size,
            )
        }.filterNotNull()
            .sortedByDescending { it.lastInteractionTime }
            .take(MAX_RECENT_MENTION_MEMBERS)
    }

    /**
     * 合并消息历史中的最近互动成员与群成员列表
     * 优先保留有互动记录的成员，群成员列表中未出现在互动记录里的成员作为补充追加
     */
    private fun mergeRecentAndGroupMembers(
        recentMembers: List<ChatRecentMember>,
        groupMembers: List<ChatRoomMember>,
        currentUserId: String?,
    ): List<ChatRecentMember> {
        val seenUserIds = recentMembers.map { it.user.id }.toMutableSet()
        val supplementMembers = groupMembers.asSequence()
            .filter { member ->
                val userId = member.user.id
                userId.isNotBlank() && userId != currentUserId && userId !in seenUserIds
            }
            .map { member ->
                ChatRecentMember(
                    user = member.user,
                    lastInteractionTime = 0L,
                    interactionCount = 0,
                )
            }
            .onEach { seenUserIds.add(it.user.id) }
            .toList()
        return (recentMembers + supplementMembers).take(MAX_RECENT_MENTION_MEMBERS)
    }

    /**
     * 解析消息时间戳
     *
     * @param createdAt ISO 8601 格式的时间字符串
     * @return 时间戳（毫秒），解析失败返回 0
     */
    private fun parseMessageTimestamp(createdAt: String): Long {
        return try {
            Instant.parse(createdAt).toEpochMilliseconds()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 选择@成员并插入到输入框
     *
     * 替换逻辑：
     * 1. 从 triggerPosition 开始，到 triggerPosition + 1 + searchQuery.length 结束，
     *    这一段是 "@搜索关键词"，需要被替换为 "@成员昵称 "
     * 2. 防御性检查：确保索引不越界
     */
    fun selectMentionMember(member: ChatRecentMember) {
        val current = state.value
        val draft = current.messageDraft
        val triggerPosition = current.mentionTriggerPosition

        if (triggerPosition == -1) return
        if (triggerPosition >= draft.length) return

        val displayName = member.user.displayName.trim().ifBlank { member.user.username.trim() }
        if (displayName.isBlank()) return

        // 计算替换范围
        val replaceEnd = triggerPosition + 1 + current.mentionSearchQuery.length
        // 防御性检查：确保结束位置不越界
        val safeReplaceEnd = replaceEnd.coerceAtMost(draft.length)

        // 构建新的文本：@符号前的内容 + @成员昵称 + 空格 + @符号后的内容
        val beforeAt = draft.substring(0, triggerPosition)
        val afterAt = draft.substring(safeReplaceEnd)
        val mentionText = "@$displayName "
        val newDraft = beforeAt + mentionText + afterAt

        mutableState.update {
            it.copy(
                messageDraft = newDraft,
                mentionPickerVisible = false,
                mentionSearchQuery = "",
                mentionTriggerPosition = -1,
            )
        }
    }

    /**
     * 获取过滤后的@提及成员列表
     * 根据搜索关键词过滤最近聊天的群成员
     */
    fun getFilteredMentionMembers(): List<ChatRecentMember> {
        val current = state.value
        val query = current.mentionSearchQuery.trim().lowercase()
        val members = current.recentChatMembers

        if (query.isEmpty()) return members

        return members.filter { member ->
            val displayNameLower = member.user.displayName.lowercase()
            val usernameLower = member.user.username.lowercase()
            displayNameLower.contains(query) || usernameLower.contains(query)
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
        launchChatWorker {
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

        val uploadStartState = state.value
        val roomId = uploadStartState.selectedRoom?.id
        val userId = uploadStartState.selectedUserConversation?.user?.id
        val isContextBound = roomId != null || userId != null
        mediaUploadRequestId += 1
        val uploadRequestId = mediaUploadRequestId

        mutableState.update {
            it.copy(
                isUploadingMedia = true,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
            fun ChatUiState.canApplyUploadResult(): Boolean {
                return mediaUploadRequestId == uploadRequestId &&
                    (!isContextBound || isCurrentMessageActionContext(roomId, userId))
            }

            fun ChatUiState.ignoreStaleUploadResult(): ChatUiState {
                return if (mediaUploadRequestId == uploadRequestId) {
                    copy(isUploadingMedia = false)
                } else {
                    this
                }
            }

            var shouldUploadPending = false
            when (val result = uploadRepository.upload(upload)) {
                is DriveFileRepositoryResult.Success -> mutableState.update { current ->
                    val canApply = current.canApplyUploadResult()
                    shouldUploadPending = canApply
                    if (!canApply) {
                        current.ignoreStaleUploadResult()
                    } else {
                        val attachments = (current.attachments + result.file.toChatComposerAttachment())
                            .distinctBy { attachment -> attachment.id }
                        current.copy(
                            attachedFile = attachments.lastOrNull()?.file,
                            attachments = attachments,
                            isUploadingMedia = false,
                            messageErrorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val canApply = current.canApplyUploadResult()
                    shouldUploadPending = canApply
                    if (!canApply) {
                        current.ignoreStaleUploadResult()
                    } else {
                        current.copy(
                            isUploadingMedia = false,
                            messageErrorMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update { current ->
                    val canApply = current.canApplyUploadResult()
                    shouldUploadPending = canApply
                    if (!canApply) {
                        current.ignoreStaleUploadResult()
                    } else {
                        current.copy(
                            isUploadingMedia = false,
                            messageErrorMessage = result.message,
                            requiresRelogin = false,
                        )
                    }
                }
                is DriveFileRepositoryResult.Error -> mutableState.update { current ->
                    val canApply = current.canApplyUploadResult()
                    shouldUploadPending = canApply
                    if (!canApply) {
                        current.ignoreStaleUploadResult()
                    } else {
                        current.copy(
                            isUploadingMedia = false,
                            messageErrorMessage = result.message,
                            requiresRelogin = false,
                        )
                    }
                }
            }
            if (shouldUploadPending) {
                uploadPendingMediaIfAny()
            } else if (mediaUploadRequestId == uploadRequestId) {
                pendingMediaUploads = emptyList()
            }
        }
    }

    fun removeAttachedFile() {
        pendingMediaUploads = emptyList()
        mediaUploadRequestId += 1
        mutableState.update {
            it.copy(
                attachedFile = null,
                attachments = emptyList(),
                isUploadingMedia = false,
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

        // 解析消息中的@成员
        val mentionedUserIds = extractMentionedUserIds(text, current.recentChatMembers)

        mutableState.update {
            it.copy(
                isSendingMessage = true,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
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
                // 如果有@成员，发送提醒通知
                if (mentionedUserIds.isNotEmpty()) {
                    sendMentionNotifications(room.id, mentionedUserIds, text)
                }
            } else if (userConversation != null) {
                applyUserMessageResult(
                    userId = userConversation.user.id,
                    result = repository.sendUserMessage(
                        userId = userConversation.user.id,
                        text = text,
                        fileIds = fileIds,
                        replyId = current.replyingMessage?.messageId,
                        quoteId = current.quotedMessage?.messageId,
                    ),
                    loadingMore = false,
                )
            }
        }
    }

    /**
     * 从消息文本中提取被@的用户ID列表
     * 同时支持 @username@host 和 @displayName 两种提及格式，复用 mentionsChatUser 检测逻辑
     */
    private fun extractMentionedUserIds(
        text: String,
        recentMembers: List<ChatRecentMember>,
    ): List<String> {
        if (text.isBlank() || recentMembers.isEmpty()) return emptyList()
        return recentMembers
            .filter { member -> text.mentionsChatUser(member.user) }
            .map { it.user.id }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 发送@提醒通知给被@的用户
     *
     * 当前实现说明：
     * Sharkey 的 notifications/create API 仅能为当前认证用户（发送者）创建通知，
     * 无法直接向其他用户发送通知。被@成员的通知提醒由以下机制保障：
     * 1. 接收方客户端通过 mentionsChatUser() 检测消息文本中的 @提及
     * 2. 命中后通过 toChatAttentionToastIfNeeded() 生成 ChatAttentionKind.Mention 提醒
     * 3. 流式消息通道实时推送，确保在线用户即时收到提醒
     *
     * 此方法保留用于未来后端支持跨用户通知时的扩展接入点。
     */
    private suspend fun sendMentionNotifications(
        roomId: String,
        mentionedUserIds: List<String>,
        messageText: String,
    ) {
        // 被@成员的通知由接收方客户端的 mentionsChatUser 检测机制处理，无需发送者侧主动推送。
        // 若后端未来支持聊天提及通知 API，可在此处接入。
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
        if (!isServerChatMessageId(cleanMessageId)) {
            mutableState.update {
                it.copy(
                    messageErrorMessage = "这条消息还没有服务器 ID，无法同步删除",
                    requiresRelogin = false,
                )
            }
            return
        }
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

        launchChatWorker {
            when (
                val result = repository.deleteMessage(
                    messageId = cleanMessageId,
                    roomId = roomId,
                    userId = userId,
                )
            ) {
                is ChatMessageRepositoryResult.Deleted -> mutableState.update { current ->
                    val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                    current.copy(
                        messages = if (isCurrentContext) {
                            current.messages.filterNot { message -> message.id == result.messageId }
                        } else {
                            current.messages
                        },
                        messageSearchResults = if (isCurrentContext) {
                            current.messageSearchResults.filterNot { message -> message.id == result.messageId }
                        } else {
                            current.messageSearchResults
                        },
                        pendingMessageDeleteIds = current.pendingMessageDeleteIds - result.messageId - cleanMessageId,
                        messageErrorMessage = if (isCurrentContext) null else current.messageErrorMessage,
                        requiresRelogin = if (isCurrentContext) false else current.requiresRelogin,
                    )
                }
                ChatMessageRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                    current.copy(
                        pendingMessageDeleteIds = current.pendingMessageDeleteIds - cleanMessageId,
                        messageErrorMessage = if (isCurrentContext) "登录已失效，请重新登录" else current.messageErrorMessage,
                        requiresRelogin = if (isCurrentContext) true else current.requiresRelogin,
                    )
                }
                is ChatMessageRepositoryResult.Error -> mutableState.update { current ->
                    val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                    current.copy(
                        pendingMessageDeleteIds = current.pendingMessageDeleteIds - cleanMessageId,
                        messageErrorMessage = if (isCurrentContext) result.message else current.messageErrorMessage,
                        requiresRelogin = if (isCurrentContext) false else current.requiresRelogin,
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
        val current = state.value
        val roomId = current.selectedRoom?.id
        val userId = current.selectedUserConversation?.user?.id

        mutableState.update {
            it.copy(
                pendingMessageReactionIds = it.pendingMessageReactionIds + messageId,
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }

        launchChatWorker {
            val result = if (react) {
                repository.reactToMessage(messageId, reaction)
            } else {
                repository.unreactToMessage(messageId, reaction)
            }
            applyReactionResult(
                messageId = messageId,
                reaction = reaction,
                react = react,
                roomId = roomId,
                userId = userId,
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
                        localReadMarkers = localUnread.roomReadMarkers,
                        persistUnread = { counts ->
                            saveLocalUnreadSnapshot(
                                localUnread.copy(
                                    roomCounts = counts,
                                    pinnedRoomIds = current.pinnedRoomIds,
                                    pinnedUserIds = current.pinnedUserConversationIds,
                                    roomGroups = current.roomGroups,
                                ),
                            )
                        },
                    )
                    .mergeLocallyActiveRooms(
                        previous = current.rooms,
                        localUnreadCounts = localUnread.roomCounts,
                        roomAttentionKinds = current.roomAttentionKinds,
                        selectedRoomId = current.selectedRoom?.id,
                    )
                    .sortedByPinnedIds(current.pinnedRoomIds) { room -> room.id }
                current.selectedRoom?.id
                    ?.let { selectedRoomId -> nextRooms.firstOrNull { room -> room.id == selectedRoomId } }
                    ?.let(::markLocalRoomRead)
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
        inboxResult: ChatRoomInvitationRepositoryResult,
        outboxResult: ChatRoomInvitationRepositoryResult,
    ) {
        mutableState.update { current ->
            var next = current.copy(isLoadingRoomExtras = false)
            when (ownedResult) {
                is ChatRepositoryResult.Success -> {
                    val localUnread = localUnreadSnapshot()
                    val ownedRooms = ownedResult.rooms.mergeRoomUnreadCounts(
                        previous = current.ownedRooms,
                        selectedRoomId = current.selectedRoom?.id,
                        localUnreadCounts = localUnread.roomCounts,
                        localReadMarkers = localUnread.roomReadMarkers,
                        persistUnread = { counts ->
                            saveLocalUnreadSnapshot(
                                localUnread.copy(
                                    roomCounts = counts,
                                    pinnedRoomIds = current.pinnedRoomIds,
                                    pinnedUserIds = current.pinnedUserConversationIds,
                                    roomGroups = current.roomGroups,
                                ),
                            )
                        },
                    )
                    next = next.copy(
                        ownedRooms = ownedRooms,
                        rooms = next.rooms
                            .mergeOwnedChatRooms(ownedRooms)
                            .sortedByPinnedIds(next.pinnedRoomIds) { room -> room.id },
                        selectedRoom = next.selectedRoom?.let { selectedRoom ->
                            ownedRooms.firstOrNull { room -> room.id == selectedRoom.id }?.let { ownedRoom ->
                                selectedRoom.mergeOwnedChatRoom(ownedRoom)
                            } ?: selectedRoom
                        },
                    )
                }
                ChatRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is ChatRepositoryResult.Error -> next = next.copy(roomManagementMessage = ownedResult.message)
            }
            when (inboxResult) {
                is ChatRoomInvitationRepositoryResult.Success -> {
                    next = next.copy(roomInvitationInbox = inboxResult.invitations)
                }
                ChatRoomInvitationRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is ChatRoomInvitationRepositoryResult.Error -> {
                    next = next.copy(roomManagementMessage = inboxResult.message)
                }
            }
            when (outboxResult) {
                is ChatRoomInvitationRepositoryResult.Success -> {
                    next = next.copy(roomInvitationOutbox = outboxResult.invitations)
                }
                ChatRoomInvitationRepositoryResult.Unauthorized -> next = next.copy(
                    roomManagementMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
                is ChatRoomInvitationRepositoryResult.Error -> {
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
                    localReadMarkers = localUnread.roomReadMarkers,
                    persistUnread = { counts ->
                        saveLocalUnreadSnapshot(
                            localUnread.copy(
                                roomCounts = counts,
                                pinnedRoomIds = current.pinnedRoomIds,
                                pinnedUserIds = current.pinnedUserConversationIds,
                                roomGroups = current.roomGroups,
                            ),
                        )
                    },
                ).map { room ->
                    if (markSelectedRoomRead && room.id == selectedRoomId) {
                        markLocalRoomRead(room)
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
                            localReadMarkers = localUnread.userReadMarkers,
                            persistUnread = { counts ->
                                saveLocalUnreadSnapshot(
                                    localUnread.copy(
                                        userCounts = counts,
                                        pinnedRoomIds = current.pinnedRoomIds,
                                        pinnedUserIds = current.pinnedUserConversationIds,
                                        roomGroups = current.roomGroups,
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
                    selectedUserId
                        ?.let { userId -> nextConversations.firstOrNull { it.user.id == userId } }
                        ?.let(::markLocalUserRead)
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

    private suspend fun resolveUserConversationAttentionReferences(
        result: ChatUserConversationRepositoryResult,
    ): ChatUserConversationRepositoryResult {
        if (result !is ChatUserConversationRepositoryResult.Success) return result
        val resolvedConversations = result.conversations.map { conversation ->
            val latestMessage = conversation.latestMessage ?: return@map conversation
            if (!latestMessage.requiresRealtimeAttentionResolution()) return@map conversation
            conversation.copy(
                latestMessage = repository.resolveRealtimeMessage(
                    message = latestMessage,
                    directUserId = conversation.user.id,
                ),
            )
        }
        return result.copy(conversations = resolvedConversations)
    }

    private fun applyRoomMessageResult(
        roomId: String,
        result: ChatMessageRepositoryResult,
        loadingMore: Boolean,
        requestGeneration: Int? = null,
    ) {
        if (!matchesRoomMessageRequestGeneration(roomId, requestGeneration)) return
        when (result) {
            is ChatMessageRepositoryResult.Success -> {
                val latestReadMarker = if (loadingMore) {
                    ""
                } else {
                    result.messages.ensureChronologicalMessages().lastOrNull()?.unreadMarker().orEmpty()
                }
                mutableState.update {
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
                if (latestReadMarker.isNotBlank() && state.value.selectedRoom?.id == roomId) {
                    markLocalRoomRead(roomId, latestReadMarker)
                }
            }
            is ChatMessageRepositoryResult.Created -> {
                mutableState.update {
                    if (it.selectedRoom?.id != roomId) return@update it
                    it.copy(
                        rooms = it.rooms
                            .withChatRoomLatestMessage(roomId, result.message)
                            .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                        selectedRoom = it.selectedRoom?.withLatestMessageAt(result.message),
                        messages = it.messages.withChronologicalMessage(result.message),
                        messageDraft = "",
                        mentionPickerVisible = false,
                        mentionSearchQuery = "",
                        mentionTriggerPosition = -1,
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
                if (state.value.selectedRoom?.id == roomId) {
                    markLocalRoomRead(roomId, result.message.unreadMarker())
                }
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
        requestGeneration: Int,
    ) {
        if (!matchesRoomMessageRequestGeneration(roomId, requestGeneration)) return
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
        requestGeneration: Int? = null,
    ) {
        if (!matchesUserMessageRequestGeneration(userId, requestGeneration)) return
        when (result) {
            is ChatMessageRepositoryResult.Success -> {
                val latestReadMarker = if (loadingMore) {
                    ""
                } else {
                    result.messages.ensureChronologicalMessages().lastOrNull()?.unreadMarker().orEmpty()
                }
                mutableState.update {
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
                if (latestReadMarker.isNotBlank() && state.value.selectedUserConversation?.user?.id == userId) {
                    markLocalUserRead(userId, latestReadMarker)
                }
            }
            is ChatMessageRepositoryResult.Created -> {
                mutableState.update {
                    if (it.selectedUserConversation?.user?.id != userId) return@update it
                    hiddenUserConversationLatestMessageIds = hiddenUserConversationLatestMessageIds - userId
                    val nextMessages = it.messages.withChronologicalMessage(result.message)
                    it.copy(
                        userConversations = it.userConversations
                            .withUserConversationLatest(userId, result.message)
                            .sortedByPinnedIds(it.pinnedUserConversationIds) { conversation -> conversation.user.id },
                        selectedUserConversation = it.selectedUserConversation?.copy(
                            latestMessage = result.message,
                            unreadCount = 0,
                        ),
                        messages = nextMessages,
                        messageDraft = "",
                        mentionPickerVisible = false,
                        mentionSearchQuery = "",
                        mentionTriggerPosition = -1,
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
                if (state.value.selectedUserConversation?.user?.id == userId) {
                    markLocalUserRead(userId, result.message.unreadMarker())
                }
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
        requestGeneration: Int,
    ) {
        if (!matchesUserMessageRequestGeneration(userId, requestGeneration)) return
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
        fun ChatUiState.matchesMessageSearchRequest(): Boolean {
            val sameConversation = if (roomId != null) {
                selectedRoom?.id == roomId
            } else {
                selectedUserConversation?.user?.id == userId
            }
            return sameConversation && messageSearchQuery == query
        }

        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update { current ->
                if (!current.matchesMessageSearchRequest()) return@update current
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
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update { current ->
                if (!current.matchesMessageSearchRequest()) return@update current
                current.copy(
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update { current ->
                if (!current.matchesMessageSearchRequest()) return@update current
                current.copy(
                    isSearchingMessages = if (loadingMore) current.isSearchingMessages else false,
                    isLoadingMoreMessageSearch = false,
                    messageSearchServerUntilId = if (loadingMore) current.messageSearchServerUntilId else null,
                    messageSearchErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is ChatMessageRepositoryResult.Created,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> mutableState.update { current ->
                if (!current.matchesMessageSearchRequest()) return@update current
                current.copy(
                    isSearchingMessages = false,
                    isLoadingMoreMessageSearch = false,
                )
            }
        }
    }

    private fun applyChatUserSearchResult(
        query: String,
        roomId: String?,
        userId: String?,
        result: DiscoverRepositoryResult,
    ) {
        fun ChatUiState.matchesChatUserSearchRequest(): Boolean {
            return chatUserSearchQuery == query &&
                selectedRoom?.id == roomId &&
                selectedUserConversation?.user?.id == userId
        }

        when (result) {
            is DiscoverRepositoryResult.UserSuccess -> mutableState.update { current ->
                if (!current.matchesChatUserSearchRequest()) return@update current
                current.copy(
                    chatUserSearchResults = result.users.distinctBy { user -> user.id }.take(CHAT_USER_SEARCH_RESULT_LIMIT),
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            DiscoverRepositoryResult.Unauthorized -> mutableState.update { current ->
                if (!current.matchesChatUserSearchRequest()) return@update current
                current.copy(
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DiscoverRepositoryResult.Error -> mutableState.update { current ->
                if (!current.matchesChatUserSearchRequest()) return@update current
                current.copy(
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.Success,
            is DiscoverRepositoryResult.PinnedUsersSuccess,
            is DiscoverRepositoryResult.DiscoverySectionsSuccess,
            is DiscoverRepositoryResult.SearchTrendsSuccess,
            DiscoverRepositoryResult.RecommendationFeedbackSuccess,
            is DiscoverRepositoryResult.RoleSuccess,
            is DiscoverRepositoryResult.RoleDetailSuccess,
            is DiscoverRepositoryResult.RoleUsersSuccess,
            is DiscoverRepositoryResult.RoleNotesSuccess,
            is DiscoverRepositoryResult.TrendSuccess,
            is DiscoverRepositoryResult.HashtagSuccess,
            is DiscoverRepositoryResult.FederationSuccess,
            is DiscoverRepositoryResult.FederationInstanceSuccess,
            is DiscoverRepositoryResult.FederationFollowSuccess,
            is DiscoverRepositoryResult.FederationStatsSuccess,
            is DiscoverRepositoryResult.RecommendedTimelineSuccess,
            DiscoverRepositoryResult.FederationActionSuccess,
                -> mutableState.update { current ->
                if (!current.matchesChatUserSearchRequest()) return@update current
                current.copy(
                    isSearchingChatUsers = false,
                    chatUserSearchErrorMessage = "用户搜索返回了无法识别的结果",
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyBackgroundRoomMessageRefreshResult(
        roomId: String,
        result: ChatMessageRepositoryResult,
        requestGeneration: Int,
    ) {
        if (!matchesRoomMessageRequestGeneration(roomId, requestGeneration)) return
        when (result) {
            is ChatMessageRepositoryResult.Success -> {
                val latestReadMarker = result.messages.ensureChronologicalMessages().lastOrNull()?.unreadMarker().orEmpty()
                mutableState.update { current ->
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
                if (latestReadMarker.isNotBlank() && state.value.selectedRoom?.id == roomId) {
                    markLocalRoomRead(roomId, latestReadMarker)
                }
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
        requestGeneration: Int,
    ) {
        if (!matchesUserMessageRequestGeneration(userId, requestGeneration)) return
        when (result) {
            is ChatMessageRepositoryResult.Success -> {
                val latestReadMarker = result.messages.ensureChronologicalMessages().lastOrNull()?.unreadMarker().orEmpty()
                mutableState.update { current ->
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
                if (latestReadMarker.isNotBlank() && state.value.selectedUserConversation?.user?.id == userId) {
                    markLocalUserRead(userId, latestReadMarker)
                }
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
                val savedOwnedRoom = it.ownedRooms.firstOrNull { room -> room.id == result.room.id }
                    ?.mergeOwnedChatRoom(result.room)
                    ?: result.room
                it.copy(
                    rooms = (listOf(savedRoom) + it.rooms.filterNot { room -> room.id == savedRoom.id })
                        .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    ownedRooms = (listOf(savedOwnedRoom) + it.ownedRooms.filterNot { room -> room.id == savedOwnedRoom.id })
                        .sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    selectedRoom = if (selectSavedRoom && it.selectedRoom?.id == savedRoom.id) {
                        savedRoom
                    } else {
                        it.selectedRoom
                    },
                    isManagingRoom = false,
                    roomManagementMessage = successMessage.takeIf { message -> message.isNotBlank() },
                    errorMessage = null,
                    memberErrorMessage = null,
                    messageErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            is ChatRoomMutationRepositoryResult.RoomRemoved -> {
                if (state.value.selectedRoom?.id == result.roomId) {
                    stopMessageStreaming()
                }
                clearLocalRoomUnread(result.roomId)
                mutableState.update {
                    val removedSelectedRoom = it.selectedRoom?.id == result.roomId
                    val nextGroups = it.roomGroups - result.roomId
                    saveLocalChatPreferences(
                        pinnedRoomIds = it.pinnedRoomIds - result.roomId,
                        roomGroups = nextGroups,
                    )
                    it.copy(
                        rooms = it.rooms.filterNot { room -> room.id == result.roomId },
                        ownedRooms = it.ownedRooms.filterNot { room -> room.id == result.roomId },
                        roomInvitationOutbox = it.roomInvitationOutbox.filterNot { invitation ->
                            invitation.room.id == result.roomId
                        },
                        pinnedRoomIds = it.pinnedRoomIds - result.roomId,
                        roomGroups = nextGroups,
                        selectedRoom = if (removedSelectedRoom) null else it.selectedRoom,
                        messages = if (removedSelectedRoom) emptyList() else it.messages,
                        members = if (removedSelectedRoom) emptyList() else it.members,
                        isManagingRoom = false,
                        showingMembers = if (removedSelectedRoom) false else it.showingMembers,
                        roomManagementMessage = successMessage,
                        selectedRoomUnreadCount = if (removedSelectedRoom) 0 else it.selectedRoomUnreadCount,
                        unreadJumpMessageId = if (removedSelectedRoom) null else it.unreadJumpMessageId,
                        requiresRelogin = false,
                    )
                }
            }
            is ChatRoomMutationRepositoryResult.RoomMessagesCleared -> {
                clearLocalRoomUnread(result.roomId)
                bumpRoomMessageRequestGeneration(result.roomId)
                mutableState.update {
                    val clearedSelectedRoom = it.selectedRoom?.id == result.roomId
                    it.copy(
                        messages = if (clearedSelectedRoom) emptyList() else it.messages,
                        messageSearchResults = if (clearedSelectedRoom) emptyList() else it.messageSearchResults,
                        messageSearchQuery = if (clearedSelectedRoom) "" else it.messageSearchQuery,
                        messageSearchServerUntilId = if (clearedSelectedRoom) null else it.messageSearchServerUntilId,
                        messageSearchEndReached = if (clearedSelectedRoom) true else it.messageSearchEndReached,
                        isLoadingMessages = if (clearedSelectedRoom) false else it.isLoadingMessages,
                        isLoadingOlderMessages = if (clearedSelectedRoom) false else it.isLoadingOlderMessages,
                        messagesEndReached = if (clearedSelectedRoom) true else it.messagesEndReached,
                        isSearchingMessages = if (clearedSelectedRoom) false else it.isSearchingMessages,
                        isLoadingMoreMessageSearch = if (clearedSelectedRoom) false else it.isLoadingMoreMessageSearch,
                        messageSearchErrorMessage = if (clearedSelectedRoom) null else it.messageSearchErrorMessage,
                        selectedRoomUnreadCount = if (clearedSelectedRoom) 0 else it.selectedRoomUnreadCount,
                        unreadJumpMessageId = if (clearedSelectedRoom) null else it.unreadJumpMessageId,
                        rooms = it.rooms.map { room ->
                            if (room.id == result.roomId) {
                                room.copy(unreadCount = 0, latestMessageAtLabel = "", latestMessageMarker = "")
                            } else {
                                room
                            }
                        }.sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                        ownedRooms = it.ownedRooms.map { room ->
                            if (room.id == result.roomId) {
                                room.copy(unreadCount = 0, latestMessageAtLabel = "", latestMessageMarker = "")
                            } else {
                                room
                            }
                        }.sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                        selectedRoom = it.selectedRoom?.let { room ->
                            if (room.id == result.roomId) {
                                room.copy(unreadCount = 0, latestMessageAtLabel = "", latestMessageMarker = "")
                            } else {
                                room
                            }
                        },
                        isManagingRoom = false,
                        roomManagementMessage = successMessage,
                        memberErrorMessage = null,
                        messageErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
            }
            is ChatRoomMutationRepositoryResult.RoomMuted -> mutableState.update {
                it.copy(
                    rooms = it.rooms.map { room ->
                        if (room.id == result.roomId) room.copy(isMuted = result.muted) else room
                    }.sortedByPinnedIds(it.pinnedRoomIds) { room -> room.id },
                    ownedRooms = it.ownedRooms.map { room ->
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
        roomId: String?,
        userId: String?,
        result: ChatMessageRepositoryResult,
    ) {
        when (result) {
            ChatMessageRepositoryResult.ReactionUpdated -> mutableState.update { current ->
                val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                current.copy(
                    messages = if (isCurrentContext) {
                        current.messages.withMessageReactionUpdated(messageId, reaction, react)
                    } else {
                        current.messages
                    },
                    pendingMessageReactionIds = current.pendingMessageReactionIds - messageId,
                    messageErrorMessage = if (isCurrentContext) null else current.messageErrorMessage,
                    requiresRelogin = if (isCurrentContext) false else current.requiresRelogin,
                )
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update { current ->
                val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                current.copy(
                    pendingMessageReactionIds = current.pendingMessageReactionIds - messageId,
                    messageErrorMessage = if (isCurrentContext) "登录已失效，请重新登录" else current.messageErrorMessage,
                    requiresRelogin = if (isCurrentContext) true else current.requiresRelogin,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update { current ->
                val isCurrentContext = current.isCurrentMessageActionContext(roomId, userId)
                current.copy(
                    pendingMessageReactionIds = current.pendingMessageReactionIds - messageId,
                    messageErrorMessage = if (isCurrentContext) result.message else current.messageErrorMessage,
                    requiresRelogin = if (isCurrentContext) false else current.requiresRelogin,
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
                    launchChatWorker { repository.cacheRoomMessage(roomId, event.message) }
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
                if (event.message.roomId == roomId && state.value.selectedRoom?.id == roomId) {
                    markLocalRoomRead(roomId, event.message.unreadMarker())
                }
            }
            is ChatStreamingEvent.MessageDeleted -> {
                val sourceRoomId = event.source.roomId
                if (sourceRoomId != null && sourceRoomId != roomId) return
                recordRealtimeMessageDeletion(
                    messageId = event.messageId,
                    roomId = sourceRoomId ?: roomId,
                )
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
                    launchChatWorker { repository.cacheUserMessage(userId, event.message) }
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
                if (
                    event.message.belongsToUserConversation(userId) &&
                    state.value.selectedUserConversation?.user?.id == userId
                ) {
                    markLocalUserRead(userId, event.message.unreadMarker())
                }
            }
            is ChatStreamingEvent.MessageDeleted -> {
                val sourceUserId = event.source.userId
                if (sourceUserId != null && sourceUserId != userId) return
                recordRealtimeMessageDeletion(
                    messageId = event.messageId,
                    directUserId = sourceUserId ?: userId,
                )
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
    localReadMarkers: Map<String, String>,
    persistUnread: ((Map<String, Int>) -> Unit)?,
): List<ChatRoom> {
    val previousById = previous.associateBy { it.id }
    var changedLocalCounts = false
    val nextLocalCounts = localUnreadCounts.toMutableMap()
    val merged = map { room ->
        val roomLatestMarker = room.unreadMarker()
        val readMarker = localReadMarkers[room.id].orEmpty().trim()
        if (room.id == selectedRoomId) {
            nextLocalCounts.remove(room.id)?.let { changedLocalCounts = true }
            return@map room.copy(unreadCount = 0)
        }
        if (roomLatestMarker.matchesReadMarker(readMarker)) {
            nextLocalCounts.remove(room.id)?.let { changedLocalCounts = true }
            return@map room.copy(unreadCount = 0)
        }
        val previousRoom = previousById[room.id]
        val localCount = nextLocalCounts[room.id].coercePositive()
        val remoteCount = room.unreadCount.coerceAtLeast(0)
        val previousCount = previousRoom?.unreadCount.coercePositive()
        val previousLatestMarker = previousRoom?.unreadMarker().orEmpty()
        val latestChanged = previousRoom != null &&
            room.hasStableUnreadMarker() &&
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

private fun List<ChatRoom>.mergeLocallyActiveRooms(
    previous: List<ChatRoom>,
    localUnreadCounts: Map<String, Int>,
    roomAttentionKinds: Map<String, ChatAttentionKind>,
    selectedRoomId: String?,
): List<ChatRoom> {
    if (previous.isEmpty()) return this
    val remoteIds = mapTo(HashSet()) { room -> room.id }
    val localUnreadIds = localUnreadCounts
        .filterValues { count -> count > 0 }
        .keys
    val retainedRooms = previous.mapNotNull { room ->
        if (room.id.isBlank() || room.id in remoteIds) return@mapNotNull null
        val localUnreadCount = localUnreadCounts[room.id].coercePositive()
        val hasLocalActivity = room.id == selectedRoomId ||
            room.id in localUnreadIds ||
            room.id in roomAttentionKinds ||
            (room.unreadCount.coerceAtLeast(0) > 0 && room.unreadMarker().isNotBlank())
        if (!hasLocalActivity) return@mapNotNull null
        room.copy(unreadCount = maxOf(room.unreadCount.coerceAtLeast(0), localUnreadCount))
    }
    if (retainedRooms.isEmpty()) return this
    return this + retainedRooms
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

private fun List<ChatRoom>.withRealtimeRoomMessage(
    roomId: String,
    message: ChatMessage,
    incrementUnread: Boolean,
): List<ChatRoom> {
    if (roomId.isBlank()) return this
    val index = indexOfFirst { room -> room.id == roomId }
    val sourceRoom = if (index >= 0) this[index] else placeholderRoom(roomId)
    val messageMarker = message.unreadMarker()
    val latestChanged = message.hasStableUnreadMarker() &&
        messageMarker.isNotBlank() &&
        messageMarker != sourceRoom.unreadMarker()
    val room = sourceRoom.withLatestMessageAt(message)
        .let { current ->
            if (incrementUnread && latestChanged) {
                current.copy(unreadCount = current.unreadCount.coerceAtLeast(0) + 1)
            } else {
                current
            }
        }
    return listOf(room) + filterIndexed { currentIndex, _ -> currentIndex != index }
}

private fun List<ChatRoom>.mergeOwnedChatRooms(ownedRooms: List<ChatRoom>): List<ChatRoom> {
    if (ownedRooms.isEmpty()) return this
    val ownedById = ownedRooms.associateBy { room -> room.id }
    val currentRoomIds = mapTo(HashSet()) { room -> room.id }
    val mergedCurrentRooms = map { room ->
        ownedById[room.id]?.let { ownedRoom -> room.mergeOwnedChatRoom(ownedRoom) } ?: room
    }
    return mergedCurrentRooms + ownedRooms.filterNot { room -> room.id in currentRoomIds }
}

private fun ChatRoom.mergeOwnedChatRoom(ownedRoom: ChatRoom): ChatRoom {
    return ownedRoom.copy(
        membershipId = membershipId.ifBlank { ownedRoom.membershipId },
        unreadCount = maxOf(unreadCount.coerceAtLeast(0), ownedRoom.unreadCount.coerceAtLeast(0)),
        latestMessageAtLabel = latestMessageAtLabel.ifBlank { ownedRoom.latestMessageAtLabel },
        latestMessageMarker = latestMessageMarker.ifBlank { ownedRoom.latestMessageMarker },
    )
}

private fun ChatRoom.withLatestMessageAt(message: ChatMessage): ChatRoom {
    return copy(
        latestMessageAtLabel = message.createdAtLabel,
        latestMessageMarker = message.unreadMarker(),
    )
}

private fun ChatUiState.inferRealtimeDeletionTarget(
    messageId: String,
    roomId: String?,
    directUserId: String?,
): ChatMessageDeletionTarget {
    var targetRoomId = roomId
    var targetDirectUserId = directUserId
    if (targetRoomId == null && targetDirectUserId == null) {
        val currentMessagesContainDeleted = messages.any { message -> message.id == messageId }
        when {
            selectedRoom != null && currentMessagesContainDeleted -> targetRoomId = selectedRoom.id
            selectedUserConversation != null &&
                (currentMessagesContainDeleted || selectedUserConversation.latestMessage?.id == messageId) ->
                targetDirectUserId = selectedUserConversation.user.id
        }
    }
    if (targetDirectUserId == null) {
        targetDirectUserId = userConversations
            .firstOrNull { conversation -> conversation.latestMessage?.id == messageId }
            ?.user
            ?.id
    }
    return ChatMessageDeletionTarget(
        roomId = targetRoomId,
        directUserId = targetDirectUserId,
    )
}

private fun ChatUiState.withRealtimeMessageDeleted(
    messageId: String,
    target: ChatMessageDeletionTarget,
): ChatUiState {
    val currentMessagesContainDeleted = messages.any { message -> message.id == messageId }
    val deleteSelectedRoomMessage = selectedRoom != null &&
        currentMessagesContainDeleted &&
        (target.roomId == selectedRoom.id || (target.roomId == null && target.directUserId == null))
    val deleteSelectedUserMessage = selectedUserConversation != null &&
        currentMessagesContainDeleted &&
        (target.directUserId == selectedUserConversation.user.id || (target.roomId == null && target.directUserId == null))
    val deleteSelectedMessage = deleteSelectedRoomMessage || deleteSelectedUserMessage
    val nextMessages = if (deleteSelectedMessage) {
        messages.filterNot { message -> message.id == messageId }
    } else {
        messages
    }
    val replacementLatestMessage = if (deleteSelectedMessage) nextMessages.lastOrNull() else null
    val nextRooms = target.roomId?.let { roomId ->
        rooms.withDeletedLatestRoomMessage(
            roomId = roomId,
            messageId = messageId,
            replacement = replacementLatestMessage.takeIf { deleteSelectedRoomMessage },
        )
    } ?: rooms
    val nextOwnedRooms = target.roomId?.let { roomId ->
        ownedRooms.withDeletedLatestRoomMessage(
            roomId = roomId,
            messageId = messageId,
            replacement = replacementLatestMessage.takeIf { deleteSelectedRoomMessage },
        )
    } ?: ownedRooms
    val nextUserConversations = target.directUserId?.let { userId ->
        userConversations.withDeletedLatestUserMessage(
            userId = userId,
            messageId = messageId,
            replacement = replacementLatestMessage.takeIf { deleteSelectedUserMessage },
        )
    } ?: userConversations
    return copy(
        rooms = nextRooms,
        ownedRooms = nextOwnedRooms,
        userConversations = nextUserConversations,
        selectedRoom = selectedRoom?.let { room ->
            if (deleteSelectedRoomMessage) {
                room.withDeletedLatestMessage(messageId, replacementLatestMessage)
            } else {
                room
            }
        },
        selectedUserConversation = selectedUserConversation?.let { conversation ->
            if (deleteSelectedUserMessage) {
                conversation.withDeletedLatestMessage(messageId, replacementLatestMessage)
            } else {
                conversation
            }
        },
        messages = nextMessages,
        messageSearchResults = messageSearchResults.filterNot { message -> message.id == messageId },
        pendingMessageDeleteIds = pendingMessageDeleteIds - messageId,
        specialCareToast = specialCareToast?.takeUnless { toast -> toast.messageId == messageId },
        specialCareJumpMessageId = specialCareJumpMessageId.takeUnless { it == messageId },
        unreadJumpMessageId = unreadJumpMessageId.takeUnless { it == messageId },
    )
}

private fun List<ChatRoom>.withDeletedLatestRoomMessage(
    roomId: String,
    messageId: String,
    replacement: ChatMessage?,
): List<ChatRoom> {
    val index = indexOfFirst { room -> room.id == roomId }
    if (index < 0) return this
    val updated = this[index].withDeletedLatestMessage(messageId, replacement)
    if (updated == this[index]) return this
    val next = toMutableList()
    next[index] = updated
    return next
}

private fun ChatRoom.withDeletedLatestMessage(
    messageId: String,
    replacement: ChatMessage?,
): ChatRoom {
    if (!messageId.matchesReadMarker(unreadMarker())) return this
    return replacement?.let(::withLatestMessageAt)
        ?: copy(latestMessageAtLabel = "", latestMessageMarker = "")
}

private fun List<ChatUserConversation>.markChatUserConversationRead(userId: String): List<ChatUserConversation> {
    if (userId.isBlank()) return this
    val index = indexOfFirst { conversation -> conversation.user.id == userId }
    if (index < 0 || this[index].unreadCount == 0) return this
    val next = toMutableList()
    next[index] = this[index].copy(unreadCount = 0)
    return next
}

private fun List<ChatUserConversation>.withDeletedLatestUserMessage(
    userId: String,
    messageId: String,
    replacement: ChatMessage?,
): List<ChatUserConversation> {
    val index = indexOfFirst { conversation -> conversation.user.id == userId }
    if (index < 0) return this
    val updated = this[index].withDeletedLatestMessage(messageId, replacement)
    if (updated == this[index]) return this
    val next = toMutableList()
    next[index] = updated
    return next
}

private fun ChatUserConversation.withDeletedLatestMessage(
    messageId: String,
    replacement: ChatMessage?,
): ChatUserConversation {
    if (latestMessage?.id != messageId) return this
    return copy(latestMessage = replacement)
}

private fun List<ChatUserConversation>.mergeUserUnreadCounts(
    previous: List<ChatUserConversation>,
    selectedUserId: String?,
    localUnreadCounts: Map<String, Int>,
    localReadMarkers: Map<String, String>,
    persistUnread: ((Map<String, Int>) -> Unit)?,
): List<ChatUserConversation> {
    val previousByUserId = previous.associateBy { it.user.id }
    var changedLocalCounts = false
    val nextLocalCounts = localUnreadCounts.toMutableMap()
    val merged = map { conversation ->
        val userId = conversation.user.id
        val latestMessage = conversation.latestMessage
        val latestMarker = latestMessage?.unreadMarker().orEmpty()
        val readMarker = localReadMarkers[userId].orEmpty().trim()
        if (userId == selectedUserId) {
            nextLocalCounts.remove(userId)?.let { changedLocalCounts = true }
            return@map conversation.copy(unreadCount = 0)
        }
        if (latestMarker.matchesReadMarker(readMarker)) {
            nextLocalCounts.remove(userId)?.let { changedLocalCounts = true }
            return@map conversation.copy(unreadCount = 0)
        }
        val previousConversation = previousByUserId[userId]
        val localCount = nextLocalCounts[userId].coercePositive()
        val remoteCount = conversation.unreadCount.coerceAtLeast(0)
        val previousCount = previousConversation?.unreadCount.coercePositive()
        val latestChanged = previousConversation != null &&
            latestMessage != null &&
            latestMessage.hasStableUnreadMarker() &&
            latestMarker != previousConversation.latestMessage?.unreadMarker().orEmpty() &&
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

private fun ChatRoom.hasStableUnreadMarker(): Boolean {
    return latestMessageMarker.isStableUnreadMarker()
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

private fun ChatMessage.hasStableUnreadMarker(): Boolean {
    return id.isStableUnreadMarker() || createdAt.isStableUnreadMarker()
}

private fun String?.withReadMarkerAlias(marker: String): String {
    val cleanMarker = marker.trim()
    if (cleanMarker.isEmpty()) return this?.trim().orEmpty()
    val markers = (this.readMarkerAliases() + cleanMarker)
        .distinct()
        .takeLast(MAX_READ_MARKER_ALIASES)
    return markers.joinToString(READ_MARKER_ALIAS_SEPARATOR)
}

private fun String.matchesReadMarker(readMarkerValue: String): Boolean {
    val cleanMarker = trim()
    return cleanMarker.isNotEmpty() && cleanMarker in readMarkerValue.readMarkerAliases()
}

private fun String?.readMarkerAliases(): List<String> {
    return orEmpty()
        .split(READ_MARKER_ALIAS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun String.isStableUnreadMarker(): Boolean {
    val clean = trim()
    return clean.isNotEmpty() && !unstableUnreadMarkerPattern.containsMatchIn(clean)
}

private const val READ_MARKER_ALIAS_SEPARATOR = "\n"
private const val MAX_READ_MARKER_ALIASES = 4
private val unstableUnreadMarkerPattern = Regex(
    pattern = "(刚刚|秒前|分钟前|小时前|天前|周前|月前|年前|just now|\\bago\\b)",
    options = setOf(RegexOption.IGNORE_CASE),
)

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

private fun List<ChatUserConversation>.withRealtimeUserConversationMessage(
    userId: String,
    message: ChatMessage,
    incrementUnread: Boolean,
): List<ChatUserConversation> {
    if (userId.isBlank()) return this
    val index = indexOfFirst { conversation -> conversation.user.id == userId }
    val conversation = if (index >= 0) {
        this[index]
    } else {
        ChatUserConversation(
            user = message.userForDirectConversation(userId),
            latestMessage = null,
            unreadCount = 0,
        )
    }
    val messageMarker = message.unreadMarker()
    val latestChanged = message.hasStableUnreadMarker() &&
        messageMarker.isNotBlank() &&
        messageMarker != conversation.latestMessage?.unreadMarker().orEmpty()
    val updated = conversation.copy(
        latestMessage = message,
        unreadCount = when {
            incrementUnread && latestChanged -> conversation.unreadCount.coerceAtLeast(0) + 1
            incrementUnread -> conversation.unreadCount.coerceAtLeast(0)
            else -> 0
        },
    )
    return listOf(updated) + filterIndexed { currentIndex, _ -> currentIndex != index }
}

private fun ChatMessage.userForDirectConversation(userId: String): User {
    return when {
        fromUser.id == userId -> fromUser
        toUser?.id == userId -> toUser
        else -> User(
            id = userId,
            displayName = userId,
            username = userId,
            avatarInitial = userId.trim().firstOrNull()?.toString()?.uppercase() ?: "?",
        )
    }
}

private fun List<ChatUserConversation>.mergeChatUserConversations(
    existing: List<ChatUserConversation>,
): List<ChatUserConversation> {
    if (isEmpty()) return existing
    val byUserId = LinkedHashMap<String, ChatUserConversation>(size + existing.size)
    for (conversation in this) {
        byUserId[conversation.stableChatUserConversationKey()] = conversation
    }
    for (conversation in existing) {
        byUserId.putIfAbsent(conversation.stableChatUserConversationKey(), conversation)
    }
    return byUserId.values.toList()
}

private fun ChatUserConversation.stableChatUserConversationKey(): String {
    return user.id.ifBlank { user.username.ifBlank { user.displayName } }
}

private fun Set<String>.toggleMembership(id: String): Set<String> {
    return if (id in this) this - id else this + id
}

private fun String.cleanChatRoomGroupName(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(24)
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

private const val SPECIAL_CARE_ROOM_SCAN_LIMIT = 256

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
    val stableMessages = withStableChatMessageIds()
    val stableMessage = message.withStableChatMessageId()
    for (index in stableMessages.indices) {
        if (stableMessages[index].id == stableMessage.id) {
            if (stableMessages[index] == stableMessage) return stableMessages
            val nextMessages = stableMessages.toMutableList()
            nextMessages[index] = stableMessage
            return nextMessages.ensureChronologicalMessages()
        }
    }
    if (stableMessages.isEmpty()) return listOf(stableMessage)
    val last = stableMessages.last()
    if (stableMessage.isSameOrAfter(last)) return stableMessages + stableMessage
    val first = stableMessages.first()
    if (!stableMessage.isSameOrAfter(first)) return listOf(stableMessage) + stableMessages

    val nextMessages = ArrayList<ChatMessage>(stableMessages.size + 1)
    var inserted = false
    for (existing in stableMessages) {
        if (!inserted && existing.isSameOrAfter(stableMessage)) {
            nextMessages += stableMessage
            inserted = true
        }
        nextMessages += existing
    }
    if (!inserted) nextMessages += stableMessage
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

    // 1. 检查 @username@host 格式（与服务端兼容的标准提及格式）
    var index = indexOf('@')
    while (index >= 0 && index < length - 1) {
        val parsed = parseMentionAt(index)
        if (parsed != null && parsed.username.equals(username, ignoreCase = true)) {
            if (host == null || parsed.host == null || parsed.host.equals(host, ignoreCase = true)) return true
        }
        index = indexOf('@', startIndex = index + 1)
    }

    // 2. 检查 @displayName 格式（聊天室 @功能使用的昵称格式）
    val displayName = user.displayName.trim().takeIf { it.isNotEmpty() } ?: return false
    return containsDisplayNameMention(displayName)
}

/**
 * 检查文本中是否包含 @displayName 格式的提及
 * 要求 @ 符号位于行首或前面是空白字符，且 displayName 后面是空白字符或文本末尾
 */
private fun String.containsDisplayNameMention(displayName: String): Boolean {
    val token = "@$displayName"
    var searchFrom = 0
    while (true) {
        val found = indexOf(token, searchFrom, ignoreCase = true)
        if (found < 0) return false
        // 检查 @ 前面是否是行首或空白字符
        val precedingOk = found == 0 || this[found - 1].isWhitespace()
        // 检查 displayName 后面是否是空白字符或文本末尾
        val afterIndex = found + token.length
        val followingOk = afterIndex >= length || this[afterIndex].isWhitespace()
        if (precedingOk && followingOk) return true
        searchFrom = found + 1
    }
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
    val stableMessages = withStableChatMessageIds()
    val byId = LinkedHashMap<String, ChatMessage>(stableMessages.size)
    for (message in stableMessages) {
        if (!byId.containsKey(message.id)) {
            byId[message.id] = message
        }
    }
    return byId.values.sortedByChatMessageOrder()
}

internal fun List<ChatMessage>.mergeReplacingChronologicalMessages(incoming: List<ChatMessage>): List<ChatMessage> {
    val stableCurrent = withStableChatMessageIds()
    val stableIncoming = incoming.withStableChatMessageIds()
    if (stableIncoming.isEmpty()) return stableCurrent.ensureChronologicalMessages()
    val byId = LinkedHashMap<String, ChatMessage>(stableCurrent.size + stableIncoming.size)
    for (message in stableCurrent) {
        byId[message.id] = message
    }
    for (message in stableIncoming) {
        byId[message.id] = message
    }
    return byId.values.sortedByChatMessageOrder()
}

internal fun List<ChatMessage>.ensureChronologicalMessages(): List<ChatMessage> {
    val stableMessages = withStableChatMessageIds()
    if (stableMessages.size <= 1) return stableMessages
    val seen = HashSet<String>(stableMessages.size)
    var previousKey: String? = null
    var previousId: String? = null
    for (message in stableMessages) {
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
    return stableMessages
}

private fun List<ChatMessage>.withStableChatMessageIds(): List<ChatMessage> {
    if (isEmpty()) return this
    val seenIds = HashMap<String, Int>(size)
    var changed = false
    val stableMessages = mapIndexed { index, message ->
        val hasServerId = message.id.isNotBlank()
        val baseId = if (hasServerId) message.id else message.chatMessageFallbackId(index)
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

private fun ChatMessage.withStableChatMessageId(): ChatMessage {
    return if (id.isNotBlank()) this else copy(id = chatMessageFallbackId(0))
}

private fun ChatMessage.chatMessageFallbackId(index: Int): String {
    val seed = listOf(roomId, toUserId.orEmpty(), fromUser.id, createdAt, createdAtLabel, text, file?.id.orEmpty(), index.toString())
        .joinToString(separator = "\u0000")
    return "local-chat-${seed.stableStateChatHash()}"
}

private fun String.stableStateChatHash(): String {
    var hash = 1125899906842597L
    for (char in this) {
        hash = 31L * hash + char.code
    }
    return hash.toULong().toString(36)
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
private const val CHAT_USER_SEARCH_TIMEOUT_MS = 12_000L
private const val CHAT_USER_SEARCH_RESULT_LIMIT = 80

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

/**
 * 判断流式事件是否应该触发重试
 */
private fun ChatStreamingEvent.shouldTriggerStreamingRetry(): Boolean {
    return when (this) {
        is ChatStreamingEvent.Error, is ChatStreamingEvent.Closed -> true
        else -> false
    }
}

/**
 * 带重试机制的 Flow 收集器
 * 用于 WebSocket 连接的自动重连
 *
 * @param flowProvider 提供需要收集的 Flow
 * @param onEvent 事件处理回调，返回是否需要重试
 */
private suspend fun collectWithRetry(
    flowProvider: () -> Flow<ChatStreamingEvent>,
    onEvent: suspend (ChatStreamingEvent) -> Boolean,
) {
    var retryCount = 0
    var retryDelay = STREAMING_INITIAL_RETRY_DELAY_MS

    while (retryCount < STREAMING_MAX_RETRIES) {
        var shouldRetry = false

        flowProvider().collect { event ->
            val needsRetry = onEvent(event)
            if (needsRetry) {
                shouldRetry = true
            }
            // 连接成功后重置重试计数
            if (event is ChatStreamingEvent.Connected) {
                retryCount = 0
                retryDelay = STREAMING_INITIAL_RETRY_DELAY_MS
            }
        }

        // Flow 结束后，检查是否需要重试
        if (shouldRetry && retryCount < STREAMING_MAX_RETRIES - 1) {
            retryCount++
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(STREAMING_MAX_RETRY_DELAY_MS)
        } else {
            break
        }
    }
}
