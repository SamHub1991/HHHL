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
    fun bottomNavBadgeWidthKeepsTwoDigitsVisible() {
        assertEquals(18.dp, bottomNavBadgeMinWidth("7"))
        assertEquals(24.dp, bottomNavBadgeMinWidth("20"))
        assertEquals(30.dp, bottomNavBadgeMinWidth("99+"))
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
    fun wechatBottomNavAndSharedActionControlsMatchCurrentMetrics() {
        assertEquals(64.dp, HhhlBottomNavHeight)
        assertEquals(25.dp, HhhlBottomNavIconSize)
        assertEquals(30.dp, HhhlBottomNavIconSlotHeight)
        assertEquals(18.dp, HhhlBottomNavLabelSlotHeight)
        assertEquals(7.dp, HhhlBottomNavTopPadding)
        assertEquals(6.dp, HhhlBottomNavBottomPadding)
        assertEquals(18.dp, HhhlBottomNavBadgeHeight)
        assertEquals(18.dp, HhhlBottomNavBadgeSingleMinWidth)
        assertEquals(24.dp, HhhlBottomNavBadgeDoubleMinWidth)
        assertEquals(30.dp, HhhlBottomNavBadgeLargeMinWidth)
        assertEquals(0.36f, HhhlNotificationBadgeStrokeAlpha)
        assertEquals(13.dp, HhhlControlCornerRadius)
        assertEquals(999.dp, HhhlIconActionCornerRadius)
        assertEquals(0.dp, HhhlIconActionIdleElevation)
        assertEquals(0.dp, HhhlIconActionEmphasizedElevation)
        assertEquals(0.08f, HhhlControlHighlightAlpha)
        assertEquals(40.dp, HhhlControlMinHeight)
        assertEquals(44.dp, HhhlControlMinWidth)
        assertEquals(40.dp, HhhlActionChipMinHeight)
        assertEquals(15.dp, HhhlActionChipHorizontalPadding)
        assertEquals(6.dp, HhhlActionChipVerticalPadding)
        assertEquals(200.dp, HhhlActionChipMaxWidth)
        assertEquals(40.dp, HhhlTextButtonMinHeight)
        assertEquals(16.dp, HhhlTextButtonHorizontalPadding)
        assertEquals(8.dp, HhhlTextButtonVerticalPadding)
        assertEquals(999.dp, HhhlTextButtonCornerRadius)
        assertEquals(44.dp, HhhlOverflowMenuButtonHeight)
        assertEquals(44.dp, HhhlOverflowMenuButtonMinWidth)
        assertEquals(19.dp, HhhlOverflowMenuIconSize)
        assertEquals(184.dp, HhhlOverflowMenuMinWidth)
        assertEquals(240.dp, HhhlOverflowMenuMaxWidth)
        assertEquals(0.dp, HhhlOverflowMenuOffsetX)
        assertEquals(6.dp, HhhlOverflowMenuOffsetY)
        assertEquals(20.dp, HhhlOverflowMenuItemIconSlotWidth)
        assertEquals(18.dp, HhhlOverflowMenuItemIconSize)
    }
}
