package cc.hhhl.client.state

import cc.hhhl.client.api.ChatApi
import cc.hhhl.client.api.ChatMessageCreateResult
import cc.hhhl.client.api.ChatMessageLoadResult
import cc.hhhl.client.api.ChatMessageReactionResult
import cc.hhhl.client.api.ChatRoomMemberLoadResult
import cc.hhhl.client.api.ChatRoomLoadResult
import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRoomMemberRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStateHolderTest {
    @Test
    fun updateAvailabilityDisablesChatAndClearsRooms() {
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(listOf(sampleRoom()))),
            scope = TestScope(),
        )

        holder.updateAvailability(chatAvailable = false)

        assertFalse(holder.state.value.chatAvailable)
        assertEquals(emptyList(), holder.state.value.rooms)
    }

    @Test
    fun updateAvailabilityClearsReloginWhenChatBecomesAvailableAgain() = runTest {
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.updateAvailability(chatAvailable = true)

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(null, holder.state.value.errorMessage)
        assertEquals(null, holder.state.value.messageErrorMessage)
        assertEquals(null, holder.state.value.memberErrorMessage)
    }

    @Test
    fun refreshStoresLoadedRooms() = runTest {
        val room = sampleRoom()
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(listOf(room))),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(room), holder.state.value.rooms)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun refreshDoesNothingWhenChatUnavailable() = runTest {
        val calls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(sampleRoom())),
                onRefresh = { calls.add("refresh") },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = false)
        holder.refresh()
        advanceUntilIdle()

        assertEquals(emptyList(), calls)
        assertEquals("实例未启用聊天", holder.state.value.errorMessage)
    }

    @Test
    fun unauthorizedRefreshMarksRelogin() = runTest {
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun permissionErrorRefreshDoesNotRequestRelogin() = runTest {
        val holder = ChatStateHolder(
            repository = fakeRepository(
                ChatRepositoryResult.Error("当前登录缺少此功能权限，请重新登录一次后再试"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("当前登录缺少此功能权限，请重新登录一次后再试", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorized() = runTest {
        val room = sampleRoom()
        var refreshResult: ChatRepositoryResult = ChatRepositoryResult.Unauthorized
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = { refreshResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshResult = ChatRepositoryResult.Success(listOf(room))
        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(room), holder.state.value.rooms)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun selectRoomLoadsMessages() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        assertEquals(room, holder.state.value.selectedRoom)
        assertTrue(holder.state.value.isLoadingMessages)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMessages)
        assertEquals(listOf(message), holder.state.value.messages)
    }

    @Test
    fun sendMessageCreatesMessageAndClearsDraft() = runTest {
        val room = sampleRoom()
        val created = sampleMessage("message-created", text = "你好")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        holder.updateMessageDraft("你好")
        holder.sendMessage()
        assertTrue(holder.state.value.isSendingMessage)
        advanceUntilIdle()

        assertFalse(holder.state.value.isSendingMessage)
        assertEquals("", holder.state.value.messageDraft)
        assertEquals(listOf(created), holder.state.value.messages)
    }

    @Test
    fun quoteMessageStoresComposerQuotePreview() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", text = "第一行\n第二行")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.quoteMessage("message-1")

        assertEquals("message-1", holder.state.value.quotedMessage?.messageId)
        assertEquals("Alice", holder.state.value.quotedMessage?.authorName)
        assertEquals("第一行", holder.state.value.quotedMessage?.previewText)
    }

    @Test
    fun sendQuotedMessagePrefixesQuoteAndClearsComposerQuote() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", text = "原消息")
        val created = sampleMessage("message-created", text = "回复")
        val calls = mutableListOf<SendCall>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
                onSend = { roomId, text, fileId -> calls.add(SendCall(roomId, text, fileId)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.quoteMessage("message-1")
        holder.updateMessageDraft("收到")
        holder.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf(SendCall("room-1", "> Alice: 原消息\n\n收到", null)), calls)
        assertEquals(null, holder.state.value.quotedMessage)
    }

    @Test
    fun cancelQuotedMessageClearsComposerQuote() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", text = "原消息")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.quoteMessage("message-1")
        holder.cancelQuotedMessage()

        assertEquals(null, holder.state.value.quotedMessage)
    }

    @Test
    fun uploadMediaStoresAttachedFileForChatMessage() = runTest {
        val file = sampleDriveFile()
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(emptyList())),
            driveFileRepository = fakeDriveRepository(DriveFileRepositoryResult.Success(file)),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        assertTrue(holder.state.value.isUploadingMedia)
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(file, holder.state.value.attachedFile)
        assertEquals(listOf(file.toChatComposerAttachment()), holder.state.value.attachments)
        assertEquals(ChatComposerAttachmentKind.Photo, holder.state.value.attachments.single().kind)
    }

    @Test
    fun uploadMediaKeepsLatestQueuedAttachmentForChatMessage() = runTest {
        val files = listOf(
            sampleDriveFile().copy(id = "file-1", name = "first.png"),
            sampleDriveFile().copy(id = "file-2", name = "second.png"),
        )
        var index = 0
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(emptyList())),
            driveFileRepository = fakeDriveRepository {
                DriveFileRepositoryResult.Success(files[index++])
            },
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        holder.uploadMedia(sampleUpload().copy(fileName = "second.png"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(files[1], holder.state.value.attachedFile)
        assertEquals(listOf(files[1].toChatComposerAttachment()), holder.state.value.attachments)
    }

    @Test
    fun sendMessageWithAttachedFilePassesFileIdAndClearsAttachment() = runTest {
        val room = sampleRoom()
        val file = sampleDriveFile()
        val created = sampleMessage("message-created", text = "")
        val calls = mutableListOf<SendCall>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
                onSend = { roomId, text, fileId -> calls.add(SendCall(roomId, text, fileId)) },
            ),
            driveFileRepository = fakeDriveRepository(DriveFileRepositoryResult.Success(file)),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.uploadMedia(sampleUpload())
        advanceUntilIdle()
        holder.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf(SendCall("room-1", "", "file-1")), calls)
        assertEquals(null, holder.state.value.attachedFile)
        assertEquals(emptyList(), holder.state.value.attachments)
        assertEquals(listOf(created), holder.state.value.messages)
    }

    @Test
    fun chatComposerAttachmentModelClassifiesNonImageFiles() {
        val file = sampleDriveFile().copy(
            id = "file-pdf",
            name = "spec.pdf",
            type = "application/pdf",
        )

        val attachment = file.toChatComposerAttachment()

        assertEquals("file-pdf", attachment.id)
        assertEquals(ChatComposerAttachmentKind.File, attachment.kind)
        assertEquals(file, attachment.file)
    }

    @Test
    fun reactToMessageUpdatesReactionCount() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", reactions = emptyList())
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                reactResult = ChatMessageRepositoryResult.ReactionUpdated,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.reactToMessage("message-1", "❤️")
        assertTrue(holder.state.value.pendingMessageReactionIds.contains("message-1"))
        advanceUntilIdle()

        val updated = holder.state.value.messages.single()
        assertFalse(holder.state.value.pendingMessageReactionIds.contains("message-1"))
        assertEquals(1, updated.reactionCount)
        assertEquals(listOf(ChatMessageReaction("❤️", 1)), updated.reactions)
    }

    @Test
    fun unreactToMessageUpdatesReactionCount() = runTest {
        val room = sampleRoom()
        val message = sampleMessage(
            id = "message-1",
            reactions = listOf(ChatMessageReaction("❤️", 1)),
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                reactResult = ChatMessageRepositoryResult.ReactionUpdated,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.unreactToMessage("message-1", "❤️")
        advanceUntilIdle()

        val updated = holder.state.value.messages.single()
        assertEquals(0, updated.reactionCount)
        assertEquals(emptyList(), updated.reactions)
    }

    @Test
    fun showMembersLoadsRoomMembers() = runTest {
        val room = sampleRoom()
        val member = sampleMember("membership-member-1")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMembersResult = ChatRoomMemberRepositoryResult.Success(listOf(member)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.showMembers()
        assertTrue(holder.state.value.showingMembers)
        assertTrue(holder.state.value.isLoadingMembers)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMembers)
        assertEquals(listOf(member), holder.state.value.members)
        assertEquals(null, holder.state.value.memberErrorMessage)
    }

    @Test
    fun loadMoreMembersAppendsAndMarksEndReached() = runTest {
        val room = sampleRoom()
        val first = sampleMember("membership-member-1")
        val second = sampleMember("membership-member-2", userId = "user-2")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMembersResult = ChatRoomMemberRepositoryResult.Success(listOf(first)),
                loadMoreMembersResult = ChatRoomMemberRepositoryResult.Success(
                    members = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.showMembers()
        advanceUntilIdle()
        holder.loadMoreMembers()
        assertTrue(holder.state.value.isLoadingMoreMembers)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMoreMembers)
        assertTrue(holder.state.value.membersEndReached)
        assertEquals(listOf(first, second), holder.state.value.members)
    }

    @Test
    fun unauthorizedMemberLoadMarksRelogin() = runTest {
        val room = sampleRoom()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMembersResult = ChatRoomMemberRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.showMembers()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.memberErrorMessage)
    }

    @Test
    fun successfulMemberReloadClearsReloginAfterUnauthorized() = runTest {
        val room = sampleRoom()
        val member = sampleMember("membership-member-1")
        var refreshMembersResult: ChatRoomMemberRepositoryResult =
            ChatRoomMemberRepositoryResult.Unauthorized
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMembersResultProvider = { refreshMembersResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.showMembers()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshMembersResult = ChatRoomMemberRepositoryResult.Success(listOf(member))
        holder.showMembers()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(member), holder.state.value.members)
        assertEquals(null, holder.state.value.memberErrorMessage)
    }

    @Test
    fun streamingMessageAppendsToSelectedRoomWithoutBreakingLoadedMessages() = runTest {
        val room = sampleRoom()
        val existing = sampleMessage("message-1", text = "旧消息")
        val streamed = sampleMessage("message-2", text = "实时消息")
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(existing)),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.Connected)
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()

        assertTrue(holder.state.value.isStreamingMessages)
        assertEquals(listOf(existing, streamed), holder.state.value.messages)
        assertEquals(null, holder.state.value.streamingErrorMessage)
    }

    @Test
    fun messagesStayOldestToNewestUsingApiTimestamp() = runTest {
        val room = sampleRoom()
        val newer = sampleMessage(
            id = "message-newer",
            createdAt = "2026-05-25T02:00:00.000Z",
            createdAtLabel = "刚刚",
        )
        val older = sampleMessage(
            id = "message-older",
            createdAt = "2026-05-25T01:00:00.000Z",
            createdAtLabel = "1 小时前",
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(newer, older)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()

        assertEquals(listOf("message-older", "message-newer"), holder.state.value.messages.map { it.id })
    }

    @Test
    fun streamingMessageIsDedupedById() = runTest {
        val room = sampleRoom()
        val existing = sampleMessage("message-1", text = "旧消息")
        val duplicate = sampleMessage("message-1", text = "实时重复")
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(existing)),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(duplicate))
        advanceUntilIdle()

        assertEquals(listOf(existing), holder.state.value.messages)
    }

    @Test
    fun withChronologicalMessageReusesListForDuplicateAndSortsNewMessages() {
        val older = sampleMessage(
            id = "message-older",
            createdAt = "2026-05-25T01:00:00.000Z",
        )
        val newer = sampleMessage(
            id = "message-newer",
            createdAt = "2026-05-25T03:00:00.000Z",
        )
        val current = listOf(newer)

        val unchanged = current.withChronologicalMessage(newer.copy(text = "duplicate"))
        val inserted = current.withChronologicalMessage(older)

        assertSame(current, unchanged)
        assertEquals(listOf("message-older", "message-newer"), inserted.map { it.id })
    }

    @Test
    fun userFacingStreamingErrorMessageHidesTransportDetails() {
        assertEquals("登录已失效，请重新登录", userFacingStreamingErrorMessage("HTTP 401 Unauthorized"))
        assertEquals("实时连接超时，请稍后重试", userFacingStreamingErrorMessage("Socket timeout"))
        assertEquals("实时连接已断开，请稍后重试", userFacingStreamingErrorMessage("connection reset by peer"))
        assertEquals("实时连接暂时不可用，请稍后重试", userFacingStreamingErrorMessage("serializer failed at frame 12"))
    }

    @Test
    fun stableSpecialCareToastKeepsExistingToastForDuplicateMessage() {
        val current = SpecialCareChatToast(
            messageId = "message-1",
            roomId = "room-1",
            userId = "user-1",
            displayName = "Alice",
            previewText = "first",
        )
        val duplicate = current.copy(previewText = "duplicate")
        val next = current.copy(messageId = "message-2", previewText = "next")

        assertSame(current, current.withStableSpecialCareToast(duplicate))
        assertEquals(next, current.withStableSpecialCareToast(next))
        assertSame(current, current.withStableSpecialCareToast(null))
    }

    @Test
    fun closeRoomStopsMessageStreamingState() = runTest {
        val room = sampleRoom()
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.Connected)
        advanceUntilIdle()
        assertTrue(holder.state.value.isStreamingMessages)

        holder.closeRoom()

        assertFalse(holder.state.value.isStreamingMessages)
        assertEquals(null, holder.state.value.selectedRoom)
    }

    @Test
    fun streamingUnauthorizedRequestsReloginWithoutClearingRoom() = runTest {
        val room = sampleRoom()
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.Unauthorized)
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertFalse(holder.state.value.isStreamingMessages)
        assertEquals("登录已失效，请重新登录", holder.state.value.streamingErrorMessage)
        assertEquals(room, holder.state.value.selectedRoom)
    }

    private fun fakeRepository(
        result: ChatRepositoryResult,
        loadMoreResult: ChatRepositoryResult = result,
        refreshMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        loadMoreMessagesResult: ChatMessageRepositoryResult = refreshMessagesResult,
        sendMessageResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Created(sampleMessage("created")),
        reactResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.ReactionUpdated,
        refreshMembersResult: ChatRoomMemberRepositoryResult = ChatRoomMemberRepositoryResult.Success(emptyList()),
        loadMoreMembersResult: ChatRoomMemberRepositoryResult = refreshMembersResult,
        onRefresh: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onSend: (String, String, String?) -> Unit = { _, _, _ -> },
        refreshResultProvider: (() -> ChatRepositoryResult)? = null,
        refreshMembersResultProvider: (() -> ChatRoomMemberRepositoryResult)? = null,
    ): ChatRepository {
        return object : ChatRepository(
            tokenProvider = { "token-123" },
            api = object : ChatApi {
                override suspend fun loadJoiningRooms(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomLoadResult = ChatRoomLoadResult.Success(emptyList())

                override suspend fun loadRoomMessages(
                    token: String,
                    roomId: String,
                    limit: Int,
                    untilId: String?,
                ): ChatMessageLoadResult = ChatMessageLoadResult.Success(emptyList())

                override suspend fun createRoomMessage(
                    token: String,
                    roomId: String,
                    text: String,
                    fileId: String?,
                ): ChatMessageCreateResult = ChatMessageCreateResult.Success(sampleMessage("created"))

                override suspend fun reactToMessage(
                    token: String,
                    messageId: String,
                    reaction: String,
                ): ChatMessageReactionResult = ChatMessageReactionResult.Success

                override suspend fun unreactToMessage(
                    token: String,
                    messageId: String,
                    reaction: String,
                ): ChatMessageReactionResult = ChatMessageReactionResult.Success

                override suspend fun loadRoomMembers(
                    token: String,
                    roomId: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomMemberLoadResult = ChatRoomMemberLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(): ChatRepositoryResult {
                onRefresh()
                return refreshResultProvider?.invoke() ?: result
            }

            override suspend fun loadMore(currentRooms: List<ChatRoom>): ChatRepositoryResult {
                onLoadMore()
                return loadMoreResult
            }

            override suspend fun refreshMessages(roomId: String): ChatMessageRepositoryResult {
                return refreshMessagesResult
            }

            override suspend fun loadMoreMessages(
                roomId: String,
                currentMessages: List<ChatMessage>,
            ): ChatMessageRepositoryResult {
                return loadMoreMessagesResult
            }

            override suspend fun sendMessage(
                roomId: String,
                text: String,
                fileId: String?,
            ): ChatMessageRepositoryResult {
                onSend(roomId, text, fileId)
                return sendMessageResult
            }

            override suspend fun reactToMessage(
                messageId: String,
                reaction: String,
            ): ChatMessageRepositoryResult {
                return reactResult
            }

            override suspend fun unreactToMessage(
                messageId: String,
                reaction: String,
            ): ChatMessageRepositoryResult {
                return reactResult
            }

            override suspend fun refreshMembers(roomId: String): ChatRoomMemberRepositoryResult {
                return refreshMembersResultProvider?.invoke() ?: refreshMembersResult
            }

            override suspend fun loadMoreMembers(
                roomId: String,
                currentMembers: List<ChatRoomMember>,
            ): ChatRoomMemberRepositoryResult {
                return loadMoreMembersResult
            }
        }
    }

    private fun fakeStreamingRepository(
        flow: Flow<ChatStreamingEvent>,
    ): ChatStreamingRepository {
        return object : ChatStreamingRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ChatStreamingApi {
                override fun streamRoomMessages(
                    token: String,
                    roomId: String,
                ): Flow<ChatStreamingEvent> = flow
            },
        ) {
            override fun streamRoomMessages(roomId: String): Flow<ChatStreamingEvent> = flow
        }
    }

    private fun fakeDriveRepository(result: DriveFileRepositoryResult): DriveFileRepository {
        return fakeDriveRepository { result }
    }

    private fun fakeDriveRepository(resultProvider: () -> DriveFileRepositoryResult): DriveFileRepository {
        return object : DriveFileRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.DriveFileApi {
                override suspend fun loadFiles(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    sort: cc.hhhl.client.api.DriveFileSort,
                    searchQuery: String,
                    showAll: Boolean,
                ): cc.hhhl.client.api.DriveFileListResult {
                    return cc.hhhl.client.api.DriveFileListResult.Success(emptyList())
                }

                override suspend fun loadFolders(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    searchQuery: String,
                ): cc.hhhl.client.api.DriveFolderListResult {
                    return cc.hhhl.client.api.DriveFolderListResult.Success(emptyList())
                }

                override suspend fun uploadFile(
                    token: String,
                    upload: DriveFileUpload,
                ): cc.hhhl.client.api.DriveFileUploadResult {
                    return cc.hhhl.client.api.DriveFileUploadResult.Unauthorized
                }

                override suspend fun updateFile(
                    token: String,
                    fileId: String,
                    name: String?,
                    folderId: String?,
                    comment: String?,
                    isSensitive: Boolean?,
                ): cc.hhhl.client.api.DriveFileMutationResult {
                    return cc.hhhl.client.api.DriveFileMutationResult.Success(sampleDriveFile())
                }

                override suspend fun deleteFile(
                    token: String,
                    fileId: String,
                ): cc.hhhl.client.api.DriveFileMutationResult {
                    return cc.hhhl.client.api.DriveFileMutationResult.Deleted
                }

                override suspend fun createFolder(
                    token: String,
                    name: String,
                    parentId: String?,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Success(sampleDriveFolder())
                }

                override suspend fun updateFolder(
                    token: String,
                    folderId: String,
                    name: String?,
                    parentId: String?,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Success(sampleDriveFolder())
                }

                override suspend fun deleteFolder(
                    token: String,
                    folderId: String,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Deleted
                }
            },
        ) {
            override suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult {
                return resultProvider()
            }
        }
    }

    private fun sampleRoom(): ChatRoom {
        return ChatRoom(
            id = "room-1",
            membershipId = "membership-1",
            name = "AGI 讨论",
            description = "聊 AGI",
            joinMode = "open",
            memberCount = 2,
            isMuted = false,
            owner = User("owner", "Owner", "owner", "O"),
        )
    }

    private fun sampleMessage(
        id: String,
        text: String = "message $id",
        reactions: List<ChatMessageReaction> = emptyList(),
        createdAt: String = "",
        createdAtLabel: String = "2026-05-25 01:23",
    ): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = "room-1",
            fromUser = User("user-1", "Alice", "alice", "A"),
            text = text,
            createdAtLabel = createdAtLabel,
            createdAt = createdAt,
            reactions = reactions,
        )
    }

    private fun sampleMember(
        membershipId: String,
        userId: String = "user-1",
    ): ChatRoomMember {
        return ChatRoomMember(
            membershipId = membershipId,
            roomId = "room-1",
            user = User(userId, "Member $userId", "member$userId", "M"),
            joinedAtLabel = "2026-05-25 02:00",
        )
    }

    private fun sampleDriveFile(): DriveFile {
        return DriveFile(
            id = "file-1",
            name = "image.png",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/image.png",
            thumbnailUrl = null,
            comment = null,
            size = 1024,
            isSensitive = false,
        )
    }

    private fun sampleDriveFolder(): cc.hhhl.client.model.DriveFolder {
        return cc.hhhl.client.model.DriveFolder(
            id = "folder-1",
            name = "素材",
            parentId = null,
            foldersCount = 0,
            filesCount = 0,
        )
    }

    private fun sampleUpload(): DriveFileUpload {
        return DriveFileUpload(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "image.png",
            contentType = "image/png",
        )
    }

    private data class SendCall(
        val roomId: String,
        val text: String,
        val fileId: String?,
    )
}
