package cc.hhhl.client.repository

import cc.hhhl.client.api.DiscoverApi
import cc.hhhl.client.api.DiscoverFederationActionResult
import cc.hhhl.client.api.DiscoverFederationInstanceResult
import cc.hhhl.client.api.DiscoverFederationResult
import cc.hhhl.client.api.DiscoverNoteSearchOptions
import cc.hhhl.client.api.DiscoverSearchResult
import cc.hhhl.client.api.DiscoverTrendResult
import cc.hhhl.client.api.DiscoverUserSearchResult
import cc.hhhl.client.api.SharkeyDiscoverApi
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOperator

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
        untilId: String? = currentNotes.lastOrNull()?.id,
    ): DiscoverRepositoryResult {
        return load(
            query = query,
            currentNotes = currentNotes,
            untilId = untilId,
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
                instances = currentInstances.appendDistinctBy(result.instances) { it.id },
                endReached = result.instances.isEmpty(),
            )
            is DiscoverFederationResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverFederationResult.ServerError -> DiscoverRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadFederationInstance(host: String): DiscoverRepositoryResult {
        val cleanHost = host.trim().takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Error("实例域名为空")

        return when (val result = api.loadFederationInstance(cleanHost)) {
            is DiscoverFederationInstanceResult.Success -> {
                DiscoverRepositoryResult.FederationInstanceSuccess(result.instance)
            }
            DiscoverFederationInstanceResult.Unavailable -> {
                DiscoverRepositoryResult.Error("未找到该实例，或当前账号无权查看实例详情")
            }
            is DiscoverFederationInstanceResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverFederationInstanceResult.ServerError -> DiscoverRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updateFederationInstance(
        host: String,
        isSilenced: Boolean,
        isSuspended: Boolean,
    ): DiscoverRepositoryResult {
        val cleanHost = host.trim().takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Error("实例域名为空")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Unauthorized

        return when (
            val result = api.updateFederationInstance(
                token = token,
                host = cleanHost,
                isSilenced = isSilenced,
                isSuspended = isSuspended,
            )
        ) {
            DiscoverFederationActionResult.Success -> DiscoverRepositoryResult.FederationActionSuccess
            DiscoverFederationActionResult.Unauthorized -> DiscoverRepositoryResult.Unauthorized
            DiscoverFederationActionResult.Unavailable -> {
                DiscoverRepositoryResult.Error("未找到该实例，或当前账号无权管理联邦实例")
            }
            is DiscoverFederationActionResult.NetworkError -> {
                DiscoverRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DiscoverFederationActionResult.ServerError -> DiscoverRepositoryResult.Error(result.message)
        }
    }

    private suspend fun load(
        query: String,
        currentNotes: List<Note>,
        untilId: String?,
        filters: DiscoverAdvancedFilters,
    ): DiscoverRepositoryResult {
        val cleanQuery = query.trim().takeIf { it.isNotBlank() }
            ?: return DiscoverRepositoryResult.Error("请输入关键词")
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
        val tagQuery = cleanQuery.toSingleHashtagQuery()
        if (token == null && tagQuery == null) {
            return DiscoverRepositoryResult.Unauthorized
        }

        val options = filters.toNoteSearchOptions()
        val result = if (tagQuery != null) {
            api.searchNotesByTag(
                token = token,
                tag = tagQuery,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                options = options,
            )
        } else {
            api.searchNotes(
                token = token.orEmpty(),
                query = cleanQuery.withDiscoverQueryOperators(filters),
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                options = options,
            )
        }

        return when (result) {
            is DiscoverSearchResult.Success -> {
                val filteredNotes = result.notes.filterNotesByDiscoverFilters(filters)
                DiscoverRepositoryResult.Success(
                    notes = currentNotes.appendDistinctBy(filteredNotes) { it.id },
                    endReached = result.notes.isEmpty(),
                    nextUntilId = result.notes.lastOrNull()?.id,
                )
            }
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
        val userIdMatches = filters.userId.trim().takeIf { it.isNotBlank() }?.let { userId ->
            author.id.equals(userId, ignoreCase = true)
        } ?: true
        val sinceMatches = filters.sinceDate.normalizedDatePrefix()?.let { sinceDate ->
            note.createdAt.take(10) >= sinceDate
        } ?: true
        val untilMatches = filters.untilDate.normalizedDatePrefix()?.let { untilDate ->
            note.createdAt.take(10) <= untilDate
        } ?: true
        val withFilesMatches = !filters.withFiles || note.media.isNotEmpty()
        val includeRepliesMatches = filters.includeReplies || note.replyId == null

        usernameMatches &&
            domainMatches &&
            userIdMatches &&
            sinceMatches &&
            untilMatches &&
            withFilesMatches &&
            includeRepliesMatches
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
        val userIdMatches = filters.userId.trim().takeIf { it.isNotBlank() }?.let { userId ->
            user.id.equals(userId, ignoreCase = true)
        } ?: true

        usernameMatches && domainMatches && userIdMatches
    }
}

private fun DiscoverAdvancedFilters.toNoteSearchOptions(): DiscoverNoteSearchOptions {
    return DiscoverNoteSearchOptions(
        origin = origin.apiValue,
        username = username.normalizedUsername().orEmpty(),
        userId = userId.trim(),
        host = domain.normalizedDomain().orEmpty(),
        channelId = channelId.trim(),
        sinceDate = sinceDate.normalizedDatePrefix().orEmpty(),
        untilDate = untilDate.normalizedDatePrefix().orEmpty(),
        withFiles = withFiles,
        includeReplies = includeReplies,
    )
}

private fun String.withDiscoverQueryOperators(filters: DiscoverAdvancedFilters): String {
    val baseQuery = when (filters.operator) {
        DiscoverSearchOperator.AllWords -> trim()
        DiscoverSearchOperator.AnyWord -> trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" OR ")
        DiscoverSearchOperator.ExactPhrase -> trim().quoteIfNeeded()
    }
    return buildList {
        add(baseQuery)
        filters.username.normalizedUsername()?.let { add("from:@$it") }
        filters.domain.normalizedDomain()?.let { add("host:$it") }
        filters.userId.trim().takeIf { it.isNotBlank() }?.let { add("user:$it") }
        filters.channelId.trim().takeIf { it.isNotBlank() }?.let { add("channel:$it") }
        filters.sinceDate.normalizedDatePrefix()?.let { add("since:$it") }
        filters.untilDate.normalizedDatePrefix()?.let { add("until:$it") }
        if (filters.withFiles) add("has:file")
        if (!filters.includeReplies) add("-is:reply")
        filters.excludeWords
            .split(Regex("\\s+"))
            .mapNotNull { word -> word.trim().trimStart('-').takeIf { it.isNotBlank() } }
            .forEach { add("-$it") }
    }.joinToString(" ")
}

private fun String.toSingleHashtagQuery(): String? {
    val cleanValue = trim()
    if (!cleanValue.startsWith("#") || cleanValue.any { it.isWhitespace() }) return null
    return cleanValue.removePrefix("#").takeIf { it.isNotBlank() }
}

private fun String.quoteIfNeeded(): String {
    val cleanValue = trim()
    return if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"")) cleanValue else "\"$cleanValue\""
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
        val nextUntilId: String? = null,
    ) : DiscoverRepositoryResult

    data class UserSuccess(val users: List<User>) : DiscoverRepositoryResult

    data class TrendSuccess(val trends: List<TrendingHashtag>) : DiscoverRepositoryResult

    data class FederationSuccess(
        val instances: List<FederationInstance>,
        val endReached: Boolean = false,
    ) : DiscoverRepositoryResult

    data class FederationInstanceSuccess(val instance: FederationInstance) : DiscoverRepositoryResult

    data object FederationActionSuccess : DiscoverRepositoryResult

    data object Unauthorized : DiscoverRepositoryResult

    data class Error(val message: String) : DiscoverRepositoryResult
}
