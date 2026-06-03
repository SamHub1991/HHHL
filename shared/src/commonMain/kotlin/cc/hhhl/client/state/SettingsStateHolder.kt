package cc.hhhl.client.state

import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiTask
import cc.hhhl.client.ai.AiUsageWindow
import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.model.FilterSettings
import cc.hhhl.client.model.SettingsManagementAction
import cc.hhhl.client.model.NotificationSettings
import cc.hhhl.client.model.PrivacySettings
import cc.hhhl.client.model.SettingsManagementSection
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.SettingsPreferences
import cc.hhhl.client.model.SettingsWebhookCreateInput
import cc.hhhl.client.model.SettingsWebhookDetail
import cc.hhhl.client.model.SettingsWebhookUpdateInput
import cc.hhhl.client.notification.ChatNoiseReductionSettings
import cc.hhhl.client.notification.toChatNoiseRules
import cc.hhhl.client.repository.SettingsManagementMutationRepositoryResult
import cc.hhhl.client.repository.SettingsManagementRepositoryResult
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.repository.SettingsRepositoryResult
import cc.hhhl.client.repository.SettingsSharedAccessLoginRepositoryResult
import cc.hhhl.client.repository.SettingsWebhookDetailRepositoryResult
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.HhhlThemePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
    val customTheme: HhhlCustomTheme = HhhlCustomTheme(),
    val selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
    val selectedDefaultNoteVisibility: DefaultNoteVisibility = DefaultNoteVisibility.Public,
    val selectedNotificationBadgeMode: NotificationBadgeMode = NotificationBadgeMode.Show,
    val aiSettings: AiSettings = AiSettings(),
    val aiTasks: List<AiTask> = emptyList(),
    val aiUsage: AiUsageWindow = AiUsageWindow(),
    val listGesturesEnabled: Boolean = true,
    val backgroundNotificationsEnabled: Boolean = false,
    val specialCareBackgroundNotificationsEnabled: Boolean = true,
    val chatNoiseReductionSettings: ChatNoiseReductionSettings = ChatNoiseReductionSettings(),
    val chatNoiseKeywordDraft: String = "",
    val chatNoiseUserDraft: String = "",
    val accountDisplayName: String = "未登录",
    val remotePreferences: SettingsPreferences? = null,
    val isRemoteLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
    val mutedWordDraft: String = "",
    val hardMutedWordDraft: String = "",
    val mutedInstanceDraft: String = "",
    val openedManagementKey: SettingsManagementSectionKey? = null,
    val managementSection: SettingsManagementSection? = null,
    val isManagementLoading: Boolean = false,
    val isManagementMutating: Boolean = false,
    val editingWebhook: SettingsWebhookDetail? = null,
    val isWebhookEditorLoading: Boolean = false,
    val managementMessage: String? = null,
    val groups: List<SettingsGroup> = SettingsRepository.defaultGroups(),
)

data class SettingsGroup(
    val key: SettingsGroupKey,
    val label: String,
    val items: List<SettingsItem>,
)

data class SettingsItem(
    val key: SettingsItemKey,
    val label: String,
    val value: String? = null,
    val icon: String = "•",
    val enabled: Boolean = true,
)

enum class SettingsGroupKey {
    Appearance,
    AccountSecurity,
    Management,
    Privacy,
    Notifications,
    Filters,
    Ai,
    Integrations,
}

enum class SettingsItemKey {
    Theme,
    TimelineDensity,
    ListGestures,
    AdvancedTheme,
    AccountProfile,
    AdminDashboard,
    TwoFactor,
    Passkeys,
    SigninHistory,
    AvatarDecorations,
    DefaultNoteVisibility,
    LockAccount,
    AutoAcceptFollowed,
    NoCrawle,
    PreventAiLearning,
    PublicReactions,
    NotificationBadges,
    BackgroundNotifications,
    SpecialCareBackgroundNotifications,
    BatteryOptimization,
    ChatNoiseAggregate,
    ChatNoiseImportantOnly,
    ChatNoiseAiImportance,
    ChatNoiseKeywords,
    ChatNoiseUsers,
    ChatMessageCache,
    MuteReactions,
    MuteFollows,
    MutedWords,
    HardMutedWords,
    MutedInstances,
    AiFloatingAssistant,
    AiSettingsEntry,
    AiEnabled,
    AiProvider,
    AiBaseUrl,
    AiApiKey,
    AiChatModel,
    AiFastModel,
    AiLongContextModel,
    AiVisionModel,
    AiEmbeddingModel,
    AiReadPermissions,
    AiAutomation,
    AiAssistantLowRiskAutoApproval,
    AiAssistantHighRiskAutoApproval,
    AiBackground,
    AiQueue,
    AiLimits,
    AiTone,
    ApiTokens,
    Invites,
    SharedAccess,
    Webhooks,
    AuthorizedApps,
}

