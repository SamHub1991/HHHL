package cc.hhhl.client.state

import cc.hhhl.client.api.DiscoverRecommendationFeedbackEvent
import cc.hhhl.client.api.DiscoverRecommendedTimelineCategory
import cc.hhhl.client.api.DiscoverRecommendedTimelineOptions
import cc.hhhl.client.api.DiscoverRecommendedTimelineScope
import cc.hhhl.client.api.DiscoverRecommendedTimelineSurface
import cc.hhhl.client.model.DiscoverySections
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.NoteSearchTrends
import cc.hhhl.client.model.RoleSummary
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
    Hashtags("话题"),
    Roles("角色"),
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
    val pinnedUsers: List<User> = emptyList(),
    val isLoadingPinnedUsers: Boolean = false,
    val pinnedUsersMessage: String? = null,
    val discoverySections: DiscoverySections = DiscoverySections(),
    val searchTrends: NoteSearchTrends = NoteSearchTrends(),
    val recommendedNotes: List<Note> = emptyList(),
    val recommendedScope: DiscoverRecommendedTimelineScope = DiscoverRecommendedTimelineScope.Mixed,
    val recommendedCategory: DiscoverRecommendedTimelineCategory = DiscoverRecommendedTimelineCategory.ForYou,
    val recommendedWithFiles: Boolean = false,
    val isLoadingDiscoverySections: Boolean = false,
    val isLoadingSearchTrends: Boolean = false,
    val isLoadingRecommendedNotes: Boolean = false,
    val isLoadingMoreRecommendedNotes: Boolean = false,
    val recommendedEndReached: Boolean = false,
    val discoveryMessage: String? = null,
    val searchTrendsMessage: String? = null,
    val recommendedMessage: String? = null,
    val roles: List<RoleSummary> = emptyList(),
    val selectedRole: RoleSummary? = null,
    val roleUsers: List<User> = emptyList(),
    val roleNotes: List<Note> = emptyList(),
    val isLoadingRoleDetails: Boolean = false,
    val roleDetailMessage: String? = null,
    val trends: List<TrendingHashtag> = emptyList(),
    val selectedHashtag: TrendingHashtag? = null,
    val hashtagUsers: List<User> = emptyList(),
    val isLoadingHashtagDetails: Boolean = false,
    val hashtagDetailMessage: String? = null,
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
    private var searchRequestId = 0
    private var trendRefreshRequestId = 0
    private var federationDetailRequestId = 0
    private var hashtagDetailRequestId = 0
    private var pinnedUsersRequestId = 0
    private var roleDetailRequestId = 0
    private var discoveryHomeRequestId = 0
    private var searchTrendsRequestId = 0
    private var recommendedTimelineRequestId = 0
    private var federationActionRequestId = 0
    private val recommendedImpressionNoteIds = mutableSetOf<String>()

    fun refreshPinnedUsersQuietly(force: Boolean = false) {
        val current = state.value
        if (current.isLoadingPinnedUsers) return
        if (!force && current.pinnedUsers.isNotEmpty()) return
        val requestId = ++pinnedUsersRequestId

        mutableState.update {
            it.copy(
                isLoadingPinnedUsers = true,
                pinnedUsersMessage = null,
            )
        }

        scope.launch {
            when (val result = repository.loadPinnedUsers()) {
                is DiscoverRepositoryResult.PinnedUsersSuccess -> mutableState.update {
                    if (requestId != pinnedUsersRequestId) return@update it
                    it.copy(
                        pinnedUsers = result.users,
                        isLoadingPinnedUsers = false,
                        pinnedUsersMessage = if (result.users.isEmpty()) "暂无推荐用户" else null,
                    )
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != pinnedUsersRequestId) return@update it
                    it.copy(
                        isLoadingPinnedUsers = false,
                        pinnedUsersMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    if (requestId != pinnedUsersRequestId) return@update it
                    it.copy(
                        isLoadingPinnedUsers = false,
                        pinnedUsersMessage = result.message,
                    )
                }
                else -> mutableState.update {
                    if (requestId != pinnedUsersRequestId) return@update it
                    it.copy(isLoadingPinnedUsers = false)
                }
            }
        }
    }

    fun refreshHomeQuietly(force: Boolean = false) {
        refreshDiscoverySectionsQuietly(force)
        refreshSearchTrendsQuietly(force)
        refreshRecommendedTimeline(force = force)
    }

    fun refreshDiscoverySectionsQuietly(force: Boolean = false) {
        val current = state.value
        if (current.isLoadingDiscoverySections) return
        if (!force && !current.discoverySections.isEmpty) return
        val requestId = ++discoveryHomeRequestId

        mutableState.update {
            it.copy(
                isLoadingDiscoverySections = true,
                discoveryMessage = null,
            )
        }

        scope.launch {
            try {
                when (val result = repository.loadDiscoverySections()) {
                    is DiscoverRepositoryResult.DiscoverySectionsSuccess -> mutableState.update {
                        if (requestId != discoveryHomeRequestId) return@update it
                        it.copy(
                            discoverySections = result.sections,
                            searchTrends = if (result.sections.searchTrends.isEmpty) it.searchTrends else result.sections.searchTrends,
                            isLoadingDiscoverySections = false,
                            discoveryMessage = if (result.sections.isEmpty) "暂无发现内容" else null,
                            requiresRelogin = false,
                        )
                    }
                    DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                        if (requestId != discoveryHomeRequestId) return@update it
                        it.copy(
                            isLoadingDiscoverySections = false,
                            discoveryMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is DiscoverRepositoryResult.Error -> mutableState.update {
                        if (requestId != discoveryHomeRequestId) return@update it
                        it.copy(
                            isLoadingDiscoverySections = false,
                            discoveryMessage = result.message,
                            requiresRelogin = false,
                        )
                    }
                    else -> mutableState.update {
                        if (requestId != discoveryHomeRequestId) return@update it
                        it.copy(isLoadingDiscoverySections = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    if (requestId != discoveryHomeRequestId) return@update it
                    it.copy(
                        isLoadingDiscoverySections = false,
                        discoveryMessage = error.toDiscoverErrorMessage(),
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun refreshSearchTrendsQuietly(force: Boolean = false) {
        val current = state.value
        if (current.isLoadingSearchTrends) return
        if (!force && !current.searchTrends.isEmpty) return
        val requestId = ++searchTrendsRequestId

        mutableState.update {
            it.copy(
                isLoadingSearchTrends = true,
                searchTrendsMessage = null,
            )
        }

        scope.launch {
            try {
                when (val result = repository.loadSearchTrends()) {
                    is DiscoverRepositoryResult.SearchTrendsSuccess -> mutableState.update {
                        if (requestId != searchTrendsRequestId) return@update it
                        it.copy(
                            searchTrends = result.trends,
                            isLoadingSearchTrends = false,
                            searchTrendsMessage = if (result.trends.isEmpty) "暂无搜索趋势" else null,
                            requiresRelogin = false,
                        )
                    }
                    DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                        if (requestId != searchTrendsRequestId) return@update it
                        it.copy(
                            isLoadingSearchTrends = false,
                            searchTrendsMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is DiscoverRepositoryResult.Error -> mutableState.update {
                        if (requestId != searchTrendsRequestId) return@update it
                        it.copy(
                            isLoadingSearchTrends = false,
                            searchTrendsMessage = result.message,
                            requiresRelogin = false,
                        )
                    }
                    else -> mutableState.update {
                        if (requestId != searchTrendsRequestId) return@update it
                        it.copy(isLoadingSearchTrends = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    if (requestId != searchTrendsRequestId) return@update it
                    it.copy(
                        isLoadingSearchTrends = false,
                        searchTrendsMessage = error.toDiscoverErrorMessage(),
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun refreshTrendsQuietly() {
        val current = state.value
        if (!current.canTrend || current.isRefreshingTrends) return
        if (current.selectedMode == DiscoverSearchMode.Trends && current.isSearching) return
        val requestId = ++trendRefreshRequestId

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
                        if (requestId != trendRefreshRequestId) return@update it
                        it.copy(
                            trends = result.trends,
                            isRefreshingTrends = false,
                            trendErrorMessage = null,
                            errorMessage = if (it.selectedMode == DiscoverSearchMode.Trends) null else it.errorMessage,
                            requiresRelogin = false,
                        )
                    }
                    DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                        if (requestId != trendRefreshRequestId) return@update it
                        it.copy(
                            isRefreshingTrends = false,
                            trendErrorMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is DiscoverRepositoryResult.Error -> mutableState.update {
                        if (requestId != trendRefreshRequestId) return@update it
                        it.copy(
                            isRefreshingTrends = false,
                            trendErrorMessage = result.message,
                            errorMessage = if (it.selectedMode == DiscoverSearchMode.Trends) result.message else it.errorMessage,
                            requiresRelogin = false,
                        )
                    }
                    else -> mutableState.update {
                        if (requestId != trendRefreshRequestId) return@update it
                        it.copy(isRefreshingTrends = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    if (requestId != trendRefreshRequestId) return@update it
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

    fun updateRecommendedScope(scope: DiscoverRecommendedTimelineScope) {
        if (state.value.recommendedScope == scope) return
        recommendedTimelineRequestId += 1
        recommendedImpressionNoteIds.clear()
        mutableState.update {
            it.copy(
                recommendedScope = scope,
                recommendedNotes = emptyList(),
                recommendedEndReached = false,
                recommendedMessage = null,
                isLoadingRecommendedNotes = false,
                isLoadingMoreRecommendedNotes = false,
                requiresRelogin = false,
            )
        }
        refreshRecommendedTimeline(force = true)
    }

    fun updateRecommendedCategory(category: DiscoverRecommendedTimelineCategory) {
        if (state.value.recommendedCategory == category) return
        recommendedTimelineRequestId += 1
        recommendedImpressionNoteIds.clear()
        mutableState.update {
            it.copy(
                recommendedCategory = category,
                recommendedNotes = emptyList(),
                recommendedEndReached = false,
                recommendedMessage = null,
                isLoadingRecommendedNotes = false,
                isLoadingMoreRecommendedNotes = false,
                requiresRelogin = false,
            )
        }
        refreshRecommendedTimeline(force = true)
    }

    fun toggleRecommendedWithFiles() {
        recommendedTimelineRequestId += 1
        recommendedImpressionNoteIds.clear()
        mutableState.update {
            it.copy(
                recommendedWithFiles = !it.recommendedWithFiles,
                recommendedNotes = emptyList(),
                recommendedEndReached = false,
                recommendedMessage = null,
                isLoadingRecommendedNotes = false,
                isLoadingMoreRecommendedNotes = false,
                requiresRelogin = false,
            )
        }
        refreshRecommendedTimeline(force = true)
    }

    fun refreshRecommendedTimeline(force: Boolean = false) {
        val current = state.value
        if (current.isLoadingRecommendedNotes || current.isLoadingMoreRecommendedNotes) return
        if (!force && current.recommendedNotes.isNotEmpty()) return
        loadRecommendedTimeline(loadMore = false)
    }

    fun loadMoreRecommendedTimeline() {
        val current = state.value
        if (
            current.isLoadingRecommendedNotes ||
            current.isLoadingMoreRecommendedNotes ||
            current.recommendedEndReached ||
            current.recommendedNotes.isEmpty()
        ) {
            return
        }
        loadRecommendedTimeline(loadMore = true)
    }

    fun sendRecommendationFeedback(
        noteId: String,
        event: DiscoverRecommendationFeedbackEvent,
        dwellMs: Int? = null,
    ) {
        val cleanNoteId = noteId.trim()
        if (cleanNoteId.isBlank()) return
        if (event == DiscoverRecommendationFeedbackEvent.Impression && !recommendedImpressionNoteIds.add(cleanNoteId)) {
            return
        }
        scope.launch {
            when (repository.sendRecommendationFeedback(cleanNoteId, event, dwellMs)) {
                DiscoverRepositoryResult.Unauthorized -> mutableState.update { it.copy(requiresRelogin = true) }
                else -> Unit
            }
        }
    }

    fun updateQuery(query: String) {
        searchRequestId += 1
        federationDetailRequestId += 1
        roleDetailRequestId += 1
        mutableState.update {
            if (it.query == query) {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingFederationDetail = false,
                    isLoadingRoleDetails = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            } else {
                it.copy(
                    query = query,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
                    federationInstances = emptyList(),
                    selectedFederationInstance = null,
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingFederationDetail = false,
                    endReached = false,
                    hasSearched = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun updateFilters(filters: DiscoverAdvancedFilters) {
        searchRequestId += 1
        federationDetailRequestId += 1
        roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                filters = filters,
                notes = emptyList(),
                nextNotesUntilId = null,
                users = emptyList(),
                roles = emptyList(),
                selectedRole = null,
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = false,
                roleDetailMessage = null,
                federationInstances = emptyList(),
                selectedFederationInstance = null,
                isSearching = false,
                isLoadingMore = false,
                isLoadingFederationDetail = false,
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
        val requestId = ++searchRequestId
        val detailRequestId = ++hashtagDetailRequestId
        federationDetailRequestId += 1
        roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                query = cleanTag,
                selectedMode = DiscoverSearchMode.Hashtags,
                notes = emptyList(),
                nextNotesUntilId = null,
                users = emptyList(),
                roles = emptyList(),
                selectedRole = null,
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = false,
                roleDetailMessage = null,
                trends = emptyList(),
                selectedHashtag = null,
                hashtagUsers = emptyList(),
                isLoadingHashtagDetails = true,
                hashtagDetailMessage = null,
                federationInstances = emptyList(),
                selectedFederationInstance = null,
                endReached = false,
                hasSearched = true,
                isSearching = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            applyResult(
                result = repository.showHashtag(cleanTag),
                loadingMore = false,
                requestId = requestId,
            )
        }
        scope.launch {
            val usersResult = repository.loadHashtagUsers(cleanTag, state.value.filters)
            if (detailRequestId != hashtagDetailRequestId || state.value.query != cleanTag) return@launch
            when (usersResult) {
                is DiscoverRepositoryResult.UserSuccess -> mutableState.update {
                    it.copy(
                        hashtagUsers = usersResult.users,
                        isLoadingHashtagDetails = false,
                        hashtagDetailMessage = if (usersResult.users.isEmpty()) "暂无使用者" else null,
                        requiresRelogin = false,
                    )
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isLoadingHashtagDetails = false,
                        hashtagDetailMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isLoadingHashtagDetails = false,
                        hashtagDetailMessage = usersResult.message,
                        requiresRelogin = false,
                    )
                }
                else -> mutableState.update { it.copy(isLoadingHashtagDetails = false) }
            }
        }
    }

    fun openMention(username: String) {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return
        searchRequestId += 1
        federationDetailRequestId += 1
        roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                query = cleanUsername,
                selectedMode = DiscoverSearchMode.Users,
                notes = emptyList(),
                nextNotesUntilId = null,
                users = emptyList(),
                roles = emptyList(),
                selectedRole = null,
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = false,
                roleDetailMessage = null,
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

    fun openRole(roleId: String) {
        val cleanRoleId = roleId.trim()
        if (cleanRoleId.isBlank()) return
        val requestId = ++roleDetailRequestId
        mutableState.update {
            it.copy(
                selectedRole = it.roles.firstOrNull { role -> role.id == cleanRoleId },
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = true,
                roleDetailMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            val detailResult = repository.openRole(cleanRoleId)
            if (requestId != roleDetailRequestId) return@launch
            when (detailResult) {
                is DiscoverRepositoryResult.RoleDetailSuccess -> mutableState.update {
                    it.copy(selectedRole = detailResult.role, roleDetailMessage = null)
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(roleDetailMessage = "登录已失效，请重新登录", requiresRelogin = true)
                }
                is DiscoverRepositoryResult.Error -> mutableState.update { it.copy(roleDetailMessage = detailResult.message) }
                else -> Unit
            }
            val usersResult = repository.loadRoleUsers(cleanRoleId)
            if (requestId != roleDetailRequestId) return@launch
            val notesResult = repository.loadRoleNotes(cleanRoleId)
            if (requestId != roleDetailRequestId) return@launch
            mutableState.update { current ->
                var next = current.copy(isLoadingRoleDetails = false)
                when (usersResult) {
                    is DiscoverRepositoryResult.RoleUsersSuccess -> next = next.copy(roleUsers = usersResult.users)
                    DiscoverRepositoryResult.Unauthorized -> next = next.copy(roleDetailMessage = "登录已失效，请重新登录", requiresRelogin = true)
                    is DiscoverRepositoryResult.Error -> next = next.copy(roleDetailMessage = usersResult.message)
                    else -> Unit
                }
                when (notesResult) {
                    is DiscoverRepositoryResult.RoleNotesSuccess -> next = next.copy(roleNotes = notesResult.notes)
                    DiscoverRepositoryResult.Unauthorized -> next = next.copy(roleDetailMessage = "登录已失效，请重新登录", requiresRelogin = true)
                    is DiscoverRepositoryResult.Error -> next = next.copy(roleDetailMessage = notesResult.message)
                    else -> Unit
                }
                next
            }
        }
    }

    fun closeRoleDetail() {
        roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                selectedRole = null,
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = false,
                roleDetailMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateCapabilities(
        canSearchNotes: Boolean,
        canTrend: Boolean = state.value.canTrend,
        canViewFederation: Boolean = state.value.canViewFederation,
    ) {
        searchRequestId += 1
        trendRefreshRequestId += 1
        federationDetailRequestId += 1
        roleDetailRequestId += 1
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

        searchRequestId += 1
        federationDetailRequestId += 1
        roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                selectedMode = mode,
                notes = emptyList(),
                nextNotesUntilId = null,
                users = emptyList(),
                roles = emptyList(),
                selectedRole = null,
                roleUsers = emptyList(),
                roleNotes = emptyList(),
                isLoadingRoleDetails = false,
                roleDetailMessage = null,
                trends = emptyList(),
                federationInstances = emptyList(),
                isSearching = false,
                isLoadingMore = false,
                isLoadingFederationDetail = false,
                endReached = false,
                hasSearched = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        if (mode == DiscoverSearchMode.Trends || mode == DiscoverSearchMode.Federation || mode == DiscoverSearchMode.Roles) {
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

        val requestId = ++searchRequestId
        if (selectedMode != DiscoverSearchMode.Roles) roleDetailRequestId += 1
        mutableState.update {
            it.copy(
                isSearching = true,
                hasSearched = true,
                endReached = false,
                nextNotesUntilId = null,
                selectedFederationInstance = null,
                federationDetailMessage = null,
                selectedRole = if (selectedMode == DiscoverSearchMode.Roles) it.selectedRole else null,
                roleUsers = if (selectedMode == DiscoverSearchMode.Roles) it.roleUsers else emptyList(),
                roleNotes = if (selectedMode == DiscoverSearchMode.Roles) it.roleNotes else emptyList(),
                isLoadingRoleDetails = if (selectedMode == DiscoverSearchMode.Roles) it.isLoadingRoleDetails else false,
                roleDetailMessage = if (selectedMode == DiscoverSearchMode.Roles) it.roleDetailMessage else null,
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
                        DiscoverSearchMode.Hashtags -> if (query.isBlank()) {
                            repository.loadHashtags()
                        } else {
                            repository.searchHashtags(query)
                        }
                        DiscoverSearchMode.Roles -> repository.loadRoles()
                        DiscoverSearchMode.Trends -> repository.loadTrends()
                        DiscoverSearchMode.Federation -> repository.loadFederation(emptyList(), filters)
                    },
                    loadingMore = false,
                    requestId = requestId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyResult(
                    result = DiscoverRepositoryResult.Error(error.toDiscoverErrorMessage()),
                    loadingMore = false,
                    requestId = requestId,
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
            current.selectedMode == DiscoverSearchMode.Roles ||
            current.selectedMode == DiscoverSearchMode.Trends ||
            current.endReached ||
            (current.selectedMode == DiscoverSearchMode.Notes && !current.canSearchNotes) ||
            (current.selectedMode == DiscoverSearchMode.Notes && current.notes.isEmpty() && current.nextNotesUntilId == null) ||
            (current.selectedMode == DiscoverSearchMode.Federation && !current.canViewFederation) ||
            (current.selectedMode == DiscoverSearchMode.Hashtags && current.query.isNotBlank()) ||
            (current.selectedMode == DiscoverSearchMode.Hashtags && current.trends.isEmpty())
        ) {
            return
        }

        val requestId = ++searchRequestId
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
                        DiscoverSearchMode.Hashtags -> repository.loadMoreHashtags(current.trends)
                        else -> repository.loadMore(
                            query = current.query,
                            currentNotes = current.notes,
                            filters = current.filters,
                            untilId = current.nextNotesUntilId,
                        )
                    },
                    loadingMore = true,
                    requestId = requestId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyResult(
                    result = DiscoverRepositoryResult.Error(error.toDiscoverErrorMessage()),
                    loadingMore = true,
                    requestId = requestId,
                )
            }
        }
    }

    fun openFederationInstance(instance: FederationInstance) {
        val requestId = ++federationDetailRequestId
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
                    if (requestId != federationDetailRequestId || it.selectedFederationInstance?.host != instance.host) {
                        return@update it
                    }
                    it.copy(
                        selectedFederationInstance = result.instance,
                        federationInstances = it.federationInstances.replaceFederationInstance(result.instance),
                        isLoadingFederationDetail = false,
                        federationDetailMessage = null,
                        requiresRelogin = false,
                    )
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != federationDetailRequestId || it.selectedFederationInstance?.host != instance.host) {
                        return@update it
                    }
                    it.copy(
                        isLoadingFederationDetail = false,
                        federationDetailMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    if (requestId != federationDetailRequestId || it.selectedFederationInstance?.host != instance.host) {
                        return@update it
                    }
                    it.copy(
                        isLoadingFederationDetail = false,
                        federationDetailMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                else -> mutableState.update {
                    if (requestId != federationDetailRequestId || it.selectedFederationInstance?.host != instance.host) {
                        return@update it
                    }
                    it.copy(isLoadingFederationDetail = false)
                }
            }
        }
    }

    fun closeFederationInstance() {
        federationDetailRequestId += 1
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
                recommendedNotes = it.recommendedNotes.applyNoteLocalMutation(mutation),
                discoverySections = it.discoverySections.applyNoteLocalMutation(mutation),
                requiresRelogin = false,
            )
        }
    }

    private fun loadRecommendedTimeline(loadMore: Boolean) {
        val current = state.value
        val requestId = ++recommendedTimelineRequestId
        val offset = if (loadMore) current.recommendedNotes.size else 0
        val options = DiscoverRecommendedTimelineOptions(
            scope = current.recommendedScope,
            surface = DiscoverRecommendedTimelineSurface.Explore,
            category = current.recommendedCategory,
            withFiles = current.recommendedWithFiles,
            withRenotes = true,
            withBots = true,
            limit = RECOMMENDED_TIMELINE_PAGE_SIZE,
            offset = offset,
        )

        mutableState.update {
            if (loadMore) {
                it.copy(
                    isLoadingMoreRecommendedNotes = true,
                    recommendedMessage = null,
                    requiresRelogin = false,
                )
            } else {
                it.copy(
                    isLoadingRecommendedNotes = true,
                    isLoadingMoreRecommendedNotes = false,
                    recommendedEndReached = false,
                    recommendedMessage = null,
                    requiresRelogin = false,
                )
            }
        }

        scope.launch {
            try {
                val baseNotes = if (loadMore) state.value.recommendedNotes else emptyList()
                when (
                    val result = repository.loadRecommendedTimeline(
                        currentNotes = baseNotes,
                        options = options,
                    )
                ) {
                    is DiscoverRepositoryResult.RecommendedTimelineSuccess -> mutableState.update {
                        if (requestId != recommendedTimelineRequestId) return@update it
                        it.copy(
                            recommendedNotes = result.notes,
                            isLoadingRecommendedNotes = false,
                            isLoadingMoreRecommendedNotes = false,
                            recommendedEndReached = result.endReached,
                            recommendedMessage = if (result.notes.isEmpty()) "暂无推荐帖子" else null,
                            requiresRelogin = false,
                        )
                    }
                    DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                        if (requestId != recommendedTimelineRequestId) return@update it
                        it.copy(
                            isLoadingRecommendedNotes = false,
                            isLoadingMoreRecommendedNotes = false,
                            recommendedMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        )
                    }
                    is DiscoverRepositoryResult.Error -> mutableState.update {
                        if (requestId != recommendedTimelineRequestId) return@update it
                        it.copy(
                            isLoadingRecommendedNotes = false,
                            isLoadingMoreRecommendedNotes = false,
                            recommendedMessage = result.message,
                            requiresRelogin = false,
                        )
                    }
                    else -> mutableState.update {
                        if (requestId != recommendedTimelineRequestId) return@update it
                        it.copy(
                            isLoadingRecommendedNotes = false,
                            isLoadingMoreRecommendedNotes = false,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    if (requestId != recommendedTimelineRequestId) return@update it
                    it.copy(
                        isLoadingRecommendedNotes = false,
                        isLoadingMoreRecommendedNotes = false,
                        recommendedMessage = error.toDiscoverErrorMessage(),
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun applyResult(
        result: DiscoverRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
    ) {
        when (result) {
            is DiscoverRepositoryResult.Success -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    notes = result.notes,
                    nextNotesUntilId = result.nextUntilId,
                    users = emptyList(),
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
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
                if (requestId != searchRequestId) return@update it
                it.copy(
                    users = result.users,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
                    trends = emptyList(),
                    federationInstances = emptyList(),
                    selectedHashtag = null,
                    hashtagUsers = emptyList(),
                    isLoadingHashtagDetails = false,
                    hashtagDetailMessage = null,
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.RoleSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    roles = result.roles,
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    trends = emptyList(),
                    federationInstances = emptyList(),
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = true,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.RoleDetailSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(selectedRole = result.role, isSearching = false, isLoadingMore = false)
            }
            is DiscoverRepositoryResult.RoleUsersSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(roleUsers = result.users, isSearching = false, isLoadingMore = false)
            }
            is DiscoverRepositoryResult.RoleNotesSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(roleNotes = result.notes, isSearching = false, isLoadingMore = false)
            }
            is DiscoverRepositoryResult.TrendSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    trends = result.trends,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
                    federationInstances = emptyList(),
                    selectedHashtag = null,
                    hashtagUsers = emptyList(),
                    isLoadingHashtagDetails = false,
                    hashtagDetailMessage = null,
                    isRefreshingTrends = false,
                    trendErrorMessage = null,
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = true,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.HashtagSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    trends = listOf(result.hashtag),
                    selectedHashtag = result.hashtag,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
                    federationInstances = emptyList(),
                    hashtagUsers = emptyList(),
                    isLoadingHashtagDetails = false,
                    hashtagDetailMessage = null,
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = true,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.FederationSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    federationInstances = result.instances,
                    notes = emptyList(),
                    nextNotesUntilId = null,
                    users = emptyList(),
                    roles = emptyList(),
                    selectedRole = null,
                    roleUsers = emptyList(),
                    roleNotes = emptyList(),
                    isLoadingRoleDetails = false,
                    roleDetailMessage = null,
                    trends = emptyList(),
                    selectedHashtag = null,
                    hashtagUsers = emptyList(),
                    isLoadingHashtagDetails = false,
                    hashtagDetailMessage = null,
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.FederationInstanceSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
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
            is DiscoverRepositoryResult.FederationFollowSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    federationDetailMessage = if (result.follows.isEmpty()) "暂无联邦关系" else null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.FederationStatsSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    federationDetailMessage = null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.PinnedUsersSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    pinnedUsers = result.users,
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingPinnedUsers = false,
                    pinnedUsersMessage = if (result.users.isEmpty()) "暂无推荐用户" else null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.DiscoverySectionsSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    discoverySections = result.sections,
                    searchTrends = if (result.sections.searchTrends.isEmpty) it.searchTrends else result.sections.searchTrends,
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingDiscoverySections = false,
                    discoveryMessage = if (result.sections.isEmpty) "暂无发现内容" else null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.SearchTrendsSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    searchTrends = result.trends,
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingSearchTrends = false,
                    searchTrendsMessage = if (result.trends.isEmpty) "暂无搜索趋势" else null,
                    requiresRelogin = false,
                )
            }
            is DiscoverRepositoryResult.RecommendedTimelineSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    recommendedNotes = result.notes,
                    isSearching = false,
                    isLoadingMore = false,
                    isLoadingRecommendedNotes = false,
                    isLoadingMoreRecommendedNotes = false,
                    recommendedEndReached = result.endReached,
                    recommendedMessage = if (result.notes.isEmpty()) "暂无推荐帖子" else null,
                    requiresRelogin = false,
                )
            }
            DiscoverRepositoryResult.RecommendationFeedbackSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(isSearching = false, isLoadingMore = false, requiresRelogin = false)
            }
            DiscoverRepositoryResult.FederationActionSuccess -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    federationActionHost = null,
                    federationDetailMessage = "联邦设置已更新",
                    requiresRelogin = false,
                )
            }
            DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                if (requestId != searchRequestId) return@update it
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DiscoverRepositoryResult.Error -> mutableState.update {
                if (requestId != searchRequestId) return@update it
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
        val requestId = ++federationActionRequestId
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
                    mutableState.update { currentState ->
                        if (requestId != federationActionRequestId) return@update currentState
                        val current = currentState.findFederationInstance(host)
                        val updated = current?.copy(
                            isSilenced = isSilenced,
                            isSuspended = isSuspended,
                            isBlocked = if (isSuspended) current.isBlocked else false,
                        )
                        val isSelectedHost = currentState.selectedFederationInstance?.host == host
                        currentState.copy(
                            federationInstances = if (updated == null) {
                                currentState.federationInstances
                            } else {
                                currentState.federationInstances.replaceFederationInstance(updated)
                            },
                            selectedFederationInstance = if (isSelectedHost) {
                                updated ?: currentState.selectedFederationInstance
                            } else {
                                currentState.selectedFederationInstance
                            },
                            federationActionHost = if (currentState.federationActionHost == host) null else currentState.federationActionHost,
                            federationDetailMessage = if (isSelectedHost) "联邦设置已更新" else currentState.federationDetailMessage,
                            requiresRelogin = if (isSelectedHost) false else currentState.requiresRelogin,
                        )
                    }
                }
                DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != federationActionRequestId) return@update it
                    val isSelectedHost = it.selectedFederationInstance?.host == host
                    it.copy(
                        federationActionHost = if (it.federationActionHost == host) null else it.federationActionHost,
                        federationDetailMessage = if (isSelectedHost) "登录已失效，请重新登录" else it.federationDetailMessage,
                        requiresRelogin = true,
                    )
                }
                is DiscoverRepositoryResult.Error -> mutableState.update {
                    if (requestId != federationActionRequestId) return@update it
                    val isSelectedHost = it.selectedFederationInstance?.host == host
                    it.copy(
                        federationActionHost = if (it.federationActionHost == host) null else it.federationActionHost,
                        federationDetailMessage = if (isSelectedHost) result.message else it.federationDetailMessage,
                        requiresRelogin = if (isSelectedHost) false else it.requiresRelogin,
                    )
                }
                else -> mutableState.update {
                    if (requestId != federationActionRequestId) return@update it
                    it.copy(federationActionHost = if (it.federationActionHost == host) null else it.federationActionHost)
                }
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

private fun DiscoverySections.applyNoteLocalMutation(mutation: NoteLocalMutation): DiscoverySections {
    return copy(
        coverNotes = coverNotes.applyNoteLocalMutation(mutation),
        hotNotes = hotNotes.applyNoteLocalMutation(mutation),
        tutorialNotes = tutorialNotes.applyNoteLocalMutation(mutation),
        channels = channels.map { channel ->
            channel.copy(pinnedNotes = channel.pinnedNotes.applyNoteLocalMutation(mutation))
        },
    )
}

private const val RECOMMENDED_TIMELINE_PAGE_SIZE = 20
