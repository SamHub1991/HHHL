package cc.hhhl.client.state

import cc.hhhl.client.model.UserRelationshipListEntry
import cc.hhhl.client.repository.UserRelationshipListRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RelationshipManagementTab(
    val label: String,
) {
    Muted("静音"),
    Blocked("拉黑"),
}

data class RelationshipManagementUiState(
    val selectedTab: RelationshipManagementTab = RelationshipManagementTab.Muted,
    val mutedUsers: List<UserRelationshipListEntry> = emptyList(),
    val blockedUsers: List<UserRelationshipListEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutating: Boolean = false,
    val mutedEndReached: Boolean = false,
    val blockedEndReached: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val requiresRelogin: Boolean = false,
) {
    val visibleEntries: List<UserRelationshipListEntry>
        get() = when (selectedTab) {
            RelationshipManagementTab.Muted -> mutedUsers
            RelationshipManagementTab.Blocked -> blockedUsers
        }

    val currentEndReached: Boolean
        get() = when (selectedTab) {
            RelationshipManagementTab.Muted -> mutedEndReached
            RelationshipManagementTab.Blocked -> blockedEndReached
        }
}

class RelationshipManagementStateHolder(
    private val repository: UserRelationshipRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(RelationshipManagementUiState())
    val state: StateFlow<RelationshipManagementUiState> = mutableState

    fun selectTab(tab: RelationshipManagementTab) {
        if (state.value.selectedTab == tab && state.value.visibleEntries.isNotEmpty()) return
        mutableState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun refresh() {
        if (state.value.isLoading) return
        val tab = state.value.selectedTab
        mutableState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            val result = when (tab) {
                RelationshipManagementTab.Muted -> repository.loadMutedUsers()
                RelationshipManagementTab.Blocked -> repository.loadBlockedUsers()
            }
            applyListResult(tab, result)
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.currentEndReached || current.visibleEntries.isEmpty()) {
            return
        }
        val tab = current.selectedTab
        val currentEntries = current.visibleEntries
        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            val result = when (tab) {
                RelationshipManagementTab.Muted -> repository.loadMutedUsers(currentEntries)
                RelationshipManagementTab.Blocked -> repository.loadBlockedUsers(currentEntries)
            }
            applyListResult(tab, result)
        }
    }

    fun removeRelationship(userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty() || state.value.isMutating) return
        val tab = state.value.selectedTab
        mutableState.update {
            it.copy(
                isMutating = true,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            val result = when (tab) {
                RelationshipManagementTab.Muted -> repository.unmute(cleanUserId)
                RelationshipManagementTab.Blocked -> repository.unblock(cleanUserId)
            }
            applyMutationResult(tab, cleanUserId, result)
        }
    }

    private fun applyListResult(
        tab: RelationshipManagementTab,
        result: UserRelationshipListRepositoryResult,
    ) {
        when (result) {
            is UserRelationshipListRepositoryResult.Success -> mutableState.update {
                when (tab) {
                    RelationshipManagementTab.Muted -> it.copy(
                        mutedUsers = result.entries,
                        mutedEndReached = result.endReached,
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Blocked -> it.copy(
                        blockedUsers = result.entries,
                        blockedEndReached = result.endReached,
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
            }
            UserRelationshipListRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipListRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyMutationResult(
        tab: RelationshipManagementTab,
        userId: String,
        result: UserRelationshipRepositoryResult,
    ) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update {
                when (tab) {
                    RelationshipManagementTab.Muted -> it.copy(
                        mutedUsers = it.mutedUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已取消静音",
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Blocked -> it.copy(
                        blockedUsers = it.blockedUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已取消拉黑",
                        requiresRelogin = false,
                    )
                }
            }
            UserRelationshipRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isMutating = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is UserRelationshipRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isMutating = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
            is UserRelationshipRepositoryResult.RelationLoaded -> mutableState.update {
                it.copy(isMutating = false, requiresRelogin = false)
            }
        }
    }
}
