package cc.hhhl.client.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesStoreTest {
    @Test
    fun timelineListsKnownVersionsLatestFirst() {
        val timeline = releaseNotesTimeline()

        assertEquals(listOf("0.4.2", "0.4.1", "0.4.0"), timeline.map { it.versionName })
    }

    @Test
    fun currentReleaseNotesIncludeUpdateTimelineChanges() {
        val notes = releaseNotesFor("v0.4.2")

        assertEquals("0.4.2", notes.versionName)
        assertTrue(notes.highlights.any { it.contains("更新日志时间线") })
        assertTrue(notes.highlights.any { it.contains("首次打开") })
    }
}
