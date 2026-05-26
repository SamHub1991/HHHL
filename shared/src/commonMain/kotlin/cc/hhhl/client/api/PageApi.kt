package cc.hhhl.client.api

import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageBlock
import cc.hhhl.client.model.PageListKind
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

interface PageApi {
    suspend fun loadPages(
        token: String,
        kind: PageListKind,
        limit: Int,
        untilId: String? = null,
    ): PageLoadResult

    suspend fun showPage(
        token: String,
        pageId: String,
    ): PageShowResult

    suspend fun likePage(
        token: String,
        pageId: String,
    ): PageActionResult

    suspend fun unlikePage(
        token: String,
        pageId: String,
    ): PageActionResult
}

sealed interface PageLoadResult {
    data class Success(val pages: List<Page>) : PageLoadResult

    data object Unauthorized : PageLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : PageLoadResult

    data class NetworkError(val message: String) : PageLoadResult
}

sealed interface PageShowResult {
    data class Success(val page: Page) : PageShowResult

    data object Unauthorized : PageShowResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : PageShowResult

    data class NetworkError(val message: String) : PageShowResult
}

sealed interface PageActionResult {
    data object Success : PageActionResult

    data object Unauthorized : PageActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : PageActionResult

    data class NetworkError(val message: String) : PageActionResult
}

class SharkeyPageApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultPageClient(),
) : PageApi {
    override suspend fun loadPages(
        token: String,
        kind: PageListKind,
        limit: Int,
        untilId: String?,
    ): PageLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return PageLoadResult.Unauthorized

        return try {
            val endpoint = when (kind) {
                PageListKind.Featured -> arrayOf("pages", "featured")
                PageListKind.Mine -> arrayOf("i", "pages")
            }
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    PageListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> PageLoadResult.Success(
                    response.body<List<PageDto>>().map { it.toDomainPage() },
                )
                HttpStatusCode.Unauthorized -> PageLoadResult.Unauthorized
                else -> PageLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PageLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showPage(
        token: String,
        pageId: String,
    ): PageShowResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return PageShowResult.Unauthorized

        return try {
            val response = client.post(apiUrl("pages", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    PageShowRequest(
                        i = cleanToken,
                        pageId = pageId.trim(),
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> PageShowResult.Success(response.body<PageDto>().toDomainPage())
                HttpStatusCode.Unauthorized -> PageShowResult.Unauthorized
                else -> PageShowResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PageShowResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun likePage(
        token: String,
        pageId: String,
    ): PageActionResult {
        return postPageAction(token, pageId, "like")
    }

    override suspend fun unlikePage(
        token: String,
        pageId: String,
    ): PageActionResult {
        return postPageAction(token, pageId, "unlike")
    }

    private suspend fun postPageAction(
        token: String,
        pageId: String,
        action: String,
    ): PageActionResult {
        val cleanToken = token.trim()
        val cleanPageId = pageId.trim()
        if (cleanToken.isEmpty()) return PageActionResult.Unauthorized
        if (cleanPageId.isEmpty()) {
            return PageActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择页面",
            )
        }

        return try {
            val response = client.post(apiUrl("pages", action)) {
                contentType(ContentType.Application.Json)
                setBody(PageActionRequest(i = cleanToken, pageId = cleanPageId))
            }

            when {
                response.status.value in 200..299 -> PageActionResult.Success
                response.status == HttpStatusCode.Unauthorized -> PageActionResult.Unauthorized
                else -> PageActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            PageActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class PageListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class PageShowRequest(
    val i: String,
    val pageId: String,
)

@Serializable
private data class PageActionRequest(
    val i: String,
    val pageId: String,
)

@Serializable
private data class PageDto(
    val id: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    val userId: String,
    val user: SharkeyUserSummaryDto,
    val content: List<PageBlockDto> = emptyList(),
    val title: String,
    val name: String,
    val summary: String? = null,
    val likedCount: Int = 0,
    val isLiked: Boolean = false,
) {
    fun toDomainPage(): Page {
        return Page(
            id = id,
            title = title,
            name = name,
            summary = summary.orEmpty(),
            author = user.toDomainUser(),
            userId = userId,
            blocks = content.map { it.toDomainBlock() },
            likedCount = likedCount,
            isLiked = isLiked,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            updatedAtLabel = updatedAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class PageBlockDto(
    val id: String,
    val type: String,
    val text: String? = null,
    val title: String? = null,
    val fileId: String? = null,
    val note: String? = null,
) {
    fun toDomainBlock(): PageBlock {
        return PageBlock(
            id = id,
            type = type,
            text = when (type) {
                "text" -> text.orEmpty()
                "section" -> title.orEmpty()
                "image" -> fileId?.let { "图片：$it" }.orEmpty()
                "note" -> note?.let { "引用帖子：$it" }.orEmpty()
                else -> text ?: title ?: note.orEmpty()
            },
        )
    }
}

@Serializable
private data class PageErrorEnvelope(
    val error: PageErrorDto? = null,
)

@Serializable
private data class PageErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultPageClient(): HttpClient {
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
