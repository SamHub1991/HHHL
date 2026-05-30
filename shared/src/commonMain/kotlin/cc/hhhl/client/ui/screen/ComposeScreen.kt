package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposeReactionAcceptance
import cc.hhhl.client.api.ComposeScheduleDraft
import cc.hhhl.client.api.ComposeScheduledNote
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.model.User
import cc.hhhl.client.presentation.notePreviewText
import cc.hhhl.client.presentation.truncateRichTextPreviewText
import cc.hhhl.client.state.ComposeCompletionKind
import cc.hhhl.client.state.ComposeCompletionUiState
import cc.hhhl.client.state.ComposeFailedSend
import cc.hhhl.client.state.ComposePollDeadlinePreset
import cc.hhhl.client.state.ComposeUiState
import cc.hhhl.client.state.isComposeVisibleUserMention
import cc.hhhl.client.state.toComposeVisibleUserTokens
import cc.hhhl.client.state.toExpiresAtIso
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AiResultPanel
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.CustomEmojiPicker
import cc.hhhl.client.ui.component.CustomEmojiReactionLabel
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Composable
fun ComposeScreen(
    state: ComposeUiState? = null,
    onTextChanged: (String) -> Unit = {},
    onCwChanged: (String?) -> Unit = {},
    onVisibilitySelected: (NoteVisibility) -> Unit = {},
    onLocalOnlyChanged: (Boolean) -> Unit = {},
    onReactionAcceptanceSelected: (ComposeReactionAcceptance) -> Unit = {},
    onScheduleAtChanged: (Long?) -> Unit = {},
    onInsertText: (String) -> Unit = {},
    onResetDraft: () -> Unit = {},
    onLoadScheduledNotes: () -> Unit = {},
    onEditScheduledNote: (ComposeScheduledNote) -> Unit = {},
    onDeleteScheduledNote: (String) -> Unit = {},
    onVisibleUserIdsChanged: (String) -> Unit = {},
    onResolveVisibleUserMentions: () -> Unit = {},
    onPollEnabledChanged: (Boolean) -> Unit = {},
    onPollChoiceChanged: (Int, String) -> Unit = { _, _ -> },
    onPollMultipleChanged: (Boolean) -> Unit = {},
    onPollExpiresAtChanged: (String) -> Unit = {},
    onPollDeadlinePresetSelected: (ComposePollDeadlinePreset, Long) -> Unit = { _, _ -> },
    onPollChoiceAdded: () -> Unit = {},
    onPollChoiceRemoved: (Int) -> Unit = {},
    onAddMedia: () -> Unit = {},
    onOpenDrivePicker: () -> Unit = {},
    onRemoveFileId: (String) -> Unit = {},
    onAttachedFileMetadataChanged: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    isMediaPickerAvailable: Boolean = false,
    customEmojis: List<CustomEmoji> = emptyList(),
    recentEmojiCodes: List<String> = emptyList(),
    completionState: ComposeCompletionUiState = ComposeCompletionUiState(),
    onCompletionTokenChanged: (ComposeCompletionKind?, String) -> Unit = { _, _ -> },
    targetNote: Note? = null,
    onSend: () -> Unit = {},
    onRetryFailedSend: (String) -> Unit = {},
    onRestoreFailedSend: (String) -> Unit = {},
    onRemoveFailedSend: (String) -> Unit = {},
    aiEnabled: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    isAiProcessing: Boolean = false,
    onAiAction: (AiTaskKind, ComposeDraft, Note?) -> Unit = { _, _, _ -> },
    onDismissAiResult: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var localDraft by remember { mutableStateOf(ComposeDraft()) }
    var pendingRemoveFileId by remember { mutableStateOf<String?>(null) }
    var removePollDialogOpen by remember { mutableStateOf(false) }
    var scheduleEditorOpen by remember { mutableStateOf(false) }
    var scheduledNotesDialogOpen by remember { mutableStateOf(false) }
    var resetDraftDialogOpen by remember { mutableStateOf(false) }
    val draft = state?.draft ?: localDraft
    val maxTextLength = state?.maxTextLength ?: 3000
    val maxCwLength = state?.maxCwLength ?: 500
    val canPublicNote = state?.canPublicNote ?: true
    val canScheduleNotes = state?.canScheduleNotes ?: true
    val isSending = state?.isSending ?: false
    val isUploadingMedia = state?.isUploadingMedia ?: false
    val isResolvingVisibleUsers = state?.isResolvingVisibleUsers ?: false
    val errorMessage = state?.errorMessage
    val attachedFileById = state?.attachedFiles?.associateBy { it.id }.orEmpty()
    val isReply = draft.replyId != null
    val isQuote = draft.renoteId != null
    val isEditingScheduled = draft.editId != null
    val isScheduledDraft = draft.scheduleNote != null
    val targetKind = when {
        isReply -> ComposeTargetKind.Reply
        isQuote -> ComposeTargetKind.Quote
        else -> null
    }
    val targetPreview = targetKind?.let { composeTargetPreview(targetNote, it) }
    val isChannelNote = draft.channelId != null
    val poll = draft.poll
    val sendEnabled = (draft.text.isNotBlank() || draft.fileIds.isNotEmpty()) &&
        draft.text.length <= maxTextLength &&
        draft.cw.orEmpty().length <= maxCwLength &&
        draft.visibility.canSendWithVisibleUsers(draft.visibleUserIds) &&
        !isSending &&
        !isResolvingVisibleUsers
    val textUpdater: (String) -> Unit = { value ->
        if (state != null) {
            onTextChanged(value)
        } else {
            localDraft = localDraft.copy(text = value)
        }
    }
    val cwUpdater: (String?) -> Unit = { value ->
        if (state != null) {
            onCwChanged(value)
        } else {
            localDraft = localDraft.copy(cw = value)
        }
    }
    val visibilityUpdater: (NoteVisibility) -> Unit = { value ->
        if (state != null) {
            onVisibilitySelected(value)
        } else {
            localDraft = localDraft.copy(
                visibility = value,
                visibleUserIds = if (value == NoteVisibility.Specified) localDraft.visibleUserIds else emptyList(),
            )
        }
    }
    val localOnlyUpdater: (Boolean) -> Unit = { value ->
        if (state != null) {
            onLocalOnlyChanged(value)
        } else {
            localDraft = localDraft.copy(localOnly = value)
        }
    }
    val reactionAcceptanceUpdater: (ComposeReactionAcceptance) -> Unit = { value ->
        if (state != null) {
            onReactionAcceptanceSelected(value)
        } else {
            localDraft = localDraft.copy(reactionAcceptance = value)
        }
    }
    val scheduleAtUpdater: (Long?) -> Unit = { value ->
        if (state != null) {
            onScheduleAtChanged(value)
        } else {
            localDraft = localDraft.copy(scheduleNote = value?.let(::ComposeScheduleDraft))
        }
    }
    val insertTextUpdater: (String) -> Unit = { fragment ->
        if (state != null) {
            onInsertText(fragment)
        } else {
            val cleanFragment = fragment.trim()
            if (cleanFragment.isNotEmpty()) {
                val separator = if (localDraft.text.isBlank() || localDraft.text.endsWith(" ")) "" else " "
                localDraft = localDraft.copy(text = localDraft.text + separator + cleanFragment)
            }
        }
    }
    val resetDraftUpdater: () -> Unit = {
        if (state != null) {
            onResetDraft()
        } else {
            localDraft = ComposeDraft(visibility = if (canPublicNote) NoteVisibility.Public else NoteVisibility.Home)
        }
    }
    val visibleUserIdsUpdater: (String) -> Unit = { value ->
        if (state != null) {
            onVisibleUserIdsChanged(value)
        } else {
            localDraft = localDraft.copy(visibleUserIds = value.toComposeVisibleUserTokens())
        }
    }
    val pollEnabledUpdater: (Boolean) -> Unit = { value ->
        if (state != null) {
            onPollEnabledChanged(value)
        } else {
            localDraft = localDraft.copy(
                poll = if (value) localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft() else null,
            )
        }
    }
    val pollChoiceUpdater: (Int, String) -> Unit = { index, value ->
        if (state != null) {
            onPollChoiceChanged(index, value)
        } else {
            val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
            val choices = currentPoll.choices.toMutableList()
            while (choices.size <= index) {
                choices.add("")
            }
            choices[index] = value
            localDraft = localDraft.copy(poll = currentPoll.copy(choices = choices))
        }
    }
    val pollMultipleUpdater: (Boolean) -> Unit = { value ->
        if (state != null) {
            onPollMultipleChanged(value)
        } else {
            val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
            localDraft = localDraft.copy(poll = currentPoll.copy(multiple = value))
        }
    }
    val pollExpiresAtUpdater: (String) -> Unit = { value ->
        if (state != null) {
            onPollExpiresAtChanged(value)
        } else {
            val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
            localDraft = localDraft.copy(poll = currentPoll.copy(expiresAt = value.trim().takeIf { it.isNotEmpty() }))
        }
    }
    val pollChoiceAdder: () -> Unit = {
        if (state != null) {
            onPollChoiceAdded()
        } else {
            val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
            if (currentPoll.choices.size < 10) {
                localDraft = localDraft.copy(poll = currentPoll.copy(choices = currentPoll.choices + ""))
            }
        }
    }
    val pollChoiceRemover: (Int) -> Unit = { index ->
        if (state != null) {
            onPollChoiceRemoved(index)
        } else {
            val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
            val nextChoices = currentPoll.choices
                .filterIndexed { choiceIndex, _ -> choiceIndex != index }
                .let { choices -> if (choices.size < 2) choices + List(2 - choices.size) { "" } else choices }
            localDraft = localDraft.copy(poll = currentPoll.copy(choices = nextChoices))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = when {
                isEditingScheduled -> "编辑帖子"
                isScheduledDraft -> "预约发帖"
                isReply -> "回复"
                isQuote -> "引用"
                isChannelNote -> "频道发帖"
                else -> "发帖"
            },
            supportingText = when {
                isSending -> "正在发布"
                isEditingScheduled -> "修改现有帖子"
                isScheduledDraft -> "预约内容"
                draft.fileIds.isNotEmpty() -> "含 ${draft.fileIds.size} 个文件"
                poll != null -> "含投票"
                else -> "草稿"
            },
            navigation = { HhhlBackButton(onClick = onBack, label = "取消发帖") },
            action = {
                HhhlActionChip(
                    label = if (isSending) "发送中" else "发送",
                    emphasized = true,
                    enabled = sendEnabled,
                    onClick = onSend,
                )
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "compose-summary", contentType = "compose-summary") {
                ComposeSummaryRow(
                    draft = draft,
                    targetPreview = targetPreview,
                    isReply = isReply,
                    isQuote = isQuote,
                    isChannelNote = isChannelNote,
                    isUploadingMedia = isUploadingMedia,
                    maxTextLength = maxTextLength,
                )
            }
            item(key = "compose-editor", contentType = "compose-editor") {
                ComposeEditorSection(
                    draft = draft,
                    pollEnabled = poll != null,
                    aiEnabled = aiEnabled,
                    isAiProcessing = isAiProcessing,
                    onAiAction = { kind -> onAiAction(kind, draft, targetNote) },
                    isSending = isSending,
                    isUploadingMedia = isUploadingMedia,
                    isMediaPickerAvailable = isMediaPickerAvailable,
                    isResolvingVisibleUsers = isResolvingVisibleUsers,
                    customEmojis = customEmojis,
                    recentEmojiCodes = recentEmojiCodes,
                    completionState = completionState,
                    onCompletionTokenChanged = onCompletionTokenChanged,
                    onTextChanged = textUpdater,
                    onCwChanged = cwUpdater,
                    onAddMedia = onAddMedia,
                    onOpenDrivePicker = onOpenDrivePicker,
                    onTogglePoll = {
                        if (poll == null) {
                            pollEnabledUpdater(true)
                        } else {
                            removePollDialogOpen = true
                        }
                    },
                    onInsertText = insertTextUpdater,
                    onResolveVisibleUserMentions = onResolveVisibleUserMentions,
                )
            }
            if (!aiResultText.isNullOrBlank()) {
                item(key = "compose-ai-result", contentType = "compose-ai-result") {
                    ComposeAiResultPanel(
                        label = aiResultLabel ?: "AI 结果",
                        text = aiResultText,
                        onUse = { textUpdater(aiResultText) },
                        onAppend = { insertTextUpdater(aiResultText) },
                        onUseAsCw = { cwUpdater(aiResultText) },
                        onDismiss = onDismissAiResult,
                    )
                }
            }
            if (draft.fileIds.isNotEmpty()) {
                item(key = "compose-attachments", contentType = "compose-attachments") {
                    ComposeAttachmentSection(
                        fileIds = draft.fileIds,
                        attachedFileById = attachedFileById,
                        updatingFileIds = state?.updatingFileIds.orEmpty(),
                        onRemoveFileId = { pendingRemoveFileId = it },
                        onAttachedFileMetadataChanged = onAttachedFileMetadataChanged,
                    )
                }
            }
            poll?.let { pollDraft ->
                item(key = "compose-poll", contentType = "compose-poll") {
                    ComposePollSection(
                        pollDraft = pollDraft,
                        pollChoiceUpdater = pollChoiceUpdater,
                        pollChoiceRemover = pollChoiceRemover,
                        pollMultipleUpdater = pollMultipleUpdater,
                        pollChoiceAdder = pollChoiceAdder,
                        onRemovePoll = { removePollDialogOpen = true },
                        pollExpiresAtUpdater = pollExpiresAtUpdater,
                        state = state,
                        localDraft = localDraft,
                        onPollDeadlinePresetSelected = onPollDeadlinePresetSelected,
                        onLocalDraftChanged = { localDraft = it },
                    )
                }
            }
            item(key = "compose-visibility", contentType = "compose-visibility") {
                ComposeVisibilitySection(
                    draft = draft,
                    canPublicNote = canPublicNote,
                    isResolvingVisibleUsers = isResolvingVisibleUsers,
                    onVisibilitySelected = visibilityUpdater,
                    onVisibleUserIdsChanged = visibleUserIdsUpdater,
                    onResolveVisibleUserMentions = onResolveVisibleUserMentions,
                )
            }
            item(key = "compose-web-actions", contentType = "compose-web-actions") {
                ComposeWebActionSection(
                    draft = draft,
                    canScheduleNotes = canScheduleNotes,
                    pollEnabled = poll != null,
                    onLocalOnlyChanged = localOnlyUpdater,
                    onReactionAcceptanceSelected = reactionAcceptanceUpdater,
                    onToggleCw = { cwUpdater(if (draft.cw == null) "" else null) },
                    onTogglePoll = {
                        if (poll == null) {
                            pollEnabledUpdater(true)
                        } else {
                            removePollDialogOpen = true
                        }
                    },
                    onInsertMention = { insertTextUpdater("@") },
                    onInsertHashtag = { insertTextUpdater("#") },
                    onOpenScheduleEditor = { scheduleEditorOpen = true },
                    onClearSchedule = { scheduleAtUpdater(null) },
                    onOpenScheduledNotes = {
                        scheduledNotesDialogOpen = true
                        onLoadScheduledNotes()
                    },
                    onResetDraft = { resetDraftDialogOpen = true },
                )
            }
            errorMessage?.let { message ->
                item(key = "compose-error", contentType = "compose-error") {
                    val colors = LocalHhhlColors.current
                    HhhlInlinePanel(
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = message,
                            color = colors.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (state?.restoredDraft == true) {
                item(key = "compose-draft-restored", contentType = "compose-status") {
                    HhhlInlinePanel(
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = "已恢复草稿",
                            color = LocalHhhlColors.current.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!state?.failedSendQueue.isNullOrEmpty()) {
                item(key = "compose-failed-send-queue", contentType = "compose-failed-send-queue") {
                    ComposeFailedSendQueueSection(
                        queue = state?.failedSendQueue.orEmpty(),
                        onRetry = onRetryFailedSend,
                        onRestore = onRestoreFailedSend,
                        onRemove = onRemoveFailedSend,
                    )
                }
            }
        }
        HhhlDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val colors = LocalHhhlColors.current
                Text(
                    text = composeEditorStatusParts(draft).joinToString(" · "),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${draft.text.length}/$maxTextLength",
                    color = if (draft.text.length > maxTextLength) {
                        colors.danger
                    } else {
                        colors.textSecondary
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    pendingRemoveFileId?.let { fileId ->
        HhhlAlertDialog(
            onDismissRequest = { pendingRemoveFileId = null },
            title = { Text("移除附件") },
            text = {
                val colors = LocalHhhlColors.current
                Text(
                    text = "附件会从当前发帖草稿移除，不会删除云端文件。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        onRemoveFileId(fileId)
                        pendingRemoveFileId = null
                    },
                    destructive = true,
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { pendingRemoveFileId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (removePollDialogOpen) {
        HhhlAlertDialog(
            onDismissRequest = { removePollDialogOpen = false },
            title = { Text("移除投票") },
            text = {
                val colors = LocalHhhlColors.current
                Text(
                    text = "当前投票选项、截止时间和多选设置会从草稿中移除。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        pollEnabledUpdater(false)
                        removePollDialogOpen = false
                    },
                    destructive = true,
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { removePollDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (scheduleEditorOpen) {
        ComposeScheduleDialog(
            scheduledAt = draft.scheduleNote?.scheduledAt,
            onDismiss = { scheduleEditorOpen = false },
            onConfirm = {
                scheduleAtUpdater(it)
                scheduleEditorOpen = false
            },
        )
    }

    if (scheduledNotesDialogOpen) {
        ComposeScheduledNotesDialog(
            notes = state?.scheduledNotes.orEmpty(),
            isLoading = state?.isLoadingScheduledNotes ?: false,
            deletingNoteIds = state?.deletingScheduledNoteIds.orEmpty(),
            onRefresh = onLoadScheduledNotes,
            onEdit = {
                onEditScheduledNote(it)
                scheduledNotesDialogOpen = false
            },
            onDelete = onDeleteScheduledNote,
            onDismiss = { scheduledNotesDialogOpen = false },
        )
    }

    if (resetDraftDialogOpen) {
        HhhlAlertDialog(
            onDismissRequest = { resetDraftDialogOpen = false },
            title = { Text("重置发帖") },
            text = {
                val colors = LocalHhhlColors.current
                Text(
                    text = "当前文字、附件、投票和发帖设置都会清空。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        resetDraftUpdater()
                        resetDraftDialogOpen = false
                    },
                    destructive = true,
                ) {
                    Text("重置")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { resetDraftDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ComposeWebActionSection(
    draft: ComposeDraft,
    canScheduleNotes: Boolean,
    pollEnabled: Boolean,
    onLocalOnlyChanged: (Boolean) -> Unit,
    onReactionAcceptanceSelected: (ComposeReactionAcceptance) -> Unit,
    onToggleCw: () -> Unit,
    onTogglePoll: () -> Unit,
    onInsertMention: () -> Unit,
    onInsertHashtag: () -> Unit,
    onOpenScheduleEditor: () -> Unit,
    onClearSchedule: () -> Unit,
    onOpenScheduledNotes: () -> Unit,
    onResetDraft: () -> Unit,
) {
    ComposeSection(
        title = "发帖选项",
        supportingText = composePostOptionSummary(draft),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlActionChip(
                label = if (draft.localOnly) "仅本站" else "可联合",
                emphasized = draft.localOnly,
                onClick = { onLocalOnlyChanged(!draft.localOnly) },
            )
            HhhlActionChip(
                label = if (draft.cw != null) "关闭 CW" else "内容警告",
                emphasized = draft.cw != null,
                onClick = onToggleCw,
            )
            HhhlActionChip(
                label = if (pollEnabled) "移除投票" else "投票",
                emphasized = pollEnabled,
                onClick = onTogglePoll,
            )
            HhhlActionChip(label = "@", onClick = onInsertMention)
            HhhlActionChip(label = "#", onClick = onInsertHashtag)
            HhhlActionChip(
                label = "回应：${draft.reactionAcceptance.label}",
                onClick = {
                    onReactionAcceptanceSelected(draft.reactionAcceptance.next())
                },
            )
            HhhlActionChip(
                label = draft.scheduleNote?.scheduledAt?.let { "预约 ${it.toCompactLocalDateTime()}" } ?: "预约发布",
                emphasized = draft.scheduleNote != null,
                enabled = canScheduleNotes,
                onClick = onOpenScheduleEditor,
            )
            if (draft.scheduleNote != null) {
                HhhlActionChip(
                    label = "取消预约",
                    onClick = onClearSchedule,
                )
            }
            HhhlActionChip(
                label = "预约列表",
                enabled = canScheduleNotes,
                onClick = onOpenScheduledNotes,
            )
            HhhlActionChip(
                label = "重置",
                emphasized = true,
                onClick = onResetDraft,
            )
        }
    }
}

@Composable
private fun ComposeFailedSendQueueSection(
    queue: List<ComposeFailedSend>,
    onRetry: (String) -> Unit,
    onRestore: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    ComposeSection(
        title = "发送失败队列",
        supportingText = "${queue.size} 条待处理",
        compact = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            queue.forEach { item ->
                ComposeFailedSendRow(
                    item = item,
                    onRetry = onRetry,
                    onRestore = onRestore,
                    onRemove = onRemove,
                )
            }
        }
    }
}

@Composable
private fun ComposeFailedSendRow(
    item: ComposeFailedSend,
    onRetry: (String) -> Unit,
    onRestore: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val preview = composeFailedSendPreview(item.draft)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.inputBackground.copy(alpha = if (colors.surface.luminance() < 0.2f) 0.32f else 0.58f))
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = preview,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.message,
            color = colors.danger,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlActionChip(
                label = if (item.isRetrying) "重试中" else "重试",
                emphasized = true,
                enabled = !item.isRetrying,
                onClick = { onRetry(item.id) },
            )
            HhhlActionChip(
                label = "载入编辑器",
                enabled = !item.isRetrying,
                onClick = { onRestore(item.id) },
            )
            HhhlActionChip(
                label = "移除",
                enabled = !item.isRetrying,
                onClick = { onRemove(item.id) },
            )
        }
    }
}

fun composeFailedSendPreview(draft: ComposeDraft): String {
    val text = draft.text.trim().takeIf { it.isNotEmpty() }
    val parts = buildList {
        text?.let { add(it) }
        if (draft.fileIds.isNotEmpty()) add("${draft.fileIds.size} 个附件")
        if (draft.poll != null) add("投票")
        if (draft.replyId != null) add("回复")
        if (draft.renoteId != null) add("引用")
        if (draft.channelId != null) add("频道")
    }
    return parts.joinToString(" · ").ifBlank { "未发送草稿" }.truncateRichTextPreviewText(160)
}

@Composable
private fun ComposeEditorSection(
    draft: ComposeDraft,
    pollEnabled: Boolean,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiAction: (AiTaskKind) -> Unit,
    isSending: Boolean,
    isUploadingMedia: Boolean,
    isMediaPickerAvailable: Boolean,
    isResolvingVisibleUsers: Boolean,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    completionState: ComposeCompletionUiState,
    onCompletionTokenChanged: (ComposeCompletionKind?, String) -> Unit,
    onTextChanged: (String) -> Unit,
    onCwChanged: (String?) -> Unit,
    onAddMedia: () -> Unit,
    onOpenDrivePicker: () -> Unit,
    onTogglePoll: () -> Unit,
    onInsertText: (String) -> Unit,
    onResolveVisibleUserMentions: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf(ComposeEditorMode.Edit) }
    LaunchedEffect(draft.text) {
        onCompletionTokenChanged(null, "")
    }
    fun insertMarker(marker: String) {
        val needsLeadingSpace = draft.text.isNotEmpty() && !draft.text.last().isWhitespace()
        onTextChanged(draft.text + if (needsLeadingSpace) " $marker" else marker)
    }
    ComposeSection(
        title = "正文",
        supportingText = if (draft.cw != null) "已启用内容警告" else "支持 Markdown、表情、提及和话题",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (colors.surface.luminance() < 0.2f) {
                        colors.pageBackground.copy(alpha = 0.34f)
                    } else {
                        colors.inputBackground.copy(alpha = 0.74f)
                    },
                )
                .border(
                    1.dp,
                    colors.border.copy(alpha = 0.48f),
                    RoundedCornerShape(18.dp),
                )
                .padding(composeEditorSurfaceSpec().contentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = composeEditorModeLabel(ComposeEditorMode.Edit),
                        emphasized = editorMode == ComposeEditorMode.Edit,
                        onClick = { editorMode = ComposeEditorMode.Edit },
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Visibility,
                        contentDescription = composeEditorModeLabel(ComposeEditorMode.Preview),
                        emphasized = editorMode == ComposeEditorMode.Preview,
                        onClick = { editorMode = ComposeEditorMode.Preview },
                    )
                }
                HhhlOverflowMenu(
                    actions = composeSecondaryActions(
                        cwEnabled = draft.cw != null,
                        pollEnabled = pollEnabled,
                        aiEnabled = aiEnabled,
                        isAiProcessing = isAiProcessing,
                        onAiAction = onAiAction,
                        onToggleCw = { onCwChanged(if (draft.cw == null) "" else null) },
                        onTogglePoll = onTogglePoll,
                    ),
                    enabled = !isSending,
                    label = "编辑器更多操作",
                )
            }

            if (editorMode == ComposeEditorMode.Edit) {
                draft.cw?.let { cw ->
                    ComposePlainInput(
                        value = cw,
                        onValueChange = onCwChanged,
                        placeholder = "内容警告",
                        minHeight = 28,
                        singleLine = true,
                    )
                }
                ComposePlainInput(
                    value = draft.text,
                    onValueChange = onTextChanged,
                    placeholder = "有什么新想法？",
                    minHeight = composeEditorSurfaceSpec().bodyMinHeight,
                    singleLine = false,
                )
                ComposeInsertedEmojiPreview(text = draft.text)
            } else {
                ComposeMarkdownPreview(
                    text = draft.text,
                    cw = draft.cw,
                    minHeight = composeEditorSurfaceSpec().bodyMinHeight,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Image,
                        contentDescription = when {
                            isUploadingMedia -> "上传中"
                            draft.fileIds.isEmpty() -> "添加媒体"
                            else -> "添加媒体，已选 ${draft.fileIds.size} 个文件"
                        },
                        enabled = isMediaPickerAvailable && !isSending && !isUploadingMedia,
                        onClick = onAddMedia,
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.AttachFile,
                        contentDescription = "从 Drive 添加附件",
                        enabled = !isSending && !isUploadingMedia,
                        onClick = onOpenDrivePicker,
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.EmojiEmotions,
                        contentDescription = if (emojiPickerOpen) "收起表情" else "选择表情",
                        enabled = !isSending,
                        emphasized = emojiPickerOpen,
                        onClick = { emojiPickerOpen = !emojiPickerOpen },
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Poll,
                        contentDescription = if (pollEnabled) "移除投票" else "添加投票",
                        enabled = !isSending,
                        emphasized = pollEnabled,
                        onClick = onTogglePoll,
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.AlternateEmail,
                        contentDescription = "插入提及",
                        enabled = !isSending,
                        onClick = { insertMarker("@") },
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Tag,
                        contentDescription = "插入话题",
                        enabled = !isSending,
                        onClick = { insertMarker("#") },
                    )
                    if (draft.visibility == NoteVisibility.Specified &&
                        draft.visibleUserIds.any { it.isComposeVisibleUserMention() }
                    ) {
                        HhhlActionChip(
                            label = if (isResolvingVisibleUsers) "解析中" else "解析用户",
                            emphasized = true,
                            enabled = !isResolvingVisibleUsers,
                            onClick = onResolveVisibleUserMentions,
                        )
                    }
                }
            }
            Text(
                text = "${draft.text.length} 字",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
            )
            if (emojiPickerOpen) {
                CustomEmojiPicker(
                    customEmojis = customEmojis,
                    recentEmojiCodes = recentEmojiCodes,
                    onEmojiSelected = { emoji ->
                        onInsertText(emoji)
                        emojiPickerOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ComposeInsertedEmojiPreview(text: String) {
    val colors = LocalHhhlColors.current
    val emojis = remember(text) { text.composeInsertedEmojiCodes() }
    if (emojis.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.58f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = 0.36f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.inputBackground.copy(alpha = 0.72f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                CustomEmojiReactionLabel(reaction = emoji)
            }
        }
    }
}

@Composable
private fun ComposeAiResultPanel(
    label: String,
    text: String,
    onUse: () -> Unit,
    onAppend: () -> Unit,
    onUseAsCw: () -> Unit,
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        modifier = Modifier.padding(horizontal = 16.dp),
        actions = {
            HhhlActionChip(label = "替换正文", emphasized = true, onClick = onUse)
            HhhlActionChip(label = "追加", onClick = onAppend)
            HhhlActionChip(label = "作为 CW", onClick = onUseAsCw)
        },
    )
}

@Composable
private fun ComposeMarkdownPreview(
    text: String,
    cw: String?,
    minHeight: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = LocalHhhlColors.current
        cw?.takeIf { it.isNotBlank() }?.let { warning ->
            Text(
                text = warning,
                color = colors.danger,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (text.isBlank()) {
            Text(
                text = "预览会在输入内容后显示",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            InlineRichText(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ComposeCompletionPanel(
    token: ComposeCompletionToken?,
    state: ComposeCompletionUiState,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    onHashtagSelected: (String) -> Unit,
    onUserSelected: (User) -> Unit,
    onEmojiSelected: (CustomEmoji) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val visibleToken = token ?: return
    val emojis = remember(customEmojis, recentEmojiCodes, visibleToken) {
        if (visibleToken.kind != ComposeCompletionKind.Emoji) {
            emptyList()
        } else {
            val query = visibleToken.query.trim()
            val recentNames = recentEmojiCodes
                .map { it.trim(':') }
                .filter { it.isNotBlank() }
            val ordered = (recentNames.mapNotNull { recentName ->
                customEmojis.firstOrNull { it.name.equals(recentName, ignoreCase = true) }
            } + customEmojis).distinctBy { it.name }
            ordered
                .filter { emoji ->
                    query.isBlank() ||
                        emoji.name.contains(query, ignoreCase = true) ||
                        emoji.aliases.any { it.contains(query, ignoreCase = true) }
                }
                .take(8)
        }
    }
    val showHashtags = visibleToken.kind == ComposeCompletionKind.Hashtag &&
        state.activeKind == ComposeCompletionKind.Hashtag &&
        (state.hashtags.isNotEmpty() || state.isLoading || state.errorMessage != null)
    val showUsers = visibleToken.kind == ComposeCompletionKind.Mention &&
        state.activeKind == ComposeCompletionKind.Mention &&
        (state.users.isNotEmpty() || state.isLoading || state.errorMessage != null || visibleToken.query.isBlank())
    val showEmojis = visibleToken.kind == ComposeCompletionKind.Emoji && emojis.isNotEmpty()
    if (!showHashtags && !showUsers && !showEmojis) return

    HhhlInlinePanel(
        emphasized = true,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (visibleToken.kind) {
            ComposeCompletionKind.Hashtag -> {
                state.hashtags.forEach { trend ->
                    ComposeHashtagCompletionRow(trend = trend, onClick = { onHashtagSelected(trend.tag) })
                }
            }
            ComposeCompletionKind.Mention -> {
                if (visibleToken.query.isBlank() && !state.isLoading && state.users.isEmpty()) {
                    Text(
                        text = "继续输入用户名",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                state.users.forEach { user ->
                    ComposeUserCompletionRow(user = user, onClick = { onUserSelected(user) })
                }
            }
            ComposeCompletionKind.Emoji -> {
                emojis.forEach { emoji ->
                    ComposeEmojiCompletionRow(emoji = emoji, onClick = { onEmojiSelected(emoji) })
                }
            }
        }
        if (state.isLoading) {
            Text(
                text = "搜索中",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = colors.danger,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ComposeHashtagCompletionRow(
    trend: TrendingHashtag,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            color = colors.accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trend.tag,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${trend.usersCount} 人正在使用",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComposeUserCompletionRow(
    user: User,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = user.avatarInitial,
            avatarUrl = user.avatarUrl,
            size = 30.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.composeHandleText(),
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComposeEmojiCompletionRow(
    emoji: CustomEmoji,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.inputBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ":",
                color = colors.accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emoji.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            emoji.category?.takeIf { it.isNotBlank() }?.let { category ->
                Text(
                    text = category,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ComposeScheduleDialog(
    scheduledAt: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = scheduledAt ?: (currentEpochMillis() + ONE_DAY_MILLIS)
    var date by remember(scheduledAt) { mutableStateOf(initial.toLocalDateInput()) }
    var time by remember(scheduledAt) { mutableStateOf(initial.toLocalTimeInput()) }
    val parsed = remember(date, time) { parseLocalScheduleMillis(date, time) }

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预约发布") },
        text = {
            val colors = LocalHhhlColors.current
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = date,
                    onValueChange = { date = it },
                    placeholder = "日期 YYYY-MM-DD",
                    singleLine = true,
                )
                HhhlTextInput(
                    value = time,
                    onValueChange = { time = it },
                    placeholder = "时间 HH:mm",
                    singleLine = true,
                )
                Text(
                    text = if (parsed == null) {
                        "请输入有效的本地日期和时间。"
                    } else {
                        "将于 ${parsed.toCompactLocalDateTime()} 发布。"
                    },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            HhhlTextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onConfirm) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ComposeScheduledNotesDialog(
    notes: List<ComposeScheduledNote>,
    isLoading: Boolean,
    deletingNoteIds: Set<String>,
    onRefresh: () -> Unit,
    onEdit: (ComposeScheduledNote) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预约列表") },
        text = {
            val colors = LocalHhhlColors.current
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    isLoading -> Text("加载中", color = colors.textMuted)
                    notes.isEmpty() -> Text("暂无预约帖子", color = colors.textMuted)
                    else -> notes.forEach { note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                InlineRichText(
                                    text = composeScheduledNotePreviewText(note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxChars = 220,
                                )
                                Text(
                                    text = note.scheduledAt?.toCompactLocalDateTime() ?: note.visibility.label,
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            HhhlIconActionButton(
                                icon = Icons.Filled.Edit,
                                contentDescription = "编辑预约",
                                enabled = !deletingNoteIds.contains(note.id),
                                onClick = { onEdit(note) },
                            )
                            HhhlIconActionButton(
                                icon = Icons.Filled.Delete,
                                contentDescription = "删除预约",
                                enabled = !deletingNoteIds.contains(note.id),
                                onClick = { onDelete(note.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            HhhlTextButton(onClick = onRefresh) {
                Text(if (isLoading) "加载中" else "刷新")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ComposePlainInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Int,
    singleLine: Boolean,
    fieldValue: TextFieldValue? = null,
    onFieldValueChange: ((TextFieldValue) -> Unit)? = null,
) {
    val colors = LocalHhhlColors.current
    BasicTextField(
        value = fieldValue ?: TextFieldValue(value, selection = TextRange(value.length)),
        onValueChange = { nextValue ->
            if (onFieldValueChange != null) {
                onFieldValueChange(nextValue)
            } else {
                onValueChange(nextValue.text)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.textPrimary,
        ),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                innerTextField()
            }
        },
    )
}

data class ComposeEditorSurfaceSpec(
    val cornerRadius: Int,
    val contentPadding: Int,
    val bodyMinHeight: Int,
)

fun composeEditorSurfaceSpec(): ComposeEditorSurfaceSpec = ComposeEditorSurfaceSpec(
    cornerRadius = 20,
    contentPadding = 14,
    bodyMinHeight = 184,
)

enum class ComposeEditorMode {
    Edit,
    Preview,
}

fun composeEditorModeLabel(mode: ComposeEditorMode): String = when (mode) {
    ComposeEditorMode.Edit -> "编辑"
    ComposeEditorMode.Preview -> "预览"
}

private data class ComposeCompletionToken(
    val kind: ComposeCompletionKind,
    val start: Int,
    val end: Int,
    val query: String,
)

private fun TextFieldValue.activeComposeCompletionToken(): ComposeCompletionToken? {
    val cursor = selection.end.coerceIn(0, text.length)
    if (cursor != selection.start || cursor == 0) return null
    var start = cursor - 1
    while (start >= 0 && !text[start].isComposeTokenBoundary()) {
        start--
    }
    start += 1
    if (start >= cursor) return null
    val marker = text[start]
    val kind = when (marker) {
        '#' -> ComposeCompletionKind.Hashtag
        '@' -> ComposeCompletionKind.Mention
        ':' -> ComposeCompletionKind.Emoji
        else -> return null
    }
    if (start > 0 && !text[start - 1].isComposeCompletionPrefixBoundary()) return null
    val query = text.substring(start + 1, cursor)
    if (kind == ComposeCompletionKind.Emoji && query.contains(':')) return null
    return ComposeCompletionToken(kind = kind, start = start, end = cursor, query = query)
}

private fun TextFieldValue.replaceComposeCompletionToken(
    token: ComposeCompletionToken,
    replacement: String,
): TextFieldValue {
    val cleanStart = token.start.coerceIn(0, text.length)
    val cleanEnd = token.end.coerceIn(cleanStart, text.length)
    val nextText = text.replaceRange(cleanStart, cleanEnd, replacement)
    val nextCursor = cleanStart + replacement.length
    return copy(text = nextText, selection = TextRange(nextCursor))
}

private fun TextFieldValue.insertComposeText(fragment: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    val prefix = text.take(start)
    val suffix = text.drop(end)
    val needsLeadingSpace = prefix.isNotEmpty() && !prefix.last().isWhitespace()
    val insertion = buildString {
        if (needsLeadingSpace) append(' ')
        append(fragment)
    }
    val nextText = prefix + insertion + suffix
    val nextCursor = prefix.length + insertion.length
    return copy(text = nextText, selection = TextRange(nextCursor))
}

private fun String.composeInsertedEmojiCodes(): List<String> {
    return ComposeCustomEmojiCodeRegex
        .findAll(this)
        .map { it.value }
        .distinct()
        .take(12)
        .toList()
}

private val ComposeCustomEmojiCodeRegex = Regex(":[A-Za-z0-9_@.-]+:")

private fun Char.isComposeTokenBoundary(): Boolean {
    return isWhitespace() || this in listOf('(', ')', '[', ']', '{', '}', '<', '>', '"', '\'', '`')
}

private fun Char.isComposeCompletionPrefixBoundary(): Boolean {
    return isWhitespace() || this in listOf('(', '[', '{', '<', '"', '\'', '`')
}

private fun User.composeHandleText(): String {
    return "@$username" + host?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
}

private fun User.composeMentionText(): String = composeHandleText() + " "

@Composable
private fun ComposeSummaryRow(
    draft: ComposeDraft,
    targetPreview: ComposeTargetPreview?,
    isReply: Boolean,
    isQuote: Boolean,
    isChannelNote: Boolean,
    isUploadingMedia: Boolean,
    maxTextLength: Int,
) {
    val primaryText = when {
        draft.editId != null -> "编辑帖子"
        draft.scheduleNote != null -> "预约帖子"
        targetPreview != null -> targetPreview.title
        isReply -> "回复帖子"
        isQuote -> "引用帖子"
        isChannelNote -> "发布到频道"
        else -> "新帖"
    }
    val secondaryText = buildList {
        add(draft.visibility.label)
        if (draft.fileIds.isNotEmpty()) add("${draft.fileIds.size} 个文件")
        if (draft.poll != null) add("含投票")
        if (draft.cw != null) add("含内容警告")
        if (isUploadingMedia) add("上传中")
        add("${draft.text.length}/$maxTextLength")
    }.joinToString(" · ")
    ComposeSection(
        title = primaryText,
        supportingText = secondaryText,
        compact = true,
    ) {
        targetPreview?.let { preview ->
            ComposeTargetPreviewCard(preview = preview)
        } ?: FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlActionChip(label = draft.visibility.label, emphasized = true, onClick = {})
            if (draft.localOnly) {
                HhhlActionChip(label = "仅本站", emphasized = true, onClick = {})
            }
            if (draft.scheduleNote != null) {
                HhhlActionChip(label = "预约", emphasized = true, onClick = {})
            }
            if (draft.fileIds.isNotEmpty()) {
                HhhlActionChip(label = "${draft.fileIds.size} 个附件", onClick = {})
            }
            if (draft.poll != null) {
                HhhlActionChip(label = "投票", onClick = {})
            }
        }
    }
}

@Composable
private fun ComposeVisibilitySection(
    draft: ComposeDraft,
    canPublicNote: Boolean,
    isResolvingVisibleUsers: Boolean,
    onVisibilitySelected: (NoteVisibility) -> Unit,
    onVisibleUserIdsChanged: (String) -> Unit,
    onResolveVisibleUserMentions: () -> Unit,
) {
    ComposeSection(
        title = "可见范围",
    ) {
        ComposeVisibilitySelector(
            selectedVisibility = draft.visibility,
            canPublicNote = canPublicNote,
            onVisibilitySelected = onVisibilitySelected,
        )
        if (draft.visibility == NoteVisibility.Specified) {
            HhhlTextInput(
                value = draft.visibleUserIds.joinToString(" "),
                onValueChange = onVisibleUserIdsChanged,
                placeholder = "@用户名 或用户 ID，可用空格、逗号或换行分隔",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = false,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = composeVisibleUserResolutionText(
                        visibleUserIds = draft.visibleUserIds,
                        isResolving = isResolvingVisibleUsers,
                    ),
                    color = LocalHhhlColors.current.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                HhhlActionChip(
                    label = if (isResolvingVisibleUsers) "解析中" else "解析",
                    emphasized = true,
                    enabled = !isResolvingVisibleUsers &&
                        draft.visibleUserIds.any { it.isComposeVisibleUserMention() },
                    onClick = onResolveVisibleUserMentions,
                )
            }
        }
    }
}

@Composable
private fun ComposeAttachmentSection(
    fileIds: List<String>,
    attachedFileById: Map<String, cc.hhhl.client.model.DriveFile>,
    updatingFileIds: Set<String>,
    onRemoveFileId: (String) -> Unit,
    onAttachedFileMetadataChanged: (String, String?, Boolean) -> Unit,
) {
    ComposeSection(
        title = "附件",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fileIds.forEachIndexed { index, fileId ->
                val attachedFile = attachedFileById[fileId]
                var localComment by remember(fileId, attachedFile?.comment) {
                    mutableStateOf(attachedFile?.comment.orEmpty())
                }
                var localSensitive by remember(fileId, attachedFile?.isSensitive) {
                    mutableStateOf(attachedFile?.isSensitive ?: false)
                }
                val isUpdating = updatingFileIds.contains(fileId)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalHhhlColors.current.buttonBackground)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = attachedFile?.name?.takeIf { it.isNotBlank() } ?: "附件 ${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        HhhlOverflowMenu(
                            actions = composeAttachmentActions(
                                onRemoveFile = { onRemoveFileId(fileId) },
                            ),
                            label = "附件操作",
                        )
                    }
                    HhhlTextInput(
                        value = localComment,
                        onValueChange = { localComment = it },
                        placeholder = "图片说明 / 替代文本",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUpdating,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HhhlCheckbox(
                                checked = localSensitive,
                                onCheckedChange = { localSensitive = it },
                                enabled = !isUpdating,
                            )
                            Text("敏感内容", style = MaterialTheme.typography.bodySmall)
                        }
                        HhhlActionChip(
                            label = if (isUpdating) "保存中" else "保存",
                            emphasized = true,
                            enabled = !isUpdating && attachedFile != null,
                            onClick = {
                                onAttachedFileMetadataChanged(fileId, localComment, localSensitive)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposePollSection(
    pollDraft: cc.hhhl.client.api.ComposePollDraft,
    pollChoiceUpdater: (Int, String) -> Unit,
    pollChoiceRemover: (Int) -> Unit,
    pollMultipleUpdater: (Boolean) -> Unit,
    pollChoiceAdder: () -> Unit,
    onRemovePoll: () -> Unit,
    pollExpiresAtUpdater: (String) -> Unit,
    state: ComposeUiState?,
    localDraft: ComposeDraft,
    onPollDeadlinePresetSelected: (ComposePollDeadlinePreset, Long) -> Unit,
    onLocalDraftChanged: (ComposeDraft) -> Unit,
) {
    ComposeSection(
        title = "投票",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pollDraft.choices.forEachIndexed { index, choice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlTextInput(
                        value = choice,
                        onValueChange = { pollChoiceUpdater(index, it) },
                        placeholder = "投票选项 ${index + 1}",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    HhhlOverflowMenu(
                        actions = composePollChoiceActions(
                            onRemoveChoice = { pollChoiceRemover(index) },
                        ),
                        label = "投票选项操作",
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HhhlCheckbox(
                        checked = pollDraft.multiple,
                        onCheckedChange = pollMultipleUpdater,
                    )
                    Text("允许多选", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HhhlActionChip(
                        label = "加选项",
                        enabled = pollDraft.choices.size < 10,
                        onClick = pollChoiceAdder,
                    )
                    HhhlOverflowMenu(
                        actions = composePollSectionActions(
                            onRemovePoll = onRemovePoll,
                        ),
                        label = "投票更多操作",
                    )
                }
            }
            HhhlTextInput(
                value = pollDraft.expiresAt.orEmpty(),
                onValueChange = pollExpiresAtUpdater,
                placeholder = "截止时间 ISO，可留空",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ComposePollDeadlinePreset.entries.forEach { preset ->
                    HhhlActionChip(
                        label = preset.label,
                        onClick = {
                            if (state != null) {
                                onPollDeadlinePresetSelected(preset, currentEpochMillis())
                            } else {
                                val currentPoll = localDraft.poll ?: cc.hhhl.client.api.ComposePollDraft()
                                onLocalDraftChanged(
                                    localDraft.copy(
                                        poll = currentPoll.copy(
                                            expiresAt = preset.toExpiresAtIso(currentEpochMillis()),
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeSection(
    title: String,
    supportingText: String? = null,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val shape = RoundedCornerShape(if (compact) 18.dp else 20.dp)
    val containerColor = if (isDarkSurface) {
        colors.noteBackground.copy(alpha = 0.82f)
    } else {
        colors.surface.copy(alpha = 0.92f)
    }
    val borderColor = if (isDarkSurface) {
        colors.border.copy(alpha = 0.34f)
    } else {
        colors.border.copy(alpha = 0.64f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(
                elevation = if (isDarkSurface) 3.dp else 1.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            supportingText?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        content()
    }
}

@Composable
private fun ComposeVisibilitySelector(
    selectedVisibility: NoteVisibility,
    canPublicNote: Boolean,
    onVisibilitySelected: (NoteVisibility) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        composeVisibilityOptions(canPublicNote = canPublicNote).forEach { visibility ->
            HhhlActionChip(
                label = visibility.label,
                emphasized = visibility == selectedVisibility,
                enabled = visibility != selectedVisibility,
                onClick = { onVisibilitySelected(visibility) },
            )
        }
    }
}

fun composeVisibilityOptions(canPublicNote: Boolean = true): List<NoteVisibility> {
    return NoteVisibility.entries.filterNot { visibility ->
        visibility == NoteVisibility.Public && !canPublicNote
    }
}

fun composeSecondaryActions(
    cwEnabled: Boolean,
    pollEnabled: Boolean,
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    onAiAction: (AiTaskKind) -> Unit = {},
    onToggleCw: () -> Unit,
    onTogglePoll: () -> Unit,
): List<HhhlOverflowMenuAction> = buildList {
    add(
        HhhlOverflowMenuAction(
            label = if (cwEnabled) "关闭内容警告" else "内容警告",
            onClick = onToggleCw,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = if (pollEnabled) "移除投票" else "添加投票",
            destructive = pollEnabled,
            onClick = onTogglePoll,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = if (isAiProcessing) "AI 处理中" else "AI",
            enabled = aiEnabled && !isAiProcessing,
            icon = Icons.Filled.AutoAwesome,
            onClick = {},
            children = listOf(
                HhhlOverflowMenuAction(label = "润色", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposePolish) }),
                HhhlOverflowMenuAction(label = "结合最近帖子生成", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeFromRecentPosts) }),
                HhhlOverflowMenuAction(label = "缩短", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeShorten) }),
                HhhlOverflowMenuAction(label = "扩写", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeExpand) }),
                HhhlOverflowMenuAction(label = "翻译中文", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeTranslateZh) }),
                HhhlOverflowMenuAction(label = "生成 CW", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeContentWarning) }),
                HhhlOverflowMenuAction(label = "推荐话题", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeHashtags) }),
                HhhlOverflowMenuAction(label = "推荐 @", icon = Icons.Filled.AutoAwesome, onClick = { onAiAction(AiTaskKind.ComposeMentionSuggestions) }),
            ),
        ),
    )
}

fun composeAttachmentActions(
    onRemoveFile: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "移除附件",
        destructive = true,
        onClick = onRemoveFile,
    ),
)

fun composePollChoiceActions(
    onRemoveChoice: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "删除选项",
        destructive = true,
        onClick = onRemoveChoice,
    ),
)

fun composePollSectionActions(
    onRemovePoll: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "移除投票",
        destructive = true,
        onClick = onRemovePoll,
    ),
)

fun composeEditorStatusParts(draft: ComposeDraft): List<String> = buildList {
    add(draft.visibility.label)
    if (draft.editId != null) add("编辑帖子")
    if (draft.localOnly) add("仅本站")
    if (draft.channelId != null) add("频道")
    if (draft.replyId != null) add("回复")
    if (draft.renoteId != null) add("引用")
    if (draft.cw != null) add("内容警告")
    if (draft.fileIds.isNotEmpty()) add("${draft.fileIds.size} 个文件")
    if (draft.poll != null) add("投票已开启")
    if (draft.scheduleNote != null) add("预约发布")
}

fun composePostOptionSummary(draft: ComposeDraft): String = buildList {
    if (draft.editId != null) add("编辑中")
    add(if (draft.localOnly) "仅本站" else "可联合")
    add("回应 ${draft.reactionAcceptance.label}")
    draft.scheduleNote?.scheduledAt?.let { add("预约 ${it.toCompactLocalDateTime()}") }
}.joinToString(" · ")

fun composeVisibleUserResolutionText(
    visibleUserIds: List<String>,
    isResolving: Boolean,
): String {
    val unresolvedCount = visibleUserIds.count { it.isComposeVisibleUserMention() }
    val resolvedCount = visibleUserIds.size - unresolvedCount
    return when {
        isResolving -> "解析中"
        visibleUserIds.isEmpty() -> "指定用户"
        unresolvedCount > 0 && resolvedCount > 0 -> "已指定 $resolvedCount · 待解析 $unresolvedCount"
        unresolvedCount > 0 -> "待解析 $unresolvedCount"
        else -> "已指定 ${visibleUserIds.size} 个用户。"
    }
}

internal expect fun currentEpochMillis(): Long

enum class ComposeTargetKind {
    Reply,
    Quote,
}

data class ComposeTargetPreview(
    val title: String,
    val body: String,
)

fun composeTargetPreview(
    note: Note?,
    kind: ComposeTargetKind,
): ComposeTargetPreview? {
    if (note == null) return null
    val titlePrefix = when (kind) {
        ComposeTargetKind.Reply -> "回复"
        ComposeTargetKind.Quote -> "引用"
    }
    val body = notePreviewText(
        note = note,
        fallback = note.media.takeIf { it.isNotEmpty() }?.let { "${it.size} 个附件" } ?: "这条动态",
    )
    return ComposeTargetPreview(
        title = "$titlePrefix @${note.author.username}",
        body = body.truncateRichTextPreviewText(260),
    )
}

@Composable
private fun ComposeTargetPreviewCard(preview: ComposeTargetPreview) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accent),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = preview.title,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                InlineRichText(
                    text = preview.body,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxChars = 260,
                )
            }
        }
    }
}

fun composeTargetPreviewUsesMarkdownQuoteRail(): Boolean = true

internal fun composeScheduledNotePreviewText(note: ComposeScheduledNote): String {
    return notePreviewText(text = note.text, cw = note.cw, fallback = "无正文")
}

private val NoteVisibility.label: String
    get() = when (this) {
        NoteVisibility.Public -> "公开"
        NoteVisibility.Home -> "首页"
        NoteVisibility.Followers -> "关注者"
        NoteVisibility.Specified -> "指定"
    }

private val ComposeReactionAcceptance.label: String
    get() = when (this) {
        ComposeReactionAcceptance.LikeOnly -> "仅喜欢"
        ComposeReactionAcceptance.LikeOnlyForRemote -> "远端仅喜欢"
        ComposeReactionAcceptance.NonSensitiveOnly -> "非敏感"
        ComposeReactionAcceptance.NonSensitiveOnlyForLocalLikeOnlyForRemote -> "本站非敏感/远端喜欢"
    }

private fun ComposeReactionAcceptance.next(): ComposeReactionAcceptance {
    val entries = ComposeReactionAcceptance.entries
    return entries[(entries.indexOf(this) + 1) % entries.size]
}

private fun NoteVisibility.canSendWithVisibleUsers(visibleUserIds: List<String>): Boolean {
    return this != NoteVisibility.Specified || visibleUserIds.isNotEmpty()
}

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

private fun Long.toCompactLocalDateTime(): String {
    val value = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.year}-${value.monthNumber.twoDigits()}-${value.dayOfMonth.twoDigits()} " +
        "${value.hour.twoDigits()}:${value.minute.twoDigits()}"
}

private fun Long.toLocalDateInput(): String {
    val value = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.year}-${value.monthNumber.twoDigits()}-${value.dayOfMonth.twoDigits()}"
}

private fun Long.toLocalTimeInput(): String {
    val value = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.hour.twoDigits()}:${value.minute.twoDigits()}"
}

private fun parseLocalScheduleMillis(
    date: String,
    time: String,
): Long? {
    val dateParts = date.trim().split("-").mapNotNull { it.toIntOrNull() }
    val timeParts = time.trim().split(":").mapNotNull { it.toIntOrNull() }
    if (dateParts.size != 3 || timeParts.size < 2) return null
    return runCatching {
        LocalDateTime(
            year = dateParts[0],
            monthNumber = dateParts[1],
            dayOfMonth = dateParts[2],
            hour = timeParts[0],
            minute = timeParts[1],
        ).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull()
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
