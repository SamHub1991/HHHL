package cc.hhhl.client.ui.component

import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun longNoteBodyCollapsesByCharacterCount() {
        val text = "长内容".repeat(140)

        assertTrue(noteBodyShouldCollapse(text, maxChars = 320))
    }

    @Test
    fun longNoteBodyCollapsesByExplicitLines() {
        val text = (1..7).joinToString(separator = "\n") { "第 $it 行" }

        assertTrue(noteBodyShouldCollapse(text, maxChars = 320, maxLines = 6))
    }

    @Test
    fun shortNoteBodyDoesNotCollapse() {
        assertFalse(noteBodyShouldCollapse("短内容\n第二行", maxChars = 320, maxLines = 6))
    }

    @Test
    fun mutedWordMatchesTextCwAndAuthor() {
        val note = FakeData.timeline.first().copy(
            text = "今天讨论 Kotlin",
            cw = "技术内容",
        )

        assertEquals("kotlin", note.firstMatchedMutedWord(listOf(" ", "kotlin")))
        assertTrue(note.matchesMutedWords(listOf("技术")))
        assertTrue(note.matchesMutedWords(listOf(note.author.username)))
        assertFalse(note.matchesMutedWords(listOf("", "   ")))
    }

    @Test
    fun hardMutedWordChecksQuotedNote() {
        val quoted = FakeData.timeline.first().copy(text = "需要隐藏的关键词")
        val note = FakeData.timeline.last().copy(
            text = "普通内容",
            quotedNote = quoted,
        )

        assertTrue(note.isHiddenByHardMutedWords(listOf("隐藏")))
        assertEquals("隐藏", note.firstMatchedMutedWord(listOf("隐藏")))
    }
}
