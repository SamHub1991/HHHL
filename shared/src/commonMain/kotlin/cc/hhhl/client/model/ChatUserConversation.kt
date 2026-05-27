package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatUserConversation(
    val user: User,
    val latestMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
)