class SettingsStateHolder(
    private val repository: SettingsRepository = SettingsRepository(),
    private val scope: CoroutineScope? = null,
    private val onSharedAccessLogin: (token: String, userId: String) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow(SettingsUiState(groups = repository.groups()))
    val state: StateFlow<SettingsUiState> = mutableState
    private var remoteRequestId = 0
    private var managementRequestId = 0
    private var webhookRequestId = 0

    fun sync(
        selectedTheme: HhhlThemePreset,
        customTheme: HhhlCustomTheme = HhhlCustomTheme(),
        selectedTimelineDensity: TimelineDensity,
        selectedDefaultNoteVisibility: DefaultNoteVisibility,
        selectedNotificationBadgeMode: NotificationBadgeMode,
        aiSettings: AiSettings = AiSettings(),
        aiTasks: List<AiTask> = emptyList(),
        aiUsage: AiUsageWindow = AiUsageWindow(),
        listGesturesEnabled: Boolean = true,
        backgroundNotificationsEnabled: Boolean = false,
        specialCareBackgroundNotificationsEnabled: Boolean = true,
        chatNoiseReductionSettings: ChatNoiseReductionSettings = ChatNoiseReductionSettings(),
        accountUser: AuthenticatedUser?,
    ) {
        val accountDisplayName = accountUser?.let { user ->
            user.displayName.ifBlank { "@${user.username}" }
        } ?: "未登录"

        mutableState.update {
            val next = it.copy(
                selectedTheme = selectedTheme,
                customTheme = customTheme,
                selectedTimelineDensity = selectedTimelineDensity,
                listGesturesEnabled = listGesturesEnabled,
                selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                aiSettings = aiSettings,
                aiTasks = aiTasks,
                aiUsage = aiUsage,
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                chatNoiseReductionSettings = chatNoiseReductionSettings.normalized,
                accountDisplayName = accountDisplayName,
            )
            next.withGroups()
        }
    }

    fun refreshRemote() {
        val launchScope = scope ?: return
        if (state.value.isRemoteLoading) return
        val requestId = ++remoteRequestId

        mutableState.update {
            it.copy(
                isRemoteLoading = true,
                errorMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            when (val result = repository.loadRemotePreferences()) {
                is SettingsRepositoryResult.Success -> mutableState.update {
                    if (requestId != remoteRequestId) return@update it
                    it.copy(
                        remotePreferences = result.preferences,
                        isRemoteLoading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != remoteRequestId) return@update it
                    it.copy(
                        isRemoteLoading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsRepositoryResult.Error -> mutableState.update {
                    if (requestId != remoteRequestId) return@update it
                    it.copy(
                        isRemoteLoading = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    fun updateMutedWordDraft(value: String) {
        mutableState.update { it.copy(mutedWordDraft = value) }
    }

    fun updateHardMutedWordDraft(value: String) {
        mutableState.update { it.copy(hardMutedWordDraft = value) }
    }

    fun updateMutedInstanceDraft(value: String) {
        mutableState.update { it.copy(mutedInstanceDraft = value) }
    }

    fun updateChatNoiseKeywordDraft(value: String) {
        mutableState.update { it.copy(chatNoiseKeywordDraft = value.take(240)) }
    }

    fun updateChatNoiseUserDraft(value: String) {
        mutableState.update { it.copy(chatNoiseUserDraft = value.take(240)) }
    }

    fun addChatNoiseKeyword() {
        mutableState.update { current ->
            val nextRules = (current.chatNoiseReductionSettings.keywordRules + current.chatNoiseKeywordDraft.toChatNoiseRules(
                maxRules = 40,
                maxLength = 80,
            )).distinctBy { it.lowercase() }
            current.copy(
                chatNoiseReductionSettings = current.chatNoiseReductionSettings.copy(keywordRules = nextRules).normalized,
                chatNoiseKeywordDraft = "",
            ).withGroups()
        }
    }

    fun removeChatNoiseKeyword(value: String) {
        mutableState.update { current ->
            current.copy(
                chatNoiseReductionSettings = current.chatNoiseReductionSettings.copy(
                    keywordRules = current.chatNoiseReductionSettings.keywordRules.filterNot { it.equals(value, ignoreCase = true) },
                ).normalized,
            ).withGroups()
        }
    }

    fun addChatNoiseUser() {
        mutableState.update { current ->
            val nextRules = (current.chatNoiseReductionSettings.userRules + current.chatNoiseUserDraft.toChatNoiseRules())
                .distinctBy { it.lowercase() }
            current.copy(
                chatNoiseReductionSettings = current.chatNoiseReductionSettings.copy(userRules = nextRules).normalized,
                chatNoiseUserDraft = "",
            ).withGroups()
        }
    }

    fun removeChatNoiseUser(value: String) {
        mutableState.update { current ->
            current.copy(
                chatNoiseReductionSettings = current.chatNoiseReductionSettings.copy(
                    userRules = current.chatNoiseReductionSettings.userRules.filterNot { it.equals(value, ignoreCase = true) },
                ).normalized,
            ).withGroups()
        }
    }

    fun updateChatNoiseSettings(settings: ChatNoiseReductionSettings) {
        mutableState.update { it.copy(chatNoiseReductionSettings = settings.normalized).withGroups() }
    }

    fun togglePrivacy(key: SettingsItemKey, enabled: Boolean) {
        val current = state.value.remotePreferences ?: return
        val privacy = current.privacy
        val next = when (key) {
            SettingsItemKey.LockAccount -> privacy.copy(isLocked = enabled)
            SettingsItemKey.AutoAcceptFollowed -> privacy.copy(autoAcceptFollowed = enabled)
            SettingsItemKey.NoCrawle -> privacy.copy(noCrawle = enabled)
            SettingsItemKey.PreventAiLearning -> privacy.copy(preventAiLearning = enabled)
            SettingsItemKey.PublicReactions -> privacy.copy(publicReactions = enabled)
            else -> return
        }
        mutate { repository.updatePrivacy(next) }
    }

    fun toggleNotificationType(type: String, muted: Boolean) {
        val current = state.value.remotePreferences ?: return
        val types = if (muted) {
            (current.notifications.mutedTypes + type).distinct()
        } else {
            current.notifications.mutedTypes.filterNot { it == type }
        }
        mutate { repository.updateNotifications(NotificationSettings(mutedTypes = types)) }
    }

    fun addMutedWord() {
        val phrase = state.value.mutedWordDraft.trim()
        if (phrase.isBlank()) return
        updateFilters { it.copy(mutedWords = (it.mutedWords + phrase).distinct()) }
        mutableState.update { it.copy(mutedWordDraft = "") }
    }

    fun removeMutedWord(phrase: String) {
        updateFilters { it.copy(mutedWords = it.mutedWords.filterNot { word -> word == phrase }) }
    }

    fun addHardMutedWord() {
        val phrase = state.value.hardMutedWordDraft.trim()
        if (phrase.isBlank()) return
        updateFilters { it.copy(hardMutedWords = (it.hardMutedWords + phrase).distinct()) }
        mutableState.update { it.copy(hardMutedWordDraft = "") }
    }

    fun removeHardMutedWord(phrase: String) {
        updateFilters { it.copy(hardMutedWords = it.hardMutedWords.filterNot { word -> word == phrase }) }
    }

    fun addMutedInstance() {
        val host = state.value.mutedInstanceDraft.trim()
        if (host.isBlank()) return
        updateFilters { it.copy(mutedInstances = (it.mutedInstances + host).distinct()) }
        mutableState.update { it.copy(mutedInstanceDraft = "") }
    }

    fun removeMutedInstance(host: String) {
        updateFilters { it.copy(mutedInstances = it.mutedInstances.filterNot { instance -> instance == host }) }
    }

    fun openManagement(
        key: SettingsManagementSectionKey,
        preserveMessage: String? = null,
    ) {
        val launchScope = scope ?: return
        val requestId = ++managementRequestId
        webhookRequestId += 1
        mutableState.update {
            it.copy(
                openedManagementKey = key,
                managementSection = null,
                isManagementLoading = true,
                isManagementMutating = false,
                managementMessage = preserveMessage,
                errorMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            when (val result = repository.loadManagementSection(key)) {
                is SettingsManagementRepositoryResult.Success -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        openedManagementKey = key,
                        managementSection = result.section,
                        isManagementLoading = false,
                        managementMessage = preserveMessage,
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsManagementRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementLoading = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementRepositoryResult.Error -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementLoading = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    fun closeManagement() {
        managementRequestId += 1
        webhookRequestId += 1
        mutableState.update {
            it.copy(
                openedManagementKey = null,
                managementSection = null,
                isManagementLoading = false,
                isManagementMutating = false,
                editingWebhook = null,
                isWebhookEditorLoading = false,
                managementMessage = null,
            ).withGroups()
        }
    }

    fun refreshManagement() {
        state.value.openedManagementKey?.let(::openManagement)
    }

    fun openWebhookEditor(webhookId: String) {
        val launchScope = scope ?: return
        if (state.value.openedManagementKey != SettingsManagementSectionKey.Webhooks) return
        if (state.value.isWebhookEditorLoading || state.value.isManagementMutating) return
        val requestId = ++webhookRequestId

        mutableState.update {
            it.copy(
                editingWebhook = null,
                isWebhookEditorLoading = true,
                managementMessage = "正在加载 Webhook 详情...",
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            when (val result = repository.loadWebhook(webhookId)) {
                is SettingsWebhookDetailRepositoryResult.Success -> mutableState.update {
                    if (
                        requestId != webhookRequestId ||
                        it.openedManagementKey != SettingsManagementSectionKey.Webhooks
                    ) return@update it
                    it.copy(
                        editingWebhook = result.webhook,
                        isWebhookEditorLoading = false,
                        managementMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsWebhookDetailRepositoryResult.Unauthorized -> mutableState.update {
                    if (
                        requestId != webhookRequestId ||
                        it.openedManagementKey != SettingsManagementSectionKey.Webhooks
                    ) return@update it
                    it.copy(
                        editingWebhook = null,
                        isWebhookEditorLoading = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsWebhookDetailRepositoryResult.Error -> mutableState.update {
                    if (
                        requestId != webhookRequestId ||
                        it.openedManagementKey != SettingsManagementSectionKey.Webhooks
                    ) return@update it
                    it.copy(
                        editingWebhook = null,
                        isWebhookEditorLoading = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    fun closeWebhookEditor() {
        webhookRequestId += 1
        mutableState.update {
            it.copy(
                editingWebhook = null,
                isWebhookEditorLoading = false,
            ).withGroups()
        }
    }

    fun performManagementAction(
        action: SettingsManagementAction,
        itemId: String,
    ) {
        val launchScope = scope ?: return
        val key = state.value.openedManagementKey ?: return
        if (state.value.isManagementMutating) return
        val requestId = managementRequestId

        mutableState.update {
            it.copy(
                isManagementMutating = true,
                editingWebhook = null,
                managementMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            if (action == SettingsManagementAction.LoginSharedAccess) {
                when (val result = repository.loginSharedAccess(itemId)) {
                    is SettingsSharedAccessLoginRepositoryResult.Success -> {
                        if (!isCurrentManagementRequest(requestId, key)) return@launch
                        onSharedAccessLogin(result.token, result.userId)
                        val successMessage = "共享访问已导入，正在切换账号"
                        mutableState.update {
                            if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                            it.copy(
                                isManagementMutating = false,
                                managementMessage = successMessage,
                                requiresRelogin = false,
                            ).withGroups()
                        }
                        openManagement(key, preserveMessage = successMessage)
                    }
                    SettingsSharedAccessLoginRepositoryResult.Unauthorized -> mutableState.update {
                        if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = "登录已失效，请重新登录",
                            requiresRelogin = true,
                        ).withGroups()
                    }
                    is SettingsSharedAccessLoginRepositoryResult.Error -> mutableState.update {
                        if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = result.message,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                }
                return@launch
            }

            val result = when (action) {
                SettingsManagementAction.RevokeToken -> repository.revokeApiToken(itemId)
                SettingsManagementAction.CreateInvite -> repository.createInvite()
                SettingsManagementAction.DeleteInvite -> repository.deleteInvite(itemId)
                SettingsManagementAction.LoginSharedAccess -> SettingsManagementMutationRepositoryResult.Error("共享访问登录未执行")
                SettingsManagementAction.EditWebhook -> SettingsManagementMutationRepositoryResult.Error("请在编辑表单中保存 Webhook")
                SettingsManagementAction.EnableWebhook -> repository.updateWebhookActive(itemId, true)
                SettingsManagementAction.DisableWebhook -> repository.updateWebhookActive(itemId, false)
                SettingsManagementAction.TestWebhook -> repository.testWebhook(itemId)
                SettingsManagementAction.DeleteWebhook -> repository.deleteWebhook(itemId)
            }
            when (result) {
                SettingsManagementMutationRepositoryResult.Success -> {
                    if (!isCurrentManagementRequest(requestId, key)) return@launch
                    val successMessage = when (action) {
                        SettingsManagementAction.RevokeToken -> "操作已完成"
                        SettingsManagementAction.CreateInvite -> "邀请码已创建"
                        SettingsManagementAction.DeleteInvite -> "邀请码已删除"
                        SettingsManagementAction.LoginSharedAccess -> "请通过授权登录管理共享访问"
                        SettingsManagementAction.EditWebhook -> "Webhook 已更新"
                        SettingsManagementAction.EnableWebhook -> "Webhook 已启用"
                        SettingsManagementAction.DisableWebhook -> "Webhook 已停用"
                        SettingsManagementAction.TestWebhook -> "Webhook 测试已发送"
                        SettingsManagementAction.DeleteWebhook -> "Webhook 已删除"
                    }
                    mutableState.update {
                        if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    fun updateWebhook(
        webhookId: String,
        input: SettingsWebhookUpdateInput,
    ) {
        val launchScope = scope ?: return
        val key = state.value.openedManagementKey ?: return
        if (key != SettingsManagementSectionKey.Webhooks || state.value.isManagementMutating) return
        val requestId = managementRequestId

        mutableState.update {
            it.copy(
                isManagementMutating = true,
                editingWebhook = null,
                isWebhookEditorLoading = false,
                managementMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            when (val result = repository.updateWebhook(webhookId, input)) {
                SettingsManagementMutationRepositoryResult.Success -> {
                    if (!isCurrentManagementRequest(requestId, key)) return@launch
                    val successMessage = "Webhook 已更新"
                    mutableState.update {
                        if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    fun createWebhook(input: SettingsWebhookCreateInput) {
        val launchScope = scope ?: return
        val key = state.value.openedManagementKey ?: return
        if (key != SettingsManagementSectionKey.Webhooks || state.value.isManagementMutating) return
        val requestId = managementRequestId

        mutableState.update {
            it.copy(
                isManagementMutating = true,
                editingWebhook = null,
                isWebhookEditorLoading = false,
                managementMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            when (val result = repository.createWebhook(input)) {
                SettingsManagementMutationRepositoryResult.Success -> {
                    if (!isCurrentManagementRequest(requestId, key)) return@launch
                    val successMessage = "Webhook 已创建"
                    mutableState.update {
                        if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
                    if (requestId != managementRequestId || it.openedManagementKey != key) return@update it
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    private fun isCurrentManagementRequest(
        requestId: Int,
        key: SettingsManagementSectionKey,
    ): Boolean {
        val current = state.value
        return requestId == managementRequestId && current.openedManagementKey == key
    }

    private fun updateFilters(transform: (FilterSettings) -> FilterSettings) {
        val current = state.value.remotePreferences ?: return
        mutate { repository.updateFilters(transform(current.filters)) }
    }

    private fun mutate(action: suspend () -> SettingsRepositoryResult) {
        val launchScope = scope ?: return
        if (state.value.isMutating) return

        mutableState.update {
            it.copy(isMutating = true, errorMessage = null, requiresRelogin = false).withGroups()
        }

        launchScope.launch {
            when (val result = action()) {
                is SettingsRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        remotePreferences = result.preferences,
                        isMutating = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isMutating = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isMutating = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
    }

    private fun SettingsUiState.withGroups(): SettingsUiState {
        return copy(
            groups = repository.groups(
                selectedTheme = selectedTheme,
                customTheme = customTheme,
                selectedTimelineDensity = selectedTimelineDensity,
                selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                aiSettings = aiSettings,
                aiTasks = aiTasks,
                aiUsage = aiUsage,
                listGesturesEnabled = listGesturesEnabled,
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                chatNoiseReductionSettings = chatNoiseReductionSettings,
                accountDisplayName = accountDisplayName,
                remotePreferences = remotePreferences,
                isRemoteLoading = isRemoteLoading,
            ),
        )
    }
}
