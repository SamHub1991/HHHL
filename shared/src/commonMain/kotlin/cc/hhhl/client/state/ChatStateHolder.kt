package cc.hhhl.client.state

import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.apiDateSortKey
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRoomMemberRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val selectedRoom: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val members: List<ChatRoomMember> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isLoadingMembers: Boolean = false,
    val isLoadingMoreMembers: Boolean = false,
    val isSendingMessage: Boolean = false,
    val endReached: Boolean = false,
    val messagesEndReached: Boolean = false,
    val membersEndReached: Boolean = false,
    val showingMembers: Boolean = false,
    val messageDraft: String = "",
    val quotedMessage: ChatMessageQuote? = null,
    val attachedFile: DriveFile? = null,
    val attachments: List<ChatComposerAttachment> = emptyList(),
    val isUploadingMedia: Boolean = false,
    val reactionOptions: List<String> = listOf("❤️", "👍", "🎉", "😆", "😮", "😢"),
    val pendingMessageReactionIds: Set<String> = emptySet(),
    val specialCareUserIds: Set<String> = emptySet(),
    val specialCareToast: SpecialCareChatToast? = null,
    val specialCareJumpMessageId: String? = null,
    val selectedRoomUnreadCount: Int = 0,
    val unreadJumpMessageId: String? = null,
    val chatAvailable: Boolean = false,
    val isStreamingMessages: Boolean = false,
    val streamingErrorMessage: String? = null,
    val errorMessage: String? = null,
    val messageErrorMessage: String? = null,
    val memberErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
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

data class SpecialCareChatToast(
    val messageId: String,
    val roomId: String,
    val userId: String,
    val displayName: String,
    val previewText: String,
)

