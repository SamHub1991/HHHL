package cc.hhhl.client.automation

import androidx.compose.runtime.Immutable
import cc.hhhl.client.ai.aiSemanticYes
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.User
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val trigger: AutomationTrigger = AutomationTrigger.ChatMessage,
    val conditionMode: AutomationConditionMode = AutomationConditionMode.All,
    val conditions: List<AutomationCondition> = emptyList(),
    val actionMode: AutomationActionMode = AutomationActionMode.Sequential,
    val actions: List<AutomationAction> = emptyList(),
    val ignoreOwnMessages: Boolean = true,
    val cooldownSeconds: Int = 0,
    val updatedAtEpochMillis: Long = 0L,
)

@Serializable
enum class AutomationTrigger(
    val label: String,
    val description: String,
) {
    ChatMessage("聊天消息", "聊天室或私聊收到新消息"),
    ChatAttention("聊天提醒", "@ 你、回复、引用或特别关心消息"),
    Notification("通知", "收到新的通知项"),
    SpecialCare("特别关心", "特别关心用户发帖或发消息"),
}

@Serializable
enum class AutomationConditionMode(val label: String) {
    All("全部满足"),
    Any("任一满足"),
}

@Serializable
enum class AutomationActionMode(val label: String) {
    Sequential("顺序执行"),
    Parallel("并行执行"),
}

@Immutable
@Serializable
data class AutomationCondition(
    val id: String,
    val type: AutomationConditionType,
    val value: String = "",
    val enabled: Boolean = true,
)

@Serializable
enum class AutomationConditionType(
    val label: String,
    val hint: String,
) {
    SenderUserId("发送者 ID", "用户 ID，精确匹配"),
    SenderNameContains("发送者名称", "显示名或用户名包含"),
    MessageContains("消息包含", "文本关键词"),
    RoomId("聊天室 ID", "roomId，精确匹配"),
    DirectUserId("私聊用户 ID", "私聊对方用户 ID"),
    SourceKind("来源", "room 或 direct"),
    AttentionKind("提醒类型", "specialCare / mention / reply / quote"),
    NotificationType("通知类型", "Reaction / Reply / Follow 等"),
    AiSemantic("AI 语义判断", "例如：有人问我问题 / 反馈 bug / 情绪不满"),
}

@Immutable
@Serializable
data class AutomationAction(
    val id: String,
    val type: AutomationActionType,
    val enabled: Boolean = true,
    val targetId: String = "",
    val titleTemplate: String = "",
    val bodyTemplate: String = "",
    val failurePolicy: AutomationFailurePolicy = AutomationFailurePolicy.Continue,
)

@Serializable
enum class AutomationActionType(
    val label: String,
    val description: String,
    val targetLabel: String,
    val bodyLabel: String,
) {
    AddLog("写入日志", "只记录到本地自动化日志", "", "日志内容"),
    SystemNotification("系统通知", "在系统通知栏显示一条提醒", "", "通知正文"),
    ForwardToRoom("转发到聊天室", "把事件内容转发到指定聊天室", "聊天室 ID", "转发内容"),
    ForwardToUser("转发给用户", "把事件内容转发给指定用户", "用户 ID", "转发内容"),
    Webhook("Webhook", "向自定义 URL 发送回调", "Webhook URL", "请求正文"),
    AiGenerateLog("AI 生成日志", "让 AI 根据事件生成本地日志，不会发送到外部用户", "", "AI 要求"),
    AiGenerateNotification("AI 生成通知", "让 AI 生成系统通知正文，不会自动回复或发帖", "", "AI 要求"),
    AiGenerateWebhook("AI 生成 Webhook", "让 AI 根据事件生成 Webhook 正文，再发送到指定 URL", "Webhook URL", "AI 要求"),
}

@Serializable
enum class AutomationFailurePolicy(val label: String) {
    Continue("失败后继续"),
    Stop("失败后停止"),
}

@Immutable
@Serializable
data class AutomationExecutionLog(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val eventId: String,
    val eventLabel: String,
    val actionLabel: String,
    val message: String,
    val success: Boolean,
    val createdAtEpochMillis: Long,
)

