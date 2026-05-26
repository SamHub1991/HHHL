package cc.hhhl.client.repository

import cc.hhhl.client.api.AntennaApi
import cc.hhhl.client.api.AntennaActionResult
import cc.hhhl.client.api.AntennaLoadResult
import cc.hhhl.client.api.AntennaMutationResult
import cc.hhhl.client.api.AntennaNotesLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AntennaRepositoryTest {
    @Test
    fun refreshAntennasUsesTokenAndMapsAntennas() = runTest {
        val antennas = listOf(sampleAntenna("antenna-1"))
        val calls = mutableListOf<String>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                antennaCalls = calls,
                antennaResult = AntennaLoadResult.Success(antennas),
            ),
        )

        val result = repository.refreshAntennas()

        assertIs<AntennasRepositoryResult.Success>(result)
        assertEquals(listOf("token-123"), calls)
        assertEquals(antennas, result.antennas)
    }

    @Test
    fun refreshNotesUsesTokenAndAntennaId() = runTest {
        val calls = mutableListOf<NotesCall>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                notesCalls = calls,
                notesResult = AntennaNotesLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refreshNotes("antenna-1")

        assertIs<AntennaNotesRepositoryResult.Success>(result)
        assertEquals(listOf(NotesCall("token-123", "antenna-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreNotesUsesLastNoteIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val calls = mutableListOf<NotesCall>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                notesCalls = calls,
                notesResult = AntennaNotesLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMoreNotes(
            antennaId = "antenna-1",
            currentNotes = listOf(first),
        )

        assertIs<AntennaNotesRepositoryResult.Success>(result)
        assertEquals(listOf(NotesCall("token-123", "antenna-1", first.id)), calls)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = AntennaRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<AntennasRepositoryResult.Unauthorized>(repository.refreshAntennas())
        assertIs<AntennaNotesRepositoryResult.Unauthorized>(repository.refreshNotes("antenna-1"))
        assertEquals(0, calls)
    }

    @Test
    fun createAntennaUsesTokenAndCleanDraft() = runTest {
        val created = sampleAntenna("antenna-created")
        val calls = mutableListOf<MutationCall>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                mutationResult = AntennaMutationResult.Success(created),
            ),
        )

        val result = repository.createAntenna(sampleDraft(name = "  AGI  "))

        assertIs<AntennaMutationRepositoryResult.Success>(result)
        assertEquals(created, result.antenna)
        assertEquals("create", calls.single().action)
        assertEquals("token-123", calls.single().token)
        assertEquals("AGI", calls.single().draft.name)
    }

    @Test
    fun updateAntennaUsesTokenIdAndCleanDraft() = runTest {
        val updated = sampleAntenna("antenna-1").copy(name = "LLM")
        val calls = mutableListOf<MutationCall>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                mutationResult = AntennaMutationResult.Success(updated),
            ),
        )

        val result = repository.updateAntenna(" antenna-1 ", sampleDraft(name = " LLM "))

        assertIs<AntennaMutationRepositoryResult.Success>(result)
        assertEquals(updated, result.antenna)
        assertEquals(MutationCall("update", "token-123", "antenna-1", sampleDraft(name = "LLM")), calls.single())
    }

    @Test
    fun deleteAntennaUsesTokenAndId() = runTest {
        val calls = mutableListOf<String>()
        val repository = AntennaRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteCalls = calls,
                actionResult = AntennaActionResult.Success,
            ),
        )

        assertEquals(AntennaActionRepositoryResult.Success, repository.deleteAntenna(" antenna-1 "))
        assertEquals(listOf("token-123:antenna-1"), calls)
    }

    private fun fakeApi(
        antennaCalls: MutableList<String> = mutableListOf(),
        notesCalls: MutableList<NotesCall> = mutableListOf(),
        mutationCalls: MutableList<MutationCall> = mutableListOf(),
        deleteCalls: MutableList<String> = mutableListOf(),
        antennaResult: AntennaLoadResult = AntennaLoadResult.Success(emptyList()),
        notesResult: AntennaNotesLoadResult = AntennaNotesLoadResult.Success(emptyList()),
        mutationResult: AntennaMutationResult = AntennaMutationResult.Success(sampleAntenna("antenna-mutated")),
        actionResult: AntennaActionResult = AntennaActionResult.Success,
        onCall: () -> Unit = {},
    ): AntennaApi {
        return object : AntennaApi {
            override suspend fun loadAntennas(token: String): AntennaLoadResult {
                onCall()
                antennaCalls.add(token)
                return antennaResult
            }

            override suspend fun loadAntennaNotes(
                token: String,
                antennaId: String,
                limit: Int,
                untilId: String?,
            ): AntennaNotesLoadResult {
                onCall()
                notesCalls.add(NotesCall(token, antennaId, untilId))
                return notesResult
            }

            override suspend fun createAntenna(
                token: String,
                draft: AntennaDraft,
            ): AntennaMutationResult {
                mutationCalls.add(MutationCall("create", token, null, draft))
                return mutationResult
            }

            override suspend fun updateAntenna(
                token: String,
                antennaId: String,
                draft: AntennaDraft,
            ): AntennaMutationResult {
                mutationCalls.add(MutationCall("update", token, antennaId, draft))
                return mutationResult
            }

            override suspend fun deleteAntenna(
                token: String,
                antennaId: String,
            ): AntennaActionResult {
                deleteCalls.add("$token:$antennaId")
                return actionResult
            }
        }
    }

    private fun sampleDraft(name: String = "AGI"): AntennaDraft {
        return AntennaDraft(
            name = name,
            source = "all",
            keywords = listOf(listOf("AGI")),
            excludeBots = true,
        )
    }

    private fun sampleAntenna(id: String): Antenna {
        return Antenna(
            id = id,
            name = "AGI",
            source = "all",
            keywords = listOf(listOf("AGI")),
            excludeKeywords = emptyList(),
            userListId = null,
            users = emptyList(),
            caseSensitive = false,
            localOnly = false,
            excludeBots = true,
            withReplies = false,
            withFile = false,
            isActive = true,
            hasUnreadNote = false,
            notify = false,
            excludeNotesInSensitiveChannel = true,
            createdAtLabel = "2026-05-25 07:00",
        )
    }

    private data class NotesCall(
        val token: String,
        val antennaId: String,
        val untilId: String?,
    )

    private data class MutationCall(
        val action: String,
        val token: String,
        val antennaId: String?,
        val draft: AntennaDraft,
    )
}
