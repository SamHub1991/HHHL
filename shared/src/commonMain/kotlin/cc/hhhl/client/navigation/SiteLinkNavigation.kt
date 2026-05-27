package cc.hhhl.client.navigation

import cc.hhhl.client.model.SettingsManagementSectionKey

sealed interface SiteLinkNavigationTarget {
    data class NoteDetail(val noteId: String) : SiteLinkNavigationTarget
    data class Mention(val username: String) : SiteLinkNavigationTarget
    data class UserProfile(val userId: String) : SiteLinkNavigationTarget
    data object Channels : SiteLinkNavigationTarget
    data class ChannelDetail(val channelId: String) : SiteLinkNavigationTarget
    data object Pages : SiteLinkNavigationTarget
    data class PageDetail(val pageId: String) : SiteLinkNavigationTarget
    data class PagePath(val username: String, val name: String) : SiteLinkNavigationTarget
    data object Gallery : SiteLinkNavigationTarget
    data class GalleryPostDetail(val postId: String) : SiteLinkNavigationTarget
    data object Flash : SiteLinkNavigationTarget
    data class FlashDetail(val flashId: String) : SiteLinkNavigationTarget
    data object Announcements : SiteLinkNavigationTarget
    data class AnnouncementDetail(val announcementId: String) : SiteLinkNavigationTarget
    data object Chat : SiteLinkNavigationTarget
    data class ChatRoom(val roomId: String) : SiteLinkNavigationTarget
    data class ChatUser(val userId: String) : SiteLinkNavigationTarget
    data object Drive : SiteLinkNavigationTarget
    data object Achievements : SiteLinkNavigationTarget
    data object FavoriteNotes : SiteLinkNavigationTarget
    data object UserLists : SiteLinkNavigationTarget
    data class UserListDetail(val listId: String) : SiteLinkNavigationTarget
    data object FollowRequests : SiteLinkNavigationTarget
    data object RelationshipManagement : SiteLinkNavigationTarget
    data object Antennas : SiteLinkNavigationTarget
    data class AntennaDetail(val antennaId: String) : SiteLinkNavigationTarget
    data object Clips : SiteLinkNavigationTarget
    data class ClipDetail(val clipId: String) : SiteLinkNavigationTarget
    data object Settings : SiteLinkNavigationTarget
    data class SettingsManagement(val key: SettingsManagementSectionKey) : SiteLinkNavigationTarget
    data object Profile : SiteLinkNavigationTarget
    data class External(val url: String) : SiteLinkNavigationTarget
}

