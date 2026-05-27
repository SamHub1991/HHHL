package cc.hhhl.client.api

import cc.hhhl.client.model.Announcement
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

interface AnnouncementApi {
    suspend fun loadAnnouncements(
        token: String,
        limit: Int,
        untilId: String? = null,
    ): AnnouncementLoadResult

    suspend fun showAnnouncement(
        token: String,
        announcementId: String,
    ): AnnouncementShowResult

    suspend fun markRead(
        token: String,
        announcementId: String,
    ): AnnouncementReadResult

    suspend fun loadAdminAnnouncements(
        token: String,
        limit: Int,
    ): AnnouncementAdminLoadResult

    suspend fun createAnnouncement(
        token: String,
        title: String,
        text: String,
        icon: String,
        display: String,
    ): AnnouncementMutationResult

    suspend fun updateAnnouncement(
        token: String,
        announcementId: String,
        title: String,
        text: String,
        icon: String,
        display: String,
    ): AnnouncementMutationResult

    suspend fun deleteAnnouncement(
        token: String,
        announcementId: String,
    ): AnnouncementDeleteResult
}

sealed interface AnnouncementLoadResult {
    data class Success(val announcements: List<Announcement>) : AnnouncementLoadResult

    data object Unauthorized : AnnouncementLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementLoadResult

    data class NetworkError(val message: String) : AnnouncementLoadResult
}

sealed interface AnnouncementShowResult {
    data class Success(val announcement: Announcement) : AnnouncementShowResult

    data object Unauthorized : AnnouncementShowResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementShowResult

    data class NetworkError(val message: String) : AnnouncementShowResult
}

sealed interface AnnouncementReadResult {
    data object Success : AnnouncementReadResult

    data object Unauthorized : AnnouncementReadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementReadResult

    data class NetworkError(val message: String) : AnnouncementReadResult
}

sealed interface AnnouncementAdminLoadResult {
    data class Success(val announcements: List<Announcement>) : AnnouncementAdminLoadResult

    data object Unauthorized : AnnouncementAdminLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementAdminLoadResult

    data class NetworkError(val message: String) : AnnouncementAdminLoadResult
}

sealed interface AnnouncementMutationResult {
    data class Success(val announcement: Announcement?) : AnnouncementMutationResult

    data object Unauthorized : AnnouncementMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementMutationResult

    data class NetworkError(val message: String) : AnnouncementMutationResult
}

sealed interface AnnouncementDeleteResult {
    data object Success : AnnouncementDeleteResult

    data object Unauthorized : AnnouncementDeleteResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AnnouncementDeleteResult

    data class NetworkError(val message: String) : AnnouncementDeleteResult
}

class SharkeyAnnouncementApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAnnouncementClient(),
) : AnnouncementApi {
    override suspend fun loadAnnouncements(
        token: String,
        limit: Int,
        untilId: String?,
    ): AnnouncementLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AnnouncementLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("announcements")) {
                contentType(ContentType.Application.Json)
                setBody(
                    AnnouncementListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        isActive = true,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return AnnouncementLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AnnouncementLoadResult.Success(
                    response.body<List<AnnouncementDto>>().map { it.toDomainAnnouncement() },
                )
                HttpStatusCode.Unauthorized -> AnnouncementLoadResult.Unauthorized
                else -> AnnouncementLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showAnnouncement(
        token: String,
        announcementId: String,
    ): AnnouncementShowResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AnnouncementShowResult.Unauthorized

        return try {
            val response = client.post(apiUrl("announcements", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    AnnouncementShowRequest(
                        i = cleanToken,
                        announcementId = announcementId.trim(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return AnnouncementShowResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AnnouncementShowResult.Success(
                    response.body<AnnouncementDto>().toDomainAnnouncement(),
                )
                HttpStatusCode.Unauthorized -> AnnouncementShowResult.Unauthorized
                else -> AnnouncementShowResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementShowResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun markRead(
        token: String,
        announcementId: String,
    ): AnnouncementReadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AnnouncementReadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "read-announcement")) {
                contentType(ContentType.Application.Json)
                setBody(
                    AnnouncementReadRequest(
                        i = cleanToken,
                        announcementId = announcementId.trim(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return AnnouncementReadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> AnnouncementReadResult.Success
                HttpStatusCode.Unauthorized -> AnnouncementReadResult.Unauthorized
                else -> AnnouncementReadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementReadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadAdminAnnouncements(
        token: String,
        limit: Int,
    ): AnnouncementAdminLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AnnouncementAdminLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("admin", "announcements", "list")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", cleanToken)
                        put("limit", limit.coerceIn(1, 100))
                    },
                )
            }

            if (response.isSharkeyUnauthorized()) return AnnouncementAdminLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AnnouncementAdminLoadResult.Success(
                    response.body<JsonElement>()
                        .asAnnouncementElements()
                        .map { Json.decodeFromJsonElement(AnnouncementDto.serializer(), it).toDomainAnnouncement() },
                )
                HttpStatusCode.Unauthorized -> AnnouncementAdminLoadResult.Unauthorized
                HttpStatusCode.Forbidden -> AnnouncementAdminLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AnnouncementAdminLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementAdminLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createAnnouncement(
        token: String,
        title: String,
        text: String,
        icon: String,
        display: String,
    ): AnnouncementMutationResult {
        return postAnnouncementMutation(
            token = token,
            announcementId = null,
            title = title,
            text = text,
            icon = icon,
            display = display,
            action = "create",
        )
    }

    override suspend fun updateAnnouncement(
        token: String,
        announcementId: String,
        title: String,
        text: String,
        icon: String,
        display: String,
    ): AnnouncementMutationResult {
        return postAnnouncementMutation(
            token = token,
            announcementId = announcementId,
            title = title,
            text = text,
            icon = icon,
            display = display,
            action = "update",
        )
    }

    override suspend fun deleteAnnouncement(
        token: String,
        announcementId: String,
    ): AnnouncementDeleteResult {
        val cleanToken = token.trim()
        val cleanAnnouncementId = announcementId.trim()
        if (cleanToken.isEmpty()) return AnnouncementDeleteResult.Unauthorized
        if (cleanAnnouncementId.isEmpty()) {
            return AnnouncementDeleteResult.ServerError(HttpStatusCode.BadRequest.value, "无法删除公告")
        }

        return try {
            val response = client.post(apiUrl("admin", "announcements", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", cleanToken)
                        put("id", cleanAnnouncementId)
                    },
                )
            }

            when {
                response.status.value in 200..299 -> AnnouncementDeleteResult.Success
                response.isSharkeyUnauthorized() -> AnnouncementDeleteResult.Unauthorized
                response.status == HttpStatusCode.Forbidden -> AnnouncementDeleteResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AnnouncementDeleteResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementDeleteResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postAnnouncementMutation(
        token: String,
        announcementId: String?,
        title: String,
        text: String,
        icon: String,
        display: String,
        action: String,
    ): AnnouncementMutationResult {
        val cleanToken = token.trim()
        val cleanAnnouncementId = announcementId?.trim().orEmpty()
        if (cleanToken.isEmpty()) return AnnouncementMutationResult.Unauthorized
        if (announcementId != null && cleanAnnouncementId.isEmpty()) {
            return AnnouncementMutationResult.ServerError(HttpStatusCode.BadRequest.value, "无法读取公告")
        }

        return try {
            val response = client.post(apiUrl("admin", "announcements", action)) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", cleanToken)
                        cleanAnnouncementId.ifBlank { null }?.let { put("id", it) }
                        put("title", title.trim())
                        put("text", text.trim())
                        put("icon", icon.trim().ifBlank { "info" })
                        put("display", display.trim().ifBlank { "normal" })
                    },
                )
            }

            when {
                response.status.value in 200..299 -> AnnouncementMutationResult.Success(
                    runCatching { response.body<AnnouncementDto>().toDomainAnnouncement() }.getOrNull(),
                )
                response.isSharkeyUnauthorized() -> AnnouncementMutationResult.Unauthorized
                response.status == HttpStatusCode.Forbidden -> AnnouncementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
                else -> AnnouncementMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AnnouncementMutationResult.NetworkError(error.message ?: "网络请求失败")
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
private data class AnnouncementListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
    val isActive: Boolean,
)

@Serializable
private data class AnnouncementShowRequest(
    val i: String,
    val announcementId: String,
)

@Serializable
private data class AnnouncementReadRequest(
    val i: String,
    val announcementId: String,
)

@Serializable
private data class AnnouncementDto(
    val id: String,
    val createdAt: String = "",
    val updatedAt: String? = null,
    val text: String,
    val title: String,
    val imageUrl: String? = null,
    val icon: String = "info",
    val display: String = "normal",
    val needConfirmationToRead: Boolean = false,
    val silence: Boolean = false,
    val confetti: Boolean = false,
    val forYou: Boolean = false,
    val isRead: Boolean = false,
) {
    fun toDomainAnnouncement(): Announcement {
        return Announcement(
            id = id,
            title = title,
            text = text,
            imageUrl = imageUrl,
            icon = icon,
            display = display,
            needConfirmationToRead = needConfirmationToRead,
            silence = silence,
            confetti = confetti,
            forYou = forYou,
            isRead = isRead,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            updatedAtLabel = updatedAt?.toLocalCompactDateLabel().orEmpty(),
        )
    }
}

@Serializable
private data class AnnouncementErrorEnvelope(
    val error: AnnouncementErrorDto? = null,
)

@Serializable
private data class AnnouncementErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun JsonElement.asAnnouncementElements(): List<JsonElement> {
    return when (this) {
        is JsonArray -> toList()
        is JsonObject -> this["items"]?.jsonArray?.toList()
            ?: this["announcements"]?.jsonArray?.toList()
            ?: emptyList()
        else -> emptyList()
    }
}

private fun defaultAnnouncementClient(): HttpClient {
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
