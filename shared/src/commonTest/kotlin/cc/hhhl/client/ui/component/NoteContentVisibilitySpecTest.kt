package cc.hhhl.client.ui.component

import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteContentVisibilitySpecTest {
    @Test
    fun noteContentIsInitiallyHiddenWhenContentWarningExists() {
        val note = FakeData.timeline.first().copy(cw = "剧透")

        assertFalse(noteContentVisible(note, expanded = false))
        assertTrue(noteContentVisible(note, expanded = true))
    }

    @Test
    fun noteContentIsVisibleWithoutContentWarning() {
        val note = FakeData.timeline.first().copy(cw = null)

        assertTrue(noteContentVisible(note, expanded = false))
    }
}
