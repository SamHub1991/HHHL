package cc.hhhl.client.model

data class ChatMessage(
    val id: String,
    val roomId: String,
    val fromUser: User,
    val text: String,
    val createdAtLabel: String,
    val createdAt: String = "",
    val file: DriveFile? = null,
    val reactions: List<ChatMessageReaction> = emptyList(),
    val reactionCount: Int = reactions.sumOf { it.count },
)

data class ChatMessageQuote(
    val messageId: String,
    val authorName: String,
    val previewText: String,
)

data class ChatMessageReaction(
    val reaction: String,
    val count: Int,
)
