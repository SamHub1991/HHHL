package cc.hhhl.client.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesStoreTest {
    @Test
    fun timelineListsKnownVersionsLatestFirst() {
        val timeline = releaseNotesTimeline()

        assertEquals(listOf("0.5.2", "0.5.1", "0.5.0", "0.4.2", "0.4.1", "0.4.0"), timeline.map { it.versionName })
    }

    @Test
    fun currentReleaseNotesIncludeUpdateTimelineChanges() {
        val notes = releaseNotesFor("v0.5.2")

        assertEquals("0.5.2", notes.versionName)
        assertTrue(notes.highlights.any { it.contains("后台收到聊天室") })
        assertTrue(notes.highlights.any { it.contains("后台实时保活") })
    }
}