class ChatStateHolder(
    private val repository: ChatRepository,
    private val driveFileRepository: DriveFileRepository? = null,
    private val streamingRepository: ChatStreamingRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState
    private var pendingMediaUpload: DriveFileUpload? = null
    private var streamingJob: Job? = null

    fun updateSpecialCareUsers(userIds: Set<String>) {
        val cleanUserIds = userIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        mutableState.update {
            it.copy(
                specialCareUserIds = cleanUserIds,
                specialCareToast = it.specialCareToast?.takeIf { toast -> toast.userId in cleanUserIds },
            )
        }
    }

    fun dismissSpecialCareToast() {
        mutableState.update { it.copy(specialCareToast = null) }
    }

    fun openSpecialCareToast() {
        val toast = state.value.specialCareToast ?: return
        mutableState.update {
            it.copy(
                specialCareToast = null,
                specialCareJumpMessageId = toast.messageId,
                showingMembers = false,
            )
        }
    }

    fun consumeSpecialCareJump() {
        mutableState.update { it.copy(specialCareJumpMessageId = null) }
    }

    fun consumeUnreadJump() {
        mutableState.update { it.copy(unreadJumpMessageId = null) }
    }

    fun updateAvailability(chatAvailable: Boolean) {
        if (!chatAvailable) {
            stopMessageStreaming()
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
            applyResult(repository.refresh(), loadingMore = false)
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
            it.copy(
                rooms = it.rooms.markChatRoomRead(room.id),
                selectedRoom = room.copy(unreadCount = 0),
                messages = emptyList(),
                members = emptyList(),
                selectedRoomUnreadCount = room.unreadCount.coerceAtLeast(0),
                unreadJumpMessageId = null,
                messagesEndReached = false,
                membersEndReached = false,
                showingMembers = false,
                messageDraft = "",
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
        startMessageStreaming(room.id)
        refreshMessages()
    }

    fun closeRoom() {
        stopMessageStreaming()
        mutableState.update {
            it.copy(
                selectedRoom = null,
                messages = emptyList(),
                members = emptyList(),
                selectedRoomUnreadCount = 0,
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
                applyStreamingEvent(cleanRoomId, event)
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

    fun refreshMessages() {
        val room = state.value.selectedRoom ?: return
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
            applyMessageResult(
                result = repository.refreshMessages(room.id),
                loadingMore = false,
            )
        }
    }

    fun loadOlderMessages() {
        val current = state.value
        val room = current.selectedRoom ?: return
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
            applyMessageResult(
                result = repository.loadMoreMessages(room.id, current.messages),
                loadingMore = true,
            )
        }
    }

    fun showMessages() {
        mutableState.update {
            it.copy(showingMembers = false, memberErrorMessage = null, requiresRelogin = false)
        }
    }

    fun showMembers() {
        val room = state.value.selectedRoom ?: return
        if (state.value.isLoadingMembers) return

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
            applyMemberResult(
                result = repository.refreshMembers(room.id),
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
                result = repository.loadMoreMembers(room.id, current.members),
                loadingMore = true,
            )
        }
    }

    fun updateMessageDraft(text: String) {
        mutableState.update {
            it.copy(messageDraft = text, messageErrorMessage = null, requiresRelogin = false)
        }
    }

    fun quoteMessage(messageId: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        mutableState.update {
            it.copy(
                quotedMessage = message.toChatMessageQuote(),
                messageErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun cancelQuotedMessage() {
        mutableState.update {
            it.copy(quotedMessage = null, messageErrorMessage = null, requiresRelogin = false)
        }
    }

    fun uploadMedia(upload: DriveFileUpload) {
        if (state.value.isUploadingMedia) {
            pendingMediaUpload = upload
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
                    it.copy(
                        attachedFile = result.file,
                        attachments = listOf(result.file.toChatComposerAttachment()),
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
        pendingMediaUpload = null
        mutableState.update {
            it.copy(
                attachedFile = null,
                attachments = emptyList(),
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
        val nextUpload = pendingMediaUpload ?: return
        pendingMediaUpload = null
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

    fun sendMessage() {
        val current = state.value
        val room = current.selectedRoom ?: return
        val text = chatMessageSendText(
            draft = current.messageDraft,
            quote = current.quotedMessage,
        )
        val fileId = current.sendableChatAttachmentFileId()
        if (text.isBlank() && fileId == null) {
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
            applyMessageResult(
                result = repository.sendMessage(room.id, text, fileId),
                loadingMore = false,
            )
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
            is ChatRepositoryResult.Success -> mutableState.update {
                it.copy(
                    rooms = result.rooms,
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

    private fun applyMessageResult(
        result: ChatMessageRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatMessageRepositoryResult.Success -> mutableState.update {
                val nextMessages = result.messages.dedupeChronologicalMessages()
                it.copy(
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
                it.copy(
                    messages = (it.messages + result.message).dedupeChronologicalMessages(),
                    messageDraft = "",
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
                it.copy(requiresRelogin = false)
            }
            ChatMessageRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingMessages = false,
                    isLoadingOlderMessages = false,
                    isSendingMessage = false,
                    messageErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatMessageRepositoryResult.Error -> mutableState.update {
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

    private fun applyMemberResult(
        result: ChatRoomMemberRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is ChatRoomMemberRepositoryResult.Success -> mutableState.update {
                it.copy(
                    members = result.members,
                    isLoadingMembers = false,
                    isLoadingMoreMembers = false,
                    membersEndReached = result.endReached,
                    memberErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ChatRoomMemberRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingMembers = false,
                    isLoadingMoreMembers = false,
                    memberErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatRoomMemberRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingMembers = if (loadingMore) it.isLoadingMembers else false,
                    isLoadingMoreMembers = false,
                    memberErrorMessage = result.message,
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
                    messages = it.messages.map { message ->
                        if (message.id == messageId) {
                            message.withReactionUpdated(reaction, react)
                        } else {
                            message
                        }
                    },
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
            -> mutableState.update {
                it.copy(
                    pendingMessageReactionIds = it.pendingMessageReactionIds - messageId,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyStreamingEvent(
        roomId: String,
        event: ChatStreamingEvent,
    ) {
        when (event) {
            ChatStreamingEvent.Connecting -> mutableState.update {
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Connected -> mutableState.update {
                it.copy(isStreamingMessages = true, streamingErrorMessage = null)
            }
            ChatStreamingEvent.Unauthorized -> mutableState.update {
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ChatStreamingEvent.MessageReceived -> mutableState.update {
                if (it.selectedRoom?.id != roomId || event.message.roomId != roomId) {
                    it
                } else {
                    val nextMessages = it.messages.withChronologicalMessage(event.message)
                    if (nextMessages === it.messages && it.streamingErrorMessage == null) {
                        return@update it
                    }
                    val toast = event.message.toSpecialCareToastIfNeeded(it.specialCareUserIds)
                    it.copy(
                        messages = nextMessages,
                        specialCareToast = it.specialCareToast.withStableSpecialCareToast(toast),
                        streamingErrorMessage = null,
                    )
                }
            }
            is ChatStreamingEvent.Error -> mutableState.update {
                it.copy(
                    isStreamingMessages = false,
                    streamingErrorMessage = userFacingStreamingErrorMessage(event.message),
                )
            }
            ChatStreamingEvent.Closed -> mutableState.update {
                it.copy(isStreamingMessages = false)
            }
        }
    }
}

private fun List<ChatRoom>.markChatRoomRead(roomId: String): List<ChatRoom> {
    if (roomId.isBlank()) return this
    return map { room ->
        if (room.id == roomId) {
            room.copy(unreadCount = 0)
        } else {
            room
        }
    }
}

private fun List<ChatMessage>.firstUnreadMessageId(unreadCount: Int): String? {
    val count = unreadCount.coerceAtLeast(0)
    if (count == 0 || isEmpty()) return null
    return takeLast(count.coerceAtMost(size)).firstOrNull()?.id
}

private fun ChatMessage.toSpecialCareToastIfNeeded(
    specialCareUserIds: Set<String>,
): SpecialCareChatToast? {
    if (fromUser.id !in specialCareUserIds) return null
    return SpecialCareChatToast(
        messageId = id,
        roomId = roomId,
        userId = fromUser.id,
        displayName = fromUser.displayName.ifBlank { fromUser.username },
        previewText = chatMessageQuotePreviewText(this),
    )
}

internal fun List<ChatMessage>.withChronologicalMessage(message: ChatMessage): List<ChatMessage> {
    if (any { it.id == message.id }) return this
    return (this + message).dedupeChronologicalMessages()
}

internal fun SpecialCareChatToast?.withStableSpecialCareToast(
    incoming: SpecialCareChatToast?,
): SpecialCareChatToast? {
    if (incoming == null) return this
    if (this?.messageId == incoming.messageId) return this
    return incoming
}

internal fun userFacingStreamingErrorMessage(message: String): String {
    val cleanMessage = message.trim()
    if (cleanMessage.isBlank()) return "实时连接已断开，请稍后重试"
    if (cleanMessage.isUnauthorizedStreamingError()) return "登录已失效，请重新登录"
    return when {
        cleanMessage.contains("timeout", ignoreCase = true) ||
            cleanMessage.contains("timed out", ignoreCase = true) -> "实时连接超时，请稍后重试"
        cleanMessage.contains("network", ignoreCase = true) ||
            cleanMessage.contains("connection", ignoreCase = true) ||
            cleanMessage.contains("connect", ignoreCase = true) -> "实时连接已断开，请稍后重试"
        else -> "实时连接暂时不可用，请稍后重试"
    }
}

private fun String.isUnauthorizedStreamingError(): Boolean {
    return contains("401") ||
        contains("Unauthorized", ignoreCase = true) ||
        contains("Invalid token", ignoreCase = true) ||
        contains("authentication", ignoreCase = true)
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
    return attachments.firstOrNull()?.file?.id
        ?: attachedFile?.id
}

fun chatMessageSendText(
    draft: String,
    quote: ChatMessageQuote?,
): String {
    val cleanDraft = draft.trim()
    if (quote == null) return cleanDraft
    val quoteLine = "> ${quote.authorName}: ${quote.previewText}".trim()
    return if (cleanDraft.isBlank()) {
        quoteLine
    } else {
        "$quoteLine\n\n$cleanDraft"
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
    val body = message.text
        .lineSequence()
        .map { it.trim().trimStart('>') }
        .firstOrNull { it.isNotBlank() }
        ?.take(80)
    return body
        ?: message.file?.name?.takeIf { it.isNotBlank() }
        ?: "附件"
}

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
    return distinctBy { it.id }.sortedWith(
        compareBy<ChatMessage> { apiDateSortKey(it.createdAt, it.createdAtLabel) }
            .thenBy { it.id },
    )
}
