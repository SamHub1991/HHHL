package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.automation.AutomationAction
import cc.hhhl.client.automation.AutomationActionMode
import cc.hhhl.client.automation.AutomationActionType
import cc.hhhl.client.automation.AutomationCondition
import cc.hhhl.client.automation.AutomationConditionMode
import cc.hhhl.client.automation.AutomationConditionType
import cc.hhhl.client.automation.AutomationEventSnapshot
import cc.hhhl.client.automation.AutomationExecutionLog
import cc.hhhl.client.automation.AutomationFailurePolicy
import cc.hhhl.client.automation.AutomationRuleDraftPreview
import cc.hhhl.client.automation.AutomationRuleDebugRecord
import cc.hhhl.client.automation.AutomationRule
import cc.hhhl.client.automation.AutomationTrigger
import cc.hhhl.client.automation.AutomationUiState
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AiResultCommonActionChips
import cc.hhhl.client.ui.component.AiResultPanel
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlAlertDialog
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlSwitch
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    onBack: () -> Unit,
    onCreateRule: (AutomationTrigger) -> Unit,
    onOpenRule: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onToggleRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDuplicateRule: (String) -> Unit,
    onUpdateRuleName: (String, String) -> Unit,
    onUpdateRuleTrigger: (String, AutomationTrigger) -> Unit,
    onUpdateConditionMode: (String, AutomationConditionMode) -> Unit,
    onUpdateActionMode: (String, AutomationActionMode) -> Unit,
    onUpdateIgnoreOwnMessages: (String, Boolean) -> Unit,
    onUpdateCooldown: (String, Int) -> Unit,
    onUpdateBurstLimit: (String, Int) -> Unit,
    onAddCondition: (String, AutomationConditionType) -> Unit,
    onUpdateCondition: (String, AutomationCondition) -> Unit,
    onRemoveCondition: (String, String) -> Unit,
    onAddAction: (String, AutomationActionType) -> Unit,
    onUpdateAction: (String, AutomationAction) -> Unit,
    onRemoveAction: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    onClearDebugRecords: () -> Unit,
    recentChatMessages: List<ChatMessage> = emptyList(),
    onSimulateChatMessage: (ChatMessage) -> Unit = {},
    onOpenLogs: () -> Unit,
    onApproveRuleDraft: () -> Unit,
    onRejectRuleDraft: () -> Unit,
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiExplainRule: (AutomationRule?) -> Unit = {},
    onAiSuggestRules: (AutomationUiState) -> Unit = {},
    onAiCreateRule: (String, AutomationUiState) -> Unit = { _, _ -> },
    onCopyAiResult: ((String) -> Unit)? = null,
    onDismissAiResult: () -> Unit = {},
) {
    val colors = LocalHhhlColors.current
    var createMenuOpen by remember { mutableStateOf(false) }
    var debugDialogOpen by remember { mutableStateOf(false) }
    val latestDebugRecordsByRuleId = remember(state.debugRecords) {
        state.debugRecords.latestAutomationDebugRecordByRuleId()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        HhhlTopBar(
            title = "高级自动化",
            supportingText = "${state.enabledRuleCount}/${state.rules.size} 条规则启用",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Tune,
                        contentDescription = "规则调试台",
                        onClick = { debugDialogOpen = true },
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "新建规则",
                        onClick = { createMenuOpen = true },
                    )
                }
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item(key = "automation-overview") {
                AutomationOverviewCard(
                    state = state,
                    aiEnabled = aiEnabled,
                    isAiProcessing = isAiProcessing,
                    onCreate = { createMenuOpen = true },
                    onOpenDebug = { debugDialogOpen = true },
                    onOpenLogs = onOpenLogs,
                    onAiExplain = { onAiExplainRule(state.selectedRule ?: state.rules.firstOrNull()) },
                    onAiSuggestRules = { onAiSuggestRules(state) },
                    onAiCreateRule = { description -> onAiCreateRule(description, state) },
                )
            }
            if (!aiResultText.isNullOrBlank()) {
                item(key = "automation-ai-result") {
                    AutomationAiResultCard(
                        label = aiResultLabel ?: AiTaskKind.AutomationExplain.label,
                        text = aiResultText,
                        onCopyAiResult = onCopyAiResult,
                        onCreateAutomationRule = { description -> onAiCreateRule(description, state) },
                        onDismiss = onDismissAiResult,
                    )
                }
            }
            state.pendingDraftPreview?.let { preview ->
                item(key = "automation-draft-preview-${preview.id}") {
                    AutomationRuleDraftPreviewCard(
                        preview = preview,
                        onApprove = onApproveRuleDraft,
                        onReject = onRejectRuleDraft,
                    )
                }
            }
            state.message?.let { message ->
                item(key = "automation-message") { AutomationStatusCard(message, success = true) }
            }
            state.errorMessage?.let { message ->
                item(key = "automation-error") { AutomationStatusCard(message, success = false) }
            }
            if (state.rules.isEmpty()) {
                item(key = "automation-empty") {
                    AutomationEmptyCard(onCreate = { createMenuOpen = true })
                }
            }
            items(
                items = state.rules,
                key = { it.id },
                contentType = { "automation-rule" },
            ) { rule ->
                AutomationRuleCard(
                    rule = rule,
                    latestDebugRecord = latestDebugRecordsByRuleId[rule.id],
                    onOpen = { onOpenRule(rule.id) },
                    onToggle = { onToggleRule(rule.id) },
                    onDuplicate = { onDuplicateRule(rule.id) },
                    onDelete = { onDeleteRule(rule.id) },
                    aiEnabled = aiEnabled,
                    isAiProcessing = isAiProcessing,
                    onAiExplain = { onAiExplainRule(rule) },
                )
            }
            item(key = "automation-debug-header") {
                AutomationSectionHeader(
                    title = "规则调试台",
                    trailing = {
                        HhhlActionChip(
                            label = "清空",
                            enabled = state.debugRecords.isNotEmpty(),
                            onClick = onClearDebugRecords,
                        )
                    },
                )
            }
            item(key = "automation-debug-simulator") {
                AutomationHistorySimulationCard(
                    messages = recentChatMessages,
                    onSimulate = onSimulateChatMessage,
                )
            }
            if (state.debugRecords.isEmpty()) {
                item(key = "automation-debug-empty") { AutomationStatusCard("暂无规则匹配记录", success = true) }
            } else {
                items(
                    items = state.debugRecords.take(20),
                    key = { it.id },
                    contentType = { "automation-debug-record" },
                ) { record ->
                    AutomationDebugRecordCard(record = record)
                }
            }
            item(key = "automation-log-header") {
                AutomationSectionHeader(
                    title = "执行日志",
                    trailing = {
                        HhhlActionChip(label = "清空", enabled = state.logs.isNotEmpty(), onClick = onClearLogs)
                    },
                )
            }
            if (state.logs.isEmpty()) {
                item(key = "automation-log-empty") { AutomationStatusCard("暂无执行记录", success = true) }
            } else {
                items(
                    items = state.logs.take(20),
                    key = { it.id },
                    contentType = { "automation-log" },
                ) { log ->
                    AutomationLogCard(
                        title = log.ruleName,
                        subtitle = "${log.eventLabel} · ${log.actionLabel}",
                        message = log.message,
                        success = log.success,
                    )
                }
            }
        }
    }

    if (createMenuOpen) {
        AutomationCreateRuleDialog(
            onDismiss = { createMenuOpen = false },
            onCreate = { trigger ->
                createMenuOpen = false
                onCreateRule(trigger)
            },
        )
    }

    if (debugDialogOpen) {
        AutomationDebugDialog(
            debugRecords = state.debugRecords,
            recentChatMessages = recentChatMessages,
            onSimulateChatMessage = onSimulateChatMessage,
            onClearDebugRecords = onClearDebugRecords,
            onDismiss = { debugDialogOpen = false },
        )
    }

    val selectedRule = state.selectedRule
    if (state.editorOpen && selectedRule != null) {
        AutomationRuleEditorDialog(
            rule = selectedRule,
            onDismiss = onCloseEditor,
            onUpdateName = { onUpdateRuleName(selectedRule.id, it) },
            onUpdateTrigger = { onUpdateRuleTrigger(selectedRule.id, it) },
            onUpdateConditionMode = { onUpdateConditionMode(selectedRule.id, it) },
            onUpdateActionMode = { onUpdateActionMode(selectedRule.id, it) },
            onUpdateIgnoreOwnMessages = { onUpdateIgnoreOwnMessages(selectedRule.id, it) },
            onUpdateCooldown = { onUpdateCooldown(selectedRule.id, it) },
            onUpdateBurstLimit = { onUpdateBurstLimit(selectedRule.id, it) },
            onAddCondition = { onAddCondition(selectedRule.id, it) },
            onUpdateCondition = { onUpdateCondition(selectedRule.id, it) },
            onRemoveCondition = { onRemoveCondition(selectedRule.id, it) },
            onAddAction = { onAddAction(selectedRule.id, it) },
            onUpdateAction = { onUpdateAction(selectedRule.id, it) },
            onRemoveAction = { onRemoveAction(selectedRule.id, it) },
        )
    }
}

