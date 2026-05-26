package cc.hhhl.client.api

import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind
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

interface UserSocialApi {
    suspend fun loadUsers(
        token: String,
        userId: String,
        kind: UserSocialKind,
        limit: Int,
        untilId: String? = null,
    ): UserSocialLoadResult
}

sealed interface UserSocialLoadResult {
    data class Success(val items: List<UserSocialItem>) : UserSocialLoadResult

    data object Unauthorized : UserSocialLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserSocialLoadResult

    data class NetworkError(val message: String) : UserSocialLoadResult
}

class SharkeyUserSocialApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultUserSocialClient(),
) : UserSocialApi {
    override suspend fun loadUsers(
        token: String,
        userId: String,
        kind: UserSocialKind,
        limit: Int,
        untilId: String?,
    ): UserSocialLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return UserSocialLoadResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return UserSocialLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return try {
            val response = client.post(apiUrl("users", kind.endpointSegment)) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserSocialRequest(
                        i = cleanToken,
                        userId = cleanUserId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> UserSocialLoadResult.Success(
                    response.body<List<UserSocialRelationDto>>()
                        .mapNotNull { it.toDomainItem(kind) },
                )
                HttpStatusCode.Unauthorized -> UserSocialLoadResult.Unauthorized
                else -> UserSocialLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserSocialLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

private val UserSocialKind.endpointSegment: String
    get() = when (this) {
        UserSocialKind.Following -> "following"
        UserSocialKind.Followers -> "followers"
    }

@Serializable
private data class UserSocialRequest(
    val i: String,
    val userId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class UserSocialRelationDto(
    val id: String,
    val followee: UserSocialUserDto? = null,
    val follower: UserSocialUserDto? = null,
) {
    fun toDomainItem(kind: UserSocialKind): UserSocialItem? {
        val user = when (kind) {
            UserSocialKind.Following -> followee
            UserSocialKind.Followers -> follower
        } ?: return null
        return UserSocialItem(id = id, user = user.toDomainUser())
    }
}

@Serializable
private data class UserSocialUserDto(
    val id: String,
    val username: String,
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
            followersCount = followersCount,
            followingCount = followingCount,
            notesCount = notesCount,
            isFollowing = isFollowing,
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
private data class UserSocialErrorEnvelope(
    val error: UserSocialErrorDto? = null,
)

@Serializable
private data class UserSocialErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun defaultUserSocialClient(): HttpClient {
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
