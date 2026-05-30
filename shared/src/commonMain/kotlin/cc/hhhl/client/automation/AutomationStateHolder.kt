package cc.hhhl.client.automation

import cc.hhhl.client.ai.AiBridge
import cc.hhhl.client.ai.AiBridgeResult
import cc.hhhl.client.ai.NoopAiBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private suspend fun <T> Iterable<T>.allSuspend(predicate: suspend (T) -> Boolean): Boolean {
    for (item in this) if (!predicate(item)) return false
    return true
}

private suspend fun <T> Iterable<T>.anySuspend(predicate: suspend (T) -> Boolean): Boolean {
    for (item in this) if (predicate(item)) return true
    return false
}

private data class AutomationConditionMatch(
    val matched: Boolean,
    val failureMessage: String? = null,
)

data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val selectedRuleId: String? = null,
    val editorOpen: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val selectedRule: AutomationRule?
        get() = rules.firstOrNull { it.id == selectedRuleId }

    val enabledRuleCount: Int
        get() = rules.count { it.enabled }
}

interface AutomationActionExecutor {
    suspend fun execute(
        action: AutomationAction,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult
}

data class AutomationActionExecutionResult(
    val success: Boolean,
    val message: String,
)

object NoopAutomationActionExecutor : AutomationActionExecutor {
    override suspend fun execute(
        action: AutomationAction,
        event: AutomationEvent,
        title: String,
        body: String,
    ): AutomationActionExecutionResult {
        return AutomationActionExecutionResult(
            success = action.type == AutomationActionType.AddLog,
            message = if (action.type == AutomationActionType.AddLog) body else "动作执行器未接入",
        )
    }
}

class AutomationStateHolder(
    private val store: AutomationStore = NoopAutomationStore,
    private val accountId: String? = null,
    private val executor: AutomationActionExecutor = NoopAutomationActionExecutor,
    private val aiBridge: AiBridge = NoopAiBridge,
    private val aiToolPermissionProvider: () -> Boolean = { true },
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AutomationUiState())
    val state: StateFlow<AutomationUiState> = mutableState
    private var cooldownMarkers: Map<String, Long> = emptyMap()
    private var recentEventRuleKeys: List<String> = emptyList()
    private var nextLocalId = 0

    fun restore() {
        val snapshot = runCatching { store.read(cleanAccountId()) }.getOrDefault(AutomationSnapshot())
        mutableState.update {
            it.copy(
                rules = snapshot.rules.sortedByDescending { rule -> rule.updatedAtEpochMillis },
                logs = snapshot.logs.sortedByDescending { log -> log.createdAtEpochMillis },
                message = null,
                errorMessage = null,
            )
        }
    }

    fun createRule(trigger: AutomationTrigger = AutomationTrigger.ChatMessage) {
        val now = nowMillis()
        val rule = AutomationRule(
            id = nextId("rule"),
            name = defaultRuleName(trigger),
            trigger = trigger,
            conditions = listOf(defaultConditionFor(trigger)),
            actions = listOf(defaultActionFor(trigger)),
            updatedAtEpochMillis = now,
        )
        updateRules(
            transform = { rules -> listOf(rule) + rules },
            selectedRuleId = rule.id,
            editorOpen = true,
            message = "已创建规则",
        )
    }

    fun addRuleDraft(rule: AutomationRule) {
        val now = nowMillis()
        val draft = rule.copy(
            id = nextId("rule"),
            conditions = rule.conditions.map { condition -> condition.copy(id = nextId("condition")) },
            actions = rule.actions.map { action -> action.copy(id = nextId("action")) },
            updatedAtEpochMillis = now,
        ).cleaned()
        updateRules(
            transform = { rules -> listOf(draft) + rules },
            selectedRuleId = draft.id,
            editorOpen = true,
            message = if (draft.enabled) "AI 已生成并启用规则草稿" else "AI 已生成规则草稿",
        )
    }

