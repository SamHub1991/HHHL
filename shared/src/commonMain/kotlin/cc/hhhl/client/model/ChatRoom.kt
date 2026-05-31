package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

const val CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX = "active:"

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
    val memberLimit: Int = 0,
    val memberLimitOverride: Int? = null,
    val canManage: Boolean = false,
    val messageRetentionDays: Int? = null,
)

@Immutable
data class ChatRoomMember(
    val membershipId: String,
    val roomId: String,
    val user: User,
    val joinedAtLabel: String,
)

@Immutable
data class ChatRoomInvitation(
    val id: String,
    val room: ChatRoom,
    val inviter: User? = null,
    val createdAtLabel: String = "",
)
