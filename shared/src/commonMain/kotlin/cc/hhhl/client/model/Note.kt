package cc.hhhl.client.model

data class Note(
    val id: String,
    val author: User,
    val text: String,
    val createdAtLabel: String,
    val createdAt: String = "",
    val visibility: NoteVisibility = NoteVisibility.Public,
    val cw: String? = null,
    val media: List<NoteMedia> = emptyList(),
    val replyCount: Int = 0,
    val renoteCount: Int = 0,
    val reactionCount: Int = 0,
    val reactions: List<NoteReaction> = emptyList(),
    val myReaction: String? = null,
    val isFavorited: Boolean = false,
    val poll: NotePoll? = null,
    val isRenote: Boolean = false,
    val quotedNote: Note? = null,
    val replyId: String? = null,
)

data class NoteReaction(
    val reaction: String,
    val count: Int,
)

data class NoteMedia(
    val id: String,
    val description: String,
    val type: String = "",
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isSensitive: Boolean = false,
)

data class NotePoll(
    val multiple: Boolean,
    val expiresAtLabel: String = "",
    val expiresAt: String = "",
    val choices: List<NotePollChoice>,
)

data class NotePollChoice(
    val text: String,
    val votes: Int,
    val isVoted: Boolean,
)

enum class NoteVisibility {
    Public,
    Home,
    Followers,
    Specified,
}
