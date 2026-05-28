package cc.hhhl.client.state

import cc.hhhl.client.api.NoteActionApi
import cc.hhhl.client.api.NoteActionApiResult
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.repository.NoteActionRepository
import cc.hhhl.client.repository.NoteActionRepositoryResult
import cc.hhhl.client.repository.NoteActionRequest
import cc.hhhl.client.repository.EmojiRepository
import cc.hhhl.client.repository.EmojiRepositoryResult
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
class NoteActionStateHolderTest {
    @Test
    fun updateDefaultReactionMovesInstanceDefaultToFirstOption() {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            scope = TestScope(),
        )

        holder.updateDefaultReaction("⭐")

        assertEquals("⭐", holder.state.value.reactionOptions.first())
        assertEquals(holder.state.value.reactionOptions.distinct(), holder.state.value.reactionOptions)
    }

    @Test
    fun loadReactionOptionsMergesDefaultBuiltInsAndCustomEmoji() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            emojiRepository = fakeEmojiRepository(
                EmojiRepositoryResult.Success(
                    reactionOptions = listOf(":blobcat:", "👍", ":party:"),
                    emojiUrls = mapOf(":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp"),
                    customEmojis = listOf(customEmoji("blobcat"), customEmoji("party")),
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateDefaultReaction("⭐")
        holder.loadReactionOptions()
        advanceUntilIdle()

        assertEquals("⭐", holder.state.value.reactionOptions.first())
        assertTrue(holder.state.value.reactionOptions.contains(":blobcat:"))
        assertTrue(holder.state.value.reactionOptions.contains(":party:"))
        assertEquals(holder.state.value.reactionOptions.distinct(), holder.state.value.reactionOptions)
        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", holder.state.value.customEmojiUrls[":blobcat:"])
        assertEquals(listOf("blobcat", "party"), holder.state.value.customEmojis.map { it.name })
        assertEquals(null, holder.state.value.reactionOptionsError)
    }

    @Test
    fun loadReactionOptionsUsesLatestDefaultReactionWhenRequestCompletes() = runTest {
        val pending = CompletableDeferred<EmojiRepositoryResult>()
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            emojiRepository = fakeEmojiRepository { pending.await() },
            scope = TestScope(testScheduler),
        )

        holder.updateDefaultReaction("⭐")
        holder.loadReactionOptions()
        runCurrent()
        holder.updateDefaultReaction("🚀")
        pending.complete(
            EmojiRepositoryResult.Success(
                reactionOptions = listOf(":blobcat:"),
                emojiUrls = emptyMap(),
                customEmojis = emptyList(),
            ),
        )
        advanceUntilIdle()

        assertEquals("🚀", holder.state.value.reactionOptions.first())
        assertTrue(holder.state.value.reactionOptions.contains(":blobcat:"))
        assertFalse(holder.state.value.isLoadingReactionOptions)
    }

    @Test
    fun loadReactionOptionsErrorKeepsBuiltInFallbacks() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            emojiRepository = fakeEmojiRepository(EmojiRepositoryResult.Error("网络请求失败")),
            scope = TestScope(testScheduler),
        )

        holder.loadReactionOptions()
        advanceUntilIdle()

        assertTrue(holder.state.value.reactionOptions.contains(NoteActionRequest.DEFAULT_REACTION))
        assertEquals("网络请求失败", holder.state.value.reactionOptionsError)
    }

    @Test
    fun performMarksPendingAndStoresSuccessMessage() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1"))
        assertTrue(holder.state.value.pendingNoteIds.contains("note-1"))
        advanceUntilIdle()

        assertFalse(holder.state.value.pendingNoteIds.contains("note-1"))
        assertEquals("已发送反应", holder.state.value.message)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun successfulReactionStoresRecentReactionFirst() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1", "👍"))
        advanceUntilIdle()
        holder.perform(NoteActionRequest.React("note-2", ":blobcat:"))
        advanceUntilIdle()
        holder.perform(NoteActionRequest.React("note-3", "👍"))
        advanceUntilIdle()

        assertEquals(listOf("👍", ":blobcat:"), holder.state.value.recentReactions)
    }

    @Test
    fun restoresStoredRecentReactions() {
        val store = InMemoryRecentReactionStore(listOf(":party:", "👍", "", ":party:"))
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            recentReactionStore = store,
            scope = TestScope(),
        )

        holder.restoreRecentReactions()

        assertEquals(listOf(":party:", "👍"), holder.state.value.recentReactions)
    }

    @Test
    fun successfulReactionPersistsRecentReactions() = runTest {
        val store = InMemoryRecentReactionStore()
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Success("已发送反应")),
            recentReactionStore = store,
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1", "👍"))
        advanceUntilIdle()

        assertEquals(listOf("👍"), store.savedRecentReactions)
    }

    @Test
    fun failedReactionDoesNotUpdateRecentReactions() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Error("服务器拒绝")),
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1", "👍"))
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.recentReactions)
    }

    @Test
    fun unauthorizedActionMarksRelogin() = runTest {
        val holder = NoteActionStateHolder(
            repository = fakeRepository(NoteActionRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1"))
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val holder = NoteActionStateHolder(
            repository = sequenceRepository(
                NoteActionRepositoryResult.Unauthorized,
                NoteActionRepositoryResult.Success("已发送反应"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.perform(NoteActionRequest.React("note-1"))
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.perform(NoteActionRequest.React("note-1"))
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("已发送反应", holder.state.value.message)
    }

    private fun fakeRepository(result: NoteActionRepositoryResult): NoteActionRepository {
        return object : NoteActionRepository(
            tokenProvider = { "token-123" },
            api = object : NoteActionApi {
                override suspend fun createReaction(
                    token: String,
                    noteId: String,
                    reaction: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun likeNote(
                    token: String,
                    noteId: String,
                    override: String?,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteReaction(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun createFavorite(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteFavorite(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun votePoll(
                    token: String,
                    noteId: String,
                    choice: Int,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun createRenote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteRenote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun reportNote(
                    token: String,
                    userId: String,
                    noteId: String,
                    comment: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun muteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun unmuteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun muteRenotes(
                    token: String,
                    userId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun unmuteRenotes(
                    token: String,
                    userId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success
            },
        ) {
            override suspend fun perform(request: NoteActionRequest): NoteActionRepositoryResult {
                return result
            }
        }
    }

    private fun fakeEmojiRepository(result: EmojiRepositoryResult): EmojiRepository {
        return fakeEmojiRepository { result }
    }

    private fun fakeEmojiRepository(resultProvider: suspend () -> EmojiRepositoryResult): EmojiRepository {
        return object : EmojiRepository(
            api = object : cc.hhhl.client.api.EmojiApi {
                override suspend fun loadEmojis(): cc.hhhl.client.api.EmojiLoadResult {
                    return cc.hhhl.client.api.EmojiLoadResult.Success(emptyList())
                }
            },
        ) {
            override suspend fun loadReactionOptions(limit: Int): EmojiRepositoryResult {
                return resultProvider()
            }
        }
    }

    private fun sequenceRepository(
        vararg results: NoteActionRepositoryResult,
    ): NoteActionRepository {
        var index = 0
        return object : NoteActionRepository(
            tokenProvider = { "token-123" },
            api = object : NoteActionApi {
                override suspend fun createReaction(
                    token: String,
                    noteId: String,
                    reaction: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun likeNote(
                    token: String,
                    noteId: String,
                    override: String?,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteReaction(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun createFavorite(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteFavorite(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun votePoll(
                    token: String,
                    noteId: String,
                    choice: Int,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun createRenote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteRenote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun deleteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun reportNote(
                    token: String,
                    userId: String,
                    noteId: String,
                    comment: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun muteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun unmuteNote(
                    token: String,
                    noteId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun muteRenotes(
                    token: String,
                    userId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success

                override suspend fun unmuteRenotes(
                    token: String,
                    userId: String,
                ): NoteActionApiResult = NoteActionApiResult.Success
            },
        ) {
            override suspend fun perform(request: NoteActionRequest): NoteActionRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }
        }
    }

    private fun customEmoji(name: String): CustomEmoji {
        return CustomEmoji(
            name = name,
            category = "cat",
            url = "https://dc.hhhl.cc/emoji/$name.webp",
            aliases = emptyList(),
            localOnly = true,
            isSensitive = false,
        )
    }

    private class InMemoryRecentReactionStore(
        private val storedRecentReactions: List<String> = emptyList(),
    ) : RecentReactionStore {
        var savedRecentReactions: List<String> = emptyList()

        override fun loadRecentReactions(): List<String> {
            return storedRecentReactions
        }

        override fun saveRecentReactions(reactions: List<String>) {
            savedRecentReactions = reactions
        }
    }
}
