package cc.hhhl.client.state

import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposePollDraft
import cc.hhhl.client.api.ComposeReactionAcceptance
import cc.hhhl.client.api.ComposeScheduleDraft
import cc.hhhl.client.api.ComposeScheduledNote
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.repository.ComposeRepository
import cc.hhhl.client.repository.ComposeRepositoryResult
import cc.hhhl.client.repository.ComposeScheduleDeleteRepositoryResult
import cc.hhhl.client.repository.ComposeScheduledNotesRepositoryResult
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import cc.hhhl.client.repository.UserProfileRepository
import cc.hhhl.client.repository.UserProfileRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ComposeUiState(
    val draft: ComposeDraft = ComposeDraft(),
    val attachedFiles: List<DriveFile> = emptyList(),
    val maxTextLength: Int = 3000,
    val maxCwLength: Int = 500,
    val canPublicNote: Boolean = true,
    val canScheduleNotes: Boolean = false,
    val isSending: Boolean = false,
    val isUploadingMedia: Boolean = false,
    val isResolvingVisibleUsers: Boolean = false,
    val isLoadingScheduledNotes: Boolean = false,
    val deletingScheduledNoteIds: Set<String> = emptySet(),
    val scheduledNotes: List<ComposeScheduledNote> = emptyList(),
    val updatingFileIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val createdNoteId: String? = null,
    val requiresRelogin: Boolean = false,
)

