package cc.hhhl.client.state

import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposeCreateResult
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.ComposeRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import cc.hhhl.client.state.ComposePollDeadlinePreset.OneDay
import cc.hhhl.client.state.ComposePollDeadlinePreset.OneHour
import cc.hhhl.client.state.ComposePollDeadlinePreset.Unlimited
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeStateHolderTest {
    @Test
    fun sendSuccessClearsDraftAndMarksCreatedNote() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        holder.send()
        assertTrue(holder.state.value.isSending)
        advanceUntilIdle()

        assertFalse(holder.state.value.isSending)
        assertEquals("", holder.state.value.draft.text)
        assertEquals("note-created", holder.state.value.createdNoteId)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun startReplyStoresReplyIdAndClearsOldText() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateText("old text")
        holder.startReply("note-parent")

        assertEquals("note-parent", holder.state.value.draft.replyId)
        assertEquals("", holder.state.value.draft.text)
    }

    @Test
    fun startQuoteStoresRenoteIdAndClearsOldText() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateText("old text")
        holder.startQuote("note-quote")

        assertEquals("note-quote", holder.state.value.draft.renoteId)
        assertEquals("", holder.state.value.draft.text)
    }

    @Test
    fun startChannelNoteStoresChannelIdAndClearsOldText() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateText("old text")
        holder.startChannelNote("channel-1")

        assertEquals("channel-1", holder.state.value.draft.channelId)
        assertEquals("", holder.state.value.draft.text)
    }

    @Test
    fun sendSuccessClearsChannelId() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(testScheduler),
        )

        holder.startChannelNote("channel-1")
        holder.updateText("channel note")
        holder.send()
        advanceUntilIdle()

        assertEquals(null, holder.state.value.draft.channelId)
    }

    @Test
    fun sendSuccessClearsReplyId() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(testScheduler),
        )

        holder.startReply("note-parent")
        holder.updateText("reply")
        holder.send()
        advanceUntilIdle()

        assertEquals(null, holder.state.value.draft.replyId)
    }

    @Test
    fun addAndRemoveFileIdsDeduplicatesDraftAttachments() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.addFileIds(listOf("file-1", "file-2", "file-1", ""))
        holder.removeFileId("file-1")

        assertEquals(listOf("file-2"), holder.state.value.draft.fileIds)
    }

    @Test
    fun uploadMediaAddsUploadedFileToDraft() = runTest {
        val file = sampleDriveFile()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(DriveFileRepositoryResult.Success(file)),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        assertTrue(holder.state.value.isUploadingMedia)
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(listOf("file-1"), holder.state.value.draft.fileIds)
        assertEquals(listOf(file), holder.state.value.attachedFiles)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun uploadMediaQueuesMultiplePickedFiles() = runTest {
        val files = listOf(
            sampleDriveFile().copy(id = "file-1", name = "first.png"),
            sampleDriveFile().copy(id = "file-2", name = "second.png"),
        )
        var index = 0
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(
                uploadResultProvider = {
                    DriveFileRepositoryResult.Success(files[index++])
                },
            ),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        holder.uploadMedia(sampleUpload().copy(fileName = "second.png"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(listOf("file-1", "file-2"), holder.state.value.draft.fileIds)
        assertEquals(files, holder.state.value.attachedFiles)
    }

    @Test
    fun uploadMediaStoresUploadError() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(DriveFileRepositoryResult.Error("Drive 空间不足，无法上传文件")),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(emptyList(), holder.state.value.draft.fileIds)
        assertEquals("Drive 空间不足，无法上传文件", holder.state.value.errorMessage)
    }

    @Test
    fun removeFileIdAlsoRemovesAttachedFileMetadata() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(DriveFileRepositoryResult.Success(sampleDriveFile())),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        advanceUntilIdle()
        holder.removeFileId("file-1")

        assertEquals(emptyList(), holder.state.value.draft.fileIds)
        assertEquals(emptyList(), holder.state.value.attachedFiles)
    }

    @Test
    fun updateAttachedFileMetadataPersistsToDriveAndUpdatesAttachment() = runTest {
        val updatedFile = sampleDriveFile().copy(comment = "alt text", isSensitive = true)
        val updateCalls = mutableListOf<Pair<String?, Boolean?>>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(
                uploadResult = DriveFileRepositoryResult.Success(sampleDriveFile()),
                updateFileResult = DriveManagementRepositoryResult.FileUpdated(updatedFile),
                onUpdateFile = { comment, isSensitive -> updateCalls.add(comment to isSensitive) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        advanceUntilIdle()
        holder.updateAttachedFileMetadata("file-1", comment = " alt text ", isSensitive = true)
        assertTrue(holder.state.value.updatingFileIds.contains("file-1"))
        advanceUntilIdle()

        assertFalse(holder.state.value.updatingFileIds.contains("file-1"))
        assertEquals(listOf<Pair<String?, Boolean?>>("alt text" to true), updateCalls)
        assertEquals(updatedFile, holder.state.value.attachedFiles.single())
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun reportMediaUploadErrorStoresInlineError() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.reportMediaUploadError("无法读取所选文件")

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals("无法读取所选文件", holder.state.value.errorMessage)
    }

    @Test
    fun updateLimitsStoresInstanceComposeLimits() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateLimits(maxTextLength = 5, maxCwLength = 3)

        assertEquals(5, holder.state.value.maxTextLength)
        assertEquals(3, holder.state.value.maxCwLength)
    }

    @Test
    fun updateVisibleUserIdsStoresCleanUniqueRecipientsAndSpecifiedRequiresOne() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateText("secret")
        holder.updateVisibility(NoteVisibility.Specified)
        holder.send()
        advanceUntilIdle()

        assertEquals(emptyList(), sentDrafts)
        assertEquals("指定可见至少需要 1 个用户", holder.state.value.errorMessage)

        holder.updateVisibleUserIds(" user-1, user-2\nuser-1 ")
        holder.send()
        advanceUntilIdle()

        assertEquals(listOf(listOf("user-1", "user-2")), sentDrafts.map { it.visibleUserIds })
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun resolveVisibleUserMentionsReplacesMentionsWithUserIds() = runTest {
        val resolvedMentions = mutableListOf<String>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            userProfileRepository = fakeUserProfileRepository { username ->
                resolvedMentions.add(username)
                when (username) {
                    "alice" -> UserProfileRepositoryResult.Success(FakeData.me.copy(id = "user-1"))
                    "bob@example.com" -> UserProfileRepositoryResult.Success(FakeData.me.copy(id = "user-3"))
                    else -> UserProfileRepositoryResult.Error("not found")
                }
            },
            scope = TestScope(testScheduler),
        )

        holder.updateVisibility(NoteVisibility.Specified)
        holder.updateVisibleUserIds("@alice，user-2、＠bob@example.com;@alice")
        holder.resolveVisibleUserMentions()
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob@example.com"), resolvedMentions)
        assertEquals(listOf("user-1", "user-2", "user-3"), holder.state.value.draft.visibleUserIds)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun sendWithVisibleMentionsResolvesUsersBeforeRepositoryCall() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            userProfileRepository = fakeUserProfileRepository { username ->
                UserProfileRepositoryResult.Success(FakeData.me.copy(id = "user-$username"))
            },
            scope = TestScope(testScheduler),
        )

        holder.updateText("secret")
        holder.updateVisibility(NoteVisibility.Specified)
        holder.updateVisibleUserIds("@alice user-2")
        holder.send()

        assertTrue(holder.state.value.isResolvingVisibleUsers)
        advanceUntilIdle()

        assertEquals(listOf(listOf("user-alice", "user-2")), sentDrafts.map { it.visibleUserIds })
        assertEquals("note-created", holder.state.value.createdNoteId)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun resolveVisibleUserMentionsWithoutResolverUsesActionableError() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateVisibility(NoteVisibility.Specified)
        holder.updateVisibleUserIds("@alice")
        holder.resolveVisibleUserMentions()

        assertEquals("无法解析指定用户", holder.state.value.errorMessage)
    }

    @Test
    fun updateTextDoesNotTruncateOverLimitDraft() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateLimits(maxTextLength = 5, maxCwLength = 3)
        holder.updateText("123456")

        assertEquals("123456", holder.state.value.draft.text)
    }

    @Test
    fun sendOverTextLimitDoesNotCallRepository() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateLimits(maxTextLength = 5, maxCwLength = 3)
        holder.updateText("123456")
        holder.send()
        advanceUntilIdle()

        assertEquals(emptyList(), sentDrafts)
        assertFalse(holder.state.value.isSending)
        assertEquals("内容不能超过 5 字", holder.state.value.errorMessage)
    }

    @Test
    fun sendOverCwLimitDoesNotCallRepository() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateLimits(maxTextLength = 10, maxCwLength = 3)
        holder.updateText("hello")
        holder.updateCw("1234")
        holder.send()
        advanceUntilIdle()

        assertEquals(emptyList(), sentDrafts)
        assertFalse(holder.state.value.isSending)
        assertEquals("内容警告不能超过 3 字", holder.state.value.errorMessage)
    }

    @Test
    fun updateCapabilitiesFallsBackFromPublicToHome() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateVisibility(NoteVisibility.Public)
        holder.updateCapabilities(canPublicNote = false)

        assertFalse(holder.state.value.canPublicNote)
        assertEquals(NoteVisibility.Home, holder.state.value.draft.visibility)
    }

    @Test
    fun startNewNoteUsesHomeVisibilityWhenPublicNotesDisabled() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateCapabilities(canPublicNote = false)
        holder.startNewNote()

        assertEquals(NoteVisibility.Home, holder.state.value.draft.visibility)
    }

    @Test
    fun updateVisibilityDoesNotAllowPublicWhenInstanceDisablesIt() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateCapabilities(canPublicNote = false)
        holder.updateVisibility(NoteVisibility.Public)

        assertEquals(NoteVisibility.Home, holder.state.value.draft.visibility)
    }

    @Test
    fun sendWithOnlyFileIdsCallsRepository() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.addFileIds(listOf("file-1"))
        holder.send()
        advanceUntilIdle()

        assertEquals(listOf(listOf("file-1")), sentDrafts.map { it.fileIds })
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun pollDraftCanBeEnabledEditedAndRemoved() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.setPollEnabled(true)
        holder.updatePollChoice(0, "A")
        holder.updatePollChoice(1, "B")
        holder.updatePollMultiple(true)
        holder.setPollEnabled(false)

        assertEquals(null, holder.state.value.draft.poll)
    }

    @Test
    fun updatePollExpiresAtStoresTrimmedDeadline() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.setPollEnabled(true)
        holder.updatePollExpiresAt(" 2026-05-26T00:00:00.000Z ")

        assertEquals("2026-05-26T00:00:00.000Z", holder.state.value.draft.poll?.expiresAt)
    }

    @Test
    fun selectPollDeadlinePresetStoresIsoDeadlineAndCanClearIt() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )
        val nowEpochMillis = 1_779_710_400_000L

        holder.setPollEnabled(true)
        holder.selectPollDeadlinePreset(OneHour, nowEpochMillis)
        assertEquals("2026-05-25T13:00:00Z", holder.state.value.draft.poll?.expiresAt)

        holder.selectPollDeadlinePreset(OneDay, nowEpochMillis)
        assertEquals("2026-05-26T12:00:00Z", holder.state.value.draft.poll?.expiresAt)

        holder.selectPollDeadlinePreset(Unlimited, nowEpochMillis)
        assertEquals(null, holder.state.value.draft.poll?.expiresAt)
    }

    @Test
    fun sendWithIncompletePollDoesNotCallRepository() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateText("vote")
        holder.setPollEnabled(true)
        holder.updatePollChoice(0, "A")
        holder.updatePollChoice(1, "")
        holder.send()
        advanceUntilIdle()

        assertEquals(emptyList(), sentDrafts)
        assertEquals("投票至少需要 2 个选项", holder.state.value.errorMessage)
    }

    @Test
    fun sendWithPollCallsRepository() = runTest {
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                result = ComposeRepositoryResult.Success("note-created"),
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateText("vote")
        holder.setPollEnabled(true)
        holder.updatePollChoice(0, "A")
        holder.updatePollChoice(1, "B")
        holder.send()
        advanceUntilIdle()

        assertEquals(listOf(listOf("A", "B")), sentDrafts.map { it.poll?.choices })
    }

    @Test
    fun addPollChoiceAppendsBlankChoiceUpToLimit() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.setPollEnabled(true)
        repeat(12) { holder.addPollChoice() }

        assertEquals(10, holder.state.value.draft.poll?.choices?.size)
    }

    @Test
    fun removePollChoiceKeepsAtLeastTwoChoices() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.setPollEnabled(true)
        holder.addPollChoice()
        holder.updatePollChoice(2, "C")
        holder.removePollChoice(0)
        holder.removePollChoice(0)

        assertEquals(listOf("C", ""), holder.state.value.draft.poll?.choices)
    }

    @Test
    fun sendValidationErrorKeepsDraftText() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.ValidationError("内容不能为空")),
            scope = TestScope(testScheduler),
        )

        holder.updateText(" ")
        holder.send()
        advanceUntilIdle()

        assertEquals(" ", holder.state.value.draft.text)
        assertEquals("内容不能为空", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val holder = ComposeStateHolder(
            repository = sequenceRepository(
                ComposeRepositoryResult.Unauthorized,
                ComposeRepositoryResult.Success("note-created"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        holder.send()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.updateText("hello again")
        holder.send()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("note-created", holder.state.value.createdNoteId)
    }

    @Test
    fun editingDraftClearsReloginAfterUnauthorized() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Unauthorized),
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        holder.send()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.updateText("hello again")

        assertFalse(holder.state.value.requiresRelogin)
    }

    @Test
    fun consumeCreatedNoteClearsOneShotCreatedNoteId() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        holder.send()
        advanceUntilIdle()
        holder.consumeCreatedNote()

        assertEquals(null, holder.state.value.createdNoteId)
    }

    private fun fakeRepository(
        result: ComposeRepositoryResult,
        onSend: (ComposeDraft) -> Unit = {},
    ): ComposeRepository {
        return object : ComposeRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ComposeApi {
                override suspend fun createNote(
                    token: String,
                    draft: ComposeDraft,
                ): ComposeCreateResult = ComposeCreateResult.Success("unused")
            },
        ) {
            override suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
                onSend(draft)
                return result
            }
        }
    }

    private fun fakeDriveRepository(
        uploadResult: DriveFileRepositoryResult,
        updateFileResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FileUpdated(sampleDriveFile()),
        onUpdateFile: (comment: String?, isSensitive: Boolean?) -> Unit = { _, _ -> },
    ): DriveFileRepository = fakeDriveRepository(
        uploadResultProvider = { uploadResult },
        updateFileResult = updateFileResult,
        onUpdateFile = onUpdateFile,
    )

    private fun fakeDriveRepository(
        uploadResultProvider: () -> DriveFileRepositoryResult,
        updateFileResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FileUpdated(sampleDriveFile()),
        onUpdateFile: (comment: String?, isSensitive: Boolean?) -> Unit = { _, _ -> },
    ): DriveFileRepository {
        return object : DriveFileRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.DriveFileApi {
                override suspend fun loadFiles(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    sort: cc.hhhl.client.api.DriveFileSort,
                    searchQuery: String,
                    showAll: Boolean,
                ): cc.hhhl.client.api.DriveFileListResult {
                    return cc.hhhl.client.api.DriveFileListResult.Success(emptyList())
                }

                override suspend fun loadFolders(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    searchQuery: String,
                ): cc.hhhl.client.api.DriveFolderListResult {
                    return cc.hhhl.client.api.DriveFolderListResult.Success(emptyList())
                }

                override suspend fun uploadFile(
                    token: String,
                    upload: DriveFileUpload,
                ): cc.hhhl.client.api.DriveFileUploadResult {
                    return cc.hhhl.client.api.DriveFileUploadResult.Success(sampleDriveFile())
                }

                override suspend fun updateFile(
                    token: String,
                    fileId: String,
                    name: String?,
                    folderId: String?,
                    comment: String?,
                    isSensitive: Boolean?,
                ): cc.hhhl.client.api.DriveFileMutationResult {
                    return cc.hhhl.client.api.DriveFileMutationResult.Success(sampleDriveFile())
                }

                override suspend fun deleteFile(
                    token: String,
                    fileId: String,
                ): cc.hhhl.client.api.DriveFileMutationResult {
                    return cc.hhhl.client.api.DriveFileMutationResult.Deleted
                }

                override suspend fun createFolder(
                    token: String,
                    name: String,
                    parentId: String?,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Success(sampleDriveFolder())
                }

                override suspend fun updateFolder(
                    token: String,
                    folderId: String,
                    name: String?,
                    parentId: String?,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Success(sampleDriveFolder())
                }

                override suspend fun deleteFolder(
                    token: String,
                    folderId: String,
                ): cc.hhhl.client.api.DriveFolderMutationResult {
                    return cc.hhhl.client.api.DriveFolderMutationResult.Deleted
                }
            },
        ) {
            override suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult = uploadResultProvider()

            override suspend fun updateFile(
                fileId: String,
                name: String?,
                comment: String?,
                isSensitive: Boolean?,
                folderId: String?,
            ): DriveManagementRepositoryResult {
                onUpdateFile(comment, isSensitive)
                return updateFileResult
            }
        }
    }

    private fun fakeUserProfileRepository(
        onResolve: (String) -> UserProfileRepositoryResult,
    ): UserProfileRepository {
        return object : UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "me" },
        ) {
            override suspend fun resolveMention(username: String): UserProfileRepositoryResult {
                return onResolve(username.trim().removePrefix("@"))
            }
        }
    }

    private fun sequenceRepository(
        vararg results: ComposeRepositoryResult,
    ): ComposeRepository {
        var index = 0
        return object : ComposeRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ComposeApi {
                override suspend fun createNote(
                    token: String,
                    draft: ComposeDraft,
                ): ComposeCreateResult = ComposeCreateResult.Success("unused")
            },
        ) {
            override suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }
        }
    }

    private fun sampleUpload(): DriveFileUpload {
        return DriveFileUpload(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "photo.png",
            contentType = "image/png",
        )
    }

    private fun sampleDriveFile(): DriveFile {
        return DriveFile(
            id = "file-1",
            name = "photo.png",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/photo.png",
            thumbnailUrl = null,
            comment = null,
            size = 3,
            isSensitive = false,
        )
    }

    private fun sampleDriveFolder(): cc.hhhl.client.model.DriveFolder {
        return cc.hhhl.client.model.DriveFolder(
            id = "folder-1",
            name = "素材",
            parentId = null,
            foldersCount = 0,
            filesCount = 0,
        )
    }
}
