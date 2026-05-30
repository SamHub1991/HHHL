package cc.hhhl.client.automation

import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.repository.ChatMessageRepositoryResult
import cc.hhhl.client.repository.ChatRepository
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.ComposeRepositoryResult
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NoteActionRepositoryResult
import cc.hhhl.client.repository.NoteActionRequest
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
    private val composeRepository: ComposeRepository? = null,
    private val noteActionRepository: NoteActionRepository? = null,
    private val clipboardWriter: ((String) -> Boolean?)? = null,
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
            AutomationActionType.ReplyToChat -> replyToChat(action, event, body)
            AutomationActionType.AiReplyToChat -> generateAndReplyToChat(action, event)
            AutomationActionType.ReplyToNote -> replyToNote(action, event, body)
            AutomationActionType.AiReplyToNote -> generateAndReplyToNote(action, event)
            AutomationActionType.QuoteNote -> quoteNote(action, event, body)
            AutomationActionType.AiQuoteNote -> generateAndQuoteNote(action, event)
            AutomationActionType.RenoteNote -> renoteNote(action, event)
            AutomationActionType.PostToChannel -> postToChannel(action, event, body)
            AutomationActionType.CopyChannelLink -> copyChannelLink(action, event)
            AutomationActionType.Webhook -> sendWebhook(action.targetId, event, title, body)
            AutomationActionType.AiGenerateLog -> generateAiLog(action.bodyTemplate, event)
            AutomationActionType.AiGenerateNotification -> generateAiNotification(title, action.bodyTemplate, event)
            AutomationActionType.AiGenerateWebhook -> generateAiWebhook(action.targetId, action.bodyTemplate, event, title)
        }
    }

    private suspend fun generateAndReplyToChat(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "聊天回复")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需回复")
                } else {
                    replyToChat(action, event, generated)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAndReplyToNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "帖子回复")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需回复帖子")
                } else {
                    replyToNote(action, event, generated)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateAndQuoteNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        return when (val result = generateOutgoingText(action.bodyTemplate, event, "引用帖子")) {
            is AiBridgeResult.Success -> {
                val generated = result.text.cleanedOutgoingText()
                if (generated.shouldSkipGeneratedAction()) {
                    AutomationActionExecutionResult(true, "AI 判断无需引用")
                } else {
                    quoteNote(action, event, generated)
                }
            }
            is AiBridgeResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun generateOutgoingText(
        prompt: String,
        event: AutomationEvent,
        actionLabel: String,
    ): AiBridgeResult {
        val fullPrompt = buildString {
            appendLine(prompt.ifBlank { "根据事件生成自然、克制、贴合上下文的$actionLabel。" })
            appendLine("只输出要发送的正文，不要解释，不要加标题。")
            appendLine("如果上下文不该自动执行，输出 SKIP。")
        }.trim()
        return aiBridge.generateAutomationText(fullPrompt, event.aiContextText())
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

    private suspend fun replyToChat(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
    ): AutomationActionExecutionResult {
        val target = resolveChatTarget(action.targetId, event)
            ?: return AutomationActionExecutionResult(false, "找不到可回复的聊天会话")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "回复内容不能为空")
        val sourceMessageId = event.chatMessageId.takeIf { it.isNotBlank() }
        val replyId = sourceMessageId.takeIf { action.replyToEvent }
        val quoteId = sourceMessageId.takeIf { action.quoteEvent }
        return when (target) {
            is AutomationChatTarget.Room -> mapChatSendResult(
                chatRepository.sendMessage(
                    roomId = target.id,
                    text = message,
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已自动回复聊天室",
                unauthorizedMessage = "登录已失效，无法回复聊天室",
            )
            is AutomationChatTarget.User -> mapChatSendResult(
                chatRepository.sendUserMessage(
                    userId = target.id,
                    text = message,
                    replyId = replyId,
                    quoteId = quoteId,
                ),
                successMessage = "已自动回复私聊",
                unauthorizedMessage = "登录已失效，无法回复私聊",
            )
        }
    }

    private suspend fun replyToNote(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "回复内容不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                replyId = noteId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已回复帖子",
        )
    }

    private suspend fun quoteNote(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "引用正文不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                renoteId = noteId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已引用帖子",
        )
    }

    private suspend fun renoteNote(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        val noteId = action.targetId.trim().ifBlank { event.noteId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "帖子 ID 不能为空")
        val repository = noteActionRepository
            ?: return AutomationActionExecutionResult(false, "帖子操作执行器未接入")
        return when (val result = repository.perform(NoteActionRequest.Renote(noteId))) {
            is NoteActionRepositoryResult.Success -> AutomationActionExecutionResult(true, result.message)
            NoteActionRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法转发帖子")
            is NoteActionRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private suspend fun postToChannel(
        action: AutomationAction,
        event: AutomationEvent,
        body: String,
    ): AutomationActionExecutionResult {
        val channelId = action.targetId.trim().ifBlank { event.channelId }.takeIf { it.isNotBlank() }
            ?: return AutomationActionExecutionResult(false, "频道 ID 不能为空")
        val message = body.withOptionalMention(event, action.mentionSender)
        if (message.isBlank()) return AutomationActionExecutionResult(false, "频道帖子正文不能为空")
        return sendComposeDraft(
            ComposeDraft(
                text = message,
                channelId = channelId,
                visibility = NoteVisibility.Public,
            ),
            successMessage = "已发布到频道",
        )
    }

    private fun copyChannelLink(
        action: AutomationAction,
        event: AutomationEvent,
    ): AutomationActionExecutionResult {
        val link = channelLink(action.targetId, event)
            ?: return AutomationActionExecutionResult(false, "频道 ID 或链接不能为空")
        val writer = clipboardWriter
            ?: return AutomationActionExecutionResult(false, "当前运行环境未接入剪贴板，频道链接：$link")
        return runCatching {
            val written = writer(link) ?: true
            AutomationActionExecutionResult(
                success = written,
                message = if (written) "已复制频道链接" else "剪贴板写入失败：$link",
            )
        }.getOrElse { error ->
            AutomationActionExecutionResult(false, error.message ?: "剪贴板写入失败：$link")
        }
    }

    private suspend fun sendComposeDraft(
        draft: ComposeDraft,
        successMessage: String,
    ): AutomationActionExecutionResult {
        val repository = composeRepository
            ?: return AutomationActionExecutionResult(false, "发帖执行器未接入")
        return when (val result = repository.send(draft)) {
            is ComposeRepositoryResult.Success -> AutomationActionExecutionResult(true, successMessage)
            ComposeRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, "登录已失效，无法发帖")
            is ComposeRepositoryResult.ValidationError -> AutomationActionExecutionResult(false, result.message)
            is ComposeRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
        }
    }

    private fun mapChatSendResult(
        result: ChatMessageRepositoryResult,
        successMessage: String,
        unauthorizedMessage: String,
    ): AutomationActionExecutionResult {
        return when (result) {
            is ChatMessageRepositoryResult.Created -> AutomationActionExecutionResult(true, successMessage)
            ChatMessageRepositoryResult.Unauthorized -> AutomationActionExecutionResult(false, unauthorizedMessage)
            is ChatMessageRepositoryResult.Error -> AutomationActionExecutionResult(false, result.message)
            is ChatMessageRepositoryResult.Success,
            is ChatMessageRepositoryResult.Deleted,
            ChatMessageRepositoryResult.ReactionUpdated,
                -> AutomationActionExecutionResult(true, successMessage)
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
    val chatMessageId: String,
    val sourceKind: String,
    val senderUserId: String,
    val senderUsername: String,
    val senderHost: String,
    val senderName: String,
    val roomId: String,
    val directUserId: String,
    val messageText: String,
    val attentionKind: String,
    val notificationType: String,
    val notificationText: String,
    val noteId: String,
    val channelId: String,
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
                "message.id",
                "source.kind",
                "sender.id",
                "sender.username",
                "sender.host",
                "sender.name",
                "sender.mention",
                "room.id",
                "direct.user.id",
                "message.text",
                "attention.kind",
                "notification.type",
                "notification.text",
                "note.id",
                "note.link",
                "channel.id",
                "channel.link",
                "createdAt",
            ).associateWith { name -> JsonPrimitive(event.variable(name)) }
            return AutomationWebhookPayload(
                title = title,
                body = body,
                eventId = event.id,
                eventKind = event.trigger.name,
                eventLabel = event.displayLabel,
                chatMessageId = event.chatMessageId,
                sourceKind = event.sourceKind,
                senderUserId = event.senderUserId,
                senderUsername = event.senderUsername,
                senderHost = event.senderHost,
                senderName = event.senderName,
                roomId = event.roomId,
                directUserId = event.directUserId,
                messageText = event.messageText,
                attentionKind = event.attentionKind,
                notificationType = event.notificationType,
                notificationText = event.notificationText,
                noteId = event.noteId,
                channelId = event.channelId,
                createdAt = event.createdAtLabel,
                variables = JsonObject(variables),
            )
        }
    }
}

