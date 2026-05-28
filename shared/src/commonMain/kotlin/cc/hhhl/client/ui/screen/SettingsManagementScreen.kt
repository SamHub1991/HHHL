package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.SettingsManagementItem
import cc.hhhl.client.model.SettingsManagementAction
import cc.hhhl.client.model.SettingsManagementItemAction
import cc.hhhl.client.model.SettingsManagementSection
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookDetail
import cc.hhhl.client.model.SettingsWebhookUpdateInput
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlAlertDialog
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun SettingsManagementScreen(
    section: SettingsManagementSection?,
    isLoading: Boolean,
    isMutating: Boolean,
    editingWebhook: SettingsWebhookDetail?,
    isWebhookEditorLoading: Boolean,
    message: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPerformAction: (SettingsManagementAction, String) -> Unit,
    onOpenWebhookEditor: (String) -> Unit,
    onCloseWebhookEditor: () -> Unit,
    onCreateWebhook: (SettingsWebhookCreateInput) -> Unit,
    onUpdateWebhook: (String, SettingsWebhookUpdateInput) -> Unit,
) {
    var showWebhookCreateDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = section?.title ?: "设置详情",
            supportingText = when {
                isLoading -> "同步中"
                section != null -> "${section.items.size} 项"
                else -> "设置详情"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section?.key == SettingsManagementSectionKey.Webhooks && section.supportsPrimaryAction) {
                        HhhlIconActionButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "创建 Webhook",
                            enabled = !isLoading && !isMutating,
                            emphasized = true,
                            onClick = { showWebhookCreateDialog = true },
                        )
                    }
                    if (section?.key == SettingsManagementSectionKey.Invites && section.supportsPrimaryAction) {
                        HhhlIconActionButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "创建邀请码",
                            enabled = !isLoading && !isMutating,
                            emphasized = true,
                            onClick = { onPerformAction(SettingsManagementAction.CreateInvite, "") },
                        )
                    }
                    HhhlIconActionButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = if (isLoading) "同步中" else "刷新",
                        enabled = !isLoading && !isMutating,
                        emphasized = true,
                        onClick = onRefresh,
                    )
                }
            },
        )
        HhhlDivider()
        section?.description?.takeIf { it.isNotBlank() }?.let { description ->
            SettingsManagementSummaryRow(
                text = description,
                isMutating = isMutating || isWebhookEditorLoading,
            )
            HhhlDivider()
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            message?.let {
                item(key = "settings-management-message", contentType = "settings-management-message") {
                    SettingsManagementStatusRow(text = it)
                }
            }
            if (isLoading) {
                item(key = "settings-management-loading", contentType = "settings-management-loading") {
                    SettingsManagementStatusRow(text = "正在加载...", loading = true)
                }
            }
            if (!isLoading && section != null && section.items.isEmpty()) {
                item(key = "settings-management-empty-${section.key.name}", contentType = "settings-management-empty") {
                    SettingsManagementStatusRow(text = "暂无可显示内容")
                }
            }
            section?.errorMessage?.let { error ->
                item(key = "settings-management-error-${section.key.name}", contentType = "settings-management-error") {
                    SettingsManagementStatusRow(text = error)
                }
            }
            section?.items?.let { items ->
                items(
                    items = items,
                    key = { it.id },
                    contentType = { "settings-management-item" },
                ) { item ->
                    SettingsManagementItemRow(
                        item = item,
                        isMutating = isMutating || isWebhookEditorLoading,
                        onPerformAction = onPerformAction,
                        onOpenWebhookEditor = onOpenWebhookEditor,
                    )
                }
            }
        }
    }

    if (showWebhookCreateDialog) {
        SettingsWebhookCreateDialog(
            isMutating = isMutating,
            onDismiss = { showWebhookCreateDialog = false },
            onCreate = { input ->
                showWebhookCreateDialog = false
                onCreateWebhook(input)
            },
        )
    }

    editingWebhook?.let { webhook ->
        SettingsWebhookEditDialog(
            webhook = webhook,
            isMutating = isMutating,
            onDismiss = onCloseWebhookEditor,
            onSave = { input ->
                onCloseWebhookEditor()
                onUpdateWebhook(webhook.id, input)
            },
        )
    }
}

