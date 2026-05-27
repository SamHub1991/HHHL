package cc.hhhl.client.api

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.GalleryPostDraft
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

interface GalleryApi {
    suspend fun loadPosts(
        token: String,
        kind: GalleryListKind,
        limit: Int,
        untilId: String? = null,
    ): GalleryLoadResult

    suspend fun showPost(
        token: String,
        postId: String,
    ): GalleryShowResult

    suspend fun likePost(
        token: String,
        postId: String,
    ): GalleryActionResult

    suspend fun unlikePost(
        token: String,
        postId: String,
    ): GalleryActionResult

    suspend fun createPost(
        token: String,
        draft: GalleryPostDraft,
    ): GalleryMutationResult

    suspend fun updatePost(
        token: String,
        postId: String,
        draft: GalleryPostDraft,
    ): GalleryMutationResult

    suspend fun deletePost(
        token: String,
        postId: String,
    ): GalleryActionResult
}

sealed interface GalleryLoadResult {
    data class Success(val posts: List<GalleryPost>) : GalleryLoadResult

    data object Unauthorized : GalleryLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : GalleryLoadResult

    data class NetworkError(val message: String) : GalleryLoadResult
}

sealed interface GalleryShowResult {
    data class Success(val post: GalleryPost) : GalleryShowResult

    data object Unauthorized : GalleryShowResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : GalleryShowResult

    data class NetworkError(val message: String) : GalleryShowResult
}

sealed interface GalleryActionResult {
    data object Success : GalleryActionResult

    data object Unauthorized : GalleryActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : GalleryActionResult

    data class NetworkError(val message: String) : GalleryActionResult
}

sealed interface GalleryMutationResult {
    data class Success(val post: GalleryPost) : GalleryMutationResult

    data object Unauthorized : GalleryMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : GalleryMutationResult

    data class NetworkError(val message: String) : GalleryMutationResult
}

class SharkeyGalleryApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultGalleryClient(),
) : GalleryApi {
    override suspend fun loadPosts(
        token: String,
        kind: GalleryListKind,
        limit: Int,
        untilId: String?,
    ): GalleryLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return GalleryLoadResult.Unauthorized

        return try {
            val endpoint = when (kind) {
                GalleryListKind.Featured -> arrayOf("gallery", "featured")
                GalleryListKind.Popular -> arrayOf("gallery", "popular")
                GalleryListKind.Recent -> arrayOf("gallery", "posts")
                GalleryListKind.Mine -> arrayOf("i", "gallery", "posts")
                GalleryListKind.Liked -> arrayOf("i", "gallery", "likes")
            }
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    GalleryListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return GalleryLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> GalleryLoadResult.Success(
                    posts = if (kind == GalleryListKind.Liked) {
                        response.body<List<GalleryLikeDto>>().map { it.post.toDomainPost() }
                    } else {
                        response.body<List<GalleryPostDto>>().map { it.toDomainPost() }
                    },
                )
                HttpStatusCode.Unauthorized -> GalleryLoadResult.Unauthorized
                else -> GalleryLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GalleryLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun showPost(
        token: String,
        postId: String,
    ): GalleryShowResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return GalleryShowResult.Unauthorized

        return try {
            val response = client.post(apiUrl("gallery", "posts", "show")) {
                contentType(ContentType.Application.Json)
                setBody(
                    GalleryShowRequest(
                        i = cleanToken,
                        postId = postId.trim(),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return GalleryShowResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> GalleryShowResult.Success(response.body<GalleryPostDto>().toDomainPost())
                HttpStatusCode.Unauthorized -> GalleryShowResult.Unauthorized
                else -> GalleryShowResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GalleryShowResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun likePost(
        token: String,
        postId: String,
    ): GalleryActionResult {
        return postGalleryAction(token, postId, "like")
    }

    override suspend fun unlikePost(
        token: String,
        postId: String,
    ): GalleryActionResult {
        return postGalleryAction(token, postId, "unlike")
    }

    override suspend fun createPost(
        token: String,
        draft: GalleryPostDraft,
    ): GalleryMutationResult {
        val cleanToken = token.trim()
        val cleanDraft = draft.cleaned()
        if (cleanToken.isEmpty()) return GalleryMutationResult.Unauthorized
        cleanDraft.validationMessage()?.let {
            return GalleryMutationResult.ServerError(HttpStatusCode.BadRequest.value, it)
        }

        return postGalleryMutation(
            endpoint = arrayOf("gallery", "posts", "create"),
            body = GalleryMutationRequest.fromDraft(cleanToken, cleanDraft),
        )
    }

    override suspend fun updatePost(
        token: String,
        postId: String,
        draft: GalleryPostDraft,
    ): GalleryMutationResult {
        val cleanToken = token.trim()
        val cleanPostId = postId.trim()
        val cleanDraft = draft.cleaned()
        if (cleanToken.isEmpty()) return GalleryMutationResult.Unauthorized
        if (cleanPostId.isEmpty()) {
            return GalleryMutationResult.ServerError(HttpStatusCode.BadRequest.value, "请选择图库作品")
        }
        cleanDraft.validationMessage()?.let {
            return GalleryMutationResult.ServerError(HttpStatusCode.BadRequest.value, it)
        }

        return postGalleryMutation(
            endpoint = arrayOf("gallery", "posts", "update"),
            body = GalleryMutationRequest.fromDraft(cleanToken, cleanDraft, cleanPostId),
        )
    }

    override suspend fun deletePost(
        token: String,
        postId: String,
    ): GalleryActionResult {
        return postGalleryAction(token, postId, "delete")
    }

    private suspend fun postGalleryMutation(
        endpoint: Array<String>,
        body: GalleryMutationRequest,
    ): GalleryMutationResult {
        return try {
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            when {
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created -> {
                    GalleryMutationResult.Success(response.body<GalleryPostDto>().toDomainPost())
                }
                response.isSharkeyUnauthorized() -> GalleryMutationResult.Unauthorized
                else -> GalleryMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GalleryMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postGalleryAction(
        token: String,
        postId: String,
        action: String,
    ): GalleryActionResult {
        val cleanToken = token.trim()
        val cleanPostId = postId.trim()
        if (cleanToken.isEmpty()) return GalleryActionResult.Unauthorized
        if (cleanPostId.isEmpty()) {
            return GalleryActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择图库作品",
            )
        }

        return try {
            val response = client.post(apiUrl("gallery", "posts", action)) {
                contentType(ContentType.Application.Json)
                setBody(GalleryActionRequest(i = cleanToken, postId = cleanPostId))
            }

            when {
                response.status.value in 200..299 -> GalleryActionResult.Success
                response.isSharkeyUnauthorized() -> GalleryActionResult.Unauthorized
                else -> GalleryActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GalleryActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class GalleryListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@Serializable
private data class GalleryShowRequest(
    val i: String,
    val postId: String,
)

@Serializable
private data class GalleryActionRequest(
    val i: String,
    val postId: String,
)

@Serializable
private data class GalleryMutationRequest(
    val i: String,
    val title: String,
    val description: String? = null,
    val fileIds: List<String>,
    val isSensitive: Boolean = false,
    val isPublic: Boolean = true,
    val postId: String? = null,
) {
    companion object {
        fun fromDraft(
            token: String,
            draft: GalleryPostDraft,
            postId: String? = null,
        ): GalleryMutationRequest {
            return GalleryMutationRequest(
                i = token,
                title = draft.title,
                description = draft.description.takeIf { it.isNotBlank() },
                fileIds = draft.fileIds,
                isSensitive = draft.isSensitive,
                isPublic = draft.isPublic,
                postId = postId,
            )
        }
    }
}

@Serializable
private data class GalleryLikeDto(
    val id: String,
    val post: GalleryPostDto,
)

@Serializable
private data class GalleryPostDto(
    val id: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    val userId: String,
    val user: SharkeyUserSummaryDto,
    val title: String,
    val description: String? = null,
    val fileIds: List<String> = emptyList(),
    val files: List<GalleryDriveFileDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val isSensitive: Boolean = false,
    val isPublic: Boolean = true,
    val likedCount: Int = 0,
    val isLiked: Boolean = false,
) {
    fun toDomainPost(): GalleryPost {
        return GalleryPost(
            id = id,
            title = title,
            description = description.orEmpty(),
            author = user.toDomainUser(),
            userId = userId,
            fileIds = fileIds,
            files = files.map { it.toDomainFile() },
            tags = tags,
            isSensitive = isSensitive,
            isPublic = isPublic,
            likedCount = likedCount,
            isLiked = isLiked,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            updatedAtLabel = updatedAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class GalleryDriveFileDto(
    val id: String,
    val createdAt: String = "",
    val name: String = "",
    val type: String = "",
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val comment: String? = null,
    val size: Long = 0,
    val isSensitive: Boolean = false,
) {
    fun toDomainFile(): DriveFile {
        return DriveFile(
            id = id,
            name = name,
            type = type,
            url = url,
            thumbnailUrl = thumbnailUrl,
            comment = comment,
            size = size,
            isSensitive = isSensitive,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class GalleryErrorEnvelope(
    val error: GalleryErrorDto? = null,
)

@Serializable
private data class GalleryErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun GalleryPostDraft.cleaned(): GalleryPostDraft {
    return copy(
        title = title.trim(),
        description = description.trim(),
        fileIds = fileIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )
}

private fun GalleryPostDraft.validationMessage(): String? {
    return when {
        title.isBlank() -> "请输入标题"
        fileIds.isEmpty() -> "请至少选择一个文件"
        else -> null
    }
}

private fun defaultGalleryClient(): HttpClient {
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
