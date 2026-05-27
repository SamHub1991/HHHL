package cc.hhhl.client.api

import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.model.Note
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

interface AntennaApi {
    suspend fun loadAntennas(token: String): AntennaLoadResult

    suspend fun loadAntennaNotes(
        token: String,
        antennaId: String,
        limit: Int,
        untilId: String? = null,
    ): AntennaNotesLoadResult

    suspend fun createAntenna(
        token: String,
        draft: AntennaDraft,
    ): AntennaMutationResult

    suspend fun updateAntenna(
        token: String,
        antennaId: String,
        draft: AntennaDraft,
    ): AntennaMutationResult

    suspend fun deleteAntenna(
        token: String,
        antennaId: String,
    ): AntennaActionResult
}

sealed interface AntennaLoadResult {
    data class Success(val antennas: List<Antenna>) : AntennaLoadResult

    data object Unauthorized : AntennaLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AntennaLoadResult

    data class NetworkError(val message: String) : AntennaLoadResult
}

sealed interface AntennaNotesLoadResult {
    data class Success(val notes: List<Note>) : AntennaNotesLoadResult

    data object Unauthorized : AntennaNotesLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AntennaNotesLoadResult

    data class NetworkError(val message: String) : AntennaNotesLoadResult
}

sealed interface AntennaMutationResult {
    data class Success(val antenna: Antenna) : AntennaMutationResult

    data object Unauthorized : AntennaMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AntennaMutationResult

    data class NetworkError(val message: String) : AntennaMutationResult
}

sealed interface AntennaActionResult {
    data object Success : AntennaActionResult

    data object Unauthorized : AntennaActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : AntennaActionResult

    data class NetworkError(val message: String) : AntennaActionResult
}

class SharkeyAntennaApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultAntennaClient(),
) : AntennaApi {
    override suspend fun loadAntennas(token: String): AntennaLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return AntennaLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("antennas", "list")) {
                contentType(ContentType.Application.Json)
                setBody(AntennasRequest(i = cleanToken))
            }

            if (response.isSharkeyUnauthorized()) return AntennaLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AntennaLoadResult.Success(
                    response.body<List<AntennaDto>>().map { it.toDomainAntenna() },
                )
                HttpStatusCode.Unauthorized -> AntennaLoadResult.Unauthorized
                else -> AntennaLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AntennaLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadAntennaNotes(
        token: String,
        antennaId: String,
        limit: Int,
        untilId: String?,
    ): AntennaNotesLoadResult {
        val cleanToken = token.trim()
        val cleanAntennaId = antennaId.trim()
        if (cleanToken.isEmpty()) return AntennaNotesLoadResult.Unauthorized
        if (cleanAntennaId.isEmpty()) {
            return AntennaNotesLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择天线",
            )
        }

        return try {
            val response = client.post(apiUrl("antennas", "notes")) {
                contentType(ContentType.Application.Json)
                setBody(
                    AntennaNotesRequest(
                        i = cleanToken,
                        antennaId = cleanAntennaId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return AntennaNotesLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AntennaNotesLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> AntennaNotesLoadResult.Unauthorized
                else -> AntennaNotesLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AntennaNotesLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createAntenna(
        token: String,
        draft: AntennaDraft,
    ): AntennaMutationResult {
        return postAntennaMutation(token = token, antennaId = null, draft = draft, action = "create")
    }

    override suspend fun updateAntenna(
        token: String,
        antennaId: String,
        draft: AntennaDraft,
    ): AntennaMutationResult {
        return postAntennaMutation(token = token, antennaId = antennaId, draft = draft, action = "update")
    }

    override suspend fun deleteAntenna(
        token: String,
        antennaId: String,
    ): AntennaActionResult {
        val cleanToken = token.trim()
        val cleanAntennaId = antennaId.trim()
        if (cleanToken.isEmpty()) return AntennaActionResult.Unauthorized
        if (cleanAntennaId.isEmpty()) {
            return AntennaActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择天线",
            )
        }

        return try {
            val response = client.post(apiUrl("antennas", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(AntennaDeleteRequest(i = cleanToken, antennaId = cleanAntennaId))
            }

            when {
                response.status.value in 200..299 -> AntennaActionResult.Success
                response.isSharkeyUnauthorized() -> AntennaActionResult.Unauthorized
                else -> AntennaActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AntennaActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postAntennaMutation(
        token: String,
        antennaId: String?,
        draft: AntennaDraft,
        action: String,
    ): AntennaMutationResult {
        val cleanToken = token.trim()
        val cleanAntennaId = antennaId?.trim().orEmpty()
        if (cleanToken.isEmpty()) return AntennaMutationResult.Unauthorized
        if (antennaId != null && cleanAntennaId.isEmpty()) {
            return AntennaMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择天线",
            )
        }

        return try {
            val response = client.post(apiUrl("antennas", action)) {
                contentType(ContentType.Application.Json)
                setBody(AntennaMutationRequest.fromDraft(cleanToken, cleanAntennaId.ifBlank { null }, draft))
            }

            if (response.isSharkeyUnauthorized()) return AntennaMutationResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> AntennaMutationResult.Success(
                    response.body<AntennaDto>().toDomainAntenna(),
                )
                HttpStatusCode.Unauthorized -> AntennaMutationResult.Unauthorized
                else -> AntennaMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AntennaMutationResult.NetworkError(error.message ?: "网络请求失败")
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
private data class AntennasRequest(
    val i: String,
)

@Serializable
private data class AntennaNotesRequest(
    val i: String,
    val antennaId: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class AntennaMutationRequest(
    val i: String,
    val antennaId: String? = null,
    val name: String,
    val src: String,
    val keywords: List<List<String>>,
    val excludeKeywords: List<List<String>>,
    val userListId: String? = null,
    val users: List<String>,
    val caseSensitive: Boolean,
    val localOnly: Boolean,
    val excludeBots: Boolean,
    val withReplies: Boolean,
    val withFile: Boolean,
    val isActive: Boolean,
    val notify: Boolean,
    val excludeNotesInSensitiveChannel: Boolean,
) {
    companion object {
        fun fromDraft(
            token: String,
            antennaId: String?,
            draft: AntennaDraft,
        ): AntennaMutationRequest {
            return AntennaMutationRequest(
                i = token,
                antennaId = antennaId,
                name = draft.name.trim(),
                src = draft.source.trim().ifBlank { "all" },
                keywords = draft.keywords.cleanedKeywordGroups(),
                excludeKeywords = draft.excludeKeywords.cleanedKeywordGroups(),
                userListId = draft.userListId?.trim()?.takeIf { it.isNotBlank() },
                users = draft.users.mapNotNull { it.trim().takeIf(String::isNotBlank) },
                caseSensitive = draft.caseSensitive,
                localOnly = draft.localOnly,
                excludeBots = draft.excludeBots,
                withReplies = draft.withReplies,
                withFile = draft.withFile,
                isActive = draft.isActive,
                notify = draft.notify,
                excludeNotesInSensitiveChannel = draft.excludeNotesInSensitiveChannel,
            )
        }
    }
}

@Serializable
private data class AntennaDeleteRequest(
    val i: String,
    val antennaId: String,
)

@Serializable
private data class AntennaDto(
    val id: String,
    val createdAt: String = "",
    val name: String,
    val keywords: List<List<String>> = emptyList(),
    val excludeKeywords: List<List<String>> = emptyList(),
    val src: String,
    val userListId: String? = null,
    val users: List<String> = emptyList(),
    val caseSensitive: Boolean = false,
    val localOnly: Boolean = false,
    val excludeBots: Boolean = false,
    val withReplies: Boolean = false,
    val withFile: Boolean = false,
    val isActive: Boolean = true,
    val hasUnreadNote: Boolean = false,
    val notify: Boolean = false,
    val excludeNotesInSensitiveChannel: Boolean = false,
) {
    fun toDomainAntenna(): Antenna {
        return Antenna(
            id = id,
            name = name,
            source = src,
            keywords = keywords,
            excludeKeywords = excludeKeywords,
            userListId = userListId,
            users = users,
            caseSensitive = caseSensitive,
            localOnly = localOnly,
            excludeBots = excludeBots,
            withReplies = withReplies,
            withFile = withFile,
            isActive = isActive,
            hasUnreadNote = hasUnreadNote,
            notify = notify,
            excludeNotesInSensitiveChannel = excludeNotesInSensitiveChannel,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class AntennaErrorEnvelope(
    val error: AntennaErrorDto? = null,
)

@Serializable
private data class AntennaErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun List<List<String>>.cleanedKeywordGroups(): List<List<String>> {
    return mapNotNull { group ->
        group.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .takeIf { it.isNotEmpty() }
    }
}

private fun defaultAntennaClient(): HttpClient {
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
