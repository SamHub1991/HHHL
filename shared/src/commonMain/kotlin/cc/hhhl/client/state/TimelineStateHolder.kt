package cc.hhhl.client.state

import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteRepliesRepositoryResult
import cc.hhhl.client.repository.TimelineRepository
import cc.hhhl.client.repository.TimelineRepositoryResult
import cc.hhhl.client.repository.appendDistinctBy
import cc.hhhl.client.repository.prependDistinctBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    val selectedKind: TimelineKind = TimelineKind.Home,
    val tabs: Map<TimelineKind, TimelineTabState> = TimelineKind.entries.associateWith {
        TimelineTabState()
    },
    val requiresRelogin: Boolean = false,
)

data class TimelineTabState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val firstUnreadNoteId: String? = null,
    val newNoteCount: Int = 0,
    val errorMessage: String? = null,
)

class TimelineStateHolder(
    private val repository: TimelineRepository,
    private val repliesRepository: NoteRepliesRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = mutableState
    private var quietRefreshingKinds: Set<TimelineKind> = emptySet()

    fun select(kind: TimelineKind) {
        mutableState.update { it.copy(selectedKind = kind, requiresRelogin = false) }
        val tab = state.value.tabs.getValue(kind)
        if (tab.notes.isEmpty() && !tab.isLoading) {
            refresh(kind)
        }
    }

    fun refresh(kind: TimelineKind = state.value.selectedKind) {
        val tab = state.value.tabs.getValue(kind)
        if (tab.isLoading) return
        val previousFirstNoteId = tab.notes.firstOrNull()?.id

        mutableState.update { current ->
            current.copy(
                requiresRelogin = false,
                tabs = current.tabs + (kind to current.tabs.getValue(kind).copy(
                    isLoading = true,
                    endReached = false,
                    errorMessage = null,
                )),
            )
        }

        scope.launch {
            restoreCached(kind)
            applyResult(
                kind = kind,
                loadingMore = false,
                result = repository.refresh(kind),
                previousFirstNoteId = previousFirstNoteId,
            )
        }
    }

    fun refreshQuietly(kind: TimelineKind = state.value.selectedKind) {
        val tab = state.value.tabs.getValue(kind)
        if (tab.isLoading || tab.isLoadingMore || kind in quietRefreshingKinds) return
        quietRefreshingKinds = quietRefreshingKinds + kind

        scope.launch {
            try {
                applyQuietRefreshResult(
                    kind = kind,
                    result = repository.refresh(kind),
                )
            } finally {
                quietRefreshingKinds = quietRefreshingKinds - kind
            }
        }
    }

    fun loadMore(kind: TimelineKind = state.value.selectedKind) {
        val tab = state.value.tabs.getValue(kind)
        if (tab.isLoading || tab.isLoadingMore || tab.notes.isEmpty() || tab.endReached) return

        mutableState.update { current ->
            current.copy(
                requiresRelogin = false,
                tabs = current.tabs + (kind to current.tabs.getValue(kind).copy(
                    isLoadingMore = true,
                    errorMessage = null,
                )),
            )
        }

        scope.launch {
            applyResult(
                kind = kind,
                loadingMore = true,
                result = repository.loadMore(kind, tab.notes),
            )
        }
    }

    fun applyNoteMutation(mutation: NoteLocalMutation) {
        if (mutation.noteId.isBlank()) return

        mutableState.update { current ->
            current.copy(
                requiresRelogin = false,
                tabs = current.tabs.mapValues { (_, tab) ->
                    tab.copy(notes = tab.notes.applyNoteLocalMutation(mutation))
                },
            )
        }
    }

    fun consumeNewNotesMarker(kind: TimelineKind = state.value.selectedKind) {
        updateTab(kind) {
            it.copy(firstUnreadNoteId = null, newNoteCount = 0)
        }
    }

    private fun applyResult(
        kind: TimelineKind,
        loadingMore: Boolean,
        result: TimelineRepositoryResult,
        previousFirstNoteId: String? = null,
    ) {
        when (result) {
            is TimelineRepositoryResult.Success -> {
                applySuccessfulNotes(
                    kind = kind,
                    notes = result.notes,
                    endReached = result.endReached,
                    loadingMore = loadingMore,
                    previousFirstNoteId = previousFirstNoteId,
                )
                if (!loadingMore) {
                    prefetchTimelineReplies(kind, result.notes)
                }
            }
            TimelineRepositoryResult.Unauthorized -> mutableState.update { current ->
                current.copy(
                    requiresRelogin = true,
                    tabs = current.tabs + (kind to current.tabs.getValue(kind).copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = "登录已失效，请重新登录",
                    )),
                )
            }
            is TimelineRepositoryResult.Error -> mutableState.update { current ->
                val tab = current.tabs.getValue(kind)
                current.copy(
                    requiresRelogin = false,
                    tabs = current.tabs + (kind to tab.copy(
                        isLoading = if (loadingMore) tab.isLoading else false,
                        isLoadingMore = false,
                        errorMessage = result.message,
                    )),
                )
            }
        }
    }

    private suspend fun restoreCached(kind: TimelineKind) {
        val tab = state.value.tabs.getValue(kind)
        if (tab.notes.isNotEmpty()) return

        val cachedResult = repository.restore(kind)
        if (cachedResult is TimelineRepositoryResult.Success && cachedResult.notes.isNotEmpty()) {
            updateTab(kind) {
                it.copy(
                    notes = cachedResult.notes,
                    endReached = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun updateTab(kind: TimelineKind, transform: (TimelineTabState) -> TimelineTabState) {
        mutableState.update { current ->
            current.copy(
                tabs = current.tabs + (kind to transform(current.tabs.getValue(kind))),
            )
        }
    }

    private fun applySuccessfulNotes(
        kind: TimelineKind,
        notes: List<Note>,
        endReached: Boolean,
        loadingMore: Boolean,
        previousFirstNoteId: String?,
    ) {
        mutableState.update { current ->
            val tab = current.tabs.getValue(kind)
            val unreadMarker = if (!loadingMore && previousFirstNoteId != null) {
                timelineUnreadMarker(notes, previousFirstNoteId)
            } else {
                null
            }
            current.copy(
                requiresRelogin = false,
                tabs = current.tabs + (kind to tab.copy(
                    notes = notes,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = endReached,
                    firstUnreadNoteId = if (loadingMore) tab.firstUnreadNoteId else unreadMarker?.firstUnreadNoteId,
                    newNoteCount = if (loadingMore) tab.newNoteCount else unreadMarker?.newNoteCount ?: 0,
                    errorMessage = null,
                )),
            )
        }
    }

    private fun applyQuietRefreshResult(
        kind: TimelineKind,
        result: TimelineRepositoryResult,
    ) {
        when (result) {
            is TimelineRepositoryResult.Success -> mutableState.update { current ->
                val tab = current.tabs.getValue(kind)
                val mergedNotes = tab.notes.prependDistinctBy(
                    incoming = result.notes,
                    keySelector = { it.id },
                )
                if (mergedNotes === tab.notes && tab.errorMessage == null) return@update current
                val unreadMarker = timelineUnreadMarker(mergedNotes, tab.notes.firstOrNull()?.id)
                current.copy(
                    requiresRelogin = false,
                    tabs = current.tabs + (kind to tab.copy(
                        notes = mergedNotes,
                        endReached = if (tab.notes.isEmpty()) result.endReached else tab.endReached,
                        firstUnreadNoteId = unreadMarker?.firstUnreadNoteId ?: tab.firstUnreadNoteId,
                        newNoteCount = unreadMarker?.newNoteCount ?: tab.newNoteCount,
                        errorMessage = null,
                    )),
                )
            }
            TimelineRepositoryResult.Unauthorized -> mutableState.update { current ->
                current.copy(
                    requiresRelogin = true,
                    tabs = current.tabs + (kind to current.tabs.getValue(kind).copy(
                        errorMessage = "登录已失效，请重新登录",
                    )),
                )
            }
            is TimelineRepositoryResult.Error -> Unit
        }
    }

    private fun prefetchTimelineReplies(kind: TimelineKind, notes: List<Note>) {
        val repliesRepository = repliesRepository ?: return
        val parentIds = notes
            .asSequence()
            .filter { it.replyCount > 0 }
            .map { it.id }
            .distinct()
            .take(TIMELINE_REPLY_PREFETCH_PARENT_LIMIT)
            .toList()
        if (parentIds.isEmpty()) return

        scope.launch {
            val replies = mutableListOf<Note>()
            parentIds.forEach { parentId ->
                when (val result = repliesRepository.loadChildren(parentId)) {
                    is NoteRepliesRepositoryResult.Success -> replies += result.replies
                    NoteRepliesRepositoryResult.Unauthorized -> mutableState.update { it.copy(requiresRelogin = true) }
                    is NoteRepliesRepositoryResult.Error -> Unit
                }
            }
            if (replies.isEmpty()) return@launch

            mutableState.update { current ->
                val tab = current.tabs.getValue(kind)
                val currentParentIds = tab.notes.mapTo(LinkedHashSet(tab.notes.size)) { it.id }
                val scopedReplies = replies.filter { reply -> reply.replyId in currentParentIds }
                if (scopedReplies.isEmpty()) return@update current
                current.copy(
                    tabs = current.tabs + (kind to tab.copy(
                        notes = tab.notes.appendDistinctBy(scopedReplies) { it.id },
                    )),
                )
            }
        }
    }

    private companion object {
        const val TIMELINE_REPLY_PREFETCH_PARENT_LIMIT = 6
    }
}

data class TimelineUnreadMarker(
    val firstUnreadNoteId: String,
    val newNoteCount: Int,
)

fun timelineUnreadMarker(
    notes: List<Note>,
    previousFirstNoteId: String?,
): TimelineUnreadMarker? {
    val cleanPreviousFirstNoteId = previousFirstNoteId?.takeIf { it.isNotBlank() } ?: return null
    if (notes.isEmpty()) return null
    val previousIndex = notes.indexOfFirst { it.id == cleanPreviousFirstNoteId }
    if (previousIndex <= 0) return null
    val firstUnreadNoteId = notes.firstOrNull()?.id?.takeIf { it.isNotBlank() } ?: return null
    return TimelineUnreadMarker(
        firstUnreadNoteId = firstUnreadNoteId,
        newNoteCount = previousIndex,
    )
}
