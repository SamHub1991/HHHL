package cc.hhhl.client.api

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NotePollChoice
import cc.hhhl.client.model.NoteReaction
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.User
import kotlinx.serialization.Serializable

@Serializable
internal data class SharkeyNoteDto(
    val id: String,
    val createdAt: String,
    val text: String? = null,
    val cw: String? = null,
    val visibility: String? = null,
    val replyId: String? = null,
    val channelId: String? = null,
    val channel: SharkeyNoteChannelDto? = null,
    val user: SharkeyUserSummaryDto,
    val renote: SharkeyNoteDto? = null,
    val renoteCount: Int = 0,
    val repliesCount: Int = 0,
    val reactions: Map<String, Int> = emptyMap(),
    val myReaction: String? = null,
    val isFavorited: Boolean = false,
    val poll: SharkeyPollDto? = null,
    val files: List<SharkeyFileDto> = emptyList(),
) {
    fun toDomainNote(): Note {
        val isQuote = renote != null && !text.isNullOrBlank()
        val contentNote = if (isQuote) this else renote ?: this
        return Note(
            id = id,
            author = user.toDomainUser(),
            text = contentNote.text.orEmpty(),
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            createdAt = createdAt,
            visibility = visibility.toNoteVisibility(),
            cw = contentNote.cw,
            media = contentNote.files.map { it.toDomainMedia() },
            replyCount = repliesCount,
            renoteCount = renoteCount,
            reactionCount = reactions.values.sum(),
            reactions = reactions
                .filterValues { it > 0 }
                .map { (reaction, count) -> NoteReaction(reaction = reaction, count = count) }
                .sortedByDescending { it.count },
            myReaction = myReaction?.takeIf { it.isNotBlank() },
            isFavorited = isFavorited,
            poll = contentNote.poll?.toDomainPoll(),
            isRenote = renote != null && text.isNullOrBlank(),
            quotedNote = if (isQuote) renote?.toDomainNote() else null,
            replyId = replyId?.takeIf { it.isNotBlank() },
            channelId = (channelId ?: contentNote.channelId).orEmpty(),
            channelName = (channel?.name ?: contentNote.channel?.name).orEmpty(),
        )
    }
}

@Serializable
internal data class SharkeyNoteChannelDto(
    val id: String = "",
    val name: String = "",
)

@Serializable
internal data class SharkeyUserSummaryDto(
    val id: String,
    val username: String,
    val host: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
) {
    fun toDomainUser(): User {
        val displayName = name?.takeIf { it.isNotBlank() } ?: username
        return User(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.avatarInitial(),
            host = host?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
internal data class SharkeyFileDto(
    val id: String,
    val name: String? = null,
    val comment: String? = null,
    val type: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isSensitive: Boolean = false,
) {
    fun toDomainMedia(): NoteMedia {
        return NoteMedia(
            id = id,
            description = comment?.takeIf { it.isNotBlank() }
                ?: name?.takeIf { it.isNotBlank() }
                ?: type.orEmpty(),
            type = type.orEmpty(),
            url = url?.takeIf { it.isNotBlank() },
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
            isSensitive = isSensitive,
        )
    }
}

@Serializable
internal data class SharkeyPollDto(
    val multiple: Boolean = false,
    val expiresAt: String? = null,
    val choices: List<SharkeyPollChoiceDto> = emptyList(),
) {
    fun toDomainPoll(): NotePoll {
        return NotePoll(
            multiple = multiple,
            expiresAtLabel = expiresAt?.toLocalCompactDateLabel().orEmpty(),
            expiresAt = expiresAt.orEmpty(),
            choices = choices.map { it.toDomainChoice() },
        )
    }
}

@Serializable
internal data class SharkeyPollChoiceDto(
    val text: String = "",
    val votes: Int = 0,
    val isVoted: Boolean = false,
) {
    fun toDomainChoice(): NotePollChoice {
        return NotePollChoice(
            text = text,
            votes = votes,
            isVoted = isVoted,
        )
    }
}

private fun String?.toNoteVisibility(): NoteVisibility {
    return when (this) {
        "home" -> NoteVisibility.Home
        "followers" -> NoteVisibility.Followers
        "specified" -> NoteVisibility.Specified
        else -> NoteVisibility.Public
    }
}

private fun String.avatarInitial(): String {
    return trim().firstOrNull()?.toString()?.uppercase() ?: "?"
}
