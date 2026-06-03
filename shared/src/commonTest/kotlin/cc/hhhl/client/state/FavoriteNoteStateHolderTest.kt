package cc.hhhl.client.state

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.FavoriteNoteRepository
import cc.hhhl.client.repository.FavoriteNotesRepositoryResult
import cc.hhhl.client.repository.sampleFavoriteNote
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
class FavoriteNoteStateHolderTest {
    @Test
    fun refreshStoresFavoriteNotes() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(favorite), holder.state.value.favorites)
    }

    @Test
    fun loadMoreAppendsFavorites() = runTest {
        val first = sampleFavoriteNote("fav-1")
        val second = sampleFavoriteNote("fav-2")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(first)),
                loadMoreResult = FavoriteNotesRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(listOf(first, second), holder.state.value.favorites)
    }

    @Test
    fun applyNoteMutationUpdatesFavoriteNotePayloads() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.React(favorite.note.id, "👍"))

        assertEquals(1, holder.state.value.favorites.single().note.reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun applyUnfavoriteMutationRemovesFavoriteFromCurrentList() = runTest {
        val favorite = sampleFavoriteNote("fav-1").copy(
            note = sampleFavoriteNote("fav-1").note.copy(isFavorited = true),
        )
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.Unfavorite(favorite.note.id))

        assertEquals(emptyList(), holder.state.value.favorites)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(
                result = FavoriteNotesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = sequenceRepository(
                FavoriteNotesRepositoryResult.Unauthorized,
                FavoriteNotesRepositoryResult.Success(listOf(favorite)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(favorite), holder.state.value.favorites)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val favorite = sampleFavoriteNote("fav-1")
        val holder = FavoriteNoteStateHolder(
            repository = sequenceRepository(
                FavoriteNotesRepositoryResult.Success(listOf(favorite)),
                FavoriteNotesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(favorite.note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.favorites.single().note.reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun localFavoriteMessageChangeInvalidatesPendingRestore() = runTest {
        val pendingRead = CompletableDeferred<List<FavoriteMessage>>()
        val store = BlockingFavoriteMessageStore(readProvider = { pendingRead.await() })
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(FavoriteNotesRepositoryResult.Success(emptyList())),
            favoriteMessageStore = store,
            accountIdProvider = { "account-1" },
            scope = TestScope(testScheduler),
        )

        holder.restoreFavoriteMessages(force = true)
        runCurrent()
        holder.addFavoriteMessage(
            conversationType = FavoriteMessageConversationType.Room,
            conversationId = "room-1",
            conversationTitle = "Room",
            message = sampleChatMessage("new-message"),
        )

        pendingRead.complete(listOf(sampleFavoriteMessage("old-message")))
        advanceUntilIdle()

        assertEquals(listOf("new-message"), holder.state.value.favoriteMessages.map { it.message.id })
        assertFalse(holder.state.value.isLoadingFavoriteMessages)
    }

    @Test
    fun staleFavoriteMessageSaveDoesNotOverwriteNewerSnapshot() = runTest {
        val firstSaveCanFinish = CompletableDeferred<Unit>()
        val store = BlockingFavoriteMessageStore(
            saveGate = { saveIndex -> if (saveIndex == 1) firstSaveCanFinish.await() },
        )
        val holder = FavoriteNoteStateHolder(
            repository = fakeRepository(FavoriteNotesRepositoryResult.Success(emptyList())),
            favoriteMessageStore = store,
            accountIdProvider = { "account-1" },
            scope = TestScope(testScheduler),
        )

        holder.addFavoriteMessage(
            conversationType = FavoriteMessageConversationType.Room,
            conversationId = "room-1",
            conversationTitle = "Room",
            message = sampleChatMessage("message-1"),
        )
        runCurrent()
        holder.addFavoriteMessage(
            conversationType = FavoriteMessageConversationType.Room,
            conversationId = "room-1",
            conversationTitle = "Room",
            message = sampleChatMessage("message-2"),
        )
        runCurrent()

        firstSaveCanFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("message-2", "message-1"), store.savedMessages.map { it.message.id })
    }

    private fun fakeRepository(
        result: FavoriteNotesRepositoryResult,
        loadMoreResult: FavoriteNotesRepositoryResult = result,
    ): FavoriteNoteRepository {
        return object : FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FavoriteNoteApi {
                override suspend fun loadFavorites(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FavoriteNoteLoadResult {
                    return cc.hhhl.client.api.FavoriteNoteLoadResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun refresh(): FavoriteNotesRepositoryResult = result

            override suspend fun loadMore(currentFavorites: List<cc.hhhl.client.model.FavoriteNote>): FavoriteNotesRepositoryResult {
                return loadMoreResult
            }
        }
    }

    private fun sequenceRepository(
        vararg results: FavoriteNotesRepositoryResult,
    ): FavoriteNoteRepository {
        var index = 0
        return object : FavoriteNoteRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.FavoriteNoteApi {
                override suspend fun loadFavorites(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.FavoriteNoteLoadResult {
                    return cc.hhhl.client.api.FavoriteNoteLoadResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun refresh(): FavoriteNotesRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }

            override suspend fun loadMore(currentFavorites: List<cc.hhhl.client.model.FavoriteNote>): FavoriteNotesRepositoryResult {
                return results.last()
            }
        }
    }

    private class BlockingFavoriteMessageStore(
        private val readProvider: suspend (String) -> List<FavoriteMessage> = { emptyList() },
        private val saveGate: suspend (Int) -> Unit = {},
    ) : FavoriteMessageStore {
        private var saveCount = 0
        var savedMessages: List<FavoriteMessage> = emptyList()
            private set

        override suspend fun read(accountId: String): List<FavoriteMessage> {
            return readProvider(accountId)
        }

        override suspend fun save(accountId: String, messages: List<FavoriteMessage>) {
            saveCount += 1
            saveGate(saveCount)
            savedMessages = messages
        }

        override suspend fun clearAccount(accountId: String) = Unit
    }

    private fun sampleFavoriteMessage(messageId: String): FavoriteMessage {
        return FavoriteMessage(
            id = favoriteMessageId("account-1", FavoriteMessageConversationType.Room, "room-1", messageId),
            accountId = "account-1",
            conversationType = FavoriteMessageConversationType.Room,
            conversationId = "room-1",
            conversationTitle = "Room",
            message = sampleChatMessage(messageId),
            savedAtEpochMillis = 1,
        )
    }

    private fun sampleChatMessage(messageId: String): ChatMessage {
        return ChatMessage(
            id = messageId,
            roomId = "room-1",
            fromUser = User("user-1", "Alice", "alice", "A"),
            text = messageId,
            createdAtLabel = "now",
        )
    }
}
