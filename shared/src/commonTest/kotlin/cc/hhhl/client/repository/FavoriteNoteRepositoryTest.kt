package cc.hhhl.client.repository

import cc.hhhl.client.api.FavoriteNoteApi
import cc.hhhl.client.api.FavoriteNoteLoadResult
import cc.hhhl.client.model.FavoriteNote
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class FavoriteNoteRepositoryTest {
    @Test
    fun refreshUsesTokenAndLoadsFavorites() = runTest {
        val calls = mutableListOf<FavoriteApiCall>()
        val favorite = sampleFavoriteNote("fav-1")
        val repository = FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = FavoriteNoteLoadResult.Success(listOf(favorite)),
            ),
        )

        val result = repository.refresh()

        assertEquals(FavoriteNotesRepositoryResult.Success(listOf(favorite)), result)
        assertEquals(listOf(FavoriteApiCall("token-123", null)), calls)
    }

    @Test
    fun loadMoreUsesLastFavoriteIdAsUntilId() = runTest {
        val calls = mutableListOf<FavoriteApiCall>()
        val repository = FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls),
        )

        repository.loadMore(listOf(sampleFavoriteNote("fav-2"), sampleFavoriteNote("fav-1")))

        assertEquals(listOf(FavoriteApiCall("token-123", "fav-1")), calls)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = FavoriteNoteRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<FavoriteNotesRepositoryResult.Unauthorized>(repository.refresh())
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<FavoriteApiCall> = mutableListOf(),
        result: FavoriteNoteLoadResult = FavoriteNoteLoadResult.Success(emptyList()),
        onCall: () -> Unit = {},
    ): FavoriteNoteApi {
        return object : FavoriteNoteApi {
            override suspend fun loadFavorites(
                token: String,
                limit: Int,
                untilId: String?,
            ): FavoriteNoteLoadResult {
                onCall()
                calls.add(FavoriteApiCall(token, untilId))
                return result
            }
        }
    }
}

fun sampleFavoriteNote(id: String): FavoriteNote {
    return FavoriteNote(
        id = id,
        createdAtLabel = "2026-05-25 10:00",
        note = Note(
            id = "note-$id",
            author = User(
                id = "user-$id",
                displayName = "Alice",
                username = "alice",
                avatarInitial = "A",
            ),
            text = "收藏 $id",
            createdAtLabel = "2026-05-25 09:30",
        ),
    )
}

private data class FavoriteApiCall(
    val token: String,
    val untilId: String?,
)
