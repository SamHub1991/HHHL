package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.UserSocialKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserSocialPresentationTest {
    @Test
    fun userSocialKeepsFollowingAndFollowersVisible() {
        assertEquals(
            listOf(
                UserSocialKind.Following,
                UserSocialKind.Followers,
            ),
            userSocialKinds(),
        )
    }

    @Test
    fun specialCareOverflowActionUsesToggleStateCopy() {
        assertEquals("设为特别关心", userSocialSpecialCareActionLabel(isSpecialCare = false))
        assertEquals("取消特别关心", userSocialSpecialCareActionLabel(isSpecialCare = true))
    }

    @Test
    fun rowOverflowIncludesSpecialCareActionWhenHandlerIsAvailable() {
        val actions = userSocialRowActions(
            kind = UserSocialKind.Followers,
            onOpenUser = {},
            onUnfollow = null,
            onMute = null,
            onBlock = null,
            onReport = null,
            isSpecialCare = false,
            onToggleSpecialCare = {},
        )

        assertEquals(listOf("查看资料", "设为特别关心"), actions.map { it.label })
        assertTrue(actions.all { it.enabled })
    }

    @Test
    fun rowOverflowNamesSpecialCareRemovalWhenUserIsAlreadySpecial() {
        val actions = userSocialRowActions(
            kind = UserSocialKind.Following,
            onOpenUser = {},
            onUnfollow = {},
            onMute = {},
            onBlock = {},
            onReport = {},
            isSpecialCare = true,
            onToggleSpecialCare = {},
        )

        assertEquals(
            listOf("查看资料", "取消特别关心", "取消关注", "静音", "屏蔽", "举报"),
            actions.map { it.label },
        )
    }
}
