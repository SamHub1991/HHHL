package cc.hhhl.client.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import cc.hhhl.client.model.SettingsManagementSectionKey

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
    fun localNoteUrlIgnoresAdjacentEmoji() {
        assertEquals(
            SiteLinkNavigationTarget.NoteDetail("amtbghzudk"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/notes/amtbghzudk😺"),
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
    fun localPageIdUrlNavigatesToPageDetail() {
        assertEquals(
            SiteLinkNavigationTarget.PageDetail("page-123"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/pages/page-123"),
        )
    }

    @Test
    fun localUserPageUrlNavigatesToPagePath() {
        assertEquals(
            SiteLinkNavigationTarget.PagePath(username = "alice", name = "guide"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/@alice/pages/guide"),
        )
    }

    @Test
    fun localGalleryPostUrlNavigatesToGalleryDetail() {
        assertEquals(
            SiteLinkNavigationTarget.GalleryPostDetail("post-123"),
            siteLinkNavigationTarget("/gallery/posts/post-123"),
        )
    }

    @Test
    fun localPlayUrlNavigatesToFlashDetail() {
        assertEquals(
            SiteLinkNavigationTarget.FlashDetail("flash-123"),
            siteLinkNavigationTarget("/play/flash-123"),
        )
    }

    @Test
    fun localAnnouncementUrlNavigatesToAnnouncementDetail() {
        assertEquals(
            SiteLinkNavigationTarget.AnnouncementDetail("ann-123"),
            siteLinkNavigationTarget("/announcements/ann-123"),
        )
    }

    @Test
    fun localAchievementsUrlNavigatesToAchievements() {
        assertEquals(
            SiteLinkNavigationTarget.Achievements,
            siteLinkNavigationTarget("https://dc.hhhl.cc/my/achievements"),
        )
    }

    @Test
    fun localUserIdUrlNavigatesToUserProfile() {
        assertEquals(
            SiteLinkNavigationTarget.UserProfile("user-123"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/users/user-123"),
        )
    }

    @Test
    fun localChatRoomUrlNavigatesToChatRoom() {
        assertEquals(
            SiteLinkNavigationTarget.ChatRoom("room-123"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/chat/room/room-123"),
        )
    }

    @Test
    fun localChatRoomUrlIgnoresAdjacentCjkText() {
        assertEquals(
            SiteLinkNavigationTarget.ChatRoom("amrhafqz37"),
            siteLinkNavigationTarget("https://dc.hhhl.cc/chat/room/amrhafqz37帮我看下"),
        )
    }

    @Test
    fun localClipUrlNavigatesToClipDetail() {
        assertEquals(
            SiteLinkNavigationTarget.ClipDetail("clip-123"),
            siteLinkNavigationTarget("/clips/clip-123"),
        )
    }

    @Test
    fun localSettingsApiUrlNavigatesToNativeManagement() {
        assertEquals(
            SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.ApiTokens),
            siteLinkNavigationTarget("https://dc.hhhl.cc/settings/api"),
        )
    }

    @Test
    fun localSecondaryDomainNavigatesInternally() {
        assertEquals(
            SiteLinkNavigationTarget.NoteDetail("note-123"),
            siteLinkNavigationTarget("https://hhhl.cc/notes/note-123"),
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
