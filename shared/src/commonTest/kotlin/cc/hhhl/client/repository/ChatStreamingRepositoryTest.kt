package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatStreamingApi
import cc.hhhl.client.api.ChatStreamingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ChatStreamingRepositoryTest {
    @Test
    fun missingTokenReturnsUnauthorizedEventWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ChatStreamingRepository(
            tokenProvider = { null },
            api = object : ChatStreamingApi {
                override fun streamRoomMessages(
                    token: String,
                    roomId: String,
                ): Flow<ChatStreamingEvent> {
                    calls += 1
                    return flowOf(ChatStreamingEvent.Connected)
                }
            },
        )

        val events = repository.streamRoomMessages("room-1").toList()

        assertEquals(listOf(ChatStreamingEvent.Unauthorized, ChatStreamingEvent.Closed), events)
        assertEquals(0, calls)
    }

    @Test
    fun blankRoomIdReturnsUserFacingErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ChatStreamingRepository(
            tokenProvider = { "token-123" },
            api = object : ChatStreamingApi {
                override fun streamRoomMessages(
                    token: String,
                    roomId: String,
                ): Flow<ChatStreamingEvent> {
                    calls += 1
                    return flowOf(ChatStreamingEvent.Connected)
                }
            },
        )

        val events = repository.streamRoomMessages(" ").toList()

        assertEquals(listOf(ChatStreamingEvent.Error("请选择聊天室"), ChatStreamingEvent.Closed), events)
        assertEquals(0, calls)
    }
}
