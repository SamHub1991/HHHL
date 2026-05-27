package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatRoom(
    val id: String,
    val membershipId: String,
    val name: String,
    val description: String,
    val joinMode: String,
    val memberCount: Int,
    val isMuted: Boolean,
    val owner: User,
    val unreadCount: Int = 0,
    val latestMessageAtLabel: String = "",
    val latestMessageMarker: String = "",
)

@Immutable
data class ChatRoomMember(
    val membershipId: String,
    val roomId: String,
    val user: User,
    val joinedAtLabel: String,
)
