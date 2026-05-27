package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class User(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarInitial: String,
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val notesCount: Int = 0,
    val isFollowing: Boolean = false,
    val pinnedNotes: List<Note> = emptyList(),
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val host: String? = null,
)
