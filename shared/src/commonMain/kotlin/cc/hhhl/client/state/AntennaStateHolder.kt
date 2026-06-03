package cc.hhhl.client.state

import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.AntennaActionRepositoryResult
import cc.hhhl.client.repository.AntennaMutationRepositoryResult
import cc.hhhl.client.repository.AntennaNotesRepositoryResult
import cc.hhhl.client.repository.AntennaRepository
import cc.hhhl.client.repository.AntennasRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AntennaUiState(
    val antennas: List<Antenna> = emptyList(),
    val selectedAntenna: Antenna? = null,
    val notes: List<Note> = emptyList(),
    val isLoadingAntennas: Boolean = false,
    val isLoadingNotes: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutatingAntenna: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val notesErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class AntennaStateHolder(
    private val repository: AntennaRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AntennaUiState())
    val state: StateFlow<AntennaUiState> = mutableState
    private var notesRequestId = 0

    fun refreshAntennas() {
        if (state.value.isLoadingAntennas) return

        mutableState.update {
            it.copy(
                isLoadingAntennas = true,
                isLoadingMore = false,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.refreshAntennas()) {
                is AntennasRepositoryResult.Success -> {
                    val currentSelectedId = state.value.selectedAntenna?.id
                    val selected = result.antennas.firstOrNull { it.id == currentSelectedId }
                        ?: result.antennas.firstOrNull()
                    mutableState.update {
                        it.copy(
                            antennas = result.antennas,
                            selectedAntenna = selected,
                            notes = if (selected == null) emptyList() else it.notes,
                            isLoadingAntennas = false,
                            errorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                    selected?.let { loadNotes(it.id, clearNotes = state.value.notes.isEmpty()) }
                }
                AntennasRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingAntennas = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AntennasRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingAntennas = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun selectAntenna(antenna: Antenna) {
        if (state.value.selectedAntenna?.id == antenna.id && state.value.notes.isNotEmpty()) return

        mutableState.update {
            it.copy(
                selectedAntenna = antenna,
                notes = emptyList(),
                isLoadingNotes = true,
                isLoadingMore = false,
                endReached = false,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        val requestId = nextNotesRequestId()
        scope.launch {
            applyNotesResult(
                result = repository.refreshNotes(antenna.id),
                loadingMore = false,
                antennaId = antenna.id,
                requestId = requestId,
            )
        }
    }

    fun refreshNotes() {
        val antenna = state.value.selectedAntenna ?: return
        if (state.value.isLoadingNotes) return
        loadNotes(antenna.id, clearNotes = false)
    }

    fun loadMore() {
        val current = state.value
        val antennaId = current.selectedAntenna?.id ?: return
        if (
            current.isLoadingNotes ||
            current.isLoadingMore ||
            current.notes.isEmpty() ||
            current.endReached
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        val requestId = nextNotesRequestId()
        scope.launch {
            applyNotesResult(
                result = repository.loadMoreNotes(antennaId, current.notes),
                loadingMore = true,
                antennaId = antennaId,
                requestId = requestId,
            )
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update {
            it.copy(
                notes = it.notes.applyNoteLocalMutation(mutation),
                requiresRelogin = false,
            )
        }
    }

    fun createAntenna(draft: AntennaDraft) {
        if (draft.name.trim().isEmpty() || state.value.isMutatingAntenna) return

        mutableState.update {
            it.copy(
                isMutatingAntenna = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.createAntenna(draft.copy(name = draft.name.trim()))) {
                is AntennaMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        antennas = listOf(result.antenna) + current.antennas.filterNot { it.id == result.antenna.id },
                        selectedAntenna = result.antenna,
                        notes = emptyList(),
                        isMutatingAntenna = false,
                        isLoadingNotes = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        notesErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AntennaMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingAntenna = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AntennaMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingAntenna = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateSelectedAntenna(draft: AntennaDraft) {
        val antenna = state.value.selectedAntenna ?: return
        if (draft.name.trim().isEmpty() || state.value.isMutatingAntenna) return

        mutableState.update {
            it.copy(
                isMutatingAntenna = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.updateAntenna(
                    antennaId = antenna.id,
                    draft = draft.copy(name = draft.name.trim()),
                )
            ) {
                is AntennaMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        antennas = current.antennas.map { item ->
                            if (item.id == result.antenna.id) result.antenna else item
                        },
                        selectedAntenna = current.selectedAntenna?.let {
                            if (it.id == result.antenna.id) result.antenna else it
                        },
                        isMutatingAntenna = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                AntennaMutationRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val updatingSelectedAntenna = current.selectedAntenna?.id == antenna.id
                    current.copy(
                        isMutatingAntenna = false,
                        errorMessage = if (updatingSelectedAntenna) "登录已失效，请重新登录" else current.errorMessage,
                        requiresRelogin = true,
                    )
                }
                is AntennaMutationRepositoryResult.Error -> mutableState.update { current ->
                    val updatingSelectedAntenna = current.selectedAntenna?.id == antenna.id
                    current.copy(
                        isMutatingAntenna = false,
                        errorMessage = if (updatingSelectedAntenna) result.message else current.errorMessage,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun deleteSelectedAntenna() {
        val antenna = state.value.selectedAntenna ?: return
        if (state.value.isMutatingAntenna) return

        mutableState.update {
            it.copy(
                isMutatingAntenna = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.deleteAntenna(antenna.id)) {
                AntennaActionRepositoryResult.Success -> mutableState.update { current ->
                    val remaining = current.antennas.filterNot { it.id == antenna.id }
                    val deletedSelectedAntenna = current.selectedAntenna?.id == antenna.id
                    current.copy(
                        antennas = remaining,
                        selectedAntenna = if (deletedSelectedAntenna) remaining.firstOrNull() else current.selectedAntenna,
                        notes = if (deletedSelectedAntenna) emptyList() else current.notes,
                        isMutatingAntenna = false,
                        isLoadingNotes = if (deletedSelectedAntenna) false else current.isLoadingNotes,
                        isLoadingMore = if (deletedSelectedAntenna) false else current.isLoadingMore,
                        endReached = if (deletedSelectedAntenna) false else current.endReached,
                        errorMessage = if (deletedSelectedAntenna) null else current.errorMessage,
                        notesErrorMessage = if (deletedSelectedAntenna) null else current.notesErrorMessage,
                        requiresRelogin = false,
                    )
                }
                AntennaActionRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val deletingSelectedAntenna = current.selectedAntenna?.id == antenna.id
                    current.copy(
                        isMutatingAntenna = false,
                        errorMessage = if (deletingSelectedAntenna) "登录已失效，请重新登录" else current.errorMessage,
                        requiresRelogin = true,
                    )
                }
                is AntennaActionRepositoryResult.Error -> mutableState.update { current ->
                    val deletingSelectedAntenna = current.selectedAntenna?.id == antenna.id
                    current.copy(
                        isMutatingAntenna = false,
                        errorMessage = if (deletingSelectedAntenna) result.message else current.errorMessage,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun loadNotes(
        antennaId: String,
        clearNotes: Boolean,
    ) {
        mutableState.update {
            it.copy(
                notes = if (clearNotes) emptyList() else it.notes,
                isLoadingNotes = true,
                isLoadingMore = false,
                endReached = false,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        val requestId = nextNotesRequestId()
        scope.launch {
            applyNotesResult(
                result = repository.refreshNotes(antennaId),
                loadingMore = false,
                antennaId = antennaId,
                requestId = requestId,
            )
        }
    }

    private fun applyNotesResult(
        result: AntennaNotesRepositoryResult,
        loadingMore: Boolean,
        antennaId: String,
        requestId: Int,
    ) {
        if (!isCurrentNotesRequest(antennaId, requestId)) return
        when (result) {
            is AntennaNotesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    isLoadingNotes = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    notesErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            AntennaNotesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingNotes = false,
                    isLoadingMore = false,
                    notesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is AntennaNotesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingNotes = if (loadingMore) it.isLoadingNotes else false,
                    isLoadingMore = false,
                    notesErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun nextNotesRequestId(): Int {
        notesRequestId += 1
        return notesRequestId
    }

    private fun isCurrentNotesRequest(
        antennaId: String,
        requestId: Int,
    ): Boolean {
        return requestId == notesRequestId && state.value.selectedAntenna?.id == antennaId
    }
}
