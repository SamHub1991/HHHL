package cc.hhhl.client.model

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
)

data class ChatRoomMember(
    val membershipId: String,
    val roomId: String,
    val user: User,
    val joinedAtLabel: String,
)
