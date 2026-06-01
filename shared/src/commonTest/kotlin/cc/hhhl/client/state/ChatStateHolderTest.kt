package cc.hhhl.client.state

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
import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.ChatUserHistoryLoadResult
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.cache.ChatUnreadSnapshot
import cc.hhhl.client.cache.ChatUnreadStore
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.model.commonReactionOptions
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRoomInvitationRepositoryResult
import cc.hhhl.client.repository.ChatRoomMemberRepositoryResult
import cc.hhhl.client.repository.ChatRoomMutationRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ChatRepositoryResult
import cc.hhhl.client.repository.ChatStreamingRepository
import cc.hhhl.client.repository.ChatUserConversationRepositoryResult
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
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
    fun dismissErrorMessageClearsHomeStatusPrompt() = runTest {
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Error("服务器连接错误")),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        assertEquals("服务器连接错误", holder.state.value.errorMessage)

        holder.dismissErrorMessage()

        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun dismissChatStatusMessagesClearsDetailPromptFields() = runTest {
        val room = sampleRoom()
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Error("消息加载失败"),
                searchMessagesResult = ChatMessageRepositoryResult.Error("搜索失败"),
                refreshMembersResult = ChatRoomMemberRepositoryResult.Error("成员加载失败"),
                updateRoomManagementResult = ChatRoomMutationRepositoryResult.RoomSaved(room.copy(messageRetentionDays = 7)),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        assertEquals("消息加载失败", holder.state.value.messageErrorMessage)
        holder.dismissMessageErrorMessage()
        assertEquals(null, holder.state.value.messageErrorMessage)

        holder.searchMessages("关键字")
        advanceUntilIdle()
        assertEquals("搜索失败", holder.state.value.messageSearchErrorMessage)
        holder.dismissMessageSearchErrorMessage()
        assertEquals(null, holder.state.value.messageSearchErrorMessage)

        holder.showMembers()
        advanceUntilIdle()
        assertEquals("成员加载失败", holder.state.value.memberErrorMessage)
        holder.dismissMemberErrorMessage()
        assertEquals(null, holder.state.value.memberErrorMessage)

        holder.updateSelectedRoomManagement(7)
        advanceUntilIdle()
        assertEquals("管理设置已更新", holder.state.value.roomManagementMessage)
        holder.dismissRoomManagementMessage()
        assertEquals(null, holder.state.value.roomManagementMessage)

        stream.emit(ChatStreamingEvent.Error("connection refused"))
        advanceUntilIdle()
        assertEquals("实时连接已断开，请稍后重试", holder.state.value.streamingErrorMessage)
        holder.dismissStreamingErrorMessage()
        assertEquals(null, holder.state.value.streamingErrorMessage)
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
                ChatRepositoryResult.Error("当前登录缺少此功能权限，请检查应用授权或账号权限"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("当前登录缺少此功能权限，请检查应用授权或账号权限", holder.state.value.errorMessage)
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
    fun openRoomByIdJoinsUnlistedRoomAndSelectsIt() = runTest {
        val linkedRoom = sampleRoom(id = "room-link")
        val message = sampleMessage("message-1", roomId = linkedRoom.id)
        val shownRoom = linkedRoom.copy(name = "链接聊天室", membershipId = linkedRoom.id)
        val joinedRoom = linkedRoom.copy(membershipId = "membership-link")
        val joinedRoomIds = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(joinedRoom)),
                showRoomResult = ChatRoomMutationRepositoryResult.RoomSaved(shownRoom),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                onJoinRoom = joinedRoomIds::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.openRoomById(linkedRoom.id)
        advanceUntilIdle()

        val roomWithLatestMessage = joinedRoom.copy(
            latestMessageAtLabel = message.createdAtLabel,
            latestMessageMarker = message.id,
        )
        assertEquals(listOf(linkedRoom.id), joinedRoomIds)
        assertEquals(roomWithLatestMessage, holder.state.value.selectedRoom)
        assertEquals(listOf(roomWithLatestMessage), holder.state.value.rooms)
        assertEquals(listOf(message), holder.state.value.messages)
    }

    @Test
    fun searchMessagesExceptionStopsSearchingAndShowsError() = runTest {
        val room = sampleRoom()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                throwOnSearchMessages = true,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.searchMessages("关键字")
        advanceUntilIdle()

        assertFalse(holder.state.value.isSearchingMessages)
        assertEquals("聊天搜索异常", holder.state.value.messageSearchErrorMessage)
    }

    @Test
    fun loadMoreMessageSearchExceptionStopsLoadingAndKeepsResults() = runTest {
        val room = sampleRoom()
        val first = sampleMessage("message-1")
        var searchCalls = 0
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                searchMessagesResultProvider = {
                    if (searchCalls++ == 0) {
                        ChatMessageRepositoryResult.Success(listOf(first))
                    } else {
                        throw IllegalStateException("聊天搜索异常")
                    }
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.searchMessages("关键字")
        advanceUntilIdle()
        holder.loadMoreMessageSearch()
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMoreMessageSearch)
        assertEquals(listOf(first), holder.state.value.messageSearchResults)
        assertEquals("聊天搜索异常", holder.state.value.messageSearchErrorMessage)
    }

    @Test
    fun searchChatUsersStoresRemoteUserResults() = runTest {
        val remoteUser = User("remote-user", "赵远程", "zhao", "赵", host = "remote.example")
        val queries = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(emptyList())),
            discoverRepository = object : DiscoverRepository(tokenProvider = { "token-123" }) {
                override suspend fun searchUsers(
                    query: String,
                    filters: DiscoverAdvancedFilters,
                ): DiscoverRepositoryResult {
                    queries += query
                    return DiscoverRepositoryResult.UserSuccess(listOf(remoteUser, remoteUser))
                }
            },
            scope = TestScope(testScheduler),
        )

        holder.searchChatUsers(" 赵 ")
        advanceUntilIdle()

        assertEquals(listOf("赵"), queries)
        assertFalse(holder.state.value.isSearchingChatUsers)
        assertEquals("赵", holder.state.value.chatUserSearchQuery)
        assertEquals(listOf(remoteUser), holder.state.value.chatUserSearchResults)
        assertEquals(null, holder.state.value.chatUserSearchErrorMessage)
    }

    @Test
    fun staleMessageRefreshDoesNotOverwriteCurrentRoom() = runTest {
        val firstRoom = sampleRoom(id = "room-1")
        val secondRoom = sampleRoom(id = "room-2")
        val firstRoomMessage = sampleMessage("message-room-1", roomId = "room-1")
        val secondRoomMessage = sampleMessage("message-room-2", roomId = "room-2")
        val messageRoomRequestIds = ArrayDeque<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(firstRoom, secondRoom)),
                refreshMessagesResultProvider = {
                    when (messageRoomRequestIds.removeFirst()) {
                        "room-1" -> ChatMessageRepositoryResult.Success(listOf(firstRoomMessage))
                        else -> ChatMessageRepositoryResult.Success(listOf(secondRoomMessage))
                    }
                },
                onRefreshMessages = { messageRoomRequestIds.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(firstRoom)
        holder.selectRoom(secondRoom)
        advanceUntilIdle()

        assertEquals(secondRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals(listOf(secondRoomMessage), holder.state.value.messages)
    }

    @Test
    fun quietRoomRefreshUpdatesSelectedRoomUnreadCountWhileRoomIsNotVisible() = runTest {
        val room = sampleRoom()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshResultProvider = {
                    ChatRepositoryResult.Success(listOf(room.copy(unreadCount = 3)))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(3, holder.state.value.selectedRoomUnreadCount)
        assertEquals(3, holder.state.value.rooms.first().unreadCount)
        assertEquals(3, holder.state.value.selectedRoom?.unreadCount)
    }

    @Test
    fun quietRoomRefreshDoesNotRestoreUnreadForAlreadyReadLatestMarker() = runTest {
        val readRoom = sampleRoom(
            id = "room-read",
            unreadCount = 3,
            latestMessageAtLabel = "2026-05-25 10:01",
            latestMessageMarker = "message-1",
        )
        val otherRoom = sampleRoom(id = "room-other")
        val unreadStore = MemoryChatUnreadStore()
        var rooms = listOf(readRoom, otherRoom)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(readRoom)
        advanceUntilIdle()
        holder.selectRoom(otherRoom)
        advanceUntilIdle()
        rooms = listOf(readRoom.copy(unreadCount = 3), otherRoom)
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(0, holder.state.value.rooms.first { it.id == readRoom.id }.unreadCount)
        assertEquals("message-1", unreadStore.load("account-1").roomReadMarkers[readRoom.id])
    }

    @Test
    fun quietRoomRefreshDoesNotRestoreUnreadWhenReadMessageIdMatchesListMarkerAlias() = runTest {
        val listMarker = "2026-05-25T02:00:00.000Z"
        val readRoom = sampleRoom(
            id = "room-read",
            unreadCount = 3,
            latestMessageAtLabel = "2026-05-25 10:00",
            latestMessageMarker = listMarker,
        )
        val latestMessage = sampleMessage(
            id = "message-1",
            roomId = readRoom.id,
            createdAt = listMarker,
        )
        val otherRoom = sampleRoom(id = "room-other")
        val unreadStore = MemoryChatUnreadStore()
        var rooms = listOf(readRoom, otherRoom)
        var refreshingRoomId = ""
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
                onRefreshMessages = { roomId -> refreshingRoomId = roomId },
                refreshMessagesResultProvider = {
                    if (refreshingRoomId == readRoom.id) {
                        ChatMessageRepositoryResult.Success(listOf(latestMessage))
                    } else {
                        ChatMessageRepositoryResult.Success(emptyList())
                    }
                },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(readRoom)
        advanceUntilIdle()
        holder.selectRoom(otherRoom)
        advanceUntilIdle()
        rooms = listOf(readRoom.copy(unreadCount = 3), otherRoom)
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(0, holder.state.value.rooms.first { it.id == readRoom.id }.unreadCount)
        val readMarker = unreadStore.load("account-1").roomReadMarkers[readRoom.id].orEmpty()
        assertTrue(readMarker.contains(listMarker))
        assertTrue(readMarker.contains("message-1"))
    }

    @Test
    fun quietRoomRefreshRestoresUnreadWhenLatestMarkerChangesAfterRead() = runTest {
        val readRoom = sampleRoom(
            id = "room-read",
            unreadCount = 3,
            latestMessageAtLabel = "2026-05-25 10:01",
            latestMessageMarker = "message-1",
        )
        val otherRoom = sampleRoom(id = "room-other")
        val unreadStore = MemoryChatUnreadStore()
        var rooms = listOf(readRoom, otherRoom)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(readRoom)
        advanceUntilIdle()
        holder.selectRoom(otherRoom)
        advanceUntilIdle()
        rooms = listOf(
            readRoom.copy(
                unreadCount = 1,
                latestMessageAtLabel = "2026-05-25 10:02",
                latestMessageMarker = "message-2",
            ),
            otherRoom,
        )
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(1, holder.state.value.rooms.first { it.id == readRoom.id }.unreadCount)
    }

    @Test
    fun quietUserConversationRefreshDoesNotRestoreUnreadForAlreadyReadLatestMarker() = runTest {
        val peer = User("user-read", "Alice", "alice", "A")
        val otherPeer = User("user-other", "Bob", "bob", "B")
        val readMessage = sampleMessage("message-1", roomId = "").copy(fromUser = peer)
        val readConversation = ChatUserConversation(peer, readMessage, unreadCount = 2)
        val otherConversation = ChatUserConversation(otherPeer)
        val unreadStore = MemoryChatUnreadStore()
        var conversations = listOf(readConversation, otherConversation)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = { ChatUserConversationRepositoryResult.Success(conversations) },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectUserConversation(readConversation)
        advanceUntilIdle()
        holder.selectUserConversation(otherConversation)
        advanceUntilIdle()
        conversations = listOf(readConversation.copy(unreadCount = 2), otherConversation)
        holder.refreshUserConversationsQuietly(markSelectedUserRead = false)
        advanceUntilIdle()

        assertEquals(0, holder.state.value.userConversations.first { it.user.id == peer.id }.unreadCount)
        assertEquals("message-1", unreadStore.load("account-1").userReadMarkers[peer.id])
    }

    @Test
    fun quietUserConversationRefreshRestoresUnreadWhenLatestMarkerChangesAfterRead() = runTest {
        val peer = User("user-read", "Alice", "alice", "A")
        val otherPeer = User("user-other", "Bob", "bob", "B")
        val readMessage = sampleMessage("message-1", roomId = "").copy(fromUser = peer)
        val nextMessage = sampleMessage("message-2", roomId = "").copy(fromUser = peer)
        val readConversation = ChatUserConversation(peer, readMessage, unreadCount = 2)
        val otherConversation = ChatUserConversation(otherPeer)
        val unreadStore = MemoryChatUnreadStore()
        var conversations = listOf(readConversation, otherConversation)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = { ChatUserConversationRepositoryResult.Success(conversations) },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectUserConversation(readConversation)
        advanceUntilIdle()
        holder.selectUserConversation(otherConversation)
        advanceUntilIdle()
        conversations = listOf(
            readConversation.copy(latestMessage = nextMessage, unreadCount = 1),
            otherConversation,
        )
        holder.refreshUserConversationsQuietly(markSelectedUserRead = false)
        advanceUntilIdle()

        assertEquals(1, holder.state.value.userConversations.first { it.user.id == peer.id }.unreadCount)
    }

    @Test
    fun deleteUserConversationClearsPinnedAndLocalUnreadState() = runTest {
        val peer = User("user-read", "Alice", "alice", "A")
        val message = sampleMessage("message-1", roomId = "").copy(fromUser = peer)
        val conversation = ChatUserConversation(peer, message, unreadCount = 2)
        val unreadStore = MemoryChatUnreadStore()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = {
                    ChatUserConversationRepositoryResult.Success(listOf(conversation))
                },
            ),
            scope = TestScope(testScheduler),
            accountIdProvider = { "account-1" },
            unreadStore = unreadStore,
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.toggleUserConversationPinned(peer.id)
        holder.selectUserConversation(conversation)
        advanceUntilIdle()

        holder.deleteUserConversation(peer.id)
        advanceUntilIdle()

        val snapshot = unreadStore.load("account-1")
        assertEquals(emptyList(), holder.state.value.userConversations)
        assertFalse(peer.id in holder.state.value.pinnedUserConversationIds)
        assertEquals(null, holder.state.value.selectedUserConversation)
        assertFalse(peer.id in snapshot.userCounts)
        assertFalse(peer.id in snapshot.userReadMarkers)
        assertFalse(peer.id in snapshot.pinnedUserIds)
    }

    @Test
    fun quietRoomRefreshAccumulatesUnreadWhenRemoteCountStaysFlat() = runTest {
        val first = sampleRoom(
            unreadCount = 1,
            latestMessageAtLabel = "2026-05-25 10:01",
            latestMessageMarker = "message-1",
        )
        val next = first.copy(
            unreadCount = 1,
            latestMessageAtLabel = "2026-05-25 10:02",
            latestMessageMarker = "message-2",
        )
        var refreshCount = 0
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = {
                    refreshCount += 1
                    ChatRepositoryResult.Success(if (refreshCount == 1) listOf(first) else listOf(next))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(2, holder.state.value.rooms.first().unreadCount)
    }

    @Test
    fun quietRoomRefreshDoesNotAccumulateUnreadWhenOnlyFallbackLabelChanges() = runTest {
        val first = sampleRoom(
            unreadCount = 1,
            latestMessageAtLabel = "刚刚",
            latestMessageMarker = "",
        )
        val next = first.copy(
            unreadCount = 1,
            latestMessageAtLabel = "1分钟前",
            latestMessageMarker = "",
        )
        var refreshCount = 0
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshResultProvider = {
                    refreshCount += 1
                    ChatRepositoryResult.Success(if (refreshCount == 1) listOf(first) else listOf(next))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        assertEquals(1, holder.state.value.rooms.first().unreadCount)
    }

    @Test
    fun quietMessageRefreshComputesUnreadJumpWhenUnreadCountWasRestored() = runTest {
        val room = sampleRoom()
        var refreshMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList())
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshResultProvider = {
                    ChatRepositoryResult.Success(listOf(room.copy(unreadCount = 2)))
                },
                refreshMessagesResultProvider = { refreshMessagesResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        advanceUntilIdle()

        val older = sampleMessage("message-older", createdAt = "2026-05-25T01:00:00.000Z")
        val middle = sampleMessage("message-middle", createdAt = "2026-05-25T02:00:00.000Z")
        val latest = sampleMessage("message-latest", createdAt = "2026-05-25T03:00:00.000Z")
        refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(older, middle, latest))

        holder.refreshSelectedMessagesQuietly()
        advanceUntilIdle()

        assertEquals(2, holder.state.value.selectedRoomUnreadCount)
        assertEquals("message-middle", holder.state.value.unreadJumpMessageId)
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
        assertEquals(null, holder.state.value.replyingMessage)
    }

    @Test
    fun chatMessageQuotePreviewFallsBackToAttachmentName() {
        val message = sampleMessage("message-file", text = "").copy(file = sampleDriveFile())

        assertEquals("image.png", chatMessageQuotePreviewText(message))
    }

    @Test
    fun chatMessageQuotePreviewTruncatesWithoutSplittingMfmTokens() {
        val message = sampleMessage(
            "message-mfm",
            text = "hello $[fg.color=ff0000 ${"red ".repeat(30)}] tail",
        )

        assertEquals(false, chatMessageQuotePreviewText(message).contains("$[fg.color"))
    }

    @Test
    fun sendReplyMessageUsesNativeReplyIdAndClearsComposerReply() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", text = "原消息")
        val created = sampleMessage("message-created", text = "回复")
        val calls = mutableListOf<SendCall>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
                onSend = { roomId, text, fileIds, replyId, quoteId ->
                    calls.add(SendCall(roomId, text, fileIds, replyId, quoteId))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.replyMessage("message-1")
        holder.updateMessageDraft("收到")
        holder.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf(SendCall("room-1", "收到", emptyList(), "message-1", null)), calls)
        assertEquals(null, holder.state.value.replyingMessage)
        assertEquals(null, holder.state.value.quotedMessage)
    }

    @Test
    fun sendQuotedMessageUsesNativeQuoteIdAndClearsComposerQuote() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1", text = "原消息")
        val created = sampleMessage("message-created", text = "回复")
        val calls = mutableListOf<SendCall>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
                onSend = { roomId, text, fileIds, replyId, quoteId ->
                    calls.add(SendCall(roomId, text, fileIds, replyId, quoteId))
                },
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

        assertEquals(listOf(SendCall("room-1", "收到", emptyList(), null, "message-1")), calls)
        assertEquals(null, holder.state.value.quotedMessage)
        assertEquals(null, holder.state.value.replyingMessage)
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
    fun uploadMediaQueuesAttachmentsForChatMessage() = runTest {
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
        assertEquals(files.map { it.toChatComposerAttachment() }, holder.state.value.attachments)
    }

    @Test
    fun sendMessageWithAttachedFilesPassesFileIdsAndClearsAttachment() = runTest {
        val room = sampleRoom()
        val file = sampleDriveFile()
        val created = sampleMessage("message-created", text = "")
        val calls = mutableListOf<SendCall>()
        var uploadIndex = 0
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                sendMessageResult = ChatMessageRepositoryResult.Created(created),
                onSend = { roomId, text, fileIds, replyId, quoteId ->
                    calls.add(SendCall(roomId, text, fileIds, replyId, quoteId))
                },
            ),
            driveFileRepository = fakeDriveRepository {
                DriveFileRepositoryResult.Success(
                    if (uploadIndex++ == 0) file else file.copy(id = "file-2", name = "second.png"),
                )
            },
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.uploadMedia(sampleUpload())
        holder.uploadMedia(sampleUpload().copy(fileName = "second.png"))
        advanceUntilIdle()
        holder.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf(SendCall("room-1", "", listOf("file-1", "file-2"), null, null)), calls)
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
    fun deleteMessageRemovesMessageAfterServerSuccess() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("message-1")
        val deleteCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                deleteMessageResult = ChatMessageRepositoryResult.Deleted("message-1"),
                onDelete = deleteCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.deleteMessage("message-1")
        assertTrue(holder.state.value.pendingMessageDeleteIds.contains("message-1"))
        advanceUntilIdle()

        assertEquals(listOf("message-1"), deleteCalls)
        assertTrue(holder.state.value.messages.isEmpty())
        assertFalse(holder.state.value.pendingMessageDeleteIds.contains("message-1"))
        assertEquals(null, holder.state.value.messageErrorMessage)
    }

    @Test
    fun deleteMessageRejectsSyntheticLocalIdBeforeRepositoryCall() = runTest {
        val room = sampleRoom()
        val message = sampleMessage("local-chat-fallback")
        val deleteCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                deleteMessageResult = ChatMessageRepositoryResult.Deleted(message.id),
                onDelete = deleteCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.deleteMessage("local-chat-fallback")
        advanceUntilIdle()

        assertTrue(deleteCalls.isEmpty())
        assertEquals(listOf(message), holder.state.value.messages)
        assertEquals("这条消息还没有服务器 ID，无法同步删除", holder.state.value.messageErrorMessage)
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
    fun staleMemberRefreshDoesNotOverwriteCurrentRoom() = runTest {
        val firstRoom = sampleRoom(id = "room-1")
        val secondRoom = sampleRoom(id = "room-2")
        val firstMember = sampleMember("membership-room-1", roomId = "room-1")
        val secondMember = sampleMember("membership-room-2", roomId = "room-2")
        val memberRoomRequestIds = ArrayDeque<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(firstRoom, secondRoom)),
                refreshMembersResultProvider = {
                    when (memberRoomRequestIds.removeFirst()) {
                        "room-1" -> ChatRoomMemberRepositoryResult.Success(listOf(firstMember))
                        else -> ChatRoomMemberRepositoryResult.Success(listOf(secondMember))
                    }
                },
                onRefreshMembers = { memberRoomRequestIds.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(firstRoom)
        holder.showMembers()
        holder.selectRoom(secondRoom)
        holder.showMembers()
        advanceUntilIdle()

        assertEquals(secondRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals(listOf(secondMember), holder.state.value.members)
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
        val room = sampleRoom(unreadCount = 4)
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
        assertEquals(0, holder.state.value.rooms.first().unreadCount)
        assertEquals(null, holder.state.value.streamingErrorMessage)
    }

    @Test
    fun streamingRoomMessageAddsSenderAsActiveMember() = runTest {
        val room = sampleRoom()
        val streamed = sampleMessage("message-active", text = "实时消息")
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
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()

        val activeMember = holder.state.value.members.single()
        assertEquals(streamed.fromUser.id, activeMember.user.id)
        assertEquals("active", activeMember.user.onlineStatus)
        assertTrue(activeMember.membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX))
    }

    @Test
    fun memberRefreshKeepsStreamedActiveMemberUntilOfficialMemberArrives() = runTest {
        val room = sampleRoom()
        val firstMember = sampleMember("membership-member-1", userId = "user-2")
        val streamed = sampleMessage("message-active", text = "实时消息")
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
                refreshMembersResult = ChatRoomMemberRepositoryResult.Success(listOf(firstMember)),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()
        holder.showMembers()
        advanceUntilIdle()

        assertEquals(listOf("user-2", streamed.fromUser.id), holder.state.value.members.map { it.user.id })
    }

    @Test
    fun openingSpecialCareToastFromRoomListReopensTargetRoomAndSchedulesJump() = runTest {
        val room = sampleRoom()
        val streamed = sampleMessage("message-2", text = "实时消息")
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
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(room)
        holder.updateSpecialCareUsers(setOf(streamed.fromUser.id))
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()
        holder.closeRoom()

        holder.openSpecialCareToast()
        advanceUntilIdle()

        assertEquals(room.id, holder.state.value.selectedRoom?.id)
        assertEquals(streamed.id, holder.state.value.specialCareJumpMessageId)
        assertEquals(null, holder.state.value.specialCareToast)
    }

    @Test
    fun streamingMentionCreatesChatAttentionToast() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val room = sampleRoom()
        val streamed = sampleMessage("message-mention", text = "hello @me@hhhl.cc")
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()

        assertEquals(streamed.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.specialCareToast?.kind)
    }

    @Test
    fun streamingReplyCreatesChatAttentionToast() = runTest {
        val currentUser = User("me", "Me", "me", "M")
        val room = sampleRoom()
        val streamed = sampleMessage("message-reply", text = "reply").copy(
            reply = cc.hhhl.client.model.ChatMessageReference(
                id = "my-message",
                fromUser = currentUser,
                text = "mine",
            ),
        )
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()

        assertEquals(streamed.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Reply, holder.state.value.specialCareToast?.kind)
    }

    @Test
    fun streamingQuoteCreatesChatAttentionToast() = runTest {
        val currentUser = User("me", "Me", "me", "M")
        val room = sampleRoom()
        val streamed = sampleMessage("message-quote", text = "quote").copy(
            quote = cc.hhhl.client.model.ChatMessageReference(
                id = "my-message",
                fromUser = currentUser,
                text = "mine",
            ),
        )
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(streamed))
        advanceUntilIdle()

        assertEquals(streamed.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Quote, holder.state.value.specialCareToast?.kind)
    }

    @Test
    fun externalRealtimeRoomMessageCreatesAttentionWithoutOpeningRoom() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val room = sampleRoom()
        val message = sampleMessage("message-external-mention", roomId = room.id, text = "hello @me")
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(listOf(room))),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.recordRealtimeMessage(message)
        advanceUntilIdle()

        assertEquals(message.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.specialCareToast?.kind)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.roomAttentionKinds[room.id])
        assertEquals(message.id, holder.state.value.rooms.first { it.id == room.id }.latestMessageMarker)
        assertEquals(1, holder.state.value.rooms.first { it.id == room.id }.unreadCount)
    }

    @Test
    fun externalRealtimeUnknownRoomMessageCreatesTemporaryRoom() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val message = sampleMessage(
            id = "message-new-room-mention",
            roomId = "room-new",
            text = "hello @me",
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(emptyList())),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.recordRealtimeMessage(message)
        advanceUntilIdle()

        val room = holder.state.value.rooms.first()
        assertEquals("room-new", room.id)
        assertEquals(message.id, room.latestMessageMarker)
        assertEquals(1, room.unreadCount)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.roomAttentionKinds["room-new"])
    }

    @Test
    fun externalRealtimeUnknownRoomMessageSurvivesNormalRefresh() = runTest {
        val message = sampleMessage(
            id = "message-new-room",
            roomId = "room-new",
            text = "new message",
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(emptyList())),
            scope = TestScope(testScheduler),
            currentUserProvider = { User("me", "Me", "me", "M", host = "hhhl.cc") },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.recordRealtimeMessage(message)
        advanceUntilIdle()
        holder.refresh()
        advanceUntilIdle()

        val room = holder.state.value.rooms.single()
        assertEquals("room-new", room.id)
        assertEquals(message.id, room.latestMessageMarker)
        assertEquals(1, room.unreadCount)
    }

    @Test
    fun externalRealtimeDirectMessageCreatesAttentionWithoutOpeningConversation() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val peer = User("user-1", "Alice", "alice", "A")
        val message = sampleMessage("message-external-dm", roomId = "", text = "hello @me").copy(
            fromUser = peer,
            toUserId = currentUser.id,
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(emptyList())),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.recordRealtimeMessage(message, directUserId = peer.id)
        advanceUntilIdle()

        assertEquals(message.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.specialCareToast?.kind)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.userConversationAttentionKinds[peer.id])
        assertEquals(peer.id, holder.state.value.userConversations.first().user.id)
        assertEquals(1, holder.state.value.userConversations.first().unreadCount)
    }

    @Test
    fun duplicateExternalRealtimeMessagesDoNotIncrementUnreadTwice() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val room = sampleRoom()
        val roomMessage = sampleMessage("message-external-room", roomId = room.id, text = "hello")
        val peer = User("user-1", "Alice", "alice", "A")
        val directMessage = sampleMessage("message-external-dm", roomId = "", text = "hello").copy(
            fromUser = peer,
            toUserId = currentUser.id,
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(listOf(room))),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.recordRealtimeMessage(roomMessage)
        holder.recordRealtimeMessage(roomMessage)
        holder.recordRealtimeMessage(directMessage, directUserId = peer.id)
        holder.recordRealtimeMessage(directMessage, directUserId = peer.id)
        advanceUntilIdle()

        assertEquals(1, holder.state.value.rooms.first { it.id == room.id }.unreadCount)
        assertEquals(1, holder.state.value.userConversations.first { it.user.id == peer.id }.unreadCount)
    }

    @Test
    fun externalRealtimeSelfRoomMessageDoesNotCreateUnreadOrAttention() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val room = sampleRoom()
        val message = sampleMessage("message-self-room", roomId = room.id, text = "hello @me").copy(
            fromUser = currentUser,
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(listOf(room))),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.recordRealtimeMessage(message)
        advanceUntilIdle()

        assertEquals(null, holder.state.value.specialCareToast)
        assertEquals(null, holder.state.value.roomAttentionKinds[room.id])
        assertEquals(message.id, holder.state.value.rooms.first { it.id == room.id }.latestMessageMarker)
        assertEquals(0, holder.state.value.rooms.first { it.id == room.id }.unreadCount)
    }

    @Test
    fun externalRealtimeSelfDirectMessageDoesNotCreateUnreadOrAttention() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val peer = User("user-1", "Alice", "alice", "A")
        val message = sampleMessage("message-self-dm", roomId = "", text = "hello @me").copy(
            fromUser = currentUser,
            toUserId = peer.id,
            toUser = peer,
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(result = ChatRepositoryResult.Success(emptyList())),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.recordRealtimeMessage(message, directUserId = peer.id)
        advanceUntilIdle()

        assertEquals(null, holder.state.value.specialCareToast)
        assertEquals(null, holder.state.value.userConversationAttentionKinds[peer.id])
        assertEquals(peer.id, holder.state.value.userConversations.first().user.id)
        assertEquals(message.id, holder.state.value.userConversations.first().latestMessage?.id)
        assertEquals(0, holder.state.value.userConversations.first().unreadCount)
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
    fun streamingMessageReplacesDuplicateById() = runTest {
        val room = sampleRoom()
        val existing = sampleMessage("message-1", text = "旧消息")
        val duplicate = sampleMessage("message-1", text = "实时更新")
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

        assertEquals(listOf(duplicate), holder.state.value.messages)
    }

    @Test
    fun withChronologicalMessageReplacesChangedDuplicateAndSortsNewMessages() {
        val older = sampleMessage(
            id = "message-older",
            createdAt = "2026-05-25T01:00:00.000Z",
        )
        val newer = sampleMessage(
            id = "message-newer",
            createdAt = "2026-05-25T03:00:00.000Z",
        )
        val current = listOf(newer)

        val unchanged = current.withChronologicalMessage(newer)
        val replaced = current.withChronologicalMessage(newer.copy(text = "updated"))
        val inserted = current.withChronologicalMessage(older)

        assertSame(current, unchanged)
        assertEquals("updated", replaced.single().text)
        assertEquals(listOf("message-older", "message-newer"), inserted.map { it.id })
    }

    @Test
    fun ensureChronologicalMessagesReusesAlreadyOrderedUniqueList() {
        val messages = listOf(
            sampleMessage(
                id = "message-older",
                createdAt = "2026-05-25T01:00:00.000Z",
            ),
            sampleMessage(
                id = "message-newer",
                createdAt = "2026-05-25T02:00:00.000Z",
            ),
        )

        assertSame(messages, messages.ensureChronologicalMessages())
    }

    @Test
    fun mergeReplacingChronologicalMessagesReplacesExistingMessageById() {
        val current = listOf(
            sampleMessage(
                id = "message-1",
                text = "旧内容",
                createdAt = "2026-05-25T01:00:00.000Z",
            ),
        )
        val refreshed = listOf(
            sampleMessage(
                id = "message-1",
                text = "新内容",
                createdAt = "2026-05-25T01:00:00.000Z",
            ),
            sampleMessage(
                id = "message-2",
                createdAt = "2026-05-25T02:00:00.000Z",
            ),
        )

        val merged = current.mergeReplacingChronologicalMessages(refreshed)

        assertEquals(listOf("message-1", "message-2"), merged.map { it.id })
        assertEquals("新内容", merged.first().text)
    }

    @Test
    fun userFacingStreamingErrorMessageHidesTransportDetails() {
        assertEquals("实时连接暂时不可用，请稍后重试", userFacingStreamingErrorMessage("HTTP 401 Unauthorized"))
        assertEquals("实时连接暂时不可用，请稍后重试", userFacingStreamingErrorMessage("authentication handshake failed"))
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
    fun openUserConversationCreatesEmptyConversationAndSchedulesJump() = runTest {
        val user = User("user-2", "Bob", "bob", "B")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshUserMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.openUserConversation(user, jumpMessageId = "message-2")
        advanceUntilIdle()

        assertEquals(user.id, holder.state.value.selectedUserConversation?.user?.id)
        assertEquals(listOf(user.id), holder.state.value.userConversations.map { it.user.id })
        assertEquals("message-2", holder.state.value.specialCareJumpMessageId)
    }

    @Test
    fun refreshingUserConversationsCreatesSpecialCareToastOnlyForNewPeerMessages() = runTest {
        val peer = User("user-1", "Alice", "alice", "A")
        val first = sampleMessage("message-1")
        val next = sampleMessage("message-2", text = "new dm")
        var conversations = listOf(ChatUserConversation(peer, first))
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = {
                    ChatUserConversationRepositoryResult.Success(conversations)
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.updateSpecialCareUsers(setOf(peer.id))
        holder.refreshUserConversationsQuietly()
        advanceUntilIdle()
        assertEquals(null, holder.state.value.specialCareToast)

        conversations = listOf(ChatUserConversation(peer, next))
        holder.refreshUserConversationsQuietly()
        advanceUntilIdle()

        assertEquals(next.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(peer.id, holder.state.value.specialCareToast?.chatUserId)
    }

    @Test
    fun refreshingUserConversationsResolvesMissingReplyReferenceBeforeAttentionToast() = runTest {
        val currentUser = User("me", "Me", "me", "M")
        val peer = User("user-1", "Alice", "alice", "A")
        val first = sampleMessage("message-1", roomId = "").copy(fromUser = peer)
        val unresolvedReply = sampleMessage("message-2", roomId = "", text = "reply").copy(
            fromUser = peer,
            replyUnavailable = true,
        )
        val resolvedReply = unresolvedReply.copy(
            replyUnavailable = false,
            reply = ChatMessageReference(
                id = "message-me",
                fromUser = currentUser,
                text = "original",
            ),
        )
        var conversations = listOf(ChatUserConversation(peer, first))
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = {
                    ChatUserConversationRepositoryResult.Success(conversations)
                },
                refreshUserMessagesResult = ChatMessageRepositoryResult.Success(listOf(resolvedReply)),
            ),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refreshUserConversationsQuietly()
        advanceUntilIdle()
        conversations = listOf(ChatUserConversation(peer, unresolvedReply))
        holder.refreshUserConversationsQuietly()
        advanceUntilIdle()

        assertEquals(resolvedReply.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Reply, holder.state.value.specialCareToast?.kind)
        assertEquals(ChatAttentionKind.Reply, holder.state.value.userConversationAttentionKinds[peer.id])
    }

    @Test
    fun refreshingSpecialCareRoomsOnlyToastsNewMessagesAfterBaseline() = runTest {
        val room = sampleRoom(id = "room-1")
        val first = sampleMessage("message-1", roomId = room.id).copy(fromUser = User("user-1", "Alice", "alice", "A"))
        val next = sampleMessage("message-2", roomId = room.id, text = "new room message")
            .copy(fromUser = first.fromUser)
        var roomMessages = listOf(first)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                refreshMessagesResultProvider = {
                    ChatMessageRepositoryResult.Success(roomMessages)
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.updateSpecialCareUsers(setOf(first.fromUser.id))
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()
        assertEquals(null, holder.state.value.specialCareToast)

        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()
        assertEquals(null, holder.state.value.specialCareToast)

        roomMessages = listOf(first, next)
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals(next.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(room.id, holder.state.value.specialCareToast?.roomId)
    }

    @Test
    fun refreshingSpecialCareRoomsIncludesOwnedOnlyRooms() = runTest {
        val specialUser = User("user-special", "Special", "special", "S")
        val room = sampleRoom(
            id = "room-owned-only",
            unreadCount = 1,
            latestMessageMarker = "message-1",
        ).copy(name = "Owned only")
        val first = sampleMessage("message-1", roomId = room.id).copy(fromUser = specialUser)
        val next = sampleMessage("message-2", roomId = room.id, text = "owned room update")
            .copy(fromUser = specialUser)
        var ownedRooms = listOf(room)
        var roomMessages = listOf(first)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshOwnedRoomsResultProvider = { ChatRepositoryResult.Success(ownedRooms) },
                refreshMessagesResultProvider = { ChatMessageRepositoryResult.Success(roomMessages) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.updateSpecialCareUsers(setOf(specialUser.id))
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()
        assertEquals(null, holder.state.value.specialCareToast)

        ownedRooms = listOf(room.copy(latestMessageMarker = next.id))
        roomMessages = listOf(first, next)
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals(next.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(room.id, holder.state.value.specialCareToast?.roomId)
    }

    @Test
    fun refreshingSpecialCareRoomsScansPastOldLimitForLaterRooms() = runTest {
        val specialUser = User("user-special", "Special", "special", "S")
        val baseRooms = (1..9).map { index ->
            sampleRoom(
                id = "room-$index",
                unreadCount = 0,
                latestMessageAtLabel = "2026-05-25 10:${index.toString().padStart(2, '0')}",
                latestMessageMarker = "message-base-$index",
            )
        }
        var rooms = baseRooms
        var currentRoomId: String? = null
        val roomMessages = buildMap {
            baseRooms.dropLast(1).forEach { room ->
                put(room.id, emptyList())
            }
            put(
                "room-9",
                listOf(
                    sampleMessage(
                        "message-target",
                        roomId = "room-9",
                        text = "hello @special@hhhl.cc",
                        createdAt = "2026-05-25T10:09:00.000Z",
                    ).copy(fromUser = specialUser),
                ),
            )
        }
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(baseRooms),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
                refreshMessagesResultProvider = {
                    ChatMessageRepositoryResult.Success(roomMessages[currentRoomId].orEmpty())
                },
                onRefreshMessages = { roomId -> currentRoomId = roomId },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        rooms = baseRooms.map { room ->
            if (room.id != "room-9") return@map room
            room.copy(
                unreadCount = 1,
                latestMessageAtLabel = "2026-05-25 10:09",
                latestMessageMarker = "message-target",
            )
        }
        holder.updateSpecialCareUsers(setOf(specialUser.id))
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals("message-target", holder.state.value.specialCareToast?.messageId)
        assertEquals("room-9", holder.state.value.specialCareToast?.roomId)
    }

    @Test
    fun refreshingSpecialCareRoomsLoadsMorePagesForLaterRooms() = runTest {
        val specialUser = User("user-special", "Special", "special", "S")
        val firstPageRooms = (1..8).map { index ->
            sampleRoom(
                id = "room-$index",
                unreadCount = 0,
                latestMessageAtLabel = "2026-05-25 10:${index.toString().padStart(2, '0')}",
                latestMessageMarker = "message-base-$index",
            )
        }
        val laterRoom = sampleRoom(
            id = "room-9",
            unreadCount = 1,
            latestMessageAtLabel = "2026-05-25 10:09",
            latestMessageMarker = "message-target",
        )
        val loadMoreCalls = mutableListOf<String>()
        var currentRoomId: String? = null
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(firstPageRooms, endReached = false),
                loadMoreResult = ChatRepositoryResult.Success(firstPageRooms + laterRoom, endReached = true),
                refreshMessagesResultProvider = {
                    ChatMessageRepositoryResult.Success(
                        if (currentRoomId == laterRoom.id) {
                            listOf(
                                sampleMessage(
                                    "message-target",
                                    roomId = laterRoom.id,
                                    text = "hello @special@hhhl.cc",
                                    createdAt = "2026-05-25T10:09:00.000Z",
                                ).copy(fromUser = specialUser),
                            )
                        } else {
                            emptyList()
                        },
                    )
                },
                onRefreshMessages = { roomId -> currentRoomId = roomId },
                onLoadMore = { loadMoreCalls += "load-more" },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.updateSpecialCareUsers(setOf(specialUser.id))
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals(listOf("load-more"), loadMoreCalls)
        assertEquals("message-target", holder.state.value.specialCareToast?.messageId)
        assertEquals(laterRoom.id, holder.state.value.specialCareToast?.roomId)
    }

    @Test
    fun refreshingAttentionRoomsNotifiesFirstDetectedUnreadMention() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val baseRoom = sampleRoom(
            id = "room-1",
            unreadCount = 0,
            latestMessageAtLabel = "2026-05-25 10:01",
            latestMessageMarker = "message-1",
        )
        val unreadRoom = baseRoom.copy(
            unreadCount = 1,
            latestMessageAtLabel = "2026-05-25 10:02",
            latestMessageMarker = "message-mention",
        )
        val mention = sampleMessage(
            "message-mention",
            roomId = unreadRoom.id,
            text = "hello @me@hhhl.cc",
        )
        var rooms = listOf(baseRoom)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(baseRoom)),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
                refreshMessagesResultProvider = { ChatMessageRepositoryResult.Success(listOf(mention)) },
            ),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        rooms = listOf(unreadRoom)
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals(mention.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.specialCareToast?.kind)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.roomAttentionKinds[unreadRoom.id])
    }

    @Test
    fun refreshingAttentionRoomsDoesNotMissMentionWhenLatestMessageIsPlain() = runTest {
        val currentUser = User("me", "Me", "me", "M", host = "hhhl.cc")
        val baseRoom = sampleRoom(
            id = "room-1",
            unreadCount = 0,
            latestMessageAtLabel = "2026-05-25 10:01",
            latestMessageMarker = "message-base",
        )
        val unreadRoom = baseRoom.copy(
            unreadCount = 2,
            latestMessageAtLabel = "2026-05-25 10:03",
            latestMessageMarker = "message-plain",
        )
        val base = sampleMessage(
            "message-base",
            roomId = unreadRoom.id,
            text = "baseline",
            createdAt = "2026-05-25T10:01:00.000Z",
        )
        val mention = sampleMessage(
            "message-mention",
            roomId = unreadRoom.id,
            text = "hello @me@hhhl.cc",
            createdAt = "2026-05-25T10:02:00.000Z",
        )
        val plain = sampleMessage(
            "message-plain",
            roomId = unreadRoom.id,
            text = "plain after mention",
            createdAt = "2026-05-25T10:03:00.000Z",
        )
        var rooms = listOf(baseRoom)
        var roomMessages = listOf(base)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(baseRoom)),
                refreshResultProvider = { ChatRepositoryResult.Success(rooms) },
                refreshMessagesResultProvider = { ChatMessageRepositoryResult.Success(roomMessages) },
            ),
            scope = TestScope(testScheduler),
            currentUserProvider = { currentUser },
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()
        rooms = listOf(unreadRoom)
        roomMessages = listOf(base, mention, plain)
        holder.refreshRoomsQuietly(markSelectedRoomRead = false)
        holder.refreshSpecialCareMessagesQuietly()
        advanceUntilIdle()

        assertEquals(mention.id, holder.state.value.specialCareToast?.messageId)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.specialCareToast?.kind)
        assertEquals(ChatAttentionKind.Mention, holder.state.value.roomAttentionKinds[unreadRoom.id])
    }

    @Test
    fun refreshingUserConversationsAccumulatesUnreadWhenRemoteCountStaysFlat() = runTest {
        val peer = User("user-1", "Alice", "alice", "A")
        val first = sampleMessage("message-1").copy(isRead = false)
        val next = sampleMessage("message-2", text = "new dm").copy(isRead = false)
        var conversations = listOf(ChatUserConversation(peer, first, unreadCount = 1))
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                userConversationResultProvider = {
                    ChatUserConversationRepositoryResult.Success(conversations)
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        conversations = listOf(ChatUserConversation(peer, next, unreadCount = 1))
        holder.refreshUserConversationsQuietly()
        advanceUntilIdle()

        assertEquals(2, holder.state.value.userConversations.first().unreadCount)
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

    @Test
    fun staleStreamingMessageDoesNotOverwriteCurrentRoom() = runTest {
        val firstRoom = sampleRoom(id = "room-1")
        val secondRoom = sampleRoom(id = "room-2")
        val staleMessage = sampleMessage("message-room-1", roomId = "room-1")
        val stream = MutableSharedFlow<ChatStreamingEvent>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(firstRoom, secondRoom)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(emptyList()),
            ),
            streamingRepository = fakeStreamingRepository(stream),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(firstRoom)
        advanceUntilIdle()
        holder.selectRoom(secondRoom)
        advanceUntilIdle()
        stream.emit(ChatStreamingEvent.MessageReceived(staleMessage))
        advanceUntilIdle()

        assertEquals(secondRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals(emptyList(), holder.state.value.messages)
    }

    @Test
    fun refreshRoomExtrasAddsOwnedOnlyRoomToMainRoomList() = runTest {
        val joinedRoom = sampleRoom(id = "room-joined")
        val ownedRoom = sampleRoom(id = "room-owned").copy(
            membershipId = "room-owned",
            name = "Only mine",
        )
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(joinedRoom)),
                refreshOwnedRoomsResult = ChatRepositoryResult.Success(listOf(ownedRoom)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        assertEquals(listOf(ownedRoom), holder.state.value.ownedRooms)
        assertEquals(listOf("room-joined", "room-owned"), holder.state.value.rooms.map { it.id })
    }

    @Test
    fun deleteSelectedOwnedRoomRemovesItFromMainAndOwnedRoomLists() = runTest {
        val ownedRoom = sampleRoom(id = "room-owned")
        val deleteCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshOwnedRoomsResult = ChatRepositoryResult.Success(listOf(ownedRoom)),
                deleteRoomResult = ChatRoomMutationRepositoryResult.RoomRemoved(ownedRoom.id),
                onDeleteRoom = deleteCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refreshRoomExtras()
        advanceUntilIdle()
        holder.selectRoom(ownedRoom)
        advanceUntilIdle()

        holder.deleteSelectedRoom()
        advanceUntilIdle()

        assertEquals(listOf(ownedRoom.id), deleteCalls)
        assertEquals(emptyList(), holder.state.value.rooms)
        assertEquals(emptyList(), holder.state.value.ownedRooms)
        assertEquals(null, holder.state.value.selectedRoom)
        assertEquals("聊天室已删除", holder.state.value.roomManagementMessage)
    }

    @Test
    fun deleteRoomRemovesOwnedRoomFromMainAndOwnedRoomListsWithoutSelectingIt() = runTest {
        val ownedRoom = sampleRoom(id = "room-owned")
        val deleteCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                refreshOwnedRoomsResult = ChatRepositoryResult.Success(listOf(ownedRoom)),
                deleteRoomResult = ChatRoomMutationRepositoryResult.RoomRemoved(ownedRoom.id),
                onDeleteRoom = deleteCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refreshRoomExtras()
        advanceUntilIdle()

        holder.deleteRoom(ownedRoom.id)
        advanceUntilIdle()

        assertEquals(listOf(ownedRoom.id), deleteCalls)
        assertEquals(emptyList(), holder.state.value.rooms)
        assertEquals(emptyList(), holder.state.value.ownedRooms)
        assertEquals(null, holder.state.value.selectedRoom)
        assertEquals("聊天室已删除", holder.state.value.roomManagementMessage)
    }

    @Test
    fun deleteRoomDoesNotCloseDifferentSelectedRoom() = runTest {
        val selectedRoom = sampleRoom(id = "room-selected")
        val ownedRoom = sampleRoom(id = "room-owned").copy(name = "Owned room")
        val message = sampleMessage("message-selected", roomId = selectedRoom.id)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(selectedRoom, ownedRoom)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(message)),
                deleteRoomResult = ChatRoomMutationRepositoryResult.RoomRemoved(ownedRoom.id),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(selectedRoom)
        advanceUntilIdle()

        holder.deleteRoom(ownedRoom.id)
        advanceUntilIdle()

        assertEquals(selectedRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals(listOf(message.id), holder.state.value.messages.map { it.id })
        assertEquals(listOf(selectedRoom.id), holder.state.value.rooms.map { it.id })
        assertEquals("聊天室已删除", holder.state.value.roomManagementMessage)
    }

    @Test
    fun createRoomDoesNotShowSuccessStatusRowMessage() = runTest {
        val createdRoom = sampleRoom(id = "room-created")
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(emptyList()),
                createRoomResult = ChatRoomMutationRepositoryResult.RoomSaved(createdRoom),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.createRoom("新聊天室", "")
        advanceUntilIdle()

        assertEquals(listOf(createdRoom.id), holder.state.value.rooms.map { it.id })
        assertEquals(null, holder.state.value.roomManagementMessage)
    }

    @Test
    fun updateSelectedRoomManagementAppliesReturnedRetentionDays() = runTest {
        val room = sampleRoom(id = "room-owned").copy(canManage = true, messageRetentionDays = null)
        val updatedRoom = room.copy(messageRetentionDays = 30)
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(room)),
                updateRoomManagementResult = ChatRoomMutationRepositoryResult.RoomSaved(updatedRoom),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.selectRoom(room)
        advanceUntilIdle()
        holder.updateSelectedRoomManagement(30)
        advanceUntilIdle()

        assertEquals(30, holder.state.value.selectedRoom?.messageRetentionDays)
        assertEquals(30, holder.state.value.rooms.first().messageRetentionDays)
        assertEquals("管理设置已更新", holder.state.value.roomManagementMessage)
    }

    @Test
    fun clearSelectedRoomMessagesClearsCurrentMessagesAndRoomMarkers() = runTest {
        val room = sampleRoom(
            id = "room-owned",
            unreadCount = 3,
            latestMessageAtLabel = "2026-05-25 01:23",
            latestMessageMarker = "message-1",
        ).copy(canManage = true)
        val message = sampleMessage("message-1", roomId = room.id)
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
        holder.clearSelectedRoomMessages()
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.messages)
        assertEquals(0, holder.state.value.selectedRoom?.unreadCount)
        assertEquals("", holder.state.value.selectedRoom?.latestMessageMarker)
        assertEquals("", holder.state.value.rooms.first().latestMessageAtLabel)
        assertEquals("聊天室消息已清空", holder.state.value.roomManagementMessage)
    }

    @Test
    fun clearRoomMessagesClearsNonSelectedRoomMarkersById() = runTest {
        val selectedRoom = sampleRoom(id = "room-selected")
        val targetRoom = sampleRoom(
            id = "room-target",
            unreadCount = 5,
            latestMessageAtLabel = "2026-05-25 03:00",
            latestMessageMarker = "message-target",
        ).copy(canManage = true)
        val selectedMessage = sampleMessage("message-selected", roomId = selectedRoom.id)
        val clearCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(selectedRoom, targetRoom)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(selectedMessage)),
                onClearRoomMessages = clearCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(selectedRoom)
        advanceUntilIdle()

        holder.clearRoomMessages(targetRoom.id)
        advanceUntilIdle()

        assertEquals(listOf(targetRoom.id), clearCalls)
        assertEquals(listOf(selectedMessage.id), holder.state.value.messages.map { it.id })
        assertEquals(0, holder.state.value.rooms.first { it.id == targetRoom.id }.unreadCount)
        assertEquals("", holder.state.value.rooms.first { it.id == targetRoom.id }.latestMessageMarker)
        assertEquals(selectedRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals("聊天室消息已清空", holder.state.value.roomManagementMessage)
    }

    @Test
    fun leaveRoomRemovesNonSelectedRoomById() = runTest {
        val selectedRoom = sampleRoom(id = "room-selected")
        val targetRoom = sampleRoom(id = "room-target")
        val selectedMessage = sampleMessage("message-selected", roomId = selectedRoom.id)
        val leaveCalls = mutableListOf<String>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(selectedRoom, targetRoom)),
                refreshMessagesResult = ChatMessageRepositoryResult.Success(listOf(selectedMessage)),
                leaveRoomResult = ChatRoomMutationRepositoryResult.RoomRemoved(targetRoom.id),
                onLeaveRoom = leaveCalls::add,
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()
        holder.selectRoom(selectedRoom)
        advanceUntilIdle()

        holder.leaveRoom(targetRoom.id)
        advanceUntilIdle()

        assertEquals(listOf(targetRoom.id), leaveCalls)
        assertEquals(listOf(selectedRoom.id), holder.state.value.rooms.map { it.id })
        assertEquals(selectedRoom.id, holder.state.value.selectedRoom?.id)
        assertEquals(listOf(selectedMessage.id), holder.state.value.messages.map { it.id })
        assertEquals("已退出聊天室", holder.state.value.roomManagementMessage)
    }

    @Test
    fun muteRoomUpdatesNonSelectedRoomAndOwnedRoomById() = runTest {
        val targetRoom = sampleRoom(id = "room-target")
        val muteCalls = mutableListOf<Pair<String, Boolean>>()
        val holder = ChatStateHolder(
            repository = fakeRepository(
                result = ChatRepositoryResult.Success(listOf(targetRoom)),
                refreshOwnedRoomsResult = ChatRepositoryResult.Success(listOf(targetRoom)),
                muteRoomResult = ChatRoomMutationRepositoryResult.RoomMuted(targetRoom.id, true),
                onMuteRoom = { roomId, muted -> muteCalls += roomId to muted },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateAvailability(chatAvailable = true)
        holder.refresh()
        advanceUntilIdle()

        holder.muteRoom(targetRoom.id, true)
        advanceUntilIdle()

        assertEquals(listOf(targetRoom.id to true), muteCalls)
        assertTrue(holder.state.value.rooms.first { it.id == targetRoom.id }.isMuted)
        assertTrue(holder.state.value.ownedRooms.first { it.id == targetRoom.id }.isMuted)
        assertEquals("聊天室已静音", holder.state.value.roomManagementMessage)
    }

    @Test
    fun updateReactionOptionsPrependsLoadedReactionsForChatMenu() {
        val holder = ChatStateHolder(
            repository = fakeRepository(ChatRepositoryResult.Success(emptyList())),
            scope = TestScope(),
        )

        holder.updateReactionOptions(listOf("🔥", "👍", "", "🔥", ":hhhl:"))

        assertEquals(
            listOf("🔥", "👍", ":hhhl:") +
                commonReactionOptions.filterNot { it in listOf("🔥", "👍", ":hhhl:") },
            holder.state.value.reactionOptions,
        )
    }

    private class MemoryChatUnreadStore : ChatUnreadStore {
        private val snapshots = mutableMapOf<String, ChatUnreadSnapshot>()

        override fun load(accountId: String): ChatUnreadSnapshot {
            return snapshots[accountId.trim()] ?: ChatUnreadSnapshot()
        }

        override fun save(accountId: String, snapshot: ChatUnreadSnapshot) {
            val cleanAccountId = accountId.trim()
            if (cleanAccountId.isNotEmpty()) snapshots[cleanAccountId] = snapshot
        }

        override fun clearRoom(accountId: String, roomId: String) {
            val cleanAccountId = accountId.trim()
            val cleanRoomId = roomId.trim()
            if (cleanAccountId.isEmpty() || cleanRoomId.isEmpty()) return
            val current = load(cleanAccountId)
            save(
                cleanAccountId,
                current.copy(
                    roomCounts = current.roomCounts - cleanRoomId,
                    roomReadMarkers = current.roomReadMarkers - cleanRoomId,
                ),
            )
        }

        override fun clearUser(accountId: String, userId: String) {
            val cleanAccountId = accountId.trim()
            val cleanUserId = userId.trim()
            if (cleanAccountId.isEmpty() || cleanUserId.isEmpty()) return
            val current = load(cleanAccountId)
            save(
                cleanAccountId,
                current.copy(
                    userCounts = current.userCounts - cleanUserId,
                    userReadMarkers = current.userReadMarkers - cleanUserId,
                ),
            )
        }

        override fun clearAccount(accountId: String) {
            snapshots.remove(accountId.trim())
        }
    }

    private fun fakeRepository(
        result: ChatRepositoryResult,
        loadMoreResult: ChatRepositoryResult = result,
        refreshMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        loadMoreMessagesResult: ChatMessageRepositoryResult = refreshMessagesResult,
        sendMessageResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Created(sampleMessage("created")),
        reactResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.ReactionUpdated,
        deleteMessageResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Deleted("message-1"),
        refreshMembersResult: ChatRoomMemberRepositoryResult = ChatRoomMemberRepositoryResult.Success(emptyList()),
        loadMoreMembersResult: ChatRoomMemberRepositoryResult = refreshMembersResult,
        refreshUserConversationsResult: ChatUserConversationRepositoryResult =
            ChatUserConversationRepositoryResult.Success(emptyList()),
        refreshOwnedRoomsResult: ChatRepositoryResult = ChatRepositoryResult.Success(emptyList()),
        refreshInvitationInboxResult: ChatRoomInvitationRepositoryResult = ChatRoomInvitationRepositoryResult.Success(emptyList()),
        refreshInvitationOutboxResult: ChatRoomInvitationRepositoryResult = ChatRoomInvitationRepositoryResult.Success(emptyList()),
        createRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomSaved(sampleRoom()),
        updateRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomSaved(sampleRoom()),
        updateRoomManagementResult: ChatRoomMutationRepositoryResult = updateRoomResult,
        leaveRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomRemoved("room-1"),
        deleteRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomRemoved("room-1"),
        muteRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomMuted("room-1", true),
        refreshUserMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        onRefresh: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onRefreshMessages: (String) -> Unit = {},
        onRefreshMembers: (String) -> Unit = {},
        onSend: (String, String, List<String>, String?, String?) -> Unit = { _, _, _, _, _ -> },
        onDelete: (String) -> Unit = {},
        cachedMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        cachedUserMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        onCacheRoomMessage: (String, ChatMessage) -> Unit = { _, _ -> },
        onCacheUserMessage: (String, ChatMessage) -> Unit = { _, _ -> },
        searchMessagesResult: ChatMessageRepositoryResult = ChatMessageRepositoryResult.Success(emptyList()),
        refreshResultProvider: (() -> ChatRepositoryResult)? = null,
        refreshMessagesResultProvider: (() -> ChatMessageRepositoryResult)? = null,
        refreshMembersResultProvider: (() -> ChatRoomMemberRepositoryResult)? = null,
        refreshOwnedRoomsResultProvider: (() -> ChatRepositoryResult)? = null,
        userConversationResultProvider: (() -> ChatUserConversationRepositoryResult)? = null,
        searchMessagesResultProvider: (() -> ChatMessageRepositoryResult)? = null,
        showRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.RoomSaved(sampleRoom()),
        joinRoomResult: ChatRoomMutationRepositoryResult = ChatRoomMutationRepositoryResult.ActionCompleted("已加入聊天室"),
        onShowRoom: (String) -> Unit = {},
        onJoinRoom: (String) -> Unit = {},
        onDeleteRoom: (String) -> Unit = {},
        onClearRoomMessages: (String) -> Unit = {},
        onLeaveRoom: (String) -> Unit = {},
        onMuteRoom: (String, Boolean) -> Unit = { _, _ -> },
        throwOnSearchMessages: Boolean = false,
    ): ChatRepository {
        return object : ChatRepository(
            tokenProvider = { "token-123" },
            api = object : ChatApi {
                override suspend fun loadJoiningRooms(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomLoadResult = ChatRoomLoadResult.Success(emptyList())

                override suspend fun loadOwnedRooms(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomLoadResult = ChatRoomLoadResult.Success(emptyList())

                override suspend fun loadInvitationInbox(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomInvitationLoadResult = ChatRoomInvitationLoadResult.Success(emptyList())

                override suspend fun loadInvitationOutbox(
                    token: String,
                    roomId: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomInvitationLoadResult = ChatRoomInvitationLoadResult.Success(emptyList())

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
                    fileIds: List<String>,
                    replyId: String?,
                    quoteId: String?,
                ): ChatMessageCreateResult = ChatMessageCreateResult.Success(sampleMessage("created"))

                override suspend fun createRoom(
                    token: String,
                    name: String,
                    description: String,
                    joinMode: String,
                ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom())

                override suspend fun showRoom(
                    token: String,
                    roomId: String,
                ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom(roomId))

                override suspend fun loadUserHistory(
                    token: String,
                    limit: Int,
                ): ChatUserHistoryLoadResult = ChatUserHistoryLoadResult.Success(emptyList())

                override suspend fun loadUserMessages(
                    token: String,
                    userId: String,
                    limit: Int,
                    untilId: String?,
                ): ChatMessageLoadResult = ChatMessageLoadResult.Success(emptyList())

                override suspend fun searchMessages(
                    token: String,
                    query: String,
                    limit: Int,
                    untilId: String?,
                    roomId: String?,
                    userId: String?,
                ): ChatMessageLoadResult = ChatMessageLoadResult.Success(emptyList())

                override suspend fun createUserMessage(
                    token: String,
                    userId: String,
                    text: String,
                    fileId: String?,
                    replyId: String?,
                    quoteId: String?,
                ): ChatMessageCreateResult = ChatMessageCreateResult.Success(sampleMessage("created-user"))

                override suspend fun deleteMessage(
                    token: String,
                    messageId: String,
                ): ChatMessageDeleteResult = ChatMessageDeleteResult.Success

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

                override suspend fun updateRoom(
                    token: String,
                    roomId: String,
                    name: String?,
                    description: String?,
                    joinMode: String?,
                ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom())

                override suspend fun updateRoomManagement(
                    token: String,
                    roomId: String,
                    messageRetentionDays: Int?,
                ): ChatRoomMutationResult = ChatRoomMutationResult.Success(
                    sampleRoom(roomId).copy(messageRetentionDays = messageRetentionDays),
                )

                override suspend fun inviteRoomMember(
                    token: String,
                    roomId: String,
                    userId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun joinRoom(
                    token: String,
                    roomId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun leaveRoom(
                    token: String,
                    roomId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun deleteRoom(
                    token: String,
                    roomId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun deleteAllRoomMessages(
                    token: String,
                    roomId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun muteRoom(
                    token: String,
                    roomId: String,
                    muted: Boolean,
                ): ChatRoomActionResult = ChatRoomActionResult.Success

                override suspend fun ignoreRoomInvitation(
                    token: String,
                    roomId: String,
                ): ChatRoomActionResult = ChatRoomActionResult.Success
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

            override suspend fun refreshOwnedRooms(): ChatRepositoryResult {
                return refreshOwnedRoomsResultProvider?.invoke() ?: refreshOwnedRoomsResult
            }

            override suspend fun refreshInvitationInbox(): ChatRoomInvitationRepositoryResult {
                return refreshInvitationInboxResult
            }

            override suspend fun refreshInvitationOutbox(): ChatRoomInvitationRepositoryResult {
                return refreshInvitationOutboxResult
            }

            override suspend fun refreshMessages(roomId: String): ChatMessageRepositoryResult {
                onRefreshMessages(roomId)
                return refreshMessagesResultProvider?.invoke() ?: refreshMessagesResult
            }

            override suspend fun restoreCachedMessages(roomId: String): ChatMessageRepositoryResult {
                return cachedMessagesResult
            }

            override suspend fun restoreCachedUserMessages(userId: String): ChatMessageRepositoryResult {
                return cachedUserMessagesResult
            }

            override suspend fun cacheRoomMessage(roomId: String, message: ChatMessage) {
                onCacheRoomMessage(roomId, message)
            }

            override suspend fun cacheUserMessage(userId: String, message: ChatMessage) {
                onCacheUserMessage(userId, message)
            }

            override suspend fun loadMoreMessages(
                roomId: String,
                currentMessages: List<ChatMessage>,
            ): ChatMessageRepositoryResult {
                return loadMoreMessagesResult
            }

            override suspend fun refreshUserConversations(): ChatUserConversationRepositoryResult {
                return userConversationResultProvider?.invoke() ?: refreshUserConversationsResult
            }

            override suspend fun refreshUserMessages(userId: String): ChatMessageRepositoryResult {
                return refreshUserMessagesResult
            }

            override suspend fun loadMoreUserMessages(
                userId: String,
                currentMessages: List<ChatMessage>,
            ): ChatMessageRepositoryResult {
                return refreshUserMessagesResult
            }

            override suspend fun searchMessages(
                query: String,
                roomId: String?,
                userId: String?,
                serverUntilId: String?,
                currentResults: List<ChatMessage>,
                currentConversationMessages: List<ChatMessage>,
            ): ChatMessageRepositoryResult {
                if (throwOnSearchMessages) throw IllegalStateException("聊天搜索异常")
                return searchMessagesResultProvider?.invoke() ?: searchMessagesResult
            }

            override suspend fun showRoom(roomId: String): ChatRoomMutationRepositoryResult {
                onShowRoom(roomId)
                return showRoomResult
            }

            override suspend fun joinRoom(roomId: String): ChatRoomMutationRepositoryResult {
                onJoinRoom(roomId)
                return joinRoomResult
            }

            override suspend fun createRoom(name: String, description: String, joinMode: String): ChatRoomMutationRepositoryResult {
                return createRoomResult
            }

            override suspend fun updateRoom(
                roomId: String,
                name: String?,
                description: String?,
                joinMode: String?,
            ): ChatRoomMutationRepositoryResult {
                return updateRoomResult
            }

            override suspend fun updateRoomManagement(
                roomId: String,
                messageRetentionDays: Int?,
            ): ChatRoomMutationRepositoryResult {
                return updateRoomManagementResult
            }

            override suspend fun deleteAllRoomMessages(roomId: String): ChatRoomMutationRepositoryResult {
                onClearRoomMessages(roomId)
                return ChatRoomMutationRepositoryResult.RoomMessagesCleared(roomId)
            }

            override suspend fun leaveRoom(roomId: String): ChatRoomMutationRepositoryResult {
                onLeaveRoom(roomId)
                return leaveRoomResult
            }

            override suspend fun deleteRoom(roomId: String): ChatRoomMutationRepositoryResult {
                onDeleteRoom(roomId)
                return deleteRoomResult
            }

            override suspend fun muteRoom(roomId: String, muted: Boolean): ChatRoomMutationRepositoryResult {
                onMuteRoom(roomId, muted)
                return muteRoomResult
            }

            override suspend fun sendMessage(
                roomId: String,
                text: String,
                fileId: String?,
                fileIds: List<String>,
                replyId: String?,
                quoteId: String?,
            ): ChatMessageRepositoryResult {
                onSend(roomId, text, fileIds + listOfNotNull(fileId), replyId, quoteId)
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

            override suspend fun deleteMessage(
                messageId: String,
                roomId: String?,
                userId: String?,
            ): ChatMessageRepositoryResult {
                onDelete(messageId)
                return deleteMessageResult
            }

            override suspend fun refreshMembers(roomId: String): ChatRoomMemberRepositoryResult {
                onRefreshMembers(roomId)
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

                override fun streamUserMessages(
                    token: String,
                    userId: String,
                ): Flow<ChatStreamingEvent> = flow
            },
        ) {
            override fun streamRoomMessages(roomId: String): Flow<ChatStreamingEvent> = flow

            override fun streamUserMessages(userId: String): Flow<ChatStreamingEvent> = flow
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

                override suspend fun loadFileDetails(
                    token: String,
                    fileId: String,
                ): cc.hhhl.client.api.DriveFileDetailsResult {
                    return cc.hhhl.client.api.DriveFileDetailsResult.Success(
                        cc.hhhl.client.model.DriveFileDetails(sampleDriveFile()),
                    )
                }
            },
        ) {
            override suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult {
                return resultProvider()
            }
        }
    }

    private fun sampleRoom(
        id: String = "room-1",
        unreadCount: Int = 0,
        latestMessageAtLabel: String = "",
        latestMessageMarker: String = "",
    ): ChatRoom {
        return ChatRoom(
            id = id,
            membershipId = "membership-$id",
            name = "AGI 讨论",
            description = "聊 AGI",
            joinMode = "open",
            memberCount = 2,
            isMuted = false,
            owner = User("owner", "Owner", "owner", "O"),
            unreadCount = unreadCount,
            latestMessageAtLabel = latestMessageAtLabel,
            latestMessageMarker = latestMessageMarker,
        )
    }

    private fun sampleMessage(
        id: String,
        roomId: String = "room-1",
        text: String = "message $id",
        reactions: List<ChatMessageReaction> = emptyList(),
        createdAt: String = "",
        createdAtLabel: String = "2026-05-25 01:23",
    ): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = roomId,
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
        roomId: String = "room-1",
    ): ChatRoomMember {
        return ChatRoomMember(
            membershipId = membershipId,
            roomId = roomId,
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
        val fileIds: List<String>,
        val replyId: String?,
        val quoteId: String?,
    )
}
