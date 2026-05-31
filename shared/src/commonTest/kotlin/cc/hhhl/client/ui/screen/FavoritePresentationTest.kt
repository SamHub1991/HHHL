package cc.hhhl.client.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritePresentationTest {
    @Test
    fun favoriteScreenUsesPostAndMessageTabs() {
        assertEquals(listOf("帖子", "信息"), favoriteTabLabels())
    }
}
