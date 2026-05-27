package cc.hhhl.client.state

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
import cc.hhhl.client.repository.SettingsManagementMutationRepositoryResult
import cc.hhhl.client.repository.SettingsManagementRepositoryResult
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.repository.SettingsRepositoryResult
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
    val backgroundNotificationsEnabled: Boolean = false,
    val specialCareBackgroundNotificationsEnabled: Boolean = true,
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
    Integrations,
}

enum class SettingsItemKey {
    Theme,
    TimelineDensity,
    AdvancedTheme,
    AccountProfile,
    AdminDashboard,
    TwoFactor,
    Passkeys,
    SigninHistory,
    DefaultNoteVisibility,
    LockAccount,
    AutoAcceptFollowed,
    NoCrawle,
    PreventAiLearning,
    PublicReactions,
    NotificationBadges,
    BackgroundNotifications,
    SpecialCareBackgroundNotifications,
    ChatMessageCache,
    MuteReactions,
    MuteFollows,
    MutedWords,
    HardMutedWords,
    MutedInstances,
    ApiTokens,
    SharedAccess,
    Webhooks,
    AuthorizedApps,
}

class SettingsStateHolder(
    private val repository: SettingsRepository = SettingsRepository(),
    private val scope: CoroutineScope? = null,
) {
    private val mutableState = MutableStateFlow(SettingsUiState(groups = repository.groups()))
    val state: StateFlow<SettingsUiState> = mutableState

    fun sync(
        selectedTheme: HhhlThemePreset,
        customTheme: HhhlCustomTheme = HhhlCustomTheme(),
        selectedTimelineDensity: TimelineDensity,
        selectedDefaultNoteVisibility: DefaultNoteVisibility,
        selectedNotificationBadgeMode: NotificationBadgeMode,
        backgroundNotificationsEnabled: Boolean = false,
        specialCareBackgroundNotificationsEnabled: Boolean = true,
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
                selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                accountDisplayName = accountDisplayName,
            )
            next.withGroups()
        }
    }

    fun refreshRemote() {
        val launchScope = scope ?: return
        if (state.value.isRemoteLoading) return

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
                    it.copy(
                        remotePreferences = result.preferences,
                        isRemoteLoading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isRemoteLoading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsRepositoryResult.Error -> mutableState.update {
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
                    it.copy(
                        isManagementLoading = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementRepositoryResult.Error -> mutableState.update {
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
                    it.copy(
                        editingWebhook = result.webhook,
                        isWebhookEditorLoading = false,
                        managementMessage = null,
                        requiresRelogin = false,
                    ).withGroups()
                }
                SettingsWebhookDetailRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        editingWebhook = null,
                        isWebhookEditorLoading = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsWebhookDetailRepositoryResult.Error -> mutableState.update {
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

        mutableState.update {
            it.copy(
                isManagementMutating = true,
                editingWebhook = null,
                managementMessage = null,
                requiresRelogin = false,
            ).withGroups()
        }

        launchScope.launch {
            val result = when (action) {
                SettingsManagementAction.RevokeToken -> repository.revokeApiToken(itemId)
                SettingsManagementAction.EditWebhook -> SettingsManagementMutationRepositoryResult.Error("请在编辑表单中保存 Webhook")
                SettingsManagementAction.EnableWebhook -> repository.updateWebhookActive(itemId, true)
                SettingsManagementAction.DisableWebhook -> repository.updateWebhookActive(itemId, false)
                SettingsManagementAction.TestWebhook -> repository.testWebhook(itemId)
                SettingsManagementAction.DeleteWebhook -> repository.deleteWebhook(itemId)
            }
            when (result) {
                SettingsManagementMutationRepositoryResult.Success -> {
                    val successMessage = when (action) {
                        SettingsManagementAction.RevokeToken -> "操作已完成"
                        SettingsManagementAction.EditWebhook -> "Webhook 已更新"
                        SettingsManagementAction.EnableWebhook -> "Webhook 已启用"
                        SettingsManagementAction.DisableWebhook -> "Webhook 已停用"
                        SettingsManagementAction.TestWebhook -> "Webhook 测试已发送"
                        SettingsManagementAction.DeleteWebhook -> "Webhook 已删除"
                    }
                    mutableState.update {
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
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
                    val successMessage = "Webhook 已更新"
                    mutableState.update {
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
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
                    val successMessage = "Webhook 已创建"
                    mutableState.update {
                        it.copy(
                            isManagementMutating = false,
                            managementMessage = successMessage,
                            requiresRelogin = false,
                        ).withGroups()
                    }
                    openManagement(key, preserveMessage = successMessage)
                }
                SettingsManagementMutationRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    ).withGroups()
                }
                is SettingsManagementMutationRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isManagementMutating = false,
                        managementMessage = result.message,
                        requiresRelogin = false,
                    ).withGroups()
                }
            }
        }
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
                backgroundNotificationsEnabled = backgroundNotificationsEnabled,
                specialCareBackgroundNotificationsEnabled = specialCareBackgroundNotificationsEnabled,
                accountDisplayName = accountDisplayName,
                remotePreferences = remotePreferences,
                isRemoteLoading = isRemoteLoading,
            ),
        )
    }
}
