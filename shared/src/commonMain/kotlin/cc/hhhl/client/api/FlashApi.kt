package cc.hhhl.client.api

import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashDraft
import cc.hhhl.client.model.FlashListKind
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

interface FlashApi {
    suspend fun loadFlashes(
        token: String,
        kind: FlashListKind,
        limit: Int,
        untilId: String? = null,
        offset: Int = 0,
    ): FlashLoadResult

    suspend fun showFlash(
        token: String,
        flashId: String,
    ): FlashShowResult

    suspend fun likeFlash(
        token: String,
        flashId: String,
    ): FlashActionResult

    suspend fun unlikeFlash(
        token: String,
        flashId: String,
    ): FlashActionResult

    suspend fun createFlash(
        token: String,
        draft: FlashDraft,
    ): FlashMutationResult

    suspend fun updateFlash(
        token: String,
        flashId: String,
        draft: FlashDraft,
    ): FlashMutationResult

    suspend fun deleteFlash(
        token: String,
        flashId: String,
    ): FlashActionResult
}

sealed interface FlashLoadResult {
    data class Success(val flashes: List<Flash>) : FlashLoadResult

    data object Unauthorized : FlashLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FlashLoadResult

    data class NetworkError(val message: String) : FlashLoadResult
}

sealed interface FlashShowResult {
    data class Success(val flash: Flash) : FlashShowResult

    data object Unauthorized : FlashShowResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FlashShowResult

    data class NetworkError(val message: String) : FlashShowResult
}

sealed interface FlashActionResult {
    data object Success : FlashActionResult

    data object Unauthorized : FlashActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FlashActionResult

    data class NetworkError(val message: String) : FlashActionResult
}

sealed interface FlashMutationResult {
    data class Success(val flash: Flash) : FlashMutationResult

    data object Unauthorized : FlashMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : FlashMutationResult

    data class NetworkError(val message: String) : FlashMutationResult
}

class SharkeyFlashApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultFlashClient(),
) : FlashApi {
    override suspend fun loadFlashes(
        token: String,
        kind: FlashListKind,
        limit: Int,
        untilId: String?,
        offset: Int,
    ): FlashLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return FlashLoadResult.Unauthorized

        return try {
            val endpoint = when (kind) {
                FlashListKind.Featured -> arrayOf("flash", "featured")
                FlashListKind.Mine -> arrayOf("flash", "my")
                FlashListKind.Liked -> arrayOf("flash", "my-likes")
            }
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    FlashListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = if (kind == FlashListKind.Featured) {
                            null
                        } else {
                            untilId?.takeIf { it.isNotBlank() }
                        },
                        offset = if (kind == FlashListKind.Featured) {
                            offset.coerceAtLeast(0)
                        } else {
                            null
                        },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return FlashLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> FlashLoadResult.Success(
                    flashes = if (kind == FlashListKind.Liked) {
                        response.body<List<FlashLikeDto>>().map { it.flash.toDomainFlash() }
                    } else {
                        response.body<List<FlashDto>>().map { it.toDomainFlash() }
                    },
                )
                HttpStatusCode.Unauthorized -> FlashLoadResult.Unauthorized
                else -> FlashLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FlashLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showFlash(
        token: String,
        flashId: String,
    ): FlashShowResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return FlashShowResult.Unauthorized

        return try {
            val response = client.post(apiUrl("flash", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    FlashShowRequest(
                        i = cleanToken,
                        flashId = flashId.trim(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return FlashShowResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> FlashShowResult.Success(response.body<FlashDto>().toDomainFlash())
                HttpStatusCode.Unauthorized -> FlashShowResult.Unauthorized
                else -> FlashShowResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FlashShowResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun likeFlash(
        token: String,
        flashId: String,
    ): FlashActionResult {
        return postFlashAction(token, flashId, "like")
    }

    override suspend fun unlikeFlash(
        token: String,
        flashId: String,
    ): FlashActionResult {
        return postFlashAction(token, flashId, "unlike")
    }

    override suspend fun createFlash(
        token: String,
        draft: FlashDraft,
    ): FlashMutationResult {
        val cleanToken = token.trim()
        val cleanDraft = draft.trimmed
        if (cleanToken.isEmpty()) return FlashMutationResult.Unauthorized
        cleanDraft.validationMessage()?.let {
            return FlashMutationResult.ServerError(HttpStatusCode.BadRequest.value, it)
        }

        return postFlashMutation(
            endpoint = "create",
            body = FlashCreateRequest(
                i = cleanToken,
                title = cleanDraft.title,
                summary = cleanDraft.summary,
                script = cleanDraft.script,
                visibility = cleanDraft.visibility,
                permissions = cleanDraft.permissions,
            ),
        )
    }

    override suspend fun updateFlash(
        token: String,
        flashId: String,
        draft: FlashDraft,
    ): FlashMutationResult {
        val cleanToken = token.trim()
        val cleanFlashId = flashId.trim()
        val cleanDraft = draft.trimmed
        if (cleanToken.isEmpty()) return FlashMutationResult.Unauthorized
        if (cleanFlashId.isEmpty()) {
            return FlashMutationResult.ServerError(HttpStatusCode.BadRequest.value, "无法读取 Play")
        }
        cleanDraft.validationMessage()?.let {
            return FlashMutationResult.ServerError(HttpStatusCode.BadRequest.value, it)
        }

        return postFlashMutation(
            endpoint = "update",
            body = FlashUpdateRequest(
                i = cleanToken,
                flashId = cleanFlashId,
                title = cleanDraft.title,
                summary = cleanDraft.summary,
                script = cleanDraft.script,
                visibility = cleanDraft.visibility,
                permissions = cleanDraft.permissions,
            ),
        )
    }

    override suspend fun deleteFlash(
        token: String,
        flashId: String,
    ): FlashActionResult {
        return postFlashAction(token, flashId, "delete")
    }

    private suspend fun postFlashMutation(
        endpoint: String,
        body: Any,
    ): FlashMutationResult {
        return try {
            val response = client.post(apiUrl("flash", endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (response.isSharkeyUnauthorized()) return FlashMutationResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> FlashMutationResult.Success(response.body<FlashDto>().toDomainFlash())
                HttpStatusCode.Unauthorized -> FlashMutationResult.Unauthorized
                else -> FlashMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FlashMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postFlashAction(
        token: String,
        flashId: String,
        action: String,
    ): FlashActionResult {
        val cleanToken = token.trim()
        val cleanFlashId = flashId.trim()
        if (cleanToken.isEmpty()) return FlashActionResult.Unauthorized
        if (cleanFlashId.isEmpty()) {
            return FlashActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择 Play",
            )
        }

        return try {
            val response = client.post(apiUrl("flash", action)) {
                contentType(ContentType.Application.Json)
                setBody(FlashActionRequest(i = cleanToken, flashId = cleanFlashId))
            }

            when {
                response.status.value in 200..299 -> FlashActionResult.Success
                response.isSharkeyUnauthorized() -> FlashActionResult.Unauthorized
                else -> FlashActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            FlashActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class FlashListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
    val offset: Int? = null,
)

@Serializable
private data class FlashShowRequest(
    val i: String,
    val flashId: String,
)

@Serializable
private data class FlashActionRequest(
    val i: String,
    val flashId: String,
)

@Serializable
private data class FlashCreateRequest(
    val i: String,
    val title: String,
    val summary: String,
    val script: String,
    val visibility: String,
    val permissions: List<String> = emptyList(),
)

@Serializable
private data class FlashUpdateRequest(
    val i: String,
    val flashId: String,
    val title: String,
    val summary: String,
    val script: String,
    val visibility: String,
    val permissions: List<String> = emptyList(),
)

@Serializable
private data class FlashLikeDto(
    val id: String,
    val flash: FlashDto,
)

@Serializable
private data class FlashDto(
    val id: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    val userId: String,
    val user: SharkeyUserSummaryDto,
    val title: String,
    val summary: String = "",
    val script: String = "",
    val visibility: String = "",
    val permissions: List<String> = emptyList(),
    val likedCount: Int? = null,
    val isLiked: Boolean = false,
) {
    fun toDomainFlash(): Flash {
        return Flash(
            id = id,
            title = title,
            summary = summary,
            script = script,
            visibility = visibility,
            permissions = permissions,
            author = user.toDomainUser(),
            userId = userId,
            likedCount = likedCount ?: 0,
            isLiked = isLiked,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            updatedAtLabel = updatedAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class FlashErrorEnvelope(
    val error: FlashErrorDto? = null,
)

@Serializable
private data class FlashErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun FlashDraft.validationMessage(): String? {
    return when {
        title.isBlank() -> "请输入标题"
        script.isBlank() -> "请输入脚本"
        visibility !in setOf("public", "private") -> "不支持的可见性"
        else -> null
    }
}

private fun defaultFlashClient(): HttpClient {
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
