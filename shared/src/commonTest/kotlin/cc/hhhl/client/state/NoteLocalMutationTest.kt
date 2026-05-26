package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NotePollChoice
import cc.hhhl.client.model.NoteReaction
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteLocalMutationTest {
    @Test
    fun reactIncrementsExistingReactionAndTotalCount() {
        val note = FakeData.timeline[0].copy(
            reactionCount = 2,
            reactions = listOf(NoteReaction("👍", 2)),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.React(note.id, "👍"))

        assertEquals(3, result.single().reactionCount)
        assertEquals(listOf(NoteReaction("👍", 3)), result.single().reactions)
    }

    @Test
    fun reactAddsNewReactionChip() {
        val note = FakeData.timeline[0].copy(reactionCount = 0, reactions = emptyList())

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.React(note.id, "🚀"))

        assertEquals(1, result.single().reactionCount)
        assertEquals(listOf(NoteReaction("🚀", 1)), result.single().reactions)
    }

    @Test
    fun deleteReactionDecrementsAndRemovesEmptyChip() {
        val note = FakeData.timeline[0].copy(
            reactionCount = 1,
            reactions = listOf(NoteReaction("❤️", 1)),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.DeleteReaction(note.id, "❤️"))

        assertEquals(0, result.single().reactionCount)
        assertEquals(emptyList(), result.single().reactions)
    }

    @Test
    fun renoteIncrementsRenoteCount() {
        val note = FakeData.timeline[0].copy(renoteCount = 4)

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.Renote(note.id))

        assertEquals(5, result.single().renoteCount)
    }

    @Test
    fun deleteRemovesOnlyMatchingNote() {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]

        val result = listOf(first, second).applyNoteLocalMutation(NoteLocalMutation.Delete(first.id))

        assertEquals(listOf(second), result)
    }

    @Test
    fun votePollIncrementsChoiceAndMarksItVoted() {
        val note = FakeData.timeline[0].copy(
            poll = NotePoll(
                multiple = false,
                choices = listOf(
                    NotePollChoice(text = "A", votes = 2, isVoted = false),
                    NotePollChoice(text = "B", votes = 3, isVoted = false),
                ),
            ),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.VotePoll(note.id, 1))
        val choices = result.single().poll?.choices.orEmpty()

        assertEquals(2, choices[0].votes)
        assertEquals(false, choices[0].isVoted)
        assertEquals(4, choices[1].votes)
        assertEquals(true, choices[1].isVoted)
    }

    @Test
    fun votePollIgnoresInvalidChoice() {
        val note = FakeData.timeline[0].copy(
            poll = NotePoll(
                multiple = false,
                choices = listOf(NotePollChoice(text = "A", votes = 2, isVoted = false)),
            ),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.VotePoll(note.id, 4))

        assertEquals(note, result.single())
    }

    @Test
    fun reactStoresMyReaction() {
        val note = FakeData.timeline[0].copy(myReaction = null, reactionCount = 0, reactions = emptyList())

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.React(note.id, "👍"))

        assertEquals("👍", result.single().myReaction)
    }

    @Test
    fun reactWithSameExistingMyReactionIsIdempotent() {
        val note = FakeData.timeline[0].copy(
            myReaction = "👍",
            reactionCount = 3,
            reactions = listOf(NoteReaction("👍", 3)),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.React(note.id, "👍"))

        assertEquals(3, result.single().reactionCount)
        assertEquals(listOf(NoteReaction("👍", 3)), result.single().reactions)
        assertEquals("👍", result.single().myReaction)
    }

    @Test
    fun reactWithDifferentExistingMyReactionMovesReactionWithoutChangingTotalCount() {
        val note = FakeData.timeline[0].copy(
            myReaction = "👍",
            reactionCount = 5,
            reactions = listOf(NoteReaction("👍", 2), NoteReaction("❤️", 3)),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.React(note.id, "❤️"))

        assertEquals(5, result.single().reactionCount)
        assertEquals(listOf(NoteReaction("👍", 1), NoteReaction("❤️", 4)), result.single().reactions)
        assertEquals("❤️", result.single().myReaction)
    }

    @Test
    fun deleteReactionClearsMyReactionOnlyWhenItMatches() {
        val note = FakeData.timeline[0].copy(
            myReaction = "👍",
            reactionCount = 1,
            reactions = listOf(NoteReaction("👍", 1)),
        )

        val result = listOf(note).applyNoteLocalMutation(NoteLocalMutation.DeleteReaction(note.id, "👍"))

        assertEquals(null, result.single().myReaction)
    }

    @Test
    fun favoriteAndUnfavoriteToggleLocalState() {
        val note = FakeData.timeline[0].copy(isFavorited = false)

        val favorited = listOf(note).applyNoteLocalMutation(NoteLocalMutation.Favorite(note.id))
        val unfavorited = favorited.applyNoteLocalMutation(NoteLocalMutation.Unfavorite(note.id))

        assertEquals(true, favorited.single().isFavorited)
        assertEquals(false, unfavorited.single().isFavorited)
    }
}
