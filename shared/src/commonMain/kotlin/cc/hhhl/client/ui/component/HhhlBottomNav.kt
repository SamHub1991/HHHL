package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
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

internal val HhhlBottomNavHeight = 56.dp
internal val HhhlBottomNavPanelHeight = 44.dp
internal val HhhlBottomNavPanelHorizontalPadding = 12.dp
internal val HhhlBottomNavPanelCornerRadius = 24.dp
internal val HhhlBottomNavPanelElevation = 10.dp
internal val HhhlBottomNavPanelHighlightAlpha = 0.18f
internal val HhhlBottomNavIconSize = 20.dp
internal val HhhlBottomNavIconSlotWidth = 60.dp
internal val HhhlBottomNavIconSlotHeight = 27.dp
internal val HhhlBottomNavLabelSlotHeight = 14.dp
internal val HhhlBottomNavIconOffsetIdle = 6.dp
internal val HhhlBottomNavIconOffsetActive = 0.dp
internal val HhhlBottomNavIdlePillWidth = 40.dp
internal val HhhlBottomNavSelectedPillWidth = 54.dp
internal val HhhlBottomNavSelectedPillHeight = 25.dp
internal val HhhlBottomNavSelectedPillCornerRadius = 14.dp
internal val HhhlBottomNavVerticalPadding = 6.dp
internal val HhhlBottomNavBadgeHeight = 18.dp
internal val HhhlBottomNavBadgeSingleMinWidth = 18.dp
internal val HhhlBottomNavBadgeDoubleMinWidth = 24.dp
internal val HhhlBottomNavBadgeLargeMinWidth = 30.dp
internal val HhhlNotificationBadgeHighlightAlpha = 0.22f
internal val HhhlNotificationBadgeStrokeAlpha = 0.30f

@Composable
fun HhhlBottomNav(
    selected: RootRoute,
    onSelected: (RootRoute) -> Unit,
    modifier: Modifier = Modifier,
    routes: List<RootRoute> = primaryRootRoutes(),
    badgeCounts: Map<RootRoute, Int> = emptyMap(),
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    val extraHeight = ((fontScale - 1f) * 18f).dp
    val navHeight = HhhlBottomNavHeight + extraHeight
    val panelHeight = HhhlBottomNavPanelHeight + extraHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navHeight)
            .padding(
                horizontal = HhhlBottomNavPanelHorizontalPadding,
                vertical = HhhlBottomNavVerticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val panelShape = RoundedCornerShape(HhhlBottomNavPanelCornerRadius)
        val panelBrush = Brush.verticalGradient(
            colors = if (isDarkSurface) {
                listOf(
                    colors.accent.copy(alpha = 0.06f),
                    colors.bottomNavBackground.copy(alpha = 0.90f),
                    colors.bottomNavBackground.copy(alpha = 0.78f),
                )
            } else {
                listOf(
                    colors.bottomNavBackground.copy(alpha = 0.95f),
                    colors.bottomNavBackground.copy(alpha = 0.86f),
                )
            },
        )
        val panelBorderColor = if (isDarkSurface) {
            colors.focusRing.copy(alpha = HhhlBottomNavPanelHighlightAlpha)
        } else {
            colors.border.copy(alpha = 0.32f)
        }
        if (isDarkSurface) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .padding(horizontal = 10.dp)
                    .clip(panelShape)
                    .background(colors.accent.copy(alpha = 0.025f)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .shadow(
                    elevation = HhhlBottomNavPanelElevation,
                    shape = panelShape,
                    clip = false,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .clip(panelShape)
                .background(panelBrush)
                .border(
                    width = 1.dp,
                    color = panelBorderColor,
                    shape = panelShape,
                )
                .padding(horizontal = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        if (isDarkSurface) {
                            colors.surface.copy(alpha = 0.22f)
                        } else {
                            colors.surface.copy(alpha = 0.40f)
                        },
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                routes.forEach { route ->
                    val active = selected == route
                    HhhlBottomNavItem(
                        route = route,
                        active = active,
                        badgeText = bottomNavBadgeText(badgeCounts[route] ?: 0),
                        onClick = { onSelected(route) },
                        modifier = Modifier.weight(1f),
                        navHeight = navHeight,
                        panelHeight = panelHeight,
                        fontScale = fontScale,
                    )
                }
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
    navHeight: Dp = HhhlBottomNavHeight,
    panelHeight: Dp = HhhlBottomNavPanelHeight,
    fontScale: Float = 1f,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val pillWidth by animateDpAsState(
        targetValue = if (active) HhhlBottomNavSelectedPillWidth else HhhlBottomNavIdlePillWidth,
        animationSpec = tween(durationMillis = 190),
        label = "bottom-nav-pill-width",
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) {
            colors.bottomNavSelected.copy(alpha = if (isDarkSurface) 0.84f else 0.78f)
        } else {
            colors.surface.copy(alpha = 0.0f)
        },
        animationSpec = tween(durationMillis = 190),
        label = "bottom-nav-pill-color",
    )
    val iconOffsetY by animateDpAsState(
        targetValue = if (active) HhhlBottomNavIconOffsetActive else HhhlBottomNavIconOffsetIdle,
        animationSpec = tween(durationMillis = 220),
        label = "bottom-nav-icon-offset",
    )
    val iconColor = if (active) {
        colors.accent
    } else {
        colors.textMuted
    }
    val textColor by animateColorAsState(
        targetValue = if (active) {
            colors.accent
        } else {
            colors.textPrimary.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "bottom-nav-text-color",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(navHeight - (HhhlBottomNavVerticalPadding * 2f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = route.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.height(panelHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val labelSlotHeight = HhhlBottomNavLabelSlotHeight + ((fontScale - 1f) * 12f).dp
            Box(
                modifier = Modifier
                    .height(HhhlBottomNavIconSlotHeight)
                    .width(HhhlBottomNavIconSlotWidth),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(HhhlBottomNavSelectedPillHeight)
                        .width(pillWidth)
                        .widthIn(min = HhhlBottomNavIdlePillWidth, max = HhhlBottomNavSelectedPillWidth)
                        .clip(RoundedCornerShape(HhhlBottomNavSelectedPillCornerRadius))
                        .background(pillColor)
                        .border(
                            width = 1.dp,
                            color = if (active) {
                                colors.focusRing.copy(alpha = if (isDarkSurface) 0.44f else 0.28f)
                            } else {
                                colors.surface.copy(alpha = 0f)
                            },
                            shape = RoundedCornerShape(HhhlBottomNavSelectedPillCornerRadius),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = route.bottomNavIcon(),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .padding(top = iconOffsetY)
                            .size(HhhlBottomNavIconSize),
                    )
                }
                badgeText?.let { text ->
                    val badgeHeight = HhhlBottomNavBadgeHeight + ((fontScale - 1f) * 8f).dp
                    val badgeMinWidth = bottomNavBadgeMinWidth(text) + ((fontScale - 1f) * 7f).dp
                    val badgeBrush = Brush.verticalGradient(
                        colors = listOf(
                            colors.unreadBadge.copy(alpha = 0.96f),
                            colors.unreadBadge.copy(alpha = 0.84f),
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(999.dp))
                            .background(badgeBrush)
                            .border(
                                width = 1.dp,
                                color = colors.textInverse.copy(alpha = HhhlNotificationBadgeStrokeAlpha),
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
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationY = if (active) -1f else 0f
                    },
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
