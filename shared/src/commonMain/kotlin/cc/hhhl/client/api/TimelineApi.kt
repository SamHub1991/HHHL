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
}

interface TimelineApi {
    suspend fun loadTimeline(
        kind: TimelineKind,
        token: String,
        limit: Int,
        untilId: String? = null,
    ): TimelineLoadResult
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
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return TimelineLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl(kind.endpointPath)) {
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
