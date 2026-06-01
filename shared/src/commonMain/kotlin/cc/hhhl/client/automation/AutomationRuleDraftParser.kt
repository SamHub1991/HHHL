package cc.hhhl.client.automation

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AutomationRuleDraftParseResult(
    val rule: AutomationRule?,
    val errorMessage: String? = null,
)

fun parseAutomationRuleDraft(raw: String): AutomationRuleDraftParseResult {
    val clean = raw.trim().trimCodeFence()
    val root = runCatching { automationRuleDraftJson.parseToJsonElement(clean).jsonObject }.getOrNull()
        ?: return AutomationRuleDraftParseResult(null, "AI 没有返回可解析的规则 JSON")
    val trigger = root.triggerValue()
        ?: return AutomationRuleDraftParseResult(null, "AI 规则缺少有效触发器")
    val now = Clock.System.now().toEpochMilliseconds()
    val conditions = root["conditions"]?.jsonArray.orEmpty()
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val inferred = item.inferredCondition()
            val type = item.conditionTypeValue() ?: inferred?.first ?: return@mapNotNull null
            val value = item.stringValue("value").ifBlank { inferred?.second.orEmpty() }
            AutomationCondition(
                id = "condition-draft-$now-${type.name}-${value.hashCode()}",
                type = type,
                value = value,
                enabled = item.booleanValue("enabled", default = true),
            )
        }
        .take(8)
        .ifEmpty { listOf(defaultDraftCondition(trigger, now)) }
    val actions = root["actions"]?.jsonArray.orEmpty()
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val type = item.actionTypeValue() ?: return@mapNotNull null
            AutomationAction(
                id = "action-draft-$now-${type.name}-${item.stringValue("bodyTemplate").hashCode()}",
                type = type,
                enabled = item.booleanValue("enabled", default = true),
                targetId = item.actionTargetValue(type),
                titleTemplate = item.firstStringValue("titleTemplate", "title"),
                bodyTemplate = item.actionBodyTemplateValue(type),
                mentionSender = item.booleanValue("mentionSender", default = false),
                replyToEvent = item.booleanValue("replyToEvent", default = false),
                quoteEvent = item.booleanValue("quoteEvent", default = false),
                failurePolicy = item.enumValue("failurePolicy", AutomationFailurePolicy.entries)
                    ?: AutomationFailurePolicy.Continue,
            )
        }
        .take(6)
        .ifEmpty { listOf(defaultDraftAction(trigger, now)) }

    return AutomationRuleDraftParseResult(
        AutomationRule(
            id = "rule-draft-$now",
            name = root.stringValue("name").ifBlank { "AI 规则草稿" },
            enabled = root.booleanValue("enabled", default = true),
            trigger = trigger,
            conditionMode = root.enumValue("conditionMode", AutomationConditionMode.entries)
                ?: AutomationConditionMode.All,
            conditions = conditions,
            actionMode = root.enumValue("actionMode", AutomationActionMode.entries)
                ?: AutomationActionMode.Sequential,
            actions = actions,
            ignoreOwnMessages = root.booleanValue("ignoreOwnMessages", default = true),
            cooldownSeconds = root.intValue("cooldownSeconds", default = defaultCooldownSeconds(actions)),
            maxExecutionsPer30Seconds = root.intValue("maxExecutionsPer30Seconds", default = defaultBurstLimit(actions)),
            updatedAtEpochMillis = now,
        ),
    )
}

private fun defaultDraftCondition(trigger: AutomationTrigger, now: Long): AutomationCondition {
    return AutomationCondition(
        id = "condition-draft-$now",
        type = when (trigger) {
            AutomationTrigger.ChatMessage -> AutomationConditionType.MessageContains
            AutomationTrigger.ChatAttention -> AutomationConditionType.AttentionKind
            AutomationTrigger.TimelineNote -> AutomationConditionType.MessageContains
            AutomationTrigger.Notification -> AutomationConditionType.NotificationType
            AutomationTrigger.SpecialCare -> AutomationConditionType.AiSemantic
        },
    )
}

