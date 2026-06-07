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
    val enabled: Boolean = true,
    val serviceMode: AiServiceMode = AiServiceMode.RemoteFirst,
    val remoteBaseUrl: String = DEFAULT_SERVER_AI_BASE_URL,
    val remotePreferredModel: String = DEFAULT_SERVER_AI_MODEL,
    val provider: AiProviderPreset = AiProviderPreset.OpenAiCompatible,
    val baseUrl: String = AiProviderPreset.OpenAiCompatible.defaultBaseUrl,
    val apiKey: String = "",
    val chatModel: String = AiProviderPreset.OpenAiCompatible.defaultChatModel,
    val fastModel: String = AiProviderPreset.OpenAiCompatible.defaultFastModel,
    val longContextModel: String = AiProviderPreset.OpenAiCompatible.defaultLongContextModel,
    val visionModel: String = AiProviderPreset.OpenAiCompatible.defaultVisionModel,
    val imageGenerationBaseUrl: String = AiProviderPreset.OpenAiCompatible.defaultBaseUrl,
    val imageGenerationApiKey: String = "",
    val imageGenerationModel: String = DEFAULT_IMAGE_GENERATION_MODEL,
    val embeddingModel: String = AiProviderPreset.OpenAiCompatible.defaultEmbeddingModel,
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
    val assistantMemoryNotes: List<String> = emptyList(),
    val assistantLowRiskAutoApproval: Boolean = true,
    val assistantHighRiskAutoApproval: Boolean = false,
    val floatingAssistantEnabled: Boolean = true,
    val automationRuleDraftModel: AiAutomationModelConfig = AiAutomationModelConfig(),
) {
    val cleanRemoteBaseUrl: String
        get() = remoteBaseUrl.trim().trimEnd('/')

    val cleanBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val cleanImageGenerationBaseUrl: String
        get() = imageGenerationBaseUrl.trim().trimEnd('/')

    val hasEndpoint: Boolean
        get() = hasServerAiEndpoint || hasLocalEndpoint

    val hasImageGenerationEndpoint: Boolean
        get() = cleanImageGenerationBaseUrl.isNotBlank() && imageGenerationModel.trim().isNotBlank()

    val hasServerAiEndpoint: Boolean
        get() = serviceMode != AiServiceMode.LocalOnly &&
            cleanRemoteBaseUrl.isNotBlank() &&
            remotePreferredModel.trim().isNotBlank()

    val hasLocalEndpoint: Boolean
        get() = serviceMode != AiServiceMode.RemoteOnly &&
            cleanBaseUrl.isNotBlank() &&
            activeChatModel.isNotBlank()

    val allowLocalFallback: Boolean
        get() = serviceMode == AiServiceMode.RemoteFirst

    val activeChatModel: String
        get() = chatModel.trim().ifBlank { fastModel.trim() }

    val supportsCloudAuth: Boolean
        get() = provider != AiProviderPreset.Ollama && provider != AiProviderPreset.LmStudio
}

@Serializable
enum class AiServiceMode(val label: String) {
    RemoteFirst("远端优先"),
    RemoteOnly("仅远端"),
    LocalOnly("仅本地"),
}

@Immutable
@Serializable
data class AiAutomationModelConfig(
    val enabled: Boolean = false,
    val provider: AiProviderPreset = AiProviderPreset.OpenAiCompatible,
    val baseUrl: String = AiProviderPreset.OpenAiCompatible.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = AiProviderPreset.OpenAiCompatible.defaultChatModel,
) {
    val cleanBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val activeModel: String
        get() = model.trim()
}

