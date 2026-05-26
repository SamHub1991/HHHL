package cc.hhhl.client.api

import cc.hhhl.client.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

const val USER_PROFILE_NAME_MAX_LENGTH = 50
const val USER_PROFILE_DESCRIPTION_MAX_LENGTH = 1500

interface UserProfileApi {
    suspend fun loadProfile(
        token: String,
        userId: String,
    ): UserProfileLoadResult

    suspend fun loadProfileByUsername(
        token: String,
        username: String,
    ): UserProfileLoadResult = UserProfileLoadResult.ServerError(400, "不支持按用户名读取用户资料")

    suspend fun updateProfile(
        token: String,
        draft: UserProfileUpdateDraft,
    ): UserProfileUpdateResult = UserProfileUpdateResult.ServerError(400, "不支持编辑用户资料")
}

sealed interface UserProfileLoadResult {
    data class Success(val user: User) : UserProfileLoadResult

    data object Unauthorized : UserProfileLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserProfileLoadResult

    data class NetworkError(val message: String) : UserProfileLoadResult
}

data class UserProfileUpdateDraft(
    val name: String?,
    val description: String,
    val avatarId: String? = null,
    val bannerId: String? = null,
)

sealed interface UserProfileUpdateResult {
    data class Success(val user: User) : UserProfileUpdateResult

    data object Unauthorized : UserProfileUpdateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserProfileUpdateResult

    data class NetworkError(val message: String) : UserProfileUpdateResult
}

class SharkeyUserProfileApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultUserProfileClient(),
) : UserProfileApi {
    override suspend fun loadProfile(
        token: String,
        userId: String,
    ): UserProfileLoadResult {
        val cleanToken = token.trim()
        val cleanUserId = userId.trim()
        if (cleanToken.isEmpty()) return UserProfileLoadResult.Unauthorized
        if (cleanUserId.isEmpty()) {
            return UserProfileLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择用户",
            )
        }

        return loadProfile(UserProfileRequest(i = cleanToken, userId = cleanUserId))
    }

    override suspend fun loadProfileByUsername(
        token: String,
        username: String,
    ): UserProfileLoadResult {
        val cleanToken = token.trim()
        val mention = username.trim().removePrefix("@")
        if (cleanToken.isEmpty()) return UserProfileLoadResult.Unauthorized
        if (mention.isEmpty()) {
            return UserProfileLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请输入用户名",
            )
        }
        val parts = mention.split("@", limit = 2)
        val cleanUsername = parts.getOrNull(0)?.trim().orEmpty()
        val cleanHost = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        if (cleanUsername.isEmpty()) {
            return UserProfileLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请输入用户名",
            )
        }

        return loadProfile(
            UserProfileRequest(
                i = cleanToken,
                username = cleanUsername,
                host = cleanHost,
            ),
        )
    }

    override suspend fun updateProfile(
        token: String,
        draft: UserProfileUpdateDraft,
    ): UserProfileUpdateResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return UserProfileUpdateResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "update")) {
                contentType(ContentType.Application.Json)
                setBody(UserProfileUpdateRequest.fromDraft(cleanToken, draft))
            }

            when (response.status) {
                HttpStatusCode.OK -> UserProfileUpdateResult.Success(response.body<UserProfileDto>().toDomainUser())
                HttpStatusCode.Unauthorized -> UserProfileUpdateResult.Unauthorized
                else -> UserProfileUpdateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserProfileUpdateResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun loadProfile(requestBody: UserProfileRequest): UserProfileLoadResult {
        return try {
            val response = client.post(apiUrl("users", "show")) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            when (response.status) {
                HttpStatusCode.OK -> UserProfileLoadResult.Success(response.body<UserProfileDto>().toDomainUser())
                HttpStatusCode.Unauthorized -> UserProfileLoadResult.Unauthorized
                else -> UserProfileLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserProfileLoadResult.NetworkError(error.message ?: "网络请求失败")
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

private suspend fun io.ktor.client.statement.HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

@Serializable
private data class UserProfileRequest(
    val i: String,
    val userId: String? = null,
    val username: String? = null,
    val host: String? = null,
)

@Serializable
private data class UserProfileUpdateRequest(
    val i: String,
    val name: String? = null,
    val description: String,
    val avatarId: String? = null,
    val bannerId: String? = null,
) {
    companion object {
        fun fromDraft(
            token: String,
            draft: UserProfileUpdateDraft,
        ): UserProfileUpdateRequest {
            return UserProfileUpdateRequest(
                i = token,
                name = draft.name?.trim()?.takeIf { it.isNotBlank() },
                description = draft.description.trim(),
                avatarId = draft.avatarId?.trim()?.takeIf { it.isNotBlank() },
                bannerId = draft.bannerId?.trim()?.takeIf { it.isNotBlank() },
            )
        }
    }
}

@Serializable
private data class UserProfileDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val pinnedNotes: List<SharkeyNoteDto> = emptyList(),
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
            pinnedNotes = pinnedNotes.map { it.toDomainNote() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            bannerUrl = bannerUrl?.takeIf { it.isNotBlank() },
        )
    }
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private fun defaultUserProfileClient(): HttpClient {
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
