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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun updateAvatarRejectsEmptyFile() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val emptyUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(0),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(emptyUpload)
        advanceUntilIdle()

        assertEquals("文件内容为空", holder.state.value.profileEditErrorMessage)
        assertFalse(holder.state.value.isProfileSaving)
    }

    @Test
    fun updateAvatarRejectsOversizedFile() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val oversizedUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(AVATAR_MAX_FILE_SIZE_BYTES.toInt() + 1),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(oversizedUpload)
        advanceUntilIdle()

        assertEquals("文件大小超过限制（最大 5MB）", holder.state.value.profileEditErrorMessage)
        assertFalse(holder.state.value.isProfileSaving)
    }

    @Test
    fun updateAvatarRejectsInvalidContentType() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val invalidUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "application/pdf",
            fileName = "document.pdf",
        )
        holder.updateAvatar(invalidUpload)
        advanceUntilIdle()

        assertEquals("不支持的文件格式，仅允许 JPG、PNG、GIF、WebP", holder.state.value.profileEditErrorMessage)
        assertFalse(holder.state.value.isProfileSaving)
    }

    @Test
    fun updateAvatarAcceptsValidContentTypes() = runTest {
        val validTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp")
        for (contentType in validTypes) {
            val holder = createAvatarTestHolder()
            holder.load()
            advanceUntilIdle()

            val validUpload = cc.hhhl.client.api.DriveFileUpload(
                bytes = ByteArray(1024),
                contentType = contentType,
                fileName = "avatar.${contentType.substringAfterLast("/")}",
            )
            holder.updateAvatar(validUpload)
            advanceUntilIdle()

            // 应该通过文件校验，不会显示格式错误
            assertFalse(holder.state.value.profileEditErrorMessage?.contains("不支持的文件格式") == true)
        }
    }

    @Test
    fun updateAvatarEnforcesCooldownPeriod() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val firstUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar1.png",
        )
        holder.updateAvatar(firstUpload)
        runCurrent()

        // 第一次上传应该成功
        assertEquals(AVATAR_UPLOAD_COOLDOWN_SECONDS, holder.state.value.avatarUploadCooldownSeconds)

        // 立即尝试第二次上传，应该被冷却时间阻止
        val secondUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar2.png",
        )
        holder.updateAvatar(secondUpload)
        runCurrent()

        // 应该显示冷却时间错误
        assertTrue(holder.state.value.profileEditErrorMessage?.contains("请等待") == true)
    }

    @Test
    fun updateAvatarEnforcesDailyLimit() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        // 模拟达到每日上传限制
        repeat(AVATAR_DAILY_UPLOAD_LIMIT) { index ->
            val upload = cc.hhhl.client.api.DriveFileUpload(
                bytes = ByteArray(1024),
                contentType = "image/png",
                fileName = "avatar$index.png",
            )
            holder.updateAvatar(upload)
            advanceUntilIdle()
            // 等待冷却时间结束（模拟时间流逝）
            advanceTimeBy(AVATAR_UPLOAD_COOLDOWN_SECONDS * 1000L + 1000L)
        }

        // 尝试第 6 次上传，应该被限制
        val extraUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar_extra.png",
        )
        holder.updateAvatar(extraUpload)
        advanceUntilIdle()

        // 应该显示达到上限的错误
        assertTrue(holder.state.value.profileEditErrorMessage?.contains("今日头像上传次数已达上限") == true)
    }

    @Test
    fun updateAvatarDecrementsRemainingCount() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        assertEquals(AVATAR_DAILY_UPLOAD_LIMIT, holder.state.value.avatarDailyUploadRemaining)

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(upload)
        advanceUntilIdle()

        // 上传成功后，剩余次数应该减少
        assertEquals(AVATAR_DAILY_UPLOAD_LIMIT - 1, holder.state.value.avatarDailyUploadRemaining)
    }

    @Test
    fun updateAvatarDeletesOldAvatarOnSuccess() = runTest {
        var deletedFileIds = mutableListOf<String>()
        val holder = createAvatarTestHolder(
            onDeleteFile = { fileId -> deletedFileIds.add(fileId) }
        )
        holder.load()
        advanceUntilIdle()

        // 第一次上传
        val firstUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar1.png",
        )
        holder.updateAvatar(firstUpload)
        advanceUntilIdle()
        advanceTimeBy(AVATAR_UPLOAD_COOLDOWN_SECONDS * 1000L + 1000L)

        // 第二次上传
        val secondUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar2.png",
        )
        holder.updateAvatar(secondUpload)
        advanceUntilIdle()

        // 应该删除了第一次上传的头像
        assertEquals(1, deletedFileIds.size)
    }

    @Test
    fun updateAvatarCompressesImageWhenProcessorAvailable() = runTest {
        var compressedBytes: ByteArray? = null
        val testProcessor = object : cc.hhhl.client.media.ImageProcessor {
            override suspend fun compressImage(
                imageData: ByteArray,
                contentType: String,
                maxWidth: Int,
                maxHeight: Int,
                quality: Int,
            ): cc.hhhl.client.media.ImageProcessResult {
                compressedBytes = imageData
                // 返回压缩后的数据（模拟压缩）
                return cc.hhhl.client.media.ImageProcessResult(
                    bytes = ByteArray(imageData.size / 2),
                    contentType = contentType,
                    width = maxWidth,
                    height = maxHeight,
                )
            }

            override suspend fun cropToSquare(
                imageData: ByteArray,
                contentType: String,
                size: Int,
            ): cc.hhhl.client.media.ImageProcessResult {
                return cc.hhhl.client.media.ImageProcessResult(
                    bytes = imageData,
                    contentType = contentType,
                    width = size,
                    height = size,
                )
            }
        }

        val holder = createAvatarTestHolder(imageProcessor = testProcessor)
        holder.load()
        advanceUntilIdle()

        val originalBytes = ByteArray(2048)
        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = originalBytes,
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(upload)
        advanceUntilIdle()

        // 验证压缩被调用
        assertNotNull(compressedBytes)
        assertEquals(2048, compressedBytes!!.size)
    }

    @Test
    fun setPendingAvatarStoresValidUpload() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.setPendingAvatar(upload)

        // 验证待确认状态已设置
        assertEquals(upload, holder.state.value.pendingAvatarUpload)
    }

    @Test
    fun setPendingAvatarRejectsInvalidUpload() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        // 空文件应被拒绝
        val emptyUpload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(0),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.setPendingAvatar(emptyUpload)

        // 验证待确认状态未设置，且显示错误信息
        assertEquals(null, holder.state.value.pendingAvatarUpload)
        assertNotNull(holder.state.value.profileEditErrorMessage)
    }

    @Test
    fun confirmPendingAvatarCropsImageWhenProcessorAvailable() = runTest {
        var cropCalled = false
        val testProcessor = object : cc.hhhl.client.media.ImageProcessor {
            override suspend fun compressImage(
                imageData: ByteArray,
                contentType: String,
                maxWidth: Int,
                maxHeight: Int,
                quality: Int,
            ): cc.hhhl.client.media.ImageProcessResult {
                return cc.hhhl.client.media.ImageProcessResult(
                    bytes = imageData,
                    contentType = contentType,
                    width = maxWidth,
                    height = maxHeight,
                )
            }

            override suspend fun cropToSquare(
                imageData: ByteArray,
                contentType: String,
                size: Int,
            ): cc.hhhl.client.media.ImageProcessResult {
                cropCalled = true
                return cc.hhhl.client.media.ImageProcessResult(
                    bytes = imageData,
                    contentType = contentType,
                    width = size,
                    height = size,
                )
            }
        }

        val holder = createAvatarTestHolder(imageProcessor = testProcessor)
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(2048),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.setPendingAvatar(upload)
        holder.confirmPendingAvatar()
        advanceUntilIdle()

        // 验证裁剪被调用
        assertTrue(cropCalled)
        // 验证待确认状态已清除
        assertEquals(null, holder.state.value.pendingAvatarUpload)
    }

    @Test
    fun confirmPendingAvatarUploadsOriginalWhenNoProcessor() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(2048),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.setPendingAvatar(upload)
        holder.confirmPendingAvatar()
        advanceUntilIdle()

        // 验证待确认状态已清除
        assertEquals(null, holder.state.value.pendingAvatarUpload)
    }

    @Test
    fun cancelPendingAvatarClearsState() = runTest {
        val holder = createAvatarTestHolder()
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.setPendingAvatar(upload)
        assertEquals(upload, holder.state.value.pendingAvatarUpload)

        holder.cancelPendingAvatar()

        // 验证待确认状态已清除
        assertEquals(null, holder.state.value.pendingAvatarUpload)
    }

    @Test
    fun updateAvatarCreatesAvatarFolder() = runTest {
        var createdFolderName: String? = null
        val holder = createAvatarTestHolder(
            onCreateFolder = { name -> createdFolderName = name }
        )
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(upload)
        advanceUntilIdle()

        // 验证文件夹被创建
        assertEquals(AVATAR_FOLDER_NAME, createdFolderName)
    }

    @Test
    fun updateAvatarUploadsToAvatarFolder() = runTest {
        var uploadedFolderId: String? = null
        val holder = createAvatarTestHolder(
            onUpload = { upload -> uploadedFolderId = upload.folderId }
        )
        holder.load()
        advanceUntilIdle()

        val upload = cc.hhhl.client.api.DriveFileUpload(
            bytes = ByteArray(1024),
            contentType = "image/png",
            fileName = "avatar.png",
        )
        holder.updateAvatar(upload)
        advanceUntilIdle()

        // 验证上传到头像文件夹
        assertNotNull(uploadedFolderId)
        assertTrue(uploadedFolderId!!.isNotBlank())
    }

    private fun TestScope.createAvatarTestHolder(
        onDeleteFile: (String) -> Unit = {},
        onCreateFolder: (String) -> Unit = {},
        onUpload: (cc.hhhl.client.api.DriveFileUpload) -> Unit = {},
        imageProcessor: cc.hhhl.client.media.ImageProcessor? = null,
    ): UserProfileStateHolder {
        val baseTimeMillis = 1_000_000L
        val driveRepository = object : cc.hhhl.client.repository.DriveFileRepository(
            tokenProvider = { "token-123" },
        ) {
            override suspend fun upload(upload: cc.hhhl.client.api.DriveFileUpload): cc.hhhl.client.repository.DriveFileRepositoryResult {
                onUpload(upload)
                return cc.hhhl.client.repository.DriveFileRepositoryResult.Success(
                    cc.hhhl.client.model.DriveFile(
                        id = "file-${upload.fileName}",
                        name = upload.fileName,
                        type = upload.contentType,
                        url = "https://dc.hhhl.cc/files/avatar-${upload.fileName}",
                        thumbnailUrl = null,
                        comment = null,
                        size = upload.bytes.size.toLong(),
                        isSensitive = false,
                        createdAtLabel = "刚刚",
                        folderId = upload.folderId,
                    )
                )
            }

            override suspend fun deleteFile(fileId: String): cc.hhhl.client.repository.DriveManagementRepositoryResult {
                onDeleteFile(fileId)
                return cc.hhhl.client.repository.DriveManagementRepositoryResult.FileDeleted(fileId)
            }

            override suspend fun createFolder(
                name: String,
                parentId: String?,
            ): cc.hhhl.client.repository.DriveManagementRepositoryResult {
                onCreateFolder(name)
                return cc.hhhl.client.repository.DriveManagementRepositoryResult.FolderCreated(
                    cc.hhhl.client.model.DriveFolder(
                        id = "folder-$name",
                        name = name,
                        parentId = parentId,
                        foldersCount = 0,
                        filesCount = 0,
                        createdAtLabel = "刚刚",
                    )
                )
            }
        }

        return UserProfileStateHolder(
            repository = fakeRepository(UserProfileRepositoryResult.Success(FakeData.me)),
            driveFileRepository = driveRepository,
            imageProcessor = imageProcessor,
            scope = this,
            timeProvider = { baseTimeMillis + testScheduler.currentTime },
        )
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
