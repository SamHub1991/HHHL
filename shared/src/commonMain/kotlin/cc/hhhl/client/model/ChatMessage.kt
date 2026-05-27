package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatMessage(
    val id: String,
    val roomId: String,
    val fromUser: User,
    val text: String,
    val createdAtLabel: String,
    val createdAt: String = "",
    val toUserId: String? = null,
    val toUser: User? = null,
    val isRead: Boolean = true,
    val file: DriveFile? = null,
    val reactions: List<ChatMessageReaction> = emptyList(),
    val reactionCount: Int = reactions.sumOf { it.count },
    val reply: ChatMessageReference? = null,
    val quote: ChatMessageReference? = null,
    val replyUnavailable: Boolean = false,
    val quoteUnavailable: Boolean = false,
)

@Immutable
data class ChatMessageQuote(
    val messageId: String,
    val authorName: String,
    val previewText: String,
)

@Immutable
data class ChatMessageReaction(
    val reaction: String,
    val count: Int,
    val users: List<User> = emptyList(),
)

@Immutable
data class ChatMessageReference(
    val id: String,
    val fromUser: User?,
    val text: String,
    val file: DriveFile? = null,
    val unavailable: Boolean = false,
)
