package cc.hhhl.client.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteLinkNavigationTest {
    @Test
    fun localNoteUrlNavigatesToNoteDetail() {
        assertEquals(
            SiteLinkNavigationTarget.NoteDetail("note-123"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/notes/note-123"),
        )
    }

    @Test
    fun localUserUrlNavigatesToMentionResolution() {
        assertEquals(
            SiteLinkNavigationTarget.Mention("alice"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/@alice"),
        )
    }

    @Test
    fun relativeLocalNoteUrlNavigatesToNoteDetail() {
        assertEquals(
            SiteLinkNavigationTarget.NoteDetail("note-123"),
            siteLinkNavigationTarget("/notes/note-123?show=1"),
        )
    }

    @Test
    fun relativeLocalUserUrlNavigatesToMentionResolution() {
        assertEquals(
            SiteLinkNavigationTarget.Mention("alice"),
            siteLinkNavigationTarget("/@alice"),
        )
    }

    @Test
    fun externalUrlStaysExternal() {
        assertEquals(
            SiteLinkNavigationTarget.External("https://example.com/notes/note-123"),
            siteLinkNavigationTarget("https://example.com/notes/note-123"),
        )
    }

    @Test
    fun protocolRelativeUrlStaysExternal() {
        assertEquals(
            SiteLinkNavigationTarget.External("//example.com/notes/note-123"),
            siteLinkNavigationTarget("//example.com/notes/note-123"),
        )
    }
}
