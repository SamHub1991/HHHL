package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft
import cc.hhhl.client.repository.UserListActionRepositoryResult
import cc.hhhl.client.repository.UserListMutationRepositoryResult
import cc.hhhl.client.repository.UserListRepository
import cc.hhhl.client.repository.UserListTimelineRepositoryResult
import cc.hhhl.client.repository.UserListsRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserListUiState(
    val lists: List<UserList> = emptyList(),
    val selectedList: UserList? = null,
    val notes: List<Note> = emptyList(),
    val isLoadingLists: Boolean = false,
    val isLoadingTimeline: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutatingList: Boolean = false,
    val isMutatingMembers: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val timelineErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class UserListStateHolder(
    private val repository: UserListRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UserListUiState())
    val state: StateFlow<UserListUiState> = mutableState

    fun refreshLists() {
        if (state.value.isLoadingLists) return

        mutableState.update {
            it.copy(
                isLoadingLists = true,
                isLoadingMore = false,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.refreshLists()) {
                is UserListsRepositoryResult.Success -> {
                    val currentSelectedId = state.value.selectedList?.id
                    val selected = result.lists.firstOrNull { it.id == currentSelectedId }
                        ?: result.lists.firstOrNull()
                    mutableState.update {
                        it.copy(
                            lists = result.lists,
                            selectedList = selected,
                            notes = if (selected == null) emptyList() else it.notes,
                            isLoadingLists = false,
                            errorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                    selected?.let { loadTimeline(it.id, clearNotes = state.value.notes.isEmpty()) }
                }
                UserListsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingLists = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserListsRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingLists = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun selectList(list: UserList) {
        if (state.value.selectedList?.id == list.id && state.value.notes.isNotEmpty()) return

        mutableState.update {
            it.copy(
                selectedList = list,
                notes = emptyList(),
                isLoadingTimeline = true,
                isLoadingMore = false,
                endReached = false,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.refreshTimeline(list.id),
                loadingMore = false,
            )
        }
    }

    fun refreshTimeline() {
        val list = state.value.selectedList ?: return
        if (state.value.isLoadingTimeline) return
        loadTimeline(list.id, clearNotes = false)
    }

    fun loadMore() {
        val current = state.value
        val listId = current.selectedList?.id ?: return
        if (
            current.isLoadingTimeline ||
            current.isLoadingMore ||
            current.notes.isEmpty() ||
            current.endReached
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.loadMoreTimeline(listId, current.notes),
                loadingMore = true,
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

    fun createList(draft: UserListDraft) {
        if (draft.name.trim().isEmpty() || state.value.isMutatingList) return

        mutableState.update {
            it.copy(
                isMutatingList = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.createList(draft.copy(name = draft.name.trim()))) {
                is UserListMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        lists = listOf(result.list) + current.lists.filterNot { it.id == result.list.id },
                        selectedList = result.list,
                        notes = emptyList(),
                        isMutatingList = false,
                        isLoadingTimeline = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        timelineErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserListMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserListMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateSelectedList(draft: UserListDraft) {
        val list = state.value.selectedList ?: return
        if (draft.name.trim().isEmpty() || state.value.isMutatingList) return

        mutableState.update {
            it.copy(
                isMutatingList = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = repository.updateList(
                    listId = list.id,
                    draft = draft.copy(name = draft.name.trim()),
                )
            ) {
                is UserListMutationRepositoryResult.Success -> mutableState.update { current ->
                    current.copy(
                        lists = current.lists.map { item ->
                            if (item.id == result.list.id) result.list else item
                        },
                        selectedList = current.selectedList?.let {
                            if (it.id == result.list.id) result.list else it
                        },
                        isMutatingList = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserListMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserListMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun deleteSelectedList() {
        val list = state.value.selectedList ?: return
        if (state.value.isMutatingList) return

        mutableState.update {
            it.copy(
                isMutatingList = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.deleteList(list.id)) {
                UserListActionRepositoryResult.Success -> mutableState.update { current ->
                    val remaining = current.lists.filterNot { it.id == list.id }
                    current.copy(
                        lists = remaining,
                        selectedList = remaining.firstOrNull(),
                        notes = emptyList(),
                        isMutatingList = false,
                        isLoadingTimeline = false,
                        isLoadingMore = false,
                        endReached = false,
                        errorMessage = null,
                        timelineErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserListActionRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserListActionRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingList = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun addUserToSelectedList(userId: String) {
        mutateSelectedListMember(
            userId = userId,
            mutate = repository::addUserToList,
            update = { list, cleanUserId ->
                if (cleanUserId in list.userIds) list else list.copy(userIds = list.userIds + cleanUserId)
            },
        )
    }

    fun removeUserFromSelectedList(userId: String) {
        mutateSelectedListMember(
            userId = userId,
            mutate = repository::removeUserFromList,
            update = { list, cleanUserId ->
                list.copy(userIds = list.userIds.filterNot { it == cleanUserId })
            },
        )
    }

    private fun mutateSelectedListMember(
        userId: String,
        mutate: suspend (String, String) -> UserListActionRepositoryResult,
        update: (UserList, String) -> UserList,
    ) {
        val list = state.value.selectedList ?: return
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty() || state.value.isMutatingMembers) return

        mutableState.update {
            it.copy(
                isMutatingMembers = true,
                errorMessage = null,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = mutate(list.id, cleanUserId)) {
                UserListActionRepositoryResult.Success -> mutableState.update { current ->
                    val updated = update(list, cleanUserId)
                    current.copy(
                        lists = current.lists.map { item ->
                            if (item.id == updated.id) updated else item
                        },
                        selectedList = current.selectedList?.let {
                            if (it.id == updated.id) updated else it
                        },
                        isMutatingMembers = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                UserListActionRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutatingMembers = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is UserListActionRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutatingMembers = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun loadTimeline(
        listId: String,
        clearNotes: Boolean,
    ) {
        mutableState.update {
            it.copy(
                notes = if (clearNotes) emptyList() else it.notes,
                isLoadingTimeline = true,
                isLoadingMore = false,
                endReached = false,
                timelineErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyTimelineResult(
                result = repository.refreshTimeline(listId),
                loadingMore = false,
            )
        }
    }

    private fun applyTimelineResult(
        result: UserListTimelineRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is UserListTimelineRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    isLoadingTimeline = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    timelineErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            UserListTimelineRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoadingTimeline = false,
                    isLoadingMore = false,
                    timelineErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserListTimelineRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoadingTimeline = if (loadingMore) it.isLoadingTimeline else false,
                    isLoadingMore = false,
                    timelineErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
