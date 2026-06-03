package cc.hhhl.client.state

import cc.hhhl.client.api.UserProfileLoadResult
import cc.hhhl.client.api.UserNotesApi
import cc.hhhl.client.api.UserNotesLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.repository.UserNotesRepository
import cc.hhhl.client.repository.UserNotesRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import cc.hhhl.client.repository.UserRelationshipRepository
import cc.hhhl.client.repository.UserRelationshipRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileStateHolderTest {
    @Test
    fun loadStoresProfileUserAndNotes() = runTest {
        val note = FakeData.timeline[0]
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(FakeData.me)),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(FakeData.me, holder.state.value.user)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun unauthorizedLoadMarksRelogin() = runTest {
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun unauthorizedReloadDoesNotRefreshNotesForStaleUser() = runTest {
        var noteRefreshes = 0
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Success(FakeData.me),
                UserProfileRepositoryResult.Unauthorized,
            ),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(FakeData.timeline[0])),
                onRefresh = { noteRefreshes += 1 },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.load()
        advanceUntilIdle()

        assertEquals(1, noteRefreshes)
        assertTrue(holder.state.value.requiresRelogin)
    }

    @Test
    fun successfulReloadClearsReloginAfterUnauthorized() = runTest {
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Unauthorized,
                UserProfileRepositoryResult.Success(FakeData.me),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.load()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(FakeData.me, holder.state.value.user)
    }

    @Test
    fun clearContentLoadAllowsNewProfileRequestAndIgnoresOlderResult() = runTest {
        val firstUser = FakeData.me.copy(id = "remote-a", displayName = "Remote A")
        val secondUser = FakeData.me.copy(id = "remote-b", displayName = "Remote B")
        val firstResult = CompletableDeferred<UserProfileRepositoryResult>()
        val secondResult = CompletableDeferred<UserProfileRepositoryResult>()
        val holder = UserProfileStateHolder(
            repository = deferredSequenceRepository(firstResult, secondResult),
            scope = TestScope(testScheduler),
        )

        holder.load(clearContent = true)
        holder.load(clearContent = true)
        secondResult.complete(UserProfileRepositoryResult.Success(secondUser))
        advanceUntilIdle()
        firstResult.complete(UserProfileRepositoryResult.Success(firstUser))
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(secondUser, holder.state.value.user)
    }

    @Test
    fun loadMoreNotesUsesCurrentNotes() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(FakeData.me)),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(first)),
                loadMoreResult = UserNotesRepositoryResult.Success(listOf(first, second)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.loadMoreNotes()
        assertTrue(holder.state.value.isLoadingMoreNotes)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMoreNotes)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun clearContentLoadClearsPendingLoadMoreNotesState() = runTest {
        val firstUser = FakeData.me.copy(id = "remote-a", displayName = "Remote A")
        val secondUser = FakeData.me.copy(id = "remote-b", displayName = "Remote B")
        val firstNote = FakeData.timeline[0].copy(id = "note-a")
        val pendingMore = CompletableDeferred<UserNotesRepositoryResult>()
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Success(firstUser),
                UserProfileRepositoryResult.Success(secondUser),
            ),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(firstNote)),
                loadMoreResultProvider = { pendingMore.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.loadMoreNotes()
        assertTrue(holder.state.value.isLoadingMoreNotes)

        holder.load(clearContent = true)
        assertFalse(holder.state.value.isLoadingMoreNotes)
        pendingMore.complete(UserNotesRepositoryResult.Success(listOf(firstNote, FakeData.timeline[1])))
        advanceUntilIdle()

        assertEquals(secondUser, holder.state.value.user)
        assertFalse(holder.state.value.isLoadingMoreNotes)
    }

    @Test
    fun applyNoteMutationUpdatesProfileNotes() = runTest {
        val note = FakeData.timeline[0]
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(FakeData.me)),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.Renote(note.id))

        assertEquals(note.renoteCount + 1, holder.state.value.notes.single().renoteCount)
    }

    @Test
    fun applyNoteMutationUpdatesPinnedProfileNotes() = runTest {
        val pinnedNote = FakeData.timeline[0]
        val user = FakeData.me.copy(pinnedNotes = listOf(pinnedNote))
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.applyNoteMutation(NoteLocalMutation.React(pinnedNote.id, "❤️"))

        assertEquals("❤️", holder.state.value.user?.pinnedNotes?.single()?.myReaction)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val note = FakeData.timeline[0]
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Success(FakeData.me),
                UserProfileRepositoryResult.Unauthorized,
            ),
            notesRepository = fakeNotesRepository(
                refreshResult = UserNotesRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.load()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun loadStoresRelationshipForRemoteProfile() = runTest {
        val user = FakeData.me.copy(id = "remote-1")
        val relationship = UserRelationship(userId = "remote-1", isMuted = true, isBlocking = false)
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            relationshipRepository = fakeRelationshipRepository(
                result = UserRelationshipRepositoryResult.Success,
                relationResult = UserRelationshipRepositoryResult.RelationLoaded(relationship),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()

        assertEquals(relationship, holder.state.value.relationship)
        assertFalse(holder.state.value.isRelationshipLoading)
    }

    @Test
    fun toggleFollowFollowsUnfollowedUserAndUpdatesCounts() = runTest {
        val user = FakeData.me.copy(id = "remote-1", isFollowing = false, followersCount = 4)
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            relationshipRepository = fakeRelationshipRepository(UserRelationshipRepositoryResult.Success),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.toggleFollow()
        assertTrue(holder.state.value.isRelationshipChanging)
        advanceUntilIdle()

        assertFalse(holder.state.value.isRelationshipChanging)
        assertEquals(true, holder.state.value.user?.isFollowing)
        assertEquals(5, holder.state.value.user?.followersCount)
    }

    @Test
    fun toggleFollowUnfollowsFollowedUserAndUpdatesCounts() = runTest {
        val user = FakeData.me.copy(id = "remote-1", isFollowing = true, followersCount = 4)
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            relationshipRepository = fakeRelationshipRepository(UserRelationshipRepositoryResult.Success),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.toggleFollow()
        advanceUntilIdle()

        assertEquals(false, holder.state.value.user?.isFollowing)
        assertEquals(3, holder.state.value.user?.followersCount)
    }

    @Test
    fun pendingReportDoesNotWriteMessageAfterProfileSwitch() = runTest {
        val firstUser = FakeData.me.copy(id = "remote-a", displayName = "Remote A")
        val secondUser = FakeData.me.copy(id = "remote-b", displayName = "Remote B")
        val pendingReport = CompletableDeferred<UserRelationshipRepositoryResult>()
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Success(firstUser),
                UserProfileRepositoryResult.Success(secondUser),
            ),
            relationshipRepository = fakeRelationshipRepository(
                result = UserRelationshipRepositoryResult.Success,
                actionResultProvider = { pendingReport.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.reportUser()
        assertTrue(holder.state.value.isRelationshipChanging)

        holder.load(clearContent = true)
        assertFalse(holder.state.value.isRelationshipChanging)
        pendingReport.complete(UserRelationshipRepositoryResult.Success)
        advanceUntilIdle()

        assertEquals(secondUser, holder.state.value.user)
        assertFalse(holder.state.value.isRelationshipChanging)
        assertEquals(null, holder.state.value.message)
    }

    @Test
    fun toggleMuteUpdatesRelationship() = runTest {
        val user = FakeData.me.copy(id = "remote-1")
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            relationshipRepository = fakeRelationshipRepository(
                result = UserRelationshipRepositoryResult.Success,
                relationResult = UserRelationshipRepositoryResult.RelationLoaded(
                    UserRelationship(userId = "remote-1", isMuted = false),
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.toggleMute()
        assertTrue(holder.state.value.isRelationshipChanging)
        advanceUntilIdle()

        assertFalse(holder.state.value.isRelationshipChanging)
        assertEquals(true, holder.state.value.relationship?.isMuted)
    }

    @Test
    fun toggleBlockUpdatesRelationshipAndFollowState() = runTest {
        val user = FakeData.me.copy(id = "remote-1", isFollowing = true, followersCount = 4)
        val holder = UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(user)),
            relationshipRepository = fakeRelationshipRepository(
                result = UserRelationshipRepositoryResult.Success,
                relationResult = UserRelationshipRepositoryResult.RelationLoaded(
                    UserRelationship(userId = "remote-1", isFollowing = true, isBlocking = false),
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.toggleBlock()
        advanceUntilIdle()

        assertEquals(true, holder.state.value.relationship?.isBlocking)
        assertEquals(false, holder.state.value.user?.isFollowing)
        assertEquals(3, holder.state.value.user?.followersCount)
    }

    @Test
    fun updateProfileSavesNameAndBioWithoutDroppingCounts() = runTest {
        val original = FakeData.me.copy(
            displayName = "Alice",
            bio = "old",
            followersCount = 42,
            pinnedNotes = listOf(FakeData.timeline[0]),
        )
        val updated = original.copy(
            displayName = "Alice New",
            avatarInitial = "A",
            bio = "new bio",
            followersCount = 0,
            pinnedNotes = emptyList(),
        )
        val holder = UserProfileStateHolder(
            repository = fakeRepository(
                result = UserProfileRepositoryResult.Success(original),
                updateResult = UserProfileRepositoryResult.Success(updated),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.updateProfile("Alice New", "new bio")
        assertTrue(holder.state.value.isProfileSaving)
        advanceUntilIdle()

        assertFalse(holder.state.value.isProfileSaving)
        assertEquals("Alice New", holder.state.value.user?.displayName)
        assertEquals("new bio", holder.state.value.user?.bio)
        assertEquals(42, holder.state.value.user?.followersCount)
        assertEquals(listOf(FakeData.timeline[0]), holder.state.value.user?.pinnedNotes)
        assertEquals("资料已保存", holder.state.value.message)
    }

    @Test
    fun updateProfileErrorStaysInEditState() = runTest {
        val holder = UserProfileStateHolder(
            repository = fakeRepository(
                result = UserProfileRepositoryResult.Success(FakeData.me),
                updateResult = UserProfileRepositoryResult.Error("保存失败"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.updateProfile("Alice", "bio")
        advanceUntilIdle()

        assertFalse(holder.state.value.isProfileSaving)
        assertEquals("保存失败", holder.state.value.profileEditErrorMessage)
        assertFalse(holder.state.value.requiresRelogin)
    }

    @Test
    fun updateProfileWithoutChangesSkipsRepositoryAndShowsMessage() = runTest {
        var updates = 0
        val holder = UserProfileStateHolder(
            repository = fakeRepository(
                result = UserProfileRepositoryResult.Success(FakeData.me),
                onUpdate = { updates += 1 },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.updateProfile(" ${FakeData.me.displayName} ", " ${FakeData.me.bio} ")
        advanceUntilIdle()

        assertEquals(0, updates)
        assertFalse(holder.state.value.isProfileSaving)
        assertEquals("资料没有变化", holder.state.value.message)
    }

    @Test
    fun pendingProfileUpdateDoesNotOverwriteAfterClearContentLoad() = runTest {
        val firstUser = FakeData.me.copy(id = "remote-a", displayName = "Remote A", bio = "old")
        val secondUser = FakeData.me.copy(id = "remote-b", displayName = "Remote B", bio = "second")
        val pendingUpdate = CompletableDeferred<UserProfileRepositoryResult>()
        val holder = UserProfileStateHolder(
            repository = sequenceRepository(
                UserProfileRepositoryResult.Success(firstUser),
                UserProfileRepositoryResult.Success(secondUser),
                updateResultProvider = { pendingUpdate.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()
        holder.updateProfile("Remote A updated", "new")
        assertTrue(holder.state.value.isProfileSaving)

        holder.load(clearContent = true)
        assertFalse(holder.state.value.isProfileSaving)
        pendingUpdate.complete(
            UserProfileRepositoryResult.Success(
                firstUser.copy(displayName = "Remote A updated", bio = "new"),
            ),
        )
        advanceUntilIdle()

        assertEquals(secondUser, holder.state.value.user)
        assertFalse(holder.state.value.isProfileSaving)
        assertEquals(null, holder.state.value.message)
    }

    private fun fakeRepository(
        result: UserProfileRepositoryResult,
        updateResult: UserProfileRepositoryResult = result,
        updateResultProvider: suspend () -> UserProfileRepositoryResult = { updateResult },
        onUpdate: () -> Unit = {},
    ): UserProfileRepository {
        return object : UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = object : cc.hhhl.client.api.UserProfileApi {
                override suspend fun loadProfile(
                    token: String,
                    userId: String,
                ): UserProfileLoadResult = UserProfileLoadResult.Success(FakeData.me)
            },
        ) {
            override suspend fun load(): UserProfileRepositoryResult {
                return result
            }

            override suspend fun updateProfile(
                name: String,
                description: String,
            ): UserProfileRepositoryResult {
                onUpdate()
                return updateResultProvider()
            }
        }
    }

    private fun sequenceRepository(
        vararg results: UserProfileRepositoryResult,
        updateResultProvider: suspend () -> UserProfileRepositoryResult = { results.last() },
    ): UserProfileRepository {
        var index = 0
        return object : UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = object : cc.hhhl.client.api.UserProfileApi {
                override suspend fun loadProfile(
                    token: String,
                    userId: String,
                ): UserProfileLoadResult = UserProfileLoadResult.Success(FakeData.me)
            },
        ) {
            override suspend fun load(): UserProfileRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }

            override suspend fun updateProfile(
                name: String,
                description: String,
            ): UserProfileRepositoryResult {
                return updateResultProvider()
            }
        }
    }

    private fun deferredSequenceRepository(
        vararg results: CompletableDeferred<UserProfileRepositoryResult>,
    ): UserProfileRepository {
        var index = 0
        return object : UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = object : cc.hhhl.client.api.UserProfileApi {
                override suspend fun loadProfile(
                    token: String,
                    userId: String,
                ): UserProfileLoadResult = UserProfileLoadResult.Success(FakeData.me)
            },
        ) {
            override suspend fun load(): UserProfileRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result.await()
            }
        }
    }

    private fun fakeNotesRepository(
        refreshResult: UserNotesRepositoryResult,
        loadMoreResult: UserNotesRepositoryResult = refreshResult,
        loadMoreResultProvider: suspend () -> UserNotesRepositoryResult = { loadMoreResult },
        onRefresh: () -> Unit = {},
    ): UserNotesRepository {
        return object : UserNotesRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = object : UserNotesApi {
                override suspend fun loadUserNotes(
                    token: String,
                    userId: String,
                    limit: Int,
                    untilId: String?,
                ): UserNotesLoadResult = UserNotesLoadResult.Success(emptyList())
            },
        ) {
            override suspend fun refresh(): UserNotesRepositoryResult {
                onRefresh()
                return refreshResult
            }

            override suspend fun loadMore(currentNotes: List<Note>): UserNotesRepositoryResult {
                return loadMoreResultProvider()
            }
        }
    }

    private fun fakeRelationshipRepository(
        result: UserRelationshipRepositoryResult,
        relationResult: UserRelationshipRepositoryResult = UserRelationshipRepositoryResult.RelationLoaded(
            UserRelationship(userId = "remote-1"),
        ),
        actionResultProvider: suspend () -> UserRelationshipRepositoryResult = { result },
    ): UserRelationshipRepository {
        return object : UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.UserRelationshipApi {
                override suspend fun loadRelation(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipLoadResult {
                    return cc.hhhl.client.api.UserRelationshipLoadResult.Success(UserRelationship(userId))
                }

                override suspend fun loadMutedUsers(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.UserRelationshipListResult {
                    return cc.hhhl.client.api.UserRelationshipListResult.Success(emptyList())
                }

                override suspend fun loadBlockedUsers(
                    token: String,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.UserRelationshipListResult {
                    return cc.hhhl.client.api.UserRelationshipListResult.Success(emptyList())
                }

                override suspend fun follow(
                    token: String,
                    userId: String,
                    withReplies: Boolean?,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun unfollow(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun updateFollowing(
                    token: String,
                    userId: String,
                    notify: String?,
                    withReplies: Boolean?,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun updateAllFollowing(
                    token: String,
                    notify: String?,
                    withReplies: Boolean?,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun invalidateFollowing(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun mute(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun unmute(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun block(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun unblock(
                    token: String,
                    userId: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success

                override suspend fun reportUser(
                    token: String,
                    userId: String,
                    comment: String,
                ): cc.hhhl.client.api.UserRelationshipResult = cc.hhhl.client.api.UserRelationshipResult.Success
            },
        ) {
            override suspend fun loadRelation(userId: String): UserRelationshipRepositoryResult {
                return relationResult
            }

            override suspend fun follow(userId: String, withReplies: Boolean?): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun unfollow(userId: String): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun mute(userId: String): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun unmute(userId: String): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun block(userId: String): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun unblock(userId: String): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }

            override suspend fun reportUser(
                userId: String,
                comment: String,
            ): UserRelationshipRepositoryResult {
                return actionResultProvider()
            }
        }
    }
}
