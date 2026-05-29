package cc.hhhl.client.state

import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.TimelineLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteRepliesRepositoryResult
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineStateHolderTest {
    @Test
    fun refreshStoresLoadedNotesForSelectedKind() = runTest {
        val holder = TimelineStateHolder(
            repository = fakeRepository(
                refreshResult = TimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        assertTrue(holder.state.value.tabs.getValue(TimelineKind.Home).isLoading)
        advanceUntilIdle()

        val tab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertFalse(tab.isLoading)
        assertEquals(listOf(FakeData.timeline[0]), tab.notes)
        assertEquals(null, tab.errorMessage)
    }

    @Test
    fun unauthorizedRefreshMarksTabAndRequestsRelogin() = runTest {
        val holder = TimelineStateHolder(
            repository = fakeRepository(refreshResult = TimelineRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()

        val tab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertEquals("登录已失效，请重新登录", tab.errorMessage)
        assertTrue(holder.state.value.requiresRelogin)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = sequenceRepository(
            TimelineRepositoryResult.Unauthorized,
            TimelineRepositoryResult.Success(listOf(note)),
        ).let {
            TimelineStateHolder(
                repository = it,
                scope = TestScope(testScheduler),
            )
        }

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(note), holder.state.value.tabs.getValue(TimelineKind.Home).notes)
    }

    @Test
    fun selectingTimelineClearsReloginAfterUnauthorized() = runTest {
        val holder = TimelineStateHolder(
            repository = fakeRepository(refreshResult = TimelineRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.select(TimelineKind.Social)

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(TimelineKind.Social, holder.state.value.selectedKind)
    }

    @Test
    fun refreshRestoresCachedNotesBeforeNetworkCompletes() = runTest {
        val cached = FakeData.timeline[0]
        val fresh = FakeData.timeline[1]
        val holder = TimelineStateHolder(
            repository = fakeRepository(
                restoreResult = TimelineRepositoryResult.Success(listOf(cached)),
                refreshResult = TimelineRepositoryResult.Success(listOf(fresh)),
                refreshDelayMs = 1_000,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        runCurrent()

        val cachedTab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertTrue(cachedTab.isLoading)
        assertEquals(listOf(cached), cachedTab.notes)

        advanceUntilIdle()

        val freshTab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertFalse(freshTab.isLoading)
        assertEquals(listOf(fresh), freshTab.notes)
    }

    @Test
    fun refreshKeepsCachedNotesWhenNetworkFails() = runTest {
        val cached = FakeData.timeline[0]
        val holder = TimelineStateHolder(
            repository = fakeRepository(
                restoreResult = TimelineRepositoryResult.Success(listOf(cached)),
                refreshResult = TimelineRepositoryResult.Error("网络请求失败"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()

        val tab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertFalse(tab.isLoading)
        assertEquals(listOf(cached), tab.notes)
        assertEquals("网络请求失败", tab.errorMessage)
    }

    @Test
    fun applyNoteMutationUpdatesMatchingNoteAcrossTabs() = runTest {
        val note = FakeData.timeline[0].copy(reactionCount = 0, reactions = emptyList())
        val holder = TimelineStateHolder(
            repository = fakeRepository(
                refreshResult = TimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        holder.refresh(TimelineKind.Local)
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertEquals(1, holder.state.value.tabs.getValue(TimelineKind.Home).notes.single().reactionCount)
        assertEquals(1, holder.state.value.tabs.getValue(TimelineKind.Local).notes.single().reactionCount)
    }

    @Test
    fun refreshPrefetchesFirstLevelRepliesForVisibleTimelineNotes() = runTest {
        val parent = FakeData.timeline[0].copy(id = "parent", replyCount = 1)
        val child = FakeData.timeline[1].copy(id = "child", replyId = parent.id)
        val holder = TimelineStateHolder(
            repository = fakeRepository(
                refreshResult = TimelineRepositoryResult.Success(listOf(parent)),
            ),
            repliesRepository = fakeRepliesRepository(mapOf(parent.id to listOf(child))),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()

        assertEquals(
            listOf("parent", "child"),
            holder.state.value.tabs.getValue(TimelineKind.Home).notes.map { it.id },
        )
    }

    @Test
    fun refreshQuietlyPrependsNewNotesWithoutResettingCurrentTimeline() = runTest {
        val oldNote = FakeData.timeline[0].copy(id = "old-note")
        val newNote = FakeData.timeline[1].copy(id = "new-note")
        val holder = TimelineStateHolder(
            repository = sequenceRepository(
                TimelineRepositoryResult.Success(listOf(oldNote)),
                TimelineRepositoryResult.Success(listOf(newNote)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()
        holder.refreshQuietly(TimelineKind.Home)
        advanceUntilIdle()

        assertEquals(
            listOf("new-note", "old-note"),
            holder.state.value.tabs.getValue(TimelineKind.Home).notes.map { it.id },
        )
        assertFalse(holder.state.value.tabs.getValue(TimelineKind.Home).isLoading)
    }

    @Test
    fun refreshQuietlyMarksNewNotesAbovePreviousReadPosition() = runTest {
        val oldNote = FakeData.timeline[0].copy(id = "old-note")
        val newNote = FakeData.timeline[1].copy(id = "new-note")
        val holder = TimelineStateHolder(
            repository = sequenceRepository(
                TimelineRepositoryResult.Success(listOf(oldNote)),
                TimelineRepositoryResult.Success(listOf(newNote)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh(TimelineKind.Home)
        advanceUntilIdle()
        holder.refreshQuietly(TimelineKind.Home)
        advanceUntilIdle()

        val tab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertEquals("new-note", tab.firstUnreadNoteId)
        assertEquals(1, tab.newNoteCount)

        holder.consumeNewNotesMarker(TimelineKind.Home)

        val consumedTab = holder.state.value.tabs.getValue(TimelineKind.Home)
        assertEquals(null, consumedTab.firstUnreadNoteId)
        assertEquals(0, consumedTab.newNoteCount)
    }

    @Test
    fun timelineUnreadMarkerCountsItemsBeforePreviousFirstNote() {
        val notes = listOf(
            FakeData.timeline[0].copy(id = "new-2"),
            FakeData.timeline[1].copy(id = "new-1"),
            FakeData.timeline[2].copy(id = "old-first"),
        )

        assertEquals(
            TimelineUnreadMarker(firstUnreadNoteId = "new-2", newNoteCount = 2),
            timelineUnreadMarker(notes, previousFirstNoteId = "old-first"),
        )
    }

    @Test
    fun refreshQuietlyIgnoresParallelRefreshForSameTimeline() = runTest {
        var refreshCalls = 0
        val holder = TimelineStateHolder(
            repository = object : TimelineRepository(
                tokenProvider = { "token-123" },
                api = object : cc.hhhl.client.api.TimelineApi {
                    override suspend fun loadTimeline(
                        kind: TimelineKind,
                        token: String,
                        limit: Int,
                        untilId: String?,
                    ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                    override suspend fun loadMentions(
                        token: String,
                        limit: Int,
                        untilId: String?,
                    ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                    override suspend fun loadPollRecommendations(
                        token: String,
                        limit: Int,
                        offset: Int,
                        excludeChannels: Boolean,
                        local: Boolean?,
                        expired: Boolean,
                    ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())
                },
            ) {
                override suspend fun restore(kind: TimelineKind): TimelineRepositoryResult {
                    return TimelineRepositoryResult.Success(emptyList())
                }

                override suspend fun refresh(kind: TimelineKind): TimelineRepositoryResult {
                    refreshCalls += 1
                    delay(1_000)
                    return TimelineRepositoryResult.Success(emptyList())
                }
            },
            scope = TestScope(testScheduler),
        )

        holder.refreshQuietly(TimelineKind.Local)
        holder.refreshQuietly(TimelineKind.Local)
        advanceUntilIdle()

        assertEquals(1, refreshCalls)
    }

    private fun fakeRepository(
        refreshResult: TimelineRepositoryResult,
        restoreResult: TimelineRepositoryResult = TimelineRepositoryResult.Success(emptyList()),
        refreshDelayMs: Long = 0,
    ): TimelineRepository {
        return object : TimelineRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.TimelineApi {
                override suspend fun loadTimeline(
                    kind: TimelineKind,
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                override suspend fun loadMentions(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                override suspend fun loadPollRecommendations(
                    token: String,
                    limit: Int,
                    offset: Int,
                    excludeChannels: Boolean,
                    local: Boolean?,
                    expired: Boolean,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun restore(kind: TimelineKind): TimelineRepositoryResult {
                return restoreResult
            }

            override suspend fun refresh(kind: TimelineKind): TimelineRepositoryResult {
                if (refreshDelayMs > 0) {
                    delay(refreshDelayMs)
                }
                return refreshResult
            }

            override suspend fun loadMore(
                kind: TimelineKind,
                currentNotes: List<cc.hhhl.client.model.Note>,
            ): TimelineRepositoryResult {
                return refreshResult
            }
        }
    }

    private fun sequenceRepository(
        vararg refreshResults: TimelineRepositoryResult,
    ): TimelineRepository {
        var index = 0
        return object : TimelineRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.TimelineApi {
                override suspend fun loadTimeline(
                    kind: TimelineKind,
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                override suspend fun loadMentions(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())

                override suspend fun loadPollRecommendations(
                    token: String,
                    limit: Int,
                    offset: Int,
                    excludeChannels: Boolean,
                    local: Boolean?,
                    expired: Boolean,
                ): TimelineLoadResult = TimelineLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun restore(kind: TimelineKind): TimelineRepositoryResult {
                return TimelineRepositoryResult.Success(emptyList())
            }

            override suspend fun refresh(kind: TimelineKind): TimelineRepositoryResult {
                val result = refreshResults[index.coerceAtMost(refreshResults.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(
                kind: TimelineKind,
                currentNotes: List<cc.hhhl.client.model.Note>,
            ): TimelineRepositoryResult {
                return refreshResults.last()
            }
        }
    }

    private fun fakeRepliesRepository(childrenByParentId: Map<String, List<Note>>): NoteRepliesRepository {
        return object : NoteRepliesRepository(tokenProvider = { "token-123" }) {
            override suspend fun loadChildren(
                noteId: String,
                currentChildren: List<Note>,
            ): NoteRepliesRepositoryResult {
                return NoteRepliesRepositoryResult.Success(childrenByParentId[noteId].orEmpty())
            }
        }
    }
}