fun siteLinkNavigationTarget(url: String): SiteLinkNavigationTarget {
    val cleanUrl = url.trim()
    val path = localPath(cleanUrl)
        ?: return SiteLinkNavigationTarget.External(cleanUrl)

    return when {
        path.startsWith("notes/") -> {
            val noteId = path.removePrefix("notes/").substringBefore('/').trim()
            if (noteId.isNotBlank()) {
                SiteLinkNavigationTarget.NoteDetail(noteId)
            } else {
                SiteLinkNavigationTarget.External(cleanUrl)
            }
        }
        path.startsWith("users/") -> path.localPathIdAfter("users")
            ?.let { SiteLinkNavigationTarget.UserProfile(it) }
            ?: SiteLinkNavigationTarget.External(cleanUrl)
        path.startsWith("user/") -> path.localPathIdAfter("user")
            ?.let { SiteLinkNavigationTarget.UserProfile(it) }
            ?: SiteLinkNavigationTarget.External(cleanUrl)
        path == "channels" -> SiteLinkNavigationTarget.Channels
        path.startsWith("channels/") -> path.localPathIdAfter("channels")
            ?.let { SiteLinkNavigationTarget.ChannelDetail(it) }
            ?: SiteLinkNavigationTarget.Channels
        path == "pages" -> SiteLinkNavigationTarget.Pages
        path.startsWith("pages/") -> path.localPathIdAfter("pages")
            ?.let { SiteLinkNavigationTarget.PageDetail(it) }
            ?: SiteLinkNavigationTarget.Pages
        path.endsWith("/pages") && path.startsWith("@") -> SiteLinkNavigationTarget.Pages
        path.contains("/pages/") && path.startsWith("@") -> path.localUserPagePathTarget()
            ?: SiteLinkNavigationTarget.Pages
        path.startsWith("@") -> {
            val username = path.removePrefix("@").substringBefore('/').trim()
            if (username.isNotBlank()) {
                SiteLinkNavigationTarget.Mention(username)
            } else {
                SiteLinkNavigationTarget.External(cleanUrl)
            }
        }
        path == "gallery" -> SiteLinkNavigationTarget.Gallery
        path == "gallery/posts" -> SiteLinkNavigationTarget.Gallery
        path.startsWith("gallery/posts/") -> path.localPathIdAfter("gallery/posts")
            ?.let { SiteLinkNavigationTarget.GalleryPostDetail(it) }
            ?: SiteLinkNavigationTarget.Gallery
        path.startsWith("gallery/") -> path.localPathIdAfter("gallery")
            ?.let { SiteLinkNavigationTarget.GalleryPostDetail(it) }
            ?: SiteLinkNavigationTarget.Gallery
        path == "play" -> SiteLinkNavigationTarget.Flash
        path.startsWith("play/") -> path.localPathIdAfter("play")
            ?.let { SiteLinkNavigationTarget.FlashDetail(it) }
            ?: SiteLinkNavigationTarget.Flash
        path == "flash" -> SiteLinkNavigationTarget.Flash
        path.startsWith("flash/") -> path.localPathIdAfter("flash")
            ?.let { SiteLinkNavigationTarget.FlashDetail(it) }
            ?: SiteLinkNavigationTarget.Flash
        path == "announcements" -> SiteLinkNavigationTarget.Announcements
        path.startsWith("announcements/") -> path.localPathIdAfter("announcements")
            ?.let { SiteLinkNavigationTarget.AnnouncementDetail(it) }
            ?: SiteLinkNavigationTarget.Announcements
        path == "chat" -> SiteLinkNavigationTarget.Chat
        path.startsWith("chat/room/") -> path.localPathIdAfter("chat/room")
            ?.let { SiteLinkNavigationTarget.ChatRoom(it) }
            ?: SiteLinkNavigationTarget.Chat
        path.startsWith("chat/user/") -> path.localPathIdAfter("chat/user")
            ?.let { SiteLinkNavigationTarget.ChatUser(it) }
            ?: SiteLinkNavigationTarget.Chat
        path.startsWith("chat/messages/") -> SiteLinkNavigationTarget.Chat
        path == "my/favorites" ||
            path == "my/favorites/notes" ||
            path == "favorites" -> SiteLinkNavigationTarget.FavoriteNotes
        path == "my/achievements" -> SiteLinkNavigationTarget.Achievements
        path == "my/lists" || path == "lists" -> SiteLinkNavigationTarget.UserLists
        path.startsWith("my/lists/") -> path.localPathIdAfter("my/lists")
            ?.let { SiteLinkNavigationTarget.UserListDetail(it) }
            ?: SiteLinkNavigationTarget.UserLists
        path.startsWith("lists/") -> path.localPathIdAfter("lists")
            ?.let { SiteLinkNavigationTarget.UserListDetail(it) }
            ?: SiteLinkNavigationTarget.UserLists
        path == "my/follow-requests" ||
            path == "follow-requests" -> SiteLinkNavigationTarget.FollowRequests
        path == "my/relationships" ||
            path == "relationships" -> SiteLinkNavigationTarget.RelationshipManagement
        path == "my/antennas" || path == "antennas" -> SiteLinkNavigationTarget.Antennas
        path.startsWith("my/antennas/") -> path.localPathIdAfter("my/antennas")
            ?.let { SiteLinkNavigationTarget.AntennaDetail(it) }
            ?: SiteLinkNavigationTarget.Antennas
        path.startsWith("antennas/") -> path.localPathIdAfter("antennas")
            ?.let { SiteLinkNavigationTarget.AntennaDetail(it) }
            ?: SiteLinkNavigationTarget.Antennas
        path == "my/clips" || path == "clips" -> SiteLinkNavigationTarget.Clips
        path.startsWith("my/clips/") -> path.localPathIdAfter("my/clips")
            ?.let { SiteLinkNavigationTarget.ClipDetail(it) }
            ?: SiteLinkNavigationTarget.Clips
        path.startsWith("clips/") -> path.localPathIdAfter("clips")
            ?.let { SiteLinkNavigationTarget.ClipDetail(it) }
            ?: SiteLinkNavigationTarget.Clips
        path == "drive" || path.startsWith("drive/") -> SiteLinkNavigationTarget.Drive
        path == "settings/api" -> SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.ApiTokens)
        path == "settings/webhook" ||
            path == "settings/webhooks" -> SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.Webhooks)
        path == "settings/apps" -> SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.AuthorizedApps)
        path == "settings/shared-access" -> SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.SharedAccess)
        path == "settings/security" && cleanUrl.contains("signin-history", ignoreCase = true) ->
            SiteLinkNavigationTarget.SettingsManagement(SettingsManagementSectionKey.SigninHistory)
        path == "settings" || path.startsWith("settings/") -> SiteLinkNavigationTarget.Settings
        path == "my/profile" -> SiteLinkNavigationTarget.Profile
        path == "my" || path.startsWith("my/") -> SiteLinkNavigationTarget.Profile
        else -> SiteLinkNavigationTarget.External(cleanUrl)
    }
}

private fun String.localUserPagePathTarget(): SiteLinkNavigationTarget.PagePath? {
    val segments = removePrefix("@").split('/')
    val username = segments.getOrNull(0)?.trim().orEmpty()
    val pageName = segments.getOrNull(2)?.trim().orEmpty()
    return if (username.isNotBlank() && segments.getOrNull(1) == "pages" && pageName.isNotBlank()) {
        SiteLinkNavigationTarget.PagePath(username = username, name = pageName)
    } else {
        null
    }
}

private fun String.localPathIdAfter(prefix: String): String? {
    return removePrefix(prefix)
        .trim('/')
        .substringBefore('/')
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun localPath(url: String): String? {
    val localUrlPath = firstMatchingLocalPrefix(url)?.let { prefix ->
        url.removePrefix(prefix)
    } ?: when {
        url.startsWith("/") && !url.startsWith("//") -> url.removePrefix("/")
        else -> return null
    }

    return localUrlPath
        .substringBefore('#')
        .substringBefore('?')
        .trim('/')
}

private fun firstMatchingLocalPrefix(url: String): String? {
    return localUrlPrefixes.firstOrNull { prefix ->
        url.startsWith(prefix, ignoreCase = true)
    }
}

private val localUrlPrefixes = listOf(
    "https://dc.hhhl.cc/",
    "http://dc.hhhl.cc/",
    "https://hhhl.cc/",
    "http://hhhl.cc/",
)
