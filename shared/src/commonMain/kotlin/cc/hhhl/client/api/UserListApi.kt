package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft
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

interface UserListApi {
    suspend fun loadLists(token: String): UserListLoadResult

    suspend fun loadListTimeline(
        token: String,
        listId: String,
        limit: Int,
        untilId: String? = null,
        withRenotes: Boolean = true,
        withFiles: Boolean = false,
    ): UserListTimelineLoadResult

    suspend fun createList(
        token: String,
        draft: UserListDraft,
    ): UserListMutationResult

    suspend fun updateList(
        token: String,
        listId: String,
        draft: UserListDraft,
    ): UserListMutationResult

    suspend fun deleteList(
        token: String,
        listId: String,
    ): UserListActionResult

    suspend fun pushUser(
        token: String,
        listId: String,
        userId: String,
    ): UserListActionResult

    suspend fun pullUser(
        token: String,
        listId: String,
        userId: String,
    ): UserListActionResult
}

sealed interface UserListLoadResult {
    data class Success(val lists: List<UserList>) : UserListLoadResult

    data object Unauthorized : UserListLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserListLoadResult

    data class NetworkError(val message: String) : UserListLoadResult
}

sealed interface UserListTimelineLoadResult {
    data class Success(val notes: List<Note>) : UserListTimelineLoadResult

    data object Unauthorized : UserListTimelineLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserListTimelineLoadResult

    data class NetworkError(val message: String) : UserListTimelineLoadResult
}

sealed interface UserListMutationResult {
    data class Success(val list: UserList) : UserListMutationResult

    data object Unauthorized : UserListMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserListMutationResult

    data class NetworkError(val message: String) : UserListMutationResult
}

sealed interface UserListActionResult {
    data object Success : UserListActionResult

    data object Unauthorized : UserListActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : UserListActionResult

    data class NetworkError(val message: String) : UserListActionResult
}

class SharkeyUserListApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultUserListClient(),
) : UserListApi {
    override suspend fun loadLists(token: String): UserListLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return UserListLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("users", "lists", "list")) {
                contentType(ContentType.Application.Json)
                setBody(UserListsRequest(i = cleanToken))
            }

            if (response.isSharkeyUnauthorized()) return UserListLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> UserListLoadResult.Success(
                    response.body<List<UserListDto>>().map { it.toDomainList() },
                )
                HttpStatusCode.Unauthorized -> UserListLoadResult.Unauthorized
                else -> UserListLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserListLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadListTimeline(
        token: String,
        listId: String,
        limit: Int,
        untilId: String?,
        withRenotes: Boolean,
        withFiles: Boolean,
    ): UserListTimelineLoadResult {
        val cleanToken = token.trim()
        val cleanListId = listId.trim()
        if (cleanToken.isEmpty()) return UserListTimelineLoadResult.Unauthorized
        if (cleanListId.isEmpty()) {
            return UserListTimelineLoadResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择列表",
            )
        }

        return try {
            val response = client.post(apiUrl("notes", "user-list-timeline")) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserListTimelineRequest(
                        i = cleanToken,
                        listId = cleanListId,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        withRenotes = withRenotes,
                        withFiles = withFiles,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return UserListTimelineLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> UserListTimelineLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> UserListTimelineLoadResult.Unauthorized
                else -> UserListTimelineLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserListTimelineLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createList(
        token: String,
        draft: UserListDraft,
    ): UserListMutationResult {
        return postListMutation(token = token, listId = null, draft = draft, action = "create")
    }

    override suspend fun updateList(
        token: String,
        listId: String,
        draft: UserListDraft,
    ): UserListMutationResult {
        return postListMutation(token = token, listId = listId, draft = draft, action = "update")
    }

    override suspend fun deleteList(
        token: String,
        listId: String,
    ): UserListActionResult {
        return postListAction(
            token = token,
            listId = listId,
            userId = null,
            action = "delete",
        )
    }

    override suspend fun pushUser(
        token: String,
        listId: String,
        userId: String,
    ): UserListActionResult {
        return postListAction(
            token = token,
            listId = listId,
            userId = userId,
            action = "push",
        )
    }

    override suspend fun pullUser(
        token: String,
        listId: String,
        userId: String,
    ): UserListActionResult {
        return postListAction(
            token = token,
            listId = listId,
            userId = userId,
            action = "pull",
        )
    }

    private suspend fun postListAction(
        token: String,
        listId: String,
        userId: String?,
        action: String,
    ): UserListActionResult {
        val cleanToken = token.trim()
        val cleanListId = listId.trim()
        val cleanUserId = userId?.trim()
        if (cleanToken.isEmpty()) return UserListActionResult.Unauthorized
        if (cleanListId.isEmpty()) {
            return UserListActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择列表",
            )
        }
        if (userId != null && cleanUserId.isNullOrEmpty()) {
            return UserListActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请输入用户 ID",
            )
        }

        return try {
            val response = client.post(apiUrl("users", "lists", action)) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserListActionRequest(
                        i = cleanToken,
                        listId = cleanListId,
                        userId = cleanUserId,
                    ),
                )
            }

            when {
                response.status.value in 200..299 -> UserListActionResult.Success
                response.isSharkeyUnauthorized() -> UserListActionResult.Unauthorized
                else -> UserListActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserListActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postListMutation(
        token: String,
        listId: String?,
        draft: UserListDraft,
        action: String,
    ): UserListMutationResult {
        val cleanToken = token.trim()
        val cleanListId = listId?.trim().orEmpty()
        if (cleanToken.isEmpty()) return UserListMutationResult.Unauthorized
        if (listId != null && cleanListId.isEmpty()) {
            return UserListMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择列表",
            )
        }

        return try {
            val response = client.post(apiUrl("users", "lists", action)) {
                contentType(ContentType.Application.Json)
                setBody(
                    UserListMutationRequest(
                        i = cleanToken,
                        listId = cleanListId.ifBlank { null },
                        name = draft.name.trim(),
                        isPublic = draft.isPublic,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return UserListMutationResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> UserListMutationResult.Success(
                    response.body<UserListDto>().toDomainList(),
                )
                HttpStatusCode.Unauthorized -> UserListMutationResult.Unauthorized
                else -> UserListMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            UserListMutationResult.NetworkError(error.message ?: "网络请求失败")
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
private data class UserListsRequest(
    val i: String,
)

@Serializable
private data class UserListTimelineRequest(
    val i: String,
    val listId: String,
    val limit: Int,
    val untilId: String? = null,
    val withRenotes: Boolean = true,
    val withFiles: Boolean = false,
)

@Serializable
private data class UserListMutationRequest(
    val i: String,
    val listId: String? = null,
    val name: String,
    val isPublic: Boolean,
)

@Serializable
private data class UserListActionRequest(
    val i: String,
    val listId: String,
    val userId: String? = null,
)

@Serializable
private data class UserListDto(
    val id: String,
    val createdAt: String = "",
    val createdBy: String,
    val name: String,
    val userIds: List<String> = emptyList(),
    val isPublic: Boolean,
    val isLiked: Boolean = false,
    val likedCount: Int = 0,
) {
    fun toDomainList(): UserList {
        return UserList(
            id = id,
            name = name,
            createdBy = createdBy,
            userIds = userIds,
            isPublic = isPublic,
            isLiked = isLiked,
            likedCount = likedCount,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class UserListErrorEnvelope(
    val error: UserListErrorDto? = null,
)

@Serializable
private data class UserListErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultUserListClient(): HttpClient {
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
