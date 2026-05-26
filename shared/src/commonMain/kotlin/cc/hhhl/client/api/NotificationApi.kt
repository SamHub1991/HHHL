package cc.hhhl.client.api

import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
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

interface NotificationApi {
    suspend fun loadNotifications(
        token: String,
        limit: Int,
        untilId: String? = null,
        includeTypes: List<NotificationType> = emptyList(),
    ): NotificationLoadResult

    suspend fun markAllAsRead(token: String): NotificationActionResult
}

sealed interface NotificationLoadResult {
    data class Success(val notifications: List<NotificationItem>) : NotificationLoadResult

    data object Unauthorized : NotificationLoadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : NotificationLoadResult

    data class NetworkError(val message: String) : NotificationLoadResult
}

sealed interface NotificationActionResult {
    data object Success : NotificationActionResult

    data object Unauthorized : NotificationActionResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : NotificationActionResult

    data class NetworkError(val message: String) : NotificationActionResult
}

class SharkeyNotificationApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultNotificationClient(),
) : NotificationApi {
    override suspend fun loadNotifications(
        token: String,
        limit: Int,
        untilId: String?,
        includeTypes: List<NotificationType>,
    ): NotificationLoadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return NotificationLoadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("i", "notifications")) {
                contentType(ContentType.Application.Json)
                setBody(
                    NotificationRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        includeTypes = includeTypes
                            .mapNotNull { it.toSharkeyType() }
                            .takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when (response.status) {
                HttpStatusCode.OK -> NotificationLoadResult.Success(
                    response.body<List<NotificationDto>>().map { it.toDomainNotification() },
                )
                HttpStatusCode.Unauthorized -> NotificationLoadResult.Unauthorized
                else -> NotificationLoadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NotificationLoadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun markAllAsRead(token: String): NotificationActionResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return NotificationActionResult.Unauthorized

        return try {
            val response = client.post(apiUrl("notifications", "mark-all-as-read")) {
                contentType(ContentType.Application.Json)
                setBody(NotificationActionRequest(i = cleanToken))
            }

            when {
                response.status.value in 200..299 -> NotificationActionResult.Success
                response.status == HttpStatusCode.Unauthorized -> NotificationActionResult.Unauthorized
                else -> NotificationActionResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            NotificationActionResult.NetworkError(error.message ?: "网络请求失败")
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
private data class NotificationRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
    val includeTypes: List<String>? = null,
)

@Serializable
private data class NotificationActionRequest(
    val i: String,
)

@Serializable
private data class NotificationDto(
    val id: String,
    val createdAt: String,
    val type: String,
    val user: NotificationUserDto? = null,
    val note: NotificationNoteDto? = null,
    val reaction: String? = null,
    val reactions: List<GroupedReactionDto> = emptyList(),
    val users: List<NotificationUserDto> = emptyList(),
    val message: String? = null,
    val body: String? = null,
    val header: String? = null,
    val reason: String? = null,
    val exportedEntity: String? = null,
    val importedEntity: String? = null,
    val fileId: String? = null,
    val permCount: Int? = null,
    val rank: String? = null,
) {
    fun toDomainNotification(): NotificationItem {
        val notificationType = type.toNotificationType()
        val groupedUsers = when {
            reactions.isNotEmpty() -> reactions.map { it.user }
            users.isNotEmpty() -> users
            else -> emptyList()
        }
        return NotificationItem(
            id = id,
            type = notificationType,
            actor = user?.toDomainUser()
                ?: groupedUsers.firstOrNull()?.toDomainUser()
                ?: systemUser,
            text = toMessage(notificationType),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            noteId = note?.id,
            notePreviewText = note?.text?.takeIf { it.isNotBlank() },
        )
    }

    private fun toMessage(notificationType: NotificationType): String {
        return when (notificationType) {
            NotificationType.ReactionGrouped -> groupedReactionMessage()
            NotificationType.RenoteGrouped -> groupedRenoteMessage()
            NotificationType.App -> listOfNotNull(
                header?.takeIf { it.isNotBlank() },
                body?.takeIf { it.isNotBlank() },
            ).joinToString("：").takeIf { it.isNotBlank() } ?: "应用通知"
            NotificationType.FollowRequestAccepted -> "接受了你的关注请求" +
                message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            NotificationType.ScheduledNoteFailed -> "定时帖子发布失败" +
                reason?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            NotificationType.ExportCompleted -> "导出已完成" +
                exportedEntity?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            NotificationType.ImportCompleted -> "导入已完成" +
                importedEntity?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            NotificationType.SharedAccessGranted -> "授予了共享访问权限" +
                permCount?.let { "：$it 项权限" }.orEmpty()
            else -> notificationType.message(reaction)
        }
    }

    private fun groupedReactionMessage(): String {
        val names = reactions.map { it.user.toDomainUser().displayName }
        val reactionText = reactions
            .map { it.reaction }
            .distinct()
            .joinToString(" ")
        val actorLabel = names.firstOrNull()?.let { name ->
            if (names.size > 1) "$name 等 ${names.size} 人" else name
        } ?: "多人"
        return "${actorLabel}对你的帖子做出了反应" +
            reactionText.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    }

    private fun groupedRenoteMessage(): String {
        val names = users.map { it.toDomainUser().displayName }
        return names.firstOrNull()?.let { name ->
            if (names.size > 1) "$name 等 ${names.size} 人转发了你的帖子" else "$name 转发了你的帖子"
        } ?: "多人转发了你的帖子"
    }
}

@Serializable
private data class NotificationUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val avatarUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
private data class NotificationNoteDto(
    val id: String,
    val text: String? = null,
)

@Serializable
private data class GroupedReactionDto(
    val user: NotificationUserDto,
    val reaction: String,
)

@Serializable
private data class NotificationErrorEnvelope(
    val error: NotificationErrorDto? = null,
)

@Serializable
private data class NotificationErrorDto(
    val message: String? = null,
)

private val systemUser = User(
    id = "system",
    displayName = "系统",
    username = "system",
    avatarInitial = "系",
)

private fun String.toNotificationType(): NotificationType {
    return when (this) {
        "note" -> NotificationType.Note
        "reply" -> NotificationType.Reply
        "mention" -> NotificationType.Mention
        "reaction" -> NotificationType.Reaction
        "follow" -> NotificationType.Follow
        "renote" -> NotificationType.Renote
        "quote" -> NotificationType.Quote
        "pollEnded" -> NotificationType.PollEnded
        "receiveFollowRequest" -> NotificationType.FollowRequestReceived
        "followRequestAccepted" -> NotificationType.FollowRequestAccepted
        "roleAssigned" -> NotificationType.RoleAssigned
        "chatRoomInvitationReceived" -> NotificationType.ChatRoomInvitation
        "achievementEarned" -> NotificationType.AchievementEarned
        "exportCompleted" -> NotificationType.ExportCompleted
        "importCompleted" -> NotificationType.ImportCompleted
        "login" -> NotificationType.Login
        "createToken" -> NotificationType.CreateToken
        "app" -> NotificationType.App
        "edited" -> NotificationType.Edited
        "scheduledNoteFailed" -> NotificationType.ScheduledNoteFailed
        "scheduledNotePosted" -> NotificationType.ScheduledNotePosted
        "sharedAccessGranted" -> NotificationType.SharedAccessGranted
        "sharedAccessRevoked" -> NotificationType.SharedAccessRevoked
        "sharedAccessLogin" -> NotificationType.SharedAccessLogin
        "reaction:grouped" -> NotificationType.ReactionGrouped
        "renote:grouped" -> NotificationType.RenoteGrouped
        "test" -> NotificationType.Test
        else -> NotificationType.Unknown
    }
}

private fun NotificationType.toSharkeyType(): String? {
    return when (this) {
        NotificationType.Note -> "note"
        NotificationType.Reply -> "reply"
        NotificationType.Mention -> "mention"
        NotificationType.Reaction -> "reaction"
        NotificationType.Follow -> "follow"
        NotificationType.Renote -> "renote"
        NotificationType.Quote -> "quote"
        NotificationType.PollEnded -> "pollEnded"
        NotificationType.FollowRequestReceived -> "receiveFollowRequest"
        NotificationType.FollowRequestAccepted -> "followRequestAccepted"
        NotificationType.RoleAssigned -> "roleAssigned"
        NotificationType.ChatRoomInvitation -> "chatRoomInvitationReceived"
        NotificationType.AchievementEarned -> "achievementEarned"
        NotificationType.ExportCompleted -> "exportCompleted"
        NotificationType.ImportCompleted -> "importCompleted"
        NotificationType.Login -> "login"
        NotificationType.CreateToken -> "createToken"
        NotificationType.App -> "app"
        NotificationType.Edited -> "edited"
        NotificationType.ScheduledNoteFailed -> "scheduledNoteFailed"
        NotificationType.ScheduledNotePosted -> "scheduledNotePosted"
        NotificationType.SharedAccessGranted -> "sharedAccessGranted"
        NotificationType.SharedAccessRevoked -> "sharedAccessRevoked"
        NotificationType.SharedAccessLogin -> "sharedAccessLogin"
        NotificationType.ReactionGrouped -> "reaction:grouped"
        NotificationType.RenoteGrouped -> "renote:grouped"
        NotificationType.Test -> "test"
        NotificationType.Unknown -> null
    }
}

private fun NotificationType.message(reaction: String?): String {
    return when (this) {
        NotificationType.Note -> "发布了新帖子"
        NotificationType.Reply -> "回复了你的帖子"
        NotificationType.Mention -> "提到了你"
        NotificationType.Reaction -> "对你的帖子做出了反应" + reaction?.let { " $it" }.orEmpty()
        NotificationType.Follow -> "关注了你"
        NotificationType.Renote -> "转发了你的帖子"
        NotificationType.Quote -> "引用了你的帖子"
        NotificationType.PollEnded -> "投票已结束"
        NotificationType.FollowRequestReceived -> "请求关注你"
        NotificationType.FollowRequestAccepted -> "接受了你的关注请求"
        NotificationType.RoleAssigned -> "获得了新角色"
        NotificationType.ChatRoomInvitation -> "邀请你加入聊天室"
        NotificationType.AchievementEarned -> "达成了成就"
        NotificationType.ExportCompleted -> "导出已完成"
        NotificationType.ImportCompleted -> "导入已完成"
        NotificationType.Login -> "账号已登录"
        NotificationType.CreateToken -> "创建了访问令牌"
        NotificationType.App -> "应用通知"
        NotificationType.Edited -> "编辑了帖子"
        NotificationType.ScheduledNoteFailed -> "定时帖子发布失败"
        NotificationType.ScheduledNotePosted -> "定时帖子已发布"
        NotificationType.SharedAccessGranted -> "授予了共享访问权限"
        NotificationType.SharedAccessRevoked -> "撤销了共享访问权限"
        NotificationType.SharedAccessLogin -> "通过共享访问登录"
        NotificationType.ReactionGrouped -> "多人对你的帖子做出了反应"
        NotificationType.RenoteGrouped -> "多人转发了你的帖子"
        NotificationType.Test -> "测试通知"
        NotificationType.Unknown -> "通知"
    }
}


private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}

private fun defaultNotificationClient(): HttpClient {
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
