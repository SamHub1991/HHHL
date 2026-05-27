package cc.hhhl.client.repository

import cc.hhhl.client.api.MainStreamingApi
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.api.TimelineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class MainStreamingRepositoryTest {
    @Test
    fun missingTokenReturnsUnauthorizedEventWithoutCallingApi() = runTest {
        var calls = 0
        val repository = MainStreamingRepository(
            tokenProvider = { null },
            api = object : MainStreamingApi {
                override fun streamMain(token: String): Flow<MainStreamingEvent> {
                    calls += 1
                    return flowOf(MainStreamingEvent.Connected)
                }
            },
        )

        val events = repository.streamMain().toList()

        assertEquals(listOf(MainStreamingEvent.Unauthorized, MainStreamingEvent.Closed), events)
        assertEquals(0, calls)
    }

    @Test
    fun validTokenDelegatesToApi() = runTest {
        var tokenSeen = ""
        val repository = MainStreamingRepository(
            tokenProvider = { "token-123" },
            api = object : MainStreamingApi {
                override fun streamMain(token: String): Flow<MainStreamingEvent> {
                    tokenSeen = token
                    return flowOf(MainStreamingEvent.NewChatMessage)
                }
            },
        )

        val events = repository.streamMain().toList()

        assertEquals("token-123", tokenSeen)
        assertEquals(listOf(MainStreamingEvent.NewChatMessage), events)
    }

    @Test
    fun timelineEventsAreExposedToAppShell() = runTest {
        val repository = MainStreamingRepository(
            tokenProvider = { "token-123" },
            api = object : MainStreamingApi {
                override fun streamMain(token: String): Flow<MainStreamingEvent> {
                    return flowOf(MainStreamingEvent.TimelineNote(TimelineKind.Global))
                }
            },
        )

        val events = repository.streamMain().toList()

        assertEquals(listOf(MainStreamingEvent.TimelineNote(TimelineKind.Global)), events)
    }
}
