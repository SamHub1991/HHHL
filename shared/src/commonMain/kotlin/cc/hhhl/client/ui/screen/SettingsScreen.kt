package cc.hhhl.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.toColorOrNull
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDropdownMenuMaxHeight
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlSectionHeader
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.ThemePicker
import cc.hhhl.client.ui.component.TimelineDensityPicker

@Composable
fun SettingsScreen(
    state: SettingsUiState,
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
    onClearChatMessageCache: () -> Unit = {},
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
                            color = LocalHhhlColors.current.subtleText,
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
        }
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
                    MaterialTheme.colorScheme.onBackground
                } else {
                    LocalHhhlColors.current.subtleText
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
                    onCustomThemeChanged = onCustomThemeChanged,
                    onReset = onResetCustomTheme,
                    onPickGlobalBackgroundImage = onPickGlobalBackgroundImage,
                    onClearGlobalBackgroundImage = onClearGlobalBackgroundImage,
                    onPickChatBackgroundImage = onPickChatBackgroundImage,
                    onClearChatBackgroundImage = onClearChatBackgroundImage,
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
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SettingsItemKey.TwoFactor,
                SettingsItemKey.Passkeys,
                SettingsItemKey.SigninHistory,
                SettingsItemKey.ApiTokens,
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
                    color = LocalHhhlColors.current.subtleText,
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
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onReset: () -> Unit,
    onPickGlobalBackgroundImage: () -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onPickChatBackgroundImage: () -> Unit,
    onClearChatBackgroundImage: () -> Unit,
) {
    HhhlInlinePanel(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeSwatchGroup(
            label = "强调色",
            selectedHex = customTheme.accentColorHex,
            swatches = accentThemeSwatches,
            onSelected = { hex -> onCustomThemeChanged(customTheme.copy(accentColorHex = hex)) },
        )
        ThemeSwatchGroup(
            label = "全局背景色",
            selectedHex = customTheme.backgroundColorHex,
            swatches = backgroundThemeSwatches,
            onSelected = { hex -> onCustomThemeChanged(customTheme.copy(backgroundColorHex = hex)) },
        )
        ThemeSwatchGroup(
            label = "聊天背景色",
            selectedHex = customTheme.chatBackgroundColorHex,
            swatches = chatBackgroundThemeSwatches,
            onSelected = { hex -> onCustomThemeChanged(customTheme.copy(chatBackgroundColorHex = hex)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "全局背景图",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(label = "选择", emphasized = true, onClick = onPickGlobalBackgroundImage)
            HhhlActionChip(
                label = "清除",
                enabled = customTheme.globalBackgroundImageDataUri.isNotBlank(),
                onClick = onClearGlobalBackgroundImage,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "聊天背景图",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(label = "选择", emphasized = true, onClick = onPickChatBackgroundImage)
            HhhlActionChip(
                label = "清除",
                enabled = customTheme.chatBackgroundImageDataUri.isNotBlank(),
                onClick = onClearChatBackgroundImage,
            )
        }
        HhhlActionChip(
            label = "恢复默认",
            enabled = customTheme.enabled,
            onClick = onReset,
        )
    }
}

@Composable
private fun ThemeSwatchGroup(
    label: String,
    selectedHex: String,
    swatches: List<ThemeColorSwatch>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            swatches.forEach { swatch ->
                ThemeColorSwatchButton(
                    swatch = swatch,
                    selected = selectedHex.equals(swatch.hex, ignoreCase = true),
                    onClick = { onSelected(swatch.hex) },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatchButton(
    swatch: ThemeColorSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(swatch.hex.toColorOrNull() ?: Color.Transparent)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    LocalHhhlColors.current.divider.copy(alpha = 0.76f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
    )
}

private data class ThemeColorSwatch(
    val hex: String,
)

private val accentThemeSwatches = listOf(
    "#1D9BF0",
    "#007AFF",
    "#00C7BE",
    "#34C759",
    "#7856FF",
    "#F91880",
    "#FF7A00",
    "#8E8E93",
).map(::ThemeColorSwatch)

private val backgroundThemeSwatches = listOf(
    "#F7F9FA",
    "#F5F5F7",
    "#FFFFFF",
    "#F5F7F6",
    "#FAFCF7",
    "#15181D",
    "#101010",
    "#000000",
).map(::ThemeColorSwatch)

private val chatBackgroundThemeSwatches = listOf(
    "#F7F9FA",
    "#F2F6F8",
    "#F5F5F7",
    "#EEF6FF",
    "#EFF7F4",
    "#151B23",
    "#101418",
    "#000000",
).map(::ThemeColorSwatch)

@Composable
private fun SettingsCacheLine(
    text: String,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = LocalHhhlColors.current.subtleText,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.ifBlank { "需要在网页版继续管理" },
            color = LocalHhhlColors.current.subtleText,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "开启" else "关闭",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelMedium,
        )
        Switch(
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
                color = LocalHhhlColors.current.subtleText,
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
                        color = MaterialTheme.colorScheme.onBackground,
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
                    color = LocalHhhlColors.current.subtleText,
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
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.025f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = settingsItemIcon(key),
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else LocalHhhlColors.current.subtleText,
            modifier = Modifier.size(18.dp),
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val pickerBackground = if (isDarkSurface) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
    }
    val pickerBorder = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.14f else 0.08f)

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
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "更换",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = HhhlDropdownMenuMaxHeight),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            color = if (isSelected(option)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
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
        SettingsItemKey.Webhooks -> "/settings/webhook"
        SettingsItemKey.AuthorizedApps -> "/settings/apps"
        else -> null
    }
}

fun settingsManagementSectionKey(key: SettingsItemKey): SettingsManagementSectionKey? {
    return when (key) {
        SettingsItemKey.ApiTokens -> SettingsManagementSectionKey.ApiTokens
        SettingsItemKey.SharedAccess -> SettingsManagementSectionKey.SharedAccess
        SettingsItemKey.Webhooks -> SettingsManagementSectionKey.Webhooks
        SettingsItemKey.AuthorizedApps -> SettingsManagementSectionKey.AuthorizedApps
        SettingsItemKey.SigninHistory -> SettingsManagementSectionKey.SigninHistory
        else -> null
    }
}
