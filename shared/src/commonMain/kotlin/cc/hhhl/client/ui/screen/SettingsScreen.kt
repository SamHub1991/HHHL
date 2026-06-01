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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudQueue
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
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ModelTraining
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
import cc.hhhl.client.ai.AiAutomationModelConfig
import cc.hhhl.client.ai.AiProviderPreset
import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiTaskStatus
import cc.hhhl.client.ai.defaultChatModelForSettings
import cc.hhhl.client.ai.defaultFastModelForSettings
import cc.hhhl.client.ai.defaultLongContextModelForSettings
import cc.hhhl.client.ai.normalizedAiUsage
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.auth.AccountSession
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.InstanceMeta
import cc.hhhl.client.notification.ChatNoiseReductionSettings
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.toColorOrNull
import cc.hhhl.client.update.AppReleaseNotes
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlAnimatedSegmentedControl
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
    onListGesturesEnabledChanged: (Boolean) -> Unit = {},
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
    onBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onChatNoiseSettingsChanged: (ChatNoiseReductionSettings) -> Unit = {},
    onChatNoiseKeywordDraftChanged: (String) -> Unit = {},
    onAddChatNoiseKeyword: () -> Unit = {},
    onRemoveChatNoiseKeyword: (String) -> Unit = {},
    onChatNoiseUserDraftChanged: (String) -> Unit = {},
    onAddChatNoiseUser: () -> Unit = {},
    onRemoveChatNoiseUser: (String) -> Unit = {},
    onCheckForUpdates: (((String) -> Unit) -> Unit) = { report -> report("当前平台暂不支持应用内更新") },
    appVersionName: String = "",
    onOpenReleaseNotes: () -> Unit = {},
    onClearChatMessageCache: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {},
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
    onOpenAiSettings: () -> Unit = {},
    onAiSettingsChanged: (AiSettings) -> Unit = {},
    onAiProviderSelected: (AiProviderPreset) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onAiWorkspacePlan: () -> Unit = {},
    aiConnectionMessage: String? = null,
    isTestingAiConnection: Boolean = false,
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
                        onListGesturesEnabledChanged = onListGesturesEnabledChanged,
                        onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                        onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                        onBackgroundNotificationsChanged = onBackgroundNotificationsChanged,
                        onSpecialCareBackgroundNotificationsChanged = onSpecialCareBackgroundNotificationsChanged,
                        onChatNoiseSettingsChanged = onChatNoiseSettingsChanged,
                        onChatNoiseKeywordDraftChanged = onChatNoiseKeywordDraftChanged,
                        onAddChatNoiseKeyword = onAddChatNoiseKeyword,
                        onRemoveChatNoiseKeyword = onRemoveChatNoiseKeyword,
                        onChatNoiseUserDraftChanged = onChatNoiseUserDraftChanged,
                        onAddChatNoiseUser = onAddChatNoiseUser,
                        onRemoveChatNoiseUser = onRemoveChatNoiseUser,
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
                        onOpenAiSettings = onOpenAiSettings,
                        onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
                        onAiSettingsChanged = onAiSettingsChanged,
                        onAiProviderSelected = onAiProviderSelected,
                        onTestAiConnection = onTestAiConnection,
                        onAiWorkspacePlan = onAiWorkspacePlan,
                        aiConnectionMessage = aiConnectionMessage,
                        isTestingAiConnection = isTestingAiConnection,
                    )
                    HhhlDivider()
                }
            }
            item(key = "settings-app-update", contentType = "settings-app-update") {
                SettingsAppUpdatePanel(
                    status = updateStatus,
                    isChecking = isCheckingUpdate,
                    appVersionName = appVersionName,
                    onCheck = {
                        if (isCheckingUpdate) return@SettingsAppUpdatePanel
                        isCheckingUpdate = true
                        updateStatus = "正在检查 GitHub Release"
                        onCheckForUpdates { message ->
                            updateStatus = message
                            isCheckingUpdate = false
                        }
                    },
                    onOpenReleaseNotes = onOpenReleaseNotes,
                )
            }
        }
    }
}

private val AiModelConfigurationItemKeys = setOf(
    SettingsItemKey.AiProvider,
    SettingsItemKey.AiBaseUrl,
    SettingsItemKey.AiApiKey,
    SettingsItemKey.AiChatModel,
    SettingsItemKey.AiFastModel,
    SettingsItemKey.AiLongContextModel,
    SettingsItemKey.AiVisionModel,
    SettingsItemKey.AiEmbeddingModel,
)

