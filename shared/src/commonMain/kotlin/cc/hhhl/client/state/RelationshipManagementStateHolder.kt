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

private const val MAX_BLOCKED_USER_FILTER_PAGES = 10

enum class RelationshipManagementTab(
    val label: String,
) {
    SpecialCare("特别关心"),
    Blocked("已屏蔽"),
    Muted("已静音"),
    RenoteMuted("转发静音"),
}

data class RelationshipManagementUiState(
    val selectedTab: RelationshipManagementTab = RelationshipManagementTab.SpecialCare,
    val specialCareUsers: List<UserRelationshipListEntry> = emptyList(),
    val blockedUsers: List<UserRelationshipListEntry> = emptyList(),
    val mutedUsers: List<UserRelationshipListEntry> = emptyList(),
    val renoteMutedUsers: List<UserRelationshipListEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutating: Boolean = false,
    val blockedEndReached: Boolean = false,
    val mutedEndReached: Boolean = false,
    val renoteMutedEndReached: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val requiresRelogin: Boolean = false,
) {
    val visibleEntries: List<UserRelationshipListEntry>
        get() = when (selectedTab) {
            RelationshipManagementTab.SpecialCare -> specialCareUsers
            RelationshipManagementTab.Blocked -> blockedUsers
            RelationshipManagementTab.Muted -> mutedUsers
            RelationshipManagementTab.RenoteMuted -> renoteMutedUsers
        }

    val currentEndReached: Boolean
        get() = when (selectedTab) {
            RelationshipManagementTab.SpecialCare -> true
            RelationshipManagementTab.Blocked -> blockedEndReached
            RelationshipManagementTab.Muted -> mutedEndReached
            RelationshipManagementTab.RenoteMuted -> renoteMutedEndReached
        }

    val blockedUserIds: Set<String>
        get() = blockedUsers.mapTo(HashSet(blockedUsers.size)) { it.user.id }
}

