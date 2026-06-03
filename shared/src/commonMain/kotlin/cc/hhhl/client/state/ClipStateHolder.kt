package cc.hhhl.client.state

import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.ClipActionRepositoryResult
import cc.hhhl.client.repository.ClipCreateRepositoryResult
import cc.hhhl.client.repository.ClipNotesRepositoryResult
import cc.hhhl.client.repository.ClipRepository
import cc.hhhl.client.repository.ClipUpdateRepositoryResult
import cc.hhhl.client.repository.ClipsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClipUiState(
    val selectedKind: ClipListKind = ClipListKind.Owned,
    val clips: List<Clip> = emptyList(),
    val selectedClip: Clip? = null,
    val notes: List<Note> = emptyList(),
    val isLoadingClips: Boolean = false,
    val isLoadingNotes: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isCreatingClip: Boolean = false,
    val isUpdatingClip: Boolean = false,
    val isDeletingClip: Boolean = false,
    val isChangingFavorite: Boolean = false,
    val isChangingClipNote: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val notesErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class ClipStateHolder(
    private val repository: ClipRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ClipUiState())
    val state: StateFlow<ClipUiState> = mutableState
    private var notesRequestId = 0

    fun refreshClips(kind: ClipListKind = state.value.selectedKind) {
        if (state.value.isLoadingClips) return

        val previousState = state.value
        val previousSelectedId = if (previousState.selectedKind == kind) {
            previousState.selectedClip?.id
        } else {
            null
        }

        mutableState.update {
            it.copy(
                selectedKind = kind,
                notes = if (it.selectedKind == kind) it.notes else emptyList(),
                isLoadingClips = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.refreshClips(kind)) {
                is ClipsRepositoryResult.Success -> {
                    val selected = result.clips.firstOrNull { it.id == previousSelectedId }
                        ?: result.clips.firstOrNull()
                    mutableState.update {
                        it.copy(
                            clips = result.clips,
                            selectedClip = selected,
                            notes = if (selected == null) emptyList() else it.notes,
                            isLoadingClips = false,
                            errorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                    selected?.let { clip -> loadNotes(clip.id, clearNotes = state.value.notes.isEmpty()) }
                }
                ClipsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingClips = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ClipsRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingClips = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun selectKind(kind: ClipListKind) {
        if (state.value.selectedKind == kind && state.value.clips.isNotEmpty()) return
        refreshClips(kind)
    }

    fun selectClip(clip: Clip) {
        if (state.value.selectedClip?.id == clip.id && state.value.notes.isNotEmpty()) return

        mutableState.update {
            it.copy(
                selectedClip = clip,
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
                result = repository.refreshNotes(clip.id),
                loadingMore = false,
                clipId = clip.id,
                requestId = requestId,
            )
        }
    }

    fun refreshNotes() {
        val clip = state.value.selectedClip ?: return
        if (state.value.isLoadingNotes) return
        loadNotes(clip.id, clearNotes = false)
    }

    fun loadMore() {
        val current = state.value
        val clipId = current.selectedClip?.id ?: return
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
                result = repository.loadMoreNotes(clipId, current.notes),
                loadingMore = true,
                clipId = clipId,
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

    fun toggleFavoriteSelectedClip() {
        val clip = state.value.selectedClip ?: return
        if (state.value.isChangingFavorite) return

        mutableState.update {
            it.copy(
                isChangingFavorite = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = if (clip.isFavorited) {
                repository.unfavoriteClip(clip.id)
            } else {
                repository.favoriteClip(clip.id)
            }
            applyFavoriteResult(clip, result)
        }
    }

    fun createClip(
        name: String,
        description: String,
        isPublic: Boolean,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || state.value.isCreatingClip) return

        mutableState.update {
            it.copy(
                selectedKind = ClipListKind.Owned,
                isCreatingClip = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.createClip(
                    name = cleanName,
                    description = description.trim(),
                    isPublic = isPublic,
                )
            ) {
                is ClipCreateRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        clips = listOf(result.clip) + current.clips.filterNot { it.id == result.clip.id },
                        selectedClip = result.clip,
                        notes = emptyList(),
                        isCreatingClip = false,
                        isLoadingNotes = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        notesErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ClipCreateRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isCreatingClip = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ClipCreateRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isCreatingClip = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateSelectedClip(
        name: String,
        description: String,
        isPublic: Boolean,
    ) {
        val clip = state.value.selectedClip ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty() || state.value.isUpdatingClip || state.value.isDeletingClip) return

        mutableState.update {
            it.copy(
                isUpdatingClip = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.updateClip(
                    clipId = clip.id,
                    name = cleanName,
                    description = description.trim(),
                    isPublic = isPublic,
                )
            ) {
                is ClipUpdateRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        clips = current.clips.map { item ->
                            if (item.id == result.clip.id) result.clip else item
                        },
                        selectedClip = current.selectedClip?.let {
                            if (it.id == result.clip.id) result.clip else it
                        },
                        isUpdatingClip = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ClipUpdateRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isUpdatingClip = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ClipUpdateRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isUpdatingClip = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun deleteSelectedClip() {
        val clip = state.value.selectedClip ?: return
        if (state.value.isDeletingClip) return

        mutableState.update {
            it.copy(
                isDeletingClip = true,
                errorMessage = null,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.deleteClip(clip.id)) {
                ClipActionRepositoryResult.Success -> mutableState.update { current ->
                    val remaining = current.clips.filterNot { it.id == clip.id }
                    val deletedSelectedClip = current.selectedClip?.id == clip.id
                    current.copy(
                        clips = remaining,
                        selectedClip = if (deletedSelectedClip) remaining.firstOrNull() else current.selectedClip,
                        notes = if (deletedSelectedClip) emptyList() else current.notes,
                        isDeletingClip = false,
                        isLoadingNotes = if (deletedSelectedClip) false else current.isLoadingNotes,
                        isLoadingMore = if (deletedSelectedClip) false else current.isLoadingMore,
                        endReached = if (deletedSelectedClip) false else current.endReached,
                        errorMessage = if (deletedSelectedClip) null else current.errorMessage,
                        notesErrorMessage = if (deletedSelectedClip) null else current.notesErrorMessage,
                        requiresRelogin = false,
                    )
                }
                ClipActionRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val deletingSelectedClip = current.selectedClip?.id == clip.id
                    current.copy(
                        isDeletingClip = false,
                        errorMessage = if (deletingSelectedClip) "登录已失效，请重新登录" else current.errorMessage,
                        requiresRelogin = true,
                    )
                }
                is ClipActionRepositoryResult.Error -> mutableState.update { current ->
                    val deletingSelectedClip = current.selectedClip?.id == clip.id
                    current.copy(
                        isDeletingClip = false,
                        errorMessage = if (deletingSelectedClip) result.message else current.errorMessage,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun removeNoteFromSelectedClip(noteId: String) {
        val clip = state.value.selectedClip ?: return
        val cleanNoteId = noteId.trim()
        if (cleanNoteId.isEmpty() || state.value.isChangingClipNote) return

        mutableState.update {
            it.copy(
                isChangingClipNote = true,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyClipNoteActionResult(
                clip = clip,
                noteId = cleanNoteId,
                result = repository.removeNoteFromClip(clip.id, cleanNoteId),
            )
        }
    }

    fun addNoteToSelectedClip(note: Note) {
        val clip = state.value.selectedClip ?: return
        addNoteToClip(clip, note)
    }

    fun addNoteToClip(
        clip: Clip,
        note: Note,
    ) {
        val cleanClipId = clip.id.trim()
        val cleanNoteId = note.id.trim()
        if (cleanClipId.isEmpty() || cleanNoteId.isEmpty() || state.value.isChangingClipNote) return

        mutableState.update {
            it.copy(
                isChangingClipNote = true,
                notesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyAddClipNoteActionResult(
                clip = clip.copy(id = cleanClipId),
                note = note,
                result = repository.addNoteToClip(cleanClipId, cleanNoteId),
            )
        }
    }

    private fun loadNotes(
        clipId: String,
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
                result = repository.refreshNotes(clipId),
                loadingMore = false,
                clipId = clipId,
                requestId = requestId,
            )
        }
    }

    private fun applyNotesResult(
        result: ClipNotesRepositoryResult,
        loadingMore: Boolean,
        clipId: String,
        requestId: Int,
    ) {
        if (!isCurrentNotesRequest(clipId, requestId)) return
        when (result) {
            is ClipNotesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    isLoadingNotes = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    notesErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ClipNotesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingNotes = false,
                    isLoadingMore = false,
                    notesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ClipNotesRepositoryResult.Error -> mutableState.update {
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
        clipId: String,
        requestId: Int,
    ): Boolean {
        return requestId == notesRequestId && state.value.selectedClip?.id == clipId
    }

    private fun applyFavoriteResult(
        originalClip: Clip,
        result: ClipActionRepositoryResult,
    ) {
        when (result) {
            ClipActionRepositoryResult.Success -> mutableState.update { current ->
                val nowFavorited = !originalClip.isFavorited
                val delta = if (nowFavorited) 1 else -1
                val selected = current.selectedClip?.takeIf { it.id == originalClip.id }
                val updatedSelected = selected?.copy(
                    isFavorited = nowFavorited,
                    favoritedCount = (selected.favoritedCount + delta).coerceAtLeast(0),
                )
                current.copy(
                    clips = current.clips.map { clip ->
                        if (clip.id == originalClip.id) {
                            clip.copy(
                                isFavorited = nowFavorited,
                                favoritedCount = (clip.favoritedCount + delta).coerceAtLeast(0),
                            )
                        } else {
                            clip
                        }
                    },
                    selectedClip = updatedSelected ?: current.selectedClip,
                    isChangingFavorite = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            ClipActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingFavorite = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ClipActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingFavorite = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyClipNoteActionResult(
        clip: Clip,
        noteId: String,
        result: ClipActionRepositoryResult,
    ) {
        when (result) {
            ClipActionRepositoryResult.Success -> mutableState.update { current ->
                val selected = current.selectedClip?.takeIf { it.id == clip.id }
                val updatedSelected = selected?.copy(
                    notesCount = (selected.notesCount - 1).coerceAtLeast(0),
                )
                current.copy(
                    clips = current.clips.map { item ->
                        if (item.id == clip.id) {
                            item.copy(notesCount = (item.notesCount - 1).coerceAtLeast(0))
                        } else {
                            item
                        }
                    },
                    selectedClip = updatedSelected ?: current.selectedClip,
                    notes = current.notes.filterNot { it.id == noteId },
                    isChangingClipNote = false,
                    notesErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ClipActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingClipNote = false,
                    notesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ClipActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingClipNote = false,
                    notesErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyAddClipNoteActionResult(
        clip: Clip,
        note: Note,
        result: ClipActionRepositoryResult,
    ) {
        when (result) {
            ClipActionRepositoryResult.Success -> mutableState.update { current ->
                val selected = current.selectedClip?.takeIf { it.id == clip.id }
                val alreadyPresentInSelected = selected != null && current.notes.any { it.id == note.id }
                val countDelta = if (alreadyPresentInSelected) 0 else 1
                val updatedSelected = selected?.copy(
                    notesCount = selected.notesCount + countDelta,
                )
                current.copy(
                    clips = current.clips.map { item ->
                        if (item.id == clip.id) {
                            item.copy(notesCount = item.notesCount + countDelta)
                        } else {
                            item
                        }
                    },
                    selectedClip = updatedSelected ?: current.selectedClip,
                    notes = if (selected == null || alreadyPresentInSelected) {
                        current.notes
                    } else {
                        listOf(note) + current.notes
                    },
                    isChangingClipNote = false,
                    notesErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            ClipActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingClipNote = false,
                    notesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is ClipActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingClipNote = false,
                    notesErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
