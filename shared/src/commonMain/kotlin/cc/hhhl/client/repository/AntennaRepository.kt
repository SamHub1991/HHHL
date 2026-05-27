package cc.hhhl.client.repository

import cc.hhhl.client.api.AntennaApi
import cc.hhhl.client.api.AntennaActionResult
import cc.hhhl.client.api.AntennaLoadResult
import cc.hhhl.client.api.AntennaMutationResult
import cc.hhhl.client.api.AntennaNotesLoadResult
import cc.hhhl.client.api.SharkeyAntennaApi
import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.model.Note

open class AntennaRepository(
    private val tokenProvider: () -> String?,
    private val api: AntennaApi = SharkeyAntennaApi(),
) {
    open suspend fun refreshAntennas(): AntennasRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AntennasRepositoryResult.Unauthorized

        return when (val result = api.loadAntennas(token)) {
            is AntennaLoadResult.Success -> AntennasRepositoryResult.Success(result.antennas)
            AntennaLoadResult.Unauthorized -> AntennasRepositoryResult.Unauthorized
            is AntennaLoadResult.NetworkError -> {
                AntennasRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AntennaLoadResult.ServerError -> AntennasRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshNotes(antennaId: String): AntennaNotesRepositoryResult {
        return loadNotes(
            antennaId = antennaId,
            currentNotes = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreNotes(
        antennaId: String,
        currentNotes: List<Note>,
    ): AntennaNotesRepositoryResult {
        return loadNotes(
            antennaId = antennaId,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    open suspend fun createAntenna(draft: AntennaDraft): AntennaMutationRepositoryResult {
        val cleanDraft = draft.cleaned()
        if (cleanDraft.name.isEmpty()) {
            return AntennaMutationRepositoryResult.Error("请输入天线名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AntennaMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.createAntenna(token, cleanDraft))
    }

    open suspend fun updateAntenna(
        antennaId: String,
        draft: AntennaDraft,
    ): AntennaMutationRepositoryResult {
        val cleanAntennaId = antennaId.trim()
        val cleanDraft = draft.cleaned()
        if (cleanAntennaId.isEmpty()) {
            return AntennaMutationRepositoryResult.Error("无法读取天线")
        }
        if (cleanDraft.name.isEmpty()) {
            return AntennaMutationRepositoryResult.Error("请输入天线名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AntennaMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.updateAntenna(token, cleanAntennaId, cleanDraft))
    }

    open suspend fun deleteAntenna(antennaId: String): AntennaActionRepositoryResult {
        val cleanAntennaId = antennaId.trim()
        if (cleanAntennaId.isEmpty()) {
            return AntennaActionRepositoryResult.Error("无法读取天线")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AntennaActionRepositoryResult.Unauthorized

        return when (val result = api.deleteAntenna(token, cleanAntennaId)) {
            AntennaActionResult.Success -> AntennaActionRepositoryResult.Success
            AntennaActionResult.Unauthorized -> AntennaActionRepositoryResult.Unauthorized
            is AntennaActionResult.NetworkError -> {
                AntennaActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AntennaActionResult.ServerError -> AntennaActionRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadNotes(
        antennaId: String,
        currentNotes: List<Note>,
        untilId: String?,
    ): AntennaNotesRepositoryResult {
        val cleanAntennaId = antennaId.trim()
        if (cleanAntennaId.isEmpty()) {
            return AntennaNotesRepositoryResult.Error("无法读取天线")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AntennaNotesRepositoryResult.Unauthorized

        return when (
            val result = api.loadAntennaNotes(
                token = token,
                antennaId = cleanAntennaId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is AntennaNotesLoadResult.Success -> AntennaNotesRepositoryResult.Success(
                notes = currentNotes.appendDistinctBy(result.notes) { it.id },
                endReached = result.notes.isEmpty(),
            )
            AntennaNotesLoadResult.Unauthorized -> AntennaNotesRepositoryResult.Unauthorized
            is AntennaNotesLoadResult.NetworkError -> {
                AntennaNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AntennaNotesLoadResult.ServerError -> AntennaNotesRepositoryResult.Error(result.message)
        }
    }

    private fun mapMutationResult(result: AntennaMutationResult): AntennaMutationRepositoryResult {
        return when (result) {
            is AntennaMutationResult.Success -> AntennaMutationRepositoryResult.Success(result.antenna)
            AntennaMutationResult.Unauthorized -> AntennaMutationRepositoryResult.Unauthorized
            is AntennaMutationResult.NetworkError -> {
                AntennaMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is AntennaMutationResult.ServerError -> AntennaMutationRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

private fun AntennaDraft.cleaned(): AntennaDraft {
    return copy(
        name = name.trim(),
        source = source.trim().ifBlank { "all" },
        keywords = keywords.cleanedKeywordGroups(),
        excludeKeywords = excludeKeywords.cleanedKeywordGroups(),
        userListId = userListId?.trim()?.takeIf { it.isNotBlank() },
        users = users.mapNotNull { it.trim().takeIf(String::isNotBlank) },
    )
}

private fun List<List<String>>.cleanedKeywordGroups(): List<List<String>> {
    return mapNotNull { group ->
        group.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .takeIf { it.isNotEmpty() }
    }
}

sealed interface AntennasRepositoryResult {
    data class Success(val antennas: List<Antenna>) : AntennasRepositoryResult

    data object Unauthorized : AntennasRepositoryResult

    data class Error(val message: String) : AntennasRepositoryResult
}

sealed interface AntennaNotesRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : AntennaNotesRepositoryResult

    data object Unauthorized : AntennaNotesRepositoryResult

    data class Error(val message: String) : AntennaNotesRepositoryResult
}

sealed interface AntennaMutationRepositoryResult {
    data class Success(val antenna: Antenna) : AntennaMutationRepositoryResult

    data object Unauthorized : AntennaMutationRepositoryResult

    data class Error(val message: String) : AntennaMutationRepositoryResult
}

sealed interface AntennaActionRepositoryResult {
    data object Success : AntennaActionRepositoryResult

    data object Unauthorized : AntennaActionRepositoryResult

    data class Error(val message: String) : AntennaActionRepositoryResult
}