@Composable
fun AiSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAiSettingsChanged: (AiSettings) -> Unit = {},
    onAiProviderSelected: (AiProviderPreset) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onAiWorkspacePlan: () -> Unit = {},
    aiConnectionMessage: String? = null,
    isTestingAiConnection: Boolean = false,
) {
    val aiGroup = remember(state.aiSettings, state.aiTasks, state.aiUsage) {
        SettingsRepository().aiSettingsGroup(
            aiSettings = state.aiSettings,
            aiTasks = state.aiTasks,
            aiUsage = state.aiUsage,
        )
    }
    val visibleAiItems = remember(aiGroup.items) {
        aiGroup.items.filterNot { item -> item.key in AiModelConfigurationItemKeys }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "AI 设置",
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "ai-settings-header", contentType = "settings-group-header") {
                SettingsGroupHeader(label = aiGroup.label)
            }
            items(
                items = visibleAiItems,
                key = { item -> "ai-settings-item-${item.key}" },
                contentType = { "settings-row" },
            ) { item ->
                SettingsRow(
                    item = item,
                    state = state,
                    onAiSettingsChanged = onAiSettingsChanged,
                    onAiProviderSelected = onAiProviderSelected,
                    onTestAiConnection = onTestAiConnection,
                    onAiWorkspacePlan = onAiWorkspacePlan,
                    aiConnectionMessage = aiConnectionMessage,
                    isTestingAiConnection = isTestingAiConnection,
                )
                HhhlDivider()
                if (item.key == SettingsItemKey.AiFloatingAssistant) {
                    SettingsAiModelConfigurationTabs(
                        settings = state.aiSettings,
                        enabled = state.aiSettings.enabled,
                        defaultConnectionMessage = aiConnectionMessage,
                        isTestingDefaultConnection = isTestingAiConnection,
                        onSettingsChanged = onAiSettingsChanged,
                        onDefaultProviderSelected = onAiProviderSelected,
                        onTestDefaultConnection = onTestAiConnection,
                    )
                    HhhlDivider()
                }
            }
        }
    }
}

@Composable
private fun SettingsAiModelConfigurationTabs(
    settings: AiSettings,
    enabled: Boolean,
    defaultConnectionMessage: String?,
    isTestingDefaultConnection: Boolean,
    onSettingsChanged: (AiSettings) -> Unit,
    onDefaultProviderSelected: (AiProviderPreset) -> Unit,
    onTestDefaultConnection: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "模型配置",
            color = LocalHhhlColors.current.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HhhlAnimatedSegmentedControl(
            labels = listOf("默认配置", "自动化配置"),
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (selectedTab == 0) {
            SettingsAiDefaultModelConfigFields(
                settings = settings,
                enabled = enabled,
                connectionMessage = defaultConnectionMessage,
                isTestingConnection = isTestingDefaultConnection,
                onSettingsChanged = onSettingsChanged,
                onProviderSelected = onDefaultProviderSelected,
                onTestConnection = onTestDefaultConnection,
            )
        } else {
            SettingsAiAutomationModelConfigFields(
                settings = settings,
                enabled = enabled,
                onSettingsChanged = onSettingsChanged,
            )
        }
    }
}

@Composable
private fun SettingsAiDefaultModelConfigFields(
    settings: AiSettings,
    enabled: Boolean,
    connectionMessage: String?,
    isTestingConnection: Boolean,
    onSettingsChanged: (AiSettings) -> Unit,
    onProviderSelected: (AiProviderPreset) -> Unit,
    onTestConnection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "默认配置会用于普通 AI 助手、草稿处理、总结和未单独指定模型的自动化任务。",
            color = LocalHhhlColors.current.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsModelProviderPicker(
            provider = settings.provider,
            onProviderSelected = onProviderSelected,
        )
        SettingsTextSettingLine(
            value = settings.baseUrl,
            placeholder = "https://api.openai.com/v1",
            inputLabel = "Base URL",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(baseUrl = it)) },
        )
        SettingsTextSettingLine(
            value = settings.apiKey,
            placeholder = "sk-...",
            inputLabel = "API Key",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(apiKey = it)) },
            trailing = {
                HhhlActionChip(
                    label = if (isTestingConnection) "测试中" else "测试",
                    emphasized = true,
                    enabled = enabled && !isTestingConnection,
                    onClick = onTestConnection,
                )
            },
            helper = connectionMessage,
        )
        SettingsTextSettingLine(
            value = settings.chatModel,
            placeholder = settings.provider.defaultChatModelForSettings().ifBlank { "对话模型" },
            inputLabel = "对话模型",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(chatModel = it)) },
        )
        SettingsTextSettingLine(
            value = settings.fastModel,
            placeholder = settings.provider.defaultFastModelForSettings().ifBlank { "快速模型" },
            inputLabel = "快速模型",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(fastModel = it)) },
        )
        SettingsTextSettingLine(
            value = settings.longContextModel,
            placeholder = settings.provider.defaultLongContextModelForSettings().ifBlank { "长上下文模型" },
            inputLabel = "长上下文模型",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(longContextModel = it)) },
        )
        SettingsTextSettingLine(
            value = settings.visionModel,
            placeholder = "视觉模型",
            inputLabel = "视觉模型",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(visionModel = it)) },
        )
        SettingsTextSettingLine(
            value = settings.embeddingModel,
            placeholder = "向量模型",
            inputLabel = "向量模型",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(embeddingModel = it)) },
        )
    }
}

