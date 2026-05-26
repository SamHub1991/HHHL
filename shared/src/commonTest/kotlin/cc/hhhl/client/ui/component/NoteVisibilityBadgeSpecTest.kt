package cc.hhhl.client.ui.component

import cc.hhhl.client.model.NoteVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteVisibilityBadgeSpecTest {
    @Test
    fun publicNotesDoNotNeedAVisibilityBadge() {
        assertNull(noteVisibilityBadge(NoteVisibility.Public))
    }

    @Test
    fun nonPublicNotesShowWebParityVisibilityBadges() {
        assertEquals("首页", noteVisibilityBadge(NoteVisibility.Home))
        assertEquals("关注者", noteVisibilityBadge(NoteVisibility.Followers))
        assertEquals("指定", noteVisibilityBadge(NoteVisibility.Specified))
    }
}
