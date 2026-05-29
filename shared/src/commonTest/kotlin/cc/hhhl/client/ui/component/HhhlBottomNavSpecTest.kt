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
    fun sharedActionControlsStayCompactButTouchable() {
        assertEquals(56.dp, HhhlBottomNavHeight)
        assertEquals(44.dp, HhhlBottomNavPanelHeight)
        assertEquals(12.dp, HhhlBottomNavPanelHorizontalPadding)
        assertEquals(24.dp, HhhlBottomNavPanelCornerRadius)
        assertEquals(10.dp, HhhlBottomNavPanelElevation)
        assertEquals(0.18f, HhhlBottomNavPanelHighlightAlpha)
        assertEquals(20.dp, HhhlBottomNavIconSize)
        assertEquals(60.dp, HhhlBottomNavIconSlotWidth)
        assertEquals(27.dp, HhhlBottomNavIconSlotHeight)
        assertEquals(14.dp, HhhlBottomNavLabelSlotHeight)
        assertEquals(6.dp, HhhlBottomNavIconOffsetIdle)
        assertEquals(0.dp, HhhlBottomNavIconOffsetActive)
        assertEquals(40.dp, HhhlBottomNavIdlePillWidth)
        assertEquals(54.dp, HhhlBottomNavSelectedPillWidth)
        assertEquals(25.dp, HhhlBottomNavSelectedPillHeight)
        assertEquals(14.dp, HhhlBottomNavSelectedPillCornerRadius)
        assertEquals(6.dp, HhhlBottomNavVerticalPadding)
        assertEquals(18.dp, HhhlBottomNavBadgeHeight)
        assertEquals(18.dp, HhhlBottomNavBadgeSingleMinWidth)
        assertEquals(24.dp, HhhlBottomNavBadgeDoubleMinWidth)
        assertEquals(30.dp, HhhlBottomNavBadgeLargeMinWidth)
        assertEquals(11.dp, HhhlControlCornerRadius)
        assertEquals(13.dp, HhhlIconActionCornerRadius)
        assertEquals(0.dp, HhhlIconActionIdleElevation)
        assertEquals(0.dp, HhhlIconActionEmphasizedElevation)
        assertEquals(0.08f, HhhlControlHighlightAlpha)
        assertEquals(30.dp, HhhlControlMinHeight)
        assertEquals(34.dp, HhhlControlMinWidth)
        assertEquals(30.dp, HhhlActionChipMinHeight)
        assertEquals(11.dp, HhhlActionChipHorizontalPadding)
        assertEquals(4.dp, HhhlActionChipVerticalPadding)
        assertEquals(184.dp, HhhlActionChipMaxWidth)
        assertEquals(32.dp, HhhlTextButtonMinHeight)
        assertEquals(12.dp, HhhlTextButtonHorizontalPadding)
        assertEquals(6.dp, HhhlTextButtonVerticalPadding)
        assertEquals(13.dp, HhhlTextButtonCornerRadius)
        assertEquals(30.dp, HhhlOverflowMenuButtonHeight)
        assertEquals(34.dp, HhhlOverflowMenuButtonMinWidth)
        assertEquals(19.dp, HhhlOverflowMenuIconSize)
        assertEquals(184.dp, HhhlOverflowMenuMinWidth)
        assertEquals(240.dp, HhhlOverflowMenuMaxWidth)
        assertEquals(0.dp, HhhlOverflowMenuOffsetX)
        assertEquals(6.dp, HhhlOverflowMenuOffsetY)
        assertEquals(20.dp, HhhlOverflowMenuItemIconSlotWidth)
        assertEquals(18.dp, HhhlOverflowMenuItemIconSize)
    }
}
