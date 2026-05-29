package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.FederationFollow
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.FederationStats
import cc.hhhl.client.model.RoleSummary
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface DiscoverApi {
    suspend fun searchNotes(
        token: String,
        query: String,
        limit: Int,
        untilId: String? = null,
        options: DiscoverNoteSearchOptions = DiscoverNoteSearchOptions(),
    ): DiscoverSearchResult

    suspend fun searchNotesByTag(
        token: String?,
        tag: String,
        limit: Int,
        untilId: String? = null,
        options: DiscoverNoteSearchOptions = DiscoverNoteSearchOptions(),
    ): DiscoverSearchResult

    suspend fun searchUsers(
        token: String,
        query: String,
        limit: Int,
        origin: String = "combined",
    ): DiscoverUserSearchResult

    suspend fun loadPinnedUsers(): DiscoverUserSearchResult =
        DiscoverUserSearchResult.ServerError(501, "发现页置顶用户接口未实现")

    suspend fun loadRoles(token: String): DiscoverRoleResult =
        DiscoverRoleResult.ServerError(501, "角色列表接口未实现")

    suspend fun loadRole(token: String, roleId: String): DiscoverRoleDetailResult =
        DiscoverRoleDetailResult.ServerError(501, "角色详情接口未实现")

    suspend fun loadRoleUsers(token: String, roleId: String, limit: Int = 20): DiscoverUserSearchResult =
        DiscoverUserSearchResult.ServerError(501, "角色用户接口未实现")

    suspend fun loadRoleNotes(token: String, roleId: String, limit: Int = 20, untilId: String? = null): DiscoverSearchResult =
        DiscoverSearchResult.ServerError(501, "角色帖子接口未实现")

    suspend fun loadTrendingHashtags(): DiscoverTrendResult

    suspend fun searchHashtags(
        token: String,
        query: String,
        limit: Int,
        offset: Int = 0,
    ): DiscoverTrendResult =
        DiscoverTrendResult.ServerError(501, "话题搜索接口未实现")

    suspend fun loadHashtags(
        token: String,
        limit: Int,
        offset: Int = 0,
        sort: String = "+attachedUsers",
    ): DiscoverTrendResult =
        DiscoverTrendResult.ServerError(501, "话题列表接口未实现")

    suspend fun showHashtag(
        token: String,
        tag: String,
    ): DiscoverHashtagResult =
        DiscoverHashtagResult.ServerError(501, "话题详情接口未实现")

    suspend fun loadHashtagUsers(
        token: String,
        tag: String,
        limit: Int,
        sort: String = "+follower",
        state: String = "all",
        origin: String = "combined",
    ): DiscoverUserSearchResult =
        DiscoverUserSearchResult.ServerError(501, "话题用户接口未实现")

    suspend fun loadFederationInstances(
        limit: Int,
        offset: Int = 0,
        host: String? = null,
    ): DiscoverFederationResult

    suspend fun loadFederationInstance(host: String): DiscoverFederationInstanceResult

    suspend fun loadFederationFollowers(
        host: String,
        limit: Int,
        untilId: String? = null,
        includeFollower: Boolean = false,
        includeFollowee: Boolean = true,
    ): DiscoverFederationFollowResult =
        DiscoverFederationFollowResult.Unavailable

    suspend fun loadFederationFollowing(
        host: String,
        limit: Int,
        untilId: String? = null,
        includeFollower: Boolean = false,
        includeFollowee: Boolean = true,
    ): DiscoverFederationFollowResult =
        DiscoverFederationFollowResult.Unavailable

    suspend fun loadFederationUsers(
        host: String,
        limit: Int,
        untilId: String? = null,
    ): DiscoverUserSearchResult =
        DiscoverUserSearchResult.ServerError(501, "联邦用户接口未实现")

    suspend fun loadFederationStats(limit: Int = 10): DiscoverFederationStatsResult =
        DiscoverFederationStatsResult.ServerError(501, "联邦统计接口未实现")

    suspend fun updateFederationInstance(
        token: String,
        host: String,
        isSilenced: Boolean,
        isSuspended: Boolean,
    ): DiscoverFederationActionResult

    suspend fun updateRemoteUser(
        token: String,
        userId: String,
    ): DiscoverFederationActionResult =
        DiscoverFederationActionResult.ServerError(501, "远端用户刷新接口未实现")
}

