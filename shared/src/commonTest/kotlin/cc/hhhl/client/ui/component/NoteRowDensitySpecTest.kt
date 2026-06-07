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
                horizontalPadding = 16,
                verticalPadding = 14,
                avatarSize = 44,
                contentSpacing = 7,
                mediaHeight = 92,
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
    fun ultraCompactDensityIsTheSmallestReadableTimelineMode() {
        val ultraCompact = noteRowMetrics(NoteRowDensity.UltraCompact)
        val compact = noteRowMetrics(NoteRowDensity.Compact)

        assertTrue(ultraCompact.verticalPadding < compact.verticalPadding)
        assertTrue(ultraCompact.avatarSize < compact.avatarSize)
        assertTrue(ultraCompact.mediaHeight < compact.mediaHeight)
    }

    @Test
    fun noteActionsShareCompactControlMetrics() {
        assertEquals(28.dp, HhhlNoteActionMinHeight)
        assertEquals(30.dp, HhhlNoteActionMinWidth)
        assertEquals(4.dp, HhhlNoteActionHorizontalPadding)
        assertEquals(2.dp, HhhlNoteActionVerticalPadding)
        assertEquals(15.dp, HhhlNoteActionIconSize)
        assertEquals(2.dp, HhhlNoteActionSpacing)
    }

    @Test
    fun swipeActionsUseDirectionAndThreshold() {
        assertEquals(NoteRowSwipeAction.Reply, noteRowSwipeAction(offset = 80f, threshold = 76f))
        assertEquals(NoteRowSwipeAction.Favorite, noteRowSwipeAction(offset = -80f, threshold = 76f))
        assertEquals(null, noteRowSwipeAction(offset = 30f, threshold = 76f))
    }
}
