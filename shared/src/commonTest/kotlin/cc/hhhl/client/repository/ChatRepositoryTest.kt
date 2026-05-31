package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatApi
import cc.hhhl.client.api.ChatMessageCreateResult
import cc.hhhl.client.api.ChatMessageDeleteResult
import cc.hhhl.client.api.ChatMessageLoadResult
import cc.hhhl.client.api.ChatMessageReactionResult
import cc.hhhl.client.api.ChatRoomActionResult
import cc.hhhl.client.api.ChatRoomInvitationLoadResult
import cc.hhhl.client.api.ChatRoomMemberLoadResult
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.ChatRoomLoadResult
import cc.hhhl.client.api.ChatRoomMutationResult
import cc.hhhl.client.api.ChatUserHistoryLoadResult
import cc.hhhl.client.cache.InMemoryChatMessageCache
import cc.hhhl.client.cache.ChatMessageCacheConversationType
import cc.hhhl.client.cache.ChatMessageCacheKey
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX
import cc.hhhl.client.model.ChatMessageReaction
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.ChatRoomInvitation
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ChatRepositoryTest {
    @Test
    fun refreshLoadsRoomsWithToken() = runTest {
        val calls = mutableListOf<ApiCall>()
        val room = sampleRoom("room-1", "membership-1")
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ChatRoomLoadResult.Success(listOf(room)),
            ),
        )

        val result = repository.refresh()

        assertIs<ChatRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", null)), calls)
        assertEquals(listOf(room), result.rooms)
    }

    @Test
    fun loadMoreUsesLastMembershipIdAndDeduplicates() = runTest {
        val first = sampleRoom("room-1", "membership-1")
        val second = sampleRoom("room-2", "membership-2")
        val calls = mutableListOf<ApiCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ChatRoomLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMore(listOf(first))

        assertIs<ChatRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", "membership-1")), calls)
        assertEquals(listOf(first, second), result.rooms)
    }

    @Test
    fun emptyLoadMoreMarksEndReached() = runTest {
        val first = sampleRoom("room-1", "membership-1")
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(result = ChatRoomLoadResult.Success(emptyList())),
        )

        val result = repository.loadMore(listOf(first))

        assertIs<ChatRepositoryResult.Success>(result)
        assertEquals(listOf(first), result.rooms)
        assertTrue(result.endReached)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ChatRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        assertIs<ChatRepositoryResult.Unauthorized>(repository.refresh())
        assertEquals(0, calls)
    }

    @Test
    fun missingChatPermissionShowsErrorWithoutReloginForRoomList() = runTest {
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = ChatRoomLoadResult.ServerError(
                    statusCode = 403,
                    message = "当前登录缺少此功能权限，请检查应用授权或账号权限",
                ),
            ),
        )

        val result = repository.refresh()

        assertIs<ChatRepositoryResult.Error>(result)
        assertEquals("当前登录缺少此功能权限，请检查应用授权或账号权限", result.message)
    }

    @Test
    fun loadRoomMessagesUsesRoomTimelineEndpoint() = runTest {
        val calls = mutableListOf<MessageCall>()
        val message = sampleMessage("message-1")
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageCalls = calls,
                messageResult = ChatMessageLoadResult.Success(listOf(message)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(listOf(MessageCall("token-123", "room-1", null)), calls)
        assertEquals(listOf(message), result.messages)
    }

    @Test
    fun loadMoreRoomMessagesUsesLastMessageIdAndDeduplicates() = runTest {
        val first = sampleMessage("message-1")
        val second = sampleMessage("message-2")
        val calls = mutableListOf<MessageCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageCalls = calls,
                messageResult = ChatMessageLoadResult.Success(listOf(second, first)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.loadMoreMessages("room-1", listOf(first))

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(listOf(MessageCall("token-123", "room-1", "message-1")), calls)
        assertEquals(listOf(first, second), result.messages)
    }

    @Test
    fun loadRoomMessagesAssignsStableIdsForBlankMessageIds() = runTest {
        val first = sampleMessage(
            id = "",
            text = "first blank id",
            createdAt = "2026-05-25T01:00:00.000Z",
        )
        val second = sampleMessage(
            id = "",
            text = "second blank id",
            createdAt = "2026-05-25T02:00:00.000Z",
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageResult = ChatMessageLoadResult.Success(listOf(second, first)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(2, result.messages.size)
        assertTrue(result.messages.all { it.id.startsWith("local-chat-") })
        assertEquals(2, result.messages.map { it.id }.toSet().size)
        assertEquals(listOf("first blank id", "second blank id"), result.messages.map { it.text })
    }

    @Test
    fun loadRoomMessagesDeduplicatesRepeatedServerMessageIds() = runTest {
        val duplicateOld = sampleMessage(
            id = "message-dup",
            text = "old duplicate",
            createdAt = "2026-05-25T01:00:00.000Z",
        )
        val duplicateNew = sampleMessage(
            id = "message-dup",
            text = "new duplicate",
            createdAt = "2026-05-25T02:00:00.000Z",
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageResult = ChatMessageLoadResult.Success(listOf(duplicateNew, duplicateOld)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(listOf("message-dup"), result.messages.map { it.id })
    }

    @Test
    fun loadRoomMessagesSortsByApiTimestampNotDisplayLabel() = runTest {
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
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageResult = ChatMessageLoadResult.Success(listOf(newer, older)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(listOf("message-older", "message-newer"), result.messages.map { it.id })
    }

    @Test
    fun refreshRoomMessagesWritesAndRestoresCache() = runTest {
        val message = sampleMessage("message-cached")
        val cache = InMemoryChatMessageCache()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                messageResult = ChatMessageLoadResult.Success(listOf(message)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        assertIs<ChatMessageRepositoryResult.Success>(repository.refreshMessages("room-1"))
        val cached = repository.restoreCachedMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(listOf(message), cached.messages)
    }

    @Test
    fun restoreCachedRoomMessagesCapsInitialPayload() = runTest {
        val messages = (1..260).map { index ->
            sampleMessage(
                id = "message-${index.paddedId()}",
                createdAtLabel = index.paddedId(),
            )
        }
        val cache = InMemoryChatMessageCache()
        cache.write(
            ChatMessageCacheKey(
                accountId = "account-1",
                type = ChatMessageCacheConversationType.Room,
                conversationId = "room-1",
            ),
            messages,
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(result = ChatRoomLoadResult.Success(emptyList())),
        )

        val cached = repository.restoreCachedMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(240, cached.messages.size)
        assertEquals("message-021", cached.messages.first().id)
        assertEquals("message-260", cached.messages.last().id)
    }

    @Test
    fun refreshRoomMessagesBridgesGapToCachedHistory() = runTest {
        val cachedOldest = sampleMessage("message-001", createdAtLabel = "001")
        val bridgeSecond = sampleMessage("message-002", createdAtLabel = "002")
        val bridgeThird = sampleMessage("message-003", createdAtLabel = "003")
        val bridgeFourth = sampleMessage("message-004", createdAtLabel = "004")
        val latestOldest = sampleMessage("message-005", createdAtLabel = "005")
        val latestNewest = sampleMessage("message-006", createdAtLabel = "006")
        val calls = mutableListOf<MessageCall>()
        val cache = InMemoryChatMessageCache()
        cache.write(
            ChatMessageCacheKey(
                accountId = "account-1",
                type = ChatMessageCacheConversationType.Room,
                conversationId = "room-1",
            ),
            listOf(cachedOldest),
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                messageCalls = calls,
                messageResultProvider = { untilId ->
                    when (untilId) {
                        null -> ChatMessageLoadResult.Success(listOf(latestNewest, latestOldest))
                        "message-005" -> ChatMessageLoadResult.Success(listOf(bridgeFourth, bridgeThird))
                        "message-003" -> ChatMessageLoadResult.Success(listOf(bridgeSecond, cachedOldest))
                        else -> ChatMessageLoadResult.Success(emptyList())
                    }
                },
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(
            listOf("message-001", "message-002", "message-003", "message-004", "message-005", "message-006"),
            result.messages.map { it.id },
        )
        assertEquals(listOf(null, "message-005", "message-003"), calls.map { it.untilId })
        val cached = repository.restoreCachedMessages("room-1")
        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(result.messages.map { it.id }, cached.messages.map { it.id })
    }

    @Test
    fun loadMoreRoomMessagesKeepsFillingUnbridgedRefreshUntilCachedHistoryOverlaps() = runTest {
        val cachedOldest = sampleMessage("message-001", createdAtLabel = "001")
        val calls = mutableListOf<MessageCall>()
        val cache = InMemoryChatMessageCache()
        cache.write(
            ChatMessageCacheKey(
                accountId = "account-1",
                type = ChatMessageCacheConversationType.Room,
                conversationId = "room-1",
            ),
            listOf(cachedOldest),
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                messageCalls = calls,
                messageResultProvider = { untilId ->
                    when (untilId) {
                        null -> ChatMessageLoadResult.Success(
                            listOf(
                                sampleMessage("message-010", createdAtLabel = "010"),
                                sampleMessage("message-009", createdAtLabel = "009"),
                            ),
                        )
                        "message-009" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-008", createdAtLabel = "008")))
                        "message-008" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-007", createdAtLabel = "007")))
                        "message-007" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-006", createdAtLabel = "006")))
                        "message-006" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-005", createdAtLabel = "005")))
                        "message-005" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-004", createdAtLabel = "004")))
                        "message-004" -> ChatMessageLoadResult.Success(listOf(sampleMessage("message-003", createdAtLabel = "003")))
                        "message-003" -> ChatMessageLoadResult.Success(
                            listOf(
                                sampleMessage("message-002", createdAtLabel = "002"),
                                cachedOldest,
                            ),
                        )
                        else -> ChatMessageLoadResult.Success(emptyList())
                    }
                },
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val refreshed = repository.refreshMessages("room-1")
        assertIs<ChatMessageRepositoryResult.Success>(refreshed)
        assertEquals("message-003", refreshed.messages.first().id)
        assertEquals(listOf("message-001"), (repository.restoreCachedMessages("room-1") as ChatMessageRepositoryResult.Success).messages.map { it.id })

        val loadedMore = repository.loadMoreMessages("room-1", refreshed.messages)

        assertIs<ChatMessageRepositoryResult.Success>(loadedMore)
        assertEquals(
            (1..10).map { "message-${it.paddedId()}" },
            loadedMore.messages.map { it.id },
        )
        val cached = repository.restoreCachedMessages("room-1")
        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(loadedMore.messages.map { it.id }, cached.messages.map { it.id })
        assertEquals(listOf(null, "message-009", "message-008", "message-007", "message-006", "message-005", "message-004", "message-003"), calls.map { it.untilId })
    }

    @Test
    fun refreshUserMessagesWritesAndRestoresCache() = runTest {
        val message = sampleMessage("message-user", roomId = "")
        val calls = mutableListOf<UserMessageCall>()
        val cache = InMemoryChatMessageCache()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                userMessageCalls = calls,
                userMessageResult = ChatMessageLoadResult.Success(listOf(message)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        assertIs<ChatMessageRepositoryResult.Success>(repository.refreshUserMessages("user-2"))
        val cached = repository.restoreCachedUserMessages("user-2")

        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(listOf(UserMessageCall("token-123", "user-2", null)), calls)
        assertEquals(listOf(message), cached.messages)
    }

    @Test
    fun resolveRealtimeMessageRefreshesRoomMessagesWhenStreamingReferenceIsMissing() = runTest {
        val currentUser = User("account-user", "Me", "me", "M")
        val incoming = sampleMessage(
            id = "message-stream-room",
            roomId = "room-77",
            createdAt = "2026-05-25T03:00:00.000Z",
        ).copy(replyUnavailable = true)
        val resolved = incoming.copy(
            replyUnavailable = false,
            reply = ChatMessageReference(
                id = "message-prev",
                fromUser = currentUser,
                text = "previous",
            ),
        )
        val calls = mutableListOf<MessageCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageCalls = calls,
                messageResult = ChatMessageLoadResult.Success(listOf(resolved)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.resolveRealtimeMessage(incoming)

        assertEquals(listOf(MessageCall("token-123", "room-77", null)), calls)
        assertEquals(resolved, result)
    }

    @Test
    fun resolveRealtimeMessageRefreshesUserMessagesWhenStreamingReferenceIsMissing() = runTest {
        val currentUser = User("account-user", "Me", "me", "M")
        val peer = User("user-2", "Bob", "bob", "B")
        val incoming = sampleMessage(
            id = "message-stream-user",
            roomId = "",
            createdAt = "2026-05-25T03:00:00.000Z",
        ).copy(
            fromUser = peer,
            toUserId = currentUser.id,
            toUser = currentUser,
            quoteUnavailable = true,
        )
        val resolved = incoming.copy(
            quoteUnavailable = false,
            quote = ChatMessageReference(
                id = "message-prev",
                fromUser = currentUser,
                text = "previous",
            ),
        )
        val calls = mutableListOf<UserMessageCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                userMessageCalls = calls,
                userMessageResult = ChatMessageLoadResult.Success(listOf(resolved)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.resolveRealtimeMessage(incoming, directUserId = peer.id)

        assertEquals(listOf(UserMessageCall("token-123", "user-2", null)), calls)
        assertEquals(resolved, result)
    }

    @Test
    fun restoreCachedUserMessagesCapsInitialPayload() = runTest {
        val messages = (1..260).map { index ->
            sampleMessage(
                id = "user-message-${index.paddedId()}",
                createdAtLabel = index.paddedId(),
                roomId = "",
            )
        }
        val cache = InMemoryChatMessageCache()
        cache.write(
            ChatMessageCacheKey(
                accountId = "account-1",
                type = ChatMessageCacheConversationType.User,
                conversationId = "user-2",
            ),
            messages,
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(result = ChatRoomLoadResult.Success(emptyList())),
        )

        val cached = repository.restoreCachedUserMessages("user-2")

        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(240, cached.messages.size)
        assertEquals("user-message-021", cached.messages.first().id)
        assertEquals("user-message-260", cached.messages.last().id)
    }

    @Test
    fun refreshUserConversationsWritesLatestMessagesToUserCache() = runTest {
        val peer = User("user-2", "Bob", "bob", "B")
        val message = sampleMessage("message-user-history", roomId = "").copy(
            fromUser = User("account-user", "Alice", "alice", "A"),
            toUserId = peer.id,
            toUser = peer,
        )
        val cache = InMemoryChatMessageCache()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-user" },
            cacheAccountIdProvider = { "account-session" },
            messageCache = cache,
            api = fakeApi(
                userHistoryResult = ChatUserHistoryLoadResult.Success(listOf(message)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        assertIs<ChatUserConversationRepositoryResult.Success>(repository.refreshUserConversations())
        val cached = repository.restoreCachedUserMessages(peer.id)

        assertIs<ChatMessageRepositoryResult.Success>(cached)
        assertEquals(listOf(message), cached.messages)
    }

    @Test
    fun refreshUserConversationsCountsUnreadMessagesPerPeer() = runTest {
        val currentUser = User("account-user", "Alice", "alice", "A")
        val peer = User("user-2", "Bob", "bob", "B")
        val latestUnread = sampleMessage(
            id = "message-latest",
            roomId = "",
            createdAt = "2026-05-25T03:00:00.000Z",
        ).copy(
            fromUser = peer,
            toUserId = currentUser.id,
            toUser = currentUser,
            isRead = false,
        )
        val olderUnread = sampleMessage(
            id = "message-older",
            roomId = "",
            createdAt = "2026-05-25T02:00:00.000Z",
        ).copy(
            fromUser = peer,
            toUserId = currentUser.id,
            toUser = currentUser,
            isRead = false,
        )
        val ownMessage = sampleMessage(
            id = "message-own",
            roomId = "",
            createdAt = "2026-05-25T01:00:00.000Z",
        ).copy(
            fromUser = currentUser,
            toUserId = peer.id,
            toUser = peer,
            isRead = false,
        )
        val userHistoryCalls = mutableListOf<UserHistoryCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { currentUser.id },
            api = fakeApi(
                userHistoryCalls = userHistoryCalls,
                userHistoryResult = ChatUserHistoryLoadResult.Success(
                    listOf(ownMessage, olderUnread, latestUnread),
                ),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshUserConversations()

        assertIs<ChatUserConversationRepositoryResult.Success>(result)
        assertEquals(listOf(UserHistoryCall("token-123", 100)), userHistoryCalls)
        assertEquals(1, result.conversations.size)
        assertEquals(latestUnread.id, result.conversations.single().latestMessage?.id)
        assertEquals(2, result.conversations.single().unreadCount)
    }

    @Test
    fun refreshLimitsRoomUnreadCountResolution() = runTest {
        val rooms = (1..12).map { index ->
            sampleRoom("room-$index", "membership-$index")
                .copy(unreadCount = 1, latestMessageMarker = "marker-$index")
        }
        val messageCalls = mutableListOf<MessageCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageCalls = messageCalls,
                messageResult = ChatMessageLoadResult.Success(
                    listOf(sampleMessage("unread-message").copy(isRead = false)),
                ),
                result = ChatRoomLoadResult.Success(rooms),
            ),
        )

        val result = repository.refresh()

        assertIs<ChatRepositoryResult.Success>(result)
        assertEquals(12, result.rooms.size)
        assertEquals((1..6).map { "room-$it" }, messageCalls.map { it.roomId })
    }

    @Test
    fun refreshUserConversationsLimitsUnreadCountResolution() = runTest {
        val currentUser = User("account-user", "Alice", "alice", "A")
        val history = (1..12).map { index ->
            val peer = User("peer-$index", "Peer $index", "peer$index", "P")
            sampleMessage(
                id = "history-${index.paddedId()}",
                roomId = "",
                createdAtLabel = index.paddedId(),
            ).copy(
                fromUser = peer,
                toUserId = currentUser.id,
                toUser = currentUser,
                isRead = false,
            )
        }
        val userMessageCalls = mutableListOf<UserMessageCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { currentUser.id },
            api = fakeApi(
                userMessageCalls = userMessageCalls,
                userHistoryResult = ChatUserHistoryLoadResult.Success(history),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshUserConversations()

        assertIs<ChatUserConversationRepositoryResult.Success>(result)
        assertEquals(12, result.conversations.size)
        assertEquals(6, userMessageCalls.size)
    }

    @Test
    fun searchMessagesUsesLoadedAndCachedMessagesWithoutFullHistoryIndex() = runTest {
        val latest = sampleMessage("message-latest", text = "latest", createdAt = "2026-05-25T03:00:00.000Z")
        val cached = sampleMessage("message-cached", text = "needle cached", createdAt = "2026-05-25T02:00:00.000Z")
        val oldest = sampleMessage("message-oldest", text = "needle oldest", createdAt = "2026-05-25T01:00:00.000Z")
        val calls = mutableListOf<MessageCall>()
        val cache = InMemoryChatMessageCache()
        cache.write(
            cc.hhhl.client.cache.ChatMessageCacheKey(
                accountId = "account-1",
                type = cc.hhhl.client.cache.ChatMessageCacheConversationType.Room,
                conversationId = "room-1",
            ),
            listOf(cached),
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            cacheAccountIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                messageCalls = calls,
                messageResultProvider = { untilId ->
                    when (untilId) {
                        "message-latest" -> ChatMessageLoadResult.Success(listOf(oldest))
                        else -> ChatMessageLoadResult.Success(emptyList())
                    }
                },
                searchResult = ChatMessageLoadResult.Success(listOf(oldest)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.searchMessages(
            query = "needle",
            roomId = "room-1",
            currentConversationMessages = listOf(latest),
        )

        assertIs<ChatMessageRepositoryResult.Success>(result)
        assertEquals(listOf("message-oldest", "message-cached"), result.messages.map { it.id })
        assertEquals(emptyList(), calls)
        val restored = repository.restoreCachedMessages("room-1")
        assertIs<ChatMessageRepositoryResult.Success>(restored)
        assertEquals(listOf("message-cached", "message-latest"), restored.messages.map { it.id })
    }

    @Test
    fun loadMoreSearchUsesServerCursorInsteadOfCachedOldestResult() = runTest {
        val serverFirst = sampleMessage("message-server-1", text = "needle server 1", createdAt = "2026-05-25T03:00:00.000Z")
        val cachedOlder = sampleMessage("message-cached-old", text = "needle cached", createdAt = "2026-05-25T01:00:00.000Z")
        val serverSecond = sampleMessage("message-server-2", text = "needle server 2", createdAt = "2026-05-25T02:00:00.000Z")
        val searchCalls = mutableListOf<SearchCall>()
        val cache = InMemoryChatMessageCache()
        cache.write(
            cc.hhhl.client.cache.ChatMessageCacheKey(
                accountId = "account-1",
                type = cc.hhhl.client.cache.ChatMessageCacheConversationType.Room,
                conversationId = "room-1",
            ),
            listOf(cachedOlder),
        )
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            currentUserIdProvider = { "account-1" },
            cacheAccountIdProvider = { "account-1" },
            messageCache = cache,
            api = fakeApi(
                searchCalls = searchCalls,
                searchResultProvider = { untilId ->
                    when (untilId) {
                        null -> ChatMessageLoadResult.Success(listOf(serverFirst))
                        "message-server-1" -> ChatMessageLoadResult.Success(listOf(serverSecond))
                        else -> ChatMessageLoadResult.Success(emptyList())
                    }
                },
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val firstPage = repository.searchMessages(query = "needle", roomId = "room-1")
        assertIs<ChatMessageRepositoryResult.Success>(firstPage)
        val secondPage = repository.searchMessages(
            query = "needle",
            roomId = "room-1",
            serverUntilId = firstPage.nextUntilId,
            currentResults = firstPage.messages,
        )

        assertIs<ChatMessageRepositoryResult.Success>(secondPage)
        assertEquals(listOf(null, "message-server-1"), searchCalls.map { it.untilId })
        assertEquals(
            listOf("message-cached-old", "message-server-2", "message-server-1"),
            secondPage.messages.map { it.id },
        )
    }

    @Test
    fun missingChatPermissionShowsErrorWithoutReloginForMessages() = runTest {
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                messageResult = ChatMessageLoadResult.ServerError(
                    statusCode = 403,
                    message = "Your app does not have the necessary permissions to use this endpoint.",
                ),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMessages("room-1")

        assertIs<ChatMessageRepositoryResult.Error>(result)
        assertEquals("Your app does not have the necessary permissions to use this endpoint.", result.message)
    }

    @Test
    fun sendRoomMessageCreatesMessage() = runTest {
        val created = sampleMessage("message-created", text = "你好")
        val calls = mutableListOf<CreateCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                createCalls = calls,
                createResult = ChatMessageCreateResult.Success(created),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.sendMessage("room-1", "  你好  ")

        assertIs<ChatMessageRepositoryResult.Created>(result)
        assertEquals(listOf(CreateCall("token-123", "room-1", "你好", null, emptyList())), calls)
        assertEquals(created, result.message)
    }

    @Test
    fun sendRoomMessagePassesFileIdWhenProvided() = runTest {
        val created = sampleMessage("message-created", text = "配图")
        val calls = mutableListOf<CreateCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                createCalls = calls,
                createResult = ChatMessageCreateResult.Success(created),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.sendMessage("room-1", "配图", fileId = "file-1")

        assertIs<ChatMessageRepositoryResult.Created>(result)
        assertEquals(listOf(CreateCall("token-123", "room-1", "配图", "file-1", listOf("file-1"))), calls)
    }

    @Test
    fun sendRoomMessagePassesMultipleFileIdsWhenProvided() = runTest {
        val created = sampleMessage("message-created", text = "多图")
        val calls = mutableListOf<CreateCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                createCalls = calls,
                createResult = ChatMessageCreateResult.Success(created),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.sendMessage(
            roomId = "room-1",
            text = "多图",
            fileIds = listOf("file-1", " file-2 ", "file-1"),
        )

        assertIs<ChatMessageRepositoryResult.Created>(result)
        assertEquals(
            listOf(CreateCall("token-123", "room-1", "多图", "file-1", listOf("file-1", "file-2"))),
            calls,
        )
    }

    @Test
    fun fileOnlyRoomMessageIsAllowed() = runTest {
        val created = sampleMessage("message-created", text = "")
        val calls = mutableListOf<CreateCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                createCalls = calls,
                createResult = ChatMessageCreateResult.Success(created),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.sendMessage("room-1", "   ", fileId = "file-1")

        assertIs<ChatMessageRepositoryResult.Created>(result)
        assertEquals(listOf(CreateCall("token-123", "room-1", "", "file-1", listOf("file-1"))), calls)
    }

    @Test
    fun reactToMessageCallsApiWithToken() = runTest {
        val calls = mutableListOf<ReactionCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                reactionCalls = calls,
                reactionResult = ChatMessageReactionResult.Success,
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.reactToMessage("message-1", "❤️")

        assertIs<ChatMessageRepositoryResult.ReactionUpdated>(result)
        assertEquals(listOf(ReactionCall("react", "token-123", "message-1", "❤️")), calls)
    }

    @Test
    fun unreactToMessageCallsApiWithToken() = runTest {
        val calls = mutableListOf<ReactionCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                reactionCalls = calls,
                reactionResult = ChatMessageReactionResult.Success,
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.unreactToMessage("message-1", "❤️")

        assertIs<ChatMessageRepositoryResult.ReactionUpdated>(result)
        assertEquals(listOf(ReactionCall("unreact", "token-123", "message-1", "❤️")), calls)
    }

    @Test
    fun deleteMessageCallsApiWithTokenAndServerMessageId() = runTest {
        val calls = mutableListOf<DeleteCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteCalls = calls,
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.deleteMessage(
            messageId = " message-1 ",
            roomId = "room-1",
        )

        assertEquals(ChatMessageRepositoryResult.Deleted("message-1"), result)
        assertEquals(listOf(DeleteCall("token-123", "message-1")), calls)
    }

    @Test
    fun deleteMessageRejectsSyntheticLocalIdWithoutCallingApi() = runTest {
        val calls = mutableListOf<DeleteCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteCalls = calls,
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.deleteMessage(
            messageId = "local-chat-fallback",
            roomId = "room-1",
        )

        val error = assertIs<ChatMessageRepositoryResult.Error>(result)
        assertEquals("这条消息还没有服务器 ID，无法同步删除", error.message)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun refreshMembersLoadsWithTokenAndRoomId() = runTest {
        val calls = mutableListOf<MemberCall>()
        val member = sampleMember("membership-member-1")
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                memberCalls = calls,
                memberResult = ChatRoomMemberLoadResult.Success(listOf(member)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refreshMembers("room-1")

        assertIs<ChatRoomMemberRepositoryResult.Success>(result)
        assertEquals(listOf(MemberCall("token-123", "room-1", null)), calls)
        assertEquals(listOf(member), result.members)
    }

    @Test
    fun loadMoreMembersUsesLastMembershipIdAndDeduplicates() = runTest {
        val first = sampleMember("membership-member-1")
        val second = sampleMember("membership-member-2", userId = "user-2")
        val calls = mutableListOf<MemberCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                memberCalls = calls,
                memberResult = ChatRoomMemberLoadResult.Success(listOf(second, first)),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.loadMoreMembers("room-1", listOf(first))

        assertIs<ChatRoomMemberRepositoryResult.Success>(result)
        assertEquals(listOf(MemberCall("token-123", "room-1", "membership-member-1")), calls)
        assertEquals(listOf(first, second), result.members)
    }

    @Test
    fun loadMoreMembersIgnoresInferredActiveMemberCursor() = runTest {
        val first = sampleMember("membership-member-1")
        val active = sampleMember(
            membershipId = "${CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX}room-1:user-active",
            userId = "user-active",
        )
        val calls = mutableListOf<MemberCall>()
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                memberCalls = calls,
                memberResult = ChatRoomMemberLoadResult.Success(emptyList()),
                result = ChatRoomLoadResult.Success(emptyList()),
            ),
        )

        repository.loadMoreMembers("room-1", listOf(first, active))

        assertEquals(listOf(MemberCall("token-123", "room-1", "membership-member-1")), calls)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingMemberApi() = runTest {
        var calls = 0
        val repository = ChatRepository(
            tokenProvider = { "" },
            api = fakeApi(
                memberCalls = mutableListOf(),
                memberResult = ChatRoomMemberLoadResult.Success(emptyList()),
                result = ChatRoomLoadResult.Success(emptyList()),
                onMemberCall = { calls += 1 },
            ),
        )

        assertIs<ChatRoomMemberRepositoryResult.Unauthorized>(repository.refreshMembers("room-1"))
        assertEquals(0, calls)
    }

    @Test
    fun refreshInvitationOutboxLoadsOwnedRoomsThenAggregatesInvitationsByRoom() = runTest {
        val ownedRoomCalls = mutableListOf<String>()
        val outboxCalls = mutableListOf<String>()
        val roomA = sampleRoom("room-a", "membership-a")
        val roomB = sampleRoom("room-b", "membership-b")
        val repository = ChatRepository(
            tokenProvider = { "token-123" },
            api = object : ChatApi {
                override suspend fun loadJoiningRooms(token: String, limit: Int, untilId: String?) =
                    ChatRoomLoadResult.Success(emptyList())

                override suspend fun loadOwnedRooms(token: String, limit: Int, untilId: String?): ChatRoomLoadResult {
                    ownedRoomCalls += token
                    return ChatRoomLoadResult.Success(listOf(roomA, roomB))
                }

                override suspend fun loadInvitationInbox(token: String, limit: Int, untilId: String?) =
                    ChatRoomInvitationLoadResult.Success(emptyList())

                override suspend fun loadInvitationOutbox(
                    token: String,
                    roomId: String,
                    limit: Int,
                    untilId: String?,
                ): ChatRoomInvitationLoadResult {
                    outboxCalls += roomId
                    return ChatRoomInvitationLoadResult.Success(
                        listOf(
                            ChatRoomInvitation(
                                id = "invite-$roomId",
                                room = sampleRoom(roomId, roomId),
                                inviter = User("user-$roomId", "User $roomId", "user$roomId", "U"),
                            ),
                        ),
                    )
                }

                override suspend fun loadRoomMessages(token: String, roomId: String, limit: Int, untilId: String?) =
                    ChatMessageLoadResult.Success(emptyList())
                override suspend fun loadRoomMembers(token: String, roomId: String, limit: Int, untilId: String?) =
                    ChatRoomMemberLoadResult.Success(emptyList())
                override suspend fun showRoom(token: String, roomId: String) =
                    ChatRoomMutationResult.Success(sampleRoom(roomId, roomId))
                override suspend fun loadUserHistory(token: String, limit: Int) =
                    ChatUserHistoryLoadResult.Success(emptyList())
                override suspend fun loadUserMessages(token: String, userId: String, limit: Int, untilId: String?) =
                    ChatMessageLoadResult.Success(emptyList())
                override suspend fun searchMessages(token: String, query: String, limit: Int, untilId: String?, roomId: String?, userId: String?) =
                    ChatMessageLoadResult.Success(emptyList())
                override suspend fun createRoom(token: String, name: String, description: String, joinMode: String) =
                    ChatRoomMutationResult.Success(sampleRoom("room-created", "membership-created"))
                override suspend fun createRoomMessage(token: String, roomId: String, text: String, fileId: String?, fileIds: List<String>, replyId: String?, quoteId: String?) =
                    ChatMessageCreateResult.Success(sampleMessage("created"))
                override suspend fun createUserMessage(token: String, userId: String, text: String, fileId: String?, replyId: String?, quoteId: String?) =
                    ChatMessageCreateResult.Success(sampleMessage("created"))
                override suspend fun reactToMessage(token: String, messageId: String, reaction: String) =
                    ChatMessageReactionResult.Success
                override suspend fun unreactToMessage(token: String, messageId: String, reaction: String) =
                    ChatMessageReactionResult.Success
                override suspend fun deleteMessage(token: String, messageId: String) =
                    ChatMessageDeleteResult.Success
                override suspend fun updateRoom(token: String, roomId: String, name: String?, description: String?, joinMode: String?) =
                    ChatRoomMutationResult.Success(sampleRoom(roomId, roomId))
                override suspend fun updateRoomManagement(token: String, roomId: String, messageRetentionDays: Int?) =
                    ChatRoomMutationResult.Success(sampleRoom(roomId, roomId).copy(messageRetentionDays = messageRetentionDays))
                override suspend fun inviteRoomMember(token: String, roomId: String, userId: String) =
                    ChatRoomActionResult.Success
                override suspend fun joinRoom(token: String, roomId: String) = ChatRoomActionResult.Success
                override suspend fun leaveRoom(token: String, roomId: String) = ChatRoomActionResult.Success
                override suspend fun deleteRoom(token: String, roomId: String) = ChatRoomActionResult.Success
                override suspend fun deleteAllRoomMessages(token: String, roomId: String) = ChatRoomActionResult.Success
                override suspend fun muteRoom(token: String, roomId: String, muted: Boolean) = ChatRoomActionResult.Success
                override suspend fun ignoreRoomInvitation(token: String, roomId: String) = ChatRoomActionResult.Success
            },
        )

        val result = repository.refreshInvitationOutbox()

        assertIs<ChatRoomInvitationRepositoryResult.Success>(result)
        assertEquals(listOf("token-123"), ownedRoomCalls)
        assertEquals(listOf("room-a", "room-b"), outboxCalls)
        assertEquals(listOf("invite-room-a", "invite-room-b"), result.invitations.map { it.id })
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        messageCalls: MutableList<MessageCall> = mutableListOf(),
        userMessageCalls: MutableList<UserMessageCall> = mutableListOf(),
        userHistoryCalls: MutableList<UserHistoryCall> = mutableListOf(),
        createCalls: MutableList<CreateCall> = mutableListOf(),
        reactionCalls: MutableList<ReactionCall> = mutableListOf(),
        deleteCalls: MutableList<DeleteCall> = mutableListOf(),
        memberCalls: MutableList<MemberCall> = mutableListOf(),
        searchCalls: MutableList<SearchCall> = mutableListOf(),
        result: ChatRoomLoadResult,
        messageResult: ChatMessageLoadResult = ChatMessageLoadResult.Success(emptyList()),
        messageResultProvider: ((String?) -> ChatMessageLoadResult)? = null,
        userMessageResult: ChatMessageLoadResult = messageResult,
        userHistoryResult: ChatUserHistoryLoadResult = ChatUserHistoryLoadResult.Success(emptyList()),
        searchResult: ChatMessageLoadResult = messageResult,
        searchResultProvider: ((String?) -> ChatMessageLoadResult)? = null,
        createResult: ChatMessageCreateResult = ChatMessageCreateResult.Success(sampleMessage("created")),
        reactionResult: ChatMessageReactionResult = ChatMessageReactionResult.Success,
        deleteResult: ChatMessageDeleteResult = ChatMessageDeleteResult.Success,
        memberResult: ChatRoomMemberLoadResult = ChatRoomMemberLoadResult.Success(emptyList()),
        onCall: () -> Unit = {},
        onMemberCall: () -> Unit = {},
    ): ChatApi {
        return object : ChatApi {
            override suspend fun loadJoiningRooms(
                token: String,
                limit: Int,
                untilId: String?,
            ): ChatRoomLoadResult {
                onCall()
                calls.add(ApiCall(token, untilId))
                return result
            }

            override suspend fun loadOwnedRooms(
                token: String,
                limit: Int,
                untilId: String?,
            ): ChatRoomLoadResult = result

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
            ): ChatMessageLoadResult {
                messageCalls.add(MessageCall(token, roomId, untilId))
                return messageResultProvider?.invoke(untilId) ?: messageResult
            }

            override suspend fun loadUserMessages(
                token: String,
                userId: String,
                limit: Int,
                untilId: String?,
            ): ChatMessageLoadResult {
                userMessageCalls.add(UserMessageCall(token, userId, untilId))
                return userMessageResult
            }

            override suspend fun loadUserHistory(
                token: String,
                limit: Int,
            ): ChatUserHistoryLoadResult {
                userHistoryCalls.add(UserHistoryCall(token, limit))
                return userHistoryResult
            }

            override suspend fun searchMessages(
                token: String,
                query: String,
                limit: Int,
                untilId: String?,
                roomId: String?,
                userId: String?,
            ): ChatMessageLoadResult {
                searchCalls.add(SearchCall(token, query, untilId, roomId, userId))
                return searchResultProvider?.invoke(untilId) ?: searchResult
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
                createCalls.add(CreateCall(token, roomId, text, fileId, fileIds))
                return createResult
            }

            override suspend fun createUserMessage(
                token: String,
                userId: String,
                text: String,
                fileId: String?,
                replyId: String?,
                quoteId: String?,
            ): ChatMessageCreateResult = createResult

            override suspend fun deleteMessage(
                token: String,
                messageId: String,
            ): ChatMessageDeleteResult {
                deleteCalls.add(DeleteCall(token, messageId))
                return deleteResult
            }

            override suspend fun createRoom(
                token: String,
                name: String,
                description: String,
                joinMode: String,
            ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom("room-created", "membership-created"))

            override suspend fun showRoom(
                token: String,
                roomId: String,
            ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom(roomId, roomId))

            override suspend fun reactToMessage(
                token: String,
                messageId: String,
                reaction: String,
            ): ChatMessageReactionResult {
                reactionCalls.add(ReactionCall("react", token, messageId, reaction))
                return reactionResult
            }

            override suspend fun unreactToMessage(
                token: String,
                messageId: String,
                reaction: String,
            ): ChatMessageReactionResult {
                reactionCalls.add(ReactionCall("unreact", token, messageId, reaction))
                return reactionResult
            }

            override suspend fun updateRoom(
                token: String,
                roomId: String,
                name: String?,
                description: String?,
                joinMode: String?,
            ): ChatRoomMutationResult = ChatRoomMutationResult.Success(sampleRoom("room-updated", "membership-updated"))

            override suspend fun updateRoomManagement(
                token: String,
                roomId: String,
                messageRetentionDays: Int?,
            ): ChatRoomMutationResult = ChatRoomMutationResult.Success(
                sampleRoom(roomId, roomId).copy(messageRetentionDays = messageRetentionDays),
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

            override suspend fun loadRoomMembers(
                token: String,
                roomId: String,
                limit: Int,
                untilId: String?,
            ): ChatRoomMemberLoadResult {
                onMemberCall()
                memberCalls.add(MemberCall(token, roomId, untilId))
                return memberResult
            }
        }
    }

    private fun sampleRoom(
        id: String,
        membershipId: String,
    ): ChatRoom {
        return ChatRoom(
            id = id,
            membershipId = membershipId,
            name = "Room $id",
            description = "desc",
            joinMode = "open",
            memberCount = 2,
            isMuted = false,
            owner = User("owner", "Owner", "owner", "O"),
        )
    }

    private fun sampleMessage(
        id: String,
        text: String = "message $id",
        createdAt: String = "",
        createdAtLabel: String = "2026-05-25 01:23",
        roomId: String = "room-1",
    ): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = roomId,
            fromUser = User("user-1", "Alice", "alice", "A"),
            text = text,
            createdAtLabel = createdAtLabel,
            createdAt = createdAt,
            reactions = listOf(ChatMessageReaction("❤️", 1)),
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

    private fun Int.paddedId(): String = toString().padStart(3, '0')

    private data class ApiCall(
        val token: String,
        val untilId: String?,
    )

    private data class MessageCall(
        val token: String,
        val roomId: String,
        val untilId: String?,
    )

    private data class UserMessageCall(
        val token: String,
        val userId: String,
        val untilId: String?,
    )

    private data class UserHistoryCall(
        val token: String,
        val limit: Int,
    )

    private data class SearchCall(
        val token: String,
        val query: String,
        val untilId: String?,
        val roomId: String?,
        val userId: String?,
    )

    private data class CreateCall(
        val token: String,
        val roomId: String,
        val text: String,
        val fileId: String?,
        val fileIds: List<String>,
    )

    private data class ReactionCall(
        val kind: String,
        val token: String,
        val messageId: String,
        val reaction: String,
    )

    private data class DeleteCall(
        val token: String,
        val messageId: String,
    )

    private data class MemberCall(
        val token: String,
        val roomId: String,
        val untilId: String?,
    )
}
