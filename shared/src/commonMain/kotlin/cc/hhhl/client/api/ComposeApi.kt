package cc.hhhl.client.api

import cc.hhhl.client.model.NoteVisibility
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

@Serializable
data class ComposeDraft(
    val text: String = "",
    val visibility: NoteVisibility = NoteVisibility.Public,
    val visibleUserIds: List<String> = emptyList(),
    val cw: String? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String> = emptyList(),
    val poll: ComposePollDraft? = null,
)

@Serializable
data class ComposePollDraft(
    val choices: List<String> = listOf("", ""),
    val multiple: Boolean = false,
    val expiresAt: String? = null,
)

interface ComposeApi {
    suspend fun createNote(
        token: String,
        draft: ComposeDraft,
    ): ComposeCreateResult
}

sealed interface ComposeCreateResult {
    data class Success(val createdNoteId: String?) : ComposeCreateResult

    data object Unauthorized : ComposeCreateResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ComposeCreateResult

    data class NetworkError(val message: String) : ComposeCreateResult
}

class SharkeyComposeApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultComposeClient(),
) : ComposeApi {
    override suspend fun createNote(
        token: String,
        draft: ComposeDraft,
    ): ComposeCreateResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ComposeCreateResult.Unauthorized

        return try {
            val response = client.post(apiUrl("notes", "create")) {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateNoteRequest(
                        i = cleanToken,
                        text = draft.text.trim(),
                        visibility = draft.visibility.toApiValue(),
                        visibleUserIds = draft.visibleUserIds
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .takeIf { draft.visibility == NoteVisibility.Specified && it.isNotEmpty() },
                        cw = draft.cw?.trim()?.takeIf { it.isNotEmpty() },
                        replyId = draft.replyId?.trim()?.takeIf { it.isNotEmpty() },
                        renoteId = draft.renoteId?.trim()?.takeIf { it.isNotEmpty() },
                        channelId = draft.channelId?.trim()?.takeIf { it.isNotEmpty() },
                        fileIds = draft.fileIds
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .takeIf { it.isNotEmpty() },
                        poll = draft.poll?.toRequest(),
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.body<CreateNoteResponse>()
                    ComposeCreateResult.Success(body.createdNote?.id)
                }
                HttpStatusCode.Unauthorized -> ComposeCreateResult.Unauthorized
                else -> ComposeCreateResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ComposeCreateResult.NetworkError(error.message ?: "网络请求失败")
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
private data class CreateNoteRequest(
    val i: String,
    val text: String,
    val visibility: String,
    val visibleUserIds: List<String>? = null,
    val cw: String? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val channelId: String? = null,
    val fileIds: List<String>? = null,
    val poll: CreatePollRequest? = null,
)

@Serializable
private data class CreatePollRequest(
    val choices: List<String>,
    val multiple: Boolean,
    val expiresAt: String? = null,
)

@Serializable
private data class CreateNoteResponse(
    val createdNote: CreatedNoteDto? = null,
)

@Serializable
private data class CreatedNoteDto(
    val id: String,
)

@Serializable
private data class ComposeErrorEnvelope(
    val error: ComposeErrorDto? = null,
)

@Serializable
private data class ComposeErrorDto(
    val message: String? = null,
)

private fun NoteVisibility.toApiValue(): String {
    return when (this) {
        NoteVisibility.Public -> "public"
        NoteVisibility.Home -> "home"
        NoteVisibility.Followers -> "followers"
        NoteVisibility.Specified -> "specified"
    }
}

private fun ComposePollDraft.toRequest(): CreatePollRequest? {
    val cleanChoices = choices
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (cleanChoices.size >= 2) {
        CreatePollRequest(
            choices = cleanChoices,
            multiple = multiple,
            expiresAt = expiresAt?.trim()?.takeIf { it.isNotEmpty() },
        )
    } else {
        null
    }
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultComposeClient(): HttpClient {
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
