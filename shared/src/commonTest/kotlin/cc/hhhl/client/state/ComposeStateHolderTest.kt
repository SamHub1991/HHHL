package cc.hhhl.client.state

import cc.hhhl.client.api.ComposeCreateResult
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposePollDraft
import cc.hhhl.client.api.ComposeReactionAcceptance
import cc.hhhl.client.api.ComposeScheduleDraft
import cc.hhhl.client.api.ComposeScheduleDeleteResult
import cc.hhhl.client.api.ComposeScheduledNote
import cc.hhhl.client.api.ComposeScheduledNotesResult
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotePoll
import cc.hhhl.client.model.NotePollChoice
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.ComposeRepositoryResult
import cc.hhhl.client.repository.ComposeScheduleDeleteRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import cc.hhhl.client.state.ComposePollDeadlinePreset.OneDay
import cc.hhhl.client.state.ComposePollDeadlinePreset.OneHour
import cc.hhhl.client.state.ComposePollDeadlinePreset.Unlimited
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
class ComposeStateHolderTest {
    @Test
    fun composeDraftCodecRoundTripsPollAndVisibility() {
        val draft = ComposeDraft(
            text = "draft text",
            editId = "note-edit-1",
            visibility = NoteVisibility.Specified,
            visibleUserIds = listOf("user-1", "user-2"),
            cw = "spoiler",
            replyId = "reply-1",
            renoteId = "quote-1",
            channelId = "channel-1",
            fileIds = listOf("file-1"),
            poll = ComposePollDraft(
                choices = listOf("A", "B"),
                multiple = true,
                expiresAt = "2026-05-26T12:00:00Z",
            ),
            localOnly = true,
            reactionAcceptance = ComposeReactionAcceptance.LikeOnlyForRemote,
            scheduleNote = ComposeScheduleDraft(1_779_800_000_000L),
        )

        assertEquals(draft, ComposeDraftCodec.decode(ComposeDraftCodec.encode(draft)))
    }

    @Test
    fun restoreStoredDraftLoadsPersistedDraft() {
        val storedDraft = ComposeDraft(
            text = "saved draft",
            visibility = NoteVisibility.Followers,
            fileIds = listOf("file-1"),
        )
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = memoryDraftStore(storedDraft),
            scope = TestScope(),
        )

        holder.restoreStoredDraft()

        assertEquals(storedDraft, holder.state.value.draft)
        assertEquals(true, holder.state.value.restoredDraft)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun restoreStoredDraftFallsBackFromPublicWhenPublicNotesDisabled() {
        val store = memoryDraftStore(ComposeDraft(text = "saved draft", visibility = NoteVisibility.Public))
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            scope = TestScope(),
        )

        holder.updateCapabilities(canPublicNote = false)
        holder.restoreStoredDraft()

