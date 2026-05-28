package cc.hhhl.client.repository

import cc.hhhl.client.api.NoteActionApi
import cc.hhhl.client.api.NoteActionApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class NoteActionRepositoryTest {
    @Test
    fun reactUsesTokenAndDefaultReaction() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.React("note-1"))

        assertEquals(NoteActionRepositoryResult.Success("已发送反应"), result)
        assertEquals(listOf(ApiCall("like", "token-123", "note-1", "❤️")), calls)
    }

    @Test
    fun reactUsesSelectedReaction() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.React("note-1", "🚀"))

        assertEquals(NoteActionRepositoryResult.Success("已发送反应"), result)
        assertEquals(listOf(ApiCall("like", "token-123", "note-1", "🚀")), calls)
    }

    @Test
    fun renoteUsesTokenAndNoteId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.Renote("note-1"))

        assertEquals(NoteActionRepositoryResult.Success("已转发"), result)
        assertEquals(listOf(ApiCall("renote", "token-123", "note-1", null)), calls)
    }

    @Test
    fun unrenoteUsesTokenAndNoteId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.Unrenote("note-1"))

        assertEquals(NoteActionRepositoryResult.Success("已取消转发"), result)
        assertEquals(listOf(ApiCall("unrenote", "token-123", "note-1", null)), calls)
    }

    @Test
    fun favoriteUsesTokenAndNoteId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.Favorite("note-1"))

        assertEquals(NoteActionRepositoryResult.Success("已收藏"), result)
        assertEquals(listOf(ApiCall("favorite", "token-123", "note-1", null)), calls)
    }

    @Test
    fun votePollUsesTokenNoteIdAndChoice() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = NoteActionApiResult.Success),
        )

        val result = repository.perform(NoteActionRequest.VotePoll("note-1", choice = 2))

        assertEquals(NoteActionRepositoryResult.Success("已投票"), result)
        assertEquals(listOf(ApiCall("votePoll", "token-123", "note-1", "2")), calls)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteActionRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteActionApiResult.Success,
            ),
        )

        assertIs<NoteActionRepositoryResult.Unauthorized>(
            repository.perform(NoteActionRequest.React("note-1")),
        )
        assertEquals(0, calls)
    }

    @Test
    fun blankNoteIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = NoteActionRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = NoteActionApiResult.Success,
            ),
        )

        assertEquals(
            NoteActionRepositoryResult.Error("无法操作帖子"),
            repository.perform(NoteActionRequest.React(" ")),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: NoteActionApiResult,
        onCall: () -> Unit = {},
    ): NoteActionApi {
        return object : NoteActionApi {
            override suspend fun createReaction(
                token: String,
                noteId: String,
                reaction: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("react", token, noteId, reaction))
                return result
            }

            override suspend fun likeNote(
                token: String,
                noteId: String,
                override: String?,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("like", token, noteId, override))
                return result
            }

            override suspend fun deleteReaction(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("deleteReaction", token, noteId, null))
                return result
            }

            override suspend fun createFavorite(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("favorite", token, noteId, null))
                return result
            }

            override suspend fun deleteFavorite(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("deleteFavorite", token, noteId, null))
                return result
            }

            override suspend fun createRenote(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("renote", token, noteId, null))
                return result
            }

            override suspend fun deleteRenote(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("unrenote", token, noteId, null))
                return result
            }

            override suspend fun deleteNote(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("delete", token, noteId, null))
                return result
            }

            override suspend fun votePoll(
                token: String,
                noteId: String,
                choice: Int,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("votePoll", token, noteId, choice.toString()))
                return result
            }

            override suspend fun reportNote(
                token: String,
                userId: String,
                noteId: String,
                comment: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("report", token, noteId, comment))
                return result
            }

            override suspend fun muteNote(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("mute", token, noteId, null))
                return result
            }

            override suspend fun unmuteNote(
                token: String,
                noteId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("unmute", token, noteId, null))
                return result
            }

            override suspend fun muteRenotes(
                token: String,
                userId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("muteRenotes", token, userId, null))
                return result
            }

            override suspend fun unmuteRenotes(
                token: String,
                userId: String,
            ): NoteActionApiResult {
                onCall()
                calls.add(ApiCall("unmuteRenotes", token, userId, null))
                return result
            }
        }
    }

    private data class ApiCall(
        val kind: String,
        val token: String,
        val noteId: String,
        val reaction: String?,
    )
}