@Immutable
data class AutomationEvent(
    val id: String,
    val trigger: AutomationTrigger,
    val sourceKind: String = "",
    val senderUserId: String = "",
    val senderName: String = "",
    val roomId: String = "",
    val directUserId: String = "",
    val messageText: String = "",
    val attentionKind: String = "",
    val notificationType: String = "",
    val notificationText: String = "",
    val noteId: String = "",
    val createdAtLabel: String = "",
    val createdAtEpochMillis: Long = 0L,
    val isFromCurrentUser: Boolean = false,
) {
    val displayLabel: String
        get() = when (trigger) {
            AutomationTrigger.ChatMessage -> if (sourceKind == "direct") "私聊消息" else "聊天室消息"
            AutomationTrigger.ChatAttention -> attentionKind.ifBlank { "聊天提醒" }
            AutomationTrigger.Notification -> notificationType.ifBlank { "通知" }
            AutomationTrigger.SpecialCare -> "特别关心"
        }

    val defaultBody: String
        get() = messageText.ifBlank { notificationText.ifBlank { displayLabel } }

    fun variable(name: String): String {
        return when (name) {
            "event.id" -> id
            "event.kind" -> trigger.name
            "event.label" -> displayLabel
            "source.kind" -> sourceKind
            "sender.id" -> senderUserId
            "sender.name" -> senderName
            "room.id" -> roomId
            "direct.user.id" -> directUserId
            "message.text" -> messageText
            "attention.kind" -> attentionKind
            "notification.type" -> notificationType
            "notification.text" -> notificationText
            "note.id" -> noteId
            "createdAt" -> createdAtLabel
            "event.body" -> defaultBody
            else -> ""
        }
    }

    fun aiContextText(): String {
        return buildString {
            appendLine("事件：$displayLabel")
            if (sourceKind.isNotBlank()) appendLine("来源：$sourceKind")
            if (senderName.isNotBlank() || senderUserId.isNotBlank()) appendLine("发送者：$senderName ($senderUserId)")
            if (roomId.isNotBlank()) appendLine("聊天室：$roomId")
            if (directUserId.isNotBlank()) appendLine("私聊用户：$directUserId")
            if (attentionKind.isNotBlank()) appendLine("提醒类型：$attentionKind")
            if (notificationType.isNotBlank()) appendLine("通知类型：$notificationType")
            if (noteId.isNotBlank()) appendLine("帖子：$noteId")
            if (createdAtLabel.isNotBlank()) appendLine("时间：$createdAtLabel")
            if (notificationText.isNotBlank()) appendLine("通知：$notificationText")
            if (messageText.isNotBlank()) appendLine("正文：$messageText")
        }.trim()
    }
}

fun ChatMessage.toAutomationChatEvent(
    roomId: String,
    directUserId: String? = null,
    attentionKind: String = "",
    currentUser: User? = null,
): AutomationEvent {
    val currentUserId = currentUser?.id?.trim().orEmpty()
    val cleanDirectUserId = directUserId?.trim().orEmpty()
    return AutomationEvent(
        id = "chat-message:${id.ifBlank { createdAt.ifBlank { createdAtLabel } }}",
        trigger = if (attentionKind.isBlank()) AutomationTrigger.ChatMessage else AutomationTrigger.ChatAttention,
        sourceKind = if (cleanDirectUserId.isBlank()) "room" else "direct",
        senderUserId = fromUser.id,
        senderName = fromUser.displayName.ifBlank { fromUser.username },
        roomId = roomId,
        directUserId = cleanDirectUserId,
        messageText = text,
        attentionKind = attentionKind,
        createdAtLabel = createdAtLabel,
        isFromCurrentUser = currentUserId.isNotBlank() && fromUser.id == currentUserId,
    )
}

fun NotificationItem.toAutomationNotificationEvent(): AutomationEvent {
    return AutomationEvent(
        id = "notification:$id",
        trigger = if (isSpecialCare) AutomationTrigger.SpecialCare else AutomationTrigger.Notification,
        senderUserId = actor.id,
        senderName = actor.displayName.ifBlank { actor.username },
        messageText = notePreviewText.orEmpty(),
        notificationType = type.name,
        notificationText = text,
        noteId = noteId.orEmpty(),
        roomId = chatRoomId.orEmpty(),
        directUserId = chatUserId.orEmpty(),
        createdAtLabel = createdAtLabel,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun renderAutomationTemplate(template: String, event: AutomationEvent): String {
    val source = template.ifBlank { defaultAutomationTemplate(event) }
    return automationVariablePattern.replace(source) { match ->
        event.variable(match.groupValues[1].trim())
    }.trim()
}

fun automationAiConditionSatisfied(text: String): Boolean = text.aiSemanticYes()

fun defaultAutomationTemplate(event: AutomationEvent): String {
    return when (event.trigger) {
        AutomationTrigger.ChatMessage,
        AutomationTrigger.ChatAttention,
            -> "来自 {{sender.name}}：\n{{message.text}}"
        AutomationTrigger.Notification,
        AutomationTrigger.SpecialCare,
            -> "{{sender.name}} · {{notification.text}}\n{{message.text}}"
    }
}

private val automationVariablePattern = Regex("\\{\\{([^}]+)\\}\\}")
