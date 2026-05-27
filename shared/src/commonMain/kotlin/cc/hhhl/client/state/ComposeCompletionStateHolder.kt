package cc.hhhl.client.state

import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DiscoverRepository
import cc.hhhl.client.repository.DiscoverRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComposeCompletionUiState(
    val activeKind: ComposeCompletionKind? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val hashtags: List<TrendingHashtag> = emptyList(),
    val users: List<User> = emptyList(),
    val errorMessage: String? = null,
)

enum class ComposeCompletionKind {
    Hashtag,
    Mention,
    Emoji,
}

class ComposeCompletionStateHolder(
    private val repository: DiscoverRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ComposeCompletionUiState())
    val state: StateFlow<ComposeCompletionUiState> = mutableState

    private var searchJob: Job? = null
    private var trendCache: List<TrendingHashtag>? = null
    private val userCache = LinkedHashMap<String, List<User>>()

    fun request(kind: ComposeCompletionKind?, query: String) {
        val cleanQuery = query.trim().trimStart('#', '@', ':')
        searchJob?.cancel()
        if (kind == null) {
            mutableState.update { ComposeCompletionUiState() }
            return
        }

        mutableState.update {
            it.copy(
                activeKind = kind,
                query = cleanQuery,
                isLoading = kind == ComposeCompletionKind.Mention && cleanQuery.isNotBlank(),
                errorMessage = null,
                hashtags = if (kind == ComposeCompletionKind.Hashtag) it.hashtags else emptyList(),
                users = if (kind == ComposeCompletionKind.Mention) it.users else emptyList(),
            )
        }

        searchJob = scope.launch {
            when (kind) {
                ComposeCompletionKind.Hashtag -> loadHashtags(cleanQuery)
                ComposeCompletionKind.Mention -> loadUsers(cleanQuery)
                ComposeCompletionKind.Emoji -> mutableState.update {
                    it.copy(
                        activeKind = ComposeCompletionKind.Emoji,
                        query = cleanQuery,
                        isLoading = false,
                        hashtags = emptyList(),
                        users = emptyList(),
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private suspend fun loadHashtags(query: String) {
        val trends = trendCache ?: when (val result = repository.loadTrends()) {
            is DiscoverRepositoryResult.TrendSuccess -> result.trends.also { trendCache = it }
            is DiscoverRepositoryResult.Error -> {
                mutableState.update {
                    it.copy(isLoading = false, hashtags = emptyList(), errorMessage = result.message)
                }
                return
            }
            else -> emptyList()
        }
        val filtered = trends
            .filter { trend -> query.isBlank() || trend.tag.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<TrendingHashtag> { it.usersCount }.thenBy { it.tag.lowercase() })
            .take(8)
        mutableState.update {
            it.copy(
                activeKind = ComposeCompletionKind.Hashtag,
                query = query,
                isLoading = false,
                hashtags = filtered,
                users = emptyList(),
                errorMessage = null,
            )
        }
    }

    private suspend fun loadUsers(query: String) {
        if (query.isBlank()) {
            mutableState.update {
                it.copy(
                    activeKind = ComposeCompletionKind.Mention,
                    query = query,
                    isLoading = false,
                    users = emptyList(),
                    hashtags = emptyList(),
                    errorMessage = null,
                )
            }
            return
        }
        delay(220)
        userCache[query]?.let { cachedUsers ->
            mutableState.update {
                it.copy(
                    activeKind = ComposeCompletionKind.Mention,
                    query = query,
                    isLoading = false,
                    users = cachedUsers,
                    hashtags = emptyList(),
                    errorMessage = null,
                )
            }
            return
        }

        when (val result = repository.searchUsers(query)) {
            is DiscoverRepositoryResult.UserSuccess -> {
                val users = result.users.take(8)
                userCache[query] = users
                while (userCache.size > 24) {
                    userCache.remove(userCache.keys.first())
                }
                mutableState.update {
                    it.copy(
                        activeKind = ComposeCompletionKind.Mention,
                        query = query,
                        isLoading = false,
                        users = users,
                        hashtags = emptyList(),
                        errorMessage = null,
                    )
                }
            }
            DiscoverRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(isLoading = false, users = emptyList(), errorMessage = "登录后可搜索用户")
            }
            is DiscoverRepositoryResult.Error -> mutableState.update {
                it.copy(isLoading = false, users = emptyList(), errorMessage = result.message)
            }
            else -> mutableState.update {
                it.copy(isLoading = false, users = emptyList())
            }
        }
    }
}
