package cc.hhhl.client.notification

import kotlinx.serialization.Serializable

@Serializable
data class ChatNoiseReductionSettings(
    val aggregateBurstNotifications: Boolean = true,
    val burstWindowMinutes: Int = 5,
    val burstMessageThreshold: Int = 4,
    val importantOnly: Boolean = false,
    val aiImportanceEnabled: Boolean = false,
    val keywordRules: List<String> = emptyList(),
    val userRules: List<String> = emptyList(),
) {
    val normalized: ChatNoiseReductionSettings
        get() = copy(
            burstWindowMinutes = burstWindowMinutes.coerceIn(1, 60),
            burstMessageThreshold = burstMessageThreshold.coerceIn(2, 99),
            keywordRules = keywordRules.normalizedChatNoiseRules(maxRules = 40, maxLength = 80),
            userRules = userRules.normalizedChatNoiseRules(maxRules = 80, maxLength = 120),
        )

    val shouldUseAiImportance: Boolean
        get() = importantOnly && aiImportanceEnabled

    fun evaluate(
        candidate: ChatNoiseReductionCandidate,
        aiImportant: Boolean? = null,
    ): ChatNoiseReductionDecision {
        val settings = normalized
        if (!settings.importantOnly) {
            return ChatNoiseReductionDecision(shouldNotify = true, reason = "全部聊天")
        }
        val attention = candidate.attentionKindName.trim()
        if (attention.isNotBlank()) {
            return ChatNoiseReductionDecision(shouldNotify = true, reason = attention)
        }
        val keyword = settings.keywordRules.firstOrNull { rule ->
            candidate.text.contains(rule, ignoreCase = true)
        }
        if (keyword != null) {
            return ChatNoiseReductionDecision(shouldNotify = true, reason = "关键词：$keyword")
        }
        val userRule = settings.userRules.firstOrNull { rule -> candidate.matchesUserRule(rule) }
        if (userRule != null) {
            return ChatNoiseReductionDecision(shouldNotify = true, reason = "指定用户：$userRule")
        }
        if (aiImportant == true) {
            return ChatNoiseReductionDecision(shouldNotify = true, reason = "AI 判断重要")
        }
        return ChatNoiseReductionDecision(
            shouldNotify = false,
            reason = if (settings.shouldUseAiImportance && aiImportant == null) "等待 AI 判断" else "未命中重点条件",
            requiresAi = settings.shouldUseAiImportance && aiImportant == null,
        )
    }
}

data class ChatNoiseReductionCandidate(
    val senderUserId: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderHost: String = "",
    val text: String = "",
    val attentionKindName: String = "",
)

data class ChatNoiseReductionDecision(
    val shouldNotify: Boolean,
    val reason: String,
    val requiresAi: Boolean = false,
)

fun String.toChatNoiseRules(maxRules: Int = 80, maxLength: Int = 120): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '、')
        .normalizedChatNoiseRules(maxRules = maxRules, maxLength = maxLength)
}

fun List<String>.normalizedChatNoiseRules(
    maxRules: Int = 80,
    maxLength: Int = 120,
): List<String> {
    return asSequence()
        .map { rule -> rule.trim() }
        .filter { rule -> rule.isNotEmpty() }
        .map { rule -> rule.take(maxLength) }
        .distinctBy { rule -> rule.lowercase() }
        .take(maxRules)
        .toList()
}

fun String.aiChatImportanceDecision(): Boolean? {
    val clean = trim().lowercase()
    if (clean.isBlank()) return null
    val head = clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    return when {
        head.startsWith("important: false") || head.startsWith("important：false") -> false
        head.startsWith("important: no") || head.startsWith("important：no") -> false
        head.startsWith("important: true") || head.startsWith("important：true") -> true
        head.startsWith("important: yes") || head.startsWith("important：yes") -> true
        head.startsWith("important") || head.startsWith("yes") || head.startsWith("true") -> true
        head.startsWith("重要") || head.startsWith("提醒") || head.startsWith("保留") -> true
        head.startsWith("ignore") || head.startsWith("no") || head.startsWith("false") -> false
        head.startsWith("不重要") || head.startsWith("忽略") || head.startsWith("无需") -> false
        "important: true" in clean || "重要：是" in clean || "是否重要：是" in clean -> true
        "important: false" in clean || "重要：否" in clean || "是否重要：否" in clean -> false
        else -> null
    }
}

private fun ChatNoiseReductionCandidate.matchesUserRule(rule: String): Boolean {
    val cleanRule = rule.trim().lowercase()
    if (cleanRule.isBlank()) return false
    val cleanHost = senderHost.trim().lowercase()
    val cleanUsername = senderUsername.trim().lowercase()
    val identities = buildList {
        senderUserId.trim().lowercase().takeIf { it.isNotBlank() }?.let(::add)
        cleanUsername.takeIf { it.isNotBlank() }?.let { username ->
            add(username)
            add("@$username")
            if (cleanHost.isNotBlank()) add("@$username@$cleanHost")
        }
        senderDisplayName.trim().lowercase().takeIf { it.isNotBlank() }?.let(::add)
    }
    return cleanRule in identities
}
