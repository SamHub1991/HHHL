package cc.hhhl.client.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesStoreTest {
    @Test
    fun timelineListsKnownVersionsLatestFirst() {
        val timeline = releaseNotesTimeline()

        assertEquals(listOf("0.6.1", "0.6.0", "0.5.2", "0.5.1", "0.5.0", "0.4.2", "0.4.1", "0.4.0"), timeline.map { it.versionName })
    }

    @Test
    fun currentReleaseNotesIncludeUpdateTimelineChanges() {
        val notes = releaseNotesFor("v0.6.1")

        assertEquals("0.6.1", notes.versionName)
        assertTrue(notes.highlights.any { it.contains("服务器下拉用户") })
        assertTrue(notes.highlights.any { it.contains("AI 结果卡片") })
    }
}
