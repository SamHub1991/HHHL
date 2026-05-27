package cc.hhhl.client.api

import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserRelationshipListEntry
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import kotlinx.serialization.json.decodeFromJsonElement

interface UserRelationshipApi {
    suspend fun loadRelation(
        token: String,
        userId: String,
    ): UserRelationshipLoadResult

    suspend fun loadMutedUsers(
        token: String,
        limit: Int = 30,
        untilId: String? = null,
    ): UserRelationshipListResult

    suspend fun loadBlockedUsers(
        token: String,
        limit: Int = 30,
        untilId: String? = null,
    ): UserRelationshipListResult

    suspend fun follow(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun unfollow(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun mute(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun unmute(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun block(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun unblock(
        token: String,
        userId: String,
    ): UserRelationshipResult

    suspend fun reportUser(
        token: String,
        userId: String,
        comment: String,
    ): UserRelationshipResult
}

sealed interface UserRelationshipLoadResult {
    data class Success(val relationship: UserRelationship) : UserRelationshipLoadResult

    data object Unauthorized : UserRelationshipLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserRelationshipLoadResult

    data class NetworkError(val message: String) : UserRelationshipLoadResult
}

sealed interface UserRelationshipResult {
    data object Success : UserRelationshipResult

    data object Unauthorized : UserRelationshipResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserRelationshipResult

    data class NetworkError(val message: String) : UserRelationshipResult
}

sealed interface UserRelationshipListResult {
    data class Success(val entries: List<UserRelationshipListEntry>) : UserRelationshipListResult

    data object Unauthorized : UserRelationshipListResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserRelationshipListResult

    data class NetworkError(val message: String) : UserRelationshipListResult
}

class SharkeyUserRelationshipApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultUserRelationshipClient(),
) : UserRelationshipApi {
    override suspend fun loadRelation(
        token: String,
        userId: String,
    ): UserRelationshipLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return UserRelationshipLoadResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return UserRelationshipLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return try {
            val response = client.post(apiUrl("users", "relation")) {
                contentType(ContentType.Application.Json)
                setBody(UserRelationshipRequest(i = cleanToken, userId = cleanUserId))
            }

            when {
                response.status.value in 200..299 -> UserRelationshipLoadResult.Success(
                    response.body<JsonElement>().toUserRelationship(cleanUserId),
                )
                response.isSharkeyUnauthorized() -> UserRelationshipLoadResult.Unauthorized
                else -> UserRelationshipLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserRelationshipLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun follow(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("following", "create"),
            token = token,
            userId = userId,
        )
    }

    override suspend fun loadMutedUsers(
        token: String,
        limit: Int,
        untilId: String?,
    ): UserRelationshipListResult {
        return loadRelationshipList(
            endpoint = arrayOf("mute", "list"),
            token = token,
            limit = limit,
            untilId = untilId,
        ) { it.toMutedEntry() }
    }

    override suspend fun loadBlockedUsers(
        token: String,
        limit: Int,
        untilId: String?,
    ): UserRelationshipListResult {
        return loadRelationshipList(
            endpoint = arrayOf("blocking", "list"),
            token = token,
            limit = limit,
            untilId = untilId,
        ) { it.toBlockedEntry() }
    }

    override suspend fun mute(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("mute", "create"),
            token = token,
            userId = userId,
        )
    }

    override suspend fun unmute(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("mute", "delete"),
            token = token,
            userId = userId,
        )
    }

    override suspend fun block(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("blocking", "create"),
            token = token,
            userId = userId,
        )
    }

    override suspend fun unblock(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("blocking", "delete"),
            token = token,
            userId = userId,
        )
    }

    override suspend fun reportUser(
        token: String,
        userId: String,
        comment: String,
    ): UserRelationshipResult {
        val cleanComment = comment.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_REPORT_COMMENT
        return postAction(
            endpoint = arrayOf("users", "report-abuse"),
            token = token,
            userId = userId,
            body = UserReportRequest(i = token.trim(), userId = userId.trim(), comment = cleanComment),
        )
    }

    override suspend fun unfollow(
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postRelationship(
            endpoint = arrayOf("following", "delete"),
            token = token,
            userId = userId,
        )
    }

    private suspend fun postRelationship(
        endpoint: Array<String>,
        token: String,
        userId: String,
    ): UserRelationshipResult {
        return postAction(
            endpoint = endpoint,
            token = token,
            userId = userId,
            body = UserRelationshipRequest(i = token.trim(), userId = userId.trim()),
        )
    }

    private suspend inline fun <reified T : Any> postAction(
        endpoint: Array<String>,
        token: String,
        userId: String,
        body: T,
    ): UserRelationshipResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return UserRelationshipResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return UserRelationshipResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when {
                response.status.value in 200..299 -> UserRelationshipResult.Success
                response.isSharkeyUnauthorized() -> UserRelationshipResult.Unauthorized
                else -> UserRelationshipResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserRelationshipResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun loadRelationshipList(
        endpoint: Array<String>,
        token: String,
        limit: Int,
        untilId: String?,
        mapper: (UserRelationshipListDto) -> UserRelationshipListEntry?,
    ): UserRelationshipListResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return UserRelationshipListResult.Unauthorized

        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserRelationshipListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when {
                response.status.value in 200..299 -> UserRelationshipListResult.Success(
                    response.body<List<UserRelationshipListDto>>().mapNotNull(mapper),
                )
                response.isSharkeyUnauthorized() -> UserRelationshipListResult.Unauthorized
                else -> UserRelationshipListResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserRelationshipListResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
        const val DEFAULT_REPORT_COMMENT = "客户端举报"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

@Serializable
private data class UserRelationshipRequest(
    val i: String,
    val userId: String,
)

@Serializable
private data class UserReportRequest(
    val i: String,
    val userId: String,
    val comment: String,
)

@Serializable
private data class UserRelationshipListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class UserRelationshipDto(
    val id: String? = null,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val hasPendingFollowRequestFromYou: Boolean = false,
    val hasPendingFollowRequestToYou: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocking: Boolean = false,
    val isBlocked: Boolean = false,
) {
    fun toDomainRelationship(fallbackUserId: String): UserRelationship {
        return UserRelationship(
            userId = id?.takeIf { it.isNotBlank() } ?: fallbackUserId,
            isFollowing = isFollowing,
            isFollowed = isFollowed,
            hasPendingFollowRequestFromYou = hasPendingFollowRequestFromYou,
            hasPendingFollowRequestToYou = hasPendingFollowRequestToYou,
            isMuted = isMuted,
            isBlocking = isBlocking,
            isBlocked = isBlocked,
        )
    }
}

private fun JsonElement.toUserRelationship(fallbackUserId: String): UserRelationship {
    val relationshipElement = when (this) {
        is JsonArray -> firstOrNull()
        is JsonObject -> this
        else -> null
    } ?: return UserRelationship(userId = fallbackUserId)
    return userRelationshipJson.decodeFromJsonElement<UserRelationshipDto>(relationshipElement)
        .toDomainRelationship(fallbackUserId)
}

private val userRelationshipJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
private data class UserRelationshipListDto(
    val id: String,
    val createdAt: String = "",
    val mutee: RelationshipUserDto? = null,
    val blockee: RelationshipUserDto? = null,
) {
    fun toMutedEntry(): UserRelationshipListEntry? = mutee?.toEntry(id, createdAt)

    fun toBlockedEntry(): UserRelationshipListEntry? = blockee?.toEntry(id, createdAt)
}

@Serializable
private data class RelationshipUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
) {
    fun toEntry(entryId: String, createdAt: String): UserRelationshipListEntry {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return UserRelationshipListEntry(
            id = entryId,
            user = User(
                id = id,
                displayName = displayName,
                username = username,
                avatarInitial = displayName.avatarInitial(),
                bio = description.orEmpty(),
                followersCount = followersCount,
                followingCount = followingCount,
                notesCount = notesCount,
                avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
                bannerUrl = bannerUrl?.takeIf { it.isNotBlank() },
            ),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun defaultUserRelationshipClient(): HttpClient {
    return HttpClient {
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
