package cc.hhhl.client.api

import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelCategory
import cc.hhhl.client.model.ChannelDefaultColorHex
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
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
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

interface ChannelApi {
    suspend fun loadChannelCategories(): ChannelCategoryLoadResult

    suspend fun loadChannels(
        token: String,
        kind: ChannelListKind,
        limit: Int,
        untilId: String? = null,
    ): ChannelLoadResult

    suspend fun loadChannelsByCategory(
        category: String?,
        uncategorized: Boolean,
        limit: Int,
        offset: Int = 0,
    ): ChannelLoadResult

    suspend fun loadChannelTimeline(
        token: String,
        channelId: String,
        limit: Int,
        untilId: String? = null,
        withRenotes: Boolean = true,
        withFiles: Boolean = false,
    ): ChannelTimelineLoadResult

    suspend fun followChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult

    suspend fun unfollowChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult

    suspend fun favoriteChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult

    suspend fun unfavoriteChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult

    suspend fun createChannel(
        token: String,
        draft: ChannelDraft,
    ): ChannelMutationResult

    suspend fun updateChannel(
        token: String,
        channelId: String,
        draft: ChannelDraft,
    ): ChannelMutationResult
}

sealed interface ChannelCategoryLoadResult {
    data class Success(val categories: List<ChannelCategory>) : ChannelCategoryLoadResult

    data object Unauthorized : ChannelCategoryLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChannelCategoryLoadResult

    data class NetworkError(val message: String) : ChannelCategoryLoadResult
}

sealed interface ChannelLoadResult {
    data class Success(val channels: List<Channel>) : ChannelLoadResult

    data object Unauthorized : ChannelLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChannelLoadResult

    data class NetworkError(val message: String) : ChannelLoadResult
}

sealed interface ChannelTimelineLoadResult {
    data class Success(val notes: List<Note>) : ChannelTimelineLoadResult

    data object Unauthorized : ChannelTimelineLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChannelTimelineLoadResult

    data class NetworkError(val message: String) : ChannelTimelineLoadResult
}

sealed interface ChannelActionResult {
    data object Success : ChannelActionResult

    data object Unauthorized : ChannelActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChannelActionResult

    data class NetworkError(val message: String) : ChannelActionResult
}

sealed interface ChannelMutationResult {
    data class Success(val channel: Channel) : ChannelMutationResult

    data object Unauthorized : ChannelMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : ChannelMutationResult

    data class NetworkError(val message: String) : ChannelMutationResult
}

class SharkeyChannelApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultChannelClient(),
) : ChannelApi {
    override suspend fun loadChannelCategories(): ChannelCategoryLoadResult {
        return try {
            val response = client.post(apiUrl("channels", "categories")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { })
            }

            if (response.isSharkeyUnauthorized()) return ChannelCategoryLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ChannelCategoryLoadResult.Success(
                    response.body<List<ChannelCategoryDto>>().map { it.toDomainCategory() },
                )
                HttpStatusCode.Unauthorized -> ChannelCategoryLoadResult.Unauthorized
                else -> ChannelCategoryLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelCategoryLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadChannels(
        token: String,
        kind: ChannelListKind,
        limit: Int,
        untilId: String?,
    ): ChannelLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ChannelLoadResult.Unauthorized

        return try {
            val endpoint = when (kind) {
                ChannelListKind.Featured -> arrayOf("channels", "featured")
                ChannelListKind.Followed -> arrayOf("channels", "followed")
                ChannelListKind.Favorites -> arrayOf("channels", "my-favorites")
                ChannelListKind.Owned -> arrayOf("channels", "owned")
            }
            val response = client.post(apiUrl(*endpoint)) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChannelListRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChannelLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ChannelLoadResult.Success(
                    response.body<List<ChannelDto>>().map { it.toDomainChannel() },
                )
                HttpStatusCode.Unauthorized -> ChannelLoadResult.Unauthorized
                else -> ChannelLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadChannelsByCategory(
        category: String?,
        uncategorized: Boolean,
        limit: Int,
        offset: Int,
    ): ChannelLoadResult {
        return try {
            val response = client.post(apiUrl("channels", "by-category")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChannelCategoryChannelsRequest(
                        category = category?.trim()?.takeIf { it.isNotBlank() },
                        uncategorized = uncategorized,
                        limit = limit.coerceIn(1, 50),
                        offset = offset.coerceAtLeast(0),
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChannelLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ChannelLoadResult.Success(
                    response.body<List<ChannelDto>>().map { it.toDomainChannel() },
                )
                HttpStatusCode.Unauthorized -> ChannelLoadResult.Unauthorized
                else -> ChannelLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadChannelTimeline(
        token: String,
        channelId: String,
        limit: Int,
        untilId: String?,
        withRenotes: Boolean,
        withFiles: Boolean,
    ): ChannelTimelineLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return ChannelTimelineLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("channels", "timeline")) {
                contentType(ContentType.Application.Json)
                setBody(
                    ChannelTimelineRequest(
                        i = cleanToken,
                        channelId = channelId.trim(),
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        withRenotes = withRenotes,
                        withFiles = withFiles,
                    ),
                )
            }

            if (response.isSharkeyUnauthorized()) return ChannelTimelineLoadResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ChannelTimelineLoadResult.Success(
                    response.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                )
                HttpStatusCode.Unauthorized -> ChannelTimelineLoadResult.Unauthorized
                else -> ChannelTimelineLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelTimelineLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun followChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult {
        return postChannelAction(token, channelId, "follow")
    }

    override suspend fun unfollowChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult {
        return postChannelAction(token, channelId, "unfollow")
    }

    override suspend fun favoriteChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult {
        return postChannelAction(token, channelId, "favorite")
    }

    override suspend fun unfavoriteChannel(
        token: String,
        channelId: String,
    ): ChannelActionResult {
        return postChannelAction(token, channelId, "unfavorite")
    }

    override suspend fun createChannel(
        token: String,
        draft: ChannelDraft,
    ): ChannelMutationResult {
        return postChannelMutation(token = token, channelId = null, draft = draft, action = "create")
    }

    override suspend fun updateChannel(
        token: String,
        channelId: String,
        draft: ChannelDraft,
    ): ChannelMutationResult {
        return postChannelMutation(token = token, channelId = channelId, draft = draft, action = "update")
    }

    private suspend fun postChannelAction(
        token: String,
        channelId: String,
        action: String,
    ): ChannelActionResult {
        val cleanToken = token.trim()
        val cleanChannelId = channelId.trim()
        if (cleanToken.isEmpty()) return ChannelActionResult.Unauthorized
        if (cleanChannelId.isEmpty()) {
            return ChannelActionResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择频道",
            )
        }

        return try {
            val response = client.post(apiUrl("channels", action)) {
                contentType(ContentType.Application.Json)
                setBody(ChannelActionRequest(i = cleanToken, channelId = cleanChannelId))
            }

            when {
                response.status.value in 200..299 -> ChannelActionResult.Success
                response.isSharkeyUnauthorized() -> ChannelActionResult.Unauthorized
                else -> ChannelActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelActionResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private suspend fun postChannelMutation(
        token: String,
        channelId: String?,
        draft: ChannelDraft,
        action: String,
    ): ChannelMutationResult {
        val cleanToken = token.trim()
        val cleanChannelId = channelId?.trim().orEmpty()
        if (cleanToken.isEmpty()) return ChannelMutationResult.Unauthorized
        if (channelId != null && cleanChannelId.isEmpty()) {
            return ChannelMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择频道",
            )
        }

        return try {
            val response = client.post(apiUrl("channels", action)) {
                contentType(ContentType.Application.Json)
                setBody(ChannelMutationRequest.fromDraft(cleanToken, cleanChannelId.ifBlank { null }, draft))
            }

            if (response.isSharkeyUnauthorized()) return ChannelMutationResult.Unauthorized

            when (response.status) {
                HttpStatusCode.OK -> ChannelMutationResult.Success(
                    response.body<ChannelDto>().toDomainChannel(),
                )
                HttpStatusCode.Unauthorized -> ChannelMutationResult.Unauthorized
                else -> ChannelMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ChannelMutationResult.NetworkError(error.message ?: "网络请求失败")
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
private data class ChannelListRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ChannelCategoryChannelsRequest(
    val category: String? = null,
    @EncodeDefault
    val uncategorized: Boolean = false,
    val limit: Int,
    val offset: Int,
)

@Serializable
private data class ChannelTimelineRequest(
    val i: String,
    val channelId: String,
    val limit: Int,
    val untilId: String? = null,
    val withRenotes: Boolean = true,
    val withFiles: Boolean = false,
)

@Serializable
private data class ChannelActionRequest(
    val i: String,
    val channelId: String,
)

@Serializable
private data class ChannelMutationRequest(
    val i: String,
    val channelId: String? = null,
    val name: String,
    val description: String,
    val color: String,
    val bannerId: String? = null,
    val isArchived: Boolean,
    val isSensitive: Boolean,
    val allowRenoteToExternal: Boolean,
    val category: String? = null,
) {
    companion object {
        fun fromDraft(
            token: String,
            channelId: String?,
            draft: ChannelDraft,
        ): ChannelMutationRequest {
            return ChannelMutationRequest(
                i = token,
                channelId = channelId,
                name = draft.name.trim(),
                description = draft.description.trim(),
                color = draft.color.trim().ifBlank { ChannelDefaultColorHex },
                bannerId = draft.bannerId?.trim()?.takeIf { it.isNotBlank() },
                isArchived = draft.isArchived,
                isSensitive = draft.isSensitive,
                allowRenoteToExternal = draft.allowRenoteToExternal,
                category = draft.category.trim().takeIf { it.isNotBlank() },
            )
        }
    }
}

@Serializable
private data class ChannelCategoryDto(
    val category: String,
    val channelsCount: Int,
) {
    fun toDomainCategory(): ChannelCategory {
        return ChannelCategory(
            name = category,
            channelsCount = channelsCount,
            uncategorized = category.isBlank(),
        )
    }
}

@Serializable
private data class ChannelDto(
    val id: String,
    val createdAt: String = "",
    val lastNotedAt: String? = null,
    val name: String = "",
    val description: String? = null,
    val userId: String? = null,
    val bannerUrl: String? = null,
    val pinnedNoteIds: List<String> = emptyList(),
    val color: String = "",
    val isArchived: Boolean = false,
    val usersCount: Int = 0,
    val notesCount: Int = 0,
    val isSensitive: Boolean = false,
    val allowRenoteToExternal: Boolean = false,
    val isFollowing: Boolean = false,
    val isFavorited: Boolean = false,
    val hasUnreadNote: Boolean = false,
    val pinnedNotes: List<SharkeyNoteDto> = emptyList(),
    val category: String? = null,
) {
    fun toDomainChannel(): Channel {
        return Channel(
            id = id,
            name = name,
            description = description.orEmpty(),
            color = color,
            userId = userId,
            bannerUrl = bannerUrl,
            pinnedNoteIds = pinnedNoteIds,
            pinnedNotes = pinnedNotes.map { it.toDomainNote() },
            isArchived = isArchived,
            isSensitive = isSensitive,
            allowRenoteToExternal = allowRenoteToExternal,
            isFollowing = isFollowing,
            isFavorited = isFavorited,
            hasUnreadNote = hasUnreadNote,
            usersCount = usersCount,
            notesCount = notesCount,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            lastNotedAtLabel = lastNotedAt?.toLocalCompactDateLabel().orEmpty(),
            category = category.orEmpty(),
        )
    }
}

@Serializable
private data class ChannelErrorEnvelope(
    val error: ChannelErrorDto? = null,
)

@Serializable
private data class ChannelErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultChannelClient(): HttpClient {
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
