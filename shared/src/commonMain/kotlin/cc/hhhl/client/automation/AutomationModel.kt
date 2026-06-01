package cc.hhhl.client.automation

import androidx.compose.runtime.Immutable
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.ai.aiSemanticYes
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.User
import cc.hhhl.client.state.ChatAttentionKind
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
    val maxExecutionsPer30Seconds: Int = 0,
    val updatedAtEpochMillis: Long = 0L,
)

@Serializable
enum class AutomationTrigger(
    val label: String,
    val description: String,
) {
    ChatMessage("聊天消息", "聊天室或私聊收到新消息"),
    ChatAttention("聊天提醒", "@ 你、回复、引用或特别关心消息"),
    TimelineNote("帖子", "时间线或频道里出现新帖子"),
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
    SenderUserIds("发送者 ID 列表", "多个用户 ID，用逗号或换行分隔"),
    SenderUsername("发送者用户名", "用户名或 @acct，精确匹配"),
    SenderNameContains("发送者名称", "显示名或用户名包含"),
    MessageContains("消息包含", "文本关键词"),
    RoomId("聊天室 ID", "roomId，精确匹配"),
    RoomNameContains("聊天室名称", "聊天室名称包含"),
    DirectUserId("私聊用户 ID", "私聊对方用户 ID"),
    SourceKind("来源", "room 或 direct"),
    AttentionKind("提醒类型", "specialCare / mention / reply / quote"),
    NotificationType("通知类型", "Reaction / Reply / Follow 等"),
    ChannelId("频道 ID", "频道 ID，精确匹配"),
    ChannelNameContains("频道名称", "频道名称包含"),
    MessageType("消息类型", "text / file / image / reply / quote / poll"),
    TimelineKind("时间线来源", "Home / Social / Local / Global / Channel"),
    NoteVisibility("帖子可见性", "Public / Home / Followers / Specified"),
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
    val mentionSender: Boolean = false,
    val replyToEvent: Boolean = false,
    val quoteEvent: Boolean = false,
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
    AiForwardToRoom("AI 改写转发到聊天室", "让 AI 摘要、翻译、提取重点或按模板改写后转发", "聊天室 ID", "AI 改写要求"),
    ForwardToUser("转发给用户", "把事件内容转发给指定用户", "用户 ID", "转发内容"),
    ReplyToChat("回复聊天", "在触发消息所在会话自动发送回复", "目标会话（空为原会话，room:ID 或 user:ID）", "回复内容"),
    AiReplyToChat("AI 回复聊天", "让 AI 判断上下文并在触发会话自动回复", "目标会话（空为原会话，room:ID 或 user:ID）", "AI 回复要求"),
    ReplyToNote("回复帖子", "对触发事件里的帖子自动发送回复", "帖子 ID（空为事件帖子）", "回复内容"),
    AiReplyToNote("AI 回复帖子", "让 AI 根据事件生成回复并发送到帖子下", "帖子 ID（空为事件帖子）", "AI 回复要求"),
    QuoteNote("引用帖子", "带文字引用触发事件里的帖子", "帖子 ID（空为事件帖子）", "引用正文"),
    AiQuoteNote("AI 引用帖子", "让 AI 生成引用正文并引用帖子", "帖子 ID（空为事件帖子）", "AI 引用要求"),
    RenoteNote("转发帖子", "直接转发触发事件里的帖子", "帖子 ID（空为事件帖子）", "备注（可留空）"),
    PostToChannel("频道发帖", "自动向指定频道发布一条帖子", "频道 ID", "帖子正文"),
    CopyChannelLink("复制频道链接", "把指定频道链接写入剪贴板", "频道 ID 或链接", "备注（可留空）"),
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
    val eventSnapshot: AutomationEventSnapshot? = null,
    val actionSnapshot: AutomationAction? = null,
    val retryOfLogId: String = "",
)

