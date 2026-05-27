package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.ClipActionRepositoryResult
import cc.hhhl.client.repository.ClipCreateRepositoryResult
import cc.hhhl.client.repository.ClipNotesRepositoryResult
import cc.hhhl.client.repository.ClipRepository
import cc.hhhl.client.repository.ClipUpdateRepositoryResult
import cc.hhhl.client.repository.ClipsRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ClipStateHolderTest {
    @Test
    fun refreshClipsStoresClipsAndLoadsFirstNotes() = runTest {
        val clip = sampleClip("clip-1")
        val note = FakeData.timeline[0]
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(clip)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        assertTrue(holder.state.value.isLoadingClips)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingClips)
        assertFalse(holder.state.value.isLoadingNotes)
        assertEquals(listOf(clip), holder.state.value.clips)
        assertEquals(clip, holder.state.value.selectedClip)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun selectKindLoadsThatKindClips() = runTest {
        val owned = sampleClip("clip-owned")
        val favorite = sampleClip("clip-favorite")
        val calls = mutableListOf<ClipListKind>()
        val holder = ClipStateHolder(
            repository = sequenceRepository(
                clipsResults = listOf(
                    ClipsRepositoryResult.Success(listOf(owned)),
                    ClipsRepositoryResult.Success(listOf(favorite)),
                ),
                notesResult = ClipNotesRepositoryResult.Success(emptyList()),
                onRefreshClips = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.selectKind(ClipListKind.Favorites)
        advanceUntilIdle()

        assertEquals(listOf(ClipListKind.Owned, ClipListKind.Favorites), calls)
        assertEquals(ClipListKind.Favorites, holder.state.value.selectedKind)
        assertEquals(listOf(favorite), holder.state.value.clips)
        assertEquals(favorite, holder.state.value.selectedClip)
    }

    @Test
    fun selectClipLoadsSelectedNotes() = runTest {
        val first = sampleClip("clip-1")
        val second = sampleClip("clip-2")
        val calls = mutableListOf<String>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(first, second)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                onRefreshNotes = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.selectClip(second)
        assertTrue(holder.state.value.isLoadingNotes)
        advanceUntilIdle()

        assertEquals(second, holder.state.value.selectedClip)
        assertEquals(listOf("clip-1", "clip-2"), calls)
    }

    @Test
    fun loadMoreAppendsNotesAndMarksEndReached() = runTest {
        val clip = sampleClip("clip-1")
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(clip)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(first)),
                loadMoreResult = ClipNotesRepositoryResult.Success(
                    notes = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertTrue(holder.state.value.endReached)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun unauthorizedClipLoadMarksRelogin() = runTest {
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorizedClipLoad() = runTest {
        val clip = sampleClip("clip-1")
        val holder = ClipStateHolder(
            repository = sequenceRepository(
                clipsResults = listOf(
                    ClipsRepositoryResult.Unauthorized,
                    ClipsRepositoryResult.Success(listOf(clip)),
                ),
                notesResult = ClipNotesRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refreshClips()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(clip), holder.state.value.clips)
        assertEquals(clip, holder.state.value.selectedClip)
    }

    @Test
    fun toggleFavoriteFavoritesSelectedClipAndUpdatesLists() = runTest {
        val clip = sampleClip("clip-1").copy(isFavorited = false, favoritedCount = 3)
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(clip)),
                actionResult = ClipActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.toggleFavoriteSelectedClip()
        assertTrue(holder.state.value.isChangingFavorite)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingFavorite)
        assertEquals(true, holder.state.value.selectedClip?.isFavorited)
        assertEquals(4, holder.state.value.selectedClip?.favoritedCount)
        assertEquals(true, holder.state.value.clips.single().isFavorited)
    }

    @Test
    fun removeNoteFromSelectedClipRemovesNoteAndUpdatesCounts() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val clip = sampleClip("clip-1").copy(notesCount = 2)
        val removedNotes = mutableListOf<String>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(clip)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(first, second)),
                actionResult = ClipActionRepositoryResult.Success,
                onRemoveNote = { _, noteId -> removedNotes.add(noteId) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.removeNoteFromSelectedClip(first.id)
        assertTrue(holder.state.value.isChangingClipNote)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingClipNote)
        assertEquals(listOf(first.id), removedNotes)
        assertEquals(listOf(second), holder.state.value.notes)
        assertEquals(1, holder.state.value.selectedClip?.notesCount)
        assertEquals(1, holder.state.value.clips.single().notesCount)
    }

    @Test
    fun addNoteToSelectedClipAddsMissingNoteAndUpdatesCounts() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val clip = sampleClip("clip-1").copy(notesCount = 1)
        val addedNotes = mutableListOf<String>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(clip)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(first)),
                actionResult = ClipActionRepositoryResult.Success,
                onAddNote = { _, noteId -> addedNotes.add(noteId) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.addNoteToSelectedClip(second)
        assertTrue(holder.state.value.isChangingClipNote)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingClipNote)
        assertEquals(listOf(second.id), addedNotes)
        assertEquals(listOf(second, first), holder.state.value.notes)
        assertEquals(2, holder.state.value.selectedClip?.notesCount)
        assertEquals(2, holder.state.value.clips.single().notesCount)
    }

    @Test
    fun addNoteToSpecificClipUpdatesThatClipWithoutChangingSelectedNotes() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val selected = sampleClip("clip-1").copy(notesCount = 1)
        val target = sampleClip("clip-2").copy(notesCount = 4)
        val addedNotes = mutableListOf<Pair<String, String>>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(selected, target)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(first)),
                actionResult = ClipActionRepositoryResult.Success,
                onAddNote = { clipId, noteId -> addedNotes.add(clipId to noteId) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.addNoteToClip(target, second)
        advanceUntilIdle()

        assertEquals(listOf(target.id to second.id), addedNotes)
        assertEquals(selected, holder.state.value.selectedClip)
        assertEquals(listOf(first), holder.state.value.notes)
        assertEquals(1, holder.state.value.clips.first { it.id == selected.id }.notesCount)
        assertEquals(5, holder.state.value.clips.first { it.id == target.id }.notesCount)
    }

    @Test
    fun createClipPrependsAndSelectsCreatedClip() = runTest {
        val existing = sampleClip("clip-1").copy(notesCount = 1)
        val created = sampleClip("clip-created").copy(name = "阅读清单", description = "长文", isPublic = true)
        val createCalls = mutableListOf<Triple<String, String, Boolean>>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(existing)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                createResult = ClipCreateRepositoryResult.Success(created),
                onCreateClip = { name, description, isPublic ->
                    createCalls.add(Triple(name, description, isPublic))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.createClip(" 阅读清单 ", " 长文 ", isPublic = true)
        assertTrue(holder.state.value.isCreatingClip)
        advanceUntilIdle()

        assertFalse(holder.state.value.isCreatingClip)
        assertEquals(listOf(Triple("阅读清单", "长文", true)), createCalls)
        assertEquals(created, holder.state.value.selectedClip)
        assertEquals(listOf(created, existing), holder.state.value.clips)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun updateSelectedClipUpdatesListsAndSelection() = runTest {
        val selected = sampleClip("clip-1").copy(name = "旧名称", isPublic = false)
        val updated = selected.copy(name = "新名称", description = "新描述", isPublic = true)
        val updateCalls = mutableListOf<UpdateClipCall>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(selected)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                updateResult = ClipUpdateRepositoryResult.Success(updated),
                onUpdateClip = { clipId, name, description, isPublic ->
                    updateCalls.add(UpdateClipCall(clipId, name, description, isPublic))
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.updateSelectedClip(" 新名称 ", " 新描述 ", isPublic = true)
        assertTrue(holder.state.value.isUpdatingClip)
        advanceUntilIdle()

        assertFalse(holder.state.value.isUpdatingClip)
        assertEquals(listOf(UpdateClipCall("clip-1", "新名称", "新描述", true)), updateCalls)
        assertEquals(updated, holder.state.value.selectedClip)
        assertEquals(updated, holder.state.value.clips.single())
        assertEquals(listOf(FakeData.timeline[0]), holder.state.value.notes)
    }

    @Test
    fun deleteSelectedClipRemovesItAndSelectsNextClip() = runTest {
        val first = sampleClip("clip-1")
        val second = sampleClip("clip-2")
        val deletedClipIds = mutableListOf<String>()
        val holder = ClipStateHolder(
            repository = fakeRepository(
                clipsResult = ClipsRepositoryResult.Success(listOf(first, second)),
                notesResult = ClipNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                actionResult = ClipActionRepositoryResult.Success,
                onDeleteClip = { deletedClipIds.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.deleteSelectedClip()
        assertTrue(holder.state.value.isDeletingClip)
        advanceUntilIdle()

        assertFalse(holder.state.value.isDeletingClip)
        assertEquals(listOf("clip-1"), deletedClipIds)
        assertEquals(listOf(second), holder.state.value.clips)
        assertEquals(second, holder.state.value.selectedClip)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val clip = sampleClip("clip-1")
        val note = FakeData.timeline[0]
        val holder = ClipStateHolder(
            repository = sequenceRepository(
                clipsResults = listOf(
                    ClipsRepositoryResult.Success(listOf(clip)),
                    ClipsRepositoryResult.Unauthorized,
                ),
                notesResult = ClipNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshClips()
        advanceUntilIdle()
        holder.refreshClips()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    private fun fakeRepository(
        clipsResult: ClipsRepositoryResult,
        notesResult: ClipNotesRepositoryResult = ClipNotesRepositoryResult.Success(emptyList()),
        loadMoreResult: ClipNotesRepositoryResult = notesResult,
        actionResult: ClipActionRepositoryResult = ClipActionRepositoryResult.Success,
        createResult: ClipCreateRepositoryResult = ClipCreateRepositoryResult.Success(sampleClip("clip-created")),
        updateResult: ClipUpdateRepositoryResult = ClipUpdateRepositoryResult.Success(sampleClip("clip-updated")),
        onRefreshClips: (ClipListKind) -> Unit = {},
        onRefreshNotes: (String) -> Unit = {},
        onAddNote: (String, String) -> Unit = { _, _ -> },
        onRemoveNote: (String, String) -> Unit = { _, _ -> },
        onCreateClip: (String, String, Boolean) -> Unit = { _, _, _ -> },
        onUpdateClip: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
        onDeleteClip: (String) -> Unit = {},
    ): ClipRepository {
        return sequenceRepository(
            clipsResults = listOf(clipsResult),
            notesResult = notesResult,
            loadMoreResult = loadMoreResult,
            actionResult = actionResult,
            createResult = createResult,
            updateResult = updateResult,
            onRefreshClips = onRefreshClips,
            onRefreshNotes = onRefreshNotes,
            onAddNote = onAddNote,
            onRemoveNote = onRemoveNote,
            onCreateClip = onCreateClip,
            onUpdateClip = onUpdateClip,
            onDeleteClip = onDeleteClip,
        )
    }

    private fun sequenceRepository(
        clipsResults: List<ClipsRepositoryResult>,
        notesResult: ClipNotesRepositoryResult,
        loadMoreResult: ClipNotesRepositoryResult = notesResult,
        actionResult: ClipActionRepositoryResult = ClipActionRepositoryResult.Success,
        createResult: ClipCreateRepositoryResult = ClipCreateRepositoryResult.Success(sampleClip("clip-created")),
        updateResult: ClipUpdateRepositoryResult = ClipUpdateRepositoryResult.Success(sampleClip("clip-updated")),
        onRefreshClips: (ClipListKind) -> Unit = {},
        onRefreshNotes: (String) -> Unit = {},
        onAddNote: (String, String) -> Unit = { _, _ -> },
        onRemoveNote: (String, String) -> Unit = { _, _ -> },
        onCreateClip: (String, String, Boolean) -> Unit = { _, _, _ -> },
        onUpdateClip: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
        onDeleteClip: (String) -> Unit = {},
    ): ClipRepository {
        var clipResultIndex = 0
        return object : ClipRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ClipApi {
                override suspend fun loadClips(
                    token: String,
                    kind: ClipListKind,
                ): cc.hhhl.client.api.ClipLoadResult {
                    return cc.hhhl.client.api.ClipLoadResult.Success(emptyList())
                }

                override suspend fun loadClipNotes(
                    token: String,
                    clipId: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.ClipNotesLoadResult {
                    return cc.hhhl.client.api.ClipNotesLoadResult.Success(emptyList())
                }

                override suspend fun loadNoteClips(
                    token: String,
                    noteId: String,
                ): cc.hhhl.client.api.ClipLoadResult {
                    return cc.hhhl.client.api.ClipLoadResult.Success(emptyList())
                }

                override suspend fun createClip(
                    token: String,
                    name: String,
                    description: String,
                    isPublic: Boolean,
                ): cc.hhhl.client.api.ClipCreateResult {
                    return cc.hhhl.client.api.ClipCreateResult.Success(sampleClip("clip-created"))
                }

                override suspend fun updateClip(
                    token: String,
                    clipId: String,
                    name: String,
                    description: String,
                    isPublic: Boolean,
                ): cc.hhhl.client.api.ClipUpdateResult {
                    return cc.hhhl.client.api.ClipUpdateResult.Success(sampleClip("clip-updated"))
                }

                override suspend fun deleteClip(
                    token: String,
                    clipId: String,
                ): cc.hhhl.client.api.ClipActionResult {
                    return cc.hhhl.client.api.ClipActionResult.Success
                }

                override suspend fun favoriteClip(
                    token: String,
                    clipId: String,
                ): cc.hhhl.client.api.ClipActionResult {
                    return cc.hhhl.client.api.ClipActionResult.Success
                }

                override suspend fun unfavoriteClip(
                    token: String,
                    clipId: String,
                ): cc.hhhl.client.api.ClipActionResult {
                    return cc.hhhl.client.api.ClipActionResult.Success
                }

                override suspend fun addNoteToClip(
                    token: String,
                    clipId: String,
                    noteId: String,
                ): cc.hhhl.client.api.ClipActionResult {
                    return cc.hhhl.client.api.ClipActionResult.Success
                }

                override suspend fun removeNoteFromClip(
                    token: String,
                    clipId: String,
                    noteId: String,
                ): cc.hhhl.client.api.ClipActionResult {
                    return cc.hhhl.client.api.ClipActionResult.Success
                }
            },
        ) {
            override suspend fun refreshClips(kind: ClipListKind): ClipsRepositoryResult {
                onRefreshClips(kind)
                val result = clipsResults.getOrElse(clipResultIndex) { clipsResults.last() }
                clipResultIndex += 1
                return result
            }

            override suspend fun refreshNotes(clipId: String): ClipNotesRepositoryResult {
                onRefreshNotes(clipId)
                return notesResult
            }

            override suspend fun loadMoreNotes(
                clipId: String,
                currentNotes: List<Note>,
            ): ClipNotesRepositoryResult {
                return loadMoreResult
            }

            override suspend fun favoriteClip(clipId: String): ClipActionRepositoryResult {
                return actionResult
            }

            override suspend fun unfavoriteClip(clipId: String): ClipActionRepositoryResult {
                return actionResult
            }

            override suspend fun addNoteToClip(
                clipId: String,
                noteId: String,
            ): ClipActionRepositoryResult {
                onAddNote(clipId, noteId)
                return actionResult
            }

            override suspend fun removeNoteFromClip(
                clipId: String,
                noteId: String,
            ): ClipActionRepositoryResult {
                onRemoveNote(clipId, noteId)
                return actionResult
            }

            override suspend fun createClip(
                name: String,
                description: String,
                isPublic: Boolean,
            ): ClipCreateRepositoryResult {
                onCreateClip(name, description, isPublic)
                return createResult
            }

            override suspend fun updateClip(
                clipId: String,
                name: String,
                description: String,
                isPublic: Boolean,
            ): ClipUpdateRepositoryResult {
                onUpdateClip(clipId, name, description, isPublic)
                return updateResult
            }

            override suspend fun deleteClip(clipId: String): ClipActionRepositoryResult {
                onDeleteClip(clipId)
                return actionResult
            }
        }
    }

    private data class UpdateClipCall(
        val clipId: String,
        val name: String,
        val description: String,
        val isPublic: Boolean,
    )

    private fun sampleClip(id: String): Clip {
        return Clip(
            id = id,
            name = "Clip $id",
            description = "desc",
            owner = User("user-1", "Alice", "alice", "A"),
            ownerId = "user-1",
            isPublic = false,
            isFavorited = false,
            favoritedCount = 0,
            notesCount = 2,
            createdAtLabel = "2026-05-25 08:00",
            lastClippedAtLabel = "2026-05-25 09:00",
        )
    }
}
