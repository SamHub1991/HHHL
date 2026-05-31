package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class NoteSearchTrends(
    val popularSearches: List<String> = emptyList(),
    val recentTerms: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = popularSearches.isEmpty() && recentTerms.isEmpty() && hashtags.isEmpty()
}

@Immutable
data class DiscoverySections(
    val searchTrends: NoteSearchTrends = NoteSearchTrends(),
    val coverNotes: List<Note> = emptyList(),
    val hotNotes: List<Note> = emptyList(),
    val tutorialNotes: List<Note> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val users: List<User> = emptyList(),
) {
    val isEmpty: Boolean
        get() = searchTrends.isEmpty &&
            coverNotes.isEmpty() &&
            hotNotes.isEmpty() &&
            tutorialNotes.isEmpty() &&
            channels.isEmpty() &&
            users.isEmpty()
}