@Serializable
data class AutomationEventSnapshot(
    val id: String,
    val trigger: AutomationTrigger,
    val chatMessageId: String = "",
    val sourceKind: String = "",
    val senderUserId: String = "",
    val senderUsername: String = "",
    val senderHost: String = "",
    val senderName: String = "",
    val roomId: String = "",
    val roomName: String = "",
    val directUserId: String = "",
    val messageText: String = "",
    val messageType: String = "",
    val attentionKind: String = "",
    val notificationType: String = "",
    val notificationText: String = "",
    val noteId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val timelineKind: String = "",
    val noteVisibility: String = "",
    val createdAtLabel: String = "",
    val createdAtEpochMillis: Long = 0L,
    val isFromCurrentUser: Boolean = false,
    val isAiGenerated: Boolean = false,
    val attachments: List<AutomationAttachment> = emptyList(),
) {
    fun toEvent(): AutomationEvent {
        return AutomationEvent(
            id = id,
            trigger = trigger,
            chatMessageId = chatMessageId,
            sourceKind = sourceKind,
            senderUserId = senderUserId,
            senderUsername = senderUsername,
            senderHost = senderHost,
            senderName = senderName,
            roomId = roomId,
            roomName = roomName,
            directUserId = directUserId,
            messageText = messageText,
            messageType = messageType,
            attentionKind = attentionKind,
            notificationType = notificationType,
            notificationText = notificationText,
            noteId = noteId,
            channelId = channelId,
            channelName = channelName,
            timelineKind = timelineKind,
            noteVisibility = noteVisibility,
            createdAtLabel = createdAtLabel,
            createdAtEpochMillis = createdAtEpochMillis,
            isFromCurrentUser = isFromCurrentUser,
            isAiGenerated = isAiGenerated,
            attachments = attachments,
        )
    }
}

@Immutable
@Serializable
data class AutomationAttachment(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val url: String = "",
    val thumbnailUrl: String = "",
    val description: String = "",
    val size: Long = 0L,
    val isSensitive: Boolean = false,
)

@Immutable
@Serializable
data class AutomationConditionDebugResult(
    val conditionId: String,
    val conditionLabel: String,
    val expectedValue: String,
    val actualValue: String,
    val matched: Boolean,
    val message: String,
)

