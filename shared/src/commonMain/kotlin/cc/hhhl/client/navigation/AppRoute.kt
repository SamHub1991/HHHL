package cc.hhhl.client.navigation

import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.SettingsManagementSectionKey
import cc.hhhl.client.model.UserSocialKind

enum class RootRoute(val label: String, val icon: String) {
    Timeline("时间线", "⌂"),
    Discover("发现", "⌕"),
    Chat("聊天", "◇"),
    Notifications("通知", "◌"),
    Profile("我的", "●"),
}

fun primaryRootRoutes(): List<RootRoute> = listOf(
    RootRoute.Timeline,
    RootRoute.Discover,
    RootRoute.Chat,
    RootRoute.Notifications,
    RootRoute.Profile,
)

fun visibleRootRoutes(chatAvailable: Boolean): List<RootRoute> {
    return primaryRootRoutes().filter { route ->
        route != RootRoute.Chat || chatAvailable
    }
}

fun supportedRouteOrFallback(
    route: AppRoute,
    capabilities: InstanceCapabilities,
): AppRoute {
    return when (route) {
        AppRoute.Chat -> if (capabilities.chatAvailable) route else AppRoute.Timeline
        AppRoute.UserLists -> if (capabilities.canUseUserLists) route else AppRoute.Profile
        AppRoute.Antennas -> if (capabilities.canUseAntennas) route else AppRoute.Profile
        AppRoute.Clips -> if (capabilities.canUseClips) route else AppRoute.Profile
        else -> route
    }
}

fun rootRouteFor(route: AppRoute): RootRoute {
    return when (route) {
        AppRoute.Timeline,
        is AppRoute.NoteDetail,
        is AppRoute.Compose,
            -> RootRoute.Timeline
        AppRoute.Discover,
        AppRoute.Channels,
        AppRoute.Pages,
        AppRoute.Gallery,
        AppRoute.Flash,
        AppRoute.Announcements,
            -> RootRoute.Discover
        AppRoute.Chat,
        AppRoute.AiAssistant,
            -> RootRoute.Chat
        AppRoute.Notifications -> RootRoute.Notifications
        AppRoute.Profile,
        AppRoute.ProfileNotes,
        AppRoute.Settings,
        AppRoute.AiSettings,
        AppRoute.ReleaseNotes,
        AppRoute.ThemeCustomization,
        AppRoute.Automation,
        AppRoute.AutomationLogs,
        is AppRoute.SettingsManagement,
        AppRoute.AdminDashboard,
        AppRoute.Drive,
        AppRoute.Achievements,
        AppRoute.FavoriteNotes,
        AppRoute.UserLists,
        AppRoute.FollowRequests,
        AppRoute.RelationshipManagement,
        AppRoute.Antennas,
        AppRoute.Clips,
        is AppRoute.UserProfile,
        is AppRoute.UserSocial,
            -> RootRoute.Profile
    }
}

data class AiAssistantReviewPageTarget(
    val rootRoute: RootRoute,
    val route: AppRoute,
)

fun aiAssistantReviewPageTarget(
    currentRoute: AppRoute,
    sourceRoute: AppRoute?,
    sourceRootRoute: RootRoute?,
): AiAssistantReviewPageTarget {
    val targetRoute = if (currentRoute == AppRoute.AiAssistant) {
        sourceRoute ?: AppRoute.Chat
    } else {
        currentRoute
    }
    val targetRootRoute = if (currentRoute == AppRoute.AiAssistant && sourceRoute == targetRoute) {
        sourceRootRoute ?: rootRouteFor(targetRoute)
    } else {
        rootRouteFor(targetRoute)
    }
    return AiAssistantReviewPageTarget(
        rootRoute = targetRootRoute,
        route = targetRoute,
    )
}

sealed interface AppRoute {
    data object Timeline : AppRoute
    data object Discover : AppRoute
    data object Chat : AppRoute
    data object AiAssistant : AppRoute
    data object Notifications : AppRoute
    data object Profile : AppRoute
    data object ProfileNotes : AppRoute
    data object Settings : AppRoute
    data object AiSettings : AppRoute
    data object ReleaseNotes : AppRoute
    data object ThemeCustomization : AppRoute
    data object Automation : AppRoute
    data object AutomationLogs : AppRoute
    data class SettingsManagement(val key: SettingsManagementSectionKey) : AppRoute
    data object AdminDashboard : AppRoute
    data object Drive : AppRoute
    data object Achievements : AppRoute
    data object FavoriteNotes : AppRoute
    data object UserLists : AppRoute
    data object FollowRequests : AppRoute
    data object RelationshipManagement : AppRoute
    data object Antennas : AppRoute
    data object Clips : AppRoute
    data object Channels : AppRoute
    data object Pages : AppRoute
    data object Gallery : AppRoute
    data object Flash : AppRoute
    data object Announcements : AppRoute
    data class UserProfile(val userId: String) : AppRoute
    data class UserSocial(
        val userId: String,
        val kind: UserSocialKind,
        val displayName: String?,
    ) : AppRoute
    data class NoteDetail(val noteId: String) : AppRoute
    data class Compose(
        val replyToId: String? = null,
        val renoteId: String? = null,
        val channelId: String? = null,
        val editId: String? = null,
    ) : AppRoute
}