@Composable
private fun SettingsAiAutomationModelConfigFields(
    settings: AiSettings,
    enabled: Boolean,
    onSettingsChanged: (AiSettings) -> Unit,
) {
    val automation = settings.automationRuleDraftModel
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "只影响自动化页根据草稿生成规则；不启用时默认使用默认配置里的模型。",
            color = LocalHhhlColors.current.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsSwitchLine(
            checked = automation.enabled,
            enabled = enabled,
            onCheckedChange = { checked ->
                onSettingsChanged(settings.copy(automationRuleDraftModel = automation.copy(enabled = checked)))
            },
            supportingText = if (automation.enabled) "已启用后，AI 生成规则草稿会使用下方模型。" else "未启用时，仍使用默认配置里的模型。",
        )
        SettingsModelProviderPicker(
            provider = automation.provider,
            onProviderSelected = { provider ->
                onSettingsChanged(
                    settings.copy(
                        automationRuleDraftModel = automation.withProviderDefaults(provider),
                    ),
                )
            },
        )
        SettingsTextSettingLine(
            value = automation.baseUrl,
            placeholder = automation.provider.defaultBaseUrl.ifBlank { "https://api.openai.com/v1" },
            inputLabel = "Base URL",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(automationRuleDraftModel = automation.copy(baseUrl = it))) },
        )
        SettingsTextSettingLine(
            value = automation.apiKey,
            placeholder = "sk-...",
            inputLabel = "API Key",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(automationRuleDraftModel = automation.copy(apiKey = it))) },
        )
        SettingsTextSettingLine(
            value = automation.model,
            placeholder = automation.provider.defaultLongContextModelForSettings()
                .ifBlank { automation.provider.defaultChatModelForSettings() }
                .ifBlank { "模型名称" },
            inputLabel = "模型名称",
            enabled = enabled,
            onValueChange = { onSettingsChanged(settings.copy(automationRuleDraftModel = automation.copy(model = it))) },
        )
    }
}

