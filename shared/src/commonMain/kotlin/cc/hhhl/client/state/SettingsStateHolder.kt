package cc.hhhl.client.state

import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.theme.HhhlThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
    val selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
    val selectedDefaultNoteVisibility: DefaultNoteVisibility = DefaultNoteVisibility.Public,
    val selectedNotificationBadgeMode: NotificationBadgeMode = NotificationBadgeMode.Show,
    val accountDisplayName: String = "未登录",
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
    Account,
    Privacy,
    Notifications,
}

enum class SettingsItemKey {
    Theme,
    TimelineDensity,
    AccountProfile,
    DefaultNoteVisibility,
    NotificationBadges,
}

class SettingsStateHolder(
    private val repository: SettingsRepository = SettingsRepository(),
) {
    private val mutableState = MutableStateFlow(SettingsUiState(groups = repository.groups()))
    val state: StateFlow<SettingsUiState> = mutableState

    fun sync(
        selectedTheme: HhhlThemePreset,
        selectedTimelineDensity: TimelineDensity,
        selectedDefaultNoteVisibility: DefaultNoteVisibility,
        selectedNotificationBadgeMode: NotificationBadgeMode,
        accountUser: AuthenticatedUser?,
    ) {
        val accountDisplayName = accountUser?.let { user ->
            user.displayName.ifBlank { "@${user.username}" }
        } ?: "未登录"

        mutableState.update {
            it.copy(
                selectedTheme = selectedTheme,
                selectedTimelineDensity = selectedTimelineDensity,
                selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                accountDisplayName = accountDisplayName,
                groups = repository.groups(
                    selectedTheme = selectedTheme,
                    selectedTimelineDensity = selectedTimelineDensity,
                    selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                    selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                    accountDisplayName = accountDisplayName,
                ),
            )
        }
    }
}
