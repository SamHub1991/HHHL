package cc.hhhl.client.state

import cc.hhhl.client.auth.AuthenticatedUser
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.theme.HhhlThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStateHolderTest {
    @Test
    fun groupsExposeCurrentLocalSettingValues() {
        val holder = SettingsStateHolder(repository = SettingsRepository())

        holder.sync(
            selectedTheme = HhhlThemePreset.Dim,
            selectedTimelineDensity = TimelineDensity.Compact,
            selectedDefaultNoteVisibility = DefaultNoteVisibility.Followers,
            selectedNotificationBadgeMode = NotificationBadgeMode.Hide,
            accountUser = AuthenticatedUser(
                id = "user-1",
                username = "alice",
                displayName = "Alice",
                avatarUrl = null,
            ),
        )

        val items = holder.state.value.groups.flatMap { it.items }

        assertEquals("暗灰", items.first { it.key == SettingsItemKey.Theme }.value)
        assertEquals("紧凑", items.first { it.key == SettingsItemKey.TimelineDensity }.value)
        assertEquals("Alice", items.first { it.key == SettingsItemKey.AccountProfile }.value)
        assertEquals("关注者", items.first { it.key == SettingsItemKey.DefaultNoteVisibility }.value)
        assertEquals("隐藏", items.first { it.key == SettingsItemKey.NotificationBadges }.value)
        assertTrue(items.all { it.enabled })
        assertFalse(items.any { it.value == null })
    }

    @Test
    fun accountFallsBackToUsernameWhenDisplayNameIsBlank() {
        val holder = SettingsStateHolder(repository = SettingsRepository())

        holder.sync(
            selectedTheme = HhhlThemePreset.System,
            selectedTimelineDensity = TimelineDensity.Comfortable,
            selectedDefaultNoteVisibility = DefaultNoteVisibility.Public,
            selectedNotificationBadgeMode = NotificationBadgeMode.Show,
            accountUser = AuthenticatedUser(
                id = "user-1",
                username = "alice",
                displayName = "",
                avatarUrl = null,
            ),
        )

        val profile = holder.state.value.groups
            .flatMap { it.items }
            .first { it.key == SettingsItemKey.AccountProfile }

        assertEquals("@alice", profile.value)
    }
}
