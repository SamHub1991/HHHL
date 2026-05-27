package cc.hhhl.client.ui.screen

import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.model.SettingsManagementSectionKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPresentationTest {
    @Test
    fun webOnlySettingsOpenMatchingWebManagementSections() {
        assertEquals("/settings/security", settingsWebManagementPath(SettingsItemKey.TwoFactor))
        assertEquals("/settings/security", settingsWebManagementPath(SettingsItemKey.Passkeys))
        assertEquals("/settings/api", settingsWebManagementPath(SettingsItemKey.ApiTokens))
        assertEquals("/settings/webhook", settingsWebManagementPath(SettingsItemKey.Webhooks))
        assertEquals("/settings/apps", settingsWebManagementPath(SettingsItemKey.AuthorizedApps))
    }

    @Test
    fun nativeSettingsDoNotPretendToNeedWebManagement() {
        assertNull(settingsWebManagementPath(SettingsItemKey.Theme))
        assertNull(settingsWebManagementPath(SettingsItemKey.LockAccount))
        assertNull(settingsWebManagementPath(SettingsItemKey.MutedWords))
        assertNull(settingsWebManagementPath(SettingsItemKey.AdminDashboard))
    }

    @Test
    fun nativeManagementSectionsMapFromSettingsItems() {
        assertEquals(SettingsManagementSectionKey.ApiTokens, settingsManagementSectionKey(SettingsItemKey.ApiTokens))
        assertEquals(SettingsManagementSectionKey.SharedAccess, settingsManagementSectionKey(SettingsItemKey.SharedAccess))
        assertEquals(SettingsManagementSectionKey.Webhooks, settingsManagementSectionKey(SettingsItemKey.Webhooks))
        assertEquals(
            SettingsManagementSectionKey.AuthorizedApps,
            settingsManagementSectionKey(SettingsItemKey.AuthorizedApps),
        )
        assertEquals(
            SettingsManagementSectionKey.SigninHistory,
            settingsManagementSectionKey(SettingsItemKey.SigninHistory),
        )
        assertNull(settingsManagementSectionKey(SettingsItemKey.Passkeys))
    }
}