@Serializable
enum class AiProviderPreset(
    val label: String,
    val defaultBaseUrl: String,
    val defaultChatModel: String,
    defaultFastModelValue: String? = null,
    defaultLongContextModelValue: String? = null,
    defaultVisionModelValue: String? = null,
    defaultEmbeddingModelValue: String? = null,
) {
    OpenAiCompatible(
        "OpenAI 兼容",
        "https://api.openai.com/v1",
        "gpt-5.5",
        defaultEmbeddingModelValue = "text-embedding-3-large",
    ),
    OpenAI(
        "OpenAI",
        "https://api.openai.com/v1",
        "gpt-5.5",
        defaultEmbeddingModelValue = "text-embedding-3-large",
    ),
    Claude(
        "Claude",
        "https://api.anthropic.com/v1",
        "claude4.7",
    ),
    DeepSeek("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Qwen("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    Xiaomi("小米", "https://api.xiaomi.com/v1", "mimo-7b"),
    SiliconFlow("SiliconFlow", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
    Ollama("Ollama", "http://127.0.0.1:11434/v1", "qwen2.5:7b"),
    LmStudio("LM Studio", "http://127.0.0.1:1234/v1", "local-model"),
    Custom("自定义", "", ""),

    ;

    val defaultFastModel: String = defaultFastModelValue ?: defaultChatModel
    val defaultLongContextModel: String = defaultLongContextModelValue ?: defaultChatModel
    val defaultVisionModel: String = defaultVisionModelValue ?: defaultChatModel
    val defaultEmbeddingModel: String = defaultEmbeddingModelValue ?: defaultChatModel
}

const val DEFAULT_SERVER_AI_BASE_URL: String = "https://dc.hhhl.cc"
const val DEFAULT_SERVER_AI_MODEL: String = "gpt-5.5"
const val DEFAULT_IMAGE_GENERATION_MODEL: String = "gpt-image-2"
const val DEFAULT_IMAGE_GENERATION_SIZE: String = "1024x1024"
const val DEFAULT_IMAGE_GENERATION_QUALITY: String = "medium"
const val DEFAULT_IMAGE_GENERATION_OUTPUT_FORMAT: String = "png"

@Serializable
enum class AiTaskKind(val label: String) {
    ComposePolish("润色草稿"),
    ComposeShorten("缩短草稿"),
    ComposeExpand("扩写草稿"),
    ComposeTranslateZh("翻译成中文"),
    ComposeContentWarning("生成 CW"),
    ComposeHashtags("推荐话题"),
    ComposeMentionSuggestions("推荐 @ 人"),
    ComposeFromRecentPosts("结合最近帖子生成"),
    PostSummary("帖子总结"),
    PostReplyDraft("帖子回复草稿"),
    ThreadSummary("线程总结"),
    ThreadReplyDraft("线程回复草稿"),
    TimelineDigest("时间线速览"),
    TimelineReplyOpportunities("互动建议"),
    TimelineFilterSuggestions("过滤建议"),
    ChatSummary("聊天总结"),
    ChatRecentSummary("最近 50 条摘要"),
    ChatTodaySummary("今日聊天摘要"),
    ChatUnreadSummary("未读聊天摘要"),
    ChatReplyDraft("聊天回复草稿"),
    ChatActionItems("聊天待办提取"),
    ChatDecisionSummary("聊天决策摘要"),
    ChatImportanceCheck("聊天重要性判断"),
    NotificationSummary("通知总结"),
    NotificationFollowUp("通知待处理"),
    NotificationPriority("通知优先级"),
    ProfileSummary("资料速览"),
    ProfileInteractionSuggestions("资料互动建议"),
    AutomationSemanticCondition("自动化语义条件"),
    AutomationGeneratedAction("自动化 AI 动作"),
    AutomationExplain("自动化解释"),
    AutomationRuleSuggestions("自动化规则建议"),
    AutomationRuleDraft("自动创建规则"),
    AssistantChat("AI 助手"),
    WorkspaceActionPlan("全局行动计划"),
    ConnectionTest("连接测试"),
}

fun AiProviderPreset.defaultChatModelForSettings(): String {
    return when (this) {
        AiProviderPreset.DeepSeek -> "deepseek-v4-pro"
        else -> defaultChatModel
    }
}

fun AiProviderPreset.defaultFastModelForSettings(): String {
    return when (this) {
        AiProviderPreset.DeepSeek -> "deepseek-v4-flash"
        else -> defaultFastModel
    }
}

fun AiProviderPreset.defaultLongContextModelForSettings(): String {
    return when (this) {
        AiProviderPreset.DeepSeek -> "deepseek-v4-pro"
        else -> defaultLongContextModel
    }
}

fun AiSettings.modelForTask(kind: AiTaskKind): String {
    val chat = activeChatModel
    val fast = fastModel.trim().ifBlank { chat }
    val longContext = longContextModel.trim().ifBlank { chat }
    return when (kind) {
        AiTaskKind.ConnectionTest,
        AiTaskKind.ComposeContentWarning,
        AiTaskKind.ComposeHashtags,
        AiTaskKind.ComposeMentionSuggestions,
        AiTaskKind.ChatImportanceCheck,
        AiTaskKind.AutomationSemanticCondition,
            -> fast

        AiTaskKind.ComposeFromRecentPosts,
        AiTaskKind.ThreadSummary,
        AiTaskKind.TimelineDigest,
        AiTaskKind.TimelineReplyOpportunities,
        AiTaskKind.TimelineFilterSuggestions,
        AiTaskKind.ChatSummary,
        AiTaskKind.ChatRecentSummary,
        AiTaskKind.ChatTodaySummary,
        AiTaskKind.ChatUnreadSummary,
        AiTaskKind.ChatActionItems,
        AiTaskKind.ChatDecisionSummary,
        AiTaskKind.NotificationSummary,
        AiTaskKind.NotificationFollowUp,
        AiTaskKind.NotificationPriority,
        AiTaskKind.ProfileSummary,
        AiTaskKind.ProfileInteractionSuggestions,
        AiTaskKind.AutomationExplain,
        AiTaskKind.AutomationRuleSuggestions,
        AiTaskKind.AutomationRuleDraft,
        AiTaskKind.WorkspaceActionPlan,
            -> longContext

        AiTaskKind.ComposePolish,
        AiTaskKind.ComposeShorten,
        AiTaskKind.ComposeExpand,
        AiTaskKind.ComposeTranslateZh,
        AiTaskKind.PostSummary,
        AiTaskKind.PostReplyDraft,
        AiTaskKind.ThreadReplyDraft,
        AiTaskKind.ChatReplyDraft,
        AiTaskKind.AutomationGeneratedAction,
        AiTaskKind.AssistantChat,
            -> chat
    }.ifBlank { chat }
}

fun AiSettings.settingsForTask(kind: AiTaskKind): AiSettings {
    if (kind != AiTaskKind.AutomationRuleDraft || !automationRuleDraftModel.enabled) return this
    val automationModel = automationRuleDraftModel
    return copy(
        serviceMode = AiServiceMode.LocalOnly,
        provider = automationModel.provider,
        baseUrl = automationModel.baseUrl,
        apiKey = automationModel.apiKey,
        chatModel = automationModel.activeModel,
        fastModel = automationModel.activeModel,
        longContextModel = automationModel.activeModel,
    )
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
    val resultConsumed: Boolean = false,
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
    val fileIds: List<String> = emptyList(),
    val fileContext: String = "",
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
            fileIds = fileIds.map { it.trim().take(128) }.filter { it.isNotBlank() }.distinct().take(16),
            fileContext = fileContext.take(maxChars / 2),
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
    val id: String = "",
    val noteId: String = "",
) {
    fun compact(maxChars: Int): AiNotificationContext = copy(
        id = id.take(128),
        type = type.take(80),
        actor = actor.take(120),
        text = text.take(maxChars),
        noteId = noteId.take(128),
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
        get() = settings.enabled && (
            settings.hasServerAiEndpoint ||
                (settings.hasLocalEndpoint && (!settings.supportsCloudAuth || settings.apiKey.isNotBlank()))
            )

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

data class AiImageRequestOptions(
    val size: String = DEFAULT_IMAGE_GENERATION_SIZE,
    val quality: String = DEFAULT_IMAGE_GENERATION_QUALITY,
    val background: String? = null,
    val outputFormat: String = DEFAULT_IMAGE_GENERATION_OUTPUT_FORMAT,
    val outputCompression: Int? = null,
    val count: Int = 1,
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
            id = item.id,
            type = item.type.name,
            actor = item.actor.displayName.ifBlank { item.actor.username },
            text = item.text,
            noteId = item.noteId.orEmpty(),
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
