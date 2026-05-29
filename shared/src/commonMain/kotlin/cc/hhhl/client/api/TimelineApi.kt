package cc.hhhl.client.api

import cc.hhhl.client.model.Note
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

enum class TimelineKind(val label: String) {
    Home("首页"),
    Social("社交"),
    Local("本地"),
    Global("全局"),
    Bubble("气泡"),
    Featured("精选"),
    Mentions("提及"),
}

enum class FollowingNotesListKind(val apiValue: String) {
    Following("following"),
    Followers("followers"),
    Mutuals("mutuals"),
}

interface TimelineApi {
    suspend fun loadTimeline(
        kind: TimelineKind,
        token: String,
        limit: Int,
        untilId: String? = null,
    ): TimelineLoadResult

    suspend fun loadMentions(
        token: String,
        limit: Int,
        untilId: String? = null,
    ): TimelineLoadResult =
        TimelineLoadResult.ServerError(501, "提及时间线接口未实现")

    suspend fun loadPollRecommendations(
        token: String,
        limit: Int,
        offset: Int = 0,
        excludeChannels: Boolean = false,
        local: Boolean? = null,
        expired: Boolean = false,
    ): TimelineLoadResult =
        TimelineLoadResult.ServerError(501, "投票推荐时间线接口未实现")

    suspend fun loadFollowingNotes(
        token: String,
        limit: Int,
        untilId: String? = null,
        list: FollowingNotesListKind = FollowingNotesListKind.Following,
        filesOnly: Boolean = false,
        includeNonPublic: Boolean = false,
        includeReplies: Boolean = false,
        includeQuotes: Boolean = false,
        includeBots: Boolean = true,
    ): TimelineLoadResult =
        TimelineLoadResult.ServerError(501, "关注筛选时间线接口未实现")
}

sealed interface TimelineLoadResult {
    data class Success(val notes: List<Note>) : TimelineLoadResult

    data object Unauthorized : TimelineLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : TimelineLoadResult

    data class NetworkError(val message: String) : TimelineLoadResult
}

class SharkeyTimelineApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultTimelineClient(),
) : TimelineApi {
    override suspend fun loadTimeline(
        kind: TimelineKind,
        token: String,
        limit: Int,
        untilId: String?,
    ): TimelineLoadResult {
        return loadNotes(
            endpoint = kind.endpointPath,
            token = token,
            limit = limit,
            untilId = untilId,
        )
    }

    override suspend fun loadMentions(
        token: String,
        limit: Int,
        untilId: String?,
    ): TimelineLoadResult {
        return loadNotes(
            endpoint = listOf("notes", "mentions"),
            token = token,
            limit = limit,
            untilId = untilId,
        )
    }

    override suspend fun loadPollRecommendations(
        token: String,
        limit: Int,
        offset: Int,
        excludeChannels: Boolean,
        local: Boolean?,
        expired: Boolean,
    ): TimelineLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return TimelineLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl(listOf("notes", "polls", "recommendation"))) {
                contentType(ContentType.Application.Json)
                setBody(
                    PollRecommendationRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        offset = offset.coerceAtLeast(0),
                        excludeChannels = excludeChannels,
                        local = local,
                        expired = expired,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return TimelineLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> TimelineLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> TimelineLoadResult.Unauthorized
                else -> TimelineLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TimelineLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFollowingNotes(
        token: String,
        limit: Int,
        untilId: String?,
        list: FollowingNotesListKind,
        filesOnly: Boolean,
        includeNonPublic: Boolean,
        includeReplies: Boolean,
        includeQuotes: Boolean,
        includeBots: Boolean,
    ): TimelineLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return TimelineLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl(listOf("notes", "following"))) {
                contentType(ContentType.Application.Json)
                setBody(
                    FollowingNotesRequest(
                        i = cleanToken,
                        list = list.apiValue,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        filesOnly = filesOnly,
                        includeNonPublic = includeNonPublic,
                        includeReplies = includeReplies,
                        includeQuotes = includeQuotes,
                        includeBots = includeBots,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return TimelineLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> TimelineLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> TimelineLoadResult.Unauthorized
                else -> TimelineLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TimelineLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun loadNotes(
        endpoint: List<String>,
        token: String,
        limit: Int,
        untilId: String?,
    ): TimelineLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return TimelineLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl(endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    TimelineRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return TimelineLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> TimelineLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> TimelineLoadResult.Unauthorized
                else -> TimelineLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TimelineLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun apiUrl(endpoint: List<String>): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint.toTypedArray())
            .buildString()
    }

    private val TimelineKind.endpointPath: List<String>
        get() = when (this) {
            TimelineKind.Home -> listOf("notes", "timeline")
            TimelineKind.Social -> listOf("notes", "hybrid-timeline")
            TimelineKind.Local -> listOf("notes", "local-timeline")
            TimelineKind.Global -> listOf("notes", "global-timeline")
            TimelineKind.Bubble -> listOf("notes", "bubble-timeline")
            TimelineKind.Featured -> listOf("notes", "featured")
            TimelineKind.Mentions -> listOf("notes", "mentions")
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

@Serializable
private data class TimelineRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class PollRecommendationRequest(
    val i: String,
    val limit: Int,
    val offset: Int,
    val excludeChannels: Boolean,
    val local: Boolean? = null,
    val expired: Boolean,
)

@Serializable
private data class FollowingNotesRequest(
    val i: String,
    val list: String = "following",
    val limit: Int,
    val untilId: String? = null,
    val filesOnly: Boolean = false,
    val includeNonPublic: Boolean = false,
    val includeReplies: Boolean = false,
    val includeQuotes: Boolean = false,
    val includeBots: Boolean = true,
)

private fun defaultTimelineClient(): HttpClient {
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