sealed interface DiscoverSearchResult {
    data class Success(val notes: List<Note>) : DiscoverSearchResult

    data object Unauthorized : DiscoverSearchResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverSearchResult

    data class NetworkError(val message: String) : DiscoverSearchResult
}

sealed interface DiscoverUserSearchResult {
    data class Success(val users: List<User>) : DiscoverUserSearchResult

    data object Unauthorized : DiscoverUserSearchResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverUserSearchResult

    data class NetworkError(val message: String) : DiscoverUserSearchResult
}

sealed interface DiscoverRoleResult {
    data class Success(val roles: List<RoleSummary>) : DiscoverRoleResult
    data object Unauthorized : DiscoverRoleResult
    data class ServerError(val statusCode: Int, val message: String) : DiscoverRoleResult
    data class NetworkError(val message: String) : DiscoverRoleResult
}

sealed interface DiscoverRoleDetailResult {
    data class Success(val role: RoleSummary) : DiscoverRoleDetailResult
    data object Unauthorized : DiscoverRoleDetailResult
    data class ServerError(val statusCode: Int, val message: String) : DiscoverRoleDetailResult
    data class NetworkError(val message: String) : DiscoverRoleDetailResult
}

sealed interface DiscoverTrendResult {
    data class Success(val trends: List<TrendingHashtag>) : DiscoverTrendResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverTrendResult

    data class NetworkError(val message: String) : DiscoverTrendResult
}

sealed interface DiscoverHashtagResult {
    data class Success(val hashtag: TrendingHashtag) : DiscoverHashtagResult

    data object Unauthorized : DiscoverHashtagResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverHashtagResult
    data class NetworkError(val message: String) : DiscoverHashtagResult
}

sealed interface DiscoverFederationResult {
    data class Success(val instances: List<FederationInstance>) : DiscoverFederationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationResult

    data class NetworkError(val message: String) : DiscoverFederationResult
}

sealed interface DiscoverFederationInstanceResult {
    data class Success(val instance: FederationInstance) : DiscoverFederationInstanceResult

    data object Unavailable : DiscoverFederationInstanceResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationInstanceResult

    data class NetworkError(val message: String) : DiscoverFederationInstanceResult
}

sealed interface DiscoverFederationFollowResult {
    data class Success(val follows: List<FederationFollow>) : DiscoverFederationFollowResult

    data object Unavailable : DiscoverFederationFollowResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationFollowResult

    data class NetworkError(val message: String) : DiscoverFederationFollowResult
}

sealed interface DiscoverFederationStatsResult {
    data class Success(val stats: FederationStats) : DiscoverFederationStatsResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationStatsResult

    data class NetworkError(val message: String) : DiscoverFederationStatsResult
}

sealed interface DiscoverFederationActionResult {
    data object Success : DiscoverFederationActionResult

    data object Unauthorized : DiscoverFederationActionResult

    data object Unavailable : DiscoverFederationActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationActionResult

    data class NetworkError(val message: String) : DiscoverFederationActionResult
}

class SharkeyDiscoverApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultDiscoverClient(),
) : DiscoverApi {
    override suspend fun searchNotes(
        token: String,
        query: String,
        limit: Int,
        untilId: String?,
        options: DiscoverNoteSearchOptions,
    ): DiscoverSearchResult {
        val cleanToken = token.trim()
        val cleanQuery = query.trim()
        if (cleanToken.isEmpty()) return DiscoverSearchResult.Unauthorized
        if (cleanQuery.isEmpty()) {
            return DiscoverSearchResult.ServerError(400, "请输入关键词")
        }

        return try {
            val response = client.post(apiUrl("notes", "search")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DiscoverSearchRequest(
                        i = cleanToken,
                        query = cleanQuery,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        origin = options.origin.cleanRequestValue(),
                        userId = options.userId.cleanRequestValue(),
                        username = options.username.cleanRequestValue(),
                        host = options.host.cleanRequestValue(),
                        channelId = options.channelId.cleanRequestValue(),
                        sinceDate = options.sinceDate.cleanRequestValue(),
                        untilDate = options.untilDate.cleanRequestValue(),
                        withFiles = options.withFiles.takeIf { it },
                        includeReplies = options.includeReplies.takeIf { !it },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverSearchResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> DiscoverSearchResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> DiscoverSearchResult.Unauthorized
                else -> DiscoverSearchResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun searchNotesByTag(
        token: String?,
        tag: String,
        limit: Int,
        untilId: String?,
        options: DiscoverNoteSearchOptions,
    ): DiscoverSearchResult {
        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
        val cleanTag = tag.trim().removePrefix("#")
        if (cleanTag.isEmpty()) {
            return DiscoverSearchResult.ServerError(400, "请输入话题")
        }

        return try {
            val response = client.post(apiUrl("notes", "search-by-tag")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DiscoverTagSearchRequest(
                        i = cleanToken,
                        tag = cleanTag,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        withFiles = options.withFiles.takeIf { it },
                        reply = false.takeIf { !options.includeReplies },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverSearchResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> DiscoverSearchResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> DiscoverSearchResult.Unauthorized
                else -> DiscoverSearchResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun searchUsers(
        token: String,
        query: String,
        limit: Int,
        origin: String,
    ): DiscoverUserSearchResult {
        val cleanToken = token.trim()
        val cleanQuery = query.trim()
        if (cleanToken.isEmpty()) return DiscoverUserSearchResult.Unauthorized
        if (cleanQuery.isEmpty()) {
            return DiscoverUserSearchResult.ServerError(400, "请输入关键词")
        }

        return try {
            val response = client.post(apiUrl("users", "search")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DiscoverUserSearchRequest(
                        i = cleanToken,
                        query = cleanQuery,
                        limit = limit.coerceIn(1, 100),
                        origin = origin.toDiscoverOriginValue(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverUserSearchResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> DiscoverUserSearchResult.Success(
                    response.body<List<DiscoverUserDto>>().map { it.toDomainUser() },
                )
                HttpStatusCode.Unauthorized -> DiscoverUserSearchResult.Unauthorized
                else -> DiscoverUserSearchResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverUserSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadPinnedUsers(): DiscoverUserSearchResult {
        return try {
            val response = client.post(apiUrl("pinned-users")) {
                contentType(ContentType.Application.Json)
                setBody(DiscoverEmptyRequest())
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverUserSearchResult.Success(
                    response.body<List<DiscoverUserDto>>().map { it.toDomainUser() },
                )
                else -> DiscoverUserSearchResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverUserSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRoles(token: String): DiscoverRoleResult {
        return try {
            val response = client.post(apiUrl("roles", "list")) {
            }
            if (response.isSharkeyUnauthorized()) return DiscoverRoleResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverRoleResult.Success(
                    response.body<JsonElement>().asJsonArrayElements().map { it.toRoleSummary() },
                )
                HttpStatusCode.Unauthorized -> DiscoverRoleResult.Unauthorized
                else -> DiscoverRoleResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverRoleResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRole(token: String, roleId: String): DiscoverRoleDetailResult {
        val cleanToken = token.trim()
        val cleanRoleId = roleId.trim()
        if (cleanToken.isEmpty()) return DiscoverRoleDetailResult.Unauthorized
        if (cleanRoleId.isEmpty()) return DiscoverRoleDetailResult.ServerError(400, "角色 ID 不能为空")
        return try {
            val response = client.post(apiUrl("roles", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("roleId", JsonPrimitive(cleanRoleId))
                    },
                )
            }
            if (response.isSharkeyUnauthorized()) return DiscoverRoleDetailResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverRoleDetailResult.Success(response.body<JsonElement>().toRoleSummary())
                HttpStatusCode.Unauthorized -> DiscoverRoleDetailResult.Unauthorized
                else -> DiscoverRoleDetailResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverRoleDetailResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRoleUsers(token: String, roleId: String, limit: Int): DiscoverUserSearchResult {
        val cleanToken = token.trim()
        val cleanRoleId = roleId.trim()
        if (cleanToken.isEmpty()) return DiscoverUserSearchResult.Unauthorized
        if (cleanRoleId.isEmpty()) return DiscoverUserSearchResult.ServerError(400, "角色 ID 不能为空")
        return try {
            val response = client.post(apiUrl("roles", "users")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("roleId", JsonPrimitive(cleanRoleId))
                        put("limit", JsonPrimitive(limit.coerceIn(1, 100)))
                    },
                )
            }
            if (response.isSharkeyUnauthorized()) return DiscoverUserSearchResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverUserSearchResult.Success(
                    response.body<List<DiscoverUserDto>>().map { it.toDomainUser() },
                )
                HttpStatusCode.Unauthorized -> DiscoverUserSearchResult.Unauthorized
                else -> DiscoverUserSearchResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverUserSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadRoleNotes(token: String, roleId: String, limit: Int, untilId: String?): DiscoverSearchResult {
        val cleanToken = token.trim()
        val cleanRoleId = roleId.trim()
        if (cleanToken.isEmpty()) return DiscoverSearchResult.Unauthorized
        if (cleanRoleId.isEmpty()) return DiscoverSearchResult.ServerError(400, "角色 ID 不能为空")
        return try {
            val response = client.post(apiUrl("roles", "notes")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("roleId", JsonPrimitive(cleanRoleId))
                        put("limit", JsonPrimitive(limit.coerceIn(1, 100)))
                        untilId?.takeIf { it.isNotBlank() }?.let { put("untilId", JsonPrimitive(it)) }
                    },
                )
            }
            if (response.isSharkeyUnauthorized()) return DiscoverSearchResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverSearchResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> DiscoverSearchResult.Unauthorized
                else -> DiscoverSearchResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadTrendingHashtags(): DiscoverTrendResult {
        return try {
            val response = client.post(apiUrl("hashtags", "trend")) {
                contentType(ContentType.Application.Json)
                setBody(DiscoverEmptyRequest())
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverTrendResult.Success(
                    response.body<List<TrendingHashtagDto>>().map { it.toDomainTrend() },
                )
                else -> DiscoverTrendResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverTrendResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun searchHashtags(
        token: String,
        query: String,
        limit: Int,
        offset: Int,
    ): DiscoverTrendResult {
        val cleanQuery = query.trim().removePrefix("#")
        if (cleanQuery.isEmpty()) return DiscoverTrendResult.ServerError(400, "请输入话题")

        return try {
            val response = client.post(apiUrl("hashtags", "search")) {
                contentType(ContentType.Application.Json)
                setBody(
                    HashtagSearchRequest(
                        query = cleanQuery,
                        limit = limit.coerceIn(1, 100),
                        offset = offset.coerceAtLeast(0),
                    ),
                )
            }
            when (response.status) {
                HttpStatusCode.OK -> DiscoverTrendResult.Success(
                    response.body<List<String>>().map { it.toDomainTrend() },
                )
                else -> DiscoverTrendResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverTrendResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadHashtags(
        token: String,
        limit: Int,
        offset: Int,
        sort: String,
    ): DiscoverTrendResult {
        return try {
            val response = client.post(apiUrl("hashtags", "list")) {
                contentType(ContentType.Application.Json)
                setBody(
                    HashtagListRequest(
                        limit = limit.coerceIn(1, 100),
                        sort = sort.trim().ifBlank { "+attachedUsers" },
                    ),
                )
            }
            when (response.status) {
                HttpStatusCode.OK -> DiscoverTrendResult.Success(
                    response.body<List<TrendingHashtagDto>>().map { it.toDomainTrend() },
                )
                else -> DiscoverTrendResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverTrendResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showHashtag(
        token: String,
        tag: String,
    ): DiscoverHashtagResult {
        val cleanTag = tag.trim().removePrefix("#")
        if (cleanTag.isEmpty()) return DiscoverHashtagResult.ServerError(400, "请输入话题")

        return try {
            val response = client.post(apiUrl("hashtags", "show")) {
                contentType(ContentType.Application.Json)
                setBody(HashtagShowRequest(tag = cleanTag))
            }
            if (response.isSharkeyUnauthorized()) return DiscoverHashtagResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverHashtagResult.Success(response.body<TrendingHashtagDto>().toDomainTrend())
                HttpStatusCode.Unauthorized -> DiscoverHashtagResult.Unauthorized
                else -> DiscoverHashtagResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverHashtagResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadHashtagUsers(
        token: String,
        tag: String,
        limit: Int,
        sort: String,
        state: String,
        origin: String,
    ): DiscoverUserSearchResult {
        val cleanTag = tag.trim().removePrefix("#")
        if (cleanTag.isEmpty()) return DiscoverUserSearchResult.ServerError(400, "请输入话题")

        return try {
            val response = client.post(apiUrl("hashtags", "users")) {
                contentType(ContentType.Application.Json)
                setBody(
                    HashtagUsersRequest(
                        tag = cleanTag,
                        limit = limit.coerceIn(1, 100),
                        sort = sort,
                        state = state,
                        origin = origin,
                    ),
                )
            }
            if (response.isSharkeyUnauthorized()) return DiscoverUserSearchResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> DiscoverUserSearchResult.Success(
                    response.body<List<DiscoverUserDto>>().map { it.toDomainUser() },
                )
                HttpStatusCode.Unauthorized -> DiscoverUserSearchResult.Unauthorized
                else -> DiscoverUserSearchResult.ServerError(response.status.value, response.apiErrorMessage() ?: "服务器返回 ${response.status.value}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverUserSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFederationInstances(
        limit: Int,
        offset: Int,
        host: String?,
    ): DiscoverFederationResult {
        return try {
            val response = client.post(apiUrl("federation", "instances")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FederationInstancesRequest(
                        limit = limit.coerceIn(1, 100),
                        offset = offset.coerceAtLeast(0),
                        sort = "-users",
                        host = host?.trim()?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverFederationResult.Success(
                    response.body<List<FederationInstanceDto>>().map { it.toDomainInstance() },
                )
                else -> DiscoverFederationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFederationInstance(host: String): DiscoverFederationInstanceResult {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) {
            return DiscoverFederationInstanceResult.ServerError(400, "实例域名为空")
        }

        return try {
            val response = client.post(apiUrl("federation", "show-instance")) {
                contentType(ContentType.Application.Json)
                setBody(FederationInstanceShowRequest(host = cleanHost))
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverFederationInstanceResult.Success(
                    response.body<FederationInstanceDto>().toDomainInstance(),
                )
                HttpStatusCode.NotFound -> DiscoverFederationInstanceResult.Unavailable
                else -> DiscoverFederationInstanceResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationInstanceResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFederationFollowers(
        host: String,
        limit: Int,
        untilId: String?,
        includeFollower: Boolean,
        includeFollowee: Boolean,
    ): DiscoverFederationFollowResult {
        return loadFederationFollows(
            endpoint = "followers",
            host = host,
            limit = limit,
            untilId = untilId,
            includeFollower = includeFollower,
            includeFollowee = includeFollowee,
        )
    }

    override suspend fun loadFederationFollowing(
        host: String,
        limit: Int,
        untilId: String?,
        includeFollower: Boolean,
        includeFollowee: Boolean,
    ): DiscoverFederationFollowResult {
        return loadFederationFollows(
            endpoint = "following",
            host = host,
            limit = limit,
            untilId = untilId,
            includeFollower = includeFollower,
            includeFollowee = includeFollowee,
        )
    }

    private suspend fun loadFederationFollows(
        endpoint: String,
        host: String,
        limit: Int,
        untilId: String?,
        includeFollower: Boolean,
        includeFollowee: Boolean,
    ): DiscoverFederationFollowResult {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) {
            return DiscoverFederationFollowResult.ServerError(400, "实例域名为空")
        }

        return try {
            val response = client.post(apiUrl("federation", endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    FederationFollowsRequest(
                        host = cleanHost,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        includeFollower = includeFollower,
                        includeFollowee = includeFollowee,
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverFederationFollowResult.Success(
                    response.body<List<FederationFollowDto>>().map { it.toDomainFollow() },
                )
                HttpStatusCode.NotFound -> DiscoverFederationFollowResult.Unavailable
                else -> DiscoverFederationFollowResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationFollowResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFederationUsers(
        host: String,
        limit: Int,
        untilId: String?,
    ): DiscoverUserSearchResult {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return DiscoverUserSearchResult.ServerError(400, "实例域名为空")

        return try {
            val response = client.post(apiUrl("federation", "users")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FederationUsersRequest(
                        host = cleanHost,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverUserSearchResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> DiscoverUserSearchResult.Success(
                    response.body<List<DiscoverUserDto>>().map { it.toDomainUser() },
                )
                HttpStatusCode.Unauthorized -> DiscoverUserSearchResult.Unauthorized
                else -> DiscoverUserSearchResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverUserSearchResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFederationStats(limit: Int): DiscoverFederationStatsResult {
        return try {
            val response = client.post(apiUrl("federation", "stats")) {
                contentType(ContentType.Application.Json)
                setBody(FederationStatsRequest(limit = limit.coerceIn(1, 100)))
            }

            when (response.status) {
                HttpStatusCode.OK -> DiscoverFederationStatsResult.Success(
                    response.body<FederationStatsDto>().toDomainStats(),
                )
                else -> DiscoverFederationStatsResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationStatsResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateFederationInstance(
        token: String,
        host: String,
        isSilenced: Boolean,
        isSuspended: Boolean,
    ): DiscoverFederationActionResult {
        val cleanToken = token.trim()
        val cleanHost = host.trim()
        if (cleanToken.isEmpty()) return DiscoverFederationActionResult.Unauthorized
        if (cleanHost.isEmpty()) {
            return DiscoverFederationActionResult.ServerError(400, "实例域名为空")
        }

        return try {
            val response = client.post(apiUrl("admin", "federation", "update-instance")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FederationInstanceUpdateRequest(
                        i = cleanToken,
                        host = cleanHost,
                        isSilenced = isSilenced,
                        isSuspended = isSuspended,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverFederationActionResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> DiscoverFederationActionResult.Success
                HttpStatusCode.Unauthorized -> DiscoverFederationActionResult.Unauthorized
                HttpStatusCode.Forbidden -> DiscoverFederationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                HttpStatusCode.NotFound -> DiscoverFederationActionResult.Unavailable
                else -> DiscoverFederationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateRemoteUser(
        token: String,
        userId: String,
    ): DiscoverFederationActionResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return DiscoverFederationActionResult.Unauthorized
        if (cleanUserId.isEmpty()) return DiscoverFederationActionResult.ServerError(400, "用户 ID 不能为空")

        return try {
            val response = client.post(apiUrl("federation", "update-remote-user")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("userId", JsonPrimitive(cleanUserId))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return DiscoverFederationActionResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> DiscoverFederationActionResult.Success
                HttpStatusCode.Unauthorized -> DiscoverFederationActionResult.Unauthorized
                HttpStatusCode.NotFound -> DiscoverFederationActionResult.Unavailable
                else -> DiscoverFederationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DiscoverFederationActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

data class DiscoverNoteSearchOptions(
    val origin: String = "",
    val username: String = "",
    val userId: String = "",
    val host: String = "",
    val channelId: String = "",
    val sinceDate: String = "",
    val untilDate: String = "",
    val withFiles: Boolean = false,
    val includeReplies: Boolean = true,
)

@Serializable
private data class DiscoverSearchRequest(
    val i: String,
    val query: String,
    val limit: Int,
    val untilId: String? = null,
    val origin: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val host: String? = null,
    val channelId: String? = null,
    val sinceDate: String? = null,
    val untilDate: String? = null,
    val withFiles: Boolean? = null,
    val includeReplies: Boolean? = null,
)

@Serializable
private data class DiscoverTagSearchRequest(
    val i: String? = null,
    val tag: String,
    val limit: Int,
    val untilId: String? = null,
    val withFiles: Boolean? = null,
    val reply: Boolean? = null,
)

@Serializable
private data class DiscoverUserSearchRequest(
    val i: String,
    val query: String,
    val limit: Int,
    val origin: String,
)

@Serializable
private data class DiscoverUserDto(
    val id: String,
    val username: String,
    val host: String? = null,
    val name: String? = null,
    val description: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val avatarUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            bio = description.orEmpty(),
            host = host?.takeIf { it.isNotBlank() },
            followersCount = followersCount,
            followingCount = followingCount,
            notesCount = notesCount,
            isFollowing = isFollowing,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
private class DiscoverEmptyRequest

@Serializable
private data class HashtagSearchRequest(
    val query: String,
    val limit: Int,
    val offset: Int,
)

@Serializable
private data class HashtagListRequest(
    val limit: Int,
    val sort: String,
)

@Serializable
private data class HashtagShowRequest(
    val tag: String,
)

@Serializable
private data class HashtagUsersRequest(
    val tag: String,
    val limit: Int,
    val sort: String,
    val state: String,
    val origin: String,
)

@Serializable
private data class TrendingHashtagDto(
    val tag: String,
    val chart: List<Int> = emptyList(),
    val usersCount: Int = 0,
    val mentionedUsersCount: Int = 0,
    val mentionedLocalUsersCount: Int = 0,
    val mentionedRemoteUsersCount: Int = 0,
) {
    fun toDomainTrend(): TrendingHashtag {
        return TrendingHashtag(
            tag = tag,
            chart = chart,
            usersCount = usersCount,
            mentionedUsersCount = mentionedUsersCount,
            mentionedLocalUsersCount = mentionedLocalUsersCount,
            mentionedRemoteUsersCount = mentionedRemoteUsersCount,
        )
    }
}

private fun String.toDomainTrend(): TrendingHashtag {
    return TrendingHashtag(
        tag = trim().removePrefix("#"),
        chart = emptyList(),
        usersCount = 0,
    )
}

@Serializable
private data class FederationInstancesRequest(
    val limit: Int,
    val offset: Int,
    val sort: String,
    val host: String? = null,
)

@Serializable
private data class FederationInstanceShowRequest(
    val host: String,
)

@Serializable
private data class FederationFollowsRequest(
    val host: String,
    val limit: Int,
    val untilId: String? = null,
    val includeFollower: Boolean,
    val includeFollowee: Boolean,
)

@Serializable
private data class FederationUsersRequest(
    val host: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class FederationStatsRequest(
    val limit: Int,
)

@Serializable
private data class FederationInstanceUpdateRequest(
    val i: String,
    val host: String,
    val isSilenced: Boolean,
    val isSuspended: Boolean,
)

@Serializable
private data class FederationInstanceDto(
    val id: String = "",
    val host: String,
    val usersCount: Int = 0,
    val notesCount: Int = 0,
    val followingCount: Int = 0,
    val followersCount: Int = 0,
    val isNotResponding: Boolean = false,
    val isSuspended: Boolean = false,
    val isBlocked: Boolean = false,
    val softwareName: String? = null,
    val softwareVersion: String? = null,
    val name: String? = null,
    val description: String? = null,
    val isSilenced: Boolean = false,
    val maintainerName: String? = null,
    val maintainerEmail: String? = null,
    val iconUrl: String? = null,
    val faviconUrl: String? = null,
    val latestRequestReceivedAt: String? = null,
    val infoUpdatedAt: String? = null,
) {
    fun toDomainInstance(): FederationInstance {
        return FederationInstance(
            id = id.ifBlank { host },
            host = host,
            name = name,
            description = description,
            softwareName = softwareName,
            softwareVersion = softwareVersion,
            usersCount = usersCount,
            notesCount = notesCount,
            followingCount = followingCount,
            followersCount = followersCount,
            isNotResponding = isNotResponding,
            isSuspended = isSuspended,
            isBlocked = isBlocked,
            isSilenced = isSilenced,
            maintainerName = maintainerName,
            maintainerEmail = maintainerEmail,
            iconUrl = iconUrl,
            faviconUrl = faviconUrl,
            latestRequestReceivedAtLabel = latestRequestReceivedAt
                ?.takeIf { it.isNotBlank() }
                ?.toLocalCompactDateLabel()
                .orEmpty(),
            infoUpdatedAtLabel = infoUpdatedAt
                ?.takeIf { it.isNotBlank() }
                ?.toLocalCompactDateLabel()
                .orEmpty(),
        )
    }
}

@Serializable
private data class FederationFollowDto(
    val id: String,
    val createdAt: String = "",
    val followeeId: String,
    val followerId: String,
    val followee: DiscoverUserDto? = null,
    val follower: DiscoverUserDto? = null,
) {
    fun toDomainFollow(): FederationFollow {
        return FederationFollow(
            id = id,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            followeeId = followeeId,
            followerId = followerId,
            followee = followee?.toDomainUser(),
            follower = follower?.toDomainUser(),
        )
    }
}

@Serializable
private data class FederationStatsDto(
    val topSubInstances: List<FederationInstanceDto> = emptyList(),
    val otherFollowersCount: Double = 0.0,
    val topPubInstances: List<FederationInstanceDto> = emptyList(),
    val otherFollowingCount: Double = 0.0,
) {
    fun toDomainStats(): FederationStats {
        return FederationStats(
            topSubInstances = topSubInstances.map { it.toDomainInstance() },
            otherFollowersCount = otherFollowersCount.toInt(),
            topPubInstances = topPubInstances.map { it.toDomainInstance() },
            otherFollowingCount = otherFollowingCount.toInt(),
        )
    }
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun String.toDiscoverOriginValue(): String {
    return when (trim().lowercase()) {
        "local" -> "local"
        "remote" -> "remote"
        else -> "combined"
    }
}

private fun String.cleanRequestValue(): String? {
    return trim().takeIf { it.isNotBlank() }
}

private fun JsonElement.asJsonArrayElements(): List<JsonElement> {
    return when (this) {
        is JsonArray -> toList()
        is JsonObject -> this["roles"]?.jsonArray?.toList()
            ?: this["items"]?.jsonArray?.toList()
            ?: emptyList()
        else -> emptyList()
    }
}

private fun JsonElement.toRoleSummary(): RoleSummary {
    val obj = this as? JsonObject ?: return RoleSummary(id = "", name = "Role")
    val permissions = obj["permissions"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    val target = obj["target"]?.jsonPrimitive?.contentOrNull
    return RoleSummary(
        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        name = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { "Role" } ?: "Role",
        description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        usersCount = obj["usersCount"]?.jsonPrimitive?.intOrNull
            ?: obj["membersCount"]?.jsonPrimitive?.intOrNull
            ?: 0,
        isPublic = obj["isPublic"]?.jsonPrimitive?.booleanOrNull
            ?: obj["public"]?.jsonPrimitive?.booleanOrNull
            ?: true,
        isModerator = obj["isModerator"]?.jsonPrimitive?.booleanOrNull == true ||
            permissions.any { it.contains("moderator", ignoreCase = true) },
        isAdministrator = obj["isAdministrator"]?.jsonPrimitive?.booleanOrNull == true ||
            permissions.any { it.contains("admin", ignoreCase = true) } ||
            (target.equals("manual", ignoreCase = true) && obj["asBadge"]?.jsonPrimitive?.booleanOrNull == true),
        color = obj["color"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class DiscoverErrorEnvelope(
    val error: DiscoverErrorDto? = null,
)

@Serializable
private data class DiscoverErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultDiscoverClient(): HttpClient {
    return HttpClient {
        installDefaultHttpTimeouts()
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
