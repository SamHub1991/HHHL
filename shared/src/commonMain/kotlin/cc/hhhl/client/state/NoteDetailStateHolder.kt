package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.NoteDetailRepository
import cc.hhhl.client.repository.NoteDetailRepositoryResult
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteRepliesRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val noteId: String? = null,
    val note: Note? = null,
    val replies: List<Note> = emptyList(),
    val childRepliesByParentId: Map<String, List<Note>> = emptyMap(),
    val expandedReplyIds: Set<String> = emptySet(),
    val loadingChildReplyIds: Set<String> = emptySet(),
    val childReplyErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingReplies: Boolean = false,
    val isLoadingMoreReplies: Boolean = false,
    val errorMessage: String? = null,
    val repliesErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class NoteDetailStateHolder(
    private val repository: NoteDetailRepository,
    private val repliesRepository: NoteRepliesRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(NoteDetailUiState())
    val state: StateFlow<NoteDetailUiState> = mutableState

    fun load(noteId: String) {
        val current = state.value
        if (current.isLoading && current.noteId == noteId) return

        mutableState.update {
            it.copy(
                noteId = noteId,
                note = if (it.noteId == noteId) it.note else null,
                replies = if (it.noteId == noteId) it.replies else emptyList(),
                childRepliesByParentId = if (it.noteId == noteId) it.childRepliesByParentId else emptyMap(),
                expandedReplyIds = if (it.noteId == noteId) it.expandedReplyIds else emptySet(),
                loadingChildReplyIds = emptySet(),
                childReplyErrors = if (it.noteId == noteId) it.childReplyErrors else emptyMap(),
                isLoading = true,
                errorMessage = null,
                repliesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val shouldLoadReplies = applyResult(noteId, repository.load(noteId))
            if (shouldLoadReplies) {
                refreshReplies(noteId)
            }
        }
    }

    fun loadMoreReplies() {
        val repository = repliesRepository ?: return
        val current = state.value
        val noteId = current.noteId ?: return
        if (
            current.isLoading ||
            current.isLoadingReplies ||
            current.isLoadingMoreReplies ||
            current.replies.isEmpty()
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMoreReplies = true,
                repliesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyRepliesResult(
                noteId = noteId,
                result = repository.loadMore(noteId, current.replies),
                loadingMore = true,
            )
        }
    }

    fun toggleChildReplies(parentId: String) {
        val repository = repliesRepository ?: return
        val cleanParentId = parentId.takeIf { it.isNotBlank() } ?: return
        val current = state.value
        if (cleanParentId in current.loadingChildReplyIds) return

        if (cleanParentId in current.expandedReplyIds) {
            mutableState.update {
                it.copy(
                    expandedReplyIds = it.expandedReplyIds - cleanParentId,
                    requiresRelogin = false,
                )
            }
            return
        }

        val cachedChildren = current.childRepliesByParentId[cleanParentId]
        mutableState.update {
            it.copy(
                expandedReplyIds = it.expandedReplyIds + cleanParentId,
                childReplyErrors = it.childReplyErrors - cleanParentId,
                requiresRelogin = false,
            )
        }
        if (cachedChildren != null) return

        mutableState.update {
            it.copy(
                loadingChildReplyIds = it.loadingChildReplyIds + cleanParentId,
                childReplyErrors = it.childReplyErrors - cleanParentId,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyChildRepliesResult(
                parentId = cleanParentId,
                result = repository.loadChildren(cleanParentId),
            )
        }
    }

    private fun prefetchVisibleChildReplies(parentIds: List<String>) {
        val repository = repliesRepository ?: return
        val cleanParentIds = parentIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(AUTO_PREFETCH_CHILD_PARENT_LIMIT)
        if (cleanParentIds.isEmpty()) return

        val current = state.value
        val idsToLoad = cleanParentIds.filter { parentId ->
            parentId !in current.childRepliesByParentId &&
                parentId !in current.loadingChildReplyIds
        }
        if (idsToLoad.isEmpty()) {
            mutableState.update {
                it.copy(expandedReplyIds = it.expandedReplyIds + cleanParentIds)
            }
            return
        }

        mutableState.update {
            it.copy(
                expandedReplyIds = it.expandedReplyIds + cleanParentIds,
                loadingChildReplyIds = it.loadingChildReplyIds + idsToLoad,
                childReplyErrors = it.childReplyErrors - idsToLoad.toSet(),
                requiresRelogin = false,
            )
        }

        scope.launch {
            idsToLoad.forEach { parentId ->
                applyChildRepliesResult(
                    parentId = parentId,
                    result = repository.loadChildren(parentId),
                )
            }
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        mutableState.update { current ->
            current.copy(
                requiresRelogin = false,
                note = current.note?.let { note ->
                    listOf(note).applyNoteLocalMutation(mutation).firstOrNull()
                },
                replies = current.replies.applyNoteLocalMutation(mutation),
                childRepliesByParentId = current.childRepliesByParentId.mapValues { (_, children) ->
                    children.applyNoteLocalMutation(mutation)
                },
            )
        }
    }

    private fun applyResult(
        noteId: String,
        result: NoteDetailRepositoryResult,
    ): Boolean {
        if (state.value.noteId != noteId) return false

        when (result) {
            is NoteDetailRepositoryResult.Success -> {
                mutableState.update {
                    it.copy(
                        note = result.note,
                        isLoading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                return true
            }
            NoteDetailRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is NoteDetailRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }

        return false
    }

    private suspend fun refreshReplies(noteId: String) {
        val repository = repliesRepository ?: return

        mutableState.update {
            it.copy(
                isLoadingReplies = true,
                repliesErrorMessage = null,
                requiresRelogin = false,
            )
        }

        applyRepliesResult(
            noteId = noteId,
            result = repository.refresh(noteId),
            loadingMore = false,
        )
    }

    private fun applyRepliesResult(
        noteId: String,
        result: NoteRepliesRepositoryResult,
        loadingMore: Boolean,
    ) {
        if (state.value.noteId != noteId) return

        when (result) {
            is NoteRepliesRepositoryResult.Success -> {
                mutableState.update {
                    it.copy(
                        replies = result.replies,
                        childRepliesByParentId = if (loadingMore) it.childRepliesByParentId else emptyMap(),
                        expandedReplyIds = if (loadingMore) it.expandedReplyIds else emptySet(),
                        loadingChildReplyIds = if (loadingMore) it.loadingChildReplyIds else emptySet(),
                        childReplyErrors = if (loadingMore) it.childReplyErrors else emptyMap(),
                        isLoadingReplies = false,
                        isLoadingMoreReplies = false,
                        repliesErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                prefetchVisibleChildReplies(
                    result.replies
                        .filter { it.replyCount > 0 }
                        .map { it.id },
                )
            }
            NoteRepliesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingReplies = false,
                    isLoadingMoreReplies = false,
                    repliesErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is NoteRepliesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingReplies = if (loadingMore) it.isLoadingReplies else false,
                    isLoadingMoreReplies = false,
                    repliesErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyChildRepliesResult(
        parentId: String,
        result: NoteRepliesRepositoryResult,
    ) {
        when (result) {
            is NoteRepliesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    childRepliesByParentId = it.childRepliesByParentId + (parentId to result.replies),
                    loadingChildReplyIds = it.loadingChildReplyIds - parentId,
                    childReplyErrors = it.childReplyErrors - parentId,
                    requiresRelogin = false,
                )
            }
            NoteRepliesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    loadingChildReplyIds = it.loadingChildReplyIds - parentId,
                    childReplyErrors = it.childReplyErrors + (parentId to "登录已失效，请重新登录"),
                    requiresRelogin = true,
                )
            }
            is NoteRepliesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    loadingChildReplyIds = it.loadingChildReplyIds - parentId,
                    childReplyErrors = it.childReplyErrors + (parentId to result.message),
                    requiresRelogin = false,
                )
            }
        }
    }

    private companion object {
        const val AUTO_PREFETCH_CHILD_PARENT_LIMIT = 8
    }
}
