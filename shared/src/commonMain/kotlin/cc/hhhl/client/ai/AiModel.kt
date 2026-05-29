package cc.hhhl.client.ai

import androidx.compose.runtime.Immutable
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.User
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AiSettings(
    val enabled: Boolean = false,
    val provider: AiProviderPreset = AiProviderPreset.OpenAiCompatible,
    val baseUrl: String = AiProviderPreset.OpenAiCompatible.defaultBaseUrl,
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val fastModel: String = "gpt-4o-mini",
    val longContextModel: String = "gpt-4o-mini",
    val visionModel: String = "gpt-4o-mini",
    val embeddingModel: String = "text-embedding-3-small",
    val readTimelineAllowed: Boolean = true,
    val readNotificationsAllowed: Boolean = true,
    val readChatAllowed: Boolean = true,
    val readPrivateChatAllowed: Boolean = false,
    val readProfileAllowed: Boolean = true,
    val readDraftsAllowed: Boolean = true,
    val uploadSensitiveContentAllowed: Boolean = false,
    val toolsAllowed: Boolean = false,
    val automationAllowed: Boolean = false,
    val backgroundAllowed: Boolean = true,
    val wifiOnlyBackground: Boolean = false,
    val maxInputChars: Int = 8_000,
    val maxOutputTokens: Int = 600,
    val dailyRequestLimit: Int = 120,
    val tonePreference: String = "自然、简洁、贴近当前语气",
    val systemPrompt: String = DEFAULT_AI_SYSTEM_PROMPT,
) {
    val cleanBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val hasEndpoint: Boolean
        get() = cleanBaseUrl.isNotBlank() && activeChatModel.isNotBlank()

    val activeChatModel: String
        get() = chatModel.trim().ifBlank { fastModel.trim() }

    val supportsCloudAuth: Boolean
        get() = provider != AiProviderPreset.Ollama && provider != AiProviderPreset.LmStudio
}