@Composable
private fun AutomationOverviewCard(
    state: AutomationUiState,
    onCreate: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenLogs: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiExplain: () -> Unit,
    onAiSuggestRules: () -> Unit,
    onAiCreateRule: (String) -> Unit,
) {
    var ruleGoal by remember { mutableStateOf("") }
    AutomationPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AutomationTitle("自动化中心")
                AutomationMutedText("按聊天、通知、特别关心触发动作。规则会缓存在本机账号下。")
            }
            HhhlActionChip(label = "新建", emphasized = true, onClick = onCreate)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationMetricChip("规则 ${state.rules.size}")
            AutomationMetricChip("启用 ${state.enabledRuleCount}")
            AutomationMetricChip("调试 ${state.debugRecords.size}")
            AutomationMetricChip("日志 ${state.logs.size}")
            HhhlActionChip(label = "调试台", emphasized = state.debugRecords.isNotEmpty(), onClick = onOpenDebug)
            HhhlActionChip(label = "执行日志", enabled = state.logs.isNotEmpty(), onClick = onOpenLogs)
            HhhlActionChip(
                label = if (isAiProcessing) "AI 处理中" else "AI 解释规则",
                enabled = aiEnabled && !isAiProcessing && state.rules.isNotEmpty(),
                onClick = onAiExplain,
            )
            HhhlActionChip(
                label = "AI 规则建议",
                enabled = aiEnabled && !isAiProcessing,
                onClick = onAiSuggestRules,
            )
        }
        HhhlTextInput(
            value = ruleGoal,
            onValueChange = { ruleGoal = it },
            placeholder = "一句话描述想自动完成的事",
            label = "AI 自动创建",
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HhhlActionChip(
                label = if (isAiProcessing) "AI 处理中" else "生成规则草稿",
                emphasized = true,
                enabled = aiEnabled && !isAiProcessing && ruleGoal.isNotBlank(),
                onClick = { onAiCreateRule(ruleGoal) },
            )
        }
    }
}