    fun openRule(ruleId: String) {
        mutableState.update {
            it.copy(selectedRuleId = ruleId, editorOpen = true, message = null, errorMessage = null)
        }
    }

    fun closeEditor() {
        mutableState.update { it.copy(editorOpen = false, selectedRuleId = null) }
    }

    fun toggleRule(ruleId: String) {
        updateRule(ruleId) { rule -> rule.copy(enabled = !rule.enabled, updatedAtEpochMillis = nowMillis()) }
    }

    fun deleteRule(ruleId: String) {
        updateRules(
            transform = { rules -> rules.filterNot { it.id == ruleId } },
            selectedRuleId = null,
            editorOpen = false,
            message = "规则已删除",
        )
    }

    fun duplicateRule(ruleId: String) {
        val source = state.value.rules.firstOrNull { it.id == ruleId } ?: return
        val now = nowMillis()
        val copy = source.copy(
            id = nextId("rule"),
            name = "${source.name} 副本",
            enabled = false,
            updatedAtEpochMillis = now,
        )
        updateRules(
            transform = { rules -> listOf(copy) + rules },
            selectedRuleId = copy.id,
            editorOpen = true,
            message = "已复制规则",
        )
    }

    fun updateRuleName(ruleId: String, name: String) {
        updateRule(ruleId) { rule ->
            rule.copy(name = name.take(MAX_RULE_NAME_LENGTH), updatedAtEpochMillis = nowMillis())
        }
    }

    fun updateRuleTrigger(ruleId: String, trigger: AutomationTrigger) {
        updateRule(ruleId) { rule ->
            rule.copy(trigger = trigger, updatedAtEpochMillis = nowMillis())
        }
    }

    fun updateRuleConditionMode(ruleId: String, mode: AutomationConditionMode) {
        updateRule(ruleId) { rule -> rule.copy(conditionMode = mode, updatedAtEpochMillis = nowMillis()) }
    }

    fun updateRuleActionMode(ruleId: String, mode: AutomationActionMode) {
        updateRule(ruleId) { rule -> rule.copy(actionMode = mode, updatedAtEpochMillis = nowMillis()) }
    }

    fun updateRuleIgnoreOwnMessages(ruleId: String, ignore: Boolean) {
        updateRule(ruleId) { rule -> rule.copy(ignoreOwnMessages = ignore, updatedAtEpochMillis = nowMillis()) }
    }

    fun updateRuleCooldown(ruleId: String, seconds: Int) {
        updateRule(ruleId) { rule ->
            rule.copy(cooldownSeconds = seconds.coerceIn(0, MAX_COOLDOWN_SECONDS), updatedAtEpochMillis = nowMillis())
        }
    }