@Serializable
enum class AiProviderPreset(
    val label: String,
    val defaultBaseUrl: String,
    val defaultChatModel: String,
) {
    OpenAiCompatible("OpenAI 兼容", "https://api.openai.com/v1", "gpt-4o-mini"),
    OpenAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    DeepSeek("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Qwen("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    SiliconFlow("SiliconFlow", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
    Ollama("Ollama", "http://127.0.0.1:11434/v1", "qwen2.5:7b"),
    LmStudio("LM Studio", "http://127.0.0.1:1234/v1", "local-model"),
    Custom("自定义", "", ""),
}

@Serializable
enum class AiTaskKind(val label: String) {
    ComposePolish("润色草稿"),
    ComposeShorten("缩短草稿"),
    ComposeExpand("扩写草稿"),
    ComposeTranslateZh("翻译成中文"),
    ComposeContentWarning("生成 CW"),
    ComposeHashtags("推荐话题"),
    ComposeMentionSuggestions("推荐 @ 人"),
    PostSummary("帖子总结"),
    PostReplyDraft("帖子回复草稿"),
    ThreadSummary("线程总结"),
    ThreadReplyDraft("线程回复草稿"),
    TimelineDigest("时间线速览"),
    TimelineReplyOpportunities("互动建议"),
    TimelineFilterSuggestions("过滤建议"),
    ChatSummary("聊天总结"),
    ChatReplyDraft("聊天回复草稿"),
    ChatActionItems("聊天待办提取"),
    ChatDecisionSummary("聊天决策摘要"),
    NotificationSummary("通知总结"),
    NotificationFollowUp("通知待处理"),
    NotificationPriority("通知优先级"),
    ProfileSummary("资料速览"),
    ProfileInteractionSuggestions("资料互动建议"),
    AutomationSemanticCondition("自动化语义条件"),
    AutomationGeneratedAction("自动化 AI 动作"),
    AutomationExplain("自动化解释"),
    AutomationRuleSuggestions("自动化规则建议"),
    WorkspaceActionPlan("全局行动计划"),
    ConnectionTest("连接测试"),
}

@Serializable
enum class AiTaskStatus {
    Pending,
    Running,
    Completed,
    Failed,
}

@Immutable
@Serializable
data class AiTask(
    val id: String,
    val accountId: String,
    val kind: AiTaskKind,
    val input: AiTaskInput,
    val status: AiTaskStatus = AiTaskStatus.Pending,
    val resultText: String = "",
    val errorMessage: String = "",
    val createdAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val retryCount: Int = 0,
    val usageCharged: Boolean = false,
) {
    val isFinished: Boolean
        get() = status == AiTaskStatus.Completed || status == AiTaskStatus.Failed
}

@Immutable
@Serializable
data class AiTaskInput(
    val text: String = "",
    val title: String = "",
    val prompt: String = "",
    val noteId: String = "",
    val noteAuthor: String = "",
    val noteText: String = "",
    val quotedNoteText: String = "",
    val timelineTitle: String = "",
    val timelineNotes: List<AiPostContext> = emptyList(),
    val chatTitle: String = "",
    val chatMessages: List<AiChatMessageContext> = emptyList(),
    val notifications: List<AiNotificationContext> = emptyList(),
    val profile: AiProfileContext? = null,
    val automationEventText: String = "",
) {
    fun compact(maxChars: Int): AiTaskInput {
        return copy(
            text = text.take(maxChars),
            title = title.take(240),
            prompt = prompt.take(1_000),
            noteId = noteId.take(128),
            noteAuthor = noteAuthor.take(160),
            noteText = noteText.take(maxChars),
            quotedNoteText = quotedNoteText.take(maxChars / 2),
            timelineTitle = timelineTitle.take(120),
            timelineNotes = timelineNotes.take(60).map { it.compact(520) },
            chatTitle = chatTitle.take(200),
            chatMessages = chatMessages.takeLast(80).map { it.compact(480) },
            notifications = notifications.take(80).map { it.compact(420) },
            profile = profile?.compact(1_200),
            automationEventText = automationEventText.take(maxChars),
        )
    }
}

@Immutable
@Serializable
data class AiProfileContext(
    val id: String,
    val displayName: String,
    val username: String,
    val bio: String = "",
    val host: String = "",
    val stats: String = "",
    val relationship: String = "",
) {
    fun compact(maxChars: Int): AiProfileContext = copy(
        id = id.take(128),
        displayName = displayName.take(120),
        username = username.take(120),
        bio = bio.take(maxChars),
        host = host.take(160),
        stats = stats.take(240),
        relationship = relationship.take(240),
    )
}

@Immutable
@Serializable
data class AiPostContext(
    val id: String,
    val author: String,
    val username: String = "",
    val text: String,
    val createdAtLabel: String = "",
    val stats: String = "",
) {
    fun compact(maxChars: Int): AiPostContext = copy(
        id = id.take(128),
        author = author.take(120),
        username = username.take(80),
        text = text.take(maxChars),
        createdAtLabel = createdAtLabel.take(80),
        stats = stats.take(120),
    )
}

@Immutable
@Serializable
data class AiChatMessageContext(
    val sender: String,
    val text: String,
    val createdAtLabel: String = "",
) {
    fun compact(maxChars: Int): AiChatMessageContext = copy(
        sender = sender.take(120),
        text = text.take(maxChars),
        createdAtLabel = createdAtLabel.take(80),
    )
}

@Immutable
@Serializable
data class AiNotificationContext(
    val type: String,
    val actor: String,
    val text: String,
    val notePreviewText: String = "",
    val createdAtLabel: String = "",
) {
    fun compact(maxChars: Int): AiNotificationContext = copy(
        type = type.take(80),
        actor = actor.take(120),
        text = text.take(maxChars),
        notePreviewText = notePreviewText.take(maxChars),
        createdAtLabel = createdAtLabel.take(80),
    )
}

@Immutable
data class AiUiState(
    val settings: AiSettings = AiSettings(),
    val tasks: List<AiTask> = emptyList(),
    val usage: AiUsageWindow = AiUsageWindow(),
    val activeTaskId: String? = null,
    val latestResult: AiTask? = null,
    val isTestingConnection: Boolean = false,
    val isProcessing: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val hasUsableModel: Boolean
        get() = settings.enabled && settings.hasEndpoint && (!settings.supportsCloudAuth || settings.apiKey.isNotBlank())

    val remainingDailyRequests: Int
        get() = (settings.dailyRequestLimit - usage.normalizedAiUsage().requestCount).coerceAtLeast(0)

    val usedDailyRequests: Int
        get() = usage.normalizedAiUsage().requestCount
}

@Serializable
data class AiUsageWindow(
    val dayKey: String = "",
    val requestCount: Int = 0,
)

data class AiUsageConsumeResult(
    val usage: AiUsageWindow,
    val errorMessage: String? = null,
)

fun AiUsageWindow.normalizedAiUsage(nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()): AiUsageWindow {
    val today = aiUsageDayKey(nowEpochMillis)
    return if (dayKey == today) {
        copy(requestCount = requestCount.coerceAtLeast(0))
    } else {
        AiUsageWindow(dayKey = today, requestCount = 0)
    }
}

fun AiUsageWindow.consumeAiRequest(
    settings: AiSettings,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): AiUsageConsumeResult {
    val normalized = normalizedAiUsage(nowEpochMillis)
    return if (normalized.requestCount >= settings.dailyRequestLimit.coerceAtLeast(1)) {
        AiUsageConsumeResult(normalized, "已达到今日 AI 请求上限")
    } else {
        AiUsageConsumeResult(normalized.copy(requestCount = normalized.requestCount + 1))
    }
}

private fun aiUsageDayKey(nowEpochMillis: Long): String {
    return (nowEpochMillis / 86_400_000L).toString()
}

fun Note.toAiTaskInput(uploadSensitiveContentAllowed: Boolean = false): AiTaskInput {
    return AiTaskInput(
        noteId = id,
        noteAuthor = author.displayName.ifBlank { author.username },
        noteText = toAiReadableText(uploadSensitiveContentAllowed),
        quotedNoteText = quotedNote?.let { quoted ->
            "${quoted.author.displayName.ifBlank { quoted.author.username }}: " +
                quoted.toAiReadableText(uploadSensitiveContentAllowed)
        }.orEmpty(),
    )
}

fun List<Note>.toAiPostContexts(uploadSensitiveContentAllowed: Boolean = false): List<AiPostContext> {
    return map { note ->
        AiPostContext(
            id = note.id,
            author = note.author.displayName.ifBlank { note.author.username },
            username = note.author.username,
            text = note.toAiReadableText(uploadSensitiveContentAllowed),
            createdAtLabel = note.createdAtLabel,
            stats = buildString {
                append("回复 ${note.replyCount}")
                append(" · 转发 ${note.renoteCount}")
                append(" · 反应 ${note.reactionCount}")
                if (note.isFavorited) append(" · 已收藏")
                if (note.poll != null) append(" · 投票")
                if (note.media.isNotEmpty()) append(" · 附件 ${note.media.size}")
            },
        )
    }
}

fun Note.toAiReadableText(uploadSensitiveContentAllowed: Boolean = false): String {
    val cleanCw = cw?.trim().orEmpty()
    val hasSensitiveMedia = media.any { it.isSensitive }
    if (!uploadSensitiveContentAllowed && cleanCw.isNotBlank()) {
        return "内容警告：$cleanCw（正文因敏感内容权限未开启未上传）"
    }
    val cleanText = text.trim().ifBlank {
        cleanCw.ifBlank { "无正文" }
    }
    val visibleMediaDescriptions = media
        .filter { uploadSensitiveContentAllowed || !it.isSensitive }
        .mapNotNull { media -> media.description.trim().takeIf { it.isNotBlank() } }
    return buildString {
        append(cleanText)
        if (visibleMediaDescriptions.isNotEmpty()) {
            appendLine()
            append("附件描述：")
            append(visibleMediaDescriptions.joinToString("；"))
        }
        if (!uploadSensitiveContentAllowed && hasSensitiveMedia) {
            appendLine()
            append("（部分敏感附件描述因权限未开启未上传）")
        }
    }.trim()
}

fun List<ChatMessage>.toAiChatMessageContexts(): List<AiChatMessageContext> {
    return map { message ->
        AiChatMessageContext(
            sender = message.fromUser.displayName.ifBlank { message.fromUser.username },
            text = message.text,
            createdAtLabel = message.createdAtLabel,
        )
    }
}

fun List<NotificationItem>.toAiNotificationContexts(): List<AiNotificationContext> {
    return map { item ->
        AiNotificationContext(
            type = item.type.name,
            actor = item.actor.displayName.ifBlank { item.actor.username },
            text = item.text,
            notePreviewText = item.notePreviewText.orEmpty(),
            createdAtLabel = item.createdAtLabel,
        )
    }
}

fun User.toAiProfileContext(relationship: String = ""): AiProfileContext {
    return AiProfileContext(
        id = id,
        displayName = displayName.ifBlank { username },
        username = username,
        bio = bio,
        host = host.orEmpty(),
        stats = "关注 $followingCount · 关注者 $followersCount · 帖子 $notesCount",
        relationship = relationship,
    )
}

const val DEFAULT_AI_SYSTEM_PROMPT: String =
    "你是 HHHL 客户端里的写作、阅读和自动化助手。输出要直接可用，保持简洁、准确、尊重上下文，不要编造不存在的信息。"

internal const val AI_MAX_TASKS = 120
internal const val AI_MAX_RESULT_CHARS = 4_000
