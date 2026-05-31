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
    val trigger = root.enumValue("trigger", AutomationTrigger.entries)
        ?: return AutomationRuleDraftParseResult(null, "AI 规则缺少有效触发器")
    val now = Clock.System.now().toEpochMilliseconds()
    val conditions = root["conditions"]?.jsonArray.orEmpty()
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val inferred = item.inferredCondition()
            val type = item.enumValue("type", AutomationConditionType.entries) ?: inferred?.first ?: return@mapNotNull null
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
            val type = item.enumValue("type", AutomationActionType.entries) ?: return@mapNotNull null
            AutomationAction(
                id = "action-draft-$now-${type.name}-${item.stringValue("bodyTemplate").hashCode()}",
                type = type,
                enabled = item.booleanValue("enabled", default = true),
                targetId = item.stringValue("targetId"),
                titleTemplate = item.stringValue("titleTemplate"),
                bodyTemplate = item.stringValue("bodyTemplate").ifBlank { defaultDraftBodyTemplate(type) },
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
    return if (actions.any { it.type.requiresCautiousConfirmation() }) 300 else 60
}

private fun defaultBurstLimit(actions: List<AutomationAction>): Int {
    return if (actions.any { it.type.requiresCautiousConfirmation() }) 2 else 12
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

private fun AutomationActionType.requiresCautiousConfirmation(): Boolean {
    return this != AutomationActionType.AddLog &&
        this != AutomationActionType.SystemNotification &&
        this != AutomationActionType.AiGenerateLog &&
        this != AutomationActionType.AiGenerateNotification
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

private fun JsonObject.booleanValue(key: String, default: Boolean): Boolean {
    return this[key]?.jsonPrimitive?.booleanOrNull ?: default
}

private fun JsonObject.intValue(key: String, default: Int): Int {
    return this[key]?.jsonPrimitive?.intOrNull ?: default
}

private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String, values: List<T>): T? {
    val clean = stringValue(key)
    return values.firstOrNull { it.name.equals(clean, ignoreCase = true) }
}

private val automationRuleDraftJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
