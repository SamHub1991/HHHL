package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteRowDensitySpecTest {
    @Test
    fun comfortableDensityMatchesCurrentTimelineSpacing() {
        assertEquals(
            NoteRowMetrics(
                horizontalPadding = 14,
                verticalPadding = 12,
                avatarSize = 42,
                contentSpacing = 6,
                mediaHeight = 86,
            ),
            noteRowMetrics(NoteRowDensity.Comfortable),
        )
    }

    @Test
    fun compactDensityKeepsMediaAndRowsSmallerForDenseTimelines() {
        val compact = noteRowMetrics(NoteRowDensity.Compact)
        val comfortable = noteRowMetrics(NoteRowDensity.Comfortable)

        assertTrue(compact.verticalPadding < comfortable.verticalPadding)
        assertTrue(compact.avatarSize < comfortable.avatarSize)
        assertTrue(compact.mediaHeight < comfortable.mediaHeight)
    }

    @Test
    fun noteActionsShareCompactControlMetrics() {
        assertEquals(30.dp, HhhlNoteActionMinHeight)
        assertEquals(34.dp, HhhlNoteActionMinWidth)
        assertEquals(6.dp, HhhlNoteActionHorizontalPadding)
        assertEquals(5.dp, HhhlNoteActionVerticalPadding)
        assertEquals(17.dp, HhhlNoteActionIconSize)
        assertEquals(4.dp, HhhlNoteActionSpacing)
    }
}
