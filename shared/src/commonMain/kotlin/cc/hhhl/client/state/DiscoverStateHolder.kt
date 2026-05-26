package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverSearchMode(val label: String) {
    Notes("帖子"),
    Users("用户"),
    Trends("趋势"),
    Federation("联邦"),
}

enum class DiscoverSearchOrigin(val label: String, val apiValue: String) {
    Combined("全部", "combined"),
    Local("本地", "local"),
    Remote("远程", "remote"),
}

data class DiscoverAdvancedFilters(
    val origin: DiscoverSearchOrigin = DiscoverSearchOrigin.Combined,
    val username: String = "",
    val domain: String = "",
    val sinceDate: String = "",
    val untilDate: String = "",
) {
    val isActive: Boolean
        get() = origin != DiscoverSearchOrigin.Combined ||
            username.isNotBlank() ||
            domain.isNotBlank() ||
            sinceDate.isNotBlank() ||
            untilDate.isNotBlank()
}

data class DiscoverUiState(
    val query: String = "",
    val selectedMode: DiscoverSearchMode = DiscoverSearchMode.Notes,
    val filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    val notes: List<Note> = emptyList(),
    val users: List<User> = emptyList(),
    val trends: List<TrendingHashtag> = emptyList(),
    val federationInstances: List<FederationInstance> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val canSearchNotes: Boolean = true,
    val canTrend: Boolean = false,
    val canViewFederation: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class DiscoverStateHolder(
    private val repository: DiscoverRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = mutableState

    fun updateQuery(query: String) {
        mutableState.update {
            if (it.query == query) {
                it.copy(errorMessage = null, requiresRelogin = false)
            } else {
                it.copy(
                    query = query,
                    notes = emptyList(),
                    users = emptyList(),
                    federationInstances = emptyList(),
                    endReached = false,
                    hasSearched = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun updateFilters(filters: DiscoverAdvancedFilters) {
        mutableState.update {
            it.copy(
                filters = filters,
                notes = emptyList(),
                users = emptyList(),
                federationInstances = emptyList(),
                endReached = false,
                hasSearched = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun clearFilters() {
        updateFilters(DiscoverAdvancedFilters())
    }

    fun openHashtag(tag: String) {
        val cleanTag = tag.trim().removePrefix("#")
        if (cleanTag.isBlank()) return
        mutableState.update {
            it.copy(
                query = "#$cleanTag",
                selectedMode = if (it.canSearchNotes) DiscoverSearchMode.Notes else DiscoverSearchMode.Users,
                notes = emptyList(),
                users = emptyList(),
                trends = emptyList(),
                federationInstances = emptyList(),
                endReached = false,
                hasSearched = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        search()
    }

    fun openMention(username: String) {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return
        mutableState.update {
            it.copy(
                query = cleanUsername,
                selectedMode = DiscoverSearchMode.Users,
                notes = emptyList(),
                users = emptyList(),
                trends = emptyList(),
                federationInstances = emptyList(),
                endReached = false,
                hasSearched = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        search()
    }

    fun updateCapabilities(
        canSearchNotes: Boolean,
        canTrend: Boolean = state.value.canTrend,
        canViewFederation: Boolean = state.value.canViewFederation,
    ) {
        mutableState.update {
            if (
                it.canSearchNotes == canSearchNotes &&
                it.canTrend == canTrend &&
                it.canViewFederation == canViewFederation
            ) {
                it
            } else if (!canSearchNotes && it.selectedMode == DiscoverSearchMode.Notes) {
                it.copy(
                    selectedMode = DiscoverSearchMode.Users,
                    notes = emptyList(),
                    endReached = false,
                    canSearchNotes = false,
                    canTrend = canTrend,
                    canViewFederation = canViewFederation,
                    hasSearched = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            } else if (!canTrend && it.selectedMode == DiscoverSearchMode.Trends) {
                it.copy(
                    selectedMode = if (canSearchNotes) DiscoverSearchMode.Notes else DiscoverSearchMode.Users,
                    trends = emptyList(),
                    canSearchNotes = canSearchNotes,
                    canTrend = false,
                    canViewFederation = canViewFederation,
                    hasSearched = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            } else if (!canViewFederation && it.selectedMode == DiscoverSearchMode.Federation) {
                it.copy(
                    selectedMode = if (canSearchNotes) DiscoverSearchMode.Notes else DiscoverSearchMode.Users,
                    federationInstances = emptyList(),
                    canSearchNotes = canSearchNotes,
                    canTrend = canTrend,
                    canViewFederation = false,
                    hasSearched = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            } else {
                it.copy(
                    canSearchNotes = canSearchNotes,
                    canTrend = canTrend,
                    canViewFederation = canViewFederation,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun selectMode(mode: DiscoverSearchMode) {
        if (state.value.selectedMode == mode) return
        if (mode == DiscoverSearchMode.Notes && !state.value.canSearchNotes) {
            mutableState.update {
                it.copy(errorMessage = "实例未启用帖子搜索", requiresRelogin = false)
            }
            return
        }
        if (mode == DiscoverSearchMode.Trends && !state.value.canTrend) {
            mutableState.update {
                it.copy(errorMessage = "实例未启用趋势", requiresRelogin = false)
            }
            return
        }
        if (mode == DiscoverSearchMode.Federation && !state.value.canViewFederation) {
            mutableState.update {
                it.copy(errorMessage = "实例未启用联邦浏览", requiresRelogin = false)
            }
            return
        }

        mutableState.update {
            it.copy(
                selectedMode = mode,
                notes = emptyList(),
                users = emptyList(),
                trends = emptyList(),
                federationInstances = emptyList(),
                endReached = false,
                hasSearched = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        if (mode == DiscoverSearchMode.Trends || mode == DiscoverSearchMode.Federation) {
            search()
        }
    }

    fun search() {
        val query = state.value.query
        if (state.value.isSearching) return
        if (state.value.selectedMode == DiscoverSearchMode.Notes && !state.value.canSearchNotes) {
            mutableState.update {
                it.copy(
                    hasSearched = true,
                    notes = emptyList(),
                    errorMessage = "实例未启用帖子搜索",
                )
            }
            return
        }
        if (state.value.selectedMode == DiscoverSearchMode.Trends && !state.value.canTrend) {
            mutableState.update {
                it.copy(
                    hasSearched = true,
                    trends = emptyList(),
                    errorMessage = "实例未启用趋势",
                )
            }
            return
        }
        if (state.value.selectedMode == DiscoverSearchMode.Federation && !state.value.canViewFederation) {
            mutableState.update {
                it.copy(
                    hasSearched = true,
                    federationInstances = emptyList(),
                    errorMessage = "实例未启用联邦浏览",
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isSearching = true,
                hasSearched = true,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyResult(
                result = when (state.value.selectedMode) {
                    DiscoverSearchMode.Notes -> repository.search(query, state.value.filters)
                    DiscoverSearchMode.Users -> repository.searchUsers(query, state.value.filters)
                    DiscoverSearchMode.Trends -> repository.loadTrends()
                    DiscoverSearchMode.Federation -> repository.loadFederation(emptyList(), state.value.filters)
                },
                loadingMore = false,
            )
        }
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isSearching ||
            current.isLoadingMore ||
            current.selectedMode == DiscoverSearchMode.Users ||
            current.selectedMode == DiscoverSearchMode.Trends ||
            current.endReached ||
            (current.selectedMode == DiscoverSearchMode.Notes && (current.notes.isEmpty() || !current.canSearchNotes)) ||
            (current.selectedMode == DiscoverSearchMode.Federation && !current.canViewFederation)
        ) {
            return
        }

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyResult(
                result = when (current.selectedMode) {
                    DiscoverSearchMode.Federation -> repository.loadFederation(
                        current.federationInstances,
                        current.filters,
                    )
                    else -> repository.loadMore(current.query, current.notes, current.filters)
                },
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

    private fun applyResult(
        result: DiscoverRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is DiscoverRepositoryResult.Success -> mutableState.update {
                it.copy(
                    notes = result.notes,
                    users = emptyList(),
                    trends = emptyList(),
                    federationInstances = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.UserSuccess -> mutableState.update {
                it.copy(
                    users = result.users,
                    notes = emptyList(),
                    trends = emptyList(),
                    federationInstances = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.TrendSuccess -> mutableState.update {
                it.copy(
                    trends = result.trends,
                    notes = emptyList(),
                    users = emptyList(),
                    federationInstances = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = true,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.FederationSuccess -> mutableState.update {
                it.copy(
                    federationInstances = result.instances,
                    notes = emptyList(),
                    users = emptyList(),
                    trends = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DiscoverRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isSearching = if (loadingMore) it.isSearching else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
