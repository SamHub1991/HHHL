package cc.hhhl.client.api

import cc.hhhl.client.model.AvatarDecoration
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

interface AvatarDecorationApi {
    suspend fun load(token: String): AvatarDecorationLoadResult
}

sealed interface AvatarDecorationLoadResult {
    data class Success(val decorations: List<AvatarDecoration>) : AvatarDecorationLoadResult
    data object Unauthorized : AvatarDecorationLoadResult
    data class ServerError(val statusCode: Int, val message: String) : AvatarDecorationLoadResult
    data class NetworkError(val message: String) : AvatarDecorationLoadResult
}

class SharkeyAvatarDecorationApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAvatarDecorationClient(),
) : AvatarDecorationApi {
    override suspend fun load(token: String): AvatarDecorationLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AvatarDecorationLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("get-avatar-decorations")) {
                contentType(ContentType.Application.Json)
                setBody(AvatarDecorationRequest(i = cleanToken))
            }
            if (response.isSharkeyUnauthorized()) return AvatarDecorationLoadResult.Unauthorized
            when (response.status) {
                HttpStatusCode.OK -> AvatarDecorationLoadResult.Success(
                    response.body<List<AvatarDecorationDto>>().mapNotNull { it.toDomain() },
                )
                HttpStatusCode.Unauthorized -> AvatarDecorationLoadResult.Unauthorized
                else -> AvatarDecorationLoadResult.ServerError(
                    response.status.value,
                    response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AvatarDecorationLoadResult.NetworkError(error.message ?: "网络请求失败")
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
private data class AvatarDecorationRequest(val i: String)

@Serializable
private data class AvatarDecorationDto(
    val id: String? = null,
    val url: String? = null,
    val angle: Float = 0f,
    val flipH: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun toDomain(): AvatarDecoration? {
        val cleanUrl = url?.takeIf { it.isNotBlank() } ?: return null
        return AvatarDecoration(
            id = id.orEmpty(),
            url = cleanUrl,
            angle = angle,
            flipH = flipH,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultAvatarDecorationClient(): HttpClient {
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
