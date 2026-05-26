package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.repository.AntennaActionRepositoryResult
import cc.hhhl.client.repository.AntennaMutationRepositoryResult
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.AntennaNotesRepositoryResult
import cc.hhhl.client.repository.AntennaRepository
import cc.hhhl.client.repository.AntennasRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AntennaStateHolderTest {
    @Test
    fun refreshAntennasStoresAntennasAndLoadsFirstNotes() = runTest {
        val antenna = sampleAntenna("antenna-1")
        val note = FakeData.timeline[0]
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(antenna)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        assertTrue(holder.state.value.isLoadingAntennas)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingAntennas)
        assertFalse(holder.state.value.isLoadingNotes)
        assertEquals(listOf(antenna), holder.state.value.antennas)
        assertEquals(antenna, holder.state.value.selectedAntenna)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun selectAntennaLoadsSelectedNotes() = runTest {
        val first = sampleAntenna("antenna-1")
        val second = sampleAntenna("antenna-2")
        val calls = mutableListOf<String>()
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(first, second)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                onRefreshNotes = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        holder.selectAntenna(second)
        assertTrue(holder.state.value.isLoadingNotes)
        advanceUntilIdle()

        assertEquals(second, holder.state.value.selectedAntenna)
        assertEquals(listOf("antenna-1", "antenna-2"), calls)
    }

    @Test
    fun loadMoreAppendsNotesAndMarksEndReached() = runTest {
        val antenna = sampleAntenna("antenna-1")
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(antenna)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(first)),
                loadMoreResult = AntennaNotesRepositoryResult.Success(
                    notes = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertTrue(holder.state.value.endReached)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun unauthorizedAntennaLoadMarksRelogin() = runTest {
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorizedAntennaLoad() = runTest {
        val antenna = sampleAntenna("antenna-1")
        var antennasResult: AntennasRepositoryResult = AntennasRepositoryResult.Unauthorized
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(emptyList()),
                antennasResultProvider = { antennasResult },
                notesResult = AntennaNotesRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        antennasResult = AntennasRepositoryResult.Success(listOf(antenna))
        holder.refreshAntennas()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(antenna), holder.state.value.antennas)
        assertEquals(antenna, holder.state.value.selectedAntenna)
    }

    @Test
    fun createAntennaPrependsAndSelectsCreatedAntenna() = runTest {
        val existing = sampleAntenna("antenna-1")
        val created = sampleAntenna("antenna-created").copy(name = "LLM")
        val calls = mutableListOf<AntennaDraft>()
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(existing)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                mutationResult = AntennaMutationRepositoryResult.Success(created),
                onCreateAntenna = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        holder.createAntenna(sampleDraft(name = " LLM "))
        assertTrue(holder.state.value.isMutatingAntenna)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingAntenna)
        assertEquals(listOf(sampleDraft(name = "LLM")), calls)
        assertEquals(listOf(created, existing), holder.state.value.antennas)
        assertEquals(created, holder.state.value.selectedAntenna)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun updateSelectedAntennaUpdatesListAndSelection() = runTest {
        val selected = sampleAntenna("antenna-1")
        val updated = selected.copy(name = "LLM")
        val calls = mutableListOf<Pair<String, AntennaDraft>>()
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(selected)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                mutationResult = AntennaMutationRepositoryResult.Success(updated),
                onUpdateAntenna = { antennaId, draft -> calls.add(antennaId to draft) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        holder.updateSelectedAntenna(sampleDraft(name = " LLM "))
        advanceUntilIdle()

        assertEquals(listOf("antenna-1" to sampleDraft(name = "LLM")), calls)
        assertEquals(updated, holder.state.value.selectedAntenna)
        assertEquals(updated, holder.state.value.antennas.single())
        assertEquals(listOf(FakeData.timeline[0]), holder.state.value.notes)
    }

    @Test
    fun deleteSelectedAntennaRemovesAndSelectsNext() = runTest {
        val first = sampleAntenna("antenna-1")
        val second = sampleAntenna("antenna-2")
        val calls = mutableListOf<String>()
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(listOf(first, second)),
                notesResult = AntennaNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                actionResult = AntennaActionRepositoryResult.Success,
                onDeleteAntenna = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        holder.deleteSelectedAntenna()
        advanceUntilIdle()

        assertEquals(listOf("antenna-1"), calls)
        assertEquals(listOf(second), holder.state.value.antennas)
        assertEquals(second, holder.state.value.selectedAntenna)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val antenna = sampleAntenna("antenna-1")
        val note = FakeData.timeline[0]
        var antennasResult: AntennasRepositoryResult = AntennasRepositoryResult.Success(listOf(antenna))
        val holder = AntennaStateHolder(
            repository = fakeRepository(
                antennasResult = AntennasRepositoryResult.Success(emptyList()),
                antennasResultProvider = { antennasResult },
                notesResult = AntennaNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshAntennas()
        advanceUntilIdle()
        antennasResult = AntennasRepositoryResult.Unauthorized
        holder.refreshAntennas()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    private fun fakeRepository(
        antennasResult: AntennasRepositoryResult,
        notesResult: AntennaNotesRepositoryResult = AntennaNotesRepositoryResult.Success(emptyList()),
        loadMoreResult: AntennaNotesRepositoryResult = notesResult,
        mutationResult: AntennaMutationRepositoryResult = AntennaMutationRepositoryResult.Success(sampleAntenna("antenna-mutated")),
        actionResult: AntennaActionRepositoryResult = AntennaActionRepositoryResult.Success,
        onRefreshNotes: (String) -> Unit = {},
        onCreateAntenna: (AntennaDraft) -> Unit = {},
        onUpdateAntenna: (String, AntennaDraft) -> Unit = { _, _ -> },
        onDeleteAntenna: (String) -> Unit = {},
        antennasResultProvider: (() -> AntennasRepositoryResult)? = null,
    ): AntennaRepository {
        return object : AntennaRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.AntennaApi {
                override suspend fun loadAntennas(token: String): cc.hhhl.client.api.AntennaLoadResult {
                    return cc.hhhl.client.api.AntennaLoadResult.Success(emptyList())
                }

                override suspend fun loadAntennaNotes(
                    token: String,
                    antennaId: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.AntennaNotesLoadResult {
                    return cc.hhhl.client.api.AntennaNotesLoadResult.Success(emptyList())
                }

                override suspend fun createAntenna(
                    token: String,
                    draft: AntennaDraft,
                ): cc.hhhl.client.api.AntennaMutationResult {
                    return cc.hhhl.client.api.AntennaMutationResult.Success(sampleAntenna("antenna-mutated"))
                }

                override suspend fun updateAntenna(
                    token: String,
                    antennaId: String,
                    draft: AntennaDraft,
                ): cc.hhhl.client.api.AntennaMutationResult {
                    return cc.hhhl.client.api.AntennaMutationResult.Success(sampleAntenna("antenna-mutated"))
                }

                override suspend fun deleteAntenna(
                    token: String,
                    antennaId: String,
                ): cc.hhhl.client.api.AntennaActionResult {
                    return cc.hhhl.client.api.AntennaActionResult.Success
                }
            },
        ) {
            override suspend fun refreshAntennas(): AntennasRepositoryResult {
                return antennasResultProvider?.invoke() ?: antennasResult
            }

            override suspend fun refreshNotes(antennaId: String): AntennaNotesRepositoryResult {
                onRefreshNotes(antennaId)
                return notesResult
            }

            override suspend fun loadMoreNotes(
                antennaId: String,
                currentNotes: List<Note>,
            ): AntennaNotesRepositoryResult {
                return loadMoreResult
            }

            override suspend fun createAntenna(draft: AntennaDraft): AntennaMutationRepositoryResult {
                onCreateAntenna(draft)
                return mutationResult
            }

            override suspend fun updateAntenna(
                antennaId: String,
                draft: AntennaDraft,
            ): AntennaMutationRepositoryResult {
                onUpdateAntenna(antennaId, draft)
                return mutationResult
            }

            override suspend fun deleteAntenna(antennaId: String): AntennaActionRepositoryResult {
                onDeleteAntenna(antennaId)
                return actionResult
            }
        }
    }

    private fun sampleDraft(name: String = "AGI"): AntennaDraft {
        return AntennaDraft(
            name = name.trim(),
            source = "all",
            keywords = listOf(listOf("AGI")),
            excludeBots = true,
        )
    }

    private fun sampleAntenna(id: String): Antenna {
        return Antenna(
            id = id,
            name = "Antenna $id",
            source = "all",
            keywords = listOf(listOf("AGI")),
            excludeKeywords = emptyList(),
            userListId = null,
            users = emptyList(),
            caseSensitive = false,
            localOnly = false,
            excludeBots = false,
            withReplies = false,
            withFile = false,
            isActive = true,
            hasUnreadNote = false,
            notify = false,
            excludeNotesInSensitiveChannel = true,
            createdAtLabel = "2026-05-25 07:00",
        )
    }
}
