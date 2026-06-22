package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelCategory
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.repository.ChannelRepository
import cc.hhhl.client.repository.ChannelActionRepositoryResult
import cc.hhhl.client.repository.ChannelCategoriesRepositoryResult
import cc.hhhl.client.repository.ChannelMutationRepositoryResult
import cc.hhhl.client.repository.ChannelTimelineRepositoryResult
import cc.hhhl.client.repository.ChannelsRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelStateHolderTest {
    @Test
    fun refreshChannelsStoresChannelsAndLoadsFirstTimeline() = runTest {
        val channel = sampleChannel("channel-1")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        assertTrue(holder.state.value.isLoadingChannels)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingChannels)
        assertFalse(holder.state.value.isLoadingTimeline)
        assertEquals(listOf(channel), holder.state.value.channels)
        assertEquals(channel, holder.state.value.selectedChannel)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun refreshChannelsAlsoLoadsCategories() = runTest {
        val category = ChannelCategory(name = "AI与大模型", channelsCount = 13)
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                categoriesResult = ChannelCategoriesRepositoryResult.Success(listOf(category)),
                channelsResult = ChannelsRepositoryResult.Success(listOf(sampleChannel("channel-1"))),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingCategories)
        assertEquals(listOf(category), holder.state.value.categories)
    }

    @Test
    fun selectKindLoadsThatKindChannels() = runTest {
        val featured = sampleChannel("channel-featured")
        val favorite = sampleChannel("channel-favorite")
        val calls = mutableListOf<ChannelListKind>()
        val holder = ChannelStateHolder(
            repository = sequenceRepository(
                channelResults = listOf(
                    ChannelsRepositoryResult.Success(listOf(featured)),
                    ChannelsRepositoryResult.Success(listOf(favorite)),
                ),
                timelineResult = ChannelTimelineRepositoryResult.Success(emptyList()),
                onRefreshChannels = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.selectKind(ChannelListKind.Favorites)
        advanceUntilIdle()

        assertEquals(listOf(ChannelListKind.Featured, ChannelListKind.Favorites), calls)
        assertEquals(ChannelListKind.Favorites, holder.state.value.selectedKind)
        assertEquals(listOf(favorite), holder.state.value.channels)
        assertEquals(favorite, holder.state.value.selectedChannel)
    }

    @Test
    fun selectCategoryLoadsThatCategoryChannels() = runTest {
        val category = ChannelCategory(name = "AI与大模型", channelsCount = 13)
        val featured = sampleChannel("channel-featured")
        val categorized = sampleChannel("channel-category").copy(category = category.name)
        val note = FakeData.timeline[0]
        val categoryCalls = mutableListOf<ChannelCategory>()
        val timelineCalls = mutableListOf<String>()
        val holder = ChannelStateHolder(
            repository = sequenceRepository(
                channelResults = listOf(ChannelsRepositoryResult.Success(listOf(featured))),
                categoryChannelsResult = ChannelsRepositoryResult.Success(listOf(categorized)),
                categoriesResult = ChannelCategoriesRepositoryResult.Success(listOf(category)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                onRefreshChannelsByCategory = { categoryCalls.add(it) },
                onRefreshTimeline = { timelineCalls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.selectCategory(category)
        assertTrue(holder.state.value.isLoadingChannels)
        advanceUntilIdle()

        assertEquals(category, holder.state.value.selectedCategory)
        assertEquals(listOf(categorized), holder.state.value.channels)
        assertEquals(categorized, holder.state.value.selectedChannel)
        assertEquals(listOf(note), holder.state.value.notes)
        assertEquals(listOf(category), categoryCalls)
        assertEquals("channel-category", timelineCalls.last())
    }

    @Test
    fun refreshCurrentChannelsUsesSelectedCategoryWhenPresent() = runTest {
        val category = ChannelCategory(name = "AI与大模型", channelsCount = 13)
        val featured = sampleChannel("channel-featured")
        val categorized = sampleChannel("channel-category").copy(category = category.name)
        val kindCalls = mutableListOf<ChannelListKind>()
        val categoryCalls = mutableListOf<ChannelCategory>()
        val holder = ChannelStateHolder(
            repository = sequenceRepository(
                channelResults = listOf(ChannelsRepositoryResult.Success(listOf(featured))),
                categoryChannelsResult = ChannelsRepositoryResult.Success(listOf(categorized)),
                categoriesResult = ChannelCategoriesRepositoryResult.Success(listOf(category)),
                timelineResult = ChannelTimelineRepositoryResult.Success(emptyList()),
                onRefreshChannels = { kindCalls.add(it) },
                onRefreshChannelsByCategory = { categoryCalls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.selectCategory(category)
        advanceUntilIdle()
        holder.refreshCurrentChannels()
        advanceUntilIdle()

        assertEquals(listOf(ChannelListKind.Featured), kindCalls)
        assertEquals(listOf(category, category), categoryCalls)
        assertEquals(category, holder.state.value.selectedCategory)
        assertEquals(listOf(categorized), holder.state.value.channels)
    }

    @Test
    fun selectChannelLoadsSelectedTimeline() = runTest {
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val calls = mutableListOf<String>()
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                onRefreshTimeline = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.selectChannel(second)
        assertTrue(holder.state.value.isLoadingTimeline)
        advanceUntilIdle()

        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(listOf("channel-1", "channel-2"), calls)
    }

    @Test
    fun pendingChannelRefreshDoesNotOverwriteNewlySelectedChannel() = runTest {
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val note = FakeData.timeline[0]
        val pendingRefresh = CompletableDeferred<ChannelsRepositoryResult>()
        var refreshCount = 0
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                refreshChannelsResultProvider = {
                    refreshCount += 1
                    if (refreshCount == 1) {
                        ChannelsRepositoryResult.Success(listOf(first, second))
                    } else {
                        pendingRefresh.await()
                    }
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        assertEquals(first, holder.state.value.selectedChannel)

        holder.refreshChannels()
        runCurrent()
        holder.selectChannel(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedChannel)

        pendingRefresh.complete(ChannelsRepositoryResult.Success(listOf(first, second)))
        advanceUntilIdle()

        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun loadMoreAppendsTimelineAndMarksEndReached() = runTest {
        val channel = sampleChannel("channel-1")
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(first)),
                loadMoreResult = ChannelTimelineRepositoryResult.Success(
                    notes = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertTrue(holder.state.value.endReached)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun applyNoteMutationUpdatesChannelTimeline() = runTest {
        val channel = sampleChannel("channel-1")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.Delete(note.id))

        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun applyNoteMutationUpdatesSelectedChannelPinnedNotes() = runTest {
        val pinnedNote = FakeData.timeline[0]
        val channel = sampleChannel("channel-1").copy(pinnedNotes = listOf(pinnedNote))
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                timelineResult = ChannelTimelineRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.Favorite(pinnedNote.id))

        assertEquals(true, holder.state.value.selectedChannel?.pinnedNotes?.single()?.isFavorited)
        assertEquals(true, holder.state.value.channels.single().pinnedNotes.single().isFavorited)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val channel = sampleChannel("channel-1")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = sequenceRepository(
                channelResults = listOf(
                    ChannelsRepositoryResult.Success(listOf(channel)),
                    ChannelsRepositoryResult.Unauthorized,
                ),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.refreshChannels()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun unauthorizedChannelLoadMarksRelogin() = runTest {
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorizedChannelLoad() = runTest {
        val channel = sampleChannel("channel-1")
        val holder = ChannelStateHolder(
            repository = sequenceRepository(
                channelResults = listOf(
                    ChannelsRepositoryResult.Unauthorized,
                    ChannelsRepositoryResult.Success(listOf(channel)),
                ),
                timelineResult = ChannelTimelineRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refreshChannels()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(channel), holder.state.value.channels)
        assertEquals(channel, holder.state.value.selectedChannel)
    }

    @Test
    fun toggleFollowFollowsSelectedChannelAndUpdatesLists() = runTest {
        val channel = sampleChannel("channel-1").copy(isFollowing = false, usersCount = 4)
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                actionResult = ChannelActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.toggleFollowSelectedChannel()
        assertTrue(holder.state.value.isChangingFollow)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingFollow)
        assertEquals(true, holder.state.value.selectedChannel?.isFollowing)
        assertEquals(5, holder.state.value.selectedChannel?.usersCount)
        assertEquals(true, holder.state.value.channels.single().isFollowing)
    }

    @Test
    fun toggleFavoriteFavoritesSelectedChannelAndUpdatesLists() = runTest {
        val channel = sampleChannel("channel-1").copy(isFavorited = false)
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                actionResult = ChannelActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.toggleFavoriteSelectedChannel()
        assertTrue(holder.state.value.isChangingFavorite)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingFavorite)
        assertEquals(true, holder.state.value.selectedChannel?.isFavorited)
        assertEquals(true, holder.state.value.channels.single().isFavorited)
    }

    @Test
    fun createChannelPrependsAndSelectsCreatedChannel() = runTest {
        val existing = sampleChannel("channel-old")
        val created = sampleChannel("channel-new").copy(name = "新频道")
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(existing)),
                mutationResult = ChannelMutationRepositoryResult.Success(created),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.createChannel(ChannelDraft(name = " 新频道 "))
        assertTrue(holder.state.value.isMutatingChannel)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingChannel)
        assertEquals(listOf(created, existing), holder.state.value.channels)
        assertEquals(created, holder.state.value.selectedChannel)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun updateSelectedChannelReplacesSelectedAndListItem() = runTest {
        val channel = sampleChannel("channel-1")
        val updated = channel.copy(name = "改名频道", isSensitive = true)
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(channel)),
                mutationResult = ChannelMutationRepositoryResult.Success(updated),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.updateSelectedChannel(ChannelDraft(name = "改名频道", isSensitive = true))
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingChannel)
        assertEquals(updated, holder.state.value.selectedChannel)
        assertEquals(updated, holder.state.value.channels.single())
    }

    @Test
    fun pendingUpdateErrorDoesNotShowOnNewlySelectedChannel() = runTest {
        val updateResult = CompletableDeferred<ChannelMutationRepositoryResult>()
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                updateResultProvider = { _, _ -> updateResult.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.updateSelectedChannel(ChannelDraft(name = "改名频道"))
        runCurrent()

        assertTrue(holder.state.value.isMutatingChannel)

        holder.selectChannel(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)

        updateResult.complete(ChannelMutationRepositoryResult.Error("频道更新失败"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingChannel)
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun pendingFollowErrorDoesNotShowOnNewlySelectedChannel() = runTest {
        val actionResult = CompletableDeferred<ChannelActionRepositoryResult>()
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                actionResultProvider = { actionResult.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.toggleFollowSelectedChannel()
        runCurrent()

        assertTrue(holder.state.value.isChangingFollow)

        holder.selectChannel(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)

        actionResult.complete(ChannelActionRepositoryResult.Error("关注频道失败"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingFollow)
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun pendingFavoriteErrorDoesNotShowOnNewlySelectedChannel() = runTest {
        val actionResult = CompletableDeferred<ChannelActionRepositoryResult>()
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                actionResultProvider = { actionResult.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.toggleFavoriteSelectedChannel()
        runCurrent()

        assertTrue(holder.state.value.isChangingFavorite)

        holder.selectChannel(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)

        actionResult.complete(ChannelActionRepositoryResult.Error("收藏频道失败"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingFavorite)
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun archiveSelectedChannelRemovesItFromCurrentList() = runTest {
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                mutationResult = ChannelMutationRepositoryResult.Success(first.copy(isArchived = true)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.archiveSelectedChannel()
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingChannel)
        assertEquals(listOf(second), holder.state.value.channels)
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun pendingArchiveDoesNotClearNewlySelectedChannel() = runTest {
        val archiveResult = CompletableDeferred<ChannelMutationRepositoryResult>()
        val first = sampleChannel("channel-1")
        val second = sampleChannel("channel-2")
        val note = FakeData.timeline[0]
        val holder = ChannelStateHolder(
            repository = fakeRepository(
                channelsResult = ChannelsRepositoryResult.Success(listOf(first, second)),
                timelineResult = ChannelTimelineRepositoryResult.Success(listOf(note)),
                archiveResultProvider = { archiveResult.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshChannels()
        advanceUntilIdle()
        holder.archiveSelectedChannel()
        runCurrent()

        assertTrue(holder.state.value.isMutatingChannel)

        holder.selectChannel(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(listOf(note), holder.state.value.notes)

        archiveResult.complete(ChannelMutationRepositoryResult.Success(first.copy(isArchived = true)))
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingChannel)
        assertEquals(listOf(second), holder.state.value.channels)
        assertEquals(second, holder.state.value.selectedChannel)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    private fun fakeRepository(
        categoriesResult: ChannelCategoriesRepositoryResult = ChannelCategoriesRepositoryResult.Success(emptyList()),
        channelsResult: ChannelsRepositoryResult,
        categoryChannelsResult: ChannelsRepositoryResult = channelsResult,
        timelineResult: ChannelTimelineRepositoryResult = ChannelTimelineRepositoryResult.Success(emptyList()),
        loadMoreResult: ChannelTimelineRepositoryResult = timelineResult,
        actionResult: ChannelActionRepositoryResult = ChannelActionRepositoryResult.Success,
        mutationResult: ChannelMutationRepositoryResult = ChannelMutationRepositoryResult.Success(sampleChannel("channel-mutated")),
        onRefreshCategories: () -> Unit = {},
        onRefreshChannels: (ChannelListKind) -> Unit = {},
        onRefreshChannelsByCategory: (ChannelCategory) -> Unit = {},
        onRefreshTimeline: (String) -> Unit = {},
        refreshCategoriesResultProvider: (suspend () -> ChannelCategoriesRepositoryResult)? = null,
        refreshChannelsResultProvider: (suspend (ChannelListKind) -> ChannelsRepositoryResult)? = null,
        refreshCategoryChannelsResultProvider: (suspend (ChannelCategory) -> ChannelsRepositoryResult)? = null,
        actionResultProvider: suspend (String) -> ChannelActionRepositoryResult = { actionResult },
        updateResultProvider: suspend (String, ChannelDraft) -> ChannelMutationRepositoryResult = { _, _ -> mutationResult },
        archiveResultProvider: suspend (Channel) -> ChannelMutationRepositoryResult = { mutationResult },
    ): ChannelRepository {
        return sequenceRepository(
            channelResults = listOf(channelsResult),
            categoriesResult = categoriesResult,
            categoryChannelsResult = categoryChannelsResult,
            timelineResult = timelineResult,
            loadMoreResult = loadMoreResult,
            actionResult = actionResult,
            mutationResult = mutationResult,
            onRefreshCategories = onRefreshCategories,
            onRefreshChannels = onRefreshChannels,
            onRefreshChannelsByCategory = onRefreshChannelsByCategory,
            onRefreshTimeline = onRefreshTimeline,
            refreshCategoriesResultProvider = refreshCategoriesResultProvider,
            refreshChannelsResultProvider = refreshChannelsResultProvider,
            refreshCategoryChannelsResultProvider = refreshCategoryChannelsResultProvider,
            actionResultProvider = actionResultProvider,
            updateResultProvider = updateResultProvider,
            archiveResultProvider = archiveResultProvider,
        )
    }

    private fun sequenceRepository(
        channelResults: List<ChannelsRepositoryResult>,
        categoriesResult: ChannelCategoriesRepositoryResult = ChannelCategoriesRepositoryResult.Success(emptyList()),
        categoryChannelsResult: ChannelsRepositoryResult = channelResults.firstOrNull()
            ?: ChannelsRepositoryResult.Success(emptyList()),
        timelineResult: ChannelTimelineRepositoryResult,
        loadMoreResult: ChannelTimelineRepositoryResult = timelineResult,
        actionResult: ChannelActionRepositoryResult = ChannelActionRepositoryResult.Success,
        mutationResult: ChannelMutationRepositoryResult = ChannelMutationRepositoryResult.Success(sampleChannel("channel-mutated")),
        onRefreshCategories: () -> Unit = {},
        onRefreshChannels: (ChannelListKind) -> Unit = {},
        onRefreshChannelsByCategory: (ChannelCategory) -> Unit = {},
        onRefreshTimeline: (String) -> Unit = {},
        refreshCategoriesResultProvider: (suspend () -> ChannelCategoriesRepositoryResult)? = null,
        refreshChannelsResultProvider: (suspend (ChannelListKind) -> ChannelsRepositoryResult)? = null,
        refreshCategoryChannelsResultProvider: (suspend (ChannelCategory) -> ChannelsRepositoryResult)? = null,
        actionResultProvider: suspend (String) -> ChannelActionRepositoryResult = { actionResult },
        updateResultProvider: suspend (String, ChannelDraft) -> ChannelMutationRepositoryResult = { _, _ -> mutationResult },
        archiveResultProvider: suspend (Channel) -> ChannelMutationRepositoryResult = { mutationResult },
    ): ChannelRepository {
        var channelResultIndex = 0
        return object : ChannelRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ChannelApi {
                override suspend fun loadChannelCategories(): cc.hhhl.client.api.ChannelCategoryLoadResult {
                    return cc.hhhl.client.api.ChannelCategoryLoadResult.Success(emptyList())
                }

                override suspend fun loadChannels(
                    token: String,
                    kind: ChannelListKind,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.ChannelLoadResult {
                    return cc.hhhl.client.api.ChannelLoadResult.Success(emptyList())
                }

                override suspend fun loadChannelsByCategory(
                    category: String?,
                    uncategorized: Boolean,
                    limit: Int,
                    offset: Int,
                ): cc.hhhl.client.api.ChannelLoadResult {
                    return cc.hhhl.client.api.ChannelLoadResult.Success(emptyList())
                }

                override suspend fun loadChannelTimeline(
                    token: String,
                    channelId: String,
                    limit: Int,
                    untilId: String?,
                    withRenotes: Boolean,
                    withFiles: Boolean,
                ): cc.hhhl.client.api.ChannelTimelineLoadResult {
                    return cc.hhhl.client.api.ChannelTimelineLoadResult.Success(emptyList())
                }

                override suspend fun followChannel(
                    token: String,
                    channelId: String,
                ): cc.hhhl.client.api.ChannelActionResult {
                    return cc.hhhl.client.api.ChannelActionResult.Success
                }

                override suspend fun unfollowChannel(
                    token: String,
                    channelId: String,
                ): cc.hhhl.client.api.ChannelActionResult {
                    return cc.hhhl.client.api.ChannelActionResult.Success
                }

                override suspend fun favoriteChannel(
                    token: String,
                    channelId: String,
                ): cc.hhhl.client.api.ChannelActionResult {
                    return cc.hhhl.client.api.ChannelActionResult.Success
                }

                override suspend fun unfavoriteChannel(
                    token: String,
                    channelId: String,
                ): cc.hhhl.client.api.ChannelActionResult {
                    return cc.hhhl.client.api.ChannelActionResult.Success
                }

                override suspend fun createChannel(
                    token: String,
                    draft: ChannelDraft,
                ): cc.hhhl.client.api.ChannelMutationResult {
                    return cc.hhhl.client.api.ChannelMutationResult.Success(sampleChannel("created"))
                }

                override suspend fun updateChannel(
                    token: String,
                    channelId: String,
                    draft: ChannelDraft,
                ): cc.hhhl.client.api.ChannelMutationResult {
                    return cc.hhhl.client.api.ChannelMutationResult.Success(sampleChannel(channelId))
                }
            },
        ) {
            override suspend fun refreshCategories(): ChannelCategoriesRepositoryResult {
                onRefreshCategories()
                return refreshCategoriesResultProvider?.invoke() ?: categoriesResult
            }

            override suspend fun refreshChannels(kind: ChannelListKind): ChannelsRepositoryResult {
                onRefreshChannels(kind)
                refreshChannelsResultProvider?.let { provider -> return provider(kind) }
                val result = channelResults.getOrElse(channelResultIndex) { channelResults.last() }
                channelResultIndex += 1
                return result
            }

            override suspend fun refreshChannelsByCategory(category: ChannelCategory): ChannelsRepositoryResult {
                onRefreshChannelsByCategory(category)
                return refreshCategoryChannelsResultProvider?.invoke(category) ?: categoryChannelsResult
            }

            override suspend fun refreshTimeline(channelId: String): ChannelTimelineRepositoryResult {
                onRefreshTimeline(channelId)
                return timelineResult
            }

            override suspend fun loadMoreTimeline(
                channelId: String,
                currentNotes: List<Note>,
            ): ChannelTimelineRepositoryResult {
                return loadMoreResult
            }

            override suspend fun followChannel(channelId: String): ChannelActionRepositoryResult {
                return actionResultProvider(channelId)
            }

            override suspend fun unfollowChannel(channelId: String): ChannelActionRepositoryResult {
                return actionResultProvider(channelId)
            }

            override suspend fun favoriteChannel(channelId: String): ChannelActionRepositoryResult {
                return actionResultProvider(channelId)
            }

            override suspend fun unfavoriteChannel(channelId: String): ChannelActionRepositoryResult {
                return actionResultProvider(channelId)
            }

            override suspend fun createChannel(draft: ChannelDraft): ChannelMutationRepositoryResult {
                return mutationResult
            }

            override suspend fun updateChannel(
                channelId: String,
                draft: ChannelDraft,
            ): ChannelMutationRepositoryResult {
                return updateResultProvider(channelId, draft)
            }

            override suspend fun archiveChannel(channel: Channel): ChannelMutationRepositoryResult {
                return archiveResultProvider(channel)
            }
        }
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