private fun defaultDraftAction(trigger: AutomationTrigger, now: Long): AutomationAction {
    return AutomationAction(
        id = "action-draft-$now",
        type = AutomationActionType.AddLog,
        bodyTemplate = defaultAutomationTemplate(AutomationEvent("draft", trigger)),
    )
}

private fun defaultDraftBodyTemplate(type: AutomationActionType): String {
    return when (type) {
        AutomationActionType.SystemNotification -> "{{event.body}}"
        AutomationActionType.AiGenerateLog,
        AutomationActionType.AiGenerateNotification,
        AutomationActionType.AiGenerateWebhook,
        AutomationActionType.AiForwardToRoom,
        AutomationActionType.AiReplyToChat,
        AutomationActionType.AiReplyToNote,
        AutomationActionType.AiQuoteNote -> "根据事件生成简短、克制、可直接使用的内容。"
        else -> "{{event.body}}"
    }
}

private fun defaultCooldownSeconds(actions: List<AutomationAction>): Int {
    return when {
        actions.any { it.type.requiresCautiousConfirmation() && !it.type.isWebhookCallbackAction() } -> 300
        actions.any { it.type.isWebhookCallbackAction() } -> 0
        else -> 60
    }
}

private fun defaultBurstLimit(actions: List<AutomationAction>): Int {
    return when {
        actions.any { it.type.requiresCautiousConfirmation() && !it.type.isWebhookCallbackAction() } -> 2
        actions.any { it.type.isWebhookCallbackAction() } -> 12
        else -> 12
    }
}

private fun JsonObject.inferredCondition(): Pair<AutomationConditionType, String>? {
    val pairs = listOf(
        AutomationConditionType.RoomId to stringValue("roomId"),
        AutomationConditionType.RoomNameContains to stringValue("roomName"),
        AutomationConditionType.SenderUserId to stringValue("userId"),
        AutomationConditionType.SenderUserIds to stringValue("userIds"),
        AutomationConditionType.SenderUsername to stringValue("username"),
        AutomationConditionType.SenderUsername to stringValue("senderUsername"),
        AutomationConditionType.SenderNameContains to stringValue("userName"),
        AutomationConditionType.SenderNameContains to stringValue("senderName"),
        AutomationConditionType.SenderNameContains to stringValue("userNames"),
        AutomationConditionType.ChannelId to stringValue("channelId"),
        AutomationConditionType.ChannelNameContains to stringValue("channelName"),
        AutomationConditionType.MessageType to stringValue("messageType"),
        AutomationConditionType.TimelineKind to stringValue("timelineKind"),
        AutomationConditionType.NoteVisibility to stringValue("visibility"),
        AutomationConditionType.NotificationType to stringValue("notificationType"),
        AutomationConditionType.AttentionKind to stringValue("attentionKind"),
        AutomationConditionType.SourceKind to stringValue("sourceKind"),
        AutomationConditionType.MessageContains to stringValue("messageContains"),
    )
    return pairs.firstOrNull { (_, value) -> value.isNotBlank() }
}

private fun JsonObject.triggerValue(): AutomationTrigger? {
    val clean = firstStringValue("trigger", "triggerType", "kind")
    enumFromText(clean, AutomationTrigger.entries)?.let { return it }
    return when {
        clean.contains("特别关心") -> AutomationTrigger.SpecialCare
        clean.contains("@") || clean.contains("提醒") -> AutomationTrigger.ChatAttention
        clean.contains("通知") -> AutomationTrigger.Notification
        clean.contains("帖子") || clean.contains("时间线") || clean.contains("频道") -> AutomationTrigger.TimelineNote
        clean.contains("聊天") || clean.contains("聊天室") || clean.contains("私聊") || clean.contains("消息") -> AutomationTrigger.ChatMessage
        else -> null
    }
}

