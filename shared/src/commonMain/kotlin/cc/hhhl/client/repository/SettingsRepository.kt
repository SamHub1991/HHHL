package cc.hhhl.client.repository

import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsGroupKey
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.theme.HhhlThemePreset

class SettingsRepository {
    fun groups(
        selectedTheme: HhhlThemePreset = HhhlThemePreset.System,
        selectedTimelineDensity: TimelineDensity = TimelineDensity.Comfortable,
        selectedDefaultNoteVisibility: DefaultNoteVisibility = DefaultNoteVisibility.Public,
        selectedNotificationBadgeMode: NotificationBadgeMode = NotificationBadgeMode.Show,
        accountDisplayName: String = "未登录",
    ): List<SettingsGroup> {
        return listOf(
            SettingsGroup(
                key = SettingsGroupKey.Appearance,
                label = "外观",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.Theme,
                        label = "主题",
                        value = selectedTheme.label,
                        icon = "色",
                    ),
                    SettingsItem(
                        key = SettingsItemKey.TimelineDensity,
                        label = "信息流密度",
                        value = selectedTimelineDensity.label,
                        icon = "密",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Account,
                label = "账号",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.AccountProfile,
                        label = "账号资料",
                        value = accountDisplayName,
                        icon = "我",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Privacy,
                label = "隐私",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.DefaultNoteVisibility,
                        label = "默认可见范围",
                        value = selectedDefaultNoteVisibility.label,
                        icon = "权",
                    ),
                ),
            ),
            SettingsGroup(
                key = SettingsGroupKey.Notifications,
                label = "通知",
                items = listOf(
                    SettingsItem(
                        key = SettingsItemKey.NotificationBadges,
                        label = "未读角标",
                        value = selectedNotificationBadgeMode.label,
                        icon = "铃",
                    ),
                ),
            ),
        )
    }

    companion object {
        fun defaultGroups(): List<SettingsGroup> = SettingsRepository().groups()
    }
}
