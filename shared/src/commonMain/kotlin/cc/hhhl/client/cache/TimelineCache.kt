package cc.hhhl.client.cache

import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.api.toLocalCompactDateLabel
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NotePollChoice
import cc.hhhl.client.model.NoteReaction
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.User
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface TimelineCache {
    suspend fun read(kind: TimelineKind): List<Note>

    suspend fun write(kind: TimelineKind, notes: List<Note>)
}

object NoopTimelineCache : TimelineCache {
    override suspend fun read(kind: TimelineKind): List<Note> {
        return emptyList()
    }

    override suspend fun write(kind: TimelineKind, notes: List<Note>) = Unit
}

class InMemoryTimelineCache : TimelineCache {
    private val snapshots = mutableMapOf<TimelineKind, List<Note>>()
    private val mutex = Mutex()

    override suspend fun read(kind: TimelineKind): List<Note> {
        return mutex.withLock {
            snapshots[kind].orEmpty()
        }
    }

    override suspend fun write(kind: TimelineKind, notes: List<Note>) {
        mutex.withLock {
            snapshots[kind] = notes
        }
    }
}

object TimelineCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(snapshots: Map<TimelineKind, List<Note>>): String {
        return json.encodeToString(
            TimelineCacheEnvelope(
                snapshots = snapshots.toCachedSnapshots(),
            ),
        )
    }

    fun decode(payload: String?): Map<TimelineKind, List<Note>> {
        if (payload.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<TimelineCacheEnvelope>(payload)
                .snapshots
                .mapNotNull { (key, notes) ->
                    val kind = runCatching { TimelineKind.valueOf(key) }.getOrNull()
                    kind?.let { it to notes.map { note -> note.toDomainNote() } }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}

@Serializable
private data class TimelineCacheEnvelope(
    val version: Int = 2,
    val snapshots: Map<String, List<CachedNote>> = emptyMap(),
)

@Serializable
private data class CachedNote(
    val id: String,
    val author: CachedUser,
    val text: String,
    val createdAtLabel: String,
    val createdAt: String = "",
    val visibility: String = NoteVisibility.Public.name,
    val cw: String? = null,
    val media: List<CachedNoteMedia> = emptyList(),
    val replyCount: Int = 0,
    val renoteCount: Int = 0,
    val reactionCount: Int = 0,
    val reactions: List<CachedNoteReaction> = emptyList(),
    val myReaction: String? = null,
    val isFavorited: Boolean = false,
    val poll: CachedNotePoll? = null,
    val isRenote: Boolean = false,
    val quotedNote: CachedNote? = null,
    val replyId: String? = null,
)

@Serializable
private data class CachedUser(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarInitial: String,
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val host: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
)

@Serializable
private data class CachedNoteReaction(
    val reaction: String,
    val count: Int,
)

@Serializable
private data class CachedNoteMedia(
    val id: String,
    val description: String,
    val type: String = "",
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isSensitive: Boolean = false,
)

@Serializable
private data class CachedNotePoll(
    val multiple: Boolean = false,
    val expiresAtLabel: String = "",
    val expiresAt: String = "",
    val choices: List<CachedNotePollChoice> = emptyList(),
)

@Serializable
private data class CachedNotePollChoice(
    val text: String,
    val votes: Int,
    val isVoted: Boolean,
)

private fun Note.toCachedNote(): CachedNote {
    return CachedNote(
        id = id,
        author = author.toCachedUser(),
        text = text,
        createdAtLabel = createdAtLabel,
        createdAt = createdAt,
        visibility = visibility.name,
        cw = cw,
        media = media.map { it.toCachedMedia() },
        replyCount = replyCount,
        renoteCount = renoteCount,
        reactionCount = reactionCount,
        reactions = reactions.map { CachedNoteReaction(reaction = it.reaction, count = it.count) },
        myReaction = myReaction,
        isFavorited = isFavorited,
        poll = poll?.toCachedPoll(),
        isRenote = isRenote,
        quotedNote = quotedNote?.toCachedNote(),
        replyId = replyId,
    )
}

private fun CachedNote.toDomainNote(): Note {
    return Note(
        id = id,
        author = author.toDomainUser(),
        text = text,
        createdAtLabel = createdAt.takeIf { it.isNotBlank() }?.toLocalCompactDateLabel() ?: createdAtLabel,
        createdAt = createdAt,
        visibility = runCatching { NoteVisibility.valueOf(visibility) }.getOrDefault(NoteVisibility.Public),
        cw = cw,
        media = media.map { it.toDomainMedia() },
        replyCount = replyCount,
        renoteCount = renoteCount,
        reactionCount = reactionCount,
        reactions = reactions.map { NoteReaction(reaction = it.reaction, count = it.count) },
        myReaction = myReaction,
        isFavorited = isFavorited,
        poll = poll?.toDomainPoll(),
        isRenote = isRenote,
        quotedNote = quotedNote?.toDomainNote(),
        replyId = replyId,
    )
}

private fun User.toCachedUser(): CachedUser {
    return CachedUser(
        id = id,
        displayName = displayName,
        username = username,
        avatarInitial = avatarInitial,
        bio = bio,
        followersCount = followersCount,
        followingCount = followingCount,
        notesCount = notesCount,
        isFollowing = isFollowing,
        host = host,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
    )
}

private fun CachedUser.toDomainUser(): User {
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
        host = host,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
    )
}

private fun NoteMedia.toCachedMedia(): CachedNoteMedia {
    return CachedNoteMedia(
        id = id,
        description = description,
        type = type,
        url = url,
        thumbnailUrl = thumbnailUrl,
        isSensitive = isSensitive,
    )
}

private fun CachedNoteMedia.toDomainMedia(): NoteMedia {
    return NoteMedia(
        id = id,
        description = description,
        type = type,
        url = url,
        thumbnailUrl = thumbnailUrl,
        isSensitive = isSensitive,
    )
}

private fun NotePoll.toCachedPoll(): CachedNotePoll {
    return CachedNotePoll(
        multiple = multiple,
        expiresAtLabel = expiresAtLabel,
        expiresAt = expiresAt,
        choices = choices.map {
            CachedNotePollChoice(
                text = it.text,
                votes = it.votes,
                isVoted = it.isVoted,
            )
        },
    )
}

private fun CachedNotePoll.toDomainPoll(): NotePoll {
    return NotePoll(
        multiple = multiple,
        expiresAtLabel = expiresAt.takeIf { it.isNotBlank() }?.toLocalCompactDateLabel() ?: expiresAtLabel,
        expiresAt = expiresAt,
        choices = choices.map {
            NotePollChoice(
                text = it.text,
                votes = it.votes,
                isVoted = it.isVoted,
            )
        },
    )
}

private fun Map<TimelineKind, List<Note>>.toCachedSnapshots(): Map<String, List<CachedNote>> {
    return entries.associate { entry ->
        entry.key.name to entry.value.map { it.toCachedNote() }
    }
}
