package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteRepliesApi
import cc.hhhl.client.api.NoteRepliesLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class NoteRepliesRepositoryTest {
    @Test
    fun refreshUsesTokenAndNoteId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteRepliesRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NoteRepliesLoadResult.Success(listOf(FakeData.timeline[1])),
            ),
        )

        val result = repository.refresh("note-1")

        assertIs<NoteRepliesRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", "note-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[1]), result.replies)
    }

    @Test
    fun loadMoreUsesLastReplyIdAsUntilIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[1]
        val second = FakeData.timeline[2]
        val repository = NoteRepliesRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = NoteRepliesLoadResult.Success(listOf(first, second, first)),
            ),
        )

        val result = repository.loadMore("note-1", currentReplies = listOf(first))

        assertIs<NoteRepliesRepositoryResult.Success>(result)
        assertEquals(listOf(first, second), result.replies)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteRepliesRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteRepliesLoadResult.Success(emptyList()),
            ),
        )

        assertIs<NoteRepliesRepositoryResult.Unauthorized>(repository.refresh("note-1"))
        assertEquals(0, calls)
    }

    @Test
    fun blankNoteIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteRepliesRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteRepliesLoadResult.Success(emptyList()),
            ),
        )

        assertEquals(
            NoteRepliesRepositoryResult.Error("无法读取回复"),
            repository.refresh(" "),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: NoteRepliesLoadResult,
        onCall: () -> Unit = {},
    ): NoteRepliesApi {
        return object : NoteRepliesApi {
            override suspend fun loadReplies(
                token: String,
                noteId: String,
                limit: Int,
                untilId: String?,
            ): NoteRepliesLoadResult {
                onCall()
                calls.add(ApiCall(token, noteId, untilId))
                return result
            }

            override suspend fun loadChildren(
                token: String,
                noteId: String,
                limit: Int,
                untilId: String?,
            ): NoteRepliesLoadResult {
                onCall()
                calls.add(ApiCall(token, noteId, untilId))
                return result
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val noteId: String,
        val untilId: String?,
    )
}
