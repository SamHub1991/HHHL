package cc.hhhl.client.api

import cc.hhhl.client.model.FavoriteNote
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

interface FavoriteNoteApi {
    suspend fun loadFavorites(
        token: String,
        limit: Int,
        untilId: String? = null,
    ): FavoriteNoteLoadResult
}

sealed interface FavoriteNoteLoadResult {
    data class Success(val favorites: List<FavoriteNote>) : FavoriteNoteLoadResult

    data object Unauthorized : FavoriteNoteLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FavoriteNoteLoadResult

    data class NetworkError(val message: String) : FavoriteNoteLoadResult
}

class SharkeyFavoriteNoteApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultFavoriteNoteClient(),
) : FavoriteNoteApi {
    override suspend fun loadFavorites(
        token: String,
        limit: Int,
        untilId: String?,
    ): FavoriteNoteLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return FavoriteNoteLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "favorites")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FavoriteNoteListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return FavoriteNoteLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> FavoriteNoteLoadResult.Success(
                    response.body<List<FavoriteNoteDto>>().map { it.toDomainFavorite() },
                )
                HttpStatusCode.Unauthorized -> FavoriteNoteLoadResult.Unauthorized
                else -> FavoriteNoteLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FavoriteNoteLoadResult.NetworkError(error.message ?: "网络请求失败")
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
private data class FavoriteNoteListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class FavoriteNoteDto(
    val id: String,
    val createdAt: String,
    val note: SharkeyNoteDto,
) {
    fun toDomainFavorite(): FavoriteNote {
        return FavoriteNote(
            id = id,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            note = note.toDomainNote(),
        )
    }
}

@Serializable
private data class FavoriteNoteErrorEnvelope(
    val error: FavoriteNoteErrorDto? = null,
)

@Serializable
private data class FavoriteNoteErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultFavoriteNoteClient(): HttpClient {
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
