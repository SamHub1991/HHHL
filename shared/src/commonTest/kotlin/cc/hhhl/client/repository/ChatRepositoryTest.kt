package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatApi
import cc.hhhl.client.api.ChatMessageCreateResult
import cc.hhhl.client.api.ChatMessageLoadResult
import cc.hhhl.client.api.ChatMessageReactionResult
import cc.hhhl.client.api.ChatRoomMemberLoadResult
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.ChatRoomLoadResult
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageReaction
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
                    message = "当前登录缺少此功能权限，请重新登录一次后再试",
                ),
            ),
        )

        val result = repository.refresh()

        assertIs<ChatRepositoryResult.Error>(result)
        assertEquals("当前登录缺少此功能权限，请重新登录一次后再试", result.message)
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
        assertEquals(listOf(CreateCall("token-123", "room-1", "你好", null)), calls)
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
        assertEquals(listOf(CreateCall("token-123", "room-1", "配图", "file-1")), calls)
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
        assertEquals(listOf(CreateCall("token-123", "room-1", "", "file-1")), calls)
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

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        messageCalls: MutableList<MessageCall> = mutableListOf(),
        createCalls: MutableList<CreateCall> = mutableListOf(),
        reactionCalls: MutableList<ReactionCall> = mutableListOf(),
        memberCalls: MutableList<MemberCall> = mutableListOf(),
        result: ChatRoomLoadResult,
        messageResult: ChatMessageLoadResult = ChatMessageLoadResult.Success(emptyList()),
        createResult: ChatMessageCreateResult = ChatMessageCreateResult.Success(sampleMessage("created")),
        reactionResult: ChatMessageReactionResult = ChatMessageReactionResult.Success,
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

            override suspend fun loadRoomMessages(
                token: String,
                roomId: String,
                limit: Int,
                untilId: String?,
            ): ChatMessageLoadResult {
                messageCalls.add(MessageCall(token, roomId, untilId))
                return messageResult
            }

            override suspend fun createRoomMessage(
                token: String,
                roomId: String,
                text: String,
                fileId: String?,
            ): ChatMessageCreateResult {
                createCalls.add(CreateCall(token, roomId, text, fileId))
                return createResult
            }

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
    ): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = "room-1",
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

    private data class ApiCall(
        val token: String,
        val untilId: String?,
    )

    private data class MessageCall(
        val token: String,
        val roomId: String,
        val untilId: String?,
    )

    private data class CreateCall(
        val token: String,
        val roomId: String,
        val text: String,
        val fileId: String?,
    )

    private data class ReactionCall(
        val kind: String,
        val token: String,
        val messageId: String,
        val reaction: String,
    )

    private data class MemberCall(
        val token: String,
        val roomId: String,
        val untilId: String?,
    )
}
