package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import cc.hhhl.client.navigation.RootRoute
import cc.hhhl.client.navigation.primaryRootRoutes
import cc.hhhl.client.navigation.visibleRootRoutes
import kotlin.test.Test
import kotlin.test.assertEquals

class HhhlBottomNavSpecTest {
    @Test
    fun bottomNavBadgeTextOnlyShowsPositiveCounts() {
        assertEquals(null, bottomNavBadgeText(0))
        assertEquals(null, bottomNavBadgeText(-1))
        assertEquals("7", bottomNavBadgeText(7))
    }

    @Test
    fun bottomNavBadgeTextCapsLargeCounts() {
        assertEquals("99+", bottomNavBadgeText(100))
        assertEquals("99+", bottomNavBadgeText(2048))
    }

    @Test
    fun primaryRootRoutesKeepOnlyHighestFrequencyDestinations() {
        assertEquals(
            listOf(
                RootRoute.Timeline,
                RootRoute.Discover,
                RootRoute.Chat,
                RootRoute.Notifications,
                RootRoute.Profile,
            ),
            primaryRootRoutes(),
        )
    }

    @Test
    fun visibleRootRoutesHideChatWhenInstanceDoesNotSupportIt() {
        assertEquals(primaryRootRoutes(), visibleRootRoutes(chatAvailable = true))
        assertEquals(
            listOf(
                RootRoute.Timeline,
                RootRoute.Discover,
                RootRoute.Notifications,
                RootRoute.Profile,
            ),
            visibleRootRoutes(chatAvailable = false),
        )
    }

    @Test
    fun sharedActionControlsStayCompactButTouchable() {
        assertEquals(52.dp, HhhlBottomNavHeight)
        assertEquals(22.dp, HhhlBottomNavIconSize)
        assertEquals(8.dp, HhhlControlCornerRadius)
        assertEquals(30.dp, HhhlControlMinHeight)
        assertEquals(34.dp, HhhlControlMinWidth)
        assertEquals(30.dp, HhhlActionChipMinHeight)
        assertEquals(8.dp, HhhlActionChipHorizontalPadding)
        assertEquals(5.dp, HhhlActionChipVerticalPadding)
        assertEquals(184.dp, HhhlActionChipMaxWidth)
        assertEquals(30.dp, HhhlOverflowMenuButtonHeight)
        assertEquals(34.dp, HhhlOverflowMenuButtonMinWidth)
        assertEquals(18.dp, HhhlOverflowMenuIconSize)
        assertEquals(168.dp, HhhlOverflowMenuMinWidth)
        assertEquals(240.dp, HhhlOverflowMenuMaxWidth)
        assertEquals(0.dp, HhhlOverflowMenuOffsetX)
        assertEquals(6.dp, HhhlOverflowMenuOffsetY)
    }
}