class RelationshipManagementStateHolder(
    private val repository: UserRelationshipRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(RelationshipManagementUiState())
    val state: StateFlow<RelationshipManagementUiState> = mutableState
    private var listRequestId = 0

    fun updateSpecialCareUsers(entries: List<UserRelationshipListEntry>) {
        mutableState.update {
            it.copy(specialCareUsers = entries.distinctBy { entry -> entry.user.id })
        }
    }

    fun updateBlockedUser(entry: UserRelationshipListEntry, blocked: Boolean) {
        mutableState.update {
            val nextBlockedUsers = if (blocked) {
                (listOf(entry) + it.blockedUsers).distinctBy { item -> item.user.id }
            } else {
                it.blockedUsers.filterNot { item -> item.user.id == entry.user.id }
            }
            it.copy(blockedUsers = nextBlockedUsers)
        }
    }

    fun selectTab(tab: RelationshipManagementTab) {
        if (state.value.selectedTab == tab && state.value.visibleEntries.isNotEmpty()) return
        mutableState.update {
            it.copy(
                selectedTab = tab,
                isLoading = if (tab == RelationshipManagementTab.SpecialCare) false else it.isLoading,
                isLoadingMore = if (tab == RelationshipManagementTab.SpecialCare) false else it.isLoadingMore,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }
        if (tab == RelationshipManagementTab.SpecialCare) {
            nextListRequestId()
            return
        }
        refresh()
    }

    fun refresh() {
        if (state.value.isLoading) return
        val tab = state.value.selectedTab
        if (tab == RelationshipManagementTab.SpecialCare) {
            nextListRequestId()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    message = null,
                    requiresRelogin = false,
                )
            }
            return
        }
        val requestId = nextListRequestId()
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
                RelationshipManagementTab.SpecialCare -> return@launch
                RelationshipManagementTab.Blocked -> repository.loadBlockedUsers()
                RelationshipManagementTab.Muted -> repository.loadMutedUsers()
                RelationshipManagementTab.RenoteMuted -> repository.loadRenoteMutedUsers()
            }
            applyListResult(tab, requestId, result)
        }
    }

    fun refreshBlockedUsers() {
        scope.launch {
            applyListResult(
                tab = RelationshipManagementTab.Blocked,
                requestId = -1,
                result = loadAllBlockedUsers(),
                requireSelectedTab = false,
            )
        }
    }

    private suspend fun loadAllBlockedUsers(): UserRelationshipListRepositoryResult {
        var entries = emptyList<UserRelationshipListEntry>()
        repeat(MAX_BLOCKED_USER_FILTER_PAGES) {
            when (val result = repository.loadBlockedUsers(entries)) {
                is UserRelationshipListRepositoryResult.Success -> {
                    entries = result.entries
                    if (result.endReached) {
                        return UserRelationshipListRepositoryResult.Success(
                            entries = entries,
                            endReached = true,
                        )
                    }
                }
                else -> return result
            }
        }
        return UserRelationshipListRepositoryResult.Success(
            entries = entries,
            endReached = false,
        )
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || current.currentEndReached || current.visibleEntries.isEmpty()) {
            return
        }
        val tab = current.selectedTab
        if (tab == RelationshipManagementTab.SpecialCare) return
        val currentEntries = current.visibleEntries
        val requestId = nextListRequestId()
        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            val result = when (tab) {
                RelationshipManagementTab.SpecialCare -> return@launch
                RelationshipManagementTab.Blocked -> repository.loadBlockedUsers(currentEntries)
                RelationshipManagementTab.Muted -> repository.loadMutedUsers(currentEntries)
                RelationshipManagementTab.RenoteMuted -> repository.loadRenoteMutedUsers(currentEntries)
            }
            applyListResult(tab, requestId, result)
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
                RelationshipManagementTab.SpecialCare -> UserRelationshipRepositoryResult.Success
                RelationshipManagementTab.Blocked -> repository.unblock(cleanUserId)
                RelationshipManagementTab.Muted -> repository.unmute(cleanUserId)
                RelationshipManagementTab.RenoteMuted -> repository.updateFollowing(cleanUserId)
            }
            applyMutationResult(tab, cleanUserId, result)
        }
    }

    fun updateAllFollowing() {
        if (state.value.isMutating) return
        mutableState.update {
            it.copy(
                isMutating = true,
                errorMessage = null,
                message = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.updateAllFollowing()) {
                UserRelationshipRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        isMutating = false,
                        message = "已提交刷新全部关注",
                        requiresRelogin = false,
                    )
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
                    it.copy(
                        isMutating = false,
                        message = "已同步关注关系",
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun applyListResult(
        tab: RelationshipManagementTab,
        requestId: Int,
        result: UserRelationshipListRepositoryResult,
        requireSelectedTab: Boolean = true,
    ) {
        if (requireSelectedTab && (requestId != listRequestId || state.value.selectedTab != tab)) return
        when (result) {
            is UserRelationshipListRepositoryResult.Success -> mutableState.update {
                when (tab) {
                    RelationshipManagementTab.SpecialCare -> it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Blocked -> it.copy(
                        blockedUsers = result.entries,
                        blockedEndReached = result.endReached,
                        isLoading = if (requireSelectedTab) false else it.isLoading,
                        isLoadingMore = if (requireSelectedTab) false else it.isLoadingMore,
                        errorMessage = if (requireSelectedTab) null else it.errorMessage,
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Muted -> it.copy(
                        mutedUsers = result.entries,
                        mutedEndReached = result.endReached,
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.RenoteMuted -> it.copy(
                        renoteMutedUsers = result.entries,
                        renoteMutedEndReached = result.endReached,
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

    private fun nextListRequestId(): Int {
        listRequestId += 1
        return listRequestId
    }

    private fun applyMutationResult(
        tab: RelationshipManagementTab,
        userId: String,
        result: UserRelationshipRepositoryResult,
    ) {
        when (result) {
            UserRelationshipRepositoryResult.Success -> mutableState.update {
                when (tab) {
                    RelationshipManagementTab.SpecialCare -> it.copy(
                        specialCareUsers = it.specialCareUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已取消特别关心",
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Blocked -> it.copy(
                        blockedUsers = it.blockedUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已取消屏蔽",
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.Muted -> it.copy(
                        mutedUsers = it.mutedUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已取消静音",
                        requiresRelogin = false,
                    )
                    RelationshipManagementTab.RenoteMuted -> it.copy(
                        renoteMutedUsers = it.renoteMutedUsers.filterNot { entry -> entry.user.id == userId },
                        isMutating = false,
                        message = "已刷新关注关系",
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
