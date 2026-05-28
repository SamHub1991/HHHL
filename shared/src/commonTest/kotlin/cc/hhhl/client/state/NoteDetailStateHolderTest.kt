package cc.hhhl.client.state

import cc.hhhl.client.api.NoteDetailApi
import cc.hhhl.client.api.NoteDetailLoadResult
import cc.hhhl.client.api.NoteRepliesApi
import cc.hhhl.client.api.NoteRepliesLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.NoteDetailRepository
import cc.hhhl.client.repository.NoteDetailRepositoryResult
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteRepliesRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailStateHolderTest {
    @Test
    fun loadStoresNoteDetail() = runTest {
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(FakeData.timeline[0])),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals("note-1", holder.state.value.noteId)
        assertEquals(FakeData.timeline[0], holder.state.value.note)
    }

    @Test
    fun loadStoresRepliesAfterNoteDetail() = runTest {
        val reply = FakeData.timeline[1]
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(FakeData.timeline[0])),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(listOf(reply)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()

        assertEquals(listOf(reply), holder.state.value.replies)
        assertFalse(holder.state.value.isLoadingReplies)
    }

    @Test
    fun loadMoreRepliesUsesCurrentReplies() = runTest {
        val first = FakeData.timeline[1]
        val second = FakeData.timeline[2]
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(FakeData.timeline[0])),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(listOf(first)),
                loadMoreResult = NoteRepliesRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()
        holder.loadMoreReplies()
        assertTrue(holder.state.value.isLoadingMoreReplies)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMoreReplies)
        assertEquals(listOf(first, second), holder.state.value.replies)
    }

    @Test
    fun loadingAnotherNoteInvalidatesPendingLoadMoreReplies() = runTest {
        val firstReply = FakeData.timeline[1].copy(id = "reply-1")
        val staleMoreResult = CompletableDeferred<NoteRepliesRepositoryResult>()
        val holder = NoteDetailStateHolder(
            repository = sequenceRepository(
                NoteDetailRepositoryResult.Success(FakeData.timeline[0].copy(id = "note-1")),
                NoteDetailRepositoryResult.Success(FakeData.timeline[2].copy(id = "note-2")),
            ),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(listOf(firstReply)),
                loadMoreResult = staleMoreResult,
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()
        holder.loadMoreReplies()
        assertTrue(holder.state.value.isLoadingMoreReplies)

        holder.load("note-2")
        assertFalse(holder.state.value.isLoadingMoreReplies)
        staleMoreResult.complete(NoteRepliesRepositoryResult.Success(listOf(firstReply, FakeData.timeline[3])))
        advanceUntilIdle()

        assertEquals("note-2", holder.state.value.noteId)
        assertFalse(holder.state.value.isLoadingMoreReplies)
        assertEquals(listOf(firstReply), holder.state.value.replies)
    }

    @Test
    fun loadRepliesPrefetchesVisibleChildReplies() = runTest {
        val parent = FakeData.timeline[1].copy(id = "reply-1", replyId = "note-1", replyCount = 1)
        val child = FakeData.timeline[2].copy(id = "child-1", replyId = parent.id)
        val loadedChildrenFor = mutableListOf<String>()
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(FakeData.timeline[0])),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(listOf(parent)),
                childResult = NoteRepliesRepositoryResult.Success(listOf(child)),
                onLoadChildren = { loadedChildrenFor.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()

        assertEquals(listOf(parent.id), loadedChildrenFor)
        assertEquals(setOf(parent.id), holder.state.value.expandedReplyIds)
        assertEquals(listOf(child), holder.state.value.childRepliesByParentId[parent.id])
    }

    @Test
    fun loadRepliesCapsAutomaticChildPrefetches() = runTest {
        val parents = (1..10).map { index ->
            FakeData.timeline[index % FakeData.timeline.size].copy(
                id = "reply-$index",
                replyId = "note-1",
                replyCount = 1,
            )
        }
        val loadedChildrenFor = mutableListOf<String>()
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(FakeData.timeline[0])),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(parents),
                childResult = NoteRepliesRepositoryResult.Success(emptyList()),
                onLoadChildren = { loadedChildrenFor.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()

        assertEquals((1..8).map { "reply-$it" }, loadedChildrenFor)
    }

    @Test
    fun applyNoteMutationUpdatesDetailAndReplies() = runTest {
        val note = FakeData.timeline[0]
        val reply = FakeData.timeline[1]
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Success(note)),
            repliesRepository = fakeRepliesRepository(
                refreshResult = NoteRepliesRepositoryResult.Success(listOf(reply)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load(note.id)
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))
        holder.applyNoteMutation(NoteLocalMutation.React(reply.id, "👍"))

        assertEquals(1, holder.state.value.note?.reactions?.single { it.reaction == "👍" }?.count)
        assertEquals(1, holder.state.value.replies.single().reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = NoteDetailStateHolder(
            repository = fakeRepository(NoteDetailRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = NoteDetailStateHolder(
            repository = sequenceRepository(
                NoteDetailRepositoryResult.Unauthorized,
                NoteDetailRepositoryResult.Success(note),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load("note-1")
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.load("note-1")
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(note, holder.state.value.note)
    }

    @Test
    fun applyingNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = NoteDetailStateHolder(
            repository = sequenceRepository(
                NoteDetailRepositoryResult.Unauthorized,
                NoteDetailRepositoryResult.Success(note),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load(note.id)
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
    }

    private fun fakeRepository(result: NoteDetailRepositoryResult): NoteDetailRepository {
        return object : NoteDetailRepository(
            tokenProvider = { "token-123" },
            api = object : NoteDetailApi {
                override suspend fun loadNote(
                    token: String,
                    noteId: String,
                ): NoteDetailLoadResult = NoteDetailLoadResult.Success(FakeData.timeline[0])
            },
        ) {
            override suspend fun load(noteId: String): NoteDetailRepositoryResult {
                return result
            }
        }
    }

    private fun sequenceRepository(
        vararg results: NoteDetailRepositoryResult,
    ): NoteDetailRepository {
        var index = 0
        return object : NoteDetailRepository(
            tokenProvider = { "token-123" },
            api = object : NoteDetailApi {
                override suspend fun loadNote(
                    token: String,
                    noteId: String,
                ): NoteDetailLoadResult = NoteDetailLoadResult.Success(FakeData.timeline[0])
            },
        ) {
            override suspend fun load(noteId: String): NoteDetailRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }
        }
    }

    private fun fakeRepliesRepository(
        refreshResult: NoteRepliesRepositoryResult,
        loadMoreResult: NoteRepliesRepositoryResult = refreshResult,
        childResult: NoteRepliesRepositoryResult = NoteRepliesRepositoryResult.Success(emptyList()),
        onLoadChildren: (String) -> Unit = {},
    ): NoteRepliesRepository {
        return object : NoteRepliesRepository(
            tokenProvider = { "token-123" },
            api = object : NoteRepliesApi {
                override suspend fun loadReplies(
                    token: String,
                    noteId: String,
                    limit: Int,
                    untilId: String?,
                ): NoteRepliesLoadResult = NoteRepliesLoadResult.Success(emptyList())

                override suspend fun loadChildren(
                    token: String,
                    noteId: String,
                    limit: Int,
                    untilId: String?,
                ): NoteRepliesLoadResult = NoteRepliesLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(noteId: String): NoteRepliesRepositoryResult {
                return refreshResult
            }

            override suspend fun loadMore(
                noteId: String,
                currentReplies: List<Note>,
            ): NoteRepliesRepositoryResult {
                return loadMoreResult
            }

            override suspend fun loadChildren(
                noteId: String,
                currentChildren: List<Note>,
            ): NoteRepliesRepositoryResult {
                onLoadChildren(noteId)
                return childResult
            }
        }
    }

    private fun fakeRepliesRepository(
        refreshResult: NoteRepliesRepositoryResult,
        loadMoreResult: CompletableDeferred<NoteRepliesRepositoryResult>,
        childResult: NoteRepliesRepositoryResult = NoteRepliesRepositoryResult.Success(emptyList()),
        onLoadChildren: (String) -> Unit = {},
    ): NoteRepliesRepository {
        return object : NoteRepliesRepository(
            tokenProvider = { "token-123" },
            api = object : NoteRepliesApi {
                override suspend fun loadReplies(
                    token: String,
                    noteId: String,
                    limit: Int,
                    untilId: String?,
                ): NoteRepliesLoadResult = NoteRepliesLoadResult.Success(emptyList())

                override suspend fun loadChildren(
                    token: String,
                    noteId: String,
                    limit: Int,
                    untilId: String?,
                ): NoteRepliesLoadResult = NoteRepliesLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(noteId: String): NoteRepliesRepositoryResult {
                return refreshResult
            }

            override suspend fun loadMore(
                noteId: String,
                currentReplies: List<Note>,
            ): NoteRepliesRepositoryResult {
                return loadMoreResult.await()
            }

            override suspend fun loadChildren(
                noteId: String,
                currentChildren: List<Note>,
            ): NoteRepliesRepositoryResult {
                onLoadChildren(noteId)
                return childResult
            }
        }
    }
}
