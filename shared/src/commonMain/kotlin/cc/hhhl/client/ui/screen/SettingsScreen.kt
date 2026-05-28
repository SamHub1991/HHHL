package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.InstanceMeta
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.toColorOrNull
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlDropdownMenu
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlDropdownMenuItem
import cc.hhhl.client.ui.component.HhhlSectionHeader
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlSwitch
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.ThemePicker
import cc.hhhl.client.ui.component.TimelineDensityPicker
import cc.hhhl.client.ui.component.hhhlReadableOnControlColor

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    instanceMeta: InstanceMeta? = null,
    isInstanceMetaLoading: Boolean = false,
    onBack: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    customTheme: HhhlCustomTheme = HhhlCustomTheme(),
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit = {},
    onResetCustomTheme: () -> Unit = {},
    onPickGlobalBackgroundImage: () -> Unit = {},
    onClearGlobalBackgroundImage: () -> Unit = {},
    onPickChatBackgroundImage: () -> Unit = {},
    onClearChatBackgroundImage: () -> Unit = {},
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onCheckForUpdates: (((String) -> Unit) -> Unit) = { report -> report("当前平台暂不支持应用内更新") },
    onClearChatMessageCache: () -> Unit = {},
    onOpenThemeCustomization: () -> Unit = {},
    accounts: List<AccountSession> = emptyList(),
    currentAccountId: String? = null,
    onSwitchAccount: (String) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onRefreshRemote: () -> Unit = {},
    onPrivacyToggle: (SettingsItemKey, Boolean) -> Unit = { _, _ -> },
    onNotificationTypeToggle: (String, Boolean) -> Unit = { _, _ -> },
    onMutedWordDraftChanged: (String) -> Unit = {},
    onAddMutedWord: () -> Unit = {},
    onRemoveMutedWord: (String) -> Unit = {},
    onHardMutedWordDraftChanged: (String) -> Unit = {},
    onAddHardMutedWord: () -> Unit = {},
    onRemoveHardMutedWord: (String) -> Unit = {},
    onMutedInstanceDraftChanged: (String) -> Unit = {},
    onAddMutedInstance: () -> Unit = {},
    onRemoveMutedInstance: (String) -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onOpenWebSettings: (SettingsItemKey) -> Unit = {},
    onOpenManagement: (SettingsManagementSectionKey) -> Unit = {},
) {
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "设置",
            navigation = {
                HhhlBackButton(onClick = onBack)
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (accounts.isNotEmpty()) {
                item(key = "account-switch-panel", contentType = "account-switch-panel") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "账号",
                            color = LocalHhhlColors.current.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AccountSwitchPanel(
                            accounts = accounts,
                            currentAccountId = currentAccountId,
                            onSwitchAccount = onSwitchAccount,
                            onRemoveAccount = onRemoveAccount,
                            onAddAccount = onAddAccount,
                        )
                    }
                }
            }
            if (instanceMeta != null || isInstanceMetaLoading) {
                item(key = "instance-overview", contentType = "instance-overview") {
                    InstanceOverviewPanel(
                        meta = instanceMeta,
                        isLoading = isInstanceMetaLoading,
                    )
                }
            }
            if (state.errorMessage != null || state.isRemoteLoading) {
                item(key = "settings-remote-status", contentType = "settings-remote-status") {
                    SettingsStatusRow(
                        text = state.errorMessage ?: "正在同步网页版设置",
                        onRetry = onRefreshRemote,
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "settings-group-header-${group.key}", contentType = "settings-group-header") {
                    SettingsGroupHeader(label = group.label)
                }
                items(
                    items = group.items,
                    key = { item -> "settings-item-${group.key}-${item.key}" },
                    contentType = { "settings-row" },
                ) { item ->
                    SettingsRow(
                        item = item,
                        state = state,
                        onThemeSelected = onThemeSelected,
                        customTheme = customTheme,
                        onCustomThemeChanged = onCustomThemeChanged,
                        onResetCustomTheme = onResetCustomTheme,
                        onPickGlobalBackgroundImage = onPickGlobalBackgroundImage,
                        onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
                        onPickChatBackgroundImage = onPickChatBackgroundImage,
                        onClearChatBackgroundImage = onClearChatBackgroundImage,
                        onTimelineDensitySelected = onTimelineDensitySelected,
                        onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                        onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                        onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                        onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                        onClearChatMessageCache = onClearChatMessageCache,
                        onOpenThemeCustomization = onOpenThemeCustomization,
                        onPrivacyToggle = onPrivacyToggle,
                        onNotificationTypeToggle = onNotificationTypeToggle,
                        onMutedWordDraftChanged = onMutedWordDraftChanged,
                        onAddMutedWord = onAddMutedWord,
                        onRemoveMutedWord = onRemoveMutedWord,
                        onHardMutedWordDraftChanged = onHardMutedWordDraftChanged,
                        onAddHardMutedWord = onAddHardMutedWord,
                        onRemoveHardMutedWord = onRemoveHardMutedWord,
                        onMutedInstanceDraftChanged = onMutedInstanceDraftChanged,
                        onAddMutedInstance = onAddMutedInstance,
                        onRemoveMutedInstance = onRemoveMutedInstance,
                        onOpenAdminDashboard = onOpenAdminDashboard,
                        onOpenWebSettings = onOpenWebSettings,
                        onOpenManagement = onOpenManagement,
                    )
                    HhhlDivider()
                }
            }
            item(key = "settings-app-update", contentType = "settings-app-update") {
                SettingsAppUpdatePanel(
                    status = updateStatus,
                    isChecking = isCheckingUpdate,
                    onCheck = {
                        if (isCheckingUpdate) return@SettingsAppUpdatePanel
                        isCheckingUpdate = true
                        updateStatus = "正在检查 GitHub Release"
                        onCheckForUpdates { message ->
                            updateStatus = message
                            isCheckingUpdate = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsAppUpdatePanel(
    status: String?,
    isChecking: Boolean,
    onCheck: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        emphasized = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.buttonSelectedBackground)
                    .border(1.dp, colors.focusRing.copy(alpha = 0.24f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = hhhlReadableOnControlColor(colors.buttonSelectedBackground, colors.accent),
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "软件更新",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status ?: "从 GitHub Release 检查新版 APK，覆盖安装会保留缓存和本地数据",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlActionChip(
                label = if (isChecking) "检查中" else "获取更新",
                emphasized = true,
                enabled = !isChecking,
                onClick = onCheck,
            )
        }
    }
}

@Composable
private fun InstanceOverviewPanel(
    meta: InstanceMeta?,
    isLoading: Boolean,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        emphasized = true,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = meta?.name?.takeIf { it.isNotBlank() } ?: "实例信息",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta?.version?.takeIf { it.isNotBlank() }?.let { "Sharkey $it" }
                        ?: if (isLoading) "正在同步站点概况" else "站点概况",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            meta?.onlineUsers?.let { online ->
                HhhlActionChip(
                    label = "在线 ${formatCompactCount(online.countAcrossNetwork.coerceAtLeast(online.count))}",
                    emphasized = true,
                    enabled = false,
                    onClick = {},
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            meta?.stats?.let { stats ->
                InstanceStatChip("帖子", formatCompactCount(stats.notesCount))
                InstanceStatChip("用户", formatCompactCount(stats.usersCount))
                InstanceStatChip("反应", formatCompactCount(stats.reactionsCount))
                if (stats.instances > 0) InstanceStatChip("实例", formatCompactCount(stats.instances))
                val driveUsage = stats.driveUsageLocal + stats.driveUsageRemote
                if (driveUsage > 0) InstanceStatChip("网盘", formatBytes(driveUsage))
            }
            meta?.serverInfo?.takeIf { it.cpuCores > 0 || it.storageTotal > 0 || it.memoryTotal > 0 }?.let { server ->
                if (server.cpuCores > 0) InstanceStatChip("CPU", "${server.cpuCores} 核")
                if (server.memoryTotal > 0) InstanceStatChip("内存", formatBytes(server.memoryTotal))
                if (server.storageTotal > 0) InstanceStatChip("存储", "${formatBytes(server.storageUsed)} / ${formatBytes(server.storageTotal)}")
            }
        }
    }
}

@Composable
private fun InstanceStatChip(
    label: String,
    value: String,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.inputBackground)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun formatCompactCount(value: Long): String {
    return when {
        value >= 100_000_000 -> "${value / 100_000_000}亿"
        value >= 10_000 -> {
            val scaled = value / 1_000
            "${scaled / 10}.${scaled % 10}万"
        }
        else -> value.toString()
    }
}

private fun formatCompactCount(value: Int): String = formatCompactCount(value.toLong())

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (value >= 10 || unitIndex == 0) {
        "${value.toInt()} ${units[unitIndex]}"
    } else {
        "${(value * 10).toInt() / 10.0} ${units[unitIndex]}"
    }
}

@Composable
private fun SettingsGroupHeader(label: String) {
    HhhlSectionHeader(label = label)
}

@Composable
private fun SettingsRow(
    item: SettingsItem,
    state: SettingsUiState,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    customTheme: HhhlCustomTheme,
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onResetCustomTheme: () -> Unit,
    onPickGlobalBackgroundImage: () -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onPickChatBackgroundImage: () -> Unit,
    onClearChatBackgroundImage: () -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit,
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit,
    onClearChatMessageCache: () -> Unit,
    onOpenThemeCustomization: () -> Unit,
    onPrivacyToggle: (SettingsItemKey, Boolean) -> Unit,
    onNotificationTypeToggle: (String, Boolean) -> Unit,
    onMutedWordDraftChanged: (String) -> Unit,
    onAddMutedWord: () -> Unit,
    onRemoveMutedWord: (String) -> Unit,
    onHardMutedWordDraftChanged: (String) -> Unit,
    onAddHardMutedWord: () -> Unit,
    onRemoveHardMutedWord: (String) -> Unit,
    onMutedInstanceDraftChanged: (String) -> Unit,
    onAddMutedInstance: () -> Unit,
    onRemoveMutedInstance: (String) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenWebSettings: (SettingsItemKey) -> Unit,
    onOpenManagement: (SettingsManagementSectionKey) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val webManagementPath = settingsWebManagementPath(item.key)
    val nativeManagementKey = settingsManagementSectionKey(item.key)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    item.key == SettingsItemKey.AdminDashboard -> {
                        Modifier.clickable(enabled = item.enabled, onClick = onOpenAdminDashboard)
                    }
                    nativeManagementKey != null -> {
                        Modifier.clickable(enabled = item.enabled) { onOpenManagement(nativeManagementKey) }
                    }
                    item.key == SettingsItemKey.AdvancedTheme -> {
                        Modifier.clickable(enabled = item.enabled, onClick = onOpenThemeCustomization)
                    }
                    webManagementPath != null -> {
                        Modifier.clickable(enabled = item.enabled) { onOpenWebSettings(item.key) }
                    }
                    else -> Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsItemIcon(key = item.key, enabled = item.enabled)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.label,
                color = if (item.enabled) {
                    colors.textPrimary
                } else {
                    colors.textMuted
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (item.key) {
                SettingsItemKey.Theme -> ThemePicker(
                    selectedTheme = state.selectedTheme,
                    onThemeSelected = onThemeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.AdvancedTheme -> AdvancedThemeEditor(
                    customTheme = customTheme,
                    onOpen = onOpenThemeCustomization,
                )
                SettingsItemKey.TimelineDensity -> TimelineDensityPicker(
                    selectedDensity = state.selectedTimelineDensity,
                    onDensitySelected = onTimelineDensitySelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.DefaultNoteVisibility -> SettingsDropdownPicker(
                    selectedLabel = state.selectedDefaultNoteVisibility.label,
                    options = DefaultNoteVisibility.entries.toList(),
                    optionLabel = { it.label },
                    isSelected = { it == state.selectedDefaultNoteVisibility },
                    onSelected = onDefaultNoteVisibilitySelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.NotificationBadges -> SettingsDropdownPicker(
                    selectedLabel = state.selectedNotificationBadgeMode.label,
                    options = NotificationBadgeMode.entries.toList(),
                    optionLabel = { it.label },
                    isSelected = { it == state.selectedNotificationBadgeMode },
                    onSelected = onNotificationBadgeModeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.BackgroundNotifications -> SettingsSwitchLine(
                    checked = state.backgroundNotificationsEnabled,
                    enabled = true,
                    onCheckedChange = onBackgroundNotificationsChanged,
                )
                SettingsItemKey.SpecialCareBackgroundNotifications -> SettingsSwitchLine(
                    checked = state.specialCareBackgroundNotificationsEnabled,
                    enabled = state.backgroundNotificationsEnabled,
                    onCheckedChange = onSpecialCareBackgroundNotificationsChanged,
                )
                SettingsItemKey.ChatMessageCache -> SettingsCacheLine(
                    text = item.value.orEmpty(),
                    onClear = onClearChatMessageCache,
                )
                SettingsItemKey.LockAccount,
                SettingsItemKey.AutoAcceptFollowed,
                SettingsItemKey.NoCrawle,
                SettingsItemKey.PreventAiLearning,
                SettingsItemKey.PublicReactions -> SettingsSwitchLine(
                    checked = item.checkedValue(state) ?: false,
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onCheckedChange = { onPrivacyToggle(item.key, it) },
                )
                SettingsItemKey.MuteReactions -> SettingsSwitchLine(
                    checked = state.remotePreferences?.notifications?.mutedTypes?.contains("reaction") == true,
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onCheckedChange = { onNotificationTypeToggle("reaction", it) },
                )
                SettingsItemKey.MuteFollows -> SettingsSwitchLine(
                    checked = state.remotePreferences?.notifications?.mutedTypes?.contains("follow") == true,
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onCheckedChange = { onNotificationTypeToggle("follow", it) },
                )
                SettingsItemKey.MutedWords -> SettingsListEditor(
                    values = state.remotePreferences?.filters?.mutedWords.orEmpty(),
                    draft = state.mutedWordDraft,
                    placeholder = "词语",
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onDraftChanged = onMutedWordDraftChanged,
                    onAdd = onAddMutedWord,
                    onRemove = onRemoveMutedWord,
                )
                SettingsItemKey.HardMutedWords -> SettingsListEditor(
                    values = state.remotePreferences?.filters?.hardMutedWords.orEmpty(),
                    draft = state.hardMutedWordDraft,
                    placeholder = "强过滤词",
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onDraftChanged = onHardMutedWordDraftChanged,
                    onAdd = onAddHardMutedWord,
                    onRemove = onRemoveHardMutedWord,
                )
                SettingsItemKey.MutedInstances -> SettingsListEditor(
                    values = state.remotePreferences?.filters?.mutedInstances.orEmpty(),
                    draft = state.mutedInstanceDraft,
                    placeholder = "example.com",
                    enabled = state.remotePreferences != null && !state.isMutating,
                    onDraftChanged = onMutedInstanceDraftChanged,
                    onAdd = onAddMutedInstance,
                    onRemove = onRemoveMutedInstance,
                )
                SettingsItemKey.AdminDashboard -> Text(
                    text = item.value.orEmpty(),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SettingsItemKey.TwoFactor,
                SettingsItemKey.Passkeys,
                SettingsItemKey.SigninHistory,
                SettingsItemKey.AvatarDecorations,
                SettingsItemKey.ApiTokens,
                SettingsItemKey.Invites,
                SettingsItemKey.SharedAccess,
                SettingsItemKey.Webhooks,
                SettingsItemKey.AuthorizedApps -> SettingsWebManagementLine(
                    text = item.value.orEmpty(),
                    enabled = item.enabled,
                    onOpen = {
                        nativeManagementKey?.let(onOpenManagement) ?: onOpenWebSettings(item.key)
                    },
                    label = if (nativeManagementKey != null) "查看详情" else "网页版管理",
                )
                else -> Text(
                    text = item.value.orEmpty(),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AdvancedThemeEditor(
    customTheme: HhhlCustomTheme,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeCompactPreview(customTheme = customTheme)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val activeItems = customTheme.activeLabels()
            Text(
                text = if (activeItems.isEmpty()) "颜色、背景图与界面层级" else activeItems.joinToString("、"),
                color = LocalHhhlColors.current.textMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(label = "配置", emphasized = true, onClick = onOpen)
        }
    }
}

@Composable
private fun ThemeCompactPreview(customTheme: HhhlCustomTheme) {
    val colors = LocalHhhlColors.current
    val accent = customTheme.accentColorHex.toColorOrNull() ?: colors.accent
    val background = customTheme.backgroundColorHex.toColorOrNull() ?: colors.pageBackground
    val card = customTheme.cardBackgroundColorHex.toColorOrNull() ?: colors.surface
    Row(
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(background, card, accent).forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .border(
                        width = 1.dp,
                        color = if (index == 2) accent.copy(alpha = 0.42f) else colors.border.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )
        }
    }
}

private fun HhhlCustomTheme.activeLabels(): List<String> {
    return buildList {
        if (accentColorHex.isNotBlank() || accentSoftColorHex.isNotBlank()) add("强调色")
        if (backgroundColorHex.isNotBlank()) add("全局背景")
        if (surfaceColorHex.isNotBlank() || elevatedSurfaceColorHex.isNotBlank() || panelBackgroundColorHex.isNotBlank()) add("界面层级")
        if (chatBackgroundColorHex.isNotBlank()) add("聊天背景")
        if (inputBackgroundColorHex.isNotBlank()) add("输入框")
        if (cardBackgroundColorHex.isNotBlank() || noteBackgroundColorHex.isNotBlank()) add("卡片")
        if (primaryTextColorHex.isNotBlank() || secondaryTextColorHex.isNotBlank() || mutedTextColorHex.isNotBlank() || textInverseColorHex.isNotBlank()) add("文字")
        if (dividerColorHex.isNotBlank() || borderColorHex.isNotBlank() || focusRingColorHex.isNotBlank() || inputBorderColorHex.isNotBlank() || inputFocusedBorderColorHex.isNotBlank()) add("线条")
        if (mediaBackgroundColorHex.isNotBlank()) add("媒体")
        if (avatarBackgroundColorHex.isNotBlank() || badgeBackgroundColorHex.isNotBlank() || unreadBadgeColorHex.isNotBlank()) add("标记")
        if (successColorHex.isNotBlank() || warningColorHex.isNotBlank() || dangerColorHex.isNotBlank() || dangerTextColorHex.isNotBlank()) add("状态色")
        if (toastBackgroundColorHex.isNotBlank() || toastTextColorHex.isNotBlank()) add("提示")
        if (rankBronzeColorHex.isNotBlank() || rankSilverColorHex.isNotBlank() || rankGoldColorHex.isNotBlank() || rankPlatinumColorHex.isNotBlank()) add("成就色")
        if (buttonBackgroundColorHex.isNotBlank() || buttonSelectedBackgroundColorHex.isNotBlank()) add("按钮")
        if (chipBackgroundColorHex.isNotBlank() || chipSelectedBackgroundColorHex.isNotBlank()) add("标签")
        if (topBarBackgroundColorHex.isNotBlank() || bottomNavBackgroundColorHex.isNotBlank() || bottomNavSelectedColorHex.isNotBlank()) add("导航")
        if (incomingBubbleColorHex.isNotBlank() || outgoingBubbleColorHex.isNotBlank() || incomingBubbleTextColorHex.isNotBlank() || outgoingBubbleTextColorHex.isNotBlank() || chatBubbleBorderColorHex.isNotBlank() || chatMentionHighlightColorHex.isNotBlank()) add("气泡")
        if (chatComposerBackgroundColorHex.isNotBlank()) add("输入栏")
        if (noteActionBackgroundColorHex.isNotBlank() || noteReactionBackgroundColorHex.isNotBlank() || noteTreeLineColorHex.isNotBlank() || quoteBackgroundColorHex.isNotBlank()) add("帖子")
        if (overlayScrimColorHex.isNotBlank() || shadowColorHex.isNotBlank()) add("遮罩/阴影")
        if (globalBackgroundImageDataUri.isNotBlank()) add("全局图")
        if (chatBackgroundImageDataUri.isNotBlank()) add("聊天图")
    }
}

@Composable
private fun SettingsCacheLine(
    text: String,
    onClear: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HhhlActionChip(
            label = "清空",
            emphasized = false,
            onClick = onClear,
        )
    }
}

@Composable
private fun SettingsWebManagementLine(
    text: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    label: String = "网页版管理",
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.ifBlank { "需要在网页版继续管理" },
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HhhlActionChip(
            label = label,
            emphasized = true,
            enabled = enabled,
            onClick = onOpen,
        )
    }
}

@Composable
private fun SettingsStatusRow(
    text: String,
    onRetry: () -> Unit,
) {
    HhhlStatusRow(
        text = text,
        actionText = "重试",
        onAction = onRetry,
    )
}

@Composable
private fun SettingsSwitchLine(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "开启" else "关闭",
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        HhhlSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsListEditor(
    values: List<String>,
    draft: String,
    placeholder: String,
    enabled: Boolean,
    onDraftChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhhlTextInput(
                value = draft,
                onValueChange = onDraftChanged,
                enabled = enabled,
                placeholder = placeholder,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Add,
                contentDescription = "添加$placeholder",
                onClick = onAdd,
                emphasized = true,
                enabled = enabled && draft.isNotBlank(),
            )
        }
        if (values.isEmpty()) {
            Text(
                text = "无",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
            values.take(6).forEach { value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "移除$value",
                        onClick = { onRemove(value) },
                        enabled = enabled,
                    )
                }
            }
            if (values.size > 6) {
                Text(
                    text = "+${values.size - 6}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun SettingsItem.checkedValue(state: SettingsUiState): Boolean? {
    val privacy = state.remotePreferences?.privacy ?: return null
    return when (key) {
        SettingsItemKey.LockAccount -> privacy.isLocked
        SettingsItemKey.AutoAcceptFollowed -> privacy.autoAcceptFollowed
        SettingsItemKey.NoCrawle -> privacy.noCrawle
        SettingsItemKey.PreventAiLearning -> privacy.preventAiLearning
        SettingsItemKey.PublicReactions -> privacy.publicReactions
        else -> null
    }
}

@Composable
private fun SettingsItemIcon(
    key: SettingsItemKey,
    enabled: Boolean,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val containerColor = if (enabled) {
        colors.buttonSelectedBackground
    } else {
        colors.buttonBackground.copy(alpha = if (isDarkSurface) 0.28f else 0.36f)
    }
    val borderColor = if (enabled) {
        colors.focusRing.copy(alpha = if (isDarkSurface) 0.34f else 0.22f)
    } else {
        colors.border.copy(alpha = if (isDarkSurface) 0.20f else 0.16f)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = settingsItemIcon(key),
            contentDescription = null,
            tint = if (enabled) hhhlReadableOnControlColor(containerColor, colors.accent) else colors.textMuted,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun settingsItemIcon(key: SettingsItemKey): ImageVector {
    return when (key) {
        SettingsItemKey.Theme -> Icons.Filled.Palette
        SettingsItemKey.AdvancedTheme -> Icons.Filled.AutoAwesome
        SettingsItemKey.TimelineDensity -> Icons.Filled.Tune
        SettingsItemKey.AccountProfile -> Icons.Filled.Person
        SettingsItemKey.AdminDashboard -> Icons.Filled.AdminPanelSettings
        SettingsItemKey.TwoFactor -> Icons.Filled.Security
        SettingsItemKey.Passkeys -> Icons.Filled.VpnKey
        SettingsItemKey.SigninHistory -> Icons.Filled.History
        SettingsItemKey.AvatarDecorations -> Icons.Filled.AutoAwesome
        SettingsItemKey.DefaultNoteVisibility -> Icons.Filled.Visibility
        SettingsItemKey.LockAccount -> Icons.Filled.Lock
        SettingsItemKey.AutoAcceptFollowed -> Icons.Outlined.PersonAdd
        SettingsItemKey.NoCrawle -> Icons.Outlined.SearchOff
        SettingsItemKey.PreventAiLearning -> Icons.Filled.AutoAwesome
        SettingsItemKey.PublicReactions -> Icons.Outlined.AddReaction
        SettingsItemKey.NotificationBadges -> Icons.Filled.Notifications
        SettingsItemKey.BackgroundNotifications -> Icons.Filled.Sync
        SettingsItemKey.SpecialCareBackgroundNotifications -> Icons.Outlined.FavoriteBorder
        SettingsItemKey.ChatMessageCache -> Icons.Filled.Storage
        SettingsItemKey.MuteReactions -> Icons.AutoMirrored.Outlined.VolumeOff
        SettingsItemKey.MuteFollows -> Icons.Outlined.PersonRemove
        SettingsItemKey.MutedWords -> Icons.Outlined.Block
        SettingsItemKey.HardMutedWords -> Icons.Outlined.FilterList
        SettingsItemKey.MutedInstances -> Icons.Filled.Language
        SettingsItemKey.ApiTokens -> Icons.Filled.Key
        SettingsItemKey.Invites -> Icons.Filled.CardGiftcard
        SettingsItemKey.SharedAccess -> Icons.Outlined.Share
        SettingsItemKey.Webhooks -> Icons.Outlined.Link
        SettingsItemKey.AuthorizedApps -> Icons.Filled.Apps
    }
}

@Composable
private fun <T> SettingsDropdownPicker(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val pickerBackground = if (isDarkSurface) {
        colors.surfaceElevated.copy(alpha = 0.70f)
    } else {
        colors.surfaceElevated.copy(alpha = 0.68f)
    }
    val pickerBorder = colors.focusRing.copy(alpha = if (isDarkSurface) 0.14f else 0.08f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(pickerBackground)
                .border(
                    width = 1.dp,
                    color = pickerBorder,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "更换",
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                HhhlDropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            color = if (isSelected(option)) {
                                colors.accent
                            } else {
                                colors.textPrimary
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

fun settingsGroupLabels(groups: List<SettingsGroup>): List<String> {
    return groups.map { it.label }
}

fun settingsItemLabels(groups: List<SettingsGroup>): List<String> {
    return groups.flatMap { group -> group.items.map { it.label } }
}

fun settingsWebManagementPath(key: SettingsItemKey): String? {
    return when (key) {
        SettingsItemKey.TwoFactor,
        SettingsItemKey.Passkeys -> "/settings/security"
        SettingsItemKey.SigninHistory -> "/settings/security#signin-history"
        SettingsItemKey.ApiTokens -> "/settings/api"
        SettingsItemKey.Invites -> null
        SettingsItemKey.Webhooks -> "/settings/webhook"
        SettingsItemKey.AuthorizedApps -> "/settings/apps"
        SettingsItemKey.AvatarDecorations -> null
        else -> null
    }
}

fun settingsManagementSectionKey(key: SettingsItemKey): SettingsManagementSectionKey? {
    return when (key) {
        SettingsItemKey.ApiTokens -> SettingsManagementSectionKey.ApiTokens
        SettingsItemKey.Invites -> SettingsManagementSectionKey.Invites
        SettingsItemKey.SharedAccess -> SettingsManagementSectionKey.SharedAccess
        SettingsItemKey.Webhooks -> SettingsManagementSectionKey.Webhooks
        SettingsItemKey.AuthorizedApps -> SettingsManagementSectionKey.AuthorizedApps
        SettingsItemKey.SigninHistory -> SettingsManagementSectionKey.SigninHistory
        SettingsItemKey.AvatarDecorations -> SettingsManagementSectionKey.AvatarDecorations
        else -> null
    }
}