    fun addCondition(ruleId: String, type: AutomationConditionType = AutomationConditionType.MessageContains) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions + AutomationCondition(id = nextId("condition"), type = type),
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun updateCondition(ruleId: String, condition: AutomationCondition) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.map { if (it.id == condition.id) condition.cleaned() else it },
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun removeCondition(ruleId: String, conditionId: String) {
        updateRule(ruleId) { rule ->
            rule.copy(
                conditions = rule.conditions.filterNot { it.id == conditionId },
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun addAction(ruleId: String, type: AutomationActionType = AutomationActionType.AddLog) {
        updateRule(ruleId) { rule ->
            rule.copy(
                actions = rule.actions + AutomationAction(
                    id = nextId("action"),
                    type = type,
                    titleTemplate = if (type == AutomationActionType.SystemNotification) "HHHL 自动化" else "",
                    bodyTemplate = defaultAutomationTemplate(AutomationEvent(nextId("event"), rule.trigger)),
                ),
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun updateAction(ruleId: String, action: AutomationAction) {
        updateRule(ruleId) { rule ->
            rule.copy(
                actions = rule.actions.map { if (it.id == action.id) action.cleaned() else it },
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun removeAction(ruleId: String, actionId: String) {
        updateRule(ruleId) { rule ->
            rule.copy(
                actions = rule.actions.filterNot { it.id == actionId },
                updatedAtEpochMillis = nowMillis(),
            )
        }
    }

    fun clearLogs() {
        mutableState.update { it.copy(logs = emptyList(), message = "日志已清空", errorMessage = null) }
        persist()
    }

    fun emit(event: AutomationEvent) {
        scope.launch {
            emitNow(event)
        }
    }

    suspend fun emitNow(event: AutomationEvent) {
        val rules = state.value.rules
            .filter { rule -> rule.enabled && rule.trigger == event.trigger }
            .filter { rule -> !rule.ignoreOwnMessages || !event.isFromCurrentUser }
            .filter { rule -> rule.cooldownReady(event) }
        if (rules.isEmpty()) return

        rules.forEach { rule ->
            if (!rule.matches(event)) return@forEach
            val dedupeKey = "${event.id}:${rule.id}"
            if (dedupeKey in recentEventRuleKeys) return@forEach
            recentEventRuleKeys = (listOf(dedupeKey) + recentEventRuleKeys).take(MAX_RECENT_EVENT_KEYS)
            cooldownMarkers = cooldownMarkers + (rule.id to nowMillis())
            executeRule(rule, event)
        }
    }

    private suspend fun executeRule(rule: AutomationRule, event: AutomationEvent) {
        val actions = rule.actions.filter { it.enabled }
        if (actions.isEmpty()) {
            addExecutionLog(rule, event, "无动作", "没有启用的动作", success = false)
            return
        }
        if (rule.actionMode == AutomationActionMode.Parallel) {
            coroutineScope {
                actions.map { action -> async { executeAction(rule, action, event) } }.awaitAll()
            }
        } else {
            for (action in actions) {
                val success = executeAction(rule, action, event)
                if (!success && action.failurePolicy == AutomationFailurePolicy.Stop) break
            }
        }
    }

    private suspend fun executeAction(
        rule: AutomationRule,
        action: AutomationAction,
        event: AutomationEvent,
    ): Boolean {
        val title = renderAutomationTemplate(action.titleTemplate.ifBlank { rule.name }, event)
        val body = renderAutomationTemplate(action.bodyTemplate, event)
        if (action.requiresToolPermission() && !aiToolPermissionProvider()) {
            addExecutionLog(
                rule = rule,
                event = event,
                actionLabel = action.type.label,
                message = "AI 工具权限未开启，已阻止外部写入动作",
                success = false,
            )
            return false
        }
        val result = runCatching {
            if (action.type == AutomationActionType.AddLog) {
                AutomationActionExecutionResult(success = true, message = body)
            } else {
                executor.execute(action, event, title = title, body = body)
            }
        }.getOrElse { error ->
            AutomationActionExecutionResult(success = false, message = error.message ?: "动作执行失败")
        }
        addExecutionLog(
            rule = rule,
            event = event,
            actionLabel = action.type.label,
            message = result.message.ifBlank { body },
            success = result.success,
        )
        return result.success
    }

    private fun addExecutionLog(
        rule: AutomationRule,
        event: AutomationEvent,
        actionLabel: String,
        message: String,
        success: Boolean,
    ) {
        val log = AutomationExecutionLog(
            id = nextId("log"),
            ruleId = rule.id,
            ruleName = rule.name,
            eventId = event.id,
            eventLabel = event.displayLabel,
            actionLabel = actionLabel,
            message = message.take(MAX_LOG_MESSAGE_LENGTH),
            success = success,
            createdAtEpochMillis = nowMillis(),
        )
        mutableState.update {
            it.copy(
                logs = (listOf(log) + it.logs).take(AutomationStoreCodec.MAX_LOGS),
                message = if (success) "自动化已执行" else it.message,
                errorMessage = if (success) null else message,
            )
        }
        persist()
    }

    private fun updateRule(
        ruleId: String,
        transform: (AutomationRule) -> AutomationRule,
    ) {
        updateRules(
            transform = { rules -> rules.map { rule -> if (rule.id == ruleId) transform(rule).cleaned() else rule } },
            selectedRuleId = state.value.selectedRuleId,
            editorOpen = state.value.editorOpen,
            message = null,
        )
    }

    private fun updateRules(
        transform: (List<AutomationRule>) -> List<AutomationRule>,
        selectedRuleId: String?,
        editorOpen: Boolean,
        message: String?,
    ) {
        mutableState.update {
            val nextRules = transform(it.rules)
                .map { rule -> rule.cleaned() }
                .take(AutomationStoreCodec.MAX_RULES)
            it.copy(
                rules = nextRules,
                selectedRuleId = selectedRuleId,
                editorOpen = editorOpen,
                message = message,
                errorMessage = null,
            )
        }
        persist()
    }

    private fun persist() {
        val current = state.value
        runCatching {
            store.write(
                cleanAccountId(),
                AutomationSnapshot(
                    rules = current.rules,
                    logs = current.logs,
                ),
            )
        }
    }

    private suspend fun AutomationRule.matches(event: AutomationEvent): Boolean {
        val enabledConditions = conditions.filter { it.enabled }
        if (enabledConditions.isEmpty()) return true
        return when (conditionMode) {
            AutomationConditionMode.All -> {
                for (condition in enabledConditions) {
                    val result = condition.matches(event)
                    result.failureMessage?.let { message ->
                        addExecutionLog(this, event, "条件判断", message, success = false)
                    }
                    if (!result.matched) return false
                }
                true
            }
            AutomationConditionMode.Any -> {
                val failureMessages = mutableListOf<String>()
                for (condition in enabledConditions) {
                    val result = condition.matches(event)
                    if (result.matched) return true
                    result.failureMessage?.let(failureMessages::add)
                }
                failureMessages.firstOrNull()?.let { message ->
                    addExecutionLog(this, event, "条件判断", message, success = false)
                }
                false
            }
        }
    }

    private fun AutomationRule.cooldownReady(event: AutomationEvent): Boolean {
        if (cooldownSeconds <= 0) return true
        val lastRun = cooldownMarkers[id] ?: return true
        return nowMillis() - lastRun >= cooldownSeconds * 1000L || event.id.startsWith("manual:")
    }

    private suspend fun AutomationCondition.matches(event: AutomationEvent): AutomationConditionMatch {
        val cleanValue = value.trim()
        if (cleanValue.isEmpty()) return AutomationConditionMatch(true)
        return when (type) {
            AutomationConditionType.SenderUserId -> AutomationConditionMatch(event.senderUserId.equals(cleanValue, ignoreCase = true))
            AutomationConditionType.SenderUserIds -> AutomationConditionMatch(cleanValue.splitAutomationValues().any { value -> event.senderUserId.equals(value, ignoreCase = true) })
            AutomationConditionType.SenderUsername -> AutomationConditionMatch(event.matchesAutomationUsername(cleanValue))
            AutomationConditionType.SenderNameContains -> AutomationConditionMatch(event.matchesAutomationSenderName(cleanValue))
            AutomationConditionType.MessageContains -> AutomationConditionMatch(event.defaultBody.contains(cleanValue, ignoreCase = true))
            AutomationConditionType.RoomId -> AutomationConditionMatch(event.roomId.equals(cleanValue, ignoreCase = true))
            AutomationConditionType.RoomNameContains -> AutomationConditionMatch(event.roomName.contains(cleanValue, ignoreCase = true))
            AutomationConditionType.DirectUserId -> AutomationConditionMatch(event.directUserId.equals(cleanValue, ignoreCase = true))
            AutomationConditionType.SourceKind -> AutomationConditionMatch(
                event.sourceKind.normalizedAutomationSourceKind() == cleanValue.normalizedAutomationSourceKind(),
            )
            AutomationConditionType.AttentionKind -> AutomationConditionMatch(event.attentionKind.matchesAutomationTokenValue(cleanValue))
            AutomationConditionType.NotificationType -> AutomationConditionMatch(event.notificationType.matchesAutomationTokenValue(cleanValue))
            AutomationConditionType.ChannelId -> AutomationConditionMatch(event.channelId.equals(cleanValue, ignoreCase = true))
            AutomationConditionType.ChannelNameContains -> AutomationConditionMatch(event.channelName.contains(cleanValue, ignoreCase = true))
            AutomationConditionType.MessageType -> AutomationConditionMatch(event.messageType.matchesAutomationTokenValue(cleanValue))
            AutomationConditionType.TimelineKind -> AutomationConditionMatch(event.timelineKind.matchesAutomationTokenValue(cleanValue))
            AutomationConditionType.NoteVisibility -> AutomationConditionMatch(event.noteVisibility.matchesAutomationTokenValue(cleanValue))
            AutomationConditionType.AiSemantic -> when (val result = aiBridge.evaluateSemanticCondition(cleanValue, event.aiContextText())) {
                is AiBridgeResult.Success -> AutomationConditionMatch(automationAiConditionSatisfied(result.text))
                is AiBridgeResult.Error -> AutomationConditionMatch(false, "AI 语义条件失败：${result.message}")
            }
        }
    }

    private fun cleanAccountId(): String {
        return accountId?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
    }

    private fun nextId(prefix: String): String {
        nextLocalId += 1
        return "$prefix-${nowMillis()}-$nextLocalId"
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val MAX_RULE_NAME_LENGTH = 48
        const val MAX_COOLDOWN_SECONDS = 86_400
        const val MAX_LOG_MESSAGE_LENGTH = 400
        const val MAX_RECENT_EVENT_KEYS = 120
    }
}

private fun AutomationAction.requiresToolPermission(): Boolean {
    return type == AutomationActionType.ForwardToRoom ||
        type == AutomationActionType.ForwardToUser ||
        type == AutomationActionType.ReplyToChat ||
        type == AutomationActionType.AiReplyToChat ||
        type == AutomationActionType.ReplyToNote ||
        type == AutomationActionType.AiReplyToNote ||
        type == AutomationActionType.QuoteNote ||
        type == AutomationActionType.AiQuoteNote ||
        type == AutomationActionType.RenoteNote ||
        type == AutomationActionType.PostToChannel ||
        type == AutomationActionType.CopyChannelLink ||
        type == AutomationActionType.Webhook ||
        type == AutomationActionType.AiGenerateWebhook
}

private fun AutomationRule.cleaned(): AutomationRule {
    return copy(
        name = name.trim().ifBlank { defaultRuleName(trigger) }.take(48),
        conditions = conditions.map { it.cleaned() }.take(12),
        actions = actions.map { it.cleaned() }.take(12),
        cooldownSeconds = cooldownSeconds.coerceIn(0, 86_400),
    )
}

private fun AutomationCondition.cleaned(): AutomationCondition {
    return copy(value = value.trim().take(240))
}

private fun AutomationAction.cleaned(): AutomationAction {
    return copy(
        targetId = targetId.trim().take(400),
        titleTemplate = titleTemplate.take(160),
        bodyTemplate = bodyTemplate.take(1600),
        mentionSender = mentionSender && type.supportsSenderMention(),
        replyToEvent = replyToEvent && type.supportsChatReference(),
        quoteEvent = quoteEvent && type.supportsChatReference(),
    )
}

private fun AutomationActionType.supportsSenderMention(): Boolean {
    return this == AutomationActionType.ReplyToChat ||
        this == AutomationActionType.AiReplyToChat ||
        this == AutomationActionType.ReplyToNote ||
        this == AutomationActionType.AiReplyToNote ||
        this == AutomationActionType.QuoteNote ||
        this == AutomationActionType.AiQuoteNote ||
        this == AutomationActionType.PostToChannel
}

private fun AutomationActionType.supportsChatReference(): Boolean {
    return this == AutomationActionType.ReplyToChat || this == AutomationActionType.AiReplyToChat
}

private fun String.normalizedAutomationSourceKind(): String {
    return when (trim().lowercase().replace("_", "").replace("-", "")) {
        "chatroom", "room", "group", "聊天室", "群聊", "房间" -> "room"
        "private", "privatemessage", "directmessage", "user", "dm", "direct", "私聊", "用户" -> "direct"
        else -> trim().lowercase()
    }
}

private fun String.splitAutomationValues(): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '/', '、')
        .map { it.trim().trim('@') }
        .filter { it.isNotEmpty() }
}

private fun AutomationEvent.matchesAutomationUsername(value: String): Boolean {
    val candidates = buildList {
        val cleanUsername = senderUsername.trim().trim('@')
        val cleanHost = senderHost.trim()
        if (cleanUsername.isNotEmpty()) {
            add(cleanUsername)
            add("@$cleanUsername")
            if (cleanHost.isNotEmpty()) {
                add("$cleanUsername@$cleanHost")
                add("@$cleanUsername@$cleanHost")
            }
        }
    }
    return value.splitAutomationValues().any { expected ->
        candidates.any { candidate -> candidate.equals(expected, ignoreCase = true) }
    }
}

private fun AutomationEvent.matchesAutomationSenderName(value: String): Boolean {
    return value.splitAutomationValues().any { expected ->
        senderName.contains(expected, ignoreCase = true) ||
            senderUsername.contains(expected.trim('@'), ignoreCase = true) ||
            senderMention.contains(expected, ignoreCase = true)
    }
}

private fun String.matchesAutomationTokenValue(value: String): Boolean {
    val actualTokens = splitAutomationValues().map { it.normalizedAutomationToken() }.toSet()
    return value.splitAutomationValues().any { expected -> expected.normalizedAutomationToken() in actualTokens }
}

private fun String.normalizedAutomationToken(): String {
    return trim().lowercase().trim('@').let { clean ->
        when (clean) {
            "文字", "文本", "正文" -> "text"
            "图片", "图像", "照片", "photo", "picture" -> "image"
            "文件", "附件" -> "file"
            "视频" -> "video"
            "音频", "语音" -> "audio"
            "回复", "回帖" -> "reply"
            "引用", "转引" -> "quote"
            "投票" -> "poll"
            "频道" -> "channel"
            "首页" -> "home"
            "社交" -> "social"
            "本地" -> "local"
            "全局" -> "global"
            "精选" -> "featured"
            "提及", "@" -> "mention"
            "特别关心" -> "specialcare"
            "关注" -> "follow"
            "转发" -> "renote"
            "反应", "回应", "表情回应" -> "reaction"
            "公开" -> "public"
            "关注者" -> "followers"
            "指定" -> "specified"
            else -> clean
        }
    }
}

private fun defaultRuleName(trigger: AutomationTrigger): String {
    return when (trigger) {
        AutomationTrigger.ChatMessage -> "聊天消息规则"
        AutomationTrigger.ChatAttention -> "聊天提醒规则"
        AutomationTrigger.TimelineNote -> "帖子规则"
        AutomationTrigger.Notification -> "通知规则"
        AutomationTrigger.SpecialCare -> "特别关心规则"
    }
}

private fun defaultConditionFor(trigger: AutomationTrigger): AutomationCondition {
    return AutomationCondition(
        id = "condition-${Clock.System.now().toEpochMilliseconds()}",
        type = when (trigger) {
            AutomationTrigger.ChatMessage -> AutomationConditionType.MessageContains
            AutomationTrigger.ChatAttention -> AutomationConditionType.AttentionKind
            AutomationTrigger.TimelineNote -> AutomationConditionType.MessageContains
            AutomationTrigger.Notification -> AutomationConditionType.NotificationType
            AutomationTrigger.SpecialCare -> AutomationConditionType.SenderUserId
        },
    )
}

private fun defaultActionFor(trigger: AutomationTrigger): AutomationAction {
    return AutomationAction(
        id = "action-${Clock.System.now().toEpochMilliseconds()}",
        type = AutomationActionType.AddLog,
        bodyTemplate = defaultAutomationTemplate(AutomationEvent("preview", trigger)),
    )
}