class ComposeStateHolder(
    private val repository: ComposeRepository,
    private val driveFileRepository: DriveFileRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val draftStore: ComposeDraftStore = NoopComposeDraftStore,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = mutableState
    private val pendingMediaUploads = ArrayDeque<DriveFileUpload>()
    private var draftSessionId = 0
    private var visibleUserResolveRequestId = 0
    private var scheduledNotesRequestId = 0

    fun restoreStoredDraft() {
        val storedDraft = runCatching { draftStore.loadDraft() }.getOrNull() ?: return
        draftSessionId += 1
        updateDraftState { current ->
            current.copy(
                draft = storedDraft.sanitizedForCapabilities(current.canPublicNote, current.canScheduleNotes),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun startReply(replyId: String) {
        invalidateDraftSession()
        updateDraftState {
            it.copy(
                draft = ComposeDraft(
                    replyId = replyId.takeIf { id -> id.isNotBlank() },
                    visibility = defaultVisibility(it.canPublicNote),
                ),
                attachedFiles = emptyList(),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun startQuote(renoteId: String) {
        invalidateDraftSession()
        updateDraftState {
            it.copy(
                draft = ComposeDraft(
                    renoteId = renoteId.takeIf { id -> id.isNotBlank() },
                    visibility = defaultVisibility(it.canPublicNote),
                ),
                attachedFiles = emptyList(),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun startChannelNote(channelId: String) {
        invalidateDraftSession()
        updateDraftState {
            it.copy(
                draft = ComposeDraft(
                    channelId = channelId.takeIf { id -> id.isNotBlank() },
                    visibility = defaultVisibility(it.canPublicNote),
                ),
                attachedFiles = emptyList(),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun startNewNote() {
        invalidateDraftSession()
        updateDraftState {
            it.copy(
                draft = newDraft(it.canPublicNote),
                attachedFiles = emptyList(),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateText(text: String) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(text = text),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateCw(cw: String?) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(cw = cw),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateVisibility(visibility: NoteVisibility) {
        updateDraftState {
            val nextVisibility = if (!it.canPublicNote && visibility == NoteVisibility.Public) {
                defaultVisibility(canPublicNote = false)
            } else {
                visibility
            }
            it.copy(
                draft = it.draft.copy(
                    visibility = nextVisibility,
                    visibleUserIds = if (nextVisibility == NoteVisibility.Specified) {
                        it.draft.visibleUserIds
                    } else {
                        emptyList()
                    },
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateLocalOnly(localOnly: Boolean) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(localOnly = localOnly),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateReactionAcceptance(acceptance: ComposeReactionAcceptance) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(reactionAcceptance = acceptance),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateScheduleNote(scheduledAt: Long?) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(scheduleNote = scheduledAt?.let(::ComposeScheduleDraft)),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun appendText(fragment: String) {
        val cleanFragment = fragment.trim()
        if (cleanFragment.isEmpty()) return
        updateDraftState { current ->
            val separator = if (current.draft.text.isBlank() || current.draft.text.endsWith(" ")) "" else " "
            current.copy(
                draft = current.draft.copy(text = current.draft.text + separator + cleanFragment),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun resetDraft() {
        invalidateDraftSession()
        updateDraftState {
            it.copy(
                draft = newDraft(it.canPublicNote),
                attachedFiles = emptyList(),
                updatingFileIds = emptySet(),
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateVisibleUserIds(input: String) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(visibleUserIds = input.toComposeVisibleUserTokens()),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun resolveVisibleUserMentions() {
        resolveVisibleUserMentions(sendAfterResolve = false)
    }

    private fun resolveVisibleUserMentions(sendAfterResolve: Boolean) {
        val current = state.value
        if (current.isResolvingVisibleUsers) return
        val mentions = current.draft.visibleUserIds
            .mapNotNull { it.toComposeVisibleUserMention() }
            .distinct()
        if (mentions.isEmpty()) {
            if (sendAfterResolve) send()
            return
        }
        val resolver = userProfileRepository
        if (resolver == null) {
            mutableState.update { it.copy(errorMessage = "无法解析指定用户") }
            return
        }

        mutableState.update {
            it.copy(
                isResolvingVisibleUsers = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        val requestId = ++visibleUserResolveRequestId
        val visibleUserSnapshot = current.draft.visibleUserIds

        scope.launch {
            val resolvedIds = mutableMapOf<String, String>()
            var errorMessage: String? = null
            for (mention in mentions) {
                when (val result = resolver.resolveMention(mention)) {
                    is UserProfileRepositoryResult.Success -> resolvedIds[mention] = result.user.id
                    UserProfileRepositoryResult.Unauthorized -> {
                        errorMessage = "登录已失效，请重新登录"
                        mutableState.update { it.copy(requiresRelogin = true) }
                        break
                    }
                    is UserProfileRepositoryResult.Error -> {
                        errorMessage = "无法解析 @${mention}：${result.message}"
                        break
                    }
                }
            }

            updateDraftState { latest ->
                if (requestId != visibleUserResolveRequestId || latest.draft.visibleUserIds != visibleUserSnapshot) {
                    return@updateDraftState latest.copy(isResolvingVisibleUsers = false)
                }
                if (errorMessage != null) {
                    latest.copy(
                        isResolvingVisibleUsers = false,
                        errorMessage = errorMessage,
                        requiresRelogin = latest.requiresRelogin && errorMessage == "登录已失效，请重新登录",
                    )
                } else {
                    latest.copy(
                        draft = latest.draft.copy(
                            visibleUserIds = latest.draft.visibleUserIds
                                .map { value ->
                                    value.toComposeVisibleUserMention()
                                        ?.let { mention -> resolvedIds[mention] }
                                        ?: value
                                }
                                .filter { it.isNotBlank() }
                                .distinct(),
                        ),
                        isResolvingVisibleUsers = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
            }
            if (
                errorMessage == null &&
                sendAfterResolve &&
                requestId == visibleUserResolveRequestId &&
                state.value.draft.visibleUserIds.none { it.toComposeVisibleUserMention() != null }
            ) {
                send()
            }
        }
    }

    fun setPollEnabled(enabled: Boolean) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(
                    poll = if (enabled) {
                        it.draft.poll ?: ComposePollDraft()
                    } else {
                        null
                    },
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updatePollChoice(
        index: Int,
        value: String,
    ) {
        if (index !in 0 until MAX_POLL_CHOICES) return

        updateDraftState { current ->
            val poll = current.draft.poll ?: ComposePollDraft()
            val choices = poll.choices.toMutableList()
            while (choices.size <= index) {
                choices.add("")
            }
            choices[index] = value
            current.copy(
                draft = current.draft.copy(
                    poll = poll.copy(choices = choices.take(MAX_POLL_CHOICES)),
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updatePollMultiple(multiple: Boolean) {
        updateDraftState { current ->
            current.copy(
                draft = current.draft.copy(
                    poll = (current.draft.poll ?: ComposePollDraft()).copy(multiple = multiple),
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updatePollExpiresAt(expiresAt: String) {
        updateDraftState { current ->
            current.copy(
                draft = current.draft.copy(
                    poll = (current.draft.poll ?: ComposePollDraft()).copy(
                        expiresAt = expiresAt.trim().takeIf { it.isNotEmpty() },
                    ),
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun selectPollDeadlinePreset(
        preset: ComposePollDeadlinePreset,
        nowEpochMillis: Long,
    ) {
        updateDraftState { current ->
            current.copy(
                draft = current.draft.copy(
                    poll = (current.draft.poll ?: ComposePollDraft()).copy(
                        expiresAt = preset.toExpiresAtIso(nowEpochMillis),
                    ),
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun addPollChoice() {
        updateDraftState { current ->
            val poll = current.draft.poll ?: ComposePollDraft()
            if (poll.choices.size >= MAX_POLL_CHOICES) {
                current
            } else {
                current.copy(
                    draft = current.draft.copy(
                        poll = poll.copy(choices = poll.choices + ""),
                    ),
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun removePollChoice(index: Int) {
        updateDraftState { current ->
            val poll = current.draft.poll
            if (poll == null || index !in poll.choices.indices) {
                current
            } else {
                val nextChoices = poll.choices
                    .filterIndexed { choiceIndex, _ -> choiceIndex != index }
                    .let { choices ->
                        if (choices.size < MIN_POLL_CHOICES) {
                            choices + List(MIN_POLL_CHOICES - choices.size) { "" }
                        } else {
                            choices
                        }
                    }
                current.copy(
                    draft = current.draft.copy(
                        poll = poll.copy(choices = nextChoices),
                    ),
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun addFileIds(fileIds: List<String>) {
        val cleanFileIds = fileIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (cleanFileIds.isEmpty()) return

        updateDraftState {
            it.copy(
                draft = it.draft.copy(
                    fileIds = (it.draft.fileIds + cleanFileIds).distinct().take(MAX_FILE_COUNT),
                ),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun attachDriveFile(file: DriveFile) {
        if (state.value.draft.fileIds.size >= MAX_FILE_COUNT && state.value.draft.fileIds.none { it == file.id }) {
            mutableState.update {
                it.copy(errorMessage = "最多只能添加 $MAX_FILE_COUNT 个附件", requiresRelogin = false)
            }
            return
        }

        updateDraftState {
            it.copy(
                draft = it.draft.copy(
                    fileIds = (it.draft.fileIds + file.id).distinct().take(MAX_FILE_COUNT),
                ),
                attachedFiles = (it.attachedFiles + file)
                    .distinctBy { attachedFile -> attachedFile.id }
                    .take(MAX_FILE_COUNT),
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun removeFileId(fileId: String) {
        updateDraftState {
            it.copy(
                draft = it.draft.copy(fileIds = it.draft.fileIds.filterNot { id -> id == fileId }),
                attachedFiles = it.attachedFiles.filterNot { file -> file.id == fileId },
                updatingFileIds = it.updatingFileIds - fileId,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun uploadMedia(upload: DriveFileUpload) {
        val current = state.value
        if (current.draft.fileIds.size + pendingMediaUploads.size >= MAX_FILE_COUNT) {
            mutableState.update {
                it.copy(errorMessage = "最多只能添加 $MAX_FILE_COUNT 个附件", requiresRelogin = false)
            }
            return
        }
        if (current.isUploadingMedia) {
            pendingMediaUploads.addLast(upload)
            return
        }
        uploadMediaNow(upload)
    }

    private fun uploadMediaNow(upload: DriveFileUpload) {
        val uploadRepository = driveFileRepository
        if (uploadRepository == null) {
            mutableState.update {
                it.copy(errorMessage = "无法上传媒体")
            }
            return
        }
        val uploadSessionId = draftSessionId

        mutableState.update {
            it.copy(
                isUploadingMedia = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = uploadRepository.upload(upload)) {
                is DriveFileRepositoryResult.Success -> updateDraftState {
                    if (uploadSessionId != draftSessionId) {
                        return@updateDraftState it.copy(isUploadingMedia = false)
                    }
                    it.copy(
                        draft = it.draft.copy(
                            fileIds = (it.draft.fileIds + result.file.id).distinct().take(MAX_FILE_COUNT),
                        ),
                        attachedFiles = (it.attachedFiles + result.file)
                            .distinctBy { file -> file.id }
                            .take(MAX_FILE_COUNT),
                        isUploadingMedia = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update {
                    if (uploadSessionId != draftSessionId) return@update it.copy(isUploadingMedia = false)
                    it.copy(
                        isUploadingMedia = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update {
                    if (uploadSessionId != draftSessionId) return@update it.copy(isUploadingMedia = false)
                    it.copy(
                        isUploadingMedia = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is DriveFileRepositoryResult.Error -> mutableState.update {
                    if (uploadSessionId != draftSessionId) return@update it.copy(isUploadingMedia = false)
                    it.copy(
                        isUploadingMedia = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
            if (uploadSessionId == draftSessionId) {
                uploadNextPendingMedia()
            } else {
                pendingMediaUploads.clear()
            }
        }
    }

    private fun uploadNextPendingMedia() {
        val nextUpload = pendingMediaUploads.removeFirstOrNull() ?: return
        if (state.value.draft.fileIds.size >= MAX_FILE_COUNT) {
            pendingMediaUploads.clear()
            mutableState.update {
                it.copy(errorMessage = "最多只能添加 $MAX_FILE_COUNT 个附件", requiresRelogin = false)
            }
            return
        }
        uploadMediaNow(nextUpload)
    }

    fun updateAttachedFileMetadata(
        fileId: String,
        comment: String?,
        isSensitive: Boolean,
    ) {
        val cleanFileId = fileId.trim()
        if (cleanFileId.isEmpty()) return
        val updateRepository = driveFileRepository
        if (updateRepository == null) {
            mutableState.update {
                it.copy(errorMessage = "当前版本不支持编辑媒体信息")
            }
            return
        }
        if (state.value.updatingFileIds.contains(cleanFileId)) return
        val cleanComment = comment?.trim()?.takeIf { it.isNotEmpty() }

        mutableState.update {
            it.copy(
                updatingFileIds = it.updatingFileIds + cleanFileId,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val result = updateRepository.updateFile(
                    fileId = cleanFileId,
                    comment = cleanComment,
                    isSensitive = isSensitive,
                )
            ) {
                is DriveManagementRepositoryResult.FileUpdated -> mutableState.update { current ->
                    current.copy(
                        attachedFiles = current.attachedFiles.map { file ->
                            if (file.id == cleanFileId) result.file else file
                        },
                        updatingFileIds = current.updatingFileIds - cleanFileId,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                DriveManagementRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        updatingFileIds = it.updatingFileIds - cleanFileId,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveManagementRepositoryResult.ValidationError -> mutableState.update {
                    it.copy(
                        updatingFileIds = it.updatingFileIds - cleanFileId,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is DriveManagementRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        updatingFileIds = it.updatingFileIds - cleanFileId,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is DriveManagementRepositoryResult.FileDeleted,
                is DriveManagementRepositoryResult.FolderCreated,
                is DriveManagementRepositoryResult.FolderUpdated,
                is DriveManagementRepositoryResult.FolderDeleted -> mutableState.update {
                    it.copy(
                        updatingFileIds = it.updatingFileIds - cleanFileId,
                        errorMessage = "媒体信息更新失败",
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun reportMediaUploadError(message: String) {
        val cleanMessage = message.trim().takeIf { it.isNotEmpty() } ?: "无法读取所选文件"
        mutableState.update {
            it.copy(
                errorMessage = cleanMessage,
                requiresRelogin = false,
            )
        }
    }

    fun updateLimits(
        maxTextLength: Int,
        maxCwLength: Int,
    ) {
        mutableState.update {
            it.copy(
                maxTextLength = maxTextLength.takeIf { value -> value > 0 } ?: DEFAULT_MAX_TEXT_LENGTH,
                maxCwLength = maxCwLength.takeIf { value -> value > 0 } ?: DEFAULT_MAX_CW_LENGTH,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun updateCapabilities(
        canPublicNote: Boolean,
        canScheduleNotes: Boolean = state.value.canScheduleNotes,
    ) {
        updateDraftState { current ->
            if (current.canPublicNote == canPublicNote && current.canScheduleNotes == canScheduleNotes) {
                current
            } else {
                current.copy(
                    canPublicNote = canPublicNote,
                    canScheduleNotes = canScheduleNotes,
                    draft = current.draft.copy(
                        visibility = if (!canPublicNote && current.draft.visibility == NoteVisibility.Public) {
                            defaultVisibility(canPublicNote = false)
                        } else {
                            current.draft.visibility
                        },
                        scheduleNote = if (canScheduleNotes) current.draft.scheduleNote else null,
                    ),
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
        }
    }

    fun editScheduledNote(note: ComposeScheduledNote) {
        val cleanNoteId = note.id.trim()
        if (cleanNoteId.isEmpty() || state.value.deletingScheduledNoteIds.contains(cleanNoteId)) return
        mutableState.update {
            it.copy(
                deletingScheduledNoteIds = it.deletingScheduledNoteIds + cleanNoteId,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.deleteScheduledNote(cleanNoteId)) {
                ComposeScheduleDeleteRepositoryResult.Success -> {
                    val nextDraft = note.toDraft(
                        canPublicNote = state.value.canPublicNote,
                        canScheduleNotes = state.value.canScheduleNotes,
                    )
                    mutableState.update {
                        it.copy(
                            draft = nextDraft,
                            attachedFiles = note.attachedFiles,
                            scheduledNotes = it.scheduledNotes.filterNot { scheduledNote -> scheduledNote.id == cleanNoteId },
                            deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                            updatingFileIds = emptySet(),
                            errorMessage = null,
                            createdNoteId = null,
                            requiresRelogin = false,
                        )
                    }
                    persistDraft(nextDraft)
                }
                ComposeScheduleDeleteRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ComposeScheduleDeleteRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun send() {
        val current = state.value
        if (current.isSending || current.isResolvingVisibleUsers) return

        if (
            current.draft.visibility == NoteVisibility.Specified &&
            current.draft.visibleUserIds.any { it.toComposeVisibleUserMention() != null }
        ) {
            resolveVisibleUserMentions(sendAfterResolve = true)
            return
        }

        val validationError = current.validationError()
        if (validationError != null) {
            mutableState.update {
                it.copy(
                    isSending = false,
                    errorMessage = validationError,
                    createdNoteId = null,
                    requiresRelogin = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isSending = true,
                errorMessage = null,
                createdNoteId = null,
                requiresRelogin = false,
            )
        }

        val draft = current.draft
        val sendSessionId = draftSessionId
        scope.launch {
            when (val result = repository.send(draft)) {
                is ComposeRepositoryResult.Success -> {
                    mutableState.update { latest ->
                        if (sendSessionId != draftSessionId || latest.draft != draft) {
                            latest.copy(
                                isSending = false,
                                errorMessage = null,
                                createdNoteId = result.createdNoteId,
                                requiresRelogin = false,
                            )
                        } else {
                            draftSessionId += 1
                            runCatching { draftStore.clearDraft() }
                            latest.copy(
                                draft = newDraft(latest.canPublicNote),
                                attachedFiles = emptyList(),
                                updatingFileIds = emptySet(),
                                isSending = false,
                                errorMessage = null,
                                createdNoteId = result.createdNoteId,
                                requiresRelogin = false,
                            )
                        }
                    }
                }
                ComposeRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ComposeRepositoryResult.ValidationError -> mutableState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
                is ComposeRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun consumeCreatedNote() {
        mutableState.update { it.copy(createdNoteId = null, requiresRelogin = false) }
    }

    fun loadScheduledNotes() {
        if (state.value.isLoadingScheduledNotes) return
        val requestId = ++scheduledNotesRequestId
        mutableState.update {
            it.copy(
                isLoadingScheduledNotes = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.listScheduledNotes()) {
                is ComposeScheduledNotesRepositoryResult.Success -> mutableState.update {
                    if (requestId != scheduledNotesRequestId) return@update it
                    it.copy(
                        scheduledNotes = result.notes,
                        isLoadingScheduledNotes = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ComposeScheduledNotesRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != scheduledNotesRequestId) return@update it
                    it.copy(
                        isLoadingScheduledNotes = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ComposeScheduledNotesRepositoryResult.Error -> mutableState.update {
                    if (requestId != scheduledNotesRequestId) return@update it
                    it.copy(
                        isLoadingScheduledNotes = false,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun deleteScheduledNote(noteId: String) {
        val cleanNoteId = noteId.trim()
        if (cleanNoteId.isEmpty() || state.value.deletingScheduledNoteIds.contains(cleanNoteId)) return
        mutableState.update {
            it.copy(
                deletingScheduledNoteIds = it.deletingScheduledNoteIds + cleanNoteId,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.deleteScheduledNote(cleanNoteId)) {
                ComposeScheduleDeleteRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        scheduledNotes = it.scheduledNotes.filterNot { note -> note.id == cleanNoteId },
                        deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                ComposeScheduleDeleteRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is ComposeScheduleDeleteRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        deletingScheduledNoteIds = it.deletingScheduledNoteIds - cleanNoteId,
                        errorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun ComposeUiState.validationError(): String? {
        return when {
            draft.text.isBlank() && draft.fileIds.isEmpty() -> "内容不能为空"
            draft.text.length > maxTextLength -> "内容不能超过 $maxTextLength 字"
            draft.cw.orEmpty().length > maxCwLength -> "内容警告不能超过 $maxCwLength 字"
            !canPublicNote && draft.visibility == NoteVisibility.Public -> "实例未启用公开发帖"
            draft.scheduleNote != null && !canScheduleNotes -> "实例未启用预约发帖"
            draft.scheduleNote != null && draft.scheduleNote.scheduledAt <= currentEpochMillis() -> "预约时间必须晚于当前时间"
            draft.visibility == NoteVisibility.Specified && draft.visibleUserIds.isEmpty() -> {
                "指定可见至少需要 1 个用户"
            }
            draft.visibility == NoteVisibility.Specified &&
                draft.visibleUserIds.any { it.toComposeVisibleUserMention() != null } -> {
                "指定用户解析中"
            }
            draft.poll != null && draft.poll.choices.count { it.isNotBlank() } < 2 -> "投票至少需要 2 个选项"
            else -> null
        }
    }

    private fun defaultVisibility(canPublicNote: Boolean): NoteVisibility {
        return if (canPublicNote) NoteVisibility.Public else NoteVisibility.Home
    }

    private fun newDraft(canPublicNote: Boolean): ComposeDraft {
        return ComposeDraft(visibility = defaultVisibility(canPublicNote))
    }

    private fun currentEpochMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }

    private fun updateDraftState(transform: (ComposeUiState) -> ComposeUiState) {
        var nextDraft: ComposeDraft? = null
        mutableState.update { current ->
            transform(current).also { next -> nextDraft = next.draft }
        }
        nextDraft?.let(::persistDraft)
    }

    private fun persistDraft(draft: ComposeDraft) {
        runCatching { draftStore.saveDraft(draft) }
    }

    private fun invalidateDraftSession() {
        draftSessionId += 1
        visibleUserResolveRequestId += 1
        pendingMediaUploads.clear()
    }

    private fun ComposeDraft.sanitizedForCapabilities(
        canPublicNote: Boolean,
        canScheduleNotes: Boolean,
    ): ComposeDraft {
        val nextVisibility = if (!canPublicNote && visibility == NoteVisibility.Public) {
            defaultVisibility(canPublicNote = false)
        } else {
            visibility
        }
        return copy(
            visibility = nextVisibility,
            visibleUserIds = if (nextVisibility == NoteVisibility.Specified) visibleUserIds else emptyList(),
            scheduleNote = if (canScheduleNotes) scheduleNote else null,
        )
    }

    private fun ComposeScheduledNote.toDraft(
        canPublicNote: Boolean,
        canScheduleNotes: Boolean,
    ): ComposeDraft {
        return ComposeDraft(
            text = text,
            visibility = visibility,
            visibleUserIds = visibleUserIds,
            cw = cw,
            replyId = replyId,
            renoteId = renoteId,
            channelId = channelId,
            fileIds = fileIds,
            poll = poll,
            localOnly = localOnly,
            reactionAcceptance = reactionAcceptance,
            scheduleNote = scheduledAt?.takeIf { canScheduleNotes }?.let(::ComposeScheduleDraft),
        ).sanitizedForCapabilities(
            canPublicNote = canPublicNote,
            canScheduleNotes = canScheduleNotes,
        )
    }

    private companion object {
        const val DEFAULT_MAX_TEXT_LENGTH = 3000
        const val DEFAULT_MAX_CW_LENGTH = 500
        const val MAX_FILE_COUNT = 16
        const val MIN_POLL_CHOICES = 2
        const val MAX_POLL_CHOICES = 10
    }
}
