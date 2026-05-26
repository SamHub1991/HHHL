package cc.hhhl.client.api

import cc.hhhl.client.model.CustomEmoji
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
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

interface EmojiApi {
    suspend fun loadEmojis(): EmojiLoadResult
}

sealed interface EmojiLoadResult {
    data class Success(val emojis: List<CustomEmoji>) : EmojiLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : EmojiLoadResult

    data class NetworkError(val message: String) : EmojiLoadResult
}

class SharkeyEmojiApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultEmojiClient(),
) : EmojiApi {
    override suspend fun loadEmojis(): EmojiLoadResult {
        return try {
            val response = client.post(apiUrl("emojis")) {
                contentType(ContentType.Application.Json)
            }

            when (response.status) {
                HttpStatusCode.OK -> EmojiLoadResult.Success(
                    response.body<EmojisResponse>().emojis.map { it.toDomainEmoji() },
                )
                else -> EmojiLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            EmojiLoadResult.NetworkError(error.message ?: "网络请求失败")
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
private data class EmojisResponse(
    val emojis: List<EmojiDto> = emptyList(),
)

@Serializable
private data class EmojiDto(
    val aliases: List<String> = emptyList(),
    val name: String,
    val category: String? = null,
    val url: String,
    val localOnly: Boolean = false,
    val isSensitive: Boolean = false,
) {
    fun toDomainEmoji(): CustomEmoji {
        return CustomEmoji(
            name = name,
            category = category,
            url = url,
            aliases = aliases,
            localOnly = localOnly,
            isSensitive = isSensitive,
        )
    }
}

@Serializable
private data class EmojiErrorEnvelope(
    val error: EmojiErrorDto? = null,
)

@Serializable
private data class EmojiErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultEmojiClient(): HttpClient {
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