@Composable
private fun SettingsManagementSummaryRow(
    text: String,
    isMutating: Boolean,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (isMutating) {
            HhhlActionChip(
                label = "处理中",
                emphasized = true,
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun SettingsManagementItemRow(
    item: SettingsManagementItem,
    isMutating: Boolean,
    onPerformAction: (SettingsManagementAction, String) -> Unit,
    onOpenWebhookEditor: (String) -> Unit,
) {
    var pendingAction by remember { mutableStateOf<SettingsManagementItemAction?>(null) }
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.meta.takeIf { it.isNotBlank() }?.let { meta ->
                    Text(
                        text = meta,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.actions.isNotEmpty()) {
                HhhlOverflowMenu(
                    enabled = !isMutating,
                    actions = item.actions.map { action ->
                        HhhlOverflowMenuAction(
                            label = action.label,
                            destructive = action.destructive,
                            enabled = action.enabled && !isMutating,
                            onClick = {
                                if (action.type == SettingsManagementAction.EditWebhook) {
                                    onOpenWebhookEditor(item.id)
                                } else {
                                    pendingAction = action
                                }
                            },
                        )
                    },
                )
            }
        }
        if (item.badges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.badges.forEach { badge ->
                    HhhlActionChip(
                        label = badge,
                        emphasized = true,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
        if (item.permissions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.permissions.forEach { permission ->
                    HhhlActionChip(
                        label = permission,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
    HhhlDivider()

    pendingAction?.let { action ->
        HhhlAlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(settingsManagementActionTitle(action)) },
            text = { Text(settingsManagementActionMessage(action)) },
            confirmButton = {
                HhhlActionChip(
                    label = action.label,
                    emphasized = true,
                    enabled = !isMutating,
                    onClick = {
                        pendingAction = null
                        onPerformAction(action.type, item.id)
                    },
                )
            },
            dismissButton = {
                HhhlActionChip(
                    label = "取消",
                    enabled = !isMutating,
                    onClick = { pendingAction = null },
                )
            },
        )
    }

}

@Composable
private fun SettingsWebhookCreateDialog(
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (SettingsWebhookCreateInput) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var selectedEvents by remember { mutableStateOf(listOf("note")) }
    val canCreate = name.trim().isNotEmpty() && url.trim().isNotEmpty() && selectedEvents.isNotEmpty() && !isMutating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建 Webhook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "名称",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "名称",
                )
                HhhlTextInput(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://example.com/hook",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "回调 URL",
                )
                HhhlTextInput(
                    value = secret,
                    onValueChange = { secret = it },
                    placeholder = "可选",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "Secret",
                )
                Text(
                    text = "触发事件",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                WebhookEventPicker(
                    selectedEvents = selectedEvents,
                    enabled = !isMutating,
                    onSelectedEventsChanged = { selectedEvents = it },
                )
            }
        },
        confirmButton = {
            HhhlActionChip(
                label = "创建",
                emphasized = true,
                enabled = canCreate,
                onClick = {
                    onCreate(
                        SettingsWebhookCreateInput(
                            name = name,
                            url = url,
                            secret = secret,
                            events = selectedEvents,
                        ),
                    )
                },
            )
        },
        dismissButton = {
            HhhlActionChip(
                label = "取消",
                enabled = !isMutating,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SettingsWebhookEditDialog(
    webhook: SettingsWebhookDetail,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (SettingsWebhookUpdateInput) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var name by remember(webhook.id) { mutableStateOf(webhook.name) }
    var url by remember(webhook.id) { mutableStateOf(webhook.url) }
    var secret by remember(webhook.id) { mutableStateOf(webhook.secret) }
    var selectedEvents by remember(webhook.id) {
        mutableStateOf(webhook.events.ifEmpty { listOf("note") })
    }
    val canSave = name.trim().isNotEmpty() && url.trim().isNotEmpty() && selectedEvents.isNotEmpty() && !isMutating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 Webhook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "名称",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "名称",
                )
                HhhlTextInput(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://example.com/hook",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "回调 URL",
                )
                HhhlTextInput(
                    value = secret,
                    onValueChange = { secret = it },
                    placeholder = "留空则不修改",
                    enabled = !isMutating,
                    singleLine = true,
                    label = "Secret",
                )
                Text(
                    text = "触发事件",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                WebhookEventPicker(
                    selectedEvents = selectedEvents,
                    enabled = !isMutating,
                    onSelectedEventsChanged = { selectedEvents = it },
                )
            }
        },
        confirmButton = {
            HhhlActionChip(
                label = "保存",
                emphasized = true,
                enabled = canSave,
                onClick = {
                    onSave(
                        SettingsWebhookUpdateInput(
                            name = name,
                            url = url,
                            secret = secret,
                            events = selectedEvents,
                        ),
                    )
                },
            )
        },
        dismissButton = {
            HhhlActionChip(
                label = "取消",
                enabled = !isMutating,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun WebhookEventPicker(
    selectedEvents: List<String>,
    enabled: Boolean,
    onSelectedEventsChanged: (List<String>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        webhookEventOptions.forEach { event ->
            val selected = event.key in selectedEvents
            HhhlActionChip(
                label = event.label,
                emphasized = selected,
                enabled = enabled,
                onClick = {
                    onSelectedEventsChanged(
                        if (selected) {
                            selectedEvents.filterNot { it == event.key }
                        } else {
                            (selectedEvents + event.key).distinct()
                        },
                    )
                },
            )
        }
    }
}

private data class WebhookEventOption(
    val key: String,
    val label: String,
)

private val webhookEventOptions = listOf(
    WebhookEventOption("note", "帖子"),
    WebhookEventOption("reply", "回复"),
    WebhookEventOption("mention", "提及"),
    WebhookEventOption("renote", "转发"),
    WebhookEventOption("reaction", "回应"),
    WebhookEventOption("follow", "关注"),
    WebhookEventOption("followed", "被关注"),
    WebhookEventOption("unfollow", "取关"),
    WebhookEventOption("edited", "编辑"),
)

private fun settingsManagementActionTitle(action: SettingsManagementItemAction): String {
    return when (action.type) {
        SettingsManagementAction.RevokeToken -> "确认撤销"
        SettingsManagementAction.CreateInvite -> "创建邀请码"
        SettingsManagementAction.DeleteInvite -> "确认删除"
        SettingsManagementAction.LoginSharedAccess -> "授权登录"
        SettingsManagementAction.EditWebhook -> "编辑 Webhook"
        SettingsManagementAction.EnableWebhook -> "确认启用"
        SettingsManagementAction.DisableWebhook -> "确认停用"
        SettingsManagementAction.TestWebhook -> "发送测试"
        SettingsManagementAction.DeleteWebhook -> "确认删除"
    }
}

private fun settingsManagementActionMessage(action: SettingsManagementItemAction): String {
    return when (action.type) {
        SettingsManagementAction.RevokeToken -> "撤销后该令牌将无法继续访问。"
        SettingsManagementAction.CreateInvite -> "将生成一个新的邀请码。"
        SettingsManagementAction.DeleteInvite -> "删除后该邀请码将无法继续注册使用。"
        SettingsManagementAction.LoginSharedAccess -> "需要通过授权登录重新确认共享访问。"
        SettingsManagementAction.EditWebhook -> "请在 Webhook 编辑表单中保存更改。"
        SettingsManagementAction.EnableWebhook -> "启用后该 Webhook 会重新接收事件推送。"
        SettingsManagementAction.DisableWebhook -> "停用后该 Webhook 将暂停接收事件推送。"
        SettingsManagementAction.TestWebhook -> "将向该 Webhook 发送一条测试事件。"
        SettingsManagementAction.DeleteWebhook -> "删除后该 Webhook 将不再接收事件推送。"
    }
}

@Composable
private fun SettingsManagementStatusRow(
    text: String,
    loading: Boolean = false,
) {
    HhhlStatusRow(
        text = text,
        loading = loading,
    )
}
