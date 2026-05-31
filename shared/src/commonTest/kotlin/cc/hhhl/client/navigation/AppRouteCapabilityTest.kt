package cc.hhhl.client.navigation

import cc.hhhl.client.model.InstanceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteCapabilityTest {
    @Test
    fun unsupportedCollectionRoutesFallbackToProfile() {
        val capabilities = InstanceCapabilities(
            clipLimit = 0,
            antennaLimit = 0,
            userListLimit = 0,
        )

        assertEquals(AppRoute.Profile, supportedRouteOrFallback(AppRoute.Clips, capabilities))
        assertEquals(AppRoute.Profile, supportedRouteOrFallback(AppRoute.Antennas, capabilities))
        assertEquals(AppRoute.Profile, supportedRouteOrFallback(AppRoute.UserLists, capabilities))
    }

    @Test
    fun unavailableChatFallbacksToTimeline() {
        assertEquals(
            AppRoute.Timeline,
            supportedRouteOrFallback(
                route = AppRoute.Chat,
                capabilities = InstanceCapabilities(chatAvailable = false),
            ),
        )
    }

    @Test
    fun supportedRoutesRemainUnchanged() {
        val capabilities = InstanceCapabilities(
            chatAvailable = true,
            clipLimit = 10,
            antennaLimit = 5,
            userListLimit = 10,
        )

        assertEquals(AppRoute.Chat, supportedRouteOrFallback(AppRoute.Chat, capabilities))
        assertEquals(AppRoute.Clips, supportedRouteOrFallback(AppRoute.Clips, capabilities))
        assertEquals(RootRoute.Profile, rootRouteFor(AppRoute.Clips))
        assertEquals(RootRoute.Profile, rootRouteFor(AppRoute.ReleaseNotes))
        assertEquals(RootRoute.Profile, rootRouteFor(AppRoute.AutomationLogs))
        assertEquals(RootRoute.Chat, rootRouteFor(AppRoute.AiAssistant))
    }
}