@Composable
private fun SettingsModelProviderPicker(
    provider: AiProviderPreset,
    onProviderSelected: (AiProviderPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "模型来源",
            color = LocalHhhlColors.current.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsDropdownPicker(
            selectedLabel = provider.label,
            options = AiProviderPreset.entries.toList(),
            optionLabel = { it.label },
            isSelected = { it == provider },
            onSelected = onProviderSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AiAutomationModelConfig.withProviderDefaults(provider: AiProviderPreset): AiAutomationModelConfig {
    val defaultModel = provider.defaultLongContextModelForSettings()
        .ifBlank { provider.defaultChatModelForSettings() }
    return copy(
        provider = provider,
        baseUrl = provider.defaultBaseUrl.takeIf { it.isNotBlank() } ?: baseUrl,
        model = defaultModel.takeIf { it.isNotBlank() } ?: model,
    )
}
@Composable
private fun SettingsAppUpdatePanel(
    status: String?,
    isChecking: Boolean,
    appVersionName: String,
    onCheck: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val currentVersionText = appVersionName.trim().removePrefix("v").ifBlank { "未知版本" }
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
                    text = status ?: "当前版本 $currentVersionText · 从 GitHub Release 检查新版 APK",
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HhhlActionChip(
                label = "当前 $currentVersionText",
                enabled = false,
                onClick = {},
            )
            HhhlActionChip(
                label = "更新日志",
                onClick = onOpenReleaseNotes,
            )
        }
    }
}

@Composable
fun ReleaseNotesTimelineScreen(
    notes: List<AppReleaseNotes>,
    currentVersionName: String,
    onBack: () -> Unit,
) {
    val cleanCurrentVersion = currentVersionName.trim().removePrefix("v")
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "更新日志",
            supportingText = cleanCurrentVersion.takeIf { it.isNotBlank() }?.let { "当前版本 $it" },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = notes,
                key = { item -> "release-notes-${item.versionName}" },
                contentType = { "release-notes-item" },
            ) { item ->
                ReleaseNotesTimelineItem(
                    notes = item,
                    isCurrent = item.versionName == cleanCurrentVersion,
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotesTimelineItem(
    notes: AppReleaseNotes,
    isCurrent: Boolean,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        emphasized = isCurrent,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCurrent) colors.buttonSelectedBackground else colors.inputBackground)
                    .border(1.dp, colors.focusRing.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    tint = if (isCurrent) {
                        hhhlReadableOnControlColor(colors.buttonSelectedBackground, colors.accent)
                    } else {
                        colors.textSecondary
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notes.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrent) {
                        HhhlActionChip(label = "当前", emphasized = true, enabled = false, onClick = {})
                    }
                }
                Text(
                    text = notes.summary,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            notes.highlights.forEach { item ->
                Text(
                    text = "· $item",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
    onThemeSelected: (HhhlThemePreset) -> Unit = {},
    customTheme: HhhlCustomTheme = HhhlCustomTheme(),
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit = {},
    onResetCustomTheme: () -> Unit = {},
    onPickGlobalBackgroundImage: () -> Unit = {},
    onClearGlobalBackgroundImage: () -> Unit = {},
    onPickChatBackgroundImage: () -> Unit = {},
    onClearChatBackgroundImage: () -> Unit = {},
    onTimelineDensitySelected: (TimelineDensity) -> Unit = {},
    onListGesturesEnabledChanged: (Boolean) -> Unit = {},
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit = {},
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit = {},
    onBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onSpecialCareBackgroundNotificationsChanged: (Boolean) -> Unit = {},
    onChatNoiseSettingsChanged: (ChatNoiseReductionSettings) -> Unit = {},
    onChatNoiseKeywordDraftChanged: (String) -> Unit = {},
    onAddChatNoiseKeyword: () -> Unit = {},
    onRemoveChatNoiseKeyword: (String) -> Unit = {},
    onChatNoiseUserDraftChanged: (String) -> Unit = {},
    onAddChatNoiseUser: () -> Unit = {},
    onRemoveChatNoiseUser: (String) -> Unit = {},
    onClearChatMessageCache: () -> Unit = {},
    onOpenThemeCustomization: () -> Unit = {},
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
    onOpenAiSettings: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    onAiSettingsChanged: (AiSettings) -> Unit = {},
    onAiProviderSelected: (AiProviderPreset) -> Unit = {},
    onTestAiConnection: () -> Unit = {},
    onAiWorkspacePlan: () -> Unit = {},
    aiConnectionMessage: String? = null,
    isTestingAiConnection: Boolean = false,
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
                    item.key == SettingsItemKey.AiSettingsEntry -> {
                        Modifier.clickable(enabled = item.enabled, onClick = onOpenAiSettings)
                    }
                    item.key == SettingsItemKey.BatteryOptimization -> {
                        Modifier.clickable(enabled = item.enabled, onClick = onOpenBatteryOptimizationSettings)
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
                SettingsItemKey.ListGestures -> SettingsSwitchLine(
                    checked = state.listGesturesEnabled,
                    enabled = true,
                    onCheckedChange = onListGesturesEnabledChanged,
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
                    supportingText = "开启后后台会保持实时同步，并把聊天/通知事件交给自动化规则。",
                )
                SettingsItemKey.SpecialCareBackgroundNotifications -> SettingsSwitchLine(
                    checked = state.specialCareBackgroundNotificationsEnabled,
                    enabled = state.backgroundNotificationsEnabled,
                    onCheckedChange = onSpecialCareBackgroundNotificationsChanged,
                    supportingText = "只控制特别关心提醒展示，不影响普通聊天自动化扫描。",
                )
                SettingsItemKey.BatteryOptimization -> SettingsNavigationLine(
                    text = "允许系统放宽后台省电限制，降低锁屏后实时同步被挂起的概率。",
                    enabled = item.enabled,
                    onOpen = onOpenBatteryOptimizationSettings,
                    label = "去设置",
                )
                SettingsItemKey.ChatNoiseAggregate -> SettingsChatNoiseAggregateLine(
                    settings = state.chatNoiseReductionSettings,
                    enabled = item.enabled,
                    onChanged = onChatNoiseSettingsChanged,
                )
                SettingsItemKey.ChatNoiseImportantOnly -> SettingsSwitchLine(
                    checked = state.chatNoiseReductionSettings.importantOnly,
                    enabled = item.enabled,
                    onCheckedChange = { onChatNoiseSettingsChanged(state.chatNoiseReductionSettings.copy(importantOnly = it)) },
                    supportingText = "开启后普通聊天室只弹出 @ 我、回复/引用我、特别关心、关键词或指定用户消息。",
                )
                SettingsItemKey.ChatNoiseAiImportance -> SettingsSwitchLine(
                    checked = state.chatNoiseReductionSettings.aiImportanceEnabled,
                    enabled = item.enabled,
                    onCheckedChange = { onChatNoiseSettingsChanged(state.chatNoiseReductionSettings.copy(aiImportanceEnabled = it)) },
                    supportingText = "后台会用已配置 AI 判断未命中规则的聊天是否值得提醒；不可用时回退为静默。",
                )
                SettingsItemKey.ChatNoiseKeywords -> SettingsListEditor(
                    values = state.chatNoiseReductionSettings.keywordRules,
                    draft = state.chatNoiseKeywordDraft,
                    placeholder = "关键词或短语",
                    enabled = item.enabled,
                    onDraftChanged = onChatNoiseKeywordDraftChanged,
                    onAdd = onAddChatNoiseKeyword,
                    onRemove = onRemoveChatNoiseKeyword,
                )
                SettingsItemKey.ChatNoiseUsers -> SettingsListEditor(
                    values = state.chatNoiseReductionSettings.userRules,
                    draft = state.chatNoiseUserDraft,
                    placeholder = "用户 ID、@用户名、@用户名@实例或昵称",
                    enabled = item.enabled,
                    onDraftChanged = onChatNoiseUserDraftChanged,
                    onAdd = onAddChatNoiseUser,
                    onRemove = onRemoveChatNoiseUser,
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
                SettingsItemKey.AiSettingsEntry -> SettingsNavigationLine(
                    text = item.value.orEmpty(),
                    label = "配置",
                    enabled = item.enabled,
                    onOpen = onOpenAiSettings,
                )
                SettingsItemKey.AiFloatingAssistant -> SettingsSwitchLine(
                    checked = state.aiSettings.floatingAssistantEnabled,
                    enabled = true,
                    onCheckedChange = { onAiSettingsChanged(state.aiSettings.copy(floatingAssistantEnabled = it)) },
                    supportingText = "在任意页面显示可拖动的小助手光球；隐藏后可在这里重新打开。",
                )
                SettingsItemKey.AiEnabled -> SettingsSwitchLine(
                    checked = state.aiSettings.enabled,
                    enabled = true,
                    onCheckedChange = { onAiSettingsChanged(state.aiSettings.copy(enabled = it)) },
                )
                SettingsItemKey.AiProvider -> SettingsDropdownPicker(
                    selectedLabel = state.aiSettings.provider.label,
                    options = AiProviderPreset.entries.toList(),
                    optionLabel = { it.label },
                    isSelected = { it == state.aiSettings.provider },
                    onSelected = onAiProviderSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.AiBaseUrl -> SettingsTextSettingLine(
                    value = state.aiSettings.baseUrl,
                    placeholder = "https://api.openai.com/v1",
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(baseUrl = it)) },
                )
                SettingsItemKey.AiApiKey -> SettingsTextSettingLine(
                    value = state.aiSettings.apiKey,
                    placeholder = "sk-...",
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(apiKey = it)) },
                    trailing = {
                        HhhlActionChip(
                            label = if (isTestingAiConnection) "测试中" else "测试",
                            emphasized = true,
                            enabled = item.enabled && !isTestingAiConnection,
                            onClick = onTestAiConnection,
                        )
                    },
                    helper = aiConnectionMessage,
                )
                SettingsItemKey.AiChatModel -> SettingsTextSettingLine(
                    value = state.aiSettings.chatModel,
                    placeholder = state.aiSettings.provider.defaultChatModelForSettings().ifBlank { "对话模型" },
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(chatModel = it)) },
                )
                SettingsItemKey.AiFastModel -> SettingsTextSettingLine(
                    value = state.aiSettings.fastModel,
                    placeholder = state.aiSettings.provider.defaultFastModelForSettings().ifBlank { "快速模型" },
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(fastModel = it)) },
                )
                SettingsItemKey.AiLongContextModel -> SettingsTextSettingLine(
                    value = state.aiSettings.longContextModel,
                    placeholder = state.aiSettings.provider.defaultLongContextModelForSettings().ifBlank { "长上下文模型" },
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(longContextModel = it)) },
                )
                SettingsItemKey.AiVisionModel -> SettingsTextSettingLine(
                    value = state.aiSettings.visionModel,
                    placeholder = "视觉模型",
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(visionModel = it)) },
                )
                SettingsItemKey.AiEmbeddingModel -> SettingsTextSettingLine(
                    value = state.aiSettings.embeddingModel,
                    placeholder = "向量模型",
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(embeddingModel = it)) },
                )
                SettingsItemKey.AiReadPermissions -> SettingsAiPermissionLine(
                    settings = state.aiSettings,
                    enabled = item.enabled,
                    onChanged = onAiSettingsChanged,
                )
                SettingsItemKey.AiAutomation -> SettingsSwitchLine(
                    checked = state.aiSettings.automationAllowed,
                    enabled = item.enabled,
                    onCheckedChange = { onAiSettingsChanged(state.aiSettings.copy(automationAllowed = it)) },
                )
                SettingsItemKey.AiAssistantLowRiskAutoApproval -> SettingsSwitchLine(
                    checked = state.aiSettings.assistantLowRiskAutoApproval,
                    enabled = item.enabled,
                    onCheckedChange = { checked ->
                        onAiSettingsChanged(
                            state.aiSettings.copy(
                                assistantLowRiskAutoApproval = checked,
                            ),
                        )
                    },
                )
                SettingsItemKey.AiAssistantHighRiskAutoApproval -> SettingsSwitchLine(
                    checked = state.aiSettings.assistantHighRiskAutoApproval,
                    enabled = item.enabled,
                    onCheckedChange = { checked ->
                        onAiSettingsChanged(
                            state.aiSettings.copy(
                                assistantHighRiskAutoApproval = checked,
                            ),
                        )
                    },
                )
                SettingsItemKey.AiBackground -> SettingsAiBackgroundLine(
                    settings = state.aiSettings,
                    enabled = item.enabled,
                    onChanged = onAiSettingsChanged,
                )
                SettingsItemKey.AiQueue -> SettingsAiQueueLine(
                    state = state,
                    enabled = item.enabled,
                    onAiWorkspacePlan = onAiWorkspacePlan,
                )
                SettingsItemKey.AiLimits -> SettingsAiLimitsLine(
                    settings = state.aiSettings,
                    enabled = item.enabled,
                    onChanged = onAiSettingsChanged,
                )
                SettingsItemKey.AiTone -> SettingsTextSettingLine(
                    value = state.aiSettings.tonePreference,
                    placeholder = "自然、简洁、贴近当前语气",
                    enabled = item.enabled,
                    onValueChange = { onAiSettingsChanged(state.aiSettings.copy(tonePreference = it)) },
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
private fun SettingsTextSettingLine(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    helper: String? = null,
) {
    val colors = LocalHhhlColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhhlTextInput(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                label = inputLabel,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        helper?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsAiPermissionLine(
    settings: AiSettings,
    enabled: Boolean,
    onChanged: (AiSettings) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsAiToggleChip("帖子", settings.readTimelineAllowed, enabled) {
            onChanged(settings.copy(readTimelineAllowed = it))
        }
        SettingsAiToggleChip("通知", settings.readNotificationsAllowed, enabled) {
            onChanged(settings.copy(readNotificationsAllowed = it))
        }
        SettingsAiToggleChip("聊天", settings.readChatAllowed, enabled) {
            onChanged(settings.copy(readChatAllowed = it))
        }
        SettingsAiToggleChip("私聊", settings.readPrivateChatAllowed, enabled && settings.readChatAllowed) {
            onChanged(settings.copy(readPrivateChatAllowed = it))
        }
        SettingsAiToggleChip("草稿", settings.readDraftsAllowed, enabled) {
            onChanged(settings.copy(readDraftsAllowed = it))
        }
        SettingsAiToggleChip("资料", settings.readProfileAllowed, enabled) {
            onChanged(settings.copy(readProfileAllowed = it))
        }
        SettingsAiToggleChip("敏感内容", settings.uploadSensitiveContentAllowed, enabled) {
            onChanged(settings.copy(uploadSensitiveContentAllowed = it))
        }
    }
}

@Composable
private fun SettingsAiBackgroundLine(
    settings: AiSettings,
    enabled: Boolean,
    onChanged: (AiSettings) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsAiToggleChip("后台队列", settings.backgroundAllowed, enabled) {
            onChanged(settings.copy(backgroundAllowed = it))
        }
        SettingsAiToggleChip("仅 Wi-Fi", settings.wifiOnlyBackground, enabled && settings.backgroundAllowed) {
            onChanged(settings.copy(wifiOnlyBackground = it))
        }
        SettingsAiToggleChip("工具权限", settings.toolsAllowed, enabled) {
            onChanged(settings.copy(toolsAllowed = it))
        }
    }
}

@Composable
private fun SettingsAiQueueLine(
    state: SettingsUiState,
    enabled: Boolean,
    onAiWorkspacePlan: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val latest = state.aiTasks.maxByOrNull { it.updatedAtEpochMillis }
    val usage = state.aiUsage.normalizedAiUsage()
    val remaining = (state.aiSettings.dailyRequestLimit - usage.requestCount).coerceAtLeast(0)
    val pending = state.aiTasks.count { it.status == AiTaskStatus.Pending }
    val running = state.aiTasks.count { it.status == AiTaskStatus.Running }
    val completed = state.aiTasks.count { it.status == AiTaskStatus.Completed }
    val failed = state.aiTasks.count { it.status == AiTaskStatus.Failed }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = latest?.let { task ->
                    val status = when (task.status) {
                        AiTaskStatus.Pending -> "等待中"
                        AiTaskStatus.Running -> "处理中"
                        AiTaskStatus.Completed -> "已完成"
                        AiTaskStatus.Failed -> "失败"
                    }
                    "$status · ${task.kind.label}"
                } ?: "暂无 AI 任务",
                color = if (enabled) colors.textSecondary else colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(
                label = "AI 行动计划",
                emphasized = true,
                enabled = enabled && state.aiSettings.enabled,
                onClick = onAiWorkspacePlan,
            )
        }
        Text(
            text = "今日 ${usage.requestCount}/${state.aiSettings.dailyRequestLimit} · 剩余 $remaining · " +
                if (state.aiSettings.backgroundAllowed) "后台可继续处理" else "后台队列关闭",
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsAiQueueChip("等待", pending)
            SettingsAiQueueChip("处理中", running)
            SettingsAiQueueChip("完成", completed)
            SettingsAiQueueChip("失败", failed)
            SettingsAiToggleChip("仅 Wi-Fi", state.aiSettings.wifiOnlyBackground, enabled = false, onCheckedChange = {})
        }
        val notices = buildList {
            if (!state.aiSettings.backgroundAllowed) add("后台任务关闭后，离开应用时队列不会继续跑")
            if (!state.aiSettings.uploadSensitiveContentAllowed) add("CW 和敏感附件默认脱敏上传")
            if (state.aiSettings.toolsAllowed) add("工具权限已开，外部回调和 Webhook 仍受自动化确认策略限制")
        }
        notices.take(2).forEach { notice ->
            Text(
                text = notice,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        latest?.resultText?.takeIf { it.isNotBlank() }?.let { result ->
            Text(
                text = result,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        latest?.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                text = error,
                color = colors.danger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsAiQueueChip(label: String, count: Int) {
    SettingsAiToggleChip(
        label = "$label $count",
        checked = count > 0,
        enabled = false,
        onCheckedChange = {},
    )
}

@Composable
private fun SettingsAiLimitsLine(
    settings: AiSettings,
    enabled: Boolean,
    onChanged: (AiSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsTextSettingLine(
            value = settings.maxInputChars.toString(),
            placeholder = "最大输入字数",
            inputLabel = "最大输入字数",
            enabled = enabled,
            onValueChange = { onChanged(settings.copy(maxInputChars = it.toIntOrNull() ?: settings.maxInputChars)) },
            helper = "一次发给模型的上下文字数上限，超出的帖子、聊天或资料内容会被截断。",
        )
        SettingsTextSettingLine(
            value = settings.maxOutputTokens.toString(),
            placeholder = "最大输出 token",
            inputLabel = "最大输出 token",
            enabled = enabled,
            onValueChange = { onChanged(settings.copy(maxOutputTokens = it.toIntOrNull() ?: settings.maxOutputTokens)) },
            helper = "限制模型单次回复长度；调大可以写得更长，也会更容易消耗模型额度。",
        )
        SettingsTextSettingLine(
            value = settings.dailyRequestLimit.toString(),
            placeholder = "每日请求上限",
            inputLabel = "每日请求上限",
            enabled = enabled,
            onValueChange = { onChanged(settings.copy(dailyRequestLimit = it.toIntOrNull() ?: settings.dailyRequestLimit)) },
            helper = "当前账号每天最多调用 AI 的次数，达到后当天不再执行新的 AI 请求。",
        )
    }
}

@Composable
private fun SettingsAiToggleChip(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    HhhlActionChip(
        label = if (checked) "$label 开" else "$label 关",
        emphasized = checked,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun SettingsChatNoiseAggregateLine(
    settings: ChatNoiseReductionSettings,
    enabled: Boolean,
    onChanged: (ChatNoiseReductionSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsSwitchLine(
            checked = settings.aggregateBurstNotifications,
            enabled = enabled,
            onCheckedChange = { onChanged(settings.copy(aggregateBurstNotifications = it)) },
            supportingText = "同一聊天室短时间多条未读会合并成一条摘要通知。",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsTextSettingLine(
                value = settings.burstWindowMinutes.toString(),
                placeholder = "分钟",
                enabled = enabled && settings.aggregateBurstNotifications,
                onValueChange = { value ->
                    onChanged(settings.copy(burstWindowMinutes = value.toIntOrNull() ?: settings.burstWindowMinutes))
                },
                modifier = Modifier.weight(1f),
                helper = "窗口",
            )
            SettingsTextSettingLine(
                value = settings.burstMessageThreshold.toString(),
                placeholder = "条数",
                enabled = enabled && settings.aggregateBurstNotifications,
                onValueChange = { value ->
                    onChanged(settings.copy(burstMessageThreshold = value.toIntOrNull() ?: settings.burstMessageThreshold))
                },
                modifier = Modifier.weight(1f),
                helper = "阈值",
            )
        }
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
    SettingsNavigationLine(
        text = text.ifBlank { "需要在网页版继续管理" },
        enabled = enabled,
        onOpen = onOpen,
        label = label,
    )
}

@Composable
private fun SettingsNavigationLine(
    text: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    label: String,
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
    supportingText: String? = null,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        supportingText?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
        SettingsItemKey.ListGestures -> Icons.Filled.Swipe
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
        SettingsItemKey.BatteryOptimization -> Icons.Filled.Security
        SettingsItemKey.ChatNoiseAggregate -> Icons.Filled.Notifications
        SettingsItemKey.ChatNoiseImportantOnly -> Icons.Filled.Tune
        SettingsItemKey.ChatNoiseAiImportance -> Icons.Filled.AutoAwesome
        SettingsItemKey.ChatNoiseKeywords -> Icons.Outlined.FilterList
        SettingsItemKey.ChatNoiseUsers -> Icons.Filled.Person
        SettingsItemKey.ChatMessageCache -> Icons.Filled.Storage
        SettingsItemKey.MuteReactions -> Icons.AutoMirrored.Outlined.VolumeOff
        SettingsItemKey.MuteFollows -> Icons.Outlined.PersonRemove
        SettingsItemKey.MutedWords -> Icons.Outlined.Block
        SettingsItemKey.HardMutedWords -> Icons.Outlined.FilterList
        SettingsItemKey.MutedInstances -> Icons.Filled.Language
        SettingsItemKey.AiFloatingAssistant -> Icons.Filled.AutoAwesome
        SettingsItemKey.AiSettingsEntry -> Icons.Filled.AutoAwesome
        SettingsItemKey.AiEnabled -> Icons.Filled.AutoAwesome
        SettingsItemKey.AiProvider -> Icons.Outlined.Api
        SettingsItemKey.AiBaseUrl -> Icons.Outlined.Link
        SettingsItemKey.AiApiKey -> Icons.Filled.Key
        SettingsItemKey.AiChatModel,
        SettingsItemKey.AiFastModel,
        SettingsItemKey.AiLongContextModel,
        SettingsItemKey.AiVisionModel,
        SettingsItemKey.AiEmbeddingModel -> Icons.Outlined.ModelTraining
        SettingsItemKey.AiReadPermissions -> Icons.Filled.Visibility
        SettingsItemKey.AiAutomation -> Icons.Filled.AutoAwesome
        SettingsItemKey.AiAssistantLowRiskAutoApproval -> Icons.Filled.AutoAwesome
        SettingsItemKey.AiAssistantHighRiskAutoApproval -> Icons.Filled.Security
        SettingsItemKey.AiBackground -> Icons.Filled.CloudQueue
        SettingsItemKey.AiQueue -> Icons.Filled.CloudQueue
        SettingsItemKey.AiLimits -> Icons.Filled.Tune
        SettingsItemKey.AiTone -> Icons.Filled.Person
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
