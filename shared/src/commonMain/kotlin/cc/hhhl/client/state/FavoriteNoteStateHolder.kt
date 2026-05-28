package cc.hhhl.client.state

import cc.hhhl.client.model.FavoriteNote
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.FavoriteNoteRepository
import cc.hhhl.client.repository.FavoriteNotesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteNoteUiState(
    val favorites: List<FavoriteNote> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class FavoriteNoteStateHolder(
    private val repository: FavoriteNoteRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FavoriteNoteUiState())
    val state: StateFlow<FavoriteNoteUiState> = mutableState
    private var listRequestId = 0

    fun refresh() {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(isLoading = true, errorMessage = null, requiresRelogin = false)
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyResult(repository.refresh(), loadingMore = false, requestId = requestId)
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.favorites.isEmpty()) return

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        val requestId = nextListRequestId()
        scope.launch {
            applyResult(repository.loadMore(current.favorites), loadingMore = true, requestId = requestId)
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update { current ->
            current.copy(
                favorites = when (mutation) {
                    is NoteLocalMutation.Unfavorite -> current.favorites.filterNot {
                        it.note.id == mutation.noteId
                    }
                    else -> current.favorites.mapNotNull { favorite ->
                        listOf(favorite.note)
                            .applyNoteLocalMutation(mutation)
                            .firstOrNull()
                            ?.let { favorite.copy(note = it) }
                    }
                },
                requiresRelogin = false,
            )
        }
    }

    fun addLocalFavorite(note: Note) {
        if (note.id.isBlank()) return
        mutableState.update { current ->
            val favorite = FavoriteNote(
                id = "local-${note.id}",
                createdAtLabel = "刚刚",
                note = note.copy(isFavorited = true),
            )
            current.copy(
                favorites = listOf(favorite) + current.favorites.filterNot { it.note.id == note.id },
                requiresRelogin = false,
            )
        }
    }

    private fun applyResult(
        result: FavoriteNotesRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
    ) {
        if (requestId != listRequestId) return
        when (result) {
            is FavoriteNotesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    favorites = result.favorites,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = loadingMore && result.favorites.size == it.favorites.size,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            FavoriteNotesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is FavoriteNotesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun nextListRequestId(): Int {
        listRequestId += 1
        return listRequestId
    }
}
