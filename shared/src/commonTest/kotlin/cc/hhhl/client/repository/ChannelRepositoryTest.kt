package cc.hhhl.client.repository

import cc.hhhl.client.api.ChannelApi
import cc.hhhl.client.api.ChannelActionResult
import cc.hhhl.client.api.ChannelLoadResult
import cc.hhhl.client.api.ChannelMutationResult
import cc.hhhl.client.api.ChannelTimelineLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ChannelRepositoryTest {
    @Test
    fun refreshChannelsUsesTokenAndKind() = runTest {
        val channels = listOf(sampleChannel("channel-1"))
        val calls = mutableListOf<ChannelCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                channelCalls = calls,
                channelResult = ChannelLoadResult.Success(channels),
            ),
        )

        val result = repository.refreshChannels(ChannelListKind.Favorites)

        assertIs<ChannelsRepositoryResult.Success>(result)
        assertEquals(listOf(ChannelCall("token-123", ChannelListKind.Favorites, null)), calls)
        assertEquals(channels, result.channels)
    }

    @Test
    fun refreshTimelineUsesTokenAndChannelId() = runTest {
        val calls = mutableListOf<TimelineCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                timelineCalls = calls,
                timelineResult = ChannelTimelineLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refreshTimeline("channel-1")

        assertIs<ChannelTimelineRepositoryResult.Success>(result)
        assertEquals(listOf(TimelineCall("token-123", "channel-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreTimelineUsesLastNoteIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val calls = mutableListOf<TimelineCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                timelineCalls = calls,
                timelineResult = ChannelTimelineLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMoreTimeline(
            channelId = "channel-1",
            currentNotes = listOf(first),
        )

        assertIs<ChannelTimelineRepositoryResult.Success>(result)
        assertEquals(listOf(TimelineCall("token-123", "channel-1", first.id)), calls)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ChannelRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<ChannelsRepositoryResult.Unauthorized>(repository.refreshChannels(ChannelListKind.Featured))
        assertIs<ChannelTimelineRepositoryResult.Unauthorized>(repository.refreshTimeline("channel-1"))
        assertEquals(0, calls)
    }

    @Test
    fun followAndUnfollowChannelUseTokenAndChannelId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = ChannelActionResult.Success,
            ),
        )

        assertEquals(ChannelActionRepositoryResult.Success, repository.followChannel("channel-1"))
        assertEquals(ChannelActionRepositoryResult.Success, repository.unfollowChannel("channel-1"))
        assertEquals(
            listOf(
                ActionCall("follow", "token-123", "channel-1"),
                ActionCall("unfollow", "token-123", "channel-1"),
            ),
            calls,
        )
    }

    @Test
    fun favoriteAndUnfavoriteChannelUseTokenAndChannelId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = ChannelActionResult.Success,
            ),
        )

        assertEquals(ChannelActionRepositoryResult.Success, repository.favoriteChannel("channel-1"))
        assertEquals(ChannelActionRepositoryResult.Success, repository.unfavoriteChannel("channel-1"))
        assertEquals(
            listOf(
                ActionCall("favorite", "token-123", "channel-1"),
                ActionCall("unfavorite", "token-123", "channel-1"),
            ),
            calls,
        )
    }

    @Test
    fun createAndUpdateChannelUseTokenAndDraft() = runTest {
        val created = sampleChannel("channel-created")
        val updated = sampleChannel("channel-updated")
        val calls = mutableListOf<MutationCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                createResult = ChannelMutationResult.Success(created),
                updateResult = ChannelMutationResult.Success(updated),
            ),
        )
        val draft = ChannelDraft(name = "频道", description = "desc", color = "#40c057")

        val createResult = repository.createChannel(draft)
        val updateResult = repository.updateChannel("channel-1", draft)

        assertIs<ChannelMutationRepositoryResult.Success>(createResult)
        assertIs<ChannelMutationRepositoryResult.Success>(updateResult)
        assertEquals(created, createResult.channel)
        assertEquals(updated, updateResult.channel)
        assertEquals(
            listOf(
                MutationCall("create", "token-123", null, draft),
                MutationCall("update", "token-123", "channel-1", draft),
            ),
            calls,
        )
    }

    @Test
    fun archiveChannelUsesUpdateWithArchivedDraft() = runTest {
        val archived = sampleChannel("channel-1").copy(isArchived = true)
        val calls = mutableListOf<MutationCall>()
        val repository = ChannelRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                updateResult = ChannelMutationResult.Success(archived),
            ),
        )

        val result = repository.archiveChannel(sampleChannel("channel-1"))

        assertIs<ChannelMutationRepositoryResult.Success>(result)
        assertEquals(true, result.channel.isArchived)
        assertEquals("update", calls.single().action)
        assertEquals("channel-1", calls.single().channelId)
        assertEquals(true, calls.single().draft.isArchived)
    }

    private fun fakeApi(
        channelCalls: MutableList<ChannelCall> = mutableListOf(),
        timelineCalls: MutableList<TimelineCall> = mutableListOf(),
        actionCalls: MutableList<ActionCall> = mutableListOf(),
        mutationCalls: MutableList<MutationCall> = mutableListOf(),
        channelResult: ChannelLoadResult = ChannelLoadResult.Success(emptyList()),
        timelineResult: ChannelTimelineLoadResult = ChannelTimelineLoadResult.Success(emptyList()),
        actionResult: ChannelActionResult = ChannelActionResult.Success,
        createResult: ChannelMutationResult = ChannelMutationResult.Success(sampleChannel("created")),
        updateResult: ChannelMutationResult = ChannelMutationResult.Success(sampleChannel("updated")),
        onCall: () -> Unit = {},
    ): ChannelApi {
        return object : ChannelApi {
            override suspend fun loadChannels(
                token: String,
                kind: ChannelListKind,
                limit: Int,
                untilId: String?,
            ): ChannelLoadResult {
                onCall()
                channelCalls.add(ChannelCall(token, kind, untilId))
                return channelResult
            }

            override suspend fun loadChannelTimeline(
                token: String,
                channelId: String,
                limit: Int,
                untilId: String?,
                withRenotes: Boolean,
                withFiles: Boolean,
            ): ChannelTimelineLoadResult {
                onCall()
                timelineCalls.add(TimelineCall(token, channelId, untilId))
                return timelineResult
            }

            override suspend fun followChannel(
                token: String,
                channelId: String,
            ): ChannelActionResult {
                actionCalls.add(ActionCall("follow", token, channelId))
                return actionResult
            }

            override suspend fun unfollowChannel(
                token: String,
                channelId: String,
            ): ChannelActionResult {
                actionCalls.add(ActionCall("unfollow", token, channelId))
                return actionResult
            }

            override suspend fun favoriteChannel(
                token: String,
                channelId: String,
            ): ChannelActionResult {
                actionCalls.add(ActionCall("favorite", token, channelId))
                return actionResult
            }

            override suspend fun unfavoriteChannel(
                token: String,
                channelId: String,
            ): ChannelActionResult {
                actionCalls.add(ActionCall("unfavorite", token, channelId))
                return actionResult
            }

            override suspend fun createChannel(
                token: String,
                draft: ChannelDraft,
            ): ChannelMutationResult {
                mutationCalls.add(MutationCall("create", token, null, draft))
                return createResult
            }

            override suspend fun updateChannel(
                token: String,
                channelId: String,
                draft: ChannelDraft,
            ): ChannelMutationResult {
                mutationCalls.add(MutationCall("update", token, channelId, draft))
                return updateResult
            }
        }
    }

    private fun sampleChannel(id: String): Channel {
        return Channel(
            id = id,
            name = "公告频道",
            description = "站内公告",
            color = "#40c057",
            userId = "user-1",
            bannerUrl = "https://dc.hhhl.cc/banner.webp",
            pinnedNoteIds = listOf("pinned-1"),
            pinnedNotes = emptyList(),
            isArchived = false,
            isSensitive = false,
            allowRenoteToExternal = true,
            isFollowing = true,
            isFavorited = false,
            hasUnreadNote = true,
            usersCount = 4,
            notesCount = 12,
            createdAtLabel = "2026-05-25 07:00",
            lastNotedAtLabel = "2026-05-25 08:00",
        )
    }

    private data class ChannelCall(
        val token: String,
        val kind: ChannelListKind,
        val untilId: String?,
    )

    private data class TimelineCall(
        val token: String,
        val channelId: String,
        val untilId: String?,
    )

    private data class ActionCall(
        val action: String,
        val token: String,
        val channelId: String,
    )

    private data class MutationCall(
        val action: String,
        val token: String,
        val channelId: String?,
        val draft: ChannelDraft,
    )
}