        assertEquals(NoteVisibility.Home, holder.state.value.draft.visibility)
        assertEquals(NoteVisibility.Home, store.savedDraft?.visibility)
    }

    @Test
    fun editingDraftPersistsStoredDraft() {
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            scope = TestScope(),
        )

        holder.updateText("saved text")
        holder.updateCw("cw")
        holder.updateVisibility(NoteVisibility.Followers)

        assertEquals("saved text", store.savedDraft?.text)
        assertEquals("cw", store.savedDraft?.cw)
        assertEquals(NoteVisibility.Followers, store.savedDraft?.visibility)
    }

    @Test
    fun draftStorageKeySeparatesAccountAndTargetContext() {
        assertEquals(
            "account-1|new",
            composeDraftStorageKey("account-1", ComposeDraft(text = "new")),
        )
        assertEquals(
            "account-1|reply:note-1",
            composeDraftStorageKey("account-1", ComposeDraft(replyId = "note-1")),
        )
        assertEquals(
            "account-2|quote:note-1",
            composeDraftStorageKey("account-2", ComposeDraft(renoteId = "note-1")),
        )
        assertEquals(
            "account-2|channel:channel-1",
            composeDraftStorageKey("account-2", ComposeDraft(channelId = "channel-1")),
        )
        assertEquals(
            "account-2|edit:note-1",
            composeDraftStorageKey(
                "account-2",
                ComposeDraft(editId = "note-1", replyId = "reply-1", channelId = "channel-1"),
            ),
        )
    }

    @Test
    fun editingReplyDraftPersistsUnderReplyScopedKey() {
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )

        holder.startReply("note-1")
        holder.updateText("reply draft")

        assertEquals("reply draft", store.savedDrafts.getValue("account-1|reply:note-1").text)
    }

    @Test
    fun startReplyRestoresReplyScopedDraft() {
        val store = memoryDraftStore().apply {
            savedDrafts["account-1|reply:note-1"] = ComposeDraft(
                text = "saved reply",
                replyId = "note-1",
                visibility = NoteVisibility.Followers,
            )
        }
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )

        holder.startReply("note-1")

        assertEquals("saved reply", holder.state.value.draft.text)
        assertEquals("note-1", holder.state.value.draft.replyId)
        assertEquals(NoteVisibility.Followers, holder.state.value.draft.visibility)
        assertEquals(true, holder.state.value.restoredDraft)
    }

    @Test
    fun startReplyDoesNotRestoreGenericDraft() {
        val store = memoryDraftStore(ComposeDraft(text = "generic draft"))
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )

        holder.startReply("note-1")

        assertEquals("", holder.state.value.draft.text)
        assertEquals("note-1", holder.state.value.draft.replyId)
        assertEquals(false, holder.state.value.restoredDraft)
    }

    @Test
    fun startEditNoteSeedsDraftFromPublishedNote() {
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-1")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )
        val note = Note(
            id = "note-1",
            author = FakeData.me,
            text = "published text",
            createdAtLabel = "刚刚",
            visibility = NoteVisibility.Home,
            cw = "spoiler",
            media = listOf(
                NoteMedia(
                    id = "file-1",
                    description = "alt text",
                    type = "image/png",
                    url = "https://dc.hhhl.cc/files/photo.png",
                    isSensitive = true,
                ),
            ),
            poll = NotePoll(
                multiple = true,
                expiresAt = "2026-05-30T12:00:00Z",
                choices = listOf(
                    NotePollChoice("A", votes = 1, isVoted = false),
                    NotePollChoice("B", votes = 2, isVoted = true),
                ),
            ),
            replyId = "reply-1",
            renoteId = "renote-1",
            channelId = "channel-1",
            localOnly = true,
            reactionAcceptance = "likeOnly",
        )

        holder.startEditNote(note)

        val draft = holder.state.value.draft
        assertEquals("note-1", draft.editId)
        assertEquals("published text", draft.text)
        assertEquals("spoiler", draft.cw)
        assertEquals(NoteVisibility.Home, draft.visibility)
        assertEquals("reply-1", draft.replyId)
        assertEquals("renote-1", draft.renoteId)
        assertEquals("channel-1", draft.channelId)
        assertEquals(listOf("file-1"), draft.fileIds)
        assertEquals(listOf("A", "B"), draft.poll?.choices)
        assertEquals(true, draft.poll?.multiple)
        assertEquals("2026-05-30T12:00:00Z", draft.poll?.expiresAt)
        assertEquals(true, draft.localOnly)
        assertEquals(ComposeReactionAcceptance.LikeOnly, draft.reactionAcceptance)
        assertEquals("alt text", holder.state.value.attachedFiles.single().comment)
        assertEquals(true, holder.state.value.attachedFiles.single().isSensitive)
        assertEquals(null, store.savedDrafts["account-1|edit:note-1"])
    }

    @Test
    fun resetDraftInEditModeRestoresPublishedNoteContext() {
        val store = memoryDraftStore().apply {
            savedDrafts["account-1|edit:note-1"] = ComposeDraft(editId = "note-1", text = "saved edit")
        }
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-1")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )
        val note = Note(
            id = "note-1",
            author = FakeData.me,
            text = "original text",
            createdAtLabel = "刚刚",
            visibility = NoteVisibility.Home,
            media = listOf(
                NoteMedia(
                    id = "file-1",
                    description = "original alt",
                    type = "image/png",
                    url = "https://dc.hhhl.cc/files/photo.png",
                ),
            ),
            replyId = "reply-1",
        )

        holder.startEditNote(note)
        holder.updateText("changed text")
        holder.addFileIds(listOf("file-2"))
        holder.resetDraft()

        val draft = holder.state.value.draft
        assertEquals("note-1", draft.editId)
        assertEquals("original text", draft.text)
        assertEquals("reply-1", draft.replyId)
        assertEquals(listOf("file-1"), draft.fileIds)
        assertEquals("original alt", holder.state.value.attachedFiles.single().comment)
        assertEquals(false, holder.state.value.restoredDraft)
        assertEquals(null, store.savedDrafts["account-1|edit:note-1"])
    }

    @Test
    fun editingRestoredDraftClearsRestoredIndicator() {
        val store = memoryDraftStore(ComposeDraft(text = "saved draft"))
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            scope = TestScope(),
        )

        holder.restoreStoredDraft()
        holder.updateText("edited draft")

        assertEquals(false, holder.state.value.restoredDraft)
    }

    @Test
    fun sendSuccessClearsStoredDraft() = runTest {
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        assertEquals("hello", store.savedDraft?.text)
        holder.send()
        advanceUntilIdle()

        assertEquals(null, store.savedDraft)
        assertTrue(store.wasCleared)
    }

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
        assertEquals("hello", holder.state.value.completedDraft?.text)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun sendNetworkErrorQueuesFailedDraftForRetry() = runTest {
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Error("网络不可用")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(testScheduler),
        )

        holder.updateText("hello")
        holder.send()
        advanceUntilIdle()

        val queued = holder.state.value.failedSendQueue.single()
        assertFalse(holder.state.value.isSending)
        assertEquals("hello", queued.draft.text)
        assertEquals("网络不可用", queued.message)
        assertEquals(1, store.savedFailedQueues.getValue("account-1|failed-send-queue").size)
    }

    @Test
    fun retryFailedSendSuccessRemovesQueuedDraft() = runTest {
        var result: ComposeRepositoryResult = ComposeRepositoryResult.Error("网络不可用")
        val sentDrafts = mutableListOf<ComposeDraft>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(
                resultProvider = { result },
                onSend = { sentDrafts.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateText("retry me")
        holder.send()
        advanceUntilIdle()
        val queuedId = holder.state.value.failedSendQueue.single().id

        result = ComposeRepositoryResult.Success("note-created")
        holder.retryFailedSend(queuedId)
        advanceUntilIdle()

        assertEquals(listOf("retry me", "retry me"), sentDrafts.map { it.text })
        assertEquals(emptyList(), holder.state.value.failedSendQueue)
        assertEquals("note-created", holder.state.value.createdNoteId)
        assertEquals(null as String?, holder.state.value.errorMessage)
    }

    @Test
    fun restoreFailedSendQueueLoadsPersistedQueue() {
        val store = memoryDraftStore().apply {
            savedFailedQueues["account-1|failed-send-queue"] = listOf(
                ComposeFailedSend(
                    id = "failed-1",
                    draft = ComposeDraft(text = "persisted"),
                    message = "网络不可用",
                    createdAtEpochMillis = 1_779_800_000_000L,
                ),
            )
        }
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            draftStore = store,
            draftKeyProvider = { "account-1" },
            scope = TestScope(),
        )

        holder.restoreFailedSendQueue()

        assertEquals("persisted", holder.state.value.failedSendQueue.single().draft.text)
    }

    @Test
    fun sendSuccessResetsDraftToHomeWhenPublicNotesDisabled() = runTest {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(testScheduler),
        )

        holder.updateCapabilities(canPublicNote = false)
        holder.updateText("hello")
        holder.send()
        advanceUntilIdle()

        assertEquals(NoteVisibility.Home, holder.state.value.draft.visibility)
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
    fun resetDraftInvalidatesPendingMediaUploadResult() = runTest {
        val pending = CompletableDeferred<DriveFileRepositoryResult>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            driveFileRepository = fakeDriveRepository(uploadResultProvider = { pending.await() }),
            scope = TestScope(testScheduler),
        )

        holder.uploadMedia(sampleUpload())
        runCurrent()
        assertTrue(holder.state.value.isUploadingMedia)

        holder.resetDraft()
        pending.complete(DriveFileRepositoryResult.Success(sampleDriveFile()))
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploadingMedia)
        assertEquals(emptyList(), holder.state.value.draft.fileIds)
        assertEquals(emptyList(), holder.state.value.attachedFiles)
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
    fun changingVisibleUsersInvalidatesPendingMentionResolution() = runTest {
        val pending = CompletableDeferred<UserProfileRepositoryResult>()
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            userProfileRepository = fakeUserProfileRepository { pending.await() },
            scope = TestScope(testScheduler),
        )

        holder.updateVisibility(NoteVisibility.Specified)
        holder.updateVisibleUserIds("@alice")
        holder.resolveVisibleUserMentions()
        runCurrent()
        assertTrue(holder.state.value.isResolvingVisibleUsers)

        holder.updateVisibleUserIds("user-2")
        pending.complete(UserProfileRepositoryResult.Success(FakeData.me.copy(id = "user-1")))
        advanceUntilIdle()

        assertFalse(holder.state.value.isResolvingVisibleUsers)
        assertEquals(listOf("user-2"), holder.state.value.draft.visibleUserIds)
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
    fun sendSuccessDoesNotClearDraftEditedWhileSendWasPending() = runTest {
        val pending = CompletableDeferred<ComposeRepositoryResult>()
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = fakeRepository(resultProvider = { pending.await() }),
            draftStore = store,
            scope = TestScope(testScheduler),
        )

        holder.updateText("first")
        holder.send()
        runCurrent()
        assertTrue(holder.state.value.isSending)

        holder.updateText("second")
        pending.complete(ComposeRepositoryResult.Success("note-created"))
        advanceUntilIdle()

        assertFalse(holder.state.value.isSending)
        assertEquals("second", holder.state.value.draft.text)
        assertEquals("second", store.savedDraft?.text)
        assertEquals("note-created", holder.state.value.createdNoteId)
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
    fun updateCapabilitiesClearsScheduleWhenSchedulingDisabled() {
        val holder = ComposeStateHolder(
            repository = fakeRepository(ComposeRepositoryResult.Success("note-created")),
            scope = TestScope(),
        )

        holder.updateCapabilities(canPublicNote = true, canScheduleNotes = true)
        holder.updateScheduleNote(1_779_800_000_000L)
        holder.updateCapabilities(canPublicNote = true, canScheduleNotes = false)

        assertEquals(null, holder.state.value.draft.scheduleNote)
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
    fun editScheduledNoteDeletesOriginalAndRestoresDraftForEditing() = runTest {
        val deletedNoteIds = mutableListOf<String>()
        val scheduledNote = ComposeScheduledNote(
            id = "note-scheduled-1",
            text = "later",
            cw = "spoiler",
            scheduledAt = 1_779_800_000_000L,
            visibility = NoteVisibility.Specified,
            visibleUserIds = listOf("user-1"),
            replyId = "reply-1",
            renoteId = "renote-1",
            channelId = "channel-1",
            fileIds = listOf("file-1"),
            attachedFiles = listOf(sampleDriveFile()),
            poll = ComposePollDraft(choices = listOf("A", "B"), multiple = true),
            localOnly = true,
            reactionAcceptance = ComposeReactionAcceptance.LikeOnly,
        )
        val store = memoryDraftStore()
        val holder = ComposeStateHolder(
            repository = object : ComposeRepository(
                tokenProvider = { "token-123" },
                api = fakeComposeApi(),
            ) {
                override suspend fun deleteScheduledNote(noteId: String): ComposeScheduleDeleteRepositoryResult {
                    deletedNoteIds.add(noteId)
                    return ComposeScheduleDeleteRepositoryResult.Success
                }
            },
            draftStore = store,
            scope = TestScope(testScheduler),
        )

        holder.updateCapabilities(canPublicNote = true, canScheduleNotes = true)
        holder.editScheduledNote(scheduledNote)
        assertTrue(holder.state.value.deletingScheduledNoteIds.contains("note-scheduled-1"))
        advanceUntilIdle()

        assertEquals(listOf("note-scheduled-1"), deletedNoteIds)
        assertEquals("later", holder.state.value.draft.text)
        assertEquals("spoiler", holder.state.value.draft.cw)
        assertEquals(NoteVisibility.Specified, holder.state.value.draft.visibility)
        assertEquals(listOf("user-1"), holder.state.value.draft.visibleUserIds)
        assertEquals("reply-1", holder.state.value.draft.replyId)
        assertEquals("renote-1", holder.state.value.draft.renoteId)
        assertEquals("channel-1", holder.state.value.draft.channelId)
        assertEquals(listOf("file-1"), holder.state.value.draft.fileIds)
        assertEquals(listOf(sampleDriveFile()), holder.state.value.attachedFiles)
        assertEquals(ComposeScheduleDraft(1_779_800_000_000L), holder.state.value.draft.scheduleNote)
        assertEquals(true, holder.state.value.draft.localOnly)
        assertEquals(ComposeReactionAcceptance.LikeOnly, holder.state.value.draft.reactionAcceptance)
        assertEquals(emptySet(), holder.state.value.deletingScheduledNoteIds)
        assertEquals(holder.state.value.draft, store.savedDraft)
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
        assertEquals(null, holder.state.value.completedDraft)
    }

    private fun fakeRepository(
        result: ComposeRepositoryResult,
        onSend: (ComposeDraft) -> Unit = {},
    ): ComposeRepository = fakeRepository(resultProvider = { result }, onSend = onSend)

    private fun fakeRepository(
        resultProvider: suspend () -> ComposeRepositoryResult,
        onSend: (ComposeDraft) -> Unit = {},
    ): ComposeRepository {
        return object : ComposeRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.ComposeApi {
                override suspend fun createNote(
                    token: String,
                    draft: ComposeDraft,
                ): ComposeCreateResult = ComposeCreateResult.Success("unused")

                override suspend fun listScheduledNotes(
                    token: String,
                    limit: Int,
                    offset: Int,
                ): ComposeScheduledNotesResult = ComposeScheduledNotesResult.Success(emptyList())

                override suspend fun deleteScheduledNote(
                    token: String,
                    noteId: String,
                ): ComposeScheduleDeleteResult = ComposeScheduleDeleteResult.Success
            },
        ) {
            override suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
                onSend(draft)
                return resultProvider()
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
        uploadResultProvider: suspend () -> DriveFileRepositoryResult,
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

                override suspend fun loadFileDetails(
                    token: String,
                    fileId: String,
                ): cc.hhhl.client.api.DriveFileDetailsResult {
                    return cc.hhhl.client.api.DriveFileDetailsResult.Success(
                        cc.hhhl.client.model.DriveFileDetails(sampleDriveFile()),
                    )
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
        onResolve: suspend (String) -> UserProfileRepositoryResult,
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

                override suspend fun listScheduledNotes(
                    token: String,
                    limit: Int,
                    offset: Int,
                ): ComposeScheduledNotesResult = ComposeScheduledNotesResult.Success(emptyList())

                override suspend fun deleteScheduledNote(
                    token: String,
                    noteId: String,
                ): ComposeScheduleDeleteResult = ComposeScheduleDeleteResult.Success
            },
        ) {
            override suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
                val result = results[index.coerceAtMost(results.lastIndex)]
                index += 1
                return result
            }
        }
    }

    private fun fakeComposeApi(): cc.hhhl.client.api.ComposeApi {
        return object : cc.hhhl.client.api.ComposeApi {
            override suspend fun createNote(
                token: String,
                draft: ComposeDraft,
            ): ComposeCreateResult = ComposeCreateResult.Success("unused")

            override suspend fun listScheduledNotes(
                token: String,
                limit: Int,
                offset: Int,
            ): ComposeScheduledNotesResult = ComposeScheduledNotesResult.Success(emptyList())

            override suspend fun deleteScheduledNote(
                token: String,
                noteId: String,
            ): ComposeScheduleDeleteResult = ComposeScheduleDeleteResult.Success
        }
    }

    private fun memoryDraftStore(initialDraft: ComposeDraft? = null): MemoryComposeDraftStore {
        return MemoryComposeDraftStore(initialDraft)
    }

    private class MemoryComposeDraftStore(
        initialDraft: ComposeDraft? = null,
    ) : ComposeDraftStore {
        var savedDraft: ComposeDraft? = initialDraft
            private set
        val savedDrafts: MutableMap<String, ComposeDraft> = mutableMapOf()
        val savedFailedQueues: MutableMap<String, List<ComposeFailedSend>> = mutableMapOf()
        var wasCleared: Boolean = false
            private set

        override fun loadDraft(): ComposeDraft? = savedDraft

        override fun saveDraft(draft: ComposeDraft) {
            savedDraft = draft
            wasCleared = false
        }

        override fun clearDraft() {
            savedDraft = null
            wasCleared = true
        }

        override fun loadDraft(key: String): ComposeDraft? {
            return savedDrafts[key] ?: savedDraft.takeIf { key.isNewDraftKey() }
        }

        override fun saveDraft(key: String, draft: ComposeDraft) {
            savedDrafts[key] = draft
            saveDraft(draft)
        }

        override fun clearDraft(key: String) {
            savedDrafts.remove(key)
            clearDraft()
        }

        override fun loadFailedSendQueue(key: String): List<ComposeFailedSend> {
            return savedFailedQueues[key].orEmpty()
        }

        override fun saveFailedSendQueue(key: String, queue: List<ComposeFailedSend>) {
            savedFailedQueues[key] = queue
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
