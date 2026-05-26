package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelTimelinePresentationTest {
    @Test
    fun channelVisibleTimelineNotesOmitsPinnedDuplicates() {
        val pinned = FakeData.timeline[0]
        val regular = FakeData.timeline[1]

        val visibleNotes = channelVisibleTimelineNotes(
            notes = listOf(pinned, regular),
            pinnedNotes = listOf(pinned),
        )

        assertEquals(listOf(regular), visibleNotes)
    }
}
