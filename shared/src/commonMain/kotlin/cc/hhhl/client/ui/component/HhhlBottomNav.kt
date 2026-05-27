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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
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

internal val HhhlBottomNavHeight = 58.dp
internal val HhhlBottomNavPanelHeight = 46.dp
internal val HhhlBottomNavPanelHorizontalPadding = 12.dp
internal val HhhlBottomNavPanelCornerRadius = 28.dp
internal val HhhlBottomNavPanelElevation = 18.dp
internal val HhhlBottomNavPanelHighlightAlpha = 0.24f
internal val HhhlBottomNavIconSize = 20.dp
internal val HhhlBottomNavIconSlotWidth = 60.dp
internal val HhhlBottomNavIconSlotHeight = 28.dp
internal val HhhlBottomNavLabelSlotHeight = 13.dp
internal val HhhlBottomNavIconOffsetIdle = 6.dp
internal val HhhlBottomNavIconOffsetActive = 0.dp
internal val HhhlBottomNavIdlePillWidth = 40.dp
internal val HhhlBottomNavSelectedPillWidth = 56.dp
internal val HhhlBottomNavSelectedPillHeight = 26.dp
internal val HhhlBottomNavSelectedPillCornerRadius = 16.dp
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HhhlBottomNavHeight)
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
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                )
            } else {
                listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                )
            },
        )
        val panelBorderColor = if (isDarkSurface) {
            MaterialTheme.colorScheme.primary.copy(alpha = HhhlBottomNavPanelHighlightAlpha)
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        }
        if (isDarkSurface) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HhhlBottomNavPanelHeight)
                    .padding(horizontal = 10.dp)
                    .clip(panelShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HhhlBottomNavPanelHeight)
                .shadow(
                    elevation = HhhlBottomNavPanelElevation,
                    shape = panelShape,
                    clip = false,
                )
                .clip(panelShape)
                .background(panelBrush)
                .border(
                    width = 1.dp,
                    color = panelBorderColor,
                    shape = panelShape,
                )
                .padding(horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        if (isDarkSurface) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.40f)
                        },
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val pillWidth by animateDpAsState(
        targetValue = if (active) HhhlBottomNavSelectedPillWidth else HhhlBottomNavIdlePillWidth,
        animationSpec = tween(durationMillis = 190),
        label = "bottom-nav-pill-width",
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.18f else 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
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
        MaterialTheme.colorScheme.primary
    } else {
        LocalHhhlColors.current.subtleText
    }
    val textColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "bottom-nav-text-color",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(HhhlBottomNavHeight - (HhhlBottomNavVerticalPadding * 2f))
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
            modifier = Modifier.height(HhhlBottomNavPanelHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
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
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.24f else 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
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
                    val badgeBrush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.error.copy(alpha = 0.84f),
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(999.dp))
                            .background(badgeBrush)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onError.copy(alpha = HhhlNotificationBadgeStrokeAlpha),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .height(HhhlBottomNavBadgeHeight)
                            .widthIn(min = bottomNavBadgeMinWidth(text))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.height(HhhlBottomNavLabelSlotHeight),
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