@Composable
private fun AutomationEmptyCard(onCreate: () -> Unit) {
    AutomationPanel {
        AutomationTitle("还没有规则")
        AutomationMutedText("可以从聊天消息、聊天提醒、帖子、通知或特别关心开始。每条规则可以配置多个条件和多个动作。")
        HhhlActionChip(label = "创建第一条规则", emphasized = true, onClick = onCreate)
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRule,
    latestDebugRecord: AutomationRuleDebugRecord?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiExplain: () -> Unit,
) {
    AutomationPanel(
        modifier = Modifier.clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AutomationTitle(rule.name)
                AutomationMutedText("${rule.trigger.label} · ${rule.conditions.size} 条件 · ${rule.actions.size} 动作")
            }
            HhhlSwitch(checked = rule.enabled, onCheckedChange = { onToggle() })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationMetricChip(rule.conditionMode.label)
            AutomationMetricChip(rule.actionMode.label)
            if (rule.cooldownSeconds > 0) AutomationMetricChip("冷却 ${rule.cooldownSeconds}s")
            if (rule.maxExecutionsPer30Seconds > 0) AutomationMetricChip("30 秒 ${rule.maxExecutionsPer30Seconds} 次")
        }
        latestDebugRecord?.let { record ->
            HhhlDivider()
            AutomationLatestDebugSummary(record)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HhhlActionChip(label = "配置", emphasized = true, onClick = onOpen)
            HhhlActionChip(label = "AI 解释", enabled = aiEnabled && !isAiProcessing, onClick = onAiExplain)
            HhhlActionChip(label = "复制", onClick = onDuplicate)
            HhhlActionChip(label = "删除", onClick = onDelete)
        }
    }
}

@Composable
private fun AutomationLatestDebugSummary(record: AutomationRuleDebugRecord) {
    val colors = LocalHhhlColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (record.matched) Icons.Filled.PlayArrow else Icons.Filled.Tune,
            contentDescription = null,
            tint = if (record.matched) colors.success else colors.warning,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AutomationTitle("最近匹配：${record.eventLabel} · ${if (record.matched) "已命中" else "未触发"}")
            if (record.eventSummary.isNotBlank()) {
                AutomationMutedText("收到：${record.eventSummary}")
            }
            AutomationMutedText("原因：${record.reason}")
        }
    }
    val conditionSummary = record.conditionResults.take(3).joinToString(" · ") { condition ->
        "${condition.conditionLabel}${if (condition.matched) "命中" else "未命中"}"
    }
    if (conditionSummary.isNotBlank()) {
        AutomationMutedText("条件：$conditionSummary")
    }
    if (record.resolvedEntities.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            record.resolvedEntities.take(3).forEach { entity -> AutomationMetricChip(entity) }
        }
    }
}

