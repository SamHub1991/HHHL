package cc.hhhl.client.cache

import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class NotificationCacheSnapshot(
    val notifications: List<NotificationItem> = emptyList(),
    val specialCareNotifications: List<NotificationItem> = emptyList(),
)

interface NotificationCache {
    fun read(accountId: String): NotificationCacheSnapshot

    fun write(accountId: String, snapshot: NotificationCacheSnapshot)

    fun clearAccount(accountId: String)
}

object NoopNotificationCache : NotificationCache {
    override fun read(accountId: String): NotificationCacheSnapshot = NotificationCacheSnapshot()

    override fun write(accountId: String, snapshot: NotificationCacheSnapshot) = Unit

    override fun clearAccount(accountId: String) = Unit
}

object NotificationCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(snapshot: NotificationCacheSnapshot): String {
        return json.encodeToString(
            CachedNotificationEnvelope(
                notifications = snapshot.notifications.map { it.toCachedNotification() },
                specialCareNotifications = snapshot.specialCareNotifications.map { it.toCachedNotification() },
            ),
        )
    }

    fun decode(payload: String?): NotificationCacheSnapshot {
        if (payload.isNullOrBlank()) return NotificationCacheSnapshot()
        return runCatching {
            val envelope = json.decodeFromString<CachedNotificationEnvelope>(payload)
            NotificationCacheSnapshot(
                notifications = envelope.notifications.map { it.toDomainNotification() },
                specialCareNotifications = envelope.specialCareNotifications.map { it.toDomainNotification() },
            )
        }.getOrDefault(NotificationCacheSnapshot())
    }
}

@Serializable
private data class CachedNotificationEnvelope(
    val version: Int = 1,
    val notifications: List<CachedNotificationItem> = emptyList(),
    val specialCareNotifications: List<CachedNotificationItem> = emptyList(),
)

@Serializable
private data class CachedNotificationItem(
    val id: String,
    val type: String,
    val actor: CachedNotificationUser,
    val text: String,
    val createdAtLabel: String,
    val noteId: String? = null,
    val notePreviewText: String? = null,
    val isSpecialCare: Boolean = false,
    val chatRoomId: String? = null,
    val chatUserId: String? = null,
    val chatMessageId: String? = null,
    val isRead: Boolean = false,
)

@Serializable
private data class CachedNotificationUser(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarInitial: String,
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val host: String? = null,
)

private fun NotificationItem.toCachedNotification(): CachedNotificationItem {
    return CachedNotificationItem(
        id = id,
        type = type.name,
        actor = actor.toCachedNotificationUser(),
        text = text,
        createdAtLabel = createdAtLabel,
        noteId = noteId,
        notePreviewText = notePreviewText,
        isSpecialCare = isSpecialCare,
        chatRoomId = chatRoomId,
        chatUserId = chatUserId,
        chatMessageId = chatMessageId,
        isRead = isRead,
    )
}

private fun CachedNotificationItem.toDomainNotification(): NotificationItem {
    return NotificationItem(
        id = id,
        type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.Unknown),
        actor = actor.toDomainUser(),
        text = text,
        createdAtLabel = createdAtLabel,
        noteId = noteId,
        notePreviewText = notePreviewText,
        isSpecialCare = isSpecialCare,
        chatRoomId = chatRoomId,
        chatUserId = chatUserId,
        chatMessageId = chatMessageId,
        isRead = isRead,
    )
}

private fun User.toCachedNotificationUser(): CachedNotificationUser {
    return CachedNotificationUser(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = avatarInitial,
        bio = bio,
        followersCount = followersCount,
        followingCount = followingCount,
        notesCount = notesCount,
        isFollowing = isFollowing,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
        host = host,
    )
}

private fun CachedNotificationUser.toDomainUser(): User {
    return User(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = avatarInitial,
        bio = bio,
        followersCount = followersCount,
        followingCount = followingCount,
        notesCount = notesCount,
        isFollowing = isFollowing,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
        host = host,
    )
}
