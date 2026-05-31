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
    val actualValue: String = "",
    val detailMessage: String = "",
)

private data class AutomationRuleMatchEvaluation(
    val matched: Boolean,
    val reason: String,
    val conditionResults: List<AutomationConditionDebugResult> = emptyList(),
)

data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<AutomationExecutionLog> = emptyList(),
    val debugRecords: List<AutomationRuleDebugRecord> = emptyList(),
    val pendingDraftPreview: AutomationRuleDraftPreview? = null,
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
    private var ruleExecutionWindows: Map<String, List<Long>> = emptyMap()
    private var recentEventRuleKeys: List<String> = emptyList()
    private var nextLocalId = 0

    fun restore() {
        val snapshot = runCatching { store.read(cleanAccountId()) }.getOrDefault(AutomationSnapshot())
        mutableState.update {
            it.copy(
                rules = snapshot.rules.sortedByDescending { rule -> rule.updatedAtEpochMillis },
                logs = snapshot.logs.sortedByDescending { log -> log.createdAtEpochMillis },
                debugRecords = snapshot.debugRecords.sortedByDescending { record -> record.createdAtEpochMillis },
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
        ).withDefaultRiskControls()
        updateRules(
            transform = { rules -> listOf(rule) + rules },
            selectedRuleId = rule.id,
            editorOpen = true,
            message = "已创建规则",
        )
    }

    fun addRuleDraft(rule: AutomationRule) {
        confirmRuleDraft(
            AutomationRuleDraftPreview(
                id = nextId("draft-preview"),
                sourceText = "",
                rule = rule,
                createdAtEpochMillis = nowMillis(),
            ),
        )
    }

    fun previewRuleDraft(
        sourceText: String,
        rule: AutomationRule,
        messages: List<String>,
    ) {
        val preview = AutomationRuleDraftPreview(
            id = nextId("draft-preview"),
            sourceText = sourceText.trim().take(MAX_DRAFT_SOURCE_TEXT_LENGTH),
            rule = rule.cleaned(),
            messages = messages.distinct().take(MAX_DRAFT_MESSAGES),
            createdAtEpochMillis = nowMillis(),
        )
        mutableState.update {
            it.copy(
                pendingDraftPreview = preview,
                selectedRuleId = null,
                editorOpen = false,
                message = "AI 已生成规则草稿，确认后再创建",
                errorMessage = null,
            )
        }
    }

    fun approveRuleDraft() {
        val preview = state.value.pendingDraftPreview ?: return
        confirmRuleDraft(preview)
    }

    fun rejectRuleDraft() {
        mutableState.update {
            it.copy(pendingDraftPreview = null, message = "已拒绝 AI 规则草稿", errorMessage = null)
        }
    }

    private fun confirmRuleDraft(preview: AutomationRuleDraftPreview) {
        val now = nowMillis()
        val draft = preview.rule.copy(
            id = nextId("rule"),
            conditions = preview.rule.conditions.map { condition -> condition.copy(id = nextId("condition")) },
            actions = preview.rule.actions.map { action -> action.copy(id = nextId("action")) },
            updatedAtEpochMillis = now,
        ).cleaned()
        updateRules(
            transform = { rules -> listOf(draft) + rules },
            selectedRuleId = draft.id,
            editorOpen = true,
            message = if (draft.enabled) "AI 已生成并启用规则草稿" else "AI 已生成规则草稿",
            pendingDraftPreview = null,
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

    fun updateRuleBurstLimit(ruleId: String, limit: Int) {
        updateRule(ruleId) { rule ->
            rule.copy(maxExecutionsPer30Seconds = limit.coerceIn(0, MAX_BURST_EXECUTIONS_PER_30_SECONDS), updatedAtEpochMillis = nowMillis())
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

    fun clearDebugRecords() {
        mutableState.update { it.copy(debugRecords = emptyList(), message = "调试记录已清空", errorMessage = null) }
        persist()
    }

    fun emit(event: AutomationEvent) {
        scope.launch {
            emitNow(event)
        }
    }

    fun simulate(event: AutomationEvent) {
        scope.launch {
            simulateNow(event)
        }
    }

    suspend fun simulateNow(event: AutomationEvent) {
        val simulationEvent = event.copy(id = "manual-simulate:${event.id}")
        val rules = state.value.rules
        if (rules.isEmpty()) {
            mutableState.update { it.copy(message = "暂无规则可模拟", errorMessage = null) }
            return
        }

        var matchedCount = 0
        rules.forEach { rule ->
            when {
                !rule.enabled -> addDebugRecord(rule, simulationEvent, matched = false, reason = "模拟运行：规则未启用")
                rule.trigger != simulationEvent.trigger -> addDebugRecord(
                    rule = rule,
                    event = simulationEvent,
                    matched = false,
                    reason = "模拟运行：触发器不匹配，规则 ${rule.trigger.label}，事件 ${simulationEvent.trigger.label}",
                )
                rule.ignoreOwnMessages && simulationEvent.isFromCurrentUser -> addDebugRecord(
                    rule = rule,
                    event = simulationEvent,
                    matched = false,
                    reason = "模拟运行：已按规则设置忽略自己发送的消息",
                )
                else -> {
                    val evaluation = rule.evaluateMatch(simulationEvent)
                    if (evaluation.matched) matchedCount += 1
                    addDebugRecord(
                        rule = rule,
                        event = simulationEvent,
                        matched = evaluation.matched,
                        reason = "模拟运行：${evaluation.reason}，未执行任何动作",
                        conditionResults = evaluation.conditionResults,
                    )
                }
            }
        }
        mutableState.update {
            it.copy(
                message = "模拟完成：${matchedCount}/${rules.size} 条规则会命中，未执行动作",
                errorMessage = null,
            )
        }
        persist()
    }

    suspend fun emitNow(event: AutomationEvent) {
        val rules = state.value.rules
        if (rules.isEmpty()) return

        rules.forEach { rule ->
            if (!rule.enabled) {
                addDebugRecord(rule, event, matched = false, reason = "规则未启用")
                return@forEach
            }
            if (rule.trigger != event.trigger) {
                addDebugRecord(rule, event, matched = false, reason = "触发器不匹配：规则 ${rule.trigger.label}，事件 ${event.trigger.label}")
                return@forEach
            }
            if (rule.ignoreOwnMessages && event.isFromCurrentUser) {
                addDebugRecord(rule, event, matched = false, reason = "已按规则设置忽略自己发送的消息")
                return@forEach
            }
            if (event.isAiGenerated) {
                addDebugRecord(rule, event, matched = false, reason = "已忽略 AI 产生的消息，避免自动化循环")
                return@forEach
            }
            if (!rule.cooldownReady(event)) {
                addDebugRecord(rule, event, matched = false, reason = "规则冷却中，${rule.cooldownSeconds}s 内不会重复触发")
                return@forEach
            }
            if (!rule.burstLimitReady(event)) {
                addDebugRecord(
                    rule = rule,
                    event = event,
                    matched = false,
                    reason = "风控拦截：30 秒内执行次数已达到 ${rule.maxExecutionsPer30Seconds} 次",
                )
                addExecutionLog(rule, event, "风控保护", "30 秒内执行次数已达到上限，已阻止自动动作", success = false)
                return@forEach
            }
            val evaluation = rule.evaluateMatch(event)
            if (!evaluation.matched) {
                addDebugRecord(
                    rule = rule,
                    event = event,
                    matched = false,
                    reason = evaluation.reason,
                    conditionResults = evaluation.conditionResults,
                )
                return@forEach
            }
            val dedupeKey = "${event.id}:${rule.id}"
            if (dedupeKey in recentEventRuleKeys) {
                addDebugRecord(
                    rule = rule,
                    event = event,
                    matched = false,
                    reason = "同一事件和规则已经处理过，已去重跳过",
                    conditionResults = evaluation.conditionResults,
                )
                return@forEach
            }
            addDebugRecord(
                rule = rule,
                event = event,
                matched = true,
                reason = evaluation.reason,
                conditionResults = evaluation.conditionResults,
            )
            recentEventRuleKeys = (listOf(dedupeKey) + recentEventRuleKeys).take(MAX_RECENT_EVENT_KEYS)
            cooldownMarkers = cooldownMarkers + (rule.id to nowMillis())
            recordRuleExecution(rule)
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
        retryOfLogId: String = "",
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
                eventSnapshot = event.snapshot(),
                actionSnapshot = action,
                retryOfLogId = retryOfLogId,
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
            eventSnapshot = event.snapshot(),
            actionSnapshot = action,
            retryOfLogId = retryOfLogId,
        )
        return result.success
    }

    fun retryLog(logId: String) {
        scope.launch {
            retryLogNow(logId)
        }
    }

    suspend fun retryLogNow(logId: String) {
        val log = state.value.logs.firstOrNull { it.id == logId }
        if (log == null) {
            mutableState.update { it.copy(errorMessage = "未找到要重试的日志") }
            return
        }
        val action = log.actionSnapshot
        val event = log.eventSnapshot?.toEvent()
        val rule = state.value.rules.firstOrNull { it.id == log.ruleId }
        when {
            log.success -> mutableState.update { it.copy(errorMessage = "成功日志不需要重试") }
            action == null || event == null -> mutableState.update { it.copy(errorMessage = "这条日志没有可重试的事件或动作快照") }
            rule == null -> mutableState.update { it.copy(errorMessage = "原规则已不存在，无法重试") }
            else -> {
                mutableState.update { it.copy(message = "正在重试自动化动作", errorMessage = null) }
                executeAction(rule, action, event.copy(id = "manual-retry:${event.id}"), retryOfLogId = log.id)
            }
        }
    }

    private fun addExecutionLog(
        rule: AutomationRule,
        event: AutomationEvent,
        actionLabel: String,
        message: String,
        success: Boolean,
        eventSnapshot: AutomationEventSnapshot? = null,
        actionSnapshot: AutomationAction? = null,
        retryOfLogId: String = "",
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
            eventSnapshot = eventSnapshot,
            actionSnapshot = actionSnapshot,
            retryOfLogId = retryOfLogId,
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

    private fun addDebugRecord(
        rule: AutomationRule,
        event: AutomationEvent,
        matched: Boolean,
        reason: String,
        conditionResults: List<AutomationConditionDebugResult> = emptyList(),
    ) {
        val record = AutomationRuleDebugRecord(
            id = nextId("debug"),
            ruleId = rule.id,
            ruleName = rule.name,
            eventId = event.id,
            eventLabel = event.displayLabel,
            eventSummary = event.debugSummary().take(MAX_DEBUG_TEXT_LENGTH),
            matched = matched,
            reason = reason.take(MAX_DEBUG_TEXT_LENGTH),
            conditionResults = conditionResults.take(MAX_DEBUG_CONDITIONS),
            resolvedEntities = event.debugResolvedEntities().take(MAX_DEBUG_RESOLVED_ENTITIES),
            createdAtEpochMillis = nowMillis(),
        )
        mutableState.update {
            it.copy(
                debugRecords = (listOf(record) + it.debugRecords).take(AutomationStoreCodec.MAX_DEBUG_RECORDS),
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
        pendingDraftPreview: AutomationRuleDraftPreview? = state.value.pendingDraftPreview,
    ) {
        mutableState.update {
            val nextRules = transform(it.rules)
                .map { rule -> rule.cleaned() }
                .take(AutomationStoreCodec.MAX_RULES)
            it.copy(
                rules = nextRules,
                selectedRuleId = selectedRuleId,
                editorOpen = editorOpen,
                pendingDraftPreview = pendingDraftPreview,
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
                    debugRecords = current.debugRecords,
                ),
            )
        }
    }

    private suspend fun AutomationRule.evaluateMatch(event: AutomationEvent): AutomationRuleMatchEvaluation {
        val enabledConditions = conditions.filter { it.enabled }
        if (enabledConditions.isEmpty()) {
            return AutomationRuleMatchEvaluation(matched = true, reason = "没有启用条件，事件直接命中规则")
        }
        return when (conditionMode) {
            AutomationConditionMode.All -> {
                val conditionResults = mutableListOf<AutomationConditionDebugResult>()
                for (condition in enabledConditions) {
                    val result = condition.matches(event)
                    conditionResults += condition.toDebugResult(result)
                    result.failureMessage?.let { message ->
                        addExecutionLog(this, event, "条件判断", message, success = false)
                    }
                    if (!result.matched) {
                        return AutomationRuleMatchEvaluation(
                            matched = false,
                            reason = "条件未满足：${condition.type.label}",
                            conditionResults = conditionResults,
                        )
                    }
                }
                AutomationRuleMatchEvaluation(
                    matched = true,
                    reason = "全部 ${conditionResults.size} 个条件已满足",
                    conditionResults = conditionResults,
                )
            }
            AutomationConditionMode.Any -> {
                val failureMessages = mutableListOf<String>()
                val conditionResults = mutableListOf<AutomationConditionDebugResult>()
                for (condition in enabledConditions) {
                    val result = condition.matches(event)
                    conditionResults += condition.toDebugResult(result)
                    if (result.matched) {
                        return AutomationRuleMatchEvaluation(
                            matched = true,
                            reason = "条件已命中：${condition.type.label}",
                            conditionResults = conditionResults,
                        )
                    }
                    result.failureMessage?.let(failureMessages::add)
                }
                failureMessages.firstOrNull()?.let { message ->
                    addExecutionLog(this, event, "条件判断", message, success = false)
                }
                AutomationRuleMatchEvaluation(
                    matched = false,
                    reason = "任一条件均未满足",
                    conditionResults = conditionResults,
                )
            }
        }
    }

    private fun AutomationRule.cooldownReady(event: AutomationEvent): Boolean {
        if (cooldownSeconds <= 0) return true
        val lastRun = cooldownMarkers[id] ?: return true
        return nowMillis() - lastRun >= cooldownSeconds * 1000L || event.id.startsWith("manual:")
    }

    private fun AutomationRule.burstLimitReady(event: AutomationEvent): Boolean {
        if (maxExecutionsPer30Seconds <= 0 || event.id.startsWith("manual:")) return true
        val now = nowMillis()
        val windowStart = now - BURST_WINDOW_MILLIS
        val recent = ruleExecutionWindows[id].orEmpty().filter { timestamp -> timestamp >= windowStart }
        ruleExecutionWindows = ruleExecutionWindows + (id to recent)
        return recent.size < maxExecutionsPer30Seconds
    }

    private fun recordRuleExecution(rule: AutomationRule) {
        val now = nowMillis()
        val windowStart = now - BURST_WINDOW_MILLIS
        val recent = (ruleExecutionWindows[rule.id].orEmpty() + now)
            .filter { timestamp -> timestamp >= windowStart }
            .takeLast(MAX_BURST_EXECUTIONS_PER_30_SECONDS)
        ruleExecutionWindows = ruleExecutionWindows + (rule.id to recent)
    }

    private suspend fun AutomationCondition.matches(event: AutomationEvent): AutomationConditionMatch {
        val cleanValue = value.trim()
        if (cleanValue.isEmpty()) return AutomationConditionMatch(
            matched = true,
            actualValue = event.debugActualValueFor(type),
            detailMessage = "匹配值为空，视为通过",
        )
        return when (type) {
            AutomationConditionType.SenderUserId -> AutomationConditionMatch(
                matched = event.senderUserId.equals(cleanValue, ignoreCase = true),
                actualValue = event.senderUserId,
            )
            AutomationConditionType.SenderUserIds -> AutomationConditionMatch(
                matched = cleanValue.splitAutomationValues().any { value -> event.senderUserId.equals(value, ignoreCase = true) },
                actualValue = event.senderUserId,
            )
            AutomationConditionType.SenderUsername -> AutomationConditionMatch(
                matched = event.matchesAutomationUsername(cleanValue),
                actualValue = event.senderMention.ifBlank { event.senderUsername },
            )
            AutomationConditionType.SenderNameContains -> AutomationConditionMatch(
                matched = event.matchesAutomationSenderName(cleanValue),
                actualValue = listOf(event.senderName, event.senderUsername, event.senderMention).filter { it.isNotBlank() }.joinToString(" / "),
            )
            AutomationConditionType.MessageContains -> AutomationConditionMatch(
                matched = event.defaultBody.contains(cleanValue, ignoreCase = true),
                actualValue = event.defaultBody,
            )
            AutomationConditionType.RoomId -> AutomationConditionMatch(
                matched = event.roomId.equals(cleanValue, ignoreCase = true),
                actualValue = event.roomId,
            )
            AutomationConditionType.RoomNameContains -> AutomationConditionMatch(
                matched = event.roomName.contains(cleanValue, ignoreCase = true),
                actualValue = event.roomName,
            )
            AutomationConditionType.DirectUserId -> AutomationConditionMatch(
                matched = event.directUserId.equals(cleanValue, ignoreCase = true),
                actualValue = event.directUserId,
            )
            AutomationConditionType.SourceKind -> AutomationConditionMatch(
                matched = event.sourceKind.normalizedAutomationSourceKind() == cleanValue.normalizedAutomationSourceKind(),
                actualValue = event.sourceKind,
            )
            AutomationConditionType.AttentionKind -> AutomationConditionMatch(
                matched = event.attentionKind.matchesAutomationTokenValue(cleanValue),
                actualValue = event.attentionKind,
            )
            AutomationConditionType.NotificationType -> AutomationConditionMatch(
                matched = event.notificationType.matchesAutomationTokenValue(cleanValue),
                actualValue = event.notificationType,
            )
            AutomationConditionType.ChannelId -> AutomationConditionMatch(
                matched = event.channelId.equals(cleanValue, ignoreCase = true),
                actualValue = event.channelId,
            )
            AutomationConditionType.ChannelNameContains -> AutomationConditionMatch(
                matched = event.channelName.contains(cleanValue, ignoreCase = true),
                actualValue = event.channelName,
            )
            AutomationConditionType.MessageType -> AutomationConditionMatch(
                matched = event.messageType.matchesAutomationTokenValue(cleanValue),
                actualValue = event.messageType,
            )
            AutomationConditionType.TimelineKind -> AutomationConditionMatch(
                matched = event.timelineKind.matchesAutomationTokenValue(cleanValue),
                actualValue = event.timelineKind,
            )
            AutomationConditionType.NoteVisibility -> AutomationConditionMatch(
                matched = event.noteVisibility.matchesAutomationTokenValue(cleanValue),
                actualValue = event.noteVisibility,
            )
            AutomationConditionType.AiSemantic -> when (val result = aiBridge.evaluateSemanticCondition(cleanValue, event.aiContextText())) {
                is AiBridgeResult.Success -> AutomationConditionMatch(
                    matched = automationAiConditionSatisfied(result.text),
                    actualValue = result.text,
                    detailMessage = "AI 返回：${result.text}",
                )
                is AiBridgeResult.Error -> AutomationConditionMatch(
                    matched = false,
                    failureMessage = "AI 语义条件失败：${result.message}",
                    actualValue = result.message,
                    detailMessage = "AI 语义条件失败：${result.message}",
                )
            }
        }
    }

    private fun AutomationCondition.toDebugResult(match: AutomationConditionMatch): AutomationConditionDebugResult {
        val expected = value.trim()
        return AutomationConditionDebugResult(
            conditionId = id,
            conditionLabel = type.label,
            expectedValue = expected,
            actualValue = match.actualValue.ifBlank { "无" }.take(MAX_DEBUG_TEXT_LENGTH),
            matched = match.matched,
            message = match.detailMessage.ifBlank {
                if (match.matched) "已命中 ${type.label}" else "未命中 ${type.label}：期望 ${expected.ifBlank { "空" }}，实际 ${match.actualValue.ifBlank { "无" }}"
            }.take(MAX_DEBUG_TEXT_LENGTH),
        )
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
        const val MAX_BURST_EXECUTIONS_PER_30_SECONDS = 60
        const val BURST_WINDOW_MILLIS = 30_000L
        const val MAX_LOG_MESSAGE_LENGTH = 400
        const val MAX_RECENT_EVENT_KEYS = 120
        const val MAX_DEBUG_CONDITIONS = 12
        const val MAX_DEBUG_RESOLVED_ENTITIES = 8
        const val MAX_DEBUG_TEXT_LENGTH = 360
        const val MAX_DRAFT_SOURCE_TEXT_LENGTH = 1200
        const val MAX_DRAFT_MESSAGES = 12
    }
}

private fun AutomationAction.requiresToolPermission(): Boolean {
    return type == AutomationActionType.ForwardToRoom ||
        type == AutomationActionType.AiForwardToRoom ||
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
    val defaulted = withDefaultRiskControls()
    return defaulted.copy(
        name = name.trim().ifBlank { defaultRuleName(trigger) }.take(48),
        conditions = defaulted.conditions.map { it.cleaned() }.take(12),
        actions = defaulted.actions.map { it.cleaned() }.take(12),
        cooldownSeconds = defaulted.cooldownSeconds.coerceIn(0, 86_400),
        maxExecutionsPer30Seconds = defaulted.maxExecutionsPer30Seconds.coerceIn(0, 60),
    )
}

private fun AutomationRule.withDefaultRiskControls(): AutomationRule {
    val hasRiskyAction = actions.any { action -> action.type.isLoopRiskAction() || action.mentionSender }
    return copy(
        ignoreOwnMessages = ignoreOwnMessages || hasRiskyAction,
        cooldownSeconds = when {
            cooldownSeconds > 0 -> cooldownSeconds
            hasRiskyAction -> DEFAULT_RISKY_ACTION_COOLDOWN_SECONDS
            else -> cooldownSeconds
        },
        maxExecutionsPer30Seconds = when {
            maxExecutionsPer30Seconds > 0 -> maxExecutionsPer30Seconds
            hasRiskyAction -> DEFAULT_RISKY_ACTION_BURST_LIMIT
            else -> DEFAULT_SAFE_ACTION_BURST_LIMIT
        },
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

private fun AutomationActionType.isLoopRiskAction(): Boolean {
    return this == AutomationActionType.ForwardToRoom ||
        this == AutomationActionType.AiForwardToRoom ||
        this == AutomationActionType.ForwardToUser ||
        this == AutomationActionType.ReplyToChat ||
        this == AutomationActionType.AiReplyToChat ||
        this == AutomationActionType.ReplyToNote ||
        this == AutomationActionType.AiReplyToNote ||
        this == AutomationActionType.QuoteNote ||
        this == AutomationActionType.AiQuoteNote ||
        this == AutomationActionType.RenoteNote ||
        this == AutomationActionType.PostToChannel ||
        this == AutomationActionType.Webhook ||
        this == AutomationActionType.AiGenerateWebhook
}

private const val DEFAULT_RISKY_ACTION_COOLDOWN_SECONDS = 30
private const val DEFAULT_RISKY_ACTION_BURST_LIMIT = 2
private const val DEFAULT_SAFE_ACTION_BURST_LIMIT = 12

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

private fun AutomationEvent.debugSummary(): String {
    return buildList {
        add(displayLabel)
        if (roomName.isNotBlank() || roomId.isNotBlank()) add("聊天室 ${roomName.ifBlank { roomId }}")
        if (directUserId.isNotBlank()) add("私聊用户 $directUserId")
        if (channelName.isNotBlank() || channelId.isNotBlank()) add("频道 ${channelName.ifBlank { channelId }}")
        if (senderName.isNotBlank() || senderUsername.isNotBlank() || senderUserId.isNotBlank()) {
            add("发送者 ${senderName.ifBlank { senderMention.ifBlank { senderUserId } }}")
        }
        if (messageType.isNotBlank()) add("类型 $messageType")
        if (attentionKind.isNotBlank()) add("提醒 $attentionKind")
        if (notificationType.isNotBlank()) add("通知 $notificationType")
        defaultBody.takeIf { it.isNotBlank() }?.let { body -> add(body.take(96)) }
    }.joinToString(" · ")
}

private fun AutomationEvent.debugResolvedEntities(): List<String> {
    return buildList {
        if (roomId.isNotBlank() || roomName.isNotBlank()) add("聊天室 ${roomName.ifBlank { "未命名" }} -> ${roomId.ifBlank { "未解析" }}")
        if (senderUserId.isNotBlank() || senderUsername.isNotBlank() || senderName.isNotBlank()) {
            add("发送者 ${senderName.ifBlank { senderMention.ifBlank { senderUsername.ifBlank { "未命名" } } }} -> ${senderUserId.ifBlank { "未解析" }}")
        }
        if (directUserId.isNotBlank()) add("私聊目标 -> $directUserId")
        if (channelId.isNotBlank() || channelName.isNotBlank()) add("频道 ${channelName.ifBlank { "未命名" }} -> ${channelId.ifBlank { "未解析" }}")
        if (noteId.isNotBlank()) add("帖子 -> $noteId")
        if (chatMessageId.isNotBlank()) add("消息 -> $chatMessageId")
    }.distinct()
}

private fun AutomationEvent.debugActualValueFor(type: AutomationConditionType): String {
    return when (type) {
        AutomationConditionType.SenderUserId,
        AutomationConditionType.SenderUserIds,
            -> senderUserId
        AutomationConditionType.SenderUsername -> senderMention.ifBlank { senderUsername }
        AutomationConditionType.SenderNameContains -> listOf(senderName, senderUsername, senderMention).filter { it.isNotBlank() }.joinToString(" / ")
        AutomationConditionType.MessageContains -> defaultBody
        AutomationConditionType.RoomId -> roomId
        AutomationConditionType.RoomNameContains -> roomName
        AutomationConditionType.DirectUserId -> directUserId
        AutomationConditionType.SourceKind -> sourceKind
        AutomationConditionType.AttentionKind -> attentionKind
        AutomationConditionType.NotificationType -> notificationType
        AutomationConditionType.ChannelId -> channelId
        AutomationConditionType.ChannelNameContains -> channelName
        AutomationConditionType.MessageType -> messageType
        AutomationConditionType.TimelineKind -> timelineKind
        AutomationConditionType.NoteVisibility -> noteVisibility
        AutomationConditionType.AiSemantic -> aiContextText()
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
