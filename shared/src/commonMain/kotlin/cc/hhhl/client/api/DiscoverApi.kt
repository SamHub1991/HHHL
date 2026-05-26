package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.FederationInstance
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

interface DiscoverApi {
    suspend fun searchNotes(
        token: String,
        query: String,
        limit: Int,
        untilId: String? = null,
    ): DiscoverSearchResult

    suspend fun searchUsers(
        token: String,
        query: String,
        limit: Int,
        origin: String = "combined",
    ): DiscoverUserSearchResult

    suspend fun loadTrendingHashtags(): DiscoverTrendResult

    suspend fun loadFederationInstances(
        limit: Int,
        offset: Int = 0,
        host: String? = null,
    ): DiscoverFederationResult
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

sealed interface DiscoverTrendResult {
    data class Success(val trends: List<TrendingHashtag>) : DiscoverTrendResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverTrendResult

    data class NetworkError(val message: String) : DiscoverTrendResult
}

sealed interface DiscoverFederationResult {
    data class Success(val instances: List<FederationInstance>) : DiscoverFederationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DiscoverFederationResult

    data class NetworkError(val message: String) : DiscoverFederationResult
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
                    ),
                )
            }

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

    override suspend fun loadTrendingHashtags(): DiscoverTrendResult {
        return try {
            val response = client.post(apiUrl("hashtags", "trend")) {
                contentType(ContentType.Application.Json)
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

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private data class DiscoverSearchRequest(
    val i: String,
    val query: String,
    val limit: Int,
    val untilId: String? = null,
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
private data class TrendingHashtagDto(
    val tag: String,
    val chart: List<Int> = emptyList(),
    val usersCount: Int = 0,
) {
    fun toDomainTrend(): TrendingHashtag {
        return TrendingHashtag(
            tag = tag,
            chart = chart,
            usersCount = usersCount,
        )
    }
}

@Serializable
private data class FederationInstancesRequest(
    val limit: Int,
    val offset: Int,
    val sort: String,
    val host: String? = null,
)

@Serializable
private data class FederationInstanceDto(
    val id: String,
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
    val isSilenced: Boolean = false,
) {
    fun toDomainInstance(): FederationInstance {
        return FederationInstance(
            id = id,
            host = host,
            name = name,
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