@Composable
private fun AutomationRuleDraftPreviewCard(
    preview: AutomationRuleDraftPreview,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val rule = preview.rule
    AutomationPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = LocalHhhlColors.current.accent,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AutomationTitle("AI 规则草稿")
                AutomationMutedText(rule.name)
            }
        }
        if (preview.sourceText.isNotBlank()) AutomationMutedText("需求：${preview.sourceText}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationMetricChip("触发 ${rule.trigger.label}")
            AutomationMetricChip(rule.conditionMode.label)
            AutomationMetricChip(rule.actionMode.label)
            AutomationMetricChip(if (rule.enabled) "创建后启用" else "创建后停用")
            if (rule.ignoreOwnMessages) AutomationMetricChip("忽略自己")
            if (rule.cooldownSeconds > 0) AutomationMetricChip("冷却 ${rule.cooldownSeconds}s")
            if (rule.maxExecutionsPer30Seconds > 0) AutomationMetricChip("30 秒 ${rule.maxExecutionsPer30Seconds} 次")
        }
        AutomationPanel(compact = true) {
            AutomationTitle("条件")
            rule.conditions.take(6).forEachIndexed { index, condition ->
                AutomationMutedText("${index + 1}. ${condition.type.label} = ${condition.value.ifBlank { "全部" }}${if (condition.enabled) "" else "（停用）"}")
            }
        }
        AutomationPanel(compact = true) {
            AutomationTitle("动作")
            rule.actions.take(6).forEachIndexed { index, action ->
                val target = action.targetId.ifBlank { "默认目标" }
                AutomationMutedText("${index + 1}. ${action.type.label} -> $target${if (action.enabled) "" else "（停用）"}")
            }
        }
        if (preview.messages.isNotEmpty()) {
            AutomationPanel(compact = true) {
                AutomationTitle("解析结果")
                preview.messages.take(8).forEach { message -> AutomationMutedText(message) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HhhlActionChip(label = "批准创建", emphasized = true, onClick = onApprove)
            HhhlActionChip(label = "拒绝", onClick = onReject)
        }
    }
}

@Composable
private fun AutomationRuleEditorDialog(
    rule: AutomationRule,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateTrigger: (AutomationTrigger) -> Unit,
    onUpdateConditionMode: (AutomationConditionMode) -> Unit,
    onUpdateActionMode: (AutomationActionMode) -> Unit,
    onUpdateIgnoreOwnMessages: (Boolean) -> Unit,
    onUpdateCooldown: (Int) -> Unit,
    onUpdateBurstLimit: (Int) -> Unit,
    onAddCondition: (AutomationConditionType) -> Unit,
    onUpdateCondition: (AutomationCondition) -> Unit,
    onRemoveCondition: (String) -> Unit,
    onAddAction: (AutomationActionType) -> Unit,
    onUpdateAction: (AutomationAction) -> Unit,
    onRemoveAction: (String) -> Unit,
) {
    var conditionMenuOpen by remember(rule.id) { mutableStateOf(false) }
    var actionMenuOpen by remember(rule.id) { mutableStateOf(false) }
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("规则配置") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "rule-name") {
                    HhhlTextInput(
                        value = rule.name,
                        onValueChange = onUpdateName,
                        placeholder = "规则名称",
                        label = "名称",
                        singleLine = true,
                    )
                }
                item(key = "trigger") {
                    AutomationEnumPicker(
                        label = "触发器",
                        values = AutomationTrigger.entries,
                        selected = rule.trigger,
                        labelOf = { it.label },
                        onSelected = onUpdateTrigger,
                    )
                }
                item(key = "options") {
                    AutomationPanel(compact = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                AutomationTitle("忽略自己发送的消息")
                                AutomationMutedText("避免转发或通知自己触发的内容")
                            }
                            HhhlSwitch(checked = rule.ignoreOwnMessages, onCheckedChange = onUpdateIgnoreOwnMessages)
                        }
                        HhhlTextInput(
                            value = rule.cooldownSeconds.takeIf { it > 0 }?.toString().orEmpty(),
                            onValueChange = { onUpdateCooldown(it.toIntOrNull() ?: 0) },
                            placeholder = "0",
                            label = "冷却秒数",
                            singleLine = true,
                        )
                        HhhlTextInput(
                            value = rule.maxExecutionsPer30Seconds.takeIf { it > 0 }?.toString().orEmpty(),
                            onValueChange = { onUpdateBurstLimit(it.toIntOrNull() ?: 0) },
                            placeholder = "2",
                            label = "30 秒最多执行次数",
                            singleLine = true,
                        )
                    }
                }
                item(key = "condition-mode") {
                    AutomationEnumPicker(
                        label = "条件关系",
                        values = AutomationConditionMode.entries,
                        selected = rule.conditionMode,
                        labelOf = { it.label },
                        onSelected = onUpdateConditionMode,
                    )
                }
                item(key = "conditions-header") {
                    AutomationSectionHeader(
                        title = "条件",
                        trailing = { HhhlActionChip(label = "添加", onClick = { conditionMenuOpen = true }) },
                    )
                }
                items(rule.conditions, key = { it.id }) { condition ->
                    AutomationConditionEditor(
                        condition = condition,
                        onUpdate = onUpdateCondition,
                        onRemove = { onRemoveCondition(condition.id) },
                    )
                }
                item(key = "action-mode") {
                    AutomationEnumPicker(
                        label = "动作执行",
                        values = AutomationActionMode.entries,
                        selected = rule.actionMode,
                        labelOf = { it.label },
                        onSelected = onUpdateActionMode,
                    )
                }
                item(key = "actions-header") {
                    AutomationSectionHeader(
                        title = "动作",
                        trailing = { HhhlActionChip(label = "添加", onClick = { actionMenuOpen = true }) },
                    )
                }
                items(rule.actions, key = { it.id }) { action ->
                    AutomationActionEditor(
                        action = action,
                        onUpdate = onUpdateAction,
                        onRemove = { onRemoveAction(action.id) },
                    )
                }
                item(key = "template-help") {
                    AutomationStatusCard(
                        "模板变量：{{sender.name}}、{{sender.mention}}、{{message.text}}、{{message.id}}、{{message.type}}、{{room.id}}、{{room.name}}、{{direct.user.id}}、{{notification.text}}、{{note.id}}、{{note.link}}、{{channel.id}}、{{channel.name}}、{{channel.link}}、{{timeline.kind}}",
                        success = true,
                    )
                }
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onDismiss) { Text("完成") }
        },
    )
    if (conditionMenuOpen) {
        AutomationConditionTypeDialog(
            onDismiss = { conditionMenuOpen = false },
            onSelected = {
                conditionMenuOpen = false
                onAddCondition(it)
            },
        )
    }
    if (actionMenuOpen) {
        AutomationActionTypeDialog(
            onDismiss = { actionMenuOpen = false },
            onSelected = {
                actionMenuOpen = false
                onAddAction(it)
            },
        )
    }
}