private sealed interface AutomationChatTarget {
    val id: String

    data class Room(override val id: String) : AutomationChatTarget
    data class User(override val id: String) : AutomationChatTarget
}

private fun resolveChatTarget(targetId: String, event: AutomationEvent): AutomationChatTarget? {
    val cleanTarget = targetId.trim()
    if (cleanTarget.startsWith("room:", ignoreCase = true)) {
        return cleanTarget.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let { AutomationChatTarget.Room(it) }
    }
    if (cleanTarget.startsWith("user:", ignoreCase = true)) {
        return cleanTarget.substringAfter(':').trim().takeIf { it.isNotBlank() }?.let { AutomationChatTarget.User(it) }
    }
    if (cleanTarget.isNotBlank()) {
        return if (event.sourceKind == "direct") AutomationChatTarget.User(cleanTarget) else AutomationChatTarget.Room(cleanTarget)
    }
    return event.directUserId.takeIf { it.isNotBlank() }?.let { AutomationChatTarget.User(it) }
        ?: event.roomId.takeIf { it.isNotBlank() }?.let { AutomationChatTarget.Room(it) }
}

private fun String.withOptionalMention(event: AutomationEvent, mentionSender: Boolean): String {
    val clean = trim()
    val mention = event.senderMention.takeIf { mentionSender && it.isNotBlank() } ?: return clean
    return if (clean.startsWith(mention)) clean else "$mention $clean".trim()
}

private fun String.cleanedOutgoingText(): String {
    return trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .take(MAX_AUTOMATION_OUTGOING_TEXT)
}

private fun String.shouldSkipGeneratedAction(): Boolean {
    val clean = trim().uppercase()
    return clean == "SKIP" || clean == "NO_REPLY" || clean == "NO" || trim() == "不回复" || trim() == "不执行"
}

private fun channelLink(targetId: String, event: AutomationEvent): String? {
    val clean = targetId.trim().ifBlank { event.channelId }
    if (clean.isBlank()) return null
    if (clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)) return clean
    return "$DEFAULT_LOCAL_BASE_URL/channels/$clean"
}

private const val DEFAULT_LOCAL_BASE_URL = "https://dc.hhhl.cc"
private const val MAX_AUTOMATION_OUTGOING_TEXT = 1600

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
