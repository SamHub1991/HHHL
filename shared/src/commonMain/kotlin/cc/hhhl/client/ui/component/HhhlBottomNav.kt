package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.navigation.RootRoute
import cc.hhhl.client.navigation.primaryRootRoutes
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlBottomNavHeight = 52.dp
internal val HhhlBottomNavIconSize = 22.dp

@Composable
fun HhhlBottomNav(
    selected: RootRoute,
    onSelected: (RootRoute) -> Unit,
    modifier: Modifier = Modifier,
    routes: List<RootRoute> = primaryRootRoutes(),
    badgeCounts: Map<RootRoute, Int> = emptyMap(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(HhhlBottomNavHeight),
    ) {
        routes.forEach { route ->
            val active = selected == route
            NavigationBarItem(
                selected = active,
                onClick = { onSelected(route) },
                icon = {
                    BadgedBox(
                        badge = {
                            bottomNavBadgeText(badgeCounts[route] ?: 0)?.let { badgeText ->
                                Badge {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = route.bottomNavIcon(),
                            contentDescription = route.label,
                            modifier = Modifier.size(HhhlBottomNavIconSize),
                        )
                    }
                },
                label = {
                    Text(
                        text = route.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = LocalHhhlColors.current.subtleText,
                    unselectedTextColor = LocalHhhlColors.current.subtleText,
                ),
                modifier = Modifier.weight(1f),
            )
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

private fun RootRoute.bottomNavIcon(): ImageVector {
    return when (this) {
        RootRoute.Timeline -> Icons.Filled.Home
        RootRoute.Discover -> Icons.Filled.Search
        RootRoute.Chat -> Icons.Filled.Email
        RootRoute.Notifications -> Icons.Filled.Notifications
        RootRoute.Profile -> Icons.Filled.Person
    }
}
