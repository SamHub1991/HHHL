package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarImageSpecTest {
    @Test
    fun usesTransparentFallbackWhenRemoteAvatarUrlIsPresent() {
        val spec = avatarImageSpec(
            initial = "Alice",
            avatarUrl = "  https://dc.hhhl.cc/avatar.webp  ",
        )

        assertEquals("https://dc.hhhl.cc/avatar.webp", spec.remoteUrl)
        assertNull(spec.fallbackUrl)
    }

    @Test
    fun usesBrandFallbackWhenAvatarUrlIsBlank() {
        val spec = avatarImageSpec(initial = "", avatarUrl = " ")

        assertNull(spec.remoteUrl)
        assertEquals(HHHL_BRAND_AVATAR_URL, spec.fallbackUrl)
    }
}
