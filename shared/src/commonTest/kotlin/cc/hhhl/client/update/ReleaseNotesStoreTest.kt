package cc.hhhl.client.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseNotesStoreTest {
    @Test
    fun timelineListsKnownVersionsLatestFirst() {
        val timeline = releaseNotesTimeline()

        assertEquals(
            listOf("0.8.9", "0.8.8", "0.8.7", "0.8.6", "0.8.5", "0.8.4", "0.8.3", "0.8.2", "0.8.1", "0.8.0", "0.7.5", "0.7.4", "0.7.3", "0.7.2", "0.7.1", "0.7.0", "0.6.1", "0.6.0", "0.5.2", "0.5.1", "0.5.0", "0.4.2", "0.4.1", "0.4.0"),
            timeline.map { it.versionName },
        )
    }

    @Test
    fun currentReleaseNotesIncludeChatBubblePolish() {
        val notes = releaseNotesFor("v0.8.9")

        assertEquals("0.8.9", notes.versionName)
        assertTrue(notes.highlights.any { it.contains("头像") })
        assertTrue(notes.highlights.any { it.contains("气泡尾巴") })
        assertTrue(notes.highlights.any { it.contains("尖角") })
        assertTrue(notes.highlights.any { it.contains("长按") })
    }
}
