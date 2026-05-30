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
import cc.hhhl.client.automation.AutomationFailurePolicy
import cc.hhhl.client.automation.AutomationRule
import cc.hhhl.client.automation.AutomationTrigger
import cc.hhhl.client.automation.AutomationUiState
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.theme.LocalHhhlColors
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
    onAddCondition: (String, AutomationConditionType) -> Unit,
    onUpdateCondition: (String, AutomationCondition) -> Unit,
    onRemoveCondition: (String, String) -> Unit,
    onAddAction: (String, AutomationActionType) -> Unit,
    onUpdateAction: (String, AutomationAction) -> Unit,
    onRemoveAction: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiExplainRule: (AutomationRule?) -> Unit = {},
    onAiSuggestRules: (AutomationUiState) -> Unit = {},
    onAiCreateRule: (String, AutomationUiState) -> Unit = { _, _ -> },
    onDismissAiResult: () -> Unit = {},
) {
    val colors = LocalHhhlColors.current
    var createMenuOpen by remember { mutableStateOf(false) }
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
                HhhlIconActionButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "新建规则",
                    onClick = { createMenuOpen = true },
                )
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
                        onDismiss = onDismissAiResult,
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
                    onOpen = { onOpenRule(rule.id) },
                    onToggle = { onToggleRule(rule.id) },
                    onDuplicate = { onDuplicateRule(rule.id) },
                    onDelete = { onDeleteRule(rule.id) },
                    aiEnabled = aiEnabled,
                    isAiProcessing = isAiProcessing,
                    onAiExplain = { onAiExplainRule(rule) },
                )
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
            AutomationMetricChip("日志 ${state.logs.size}")
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
private fun AutomationRuleEditorDialog(
    rule: AutomationRule,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateTrigger: (AutomationTrigger) -> Unit,
    onUpdateConditionMode: (AutomationConditionMode) -> Unit,
    onUpdateActionMode: (AutomationActionMode) -> Unit,
    onUpdateIgnoreOwnMessages: (Boolean) -> Unit,
    onUpdateCooldown: (Int) -> Unit,
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
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        supportingText = "AI 只解释和建议，不会自动修改规则",
        emphasized = false,
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
