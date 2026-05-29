package cc.hhhl.client.automation

import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.NotificationRepository
import cc.hhhl.client.repository.NotificationRepositoryResult
import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeResult
import cc.hhhl.client.ai.NoopAiBridge
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AppAutomationActionExecutor(
    private val chatRepository: ChatRepository,
    private val notificationRepository: NotificationRepository,
    private val systemNotificationPublisher: ((String, String) -> Boolean?)? = null,
    private val aiBridge: AiBridge = NoopAiBridge,
    private val httpClient: HttpClient = defaultAutomationHttpClient(),
) : AutomationActionExecutor {
    override suspend fun execute(
        action: AutomationAction,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        return when (action.type) {
            AutomationActionType.AddLog -> AutomationActionExecutionResult(true, body)
            AutomationActionType.SystemNotification -> createNotification(title, body)
            AutomationActionType.ForwardToRoom -> forwardToRoom(action.targetId, body)
            AutomationActionType.ForwardToUser -> forwardToUser(action.targetId, body)
            AutomationActionType.Webhook -> sendWebhook(action.targetId, event, title, body)
            AutomationActionType.AiGenerateLog -> generateAiLog(action.bodyTemplate, event)
            AutomationActionType.AiGenerateNotification -> generateAiNotification(title, action.bodyTemplate, event)
            AutomationActionType.AiGenerateWebhook -> generateAiWebhook(action.targetId, action.bodyTemplate, event, title)
        }
    }

    private suspend fun generateAiLog(
        prompt: String,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(prompt, event.aiContextText())) {
            is AiBridgeResult.Success -> AutomationActionExecutionResult(true, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAiNotification(
        title: String,
        prompt: String,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(prompt, event.aiContextText())) {
            is AiBridgeResult.Success -> createNotification(title, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAiWebhook(
        url: String,
        prompt: String,
        event: AutomationEvent,
        title: String,
    ): AutomationActionExecutionResult {
        return when (val result = aiBridge.generateAutomationText(prompt, event.aiContextText())) {
            is AiBridgeResult.Success -> sendWebhook(url, event, title, result.text)
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun createNotification(
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        systemNotificationPublisher?.let { publisher ->
            val platformResult = runCatching {
                val published = publisher(title, body)
                published?.let {
                    AutomationActionExecutionResult(
                        success = it,
                        message = if (it) "已发送系统通知" else "系统通知权限未开启或通知发送失败",
                    )
                }
            }.getOrElse { error ->
                AutomationActionExecutionResult(false, error.message ?: "系统通知发送失败")
            }
            if (platformResult != null) return platformResult
        }
        return when (val result = notificationRepository.createNotification(body = body, header = title)) {
            NotificationRepositoryResult.ActionSuccess -> AutomationActionExecutionResult(true, "已发送系统通知")
            NotificationRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法发送通知")
            is NotificationRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            NotificationRepositoryResult.AllRead,
            is NotificationRepositoryResult.Success,
                -> AutomationActionExecutionResult(true, "已发送系统通知")
        }
    }

    private suspend fun forwardToRoom(
        roomId: String,
        body: String,
    ): AutomationActionExecutionResult {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isBlank()) return AutomationActionExecutionResult(false, "聊天室 ID 不能为空")
        return when (val result = chatRepository.sendMessage(roomId = cleanRoomId, text = body)) {
            is ChatMessageRepositoryResult.Created -> AutomationActionExecutionResult(true, "已转发到聊天室")
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发")
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, "已转发到聊天室")
        }
    }

    private suspend fun forwardToUser(
        userId: String,
        body: String,
    ): AutomationActionExecutionResult {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return AutomationActionExecutionResult(false, "用户 ID 不能为空")
        return when (val result = chatRepository.sendUserMessage(userId = cleanUserId, text = body)) {
            is ChatMessageRepositoryResult.Created -> AutomationActionExecutionResult(true, "已转发给用户")
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发")
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, "已转发给用户")
        }
    }

    private suspend fun sendWebhook(
        url: String,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) {
            return AutomationActionExecutionResult(false, "Webhook URL 必须以 http:// 或 https:// 开头")
        }
        return runCatching {
            val response = httpClient.post(cleanUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(AutomationWebhookPayload.from(event, title, body))
            }
            if (response.status.value in 200..299) {
                AutomationActionExecutionResult(true, "Webhook 已发送 (${response.status.value})")
            } else {
                val responseBody = runCatching { response.body<String>() }.getOrDefault("").take(160)
                AutomationActionExecutionResult(
                    success = false,
                    message = "Webhook 返回 ${response.status.value}${responseBody.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                )
            }
        }.getOrElse { error ->
            AutomationActionExecutionResult(false, error.message ?: "Webhook 发送失败")
        }
    }
}

@Serializable
private data class AutomationWebhookPayload(
    val title: String,
    val body: String,
    val eventId: String,
    val eventKind: String,
    val eventLabel: String,
    val sourceKind: String,
    val senderUserId: String,
    val senderName: String,
    val roomId: String,
    val directUserId: String,
    val messageText: String,
    val attentionKind: String,
    val notificationType: String,
    val notificationText: String,
    val noteId: String,
    val createdAt: String,
    val variables: JsonObject,
) {
    companion object {
        fun from(
            event: AutomationEvent,
            title: String,
            body: String,
        ): AutomationWebhookPayload {
            val variables = listOf(
                "event.id",
                "event.kind",
                "event.label",
                "source.kind",
                "sender.id",
                "sender.name",
                "room.id",
                "direct.user.id",
                "message.text",
                "attention.kind",
                "notification.type",
                "notification.text",
                "note.id",
                "createdAt",
            ).associateWith { name -> JsonPrimitive(event.variable(name)) }
            return AutomationWebhookPayload(
                title = title,
                body = body,
                eventId = event.id,
                eventKind = event.trigger.name,
                eventLabel = event.displayLabel,
                sourceKind = event.sourceKind,
                senderUserId = event.senderUserId,
                senderName = event.senderName,
                roomId = event.roomId,
                directUserId = event.directUserId,
                messageText = event.messageText,
                attentionKind = event.attentionKind,
                notificationType = event.notificationType,
                notificationText = event.notificationText,
                noteId = event.noteId,
                createdAt = event.createdAtLabel,
                variables = JsonObject(variables),
            )
        }
    }
}

private fun defaultAutomationHttpClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 10_000
        }
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