@Composable
private fun AutomationConditionEditor(
    condition: AutomationCondition,
    onUpdate: (AutomationCondition) -> Unit,
    onRemove: () -> Unit,
) {
    AutomationPanel(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                AutomationTitle(condition.type.label)
                AutomationMutedText(condition.type.hint)
            }
            HhhlSwitch(checked = condition.enabled, onCheckedChange = { onUpdate(condition.copy(enabled = it)) })
            HhhlIconActionButton(icon = Icons.Filled.DeleteOutline, contentDescription = "删除条件", onClick = onRemove)
        }
        AutomationEnumPicker(
            label = "类型",
            values = AutomationConditionType.entries,
            selected = condition.type,
            labelOf = { it.label },
            onSelected = { onUpdate(condition.copy(type = it)) },
        )
        HhhlTextInput(
            value = condition.value,
            onValueChange = { onUpdate(condition.copy(value = it)) },
            placeholder = condition.type.hint,
            label = if (condition.type == AutomationConditionType.AiSemantic) "语义条件" else "匹配值",
            minLines = if (condition.type == AutomationConditionType.AiSemantic) 2 else 1,
            singleLine = condition.type != AutomationConditionType.AiSemantic,
        )
    }
}

@Composable
private fun AutomationActionEditor(
    action: AutomationAction,
    onUpdate: (AutomationAction) -> Unit,
    onRemove: () -> Unit,
) {
    AutomationPanel(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                AutomationTitle(action.type.label)
                AutomationMutedText(action.type.description)
            }
            HhhlSwitch(checked = action.enabled, onCheckedChange = { onUpdate(action.copy(enabled = it)) })
            HhhlIconActionButton(icon = Icons.Filled.DeleteOutline, contentDescription = "删除动作", onClick = onRemove)
        }
        AutomationEnumPicker(
            label = "动作类型",
            values = AutomationActionType.entries,
            selected = action.type,
            labelOf = { it.label },
            onSelected = { onUpdate(action.copy(type = it)) },
        )
        if (action.type.targetLabel.isNotBlank()) {
            HhhlTextInput(
                value = action.targetId,
                onValueChange = { onUpdate(action.copy(targetId = it)) },
                placeholder = action.type.targetLabel,
                label = action.type.targetLabel,
                singleLine = true,
            )
        }
        HhhlTextInput(
            value = action.titleTemplate,
            onValueChange = { onUpdate(action.copy(titleTemplate = it)) },
            placeholder = "默认使用规则名",
            label = "标题模板",
            singleLine = true,
        )
        HhhlTextInput(
            value = action.bodyTemplate,
            onValueChange = { onUpdate(action.copy(bodyTemplate = it)) },
            placeholder = action.type.bodyLabel,
            label = action.type.bodyLabel,
            minLines = if (action.type.isAiGeneratedAction()) 2 else 3,
        )
        if (action.type.supportsSenderMention() || action.type.supportsChatReference()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action.type.supportsSenderMention()) {
                    HhhlActionChip(
                        label = if (action.mentionSender) "已 @ 发送者" else "@ 发送者",
                        emphasized = action.mentionSender,
                        onClick = { onUpdate(action.copy(mentionSender = !action.mentionSender)) },
                    )
                }
                if (action.type.supportsChatReference()) {
                    HhhlActionChip(
                        label = if (action.replyToEvent) "已回复原消息" else "回复原消息",
                        emphasized = action.replyToEvent,
                        onClick = { onUpdate(action.copy(replyToEvent = !action.replyToEvent)) },
                    )
                    HhhlActionChip(
                        label = if (action.quoteEvent) "已引用原消息" else "引用原消息",
                        emphasized = action.quoteEvent,
                        onClick = { onUpdate(action.copy(quoteEvent = !action.quoteEvent)) },
                    )
                }
            }
        }
        AutomationEnumPicker(
            label = "失败策略",
            values = AutomationFailurePolicy.entries,
            selected = action.failurePolicy,
            labelOf = { it.label },
            onSelected = { onUpdate(action.copy(failurePolicy = it)) },
        )
    }
}

