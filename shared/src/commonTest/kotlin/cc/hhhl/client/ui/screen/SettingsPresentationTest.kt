package cc.hhhl.client.ui.screen

import androidx.compose.ui.unit.dp
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

    @Test
    fun settingsControlsUseCompactMetrics() {
        assertEquals(11.dp, SettingsRowVerticalPadding)
        assertEquals(12.dp, SettingsRowContentSpacing)
        assertEquals(6.dp, SettingsRowDetailSpacing)
        assertEquals(40.dp, SettingsCompactInputMinHeight)
        assertEquals(12.dp, SettingsCompactInputHorizontalPadding)
        assertEquals(8.dp, SettingsCompactInputVerticalPadding)
        assertEquals(34.dp, SettingsCompactIconButtonSize)
        assertEquals(18.dp, SettingsCompactIconSize)
        assertEquals(12.dp, SettingsCompactPanelHorizontalPadding)
        assertEquals(10.dp, SettingsCompactPanelVerticalPadding)
        assertEquals(32.dp, SettingsItemIconSize)
        assertEquals(17.dp, SettingsItemIconInnerSize)
        assertEquals(0.82f, SettingsSwitchScale)
    }
}
