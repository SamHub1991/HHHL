package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteDetailApi
import cc.hhhl.client.api.NoteDetailLoadResult
import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class NoteDetailRepositoryTest {
    @Test
    fun loadUsesTokenAndNoteId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteDetailRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = NoteDetailLoadResult.Success(FakeData.timeline[0]),
            ),
        )

        val result = repository.load("note-1")

        assertIs<NoteDetailRepositoryResult.Success>(result)
        assertEquals(listOf(ApiCall("token-123", "note-1")), calls)
        assertEquals(FakeData.timeline[0], result.note)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteDetailRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteDetailLoadResult.Success(FakeData.timeline[0]),
            ),
        )

        assertIs<NoteDetailRepositoryResult.Unauthorized>(repository.load("note-1"))
        assertEquals(0, calls)
    }

    @Test
    fun blankNoteIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteDetailRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteDetailLoadResult.Success(FakeData.timeline[0]),
            ),
        )

        assertEquals(
            NoteDetailRepositoryResult.Error("无法打开帖子"),
            repository.load(" "),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: NoteDetailLoadResult,
        onCall: () -> Unit = {},
    ): NoteDetailApi {
        return object : NoteDetailApi {
            override suspend fun loadNote(
                token: String,
                noteId: String,
            ): NoteDetailLoadResult {
                onCall()
                calls.add(ApiCall(token, noteId))
                return result
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val noteId: String,
    )
}