private fun AutomationActionType.isAiGeneratedAction(): Boolean {
    return this == AutomationActionType.AiGenerateLog ||
        this == AutomationActionType.AiGenerateNotification ||
        this == AutomationActionType.AiGenerateWebhook ||
        this == AutomationActionType.AiForwardToRoom ||
        this == AutomationActionType.AiReplyToChat ||
        this == AutomationActionType.AiReplyToNote ||
        this == AutomationActionType.AiQuoteNote
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

@Composable
private fun <T> AutomationEnumPicker(
    label: String,
    values: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    AutomationPanel(compact = true) {
        AutomationMutedText(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                HhhlActionChip(
                    label = labelOf(value),
                    emphasized = value == selected,
                    onClick = { onSelected(value) },
                )
            }
        }
    }
}

@Composable
private fun AutomationCreateRuleDialog(
    onDismiss: () -> Unit,
    onCreate: (AutomationTrigger) -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自动化") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AutomationTrigger.entries.forEach { trigger ->
                    AutomationPanel(
                        compact = true,
                        modifier = Modifier.clickable { onCreate(trigger) },
                    ) {
                        AutomationTitle(trigger.label)
                        AutomationMutedText(trigger.description)
                    }
                }
            }
        },
        confirmButton = { HhhlTextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AutomationConditionTypeDialog(
    onDismiss: () -> Unit,
    onSelected: (AutomationConditionType) -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加条件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationConditionType.entries.forEach { type ->
                    AutomationPanel(compact = true, modifier = Modifier.clickable { onSelected(type) }) {
                        AutomationTitle(type.label)
                        AutomationMutedText(type.hint)
                    }
                }
            }
        },
        confirmButton = { HhhlTextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AutomationActionTypeDialog(
    onDismiss: () -> Unit,
    onSelected: (AutomationActionType) -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加动作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationActionType.entries.forEach { type ->
                    AutomationPanel(compact = true, modifier = Modifier.clickable { onSelected(type) }) {
                        AutomationTitle(type.label)
                        AutomationMutedText(type.description)
                    }
                }
            }
        },
        confirmButton = { HhhlTextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AutomationSectionHeader(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = LocalHhhlColors.current.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun AutomationLogCard(
    title: String,
    subtitle: String,
    message: String,
    success: Boolean,
) {
    AutomationPanel(compact = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (success) Icons.Filled.PlayArrow else Icons.Filled.Tune,
                contentDescription = null,
                tint = if (success) LocalHhhlColors.current.success else LocalHhhlColors.current.warning,
            )
            Column(modifier = Modifier.weight(1f)) {
                AutomationTitle(title)
                AutomationMutedText(subtitle)
            }
        }
        AutomationMutedText(message)
    }
}

