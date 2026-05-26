package cc.hhhl.client.repository

import cc.hhhl.client.api.TimelineApi
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.TimelineLoadResult
import cc.hhhl.client.cache.TimelineCache
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class TimelineRepositoryTest {
    @Test
    fun refreshLoadsTimelineWithTokenAndKind() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = TimelineRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = TimelineLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refresh(TimelineKind.Local)

        assertIs<TimelineRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall(TimelineKind.Local, "token-123", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreUsesLastNoteIdAsUntilIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val repository = TimelineRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = TimelineLoadResult.Success(listOf(first, second, first)),
            ),
        )

        val result = repository.loadMore(TimelineKind.Home, currentNotes = listOf(first))

        assertIs<TimelineRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = TimelineRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = TimelineLoadResult.Success(emptyList()),
            ),
        )

        val result = repository.refresh(TimelineKind.Home)

        assertIs<TimelineRepositoryResult.Unauthorized>(result)
        assertEquals(0, calls)
    }

    @Test
    fun restoreReadsNotesFromCache() = runTest {
        val cache = FakeTimelineCache(
            initial = mapOf(TimelineKind.Home to listOf(FakeData.timeline[0])),
        )
        val repository = TimelineRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(result = TimelineLoadResult.Success(emptyList())),
            cache = cache,
        )

        val result = repository.restore(TimelineKind.Home)

        assertIs<TimelineRepositoryResult.Success>(result)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun refreshWritesSuccessfulNotesToCache() = runTest {
        val cache = FakeTimelineCache()
        val notes = listOf(FakeData.timeline[0], FakeData.timeline[1])
        val repository = TimelineRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(result = TimelineLoadResult.Success(notes)),
            cache = cache,
        )

        repository.refresh(TimelineKind.Local)

        assertEquals(notes, cache.saved.getValue(TimelineKind.Local))
    }

    @Test
    fun loadMoreWritesMergedNotesToCache() = runTest {
        val cache = FakeTimelineCache()
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val repository = TimelineRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(result = TimelineLoadResult.Success(listOf(second))),
            cache = cache,
        )

        repository.loadMore(TimelineKind.Home, currentNotes = listOf(first))

        assertEquals(listOf(first, second), cache.saved.getValue(TimelineKind.Home))
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: TimelineLoadResult,
        onCall: () -> Unit = {},
    ): TimelineApi {
        return object : TimelineApi {
            override suspend fun loadTimeline(
                kind: TimelineKind,
                token: String,
                limit: Int,
                untilId: String?,
            ): TimelineLoadResult {
                onCall()
                calls.add(ApiCall(kind, token, untilId))
                return result
            }
        }
    }

    private data class ApiCall(
        val kind: TimelineKind,
        val token: String,
        val untilId: String?,
    )

    private class FakeTimelineCache(
        initial: Map<TimelineKind, List<Note>> = emptyMap(),
    ) : TimelineCache {
        val saved = initial.toMutableMap()

        override suspend fun read(kind: TimelineKind): List<Note> {
            return saved[kind].orEmpty()
        }

        override suspend fun write(kind: TimelineKind, notes: List<Note>) {
            saved[kind] = notes
        }
    }
}
