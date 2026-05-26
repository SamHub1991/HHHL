package cc.hhhl.client.repository

import cc.hhhl.client.api.ClipApi
import cc.hhhl.client.api.ClipActionResult
import cc.hhhl.client.api.ClipCreateResult
import cc.hhhl.client.api.ClipLoadResult
import cc.hhhl.client.api.ClipNotesLoadResult
import cc.hhhl.client.api.ClipUpdateResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ClipRepositoryTest {
    @Test
    fun refreshClipsUsesTokenAndKind() = runTest {
        val clips = listOf(sampleClip("clip-1"))
        val calls = mutableListOf<ClipCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                clipCalls = calls,
                clipResult = ClipLoadResult.Success(clips),
            ),
        )

        val result = repository.refreshClips(ClipListKind.Favorites)

        assertIs<ClipsRepositoryResult.Success>(result)
        assertEquals(listOf(ClipCall("token-123", ClipListKind.Favorites)), calls)
        assertEquals(clips, result.clips)
    }

    @Test
    fun refreshNotesUsesTokenAndClipId() = runTest {
        val calls = mutableListOf<NotesCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                notesCalls = calls,
                notesResult = ClipNotesLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refreshNotes("clip-1")

        assertIs<ClipNotesRepositoryResult.Success>(result)
        assertEquals(listOf(NotesCall("token-123", "clip-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreNotesUsesLastNoteIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val calls = mutableListOf<NotesCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                notesCalls = calls,
                notesResult = ClipNotesLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMoreNotes(
            clipId = "clip-1",
            currentNotes = listOf(first),
        )

        assertIs<ClipNotesRepositoryResult.Success>(result)
        assertEquals(listOf(NotesCall("token-123", "clip-1", first.id)), calls)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ClipRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<ClipsRepositoryResult.Unauthorized>(repository.refreshClips(ClipListKind.Owned))
        assertIs<ClipNotesRepositoryResult.Unauthorized>(repository.refreshNotes("clip-1"))
        assertEquals(0, calls)
    }

    @Test
    fun favoriteAndUnfavoriteClipUseTokenAndClipId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = ClipActionResult.Success,
            ),
        )

        assertEquals(ClipActionRepositoryResult.Success, repository.favoriteClip("clip-1"))
        assertEquals(ClipActionRepositoryResult.Success, repository.unfavoriteClip("clip-1"))
        assertEquals(
            listOf(
                ActionCall("favorite", "token-123", "clip-1"),
                ActionCall("unfavorite", "token-123", "clip-1"),
            ),
            calls,
        )
    }

    @Test
    fun addAndRemoveNoteUseTokenClipIdAndNoteId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = ClipActionResult.Success,
            ),
        )

        assertEquals(ClipActionRepositoryResult.Success, repository.addNoteToClip("clip-1", "note-1"))
        assertEquals(ClipActionRepositoryResult.Success, repository.removeNoteFromClip("clip-1", "note-1"))
        assertEquals(
            listOf(
                ActionCall("add-note", "token-123", "clip-1", "note-1"),
                ActionCall("remove-note", "token-123", "clip-1", "note-1"),
            ),
            calls,
        )
    }

    @Test
    fun createClipUsesTokenAndTrimmedFields() = runTest {
        val created = sampleClip("clip-created")
        val calls = mutableListOf<CreateCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                createCalls = calls,
                createResult = ClipCreateResult.Success(created),
            ),
        )

        val result = repository.createClip(
            name = "  阅读清单  ",
            description = "  长文和资料  ",
            isPublic = true,
        )

        assertIs<ClipCreateRepositoryResult.Success>(result)
        assertEquals(created, result.clip)
        assertEquals(
            listOf(CreateCall("token-123", "阅读清单", "长文和资料", true)),
            calls,
        )
    }

    @Test
    fun updateClipUsesTokenClipIdAndTrimmedFields() = runTest {
        val updated = sampleClip("clip-1").copy(name = "资料")
        val calls = mutableListOf<UpdateCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                updateCalls = calls,
                updateResult = ClipUpdateResult.Success(updated),
            ),
        )

        val result = repository.updateClip(
            clipId = " clip-1 ",
            name = "  资料  ",
            description = "  更新  ",
            isPublic = false,
        )

        assertIs<ClipUpdateRepositoryResult.Success>(result)
        assertEquals(updated, result.clip)
        assertEquals(
            listOf(UpdateCall("token-123", "clip-1", "资料", "更新", false)),
            calls,
        )
    }

    @Test
    fun deleteClipUsesTokenAndClipId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = ClipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = ClipActionResult.Success,
            ),
        )

        assertEquals(ClipActionRepositoryResult.Success, repository.deleteClip(" clip-1 "))
        assertEquals(listOf(ActionCall("delete", "token-123", "clip-1")), calls)
    }

    private fun fakeApi(
        clipCalls: MutableList<ClipCall> = mutableListOf(),
        notesCalls: MutableList<NotesCall> = mutableListOf(),
        actionCalls: MutableList<ActionCall> = mutableListOf(),
        createCalls: MutableList<CreateCall> = mutableListOf(),
        updateCalls: MutableList<UpdateCall> = mutableListOf(),
        clipResult: ClipLoadResult = ClipLoadResult.Success(emptyList()),
        notesResult: ClipNotesLoadResult = ClipNotesLoadResult.Success(emptyList()),
        actionResult: ClipActionResult = ClipActionResult.Success,
        createResult: ClipCreateResult = ClipCreateResult.Success(sampleClip("clip-created")),
        updateResult: ClipUpdateResult = ClipUpdateResult.Success(sampleClip("clip-updated")),
        onCall: () -> Unit = {},
    ): ClipApi {
        return object : ClipApi {
            override suspend fun loadClips(
                token: String,
                kind: ClipListKind,
            ): ClipLoadResult {
                onCall()
                clipCalls.add(ClipCall(token, kind))
                return clipResult
            }

            override suspend fun loadClipNotes(
                token: String,
                clipId: String,
                limit: Int,
                untilId: String?,
            ): ClipNotesLoadResult {
                onCall()
                notesCalls.add(NotesCall(token, clipId, untilId))
                return notesResult
            }

            override suspend fun createClip(
                token: String,
                name: String,
                description: String,
                isPublic: Boolean,
            ): ClipCreateResult {
                createCalls.add(CreateCall(token, name, description, isPublic))
                return createResult
            }

            override suspend fun updateClip(
                token: String,
                clipId: String,
                name: String,
                description: String,
                isPublic: Boolean,
            ): ClipUpdateResult {
                updateCalls.add(UpdateCall(token, clipId, name, description, isPublic))
                return updateResult
            }

            override suspend fun deleteClip(
                token: String,
                clipId: String,
            ): ClipActionResult {
                actionCalls.add(ActionCall("delete", token, clipId))
                return actionResult
            }

            override suspend fun favoriteClip(
                token: String,
                clipId: String,
            ): ClipActionResult {
                actionCalls.add(ActionCall("favorite", token, clipId))
                return actionResult
            }

            override suspend fun unfavoriteClip(
                token: String,
                clipId: String,
            ): ClipActionResult {
                actionCalls.add(ActionCall("unfavorite", token, clipId))
                return actionResult
            }

            override suspend fun addNoteToClip(
                token: String,
                clipId: String,
                noteId: String,
            ): ClipActionResult {
                actionCalls.add(ActionCall("add-note", token, clipId, noteId))
                return actionResult
            }

            override suspend fun removeNoteFromClip(
                token: String,
                clipId: String,
                noteId: String,
            ): ClipActionResult {
                actionCalls.add(ActionCall("remove-note", token, clipId, noteId))
                return actionResult
            }
        }
    }

    private fun sampleClip(id: String): Clip {
        return Clip(
            id = id,
            name = "收藏的长文",
            description = "值得反复看",
            owner = User("user-1", "Alice", "alice", "A"),
            ownerId = "user-1",
            isPublic = true,
            isFavorited = false,
            favoritedCount = 3,
            notesCount = 12,
            createdAtLabel = "2026-05-25 08:00",
            lastClippedAtLabel = "2026-05-25 09:00",
        )
    }

    private data class ClipCall(
        val token: String,
        val kind: ClipListKind,
    )

    private data class NotesCall(
        val token: String,
        val clipId: String,
        val untilId: String?,
    )

    private data class ActionCall(
        val action: String,
        val token: String,
        val clipId: String,
        val noteId: String? = null,
    )

    private data class CreateCall(
        val token: String,
        val name: String,
        val description: String,
        val isPublic: Boolean,
    )

    private data class UpdateCall(
        val token: String,
        val clipId: String,
        val name: String,
        val description: String,
        val isPublic: Boolean,
    )
}