private fun JsonObject.conditionTypeValue(): AutomationConditionType? {
    val clean = firstStringValue("type", "conditionType", "condition", "kind")
    enumFromText(clean, AutomationConditionType.entries)?.let { return it }
    return when {
        clean.contains("发送者") && clean.contains("ID", ignoreCase = true) -> AutomationConditionType.SenderUserId
        clean.contains("发送者") || clean.contains("用户") -> AutomationConditionType.SenderNameContains
        clean.contains("消息") || clean.contains("正文") || clean.contains("内容") -> AutomationConditionType.MessageContains
        clean.contains("聊天室") && clean.contains("ID", ignoreCase = true) -> AutomationConditionType.RoomId
        clean.contains("聊天室") -> AutomationConditionType.RoomNameContains
        clean.contains("私聊") -> AutomationConditionType.DirectUserId
        clean.contains("来源") -> AutomationConditionType.SourceKind
        clean.contains("提醒") -> AutomationConditionType.AttentionKind
        clean.contains("通知") -> AutomationConditionType.NotificationType
        clean.contains("频道") && clean.contains("ID", ignoreCase = true) -> AutomationConditionType.ChannelId
        clean.contains("频道") -> AutomationConditionType.ChannelNameContains
        clean.contains("类型") -> AutomationConditionType.MessageType
        clean.contains("语义") || clean.contains("AI", ignoreCase = true) -> AutomationConditionType.AiSemantic
        else -> null
    }
}

private fun JsonObject.actionTypeValue(): AutomationActionType? {
    val clean = firstStringValue("type", "actionType", "action", "kind")
    enumFromText(clean, AutomationActionType.entries)?.let { return it }
    val lower = clean.lowercase()
    return when {
        (lower.contains("ai") || clean.contains("提取") || clean.contains("总结")) &&
            (lower.contains("webhook") || clean.contains("回调")) -> AutomationActionType.AiGenerateWebhook
        lower.contains("webhook") || clean.contains("回调") || lower.contains("http") -> AutomationActionType.Webhook
        (lower.contains("ai") || clean.contains("生成")) && clean.contains("通知") -> AutomationActionType.AiGenerateNotification
        clean.contains("系统通知") || clean.contains("通知栏") -> AutomationActionType.SystemNotification
        (lower.contains("ai") || clean.contains("改写") || clean.contains("摘要")) && clean.contains("聊天室") -> AutomationActionType.AiForwardToRoom
        clean.contains("转发") && clean.contains("聊天室") -> AutomationActionType.ForwardToRoom
        clean.contains("转发") && (clean.contains("用户") || clean.contains("私聊")) -> AutomationActionType.ForwardToUser
        (lower.contains("ai") || clean.contains("生成")) && clean.contains("回复") && clean.contains("帖子") -> AutomationActionType.AiReplyToNote
        clean.contains("回复") && clean.contains("帖子") -> AutomationActionType.ReplyToNote
        (lower.contains("ai") || clean.contains("生成")) && clean.contains("回复") -> AutomationActionType.AiReplyToChat
        clean.contains("回复") -> AutomationActionType.ReplyToChat
        (lower.contains("ai") || clean.contains("生成")) && clean.contains("引用") -> AutomationActionType.AiQuoteNote
        clean.contains("引用") -> AutomationActionType.QuoteNote
        clean.contains("频道") && clean.contains("发帖") -> AutomationActionType.PostToChannel
        clean.contains("日志") -> AutomationActionType.AddLog
        else -> null
    }
}

