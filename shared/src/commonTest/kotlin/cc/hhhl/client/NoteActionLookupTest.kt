package cc.hhhl.client

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Channel
import cc.hhhl.client.state.AntennaUiState
import cc.hhhl.client.state.ChannelUiState
import cc.hhhl.client.state.ClipUiState
import cc.hhhl.client.state.DiscoverUiState
import cc.hhhl.client.state.FavoriteNoteUiState
import cc.hhhl.client.state.NoteDetailUiState
import cc.hhhl.client.state.TimelineUiState
import cc.hhhl.client.state.UserListUiState
import cc.hhhl.client.state.UserProfileUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteActionLookupTest {
    @Test
    fun findsLoadedProfilePinnedNoteForActionState() {
        val pinned = FakeData.timeline[0].copy(isFavorited = true)
        val profileState = UserProfileUiState(
            user = FakeData.me.copy(pinnedNotes = listOf(pinned)),
        )

        val note = loadedNotesForActions(
            timelineState = TimelineUiState(),
            noteDetailState = NoteDetailUiState(),
            userProfileState = profileState,
            viewedProfileState = UserProfileUiState(),
            discoverState = DiscoverUiState(),
            favoriteNoteState = FavoriteNoteUiState(),
            userListState = UserListUiState(),
            antennaState = AntennaUiState(),
            clipState = ClipUiState(),
            channelState = ChannelUiState(),
        ).firstOrNull { it.id == pinned.id }

        assertEquals(true, note?.isFavorited)
    }

    @Test
    fun findsLoadedChannelPinnedNoteForActionState() {
        val pinned = FakeData.timeline[0].copy(isFavorited = true)
        val channel = sampleChannel("channel-1").copy(pinnedNotes = listOf(pinned))

        val note = loadedNotesForActions(
            timelineState = TimelineUiState(),
            noteDetailState = NoteDetailUiState(),
            userProfileState = UserProfileUiState(),
            viewedProfileState = UserProfileUiState(),
            discoverState = DiscoverUiState(),
            favoriteNoteState = FavoriteNoteUiState(),
            userListState = UserListUiState(),
            antennaState = AntennaUiState(),
            clipState = ClipUiState(),
            channelState = ChannelUiState(
                channels = listOf(channel),
                selectedChannel = channel,
            ),
        ).firstOrNull { it.id == pinned.id }

        assertEquals(true, note?.isFavorited)
    }

    private fun sampleChannel(id: String): Channel {
        return Channel(
            id = id,
            name = "Channel $id",
            description = "desc",
            color = "#40c057",
            userId = "user-1",
            bannerUrl = null,
            pinnedNoteIds = emptyList(),
            pinnedNotes = emptyList(),
            isArchived = false,
            isSensitive = false,
            allowRenoteToExternal = true,
            isFollowing = false,
            isFavorited = false,
            hasUnreadNote = false,
            usersCount = 4,
            notesCount = 12,
            createdAtLabel = "2026-05-25 07:00",
            lastNotedAtLabel = "2026-05-25 08:00",
        )
    }
}
