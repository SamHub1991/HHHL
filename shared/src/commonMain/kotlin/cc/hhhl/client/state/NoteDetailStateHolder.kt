package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteReactionUser
import cc.hhhl.client.model.NoteState
import cc.hhhl.client.model.NoteTranslation
import cc.hhhl.client.model.NoteVersion
import cc.hhhl.client.repository.NoteDetailNotesRepositoryResult
import cc.hhhl.client.repository.NoteDetailRepository
import cc.hhhl.client.repository.NoteDetailRepositoryResult
import cc.hhhl.client.repository.NoteReactionUsersRepositoryResult
import cc.hhhl.client.repository.NoteRepliesRepository
import cc.hhhl.client.repository.NoteRepliesRepositoryResult
import cc.hhhl.client.repository.NoteStateRepositoryResult
import cc.hhhl.client.repository.NoteSimpleActionRepositoryResult
import cc.hhhl.client.repository.NoteTranslationRepositoryResult
import cc.hhhl.client.repository.NoteVersionsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val noteId: String? = null,
    val note: Note? = null,
    val replies: List<Note> = emptyList(),
    val conversationNotes: List<Note> = emptyList(),
    val renoteNotes: List<Note> = emptyList(),
    val reactionUsers: List<NoteReactionUser> = emptyList(),
    val versions: List<NoteVersion> = emptyList(),
    val translation: NoteTranslation? = null,
    val remoteState: NoteState? = null,
    val childRepliesByParentId: Map<String, List<Note>> = emptyMap(),
    val expandedReplyIds: Set<String> = emptySet(),
    val loadingChildReplyIds: Set<String> = emptySet(),
    val childReplyErrors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingReplies: Boolean = false,
    val isLoadingMoreReplies: Boolean = false,
    val isLoadingConversation: Boolean = false,
    val isLoadingRenotes: Boolean = false,
    val isLoadingReactions: Boolean = false,
    val isLoadingVersions: Boolean = false,
    val isTranslating: Boolean = false,
    val isRefreshingPollRecommendation: Boolean = false,
    val errorMessage: String? = null,
    val repliesErrorMessage: String? = null,
    val detailActionMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class NoteDetailStateHolder(
    private val repository: NoteDetailRepository,
    private val repliesRepository: NoteRepliesRepository? = null,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(NoteDetailUiState())
    val state: StateFlow<NoteDetailUiState> = mutableState
    private var repliesRequestId = 0
    private var detailRequestId = 0

    fun load(noteId: String) {
        val current = state.value
        if (current.isLoading && current.noteId == noteId) return
        val detailRequestId = nextDetailRequestId()
        nextRepliesRequestId()

        mutableState.update {
            it.copy(
                noteId = noteId,
                note = if (it.noteId == noteId) it.note else null,
                replies = if (it.noteId == noteId) it.replies else emptyList(),
                conversationNotes = if (it.noteId == noteId) it.conversationNotes else emptyList(),
                renoteNotes = if (it.noteId == noteId) it.renoteNotes else emptyList(),
                reactionUsers = if (it.noteId == noteId) it.reactionUsers else emptyList(),
                versions = if (it.noteId == noteId) it.versions else emptyList(),
                translation = if (it.noteId == noteId) it.translation else null,
                remoteState = if (it.noteId == noteId) it.remoteState else null,
                childRepliesByParentId = if (it.noteId == noteId) it.childRepliesByParentId else emptyMap(),
                expandedReplyIds = if (it.noteId == noteId) it.expandedReplyIds else emptySet(),
                loadingChildReplyIds = emptySet(),
                childReplyErrors = if (it.noteId == noteId) it.childReplyErrors else emptyMap(),
                isLoading = true,
                isLoadingReplies = false,
                isLoadingMoreReplies = false,
                isLoadingConversation = false,
                isLoadingRenotes = false,
                isLoadingReactions = false,
                isLoadingVersions = false,
                isTranslating = false,
                isRefreshingPollRecommendation = false,
                errorMessage = null,
                repliesErrorMessage = null,
                detailActionMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val shouldLoadReplies = applyResult(noteId, repository.load(noteId), detailRequestId)
            if (shouldLoadReplies) {
                refreshRemoteState(noteId, detailRequestId)
                refreshReplies(noteId, detailRequestId)
            }
        }
    }

    fun loadConversation() {
        val noteId = state.value.noteId ?: return
        if (state.value.isLoadingConversation) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isLoadingConversation = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            applyNotesActionResult(
                noteId = noteId,
                detailRequestId = detailRequestId,
                result = repository.loadConversation(noteId),
                successMessage = "已加载上下文",
                update = { current, notes -> current.copy(conversationNotes = notes, isLoadingConversation = false) },
                clearLoading = { current -> current.copy(isLoadingConversation = false) },
            )
        }
    }

    fun loadRenotes() {
        val noteId = state.value.noteId ?: return
        if (state.value.isLoadingRenotes) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isLoadingRenotes = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            applyNotesActionResult(
                noteId = noteId,
                detailRequestId = detailRequestId,
                result = repository.loadRenotes(noteId),
                successMessage = "已加载转发",
                update = { current, notes -> current.copy(renoteNotes = notes, isLoadingRenotes = false) },
                clearLoading = { current -> current.copy(isLoadingRenotes = false) },
            )
        }
    }

    fun loadReactionUsers() {
        val noteId = state.value.noteId ?: return
        if (state.value.isLoadingReactions) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isLoadingReactions = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val result = repository.loadReactionUsers(noteId)
            if (!isCurrentDetailRequest(noteId, detailRequestId)) return@launch
            when (result) {
                is NoteReactionUsersRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        reactionUsers = result.users,
                        isLoadingReactions = false,
                        detailActionMessage = if (result.users.isEmpty()) "暂无反应用户" else "已加载反应用户",
                        requiresRelogin = false,
                    )
                }
                NoteReactionUsersRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(isLoadingReactions = false, detailActionMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is NoteReactionUsersRepositoryResult.Error -> mutableState.update {
                    it.copy(isLoadingReactions = false, detailActionMessage = result.message, requiresRelogin = false)
                }
            }
        }
    }

    fun translate() {
        val noteId = state.value.noteId ?: return
        if (state.value.isTranslating) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isTranslating = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val result = repository.translate(noteId)
            if (!isCurrentDetailRequest(noteId, detailRequestId)) return@launch
            when (result) {
                is NoteTranslationRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        translation = result.translation,
                        isTranslating = false,
                        detailActionMessage = "已翻译",
                        requiresRelogin = false,
                    )
                }
                NoteTranslationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(isTranslating = false, detailActionMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is NoteTranslationRepositoryResult.Error -> mutableState.update {
                    it.copy(isTranslating = false, detailActionMessage = result.message, requiresRelogin = false)
                }
            }
        }
    }

    fun loadVersions() {
        val noteId = state.value.noteId ?: return
        if (state.value.isLoadingVersions) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isLoadingVersions = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val result = repository.loadVersions(noteId)
            if (!isCurrentDetailRequest(noteId, detailRequestId)) return@launch
            when (result) {
                is NoteVersionsRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        versions = result.versions,
                        isLoadingVersions = false,
                        detailActionMessage = if (result.versions.isEmpty()) "暂无编辑记录" else "已加载编辑记录",
                        requiresRelogin = false,
                    )
                }
                NoteVersionsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(isLoadingVersions = false, detailActionMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is NoteVersionsRepositoryResult.Error -> mutableState.update {
                    it.copy(isLoadingVersions = false, detailActionMessage = result.message, requiresRelogin = false)
                }
            }
        }
    }

    fun refreshPollRecommendation() {
        val noteId = state.value.noteId ?: return
        if (state.value.isRefreshingPollRecommendation) return
        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(isRefreshingPollRecommendation = true, detailActionMessage = null, requiresRelogin = false)
        }
        scope.launch {
            val result = repository.refreshPollRecommendation(noteId)
            if (!isCurrentDetailRequest(noteId, detailRequestId)) return@launch
            when (result) {
                NoteSimpleActionRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        isRefreshingPollRecommendation = false,
                        detailActionMessage = "已刷新投票推荐",
                        requiresRelogin = false,
                    )
                }
                NoteSimpleActionRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(isRefreshingPollRecommendation = false, detailActionMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is NoteSimpleActionRepositoryResult.Error -> mutableState.update {
                    it.copy(isRefreshingPollRecommendation = false, detailActionMessage = result.message, requiresRelogin = false)
                }
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

        val requestId = nextRepliesRequestId()
        val detailRequestId = detailRequestId
        scope.launch {
            applyRepliesResult(
                noteId = noteId,
                result = repository.loadMore(noteId, current.replies),
                loadingMore = true,
                requestId = requestId,
                detailRequestId = detailRequestId,
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

        val detailRequestId = detailRequestId
        mutableState.update {
            it.copy(
                loadingChildReplyIds = it.loadingChildReplyIds + cleanParentId,
                childReplyErrors = it.childReplyErrors - cleanParentId,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyChildRepliesResult(
                noteId = current.noteId,
                parentId = cleanParentId,
                result = repository.loadChildren(cleanParentId),
                detailRequestId = detailRequestId,
            )
        }
    }

    private fun prefetchVisibleChildReplies(parentIds: List<String>, detailRequestId: Int) {
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

        val noteId = current.noteId
        scope.launch {
            idsToLoad.forEach { parentId ->
                applyChildRepliesResult(
                    noteId = noteId,
                    parentId = parentId,
                    result = repository.loadChildren(parentId),
                    detailRequestId = detailRequestId,
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
        detailRequestId: Int,
    ): Boolean {
        if (!isCurrentDetailRequest(noteId, detailRequestId)) return false

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

    private fun refreshRemoteState(noteId: String, detailRequestId: Int) {
        scope.launch {
            val result = repository.loadState(noteId)
            if (!isCurrentDetailRequest(noteId, detailRequestId)) return@launch
            when (result) {
                is NoteStateRepositoryResult.Success -> mutableState.update {
                    it.copy(remoteState = result.state, requiresRelogin = false)
                }
                NoteStateRepositoryResult.Unauthorized -> mutableState.update { it.copy(requiresRelogin = true) }
                is NoteStateRepositoryResult.Error -> Unit
            }
        }
    }

    private fun applyNotesActionResult(
        noteId: String,
        detailRequestId: Int,
        result: NoteDetailNotesRepositoryResult,
        successMessage: String,
        update: (NoteDetailUiState, List<Note>) -> NoteDetailUiState,
        clearLoading: (NoteDetailUiState) -> NoteDetailUiState,
    ) {
        if (!isCurrentDetailRequest(noteId, detailRequestId)) return
        when (result) {
            is NoteDetailNotesRepositoryResult.Success -> mutableState.update {
                update(it, result.notes).copy(
                    detailActionMessage = if (result.notes.isEmpty()) "暂无数据" else successMessage,
                    requiresRelogin = false,
                )
            }
            NoteDetailNotesRepositoryResult.Unauthorized -> mutableState.update {
                clearLoading(it).copy(detailActionMessage = "登录已失效，请重新登录", requiresRelogin = true)
            }
            is NoteDetailNotesRepositoryResult.Error -> mutableState.update {
                clearLoading(it).copy(detailActionMessage = result.message, requiresRelogin = false)
            }
        }
    }

    private suspend fun refreshReplies(noteId: String, detailRequestId: Int) {
        val repository = repliesRepository ?: return
        val requestId = nextRepliesRequestId()
        if (!isCurrentDetailRequest(noteId, detailRequestId)) return

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
            requestId = requestId,
            detailRequestId = detailRequestId,
        )
    }

    private fun applyRepliesResult(
        noteId: String,
        result: NoteRepliesRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
        detailRequestId: Int,
    ) {
        if (requestId != repliesRequestId || !isCurrentDetailRequest(noteId, detailRequestId)) return

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
                    detailRequestId,
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

    private fun nextRepliesRequestId(): Int {
        repliesRequestId += 1
        return repliesRequestId
    }

    private fun nextDetailRequestId(): Int {
        detailRequestId += 1
        return detailRequestId
    }

    private fun isCurrentDetailRequest(noteId: String?, detailRequestId: Int): Boolean {
        return this.detailRequestId == detailRequestId && state.value.noteId == noteId
    }

    private fun applyChildRepliesResult(
        noteId: String?,
        parentId: String,
        result: NoteRepliesRepositoryResult,
        detailRequestId: Int,
    ) {
        if (!isCurrentDetailRequest(noteId, detailRequestId)) return
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
