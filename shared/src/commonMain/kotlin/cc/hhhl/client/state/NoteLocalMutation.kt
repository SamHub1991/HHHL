package cc.hhhl.client.state

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NoteReaction

sealed interface NoteLocalMutation {
    val noteId: String

    data class React(
        override val noteId: String,
        val reaction: String,
    ) : NoteLocalMutation

    data class DeleteReaction(
        override val noteId: String,
        val reaction: String,
    ) : NoteLocalMutation

    data class Renote(override val noteId: String) : NoteLocalMutation

    data class VotePoll(
        override val noteId: String,
        val choice: Int,
    ) : NoteLocalMutation

    data class Favorite(override val noteId: String) : NoteLocalMutation

    data class Unfavorite(override val noteId: String) : NoteLocalMutation

    data class Delete(override val noteId: String) : NoteLocalMutation
}

fun List<Note>.applyNoteLocalMutation(mutation: NoteLocalMutation): List<Note> {
    return when (mutation) {
        is NoteLocalMutation.Delete -> filterNot { it.id == mutation.noteId }
        is NoteLocalMutation.React,
        is NoteLocalMutation.DeleteReaction,
        is NoteLocalMutation.Renote,
        is NoteLocalMutation.VotePoll,
        is NoteLocalMutation.Favorite,
        is NoteLocalMutation.Unfavorite -> map { note ->
            if (note.id == mutation.noteId) note.applyLocalMutation(mutation) else note
        }
    }
}

private fun Note.applyLocalMutation(mutation: NoteLocalMutation): Note {
    return when (mutation) {
        is NoteLocalMutation.React -> {
            val cleanReaction = mutation.reaction.trim()
            if (cleanReaction.isEmpty()) {
                this
            } else if (myReaction == cleanReaction) {
                this
            } else {
                val previousReaction = myReaction?.takeIf { it.isNotBlank() }
                copy(
                    reactionCount = if (previousReaction == null) reactionCount + 1 else reactionCount,
                    reactions = reactions
                        .let { items ->
                            previousReaction?.let { items.decrementReaction(it) } ?: items
                        }
                        .incrementReaction(cleanReaction),
                    myReaction = cleanReaction,
                )
            }
        }
        is NoteLocalMutation.DeleteReaction -> {
            val cleanReaction = mutation.reaction.trim()
            if (cleanReaction.isEmpty()) {
                this
            } else {
                copy(
                    reactionCount = (reactionCount - 1).coerceAtLeast(0),
                    reactions = reactions.decrementReaction(cleanReaction),
                    myReaction = myReaction.takeUnless { it == cleanReaction },
                )
            }
        }
        is NoteLocalMutation.Renote -> copy(renoteCount = renoteCount + 1)
        is NoteLocalMutation.VotePoll -> copy(poll = poll?.applyVote(mutation.choice))
        is NoteLocalMutation.Favorite -> copy(isFavorited = true)
        is NoteLocalMutation.Unfavorite -> copy(isFavorited = false)
        is NoteLocalMutation.Delete -> this
    }
}

private fun NotePoll.applyVote(choice: Int): NotePoll {
    if (choice !in choices.indices) return this
    if (!multiple && choices.any { it.isVoted }) return this
    if (choices[choice].isVoted) return this

    return copy(
        choices = choices.mapIndexed { index, item ->
            if (index == choice) item.copy(votes = item.votes + 1, isVoted = true) else item
        },
    )
}

private fun List<NoteReaction>.incrementReaction(reaction: String): List<NoteReaction> {
    var found = false
    val updated = map { item ->
        if (item.reaction == reaction) {
            found = true
            item.copy(count = item.count + 1)
        } else {
            item
        }
    }
    return if (found) updated else updated + NoteReaction(reaction = reaction, count = 1)
}

private fun List<NoteReaction>.decrementReaction(reaction: String): List<NoteReaction> {
    return mapNotNull { item ->
        if (item.reaction == reaction) {
            val nextCount = item.count - 1
            if (nextCount > 0) item.copy(count = nextCount) else null
        } else {
            item
        }
    }
}