@Composable
fun AutomationExecutionLogScreen(
    logs: List<AutomationExecutionLog>,
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onRetryLog: (String) -> Unit,
    onCopyLog: (String) -> Unit,
    onClearLogs: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        HhhlTopBar(
            title = "自动化执行日志",
            supportingText = "${logs.count { it.success }} 成功 · ${logs.count { !it.success }} 失败",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlActionChip(label = "清空", enabled = logs.isNotEmpty(), onClick = onClearLogs)
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            if (logs.isEmpty()) {
                item(key = "automation-log-page-empty") { AutomationStatusCard("暂无执行记录", success = true) }
            } else {
                items(
                    items = logs,
                    key = { it.id },
                    contentType = { "automation-log-page-item" },
                ) { log ->
                    AutomationExecutionLogPageCard(
                        log = log,
                        onOpenRule = { onOpenRule(log.ruleId) },
                        onRetry = { onRetryLog(log.id) },
                        onCopy = { onCopyLog(log.toClipboardText()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationExecutionLogPageCard(
    log: AutomationExecutionLog,
    onOpenRule: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    val canRetry = !log.success && log.actionSnapshot != null && log.eventSnapshot != null
    AutomationPanel(compact = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (log.success) Icons.Filled.PlayArrow else Icons.Filled.Tune,
                contentDescription = null,
                tint = if (log.success) LocalHhhlColors.current.success else LocalHhhlColors.current.warning,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AutomationTitle(log.ruleName)
                AutomationMutedText(
                    listOf(
                        log.createdAtLabel(),
                        log.eventLabel,
                        log.actionLabel,
                        if (log.success) "成功" else "失败",
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
        AutomationMutedText("${if (log.success) "结果" else "失败原因"}：${log.message}")
        if (log.retryOfLogId.isNotBlank()) {
            AutomationMetricChip("重试自 ${log.retryOfLogId}")
        }
        log.eventSnapshot?.let { event ->
            AutomationPanel(compact = true) {
                AutomationTitle("事件快照")
                AutomationMutedText("来源：${event.sourceLabel()}")
                AutomationMutedText(
                    listOf(
                        event.id,
                        event.roomName.ifBlank { event.roomId },
                        event.senderName.ifBlank { event.senderUsername },
                        event.messageText.ifBlank { event.notificationText },
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HhhlActionChip(label = "打开规则", onClick = onOpenRule)
            HhhlActionChip(label = "重试", enabled = canRetry, onClick = onRetry)
            HhhlActionChip(label = "复制错误", enabled = log.message.isNotBlank(), onClick = onCopy)
        }
    }
}

private fun AutomationExecutionLog.toClipboardText(): String {
    return buildString {
        appendLine("规则：$ruleName ($ruleId)")
        createdAtLabel().takeIf { it.isNotBlank() }?.let { appendLine("触发时间：$it") }
        appendLine("事件：$eventLabel ($eventId)")
        appendLine("动作：$actionLabel")
        appendLine("结果：${if (success) "成功" else "失败"}")
        if (retryOfLogId.isNotBlank()) appendLine("重试自：$retryOfLogId")
        appendLine("${if (success) "消息" else "失败原因"}：$message")
        eventSnapshot?.let { event ->
            appendLine("事件快照：")
            appendLine("- id: ${event.id}")
            appendLine("- source: ${event.sourceLabel()}")
            if (event.roomId.isNotBlank() || event.roomName.isNotBlank()) appendLine("- room: ${event.roomName} (${event.roomId})")
            if (event.senderUserId.isNotBlank() || event.senderUsername.isNotBlank()) appendLine("- sender: ${event.senderName} @${event.senderUsername} (${event.senderUserId})")
            if (event.messageText.isNotBlank()) appendLine("- message: ${event.messageText}")
            if (event.notificationText.isNotBlank()) appendLine("- notification: ${event.notificationText}")
        }
    }.trim()
}

private fun AutomationEventSnapshot.sourceLabel(): String {
    return listOf(trigger.label, sourceKind, attentionKind, notificationType, timelineKind)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

internal fun AutomationExecutionLog.createdAtLabel(): String {
    if (createdAtEpochMillis <= 0L) return ""
    return runCatching {
        val local = Instant.fromEpochMilliseconds(createdAtEpochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.year}-${local.monthNumber.twoDigits()}-${local.dayOfMonth.twoDigits()} ${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    }.getOrDefault("")
}

@Composable
private fun AutomationDebugRecordCard(record: AutomationRuleDebugRecord) {
    val colors = LocalHhhlColors.current
    AutomationPanel(compact = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (record.matched) Icons.Filled.PlayArrow else Icons.Filled.Tune,
                contentDescription = null,
                tint = if (record.matched) colors.success else colors.warning,
            )
            Column(modifier = Modifier.weight(1f)) {
                AutomationTitle(record.ruleName)
                AutomationMutedText("${record.eventLabel} · ${if (record.matched) "已命中" else "未触发"}")
            }
        }
        AutomationMutedText(record.reason)
        if (record.eventSummary.isNotBlank()) {
            AutomationMutedText(record.eventSummary)
        }
        if (record.resolvedEntities.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                record.resolvedEntities.forEach { entity -> AutomationMetricChip(entity) }
            }
        }
        record.conditionResults.take(4).forEach { condition ->
            AutomationPanel(compact = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AutomationTitle(condition.conditionLabel)
                    AutomationMetricChip(if (condition.matched) "命中" else "未命中")
                }
                AutomationMutedText(condition.message)
                if (condition.expectedValue.isNotBlank() || condition.actualValue.isNotBlank()) {
                    AutomationMutedText("期望：${condition.expectedValue.ifBlank { "空" }} · 实际：${condition.actualValue.ifBlank { "无" }}")
                }
            }
        }
    }
}

@Composable
private fun AutomationDebugDialog(
    debugRecords: List<AutomationRuleDebugRecord>,
    recentChatMessages: List<ChatMessage>,
    onSimulateChatMessage: (ChatMessage) -> Unit,
    onClearDebugRecords: () -> Unit,
    onDismiss: () -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("规则调试台") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "dialog-debug-simulator") {
                    AutomationHistorySimulationCard(
                        messages = recentChatMessages,
                        onSimulate = onSimulateChatMessage,
                    )
                }
                if (debugRecords.isEmpty()) {
                    item(key = "dialog-debug-empty") {
                        AutomationStatusCard("暂无规则匹配记录", success = true)
                    }
                } else {
                    items(
                        items = debugRecords.take(20),
                        key = { it.id },
                        contentType = { "dialog-debug-record" },
                    ) { record ->
                        AutomationDebugRecordCard(record = record)
                    }
                }
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            HhhlTextButton(
                enabled = debugRecords.isNotEmpty(),
                onClick = onClearDebugRecords,
            ) { Text("清空记录") }
        },
    )
}

@Composable
private fun AutomationHistorySimulationCard(
    messages: List<ChatMessage>,
    onSimulate: (ChatMessage) -> Unit,
) {
    AutomationPanel(compact = true) {
        AutomationTitle("用历史消息模拟")
        AutomationMutedText("只生成匹配记录和条件结果，不会执行回复、转发或 Webhook。")
        if (messages.isEmpty()) {
            AutomationMutedText("先打开一个聊天室或私聊，最近消息会显示在这里。")
        } else {
            messages.take(5).forEach { message ->
                AutomationPanel(compact = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AutomationTitle(message.fromUser.displayName.ifBlank { message.fromUser.username })
                            AutomationMutedText(message.text.ifBlank { message.file?.name ?: "附件消息" })
                        }
                        HhhlActionChip(label = "模拟", onClick = { onSimulate(message) })
                    }
                    if (message.createdAtLabel.isNotBlank()) {
                        AutomationMutedText(message.createdAtLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationStatusCard(message: String, success: Boolean) {
    AutomationPanel(compact = true) {
        Text(
            text = message,
            color = if (success) LocalHhhlColors.current.textSecondary else LocalHhhlColors.current.warning,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AutomationAiResultCard(
    label: String,
    text: String,
    onCopyAiResult: ((String) -> Unit)?,
    onCreateAutomationRule: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        supportingText = "AI 只解释和建议，不会自动修改规则",
        emphasized = false,
        actions = {
            AiResultCommonActionChips(
                text = text,
                onCopyChecklist = onCopyAiResult,
                onCreateAutomationRule = onCreateAutomationRule,
            )
        },
    )
}

@Composable
private fun AutomationMetricChip(label: String) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.inputBackground.copy(alpha = 0.52f))
            .border(1.dp, colors.border.copy(alpha = 0.20f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AutomationPanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(colors.surfaceElevated.copy(alpha = if (compact) 0.70f else 0.84f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = if (compact) 0.20f else 0.28f),
                shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
            )
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        content()
    }
}

@Composable
private fun AutomationTitle(text: String) {
    Text(
        text = text,
        color = LocalHhhlColors.current.textPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AutomationMutedText(text: String) {
    Text(
        text = text,
        color = LocalHhhlColors.current.textMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun List<AutomationRuleDebugRecord>.latestAutomationDebugRecordByRuleId(): Map<String, AutomationRuleDebugRecord> {
    return groupBy { it.ruleId }.mapValues { (_, records) ->
        records.maxBy { it.createdAtEpochMillis }
    }
}

private fun Int.twoDigits(): String = if (this < 10) "0$this" else toString()
