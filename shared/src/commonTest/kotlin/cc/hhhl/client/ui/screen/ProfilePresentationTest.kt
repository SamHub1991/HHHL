package cc.hhhl.client.ui.screen

import androidx.compose.ui.unit.dp
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.repository.SettingsRepository
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
            listOf("帖子", "Drive", "设置"),
            profilePrimaryActionLabels(capabilities),
        )
        assertEquals(
            listOf("收藏", "关注请求", "关系管理"),
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
            listOf("帖子", "Drive", "设置"),
            profilePrimaryActionLabels(capabilities),
        )
        assertEquals(
            listOf("收藏", "关注请求", "关系管理"),
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
            listOf("外观", "AI", "账号与安全", "管理", "隐私", "通知", "过滤", "授权"),
            settingsGroupLabels(groups),
        )
        assertEquals(
            listOf(
                "主题",
                "高级自定义主题",
                "信息流密度",
                "列表手势",
                "AI 小光球",
                "AI 设置",
                "账号资料",
                "双重验证",
                "Passkey",
                "登录记录",
                "头像挂件",
                "管理后台",
                "默认可见范围",
                "关注需批准",
                "自动批准已关注者",
                "拒绝搜索引擎索引",
                "拒绝 AI 学习",
                "公开回应记录",
                "未读角标",
                "后台收消息与自动化",
                "特别关心后台提醒",
                "后台实时保活",
                "聊天室通知合并",
                "只提醒重点聊天",
                "AI 判断重要消息",
                "聊天提醒关键词",
                "聊天提醒用户",
                "聊天缓存",
                "静音回应通知",
                "静音关注通知",
                "词语静音",
                "强过滤词",
                "静音实例",
                "访问令牌",
                "邀请码",
                "共享访问",
                "Webhook",
                "已授权应用",
            ),
            settingsItemLabels(groups),
        )

        assertEquals(
            listOf(
                "AI 助手",
                "AI 小光球",
                "本地 Provider",
                "本地 Base URL",
                "本地 API Key",
                "对话模型",
                "快速模型",
                "长上下文模型",
                "视觉模型",
                "向量模型",
                "读取权限",
                "AI 自动化",
                "助手低风险自动批准",
                "助手高风险自动批准",
                "后台 AI 队列",
                "AI 队列",
                "用量限制",
                "语气偏好",
            ),
            settingsItemLabels(SettingsRepository().aiSettingsGroup().let(::listOf)),
        )
    }

    @Test
    fun profileSecondaryToolsAreGroupedUnderOverflowMenu() {
        assertEquals(
            listOf("刷新资料", "外观", "Flash", "公告", "退出登录"),
            profileToolActionLabels(),
        )
    }

    @Test
    fun profileQuickActionsUseCompactCardMetrics() {
        assertEquals(16.dp, ProfileQuickActionsHorizontalPadding)
        assertEquals(10.dp, ProfileQuickActionsCardInnerPadding)
        assertEquals(96.dp, ProfilePrimaryShortcutTileHeight)
        assertEquals(58.dp, ProfileWorkspaceShortcutTileHeight)
        assertEquals(14.dp, ProfileShortcutTileCornerRadius)
        assertEquals(32.dp, ProfileShortcutIconContainerSize)
        assertEquals(17.dp, ProfileShortcutIconSize)
    }
}
