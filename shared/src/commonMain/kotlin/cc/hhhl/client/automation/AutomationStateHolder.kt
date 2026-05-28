package cc.hhhl.client.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
        val rules = state.value.rules
            .filter { rule -> rule.enabled && rule.trigger == event.trigger }
            .filter { rule -> !rule.ignoreOwnMessages || !event.isFromCurrentUser }
            .filter { rule -> rule.matches(event) }
            .filter { rule -> rule.cooldownReady(event) }
        if (rules.isEmpty()) return

        scope.launch {
            rules.forEach { rule ->
                val dedupeKey = "${event.id}:${rule.id}"
                if (dedupeKey in recentEventRuleKeys) return@forEach
                recentEventRuleKeys = (listOf(dedupeKey) + recentEventRuleKeys).take(MAX_RECENT_EVENT_KEYS)
                cooldownMarkers = cooldownMarkers + (rule.id to nowMillis())
                executeRule(rule, event)
            }
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

    private fun AutomationRule.matches(event: AutomationEvent): Boolean {
        val enabledConditions = conditions.filter { it.enabled }
        if (enabledConditions.isEmpty()) return true
        return when (conditionMode) {
            AutomationConditionMode.All -> enabledConditions.all { it.matches(event) }
            AutomationConditionMode.Any -> enabledConditions.any { it.matches(event) }
        }
    }

    private fun AutomationRule.cooldownReady(event: AutomationEvent): Boolean {
        if (cooldownSeconds <= 0) return true
        val lastRun = cooldownMarkers[id] ?: return true
        return nowMillis() - lastRun >= cooldownSeconds * 1000L || event.id.startsWith("manual:")
    }

    private fun AutomationCondition.matches(event: AutomationEvent): Boolean {
        val cleanValue = value.trim()
        if (cleanValue.isEmpty()) return true
        return when (type) {
            AutomationConditionType.SenderUserId -> event.senderUserId.equals(cleanValue, ignoreCase = true)
            AutomationConditionType.SenderNameContains -> event.senderName.contains(cleanValue, ignoreCase = true)
            AutomationConditionType.MessageContains -> event.defaultBody.contains(cleanValue, ignoreCase = true)
            AutomationConditionType.RoomId -> event.roomId.equals(cleanValue, ignoreCase = true)
            AutomationConditionType.DirectUserId -> event.directUserId.equals(cleanValue, ignoreCase = true)
            AutomationConditionType.SourceKind -> event.sourceKind.equals(cleanValue, ignoreCase = true)
            AutomationConditionType.AttentionKind -> event.attentionKind.equals(cleanValue, ignoreCase = true)
            AutomationConditionType.NotificationType -> event.notificationType.equals(cleanValue, ignoreCase = true)
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
    )
}

private fun defaultRuleName(trigger: AutomationTrigger): String {
    return when (trigger) {
        AutomationTrigger.ChatMessage -> "聊天消息规则"
        AutomationTrigger.ChatAttention -> "聊天提醒规则"
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