private fun JsonObject.actionTargetValue(type: AutomationActionType): String {
    return when (type) {
        AutomationActionType.Webhook,
        AutomationActionType.AiGenerateWebhook -> firstStringValue(
            "targetId",
            "webhookUrl",
            "webhookURL",
            "url",
            "endpoint",
            "callbackUrl",
            "callbackURL",
            "target",
        )
        AutomationActionType.ForwardToRoom,
        AutomationActionType.AiForwardToRoom -> firstStringValue("targetId", "roomId", "roomName", "targetRoom", "target")
        AutomationActionType.ForwardToUser -> firstStringValue("targetId", "userId", "username", "userName", "targetUser", "target")
        AutomationActionType.ReplyToChat,
        AutomationActionType.AiReplyToChat -> firstStringValue("targetId", "roomId", "roomName", "userId", "userName", "target")
        AutomationActionType.PostToChannel,
        AutomationActionType.CopyChannelLink -> firstStringValue("targetId", "channelId", "channelName", "targetChannel", "target")
        else -> firstStringValue("targetId", "target")
    }
}

private fun JsonObject.actionBodyTemplateValue(type: AutomationActionType): String {
    val clean = when (type) {
        AutomationActionType.AiGenerateLog,
        AutomationActionType.AiGenerateNotification,
        AutomationActionType.AiGenerateWebhook,
        AutomationActionType.AiForwardToRoom,
        AutomationActionType.AiReplyToChat,
        AutomationActionType.AiReplyToNote,
        AutomationActionType.AiQuoteNote -> firstStringValue(
            "bodyTemplate",
            "prompt",
            "instruction",
            "body",
            "template",
            "content",
            "text",
            "message",
            "format",
        )
        else -> firstStringValue(
            "bodyTemplate",
            "template",
            "body",
            "content",
            "text",
            "message",
            "format",
            "prompt",
            "instruction",
        )
    }
    return clean.ifBlank { defaultDraftBodyTemplate(type) }
}

private fun AutomationActionType.requiresCautiousConfirmation(): Boolean {
    return this != AutomationActionType.AddLog &&
        this != AutomationActionType.SystemNotification &&
        this != AutomationActionType.AiGenerateLog &&
        this != AutomationActionType.AiGenerateNotification
}

private fun AutomationActionType.isWebhookCallbackAction(): Boolean {
    return this == AutomationActionType.Webhook || this == AutomationActionType.AiGenerateWebhook
}

private fun String.trimCodeFence(): String {
    val clean = trim()
    if (!clean.startsWith("```")) return clean
    return clean.lines()
        .drop(1)
        .dropLastWhile { it.trim().startsWith("```") || it.isBlank() }
        .joinToString("\n")
        .trim()
}

private fun JsonObject.stringValue(key: String): String {
    return this[key]?.jsonPrimitive?.content.orEmpty().trim()
}

private fun JsonObject.firstStringValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        stringValue(key).takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JsonObject.booleanValue(key: String, default: Boolean): Boolean {
    return this[key]?.jsonPrimitive?.booleanOrNull ?: default
}

private fun JsonObject.intValue(key: String, default: Int): Int {
    return this[key]?.jsonPrimitive?.intOrNull ?: default
}

private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String, values: List<T>): T? {
    val clean = stringValue(key)
    return enumFromText(clean, values)
}

private fun <T : Enum<T>> enumFromText(text: String, values: List<T>): T? {
    val clean = text.trim()
    if (clean.isBlank()) return null
    val normalized = clean.normalizedEnumText()
    return values.firstOrNull { value ->
        value.name.equals(clean, ignoreCase = true) ||
            value.name.normalizedEnumText() == normalized ||
            value.localizedLabel().normalizedEnumText() == normalized
    }
}

private fun Enum<*>.localizedLabel(): String {
    return when (this) {
        is AutomationTrigger -> label
        is AutomationConditionMode -> label
        is AutomationActionMode -> label
        is AutomationConditionType -> label
        is AutomationActionType -> label
        is AutomationFailurePolicy -> label
        else -> ""
    }
}

private fun String.normalizedEnumText(): String {
    return trim()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")
        .lowercase()
}

private val automationRuleDraftJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
