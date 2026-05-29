package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteRowActionSpecTest {
    @Test
    fun renoteMenuOffersRepostAndQuoteActions() {
        assertEquals(
            listOf(NoteRenoteAction.Repost, NoteRenoteAction.Quote),
            noteRenoteActions(),
        )
    }

    @Test
    fun overflowMenuCollectsSecondaryAndDestructiveActions() {
        assertEquals(
            listOf(
                NoteOverflowAction.OpenDetail,
                NoteOverflowAction.CopyContent,
                NoteOverflowAction.CopyLink,
                NoteOverflowAction.Embed,
                NoteOverflowAction.Share,
                NoteOverflowAction.AiSummary,
                NoteOverflowAction.AiReplyDraft,
                NoteOverflowAction.Favorite,
                NoteOverflowAction.AddToClip,
                NoteOverflowAction.HideFromList,
                NoteOverflowAction.MuteNote,
                NoteOverflowAction.UnmuteNote,
                NoteOverflowAction.MuteRenotes,
                NoteOverflowAction.UnmuteRenotes,
                NoteOverflowAction.User,
                NoteOverflowAction.Report,
                NoteOverflowAction.Delete,
            ),
            noteOverflowActions(canAddToClip = true, canDelete = true),
        )
        assertEquals(
            listOf(
                NoteOverflowAction.OpenDetail,
                NoteOverflowAction.CopyContent,
                NoteOverflowAction.CopyLink,
                NoteOverflowAction.Embed,
                NoteOverflowAction.Share,
                NoteOverflowAction.AiSummary,
                NoteOverflowAction.AiReplyDraft,
                NoteOverflowAction.Favorite,
                NoteOverflowAction.HideFromList,
                NoteOverflowAction.MuteNote,
                NoteOverflowAction.UnmuteNote,
                NoteOverflowAction.MuteRenotes,
                NoteOverflowAction.UnmuteRenotes,
                NoteOverflowAction.User,
                NoteOverflowAction.Report,
                NoteOverflowAction.Delete,
            ),
            noteOverflowActions(canAddToClip = false, canDelete = true),
        )
        assertEquals(
            listOf(
                NoteOverflowAction.OpenDetail,
                NoteOverflowAction.CopyContent,
                NoteOverflowAction.CopyLink,
                NoteOverflowAction.Embed,
                NoteOverflowAction.Share,
                NoteOverflowAction.AiSummary,
                NoteOverflowAction.AiReplyDraft,
                NoteOverflowAction.Favorite,
                NoteOverflowAction.HideFromList,
                NoteOverflowAction.MuteNote,
                NoteOverflowAction.UnmuteNote,
                NoteOverflowAction.MuteRenotes,
                NoteOverflowAction.UnmuteRenotes,
                NoteOverflowAction.User,
                NoteOverflowAction.Report,
            ),
            noteOverflowActions(canAddToClip = false, canDelete = false),
        )
    }

    @Test
    fun noteActionButtonHidesZeroCountsAndCompactsLargeCounts() {
        assertEquals(NoteActionButtonSpec(countLabel = null, showCount = false), noteActionButtonSpec(0))
        assertEquals(NoteActionButtonSpec(countLabel = "9", showCount = true), noteActionButtonSpec(9))
        assertEquals(NoteActionButtonSpec(countLabel = "1k", showCount = true), noteActionButtonSpec(1200))
    }
}
