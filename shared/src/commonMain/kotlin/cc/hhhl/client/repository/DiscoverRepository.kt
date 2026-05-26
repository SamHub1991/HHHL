package cc.hhhl.client.repository

import cc.hhhl.client.api.DiscoverApi
import cc.hhhl.client.api.DiscoverFederationResult
import cc.hhhl.client.api.DiscoverSearchResult
import cc.hhhl.client.api.DiscoverTrendResult
import cc.hhhl.client.api.DiscoverUserSearchResult
import cc.hhhl.client.api.SharkeyDiscoverApi
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.state.DiscoverAdvancedFilters

open class DiscoverRepository(
    private val tokenProvider: () -> String?,
    private val api: DiscoverApi = SharkeyDiscoverApi(),
) {
    open suspend fun search(
        query: String,
        filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    ): DiscoverRepositoryResult {
        return load(
            query = query,
            currentNotes = emptyList(),
            untilId = null,
            filters = filters,
        )
    }

    open suspend fun loadMore(
        query: String,
        currentNotes: List<Note>,
        filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    ): DiscoverRepositoryResult {
        return load(
            query = query,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
            filters = filters,
        )
    }

    open suspend fun searchUsers(
        query: String,
        filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    ): DiscoverRepositoryResult {
        val cleanQuery = query.takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Error("请输入关键词")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Unauthorized

        return when (
            val result = api.searchUsers(
                token = token,
                query = cleanQuery,
                limit = DEFAULT_PAGE_SIZE,
                origin = filters.origin.apiValue,
            )
        ) {
            is DiscoverUserSearchResult.Success -> DiscoverRepositoryResult.UserSuccess(
                result.users.filterUsersByDiscoverFilters(filters),
            )
            DiscoverUserSearchResult.Unauthorized -> DiscoverRepositoryResult.Unauthorized
            is DiscoverUserSearchResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverUserSearchResult.ServerError -> {
                DiscoverRepositoryResult.Error(result.message.toFriendlySearchError())
            }
        }
    }

    open suspend fun loadTrends(): DiscoverRepositoryResult {
        return when (val result = api.loadTrendingHashtags()) {
            is DiscoverTrendResult.Success -> DiscoverRepositoryResult.TrendSuccess(result.trends)
            is DiscoverTrendResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverTrendResult.ServerError -> DiscoverRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadFederation(
        currentInstances: List<FederationInstance>,
        filters: DiscoverAdvancedFilters = DiscoverAdvancedFilters(),
    ): DiscoverRepositoryResult {
        return when (
            val result = api.loadFederationInstances(
                limit = DEFAULT_FEDERATION_PAGE_SIZE,
                offset = currentInstances.size,
                host = filters.domain.takeIf { it.isNotBlank() },
            )
        ) {
            is DiscoverFederationResult.Success -> DiscoverRepositoryResult.FederationSuccess(
                instances = (currentInstances + result.instances).distinctBy { it.id },
                endReached = result.instances.isEmpty(),
            )
            is DiscoverFederationResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverFederationResult.ServerError -> DiscoverRepositoryResult.Error(result.message)
        }
    }

    private suspend fun load(
        query: String,
        currentNotes: List<Note>,
        untilId: String?,
        filters: DiscoverAdvancedFilters,
    ): DiscoverRepositoryResult {
        val cleanQuery = query.takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Error("请输入关键词")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Unauthorized

        return when (val result = api.searchNotes(token, cleanQuery, DEFAULT_PAGE_SIZE, untilId)) {
            is DiscoverSearchResult.Success -> DiscoverRepositoryResult.Success(
                notes = (currentNotes + result.notes.filterNotesByDiscoverFilters(filters)).distinctBy { it.id },
                endReached = result.notes.isEmpty(),
            )
            DiscoverSearchResult.Unauthorized -> DiscoverRepositoryResult.Unauthorized
            is DiscoverSearchResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverSearchResult.ServerError -> {
                DiscoverRepositoryResult.Error(result.message.toFriendlySearchError())
            }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_FEDERATION_PAGE_SIZE = 30
    }
}

private fun List<Note>.filterNotesByDiscoverFilters(filters: DiscoverAdvancedFilters): List<Note> {
    return filter { note ->
        val author = note.author
        val usernameMatches = filters.username.normalizedUsername()?.let { username ->
            author.username.equals(username, ignoreCase = true)
        } ?: true
        val domainMatches = filters.domain.normalizedDomain()?.let { domain ->
            author.host.equals(domain, ignoreCase = true)
        } ?: true
        val sinceMatches = filters.sinceDate.normalizedDatePrefix()?.let { sinceDate ->
            note.createdAt.take(10) >= sinceDate
        } ?: true
        val untilMatches = filters.untilDate.normalizedDatePrefix()?.let { untilDate ->
            note.createdAt.take(10) <= untilDate
        } ?: true

        usernameMatches && domainMatches && sinceMatches && untilMatches
    }
}

private fun List<User>.filterUsersByDiscoverFilters(filters: DiscoverAdvancedFilters): List<User> {
    return filter { user ->
        val usernameMatches = filters.username.normalizedUsername()?.let { username ->
            user.username.equals(username, ignoreCase = true)
        } ?: true
        val domainMatches = filters.domain.normalizedDomain()?.let { domain ->
            user.host.equals(domain, ignoreCase = true)
        } ?: true

        usernameMatches && domainMatches
    }
}

private fun String.normalizedUsername(): String? {
    return trim().removePrefix("@").substringBefore("@").takeIf { it.isNotBlank() }
}

private fun String.normalizedDomain(): String? {
    val cleanValue = trim().removePrefix("@")
    return cleanValue.substringAfter("@", missingDelimiterValue = cleanValue).takeIf { it.isNotBlank() }
}

private fun String.normalizedDatePrefix(): String? {
    return trim().takeIf { it.length >= 10 }?.take(10)
}

private fun String.toFriendlySearchError(): String {
    return when (this) {
        "Search of notes unavailable." -> "实例未启用帖子搜索"
        else -> this
    }
}

sealed interface DiscoverRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : DiscoverRepositoryResult

    data class UserSuccess(val users: List<User>) : DiscoverRepositoryResult

    data class TrendSuccess(val trends: List<TrendingHashtag>) : DiscoverRepositoryResult

    data class FederationSuccess(
        val instances: List<FederationInstance>,
        val endReached: Boolean = false,
    ) : DiscoverRepositoryResult

    data object Unauthorized : DiscoverRepositoryResult

    data class Error(val message: String) : DiscoverRepositoryResult
}