@Immutable
@Serializable
data class AutomationRuleDebugRecord(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val eventId: String,
    val eventLabel: String,
    val eventSummary: String,
    val matched: Boolean,
    val reason: String,
    val conditionResults: List<AutomationConditionDebugResult> = emptyList(),
    val resolvedEntities: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Immutable
data class AutomationRuleDraftPreview(
    val id: String,
    val sourceText: String,
    val rule: AutomationRule,
    val messages: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Immutable
data class AutomationEvent(
    val id: String,
    val trigger: AutomationTrigger,
    val chatMessageId: String = "",
    val sourceKind: String = "",
    val senderUserId: String = "",
    val senderUsername: String = "",
    val senderHost: String = "",
    val senderName: String = "",
    val roomId: String = "",
    val roomName: String = "",
    val directUserId: String = "",
    val messageText: String = "",
    val messageType: String = "",
    val attentionKind: String = "",
    val notificationType: String = "",
    val notificationText: String = "",
    val noteId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val timelineKind: String = "",
    val noteVisibility: String = "",
    val createdAtLabel: String = "",
    val createdAtEpochMillis: Long = 0L,
    val isFromCurrentUser: Boolean = false,
    val isAiGenerated: Boolean = false,
    val attachments: List<AutomationAttachment> = emptyList(),
) {
    val displayLabel: String
        get() = when (trigger) {
            AutomationTrigger.ChatMessage -> if (sourceKind == "direct") "私聊消息" else "聊天室消息"
            AutomationTrigger.ChatAttention -> attentionKind.ifBlank { "聊天提醒" }
            AutomationTrigger.TimelineNote -> timelineKind.ifBlank { "帖子" }
            AutomationTrigger.Notification -> notificationType.ifBlank { "通知" }
            AutomationTrigger.SpecialCare -> "特别关心"
        }

    val defaultBody: String
        get() = messageText.ifBlank { notificationText.ifBlank { displayLabel } }

    val senderMention: String
        get() = senderUsername.trim().takeIf { it.isNotBlank() }
            ?.let { username -> "@$username" + senderHost.trim().takeIf { host -> host.isNotBlank() }?.let { host -> "@$host" }.orEmpty() }
            .orEmpty()

    fun variable(name: String): String {
        return when (name) {
            "event.id" -> id
            "event.kind" -> trigger.name
            "event.label" -> displayLabel
            "message.id" -> chatMessageId
            "source.kind" -> sourceKind
            "sender.id" -> senderUserId
            "sender.username" -> senderUsername
            "sender.host" -> senderHost
            "sender.name" -> senderName
            "sender.mention" -> senderMention
            "room.id" -> roomId
            "room.name" -> roomName
            "direct.user.id" -> directUserId
            "message.text" -> messageText
            "message.type" -> messageType
            "attachment.count" -> attachments.size.toString()
            "attachment.id" -> attachments.firstOrNull()?.id.orEmpty()
            "attachment.name" -> attachments.firstOrNull()?.name.orEmpty()
            "attachment.type" -> attachments.firstOrNull()?.type.orEmpty()
            "attachment.url" -> attachments.firstOrNull()?.url.orEmpty()
            "attachment.thumbnailUrl" -> attachments.firstOrNull()?.thumbnailUrl.orEmpty()
            "attachment.description" -> attachments.firstOrNull()?.description.orEmpty()
            "attachment.size" -> attachments.firstOrNull()?.size?.takeIf { it > 0L }?.toString().orEmpty()
            "attachment.isSensitive" -> attachments.firstOrNull()?.isSensitive?.toString().orEmpty()
            "attention.kind" -> attentionKind
            "notification.type" -> notificationType
            "notification.text" -> notificationText
            "note.id" -> noteId
            "note.link" -> noteId.takeIf { it.isNotBlank() }?.let { "$DEFAULT_LOCAL_BASE_URL/notes/$it" }.orEmpty()
            "channel.id" -> channelId
            "channel.name" -> channelName
            "channel.link" -> channelId.takeIf { it.isNotBlank() }?.let { "$DEFAULT_LOCAL_BASE_URL/channels/$it" }.orEmpty()
            "timeline.kind" -> timelineKind
            "note.visibility" -> noteVisibility
            "createdAt" -> createdAtLabel
            "event.body" -> defaultBody
            else -> ""
        }
    }

    fun aiContextText(): String {
        return buildString {
            appendLine("事件：$displayLabel")
            if (sourceKind.isNotBlank()) appendLine("来源：$sourceKind")
            if (chatMessageId.isNotBlank()) appendLine("聊天消息：$chatMessageId")
            if (senderName.isNotBlank() || senderUserId.isNotBlank()) appendLine("发送者：$senderName ($senderUserId)")
            if (senderUsername.isNotBlank()) appendLine("发送者用户名：${senderMention.ifBlank { senderUsername }}")
            if (roomId.isNotBlank() || roomName.isNotBlank()) appendLine("聊天室：${roomName.ifBlank { roomId }} ($roomId)")
            if (directUserId.isNotBlank()) appendLine("私聊用户：$directUserId")
            if (messageType.isNotBlank()) appendLine("消息类型：$messageType")
            if (attentionKind.isNotBlank()) appendLine("提醒类型：$attentionKind")
            if (notificationType.isNotBlank()) appendLine("通知类型：$notificationType")
            if (noteId.isNotBlank()) appendLine("帖子：$noteId")
            if (channelId.isNotBlank() || channelName.isNotBlank()) appendLine("频道：${channelName.ifBlank { channelId }} ($channelId)")
            if (timelineKind.isNotBlank()) appendLine("时间线：$timelineKind")
            if (noteVisibility.isNotBlank()) appendLine("可见性：$noteVisibility")
            if (createdAtLabel.isNotBlank()) appendLine("时间：$createdAtLabel")
            if (notificationText.isNotBlank()) appendLine("通知：$notificationText")
            if (messageText.isNotBlank()) appendLine("正文：$messageText")
            if (attachments.isNotEmpty()) {
                appendLine("附件：")
                attachments.forEachIndexed { index, attachment ->
                    appendLine(
                        "${index + 1}. ${attachment.name.ifBlank { attachment.id.ifBlank { "附件" } }}" +
                            " ${attachment.type}".trimEnd() +
                            attachment.url.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
                    )
                }
            }
        }.trim()
    }

    fun snapshot(): AutomationEventSnapshot {
        return AutomationEventSnapshot(
            id = id,
            trigger = trigger,
            chatMessageId = chatMessageId,
            sourceKind = sourceKind,
            senderUserId = senderUserId,
            senderUsername = senderUsername,
            senderHost = senderHost,
            senderName = senderName,
            roomId = roomId,
            roomName = roomName,
            directUserId = directUserId,
            messageText = messageText,
            messageType = messageType,
            attentionKind = attentionKind,
            notificationType = notificationType,
            notificationText = notificationText,
            noteId = noteId,
            channelId = channelId,
            channelName = channelName,
            timelineKind = timelineKind,
            noteVisibility = noteVisibility,
            createdAtLabel = createdAtLabel,
            createdAtEpochMillis = createdAtEpochMillis,
            isFromCurrentUser = isFromCurrentUser,
            isAiGenerated = isAiGenerated,
            attachments = attachments,
        )
    }
}

fun ChatMessage.toAutomationChatEvent(
    roomId: String,
    roomName: String = "",
    directUserId: String? = null,
    attentionKind: String = "",
    currentUser: User? = null,
    isAiGenerated: Boolean = false,
): AutomationEvent {
    val currentUserId = currentUser?.id?.trim().orEmpty()
    val cleanDirectUserId = directUserId?.trim().orEmpty()
    return AutomationEvent(
        id = "chat-message:${id.ifBlank { createdAt.ifBlank { createdAtLabel } }}",
        trigger = if (attentionKind.isBlank()) AutomationTrigger.ChatMessage else AutomationTrigger.ChatAttention,
        chatMessageId = id,
        sourceKind = if (cleanDirectUserId.isBlank()) "room" else "direct",
        senderUserId = fromUser.id,
        senderUsername = fromUser.username,
        senderHost = fromUser.host.orEmpty(),
        senderName = fromUser.displayName.ifBlank { fromUser.username },
        roomId = roomId,
        roomName = roomName,
        directUserId = cleanDirectUserId,
        messageText = text,
        messageType = automationChatMessageTypes().joinToString(","),
        attachments = listOfNotNull(file?.toAutomationAttachment()),
        attentionKind = attentionKind,
        createdAtLabel = createdAtLabel,
        isFromCurrentUser = currentUserId.isNotBlank() && fromUser.id == currentUserId,
        isAiGenerated = isAiGenerated,
    )
}

fun ChatMessage.automationChatAttentionKind(
    currentUser: User?,
    specialCareUserIds: Set<String> = emptySet(),
): ChatAttentionKind? {
    val currentUserId = currentUser?.id?.trim().orEmpty()
    val isFromSelf = currentUserId.isNotEmpty() && fromUser.id == currentUserId
    return when {
        !isFromSelf && currentUserId.isNotEmpty() && text.mentionsAutomationChatUser(currentUser) -> ChatAttentionKind.Mention
        !isFromSelf && currentUserId.isNotEmpty() && reply?.fromUser?.id == currentUserId -> ChatAttentionKind.Reply
        !isFromSelf && currentUserId.isNotEmpty() && quote?.fromUser?.id == currentUserId -> ChatAttentionKind.Quote
        fromUser.id in specialCareUserIds -> ChatAttentionKind.SpecialCare
        else -> null
    }
}

fun NotificationItem.toAutomationNotificationEvent(): AutomationEvent {
    return AutomationEvent(
        id = "notification:$id",
        trigger = if (isSpecialCare) AutomationTrigger.SpecialCare else AutomationTrigger.Notification,
        chatMessageId = chatMessageId.orEmpty(),
        senderUserId = actor.id,
        senderUsername = actor.username,
        senderHost = actor.host.orEmpty(),
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

fun Note.toAutomationTimelineEvent(
    kind: TimelineKind,
    channelName: String = "",
    timelineSource: String = kind.name,
    currentUser: User? = null,
    isAiGenerated: Boolean = false,
): AutomationEvent {
    val currentUserId = currentUser?.id?.trim().orEmpty()
    return AutomationEvent(
        id = "timeline-note:${timelineSource.ifBlank { kind.name }}:${id.ifBlank { createdAt.ifBlank { createdAtLabel } }}",
        trigger = AutomationTrigger.TimelineNote,
        senderUserId = author.id,
        senderUsername = author.username,
        senderHost = author.host.orEmpty(),
        senderName = author.displayName.ifBlank { author.username },
        messageText = text,
        messageType = automationNoteMessageTypes().joinToString(","),
        attachments = media.map { it.toAutomationAttachment() },
        noteId = id,
        channelId = channelId,
        channelName = channelName.ifBlank { this.channelName },
        timelineKind = timelineSource.ifBlank { kind.name },
        noteVisibility = visibility.name,
        createdAtLabel = createdAtLabel,
        isFromCurrentUser = currentUserId.isNotBlank() && author.id == currentUserId,
        isAiGenerated = isAiGenerated,
    )
}

private fun DriveFile.toAutomationAttachment(): AutomationAttachment {
    return AutomationAttachment(
        id = id,
        name = name,
        type = type,
        url = url.orEmpty(),
        thumbnailUrl = thumbnailUrl.orEmpty(),
        description = comment.orEmpty(),
        size = size,
        isSensitive = isSensitive,
    )
}

private fun NoteMedia.toAutomationAttachment(): AutomationAttachment {
    return AutomationAttachment(
        id = id,
        name = description,
        type = type,
        url = url.orEmpty(),
        thumbnailUrl = thumbnailUrl.orEmpty(),
        description = description,
        isSensitive = isSensitive,
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
        AutomationTrigger.TimelineNote -> "{{sender.name}} 发布了帖子：\n{{message.text}}\n{{note.link}}"
        AutomationTrigger.Notification,
        AutomationTrigger.SpecialCare,
            -> "{{sender.name}} · {{notification.text}}\n{{message.text}}"
    }
}

private fun ChatMessage.automationChatMessageTypes(): List<String> {
    return buildList {
        if (text.isNotBlank()) add("text")
        file?.let { file ->
            add("file")
            val type = file.type.lowercase()
            when {
                type.startsWith("image/") -> add("image")
                type.startsWith("video/") -> add("video")
                type.startsWith("audio/") -> add("audio")
            }
        }
        if (reply != null || replyUnavailable) add("reply")
        if (quote != null || quoteUnavailable) add("quote")
    }.ifEmpty { listOf("text") }.distinct()
}

private fun Note.automationNoteMessageTypes(): List<String> {
    return buildList {
        if (text.isNotBlank()) add("text")
        if (media.isNotEmpty()) {
            add("file")
            media.forEach { item ->
                val type = item.type.lowercase()
                when {
                    type.startsWith("image/") -> add("image")
                    type.startsWith("video/") -> add("video")
                    type.startsWith("audio/") -> add("audio")
                }
            }
        }
        if (poll != null) add("poll")
        if (replyId != null) add("reply")
        if (quotedNote != null || isRenote) add("quote")
    }.ifEmpty { listOf("text") }.distinct()
}

private val automationVariablePattern = Regex("\\{\\{([^}]+)\\}\\}")

private const val DEFAULT_LOCAL_BASE_URL = "https://dc.hhhl.cc"

private fun String.mentionsAutomationChatUser(user: User?): Boolean {
    val username = user?.username?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val host = user.host?.trim()?.takeIf { it.isNotEmpty() }
    var index = indexOf('@')
    while (index >= 0 && index < length - 1) {
        val parsed = parseAutomationMentionAt(index)
        if (parsed != null && parsed.username.equals(username, ignoreCase = true)) {
            if (host == null || parsed.host == null || parsed.host.equals(host, ignoreCase = true)) return true
        }
        index = indexOf('@', startIndex = index + 1)
    }
    return false
}

private data class AutomationParsedMention(
    val username: String,
    val host: String?,
)

private fun String.parseAutomationMentionAt(atIndex: Int): AutomationParsedMention? {
    if (atIndex !in indices || this[atIndex] != '@') return null
    val usernameStart = atIndex + 1
    if (usernameStart >= length || !this[usernameStart].isAutomationMentionPart()) return null
    var usernameEnd = usernameStart
    while (usernameEnd < length && this[usernameEnd].isAutomationMentionPart()) usernameEnd++
    val username = substring(usernameStart, usernameEnd)
    var host: String? = null
    if (usernameEnd < length && this[usernameEnd] == '@') {
        var hostEnd = usernameEnd + 1
        while (hostEnd < length && this[hostEnd].isAutomationMentionHostPart()) hostEnd++
        host = substring(usernameEnd + 1, hostEnd).takeIf { it.isNotBlank() }
    }
    return AutomationParsedMention(username, host)
}

private fun Char.isAutomationMentionPart(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '.'
}

private fun Char.isAutomationMentionHostPart(): Boolean {
    return isLetterOrDigit() || this == '-' || this == '.'
}
