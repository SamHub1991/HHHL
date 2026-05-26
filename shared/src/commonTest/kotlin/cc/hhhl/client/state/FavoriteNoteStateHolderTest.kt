package cc.hhhl.client.state

import cc.hhhl.client.repository.FavoriteNoteRepository
import cc.hhhl.client.repository.FavoriteNotesRepositoryResult
import cc.hhhl.client.repository.sampleFavoriteNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteNoteStateHolderTest {
    @Test
    fun refreshStoresFavoriteNotes() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(favorite), holder.state.value.favorites)
    }

    @Test
    fun loadMoreAppendsFavorites() = runTest {
        val first = sampleFavoriteNote("fav-1")
        val second = sampleFavoriteNote("fav-2")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(first)),
                loadMoreResult = FavoriteNotesRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(listOf(first, second), holder.state.value.favorites)
    }

    @Test
    fun applyNoteMutationUpdatesFavoriteNotePayloads() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.React(favorite.note.id, "👍"))

        assertEquals(1, holder.state.value.favorites.single().note.reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun applyUnfavoriteMutationRemovesFavoriteFromCurrentList() = runTest {
        val favorite = sampleFavoriteNote("fav-1").copy(
            note = sampleFavoriteNote("fav-1").note.copy(isFavorited = true),
        )
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.Unfavorite(favorite.note.id))

        assertEquals(emptyList(), holder.state.value.favorites)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = sequenceRepository(
                FavoriteNotesRepositoryResult.Unauthorized,
                FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(favorite), holder.state.value.favorites)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = sequenceRepository(
                FavoriteNotesRepositoryResult.Success(listOf(favorite)),
                FavoriteNotesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(favorite.note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.favorites.single().note.reactions.single { it.reaction == "👍" }.count)
    }

    private fun fakeRepository(
        result: FavoriteNotesRepositoryResult,
        loadMoreResult: FavoriteNotesRepositoryResult = result,
    ): FavoriteNoteRepository {
        return object : FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FavoriteNoteApi {
                override suspend fun loadFavorites(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FavoriteNoteLoadResult {
                    return cc.hhhl.client.api.FavoriteNoteLoadResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun refresh(): FavoriteNotesRepositoryResult = result

            override suspend fun loadMore(currentFavorites: List<cc.hhhl.client.model.FavoriteNote>): FavoriteNotesRepositoryResult {
                return loadMoreResult
            }
        }
    }

    private fun sequenceRepository(
        vararg results: FavoriteNotesRepositoryResult,
    ): FavoriteNoteRepository {
        var index = 0
        return object : FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FavoriteNoteApi {
                override suspend fun loadFavorites(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FavoriteNoteLoadResult {
                    return cc.hhhl.client.api.FavoriteNoteLoadResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun refresh(): FavoriteNotesRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(currentFavorites: List<cc.hhhl.client.model.FavoriteNote>): FavoriteNotesRepositoryResult {
                return results.last()
            }
        }
    }
}
