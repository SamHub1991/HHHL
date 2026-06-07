package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hhhl.client.navigation.RootRoute
import cc.hhhl.client.navigation.primaryRootRoutes
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlBottomNavHeight = 64.dp
internal val HhhlBottomNavIconSize = 25.dp
internal val HhhlBottomNavIconSlotHeight = 30.dp
internal val HhhlBottomNavLabelSlotHeight = 18.dp
internal val HhhlBottomNavTopPadding = 7.dp
internal val HhhlBottomNavBottomPadding = 6.dp
internal val HhhlBottomNavBadgeHeight = 18.dp
internal val HhhlBottomNavBadgeSingleMinWidth = 18.dp
internal val HhhlBottomNavBadgeDoubleMinWidth = 24.dp
internal val HhhlBottomNavBadgeLargeMinWidth = 30.dp
internal val HhhlNotificationBadgeStrokeAlpha = 0.36f
private val HhhlBottomNavWechatGreen = Color(0xFF07C160)
private val HhhlBottomNavDarkBackground = Color(0xFF111111)
private val HhhlBottomNavLightBackground = Color(0xFFFFFFFF)
private val HhhlBottomNavDarkDivider = Color(0xFF242424)
private val HhhlBottomNavLightDivider = Color(0xFFE5E5E5)
private val HhhlBottomNavDarkInactiveIcon = Color(0xFFB8B8B8)
private val HhhlBottomNavDarkInactiveText = Color(0xFFC7C7C7)
private val HhhlBottomNavLightInactiveIcon = Color(0xFF666666)
private val HhhlBottomNavLightInactiveText = Color(0xFF555555)

@Composable
fun HhhlBottomNav(
    selected: RootRoute,
    onSelected: (RootRoute) -> Unit,
    modifier: Modifier = Modifier,
    routes: List<RootRoute> = primaryRootRoutes(),
    badgeCounts: Map<RootRoute, Int> = emptyMap(),
) {
    val colors = LocalHhhlColors.current
    val isDarkPage = colors.pageBackground.luminance() < 0.5f
    val navBackground = if (isDarkPage) HhhlBottomNavDarkBackground else HhhlBottomNavLightBackground
    val dividerColor = if (isDarkPage) HhhlBottomNavDarkDivider else HhhlBottomNavLightDivider
    val inactiveIconColor = if (isDarkPage) HhhlBottomNavDarkInactiveIcon else HhhlBottomNavLightInactiveIcon
    val inactiveTextColor = if (isDarkPage) HhhlBottomNavDarkInactiveText else HhhlBottomNavLightInactiveText
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    val extraHeight = ((fontScale - 1f) * 18f).dp
    val navHeight = HhhlBottomNavHeight + extraHeight
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(navHeight)
            .background(navBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight - 1.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            routes.forEach { route ->
                HhhlBottomNavItem(
                    route = route,
                    active = selected == route,
                    badgeText = bottomNavBadgeText(badgeCounts[route] ?: 0),
                    onClick = { onSelected(route) },
                    modifier = Modifier.weight(1f),
                    itemHeight = navHeight - 1.dp,
                    navBackground = navBackground,
                    activeColor = HhhlBottomNavWechatGreen,
                    inactiveIconColor = inactiveIconColor,
                    inactiveTextColor = inactiveTextColor,
                    fontScale = fontScale,
                )
            }
        }
    }
}

@Composable
private fun HhhlBottomNavItem(
    route: RootRoute,
    active: Boolean,
    badgeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = HhhlBottomNavHeight,
    navBackground: Color,
    activeColor: Color,
    inactiveIconColor: Color,
    inactiveTextColor: Color,
    fontScale: Float = 1f,
) {
    val colors = LocalHhhlColors.current
    val iconColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveIconColor,
        animationSpec = tween(durationMillis = 180),
        label = "bottom-nav-icon-color",
    )
    val textColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveTextColor,
        animationSpec = tween(durationMillis = 180),
        label = "bottom-nav-label-color",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(itemHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = route.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val labelSlotHeight = HhhlBottomNavLabelSlotHeight + ((fontScale - 1f) * 10f).dp
        Column(
            modifier = Modifier.padding(
                top = HhhlBottomNavTopPadding,
                bottom = HhhlBottomNavBottomPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Box(
                modifier = Modifier.height(HhhlBottomNavIconSlotHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = route.bottomNavIcon(),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(HhhlBottomNavIconSize),
                )
                badgeText?.let { text ->
                    val badgeHeight = HhhlBottomNavBadgeHeight + ((fontScale - 1f) * 8f).dp
                    val badgeMinWidth = bottomNavBadgeMinWidth(text) + ((fontScale - 1f) * 7f).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 13.dp, y = (-3).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.unreadBadge.copy(alpha = 0.96f))
                            .border(
                                width = 1.dp,
                                color = navBackground.copy(alpha = HhhlNotificationBadgeStrokeAlpha),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .height(badgeHeight)
                            .widthIn(min = badgeMinWidth)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            color = colors.textInverse,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.height(labelSlotHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = route.label,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
    }
}

fun bottomNavBadgeText(count: Int): String? {
    return when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }
}

internal fun bottomNavBadgeMinWidth(text: String): Dp {
    return when {
        text.length >= 3 -> HhhlBottomNavBadgeLargeMinWidth
        text.length >= 2 -> HhhlBottomNavBadgeDoubleMinWidth
        else -> HhhlBottomNavBadgeSingleMinWidth
    }
}

private fun RootRoute.bottomNavIcon(): ImageVector {
    return when (this) {
        RootRoute.Timeline -> Icons.Filled.Home
        RootRoute.Discover -> Icons.Filled.Search
        RootRoute.Chat -> Icons.Filled.Email
        RootRoute.Notifications -> Icons.Filled.Notifications
        RootRoute.Profile -> Icons.Filled.Person
    }
}
