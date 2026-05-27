package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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

enum class DiscoverSearchOperator(val label: String) {
    AllWords("全部词"),
    AnyWord("任一词"),
    ExactPhrase("精确短语"),
}

data class DiscoverAdvancedFilters(
    val operator: DiscoverSearchOperator = DiscoverSearchOperator.AllWords,
    val origin: DiscoverSearchOrigin = DiscoverSearchOrigin.Combined,
    val username: String = "",
    val userId: String = "",
    val domain: String = "",
    val channelId: String = "",
    val sinceDate: String = "",
    val untilDate: String = "",
    val excludeWords: String = "",
    val withFiles: Boolean = false,
    val includeReplies: Boolean = true,
) {
    val isActive: Boolean
        get() = operator != DiscoverSearchOperator.AllWords ||
            origin != DiscoverSearchOrigin.Combined ||
            username.isNotBlank() ||
            userId.isNotBlank() ||
            domain.isNotBlank() ||
            channelId.isNotBlank() ||
            sinceDate.isNotBlank() ||
            untilDate.isNotBlank() ||
            excludeWords.isNotBlank() ||
            withFiles ||
            !includeReplies
}

data class DiscoverUiState(
    val query: String = "",
    val selectedMode: DiscoverSearchMode = DiscoverSearchMode.Notes,
    val filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    val notes: List<Note> = emptyList(),
    val nextNotesUntilId: String? = null,
    val users: List<User> = emptyList(),
    val trends: List<TrendingHashtag> = emptyList(),
    val isRefreshingTrends: Boolean = false,
    val trendErrorMessage: String? = null,
    val federationInstances: List<FederationInstance> = emptyList(),
    val selectedFederationInstance: FederationInstance? = null,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingFederationDetail: Boolean = false,
    val federationActionHost: String? = null,
    val endReached: Boolean = false,
    val canSearchNotes: Boolean = true,
    val canTrend: Boolean = false,
    val canViewFederation: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    val federationDetailMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class DiscoverStateHolder(
    private val repository: DiscoverRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = mutableState

    fun refreshTrendsQuietly() {
        val current = state.value
        if (!current.canTrend || current.isRefreshingTrends) return
        if (current.selectedMode == DiscoverSearchMode.Trends && current.isSearching) return

        mutableState.update {
            it.copy(
                isRefreshingTrends = true,
                trendErrorMessage = null,
            )
        }

        scope.launch {
            try {
                when (val result = repository.loadTrends()) {
                    is DiscoverRepositoryResult.TrendSuccess -> mutableState.update {
                        it.copy(
                            trends = result.trends,
                            isRefreshingTrends = false,
                            trendErrorMessage = null,
                            errorMessage = if (it.selectedMode == DiscoverSearchMode.Trends) null else it.errorMessage,
                            requiresRelogin = false,
                        )
                    }
                    DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                        it.copy(
                            isRefreshingTrends = false,
                            trendErrorMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is DiscoverRepositoryResult.Error -> mutableState.update {
                        it.copy(
                            isRefreshingTrends = false,
                            trendErrorMessage = result.message,
                            errorMessage = if (it.selectedMode == DiscoverSearchMode.Trends) result.message else it.errorMessage,
                            requiresRelogin = false,
                        )
                    }
                    else -> mutableState.update {
                        it.copy(isRefreshingTrends = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isRefreshingTrends = false,
                        trendErrorMessage = error.toDiscoverErrorMessage(),
                        errorMessage = if (it.selectedMode == DiscoverSearchMode.Trends) {
                            error.toDiscoverErrorMessage()
                        } else {
                            it.errorMessage
                        },
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateQuery(query: String) {
        mutableState.update {
            if (it.query == query) {
                it.copy(errorMessage = null, requiresRelogin = false)
            } else {
                it.copy(
                    query = query,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    federationInstances = emptyList(),
                    selectedFederationInstance = null,
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
                nextNotesUntilId = null,
                users = emptyList(),
                federationInstances = emptyList(),
                selectedFederationInstance = null,
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
                selectedMode = DiscoverSearchMode.Notes,
                notes = emptyList(),
                nextNotesUntilId = null,
                users = emptyList(),
                trends = emptyList(),
                federationInstances = emptyList(),
                selectedFederationInstance = null,
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
                nextNotesUntilId = null,
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
                    nextNotesUntilId = null,
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
                it.copy(
                    errorMessage = "实例未启用趋势",
                    trendErrorMessage = "实例未启用趋势",
                    requiresRelogin = false,
                )
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
                nextNotesUntilId = null,
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
        val current = state.value
        val query = current.query
        val filters = current.filters
        val selectedMode = current.selectedMode
        if (current.isSearching) return
        if (
            selectedMode == DiscoverSearchMode.Notes &&
            !current.canSearchNotes &&
            query.toSingleHashtagQuery() == null
        ) {
            mutableState.update {
                it.copy(
                    hasSearched = true,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    errorMessage = "实例未启用帖子搜索",
                )
            }
            return
        }
        if (selectedMode == DiscoverSearchMode.Trends && !current.canTrend) {
            mutableState.update {
                it.copy(
                    hasSearched = true,
                    trends = emptyList(),
                    errorMessage = "实例未启用趋势",
                    trendErrorMessage = "实例未启用趋势",
                )
            }
            return
        }
        if (selectedMode == DiscoverSearchMode.Federation && !current.canViewFederation) {
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
                nextNotesUntilId = null,
                selectedFederationInstance = null,
                federationDetailMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            try {
                applyResult(
                    result = when (selectedMode) {
                        DiscoverSearchMode.Notes -> repository.search(query, filters)
                        DiscoverSearchMode.Users -> repository.searchUsers(query, filters)
                        DiscoverSearchMode.Trends -> repository.loadTrends()
                        DiscoverSearchMode.Federation -> repository.loadFederation(emptyList(), filters)
                    },
                    loadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyResult(
                    result = DiscoverRepositoryResult.Error(error.toDiscoverErrorMessage()),
                    loadingMore = false,
                )
            }
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
            (current.selectedMode == DiscoverSearchMode.Notes && !current.canSearchNotes) ||
            (current.selectedMode == DiscoverSearchMode.Notes && current.notes.isEmpty() && current.nextNotesUntilId == null) ||
            (current.selectedMode == DiscoverSearchMode.Federation && !current.canViewFederation)
        ) {
            return
        }

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            try {
                applyResult(
                    result = when (current.selectedMode) {
                        DiscoverSearchMode.Federation -> repository.loadFederation(
                            current.federationInstances,
                            current.filters,
                        )
                        else -> repository.loadMore(
                            query = current.query,
                            currentNotes = current.notes,
                            filters = current.filters,
                            untilId = current.nextNotesUntilId,
                        )
                    },
                    loadingMore = true,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyResult(
                    result = DiscoverRepositoryResult.Error(error.toDiscoverErrorMessage()),
                    loadingMore = true,
                )
            }
        }
    }

    fun openFederationInstance(instance: FederationInstance) {
        mutableState.update {
            it.copy(
                selectedFederationInstance = instance,
                isLoadingFederationDetail = true,
                federationDetailMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.loadFederationInstance(instance.host)) {
                is DiscoverRepositoryResult.FederationInstanceSuccess -> mutableState.update {
                    it.copy(
                        selectedFederationInstance = result.instance,
                        federationInstances = it.federationInstances.replaceFederationInstance(result.instance),
                        isLoadingFederationDetail = false,
                        federationDetailMessage = null,
                        requiresRelogin = false,
                    )
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingFederationDetail = false,
                        federationDetailMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingFederationDetail = false,
                        federationDetailMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                else -> mutableState.update {
                    it.copy(isLoadingFederationDetail = false)
                }
            }
        }
    }

    fun closeFederationInstance() {
        mutableState.update {
            it.copy(
                selectedFederationInstance = null,
                isLoadingFederationDetail = false,
                federationActionHost = null,
                federationDetailMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun toggleFederationSilence(host: String) {
        val instance = state.value.findFederationInstance(host) ?: return
        updateFederationInstance(
            host = instance.host,
            isSilenced = !instance.isSilenced,
            isSuspended = instance.isSuspended || instance.isBlocked,
        )
    }

    fun toggleFederationBlock(host: String) {
        val instance = state.value.findFederationInstance(host) ?: return
        updateFederationInstance(
            host = instance.host,
            isSilenced = instance.isSilenced,
            isSuspended = !(instance.isSuspended || instance.isBlocked),
        )
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
                    nextNotesUntilId = result.nextUntilId,
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
                    nextNotesUntilId = null,
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
                    nextNotesUntilId = null,
                    users = emptyList(),
                    federationInstances = emptyList(),
                    isRefreshingTrends = false,
                    trendErrorMessage = null,
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
                    nextNotesUntilId = null,
                    users = emptyList(),
                    trends = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.FederationInstanceSuccess -> mutableState.update {
                it.copy(
                    selectedFederationInstance = result.instance,
                    federationInstances = it.federationInstances.replaceFederationInstance(result.instance),
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingFederationDetail = false,
                    federationDetailMessage = null,
                    requiresRelogin = false,
                )
            }
            DiscoverRepositoryResult.FederationActionSuccess -> mutableState.update {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    federationActionHost = null,
                    federationDetailMessage = "联邦设置已更新",
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

    private fun updateFederationInstance(
        host: String,
        isSilenced: Boolean,
        isSuspended: Boolean,
    ) {
        if (state.value.federationActionHost != null) return
        mutableState.update {
            it.copy(
                federationActionHost = host,
                federationDetailMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.updateFederationInstance(host, isSilenced, isSuspended)) {
                DiscoverRepositoryResult.FederationActionSuccess -> {
                    val current = state.value.findFederationInstance(host)
                    val updated = current?.copy(
                        isSilenced = isSilenced,
                        isSuspended = isSuspended,
                        isBlocked = if (isSuspended) current.isBlocked else false,
                    )
                    mutableState.update {
                        it.copy(
                            federationInstances = if (updated == null) {
                                it.federationInstances
                            } else {
                                it.federationInstances.replaceFederationInstance(updated)
                            },
                            selectedFederationInstance = updated ?: it.selectedFederationInstance,
                            federationActionHost = null,
                            federationDetailMessage = "联邦设置已更新",
                            requiresRelogin = false,
                        )
                    }
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        federationActionHost = null,
                        federationDetailMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        federationActionHost = null,
                        federationDetailMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                else -> mutableState.update { it.copy(federationActionHost = null) }
            }
        }
    }
}

private fun Throwable.toDiscoverErrorMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "搜索失败，请稍后重试"
}

private fun String.toSingleHashtagQuery(): String? {
    val cleanValue = trim()
    if (!cleanValue.startsWith("#") || cleanValue.any { it.isWhitespace() }) return null
    return cleanValue.removePrefix("#").takeIf { it.isNotBlank() }
}

private fun DiscoverUiState.findFederationInstance(host: String): FederationInstance? {
    return selectedFederationInstance?.takeIf { it.host == host }
        ?: federationInstances.firstOrNull { it.host == host }
}

private fun List<FederationInstance>.replaceFederationInstance(
    instance: FederationInstance,
): List<FederationInstance> {
    val index = indexOfFirst { it.id == instance.id || it.host == instance.host }
    return if (index == -1) this else toMutableList().also { it[index] = instance }
}
