package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.repository.SettingsRepository
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.theme.HhhlThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfilePresentationTest {
    @Test
    fun profileActionsRespectCapabilityLimits() {
        val capabilities = InstanceCapabilities(
            clipLimit = 10,
            antennaLimit = 5,
            userListLimit = 10,
        )

        assertEquals(
            listOf("刷新资料", "Drive", "设置"),
            profilePrimaryActionLabels(capabilities),
        )
        assertEquals(
            listOf("收藏", "请求"),
            profileAccountActionLabels(),
        )
        assertEquals(
            listOf("列表 10", "剪辑 10", "天线 5", "频道", "页面", "图库"),
            profileWorkspaceActionLabels(capabilities),
        )
    }

    @Test
    fun profileActionsHideUnavailableCollectionFeatures() {
        val capabilities = InstanceCapabilities(
            clipLimit = 0,
            antennaLimit = 0,
            userListLimit = 0,
        )

        assertEquals(
            listOf("刷新资料", "Drive", "设置"),
            profilePrimaryActionLabels(capabilities),
        )
        assertEquals(
            listOf("收藏", "请求"),
            profileAccountActionLabels(),
        )
        assertEquals(
            listOf("频道", "页面", "图库"),
            profileWorkspaceActionLabels(capabilities),
        )
    }

    @Test
    fun settingsExposeAppearanceControlsInDedicatedScreen() {
        val groups = SettingsRepository().groups(
            selectedTheme = HhhlThemePreset.Dark,
            selectedTimelineDensity = TimelineDensity.Compact,
        )

        assertEquals(
            listOf("外观", "账号", "隐私", "通知"),
            settingsGroupLabels(groups),
        )
        assertEquals(
            listOf("主题", "信息流密度", "账号资料", "默认可见范围", "未读角标"),
            settingsItemLabels(groups),
        )
    }

    @Test
    fun profileSecondaryToolsAreGroupedUnderOverflowMenu() {
        assertEquals(
            listOf("外观", "Play", "公告", "关系管理", "退出登录"),
            profileToolActionLabels(),
        )
    }
}
