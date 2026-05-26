package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.state.ComposePollDeadlinePreset
import cc.hhhl.client.state.ComposeUiState
import cc.hhhl.client.state.isComposeVisibleUserMention
import cc.hhhl.client.state.toComposeVisibleUserTokens
import cc.hhhl.client.state.toExpiresAtIso
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.CustomEmojiPicker
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun ComposeScreen(
    state: ComposeUiState? = null,
    onTextChanged: (String) -> Unit = {},
    onCwChanged: (String?) -> Unit = {},
    onVisibilitySelected: (NoteVisibility) -> Unit = {},
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
    onRemoveFileId: (String) -> Unit = {},
    onAttachedFileMetadataChanged: (String, String?, Boolean) -> Unit = { _, _, _ -> },
    isMediaPickerAvailable: Boolean = false,
    customEmojis: List<CustomEmoji> = emptyList(),
    recentEmojiCodes: List<String> = emptyList(),
    targetNote: Note? = null,
    onSend: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var localDraft by remember { mutableStateOf(ComposeDraft()) }
    var pendingRemoveFileId by remember { mutableStateOf<String?>(null) }
    var removePollDialogOpen by remember { mutableStateOf(false) }
    val draft = state?.draft ?: localDraft
    val maxTextLength = state?.maxTextLength ?: 3000
    val maxCwLength = state?.maxCwLength ?: 500
    val canPublicNote = state?.canPublicNote ?: true
    val isSending = state?.isSending ?: false
    val isUploadingMedia = state?.isUploadingMedia ?: false
    val isResolvingVisibleUsers = state?.isResolvingVisibleUsers ?: false
    val errorMessage = state?.errorMessage
    val attachedFileById = state?.attachedFiles?.associateBy { it.id }.orEmpty()
    val isReply = draft.replyId != null
    val isQuote = draft.renoteId != null
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
                isReply -> "回复"
                isQuote -> "引用"
                isChannelNote -> "频道发帖"
                else -> "发帖"
            },
            supportingText = when {
                isSending -> "正在发布"
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
        ComposeSummaryRow(
            draft = draft,
            targetPreview = targetPreview,
            isReply = isReply,
            isQuote = isQuote,
            isChannelNote = isChannelNote,
            isUploadingMedia = isUploadingMedia,
            maxTextLength = maxTextLength,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ComposeVisibilitySection(
                    draft = draft,
                    canPublicNote = canPublicNote,
                    isResolvingVisibleUsers = isResolvingVisibleUsers,
                    onVisibilitySelected = visibilityUpdater,
                    onVisibleUserIdsChanged = visibleUserIdsUpdater,
                    onResolveVisibleUserMentions = onResolveVisibleUserMentions,
                )
            }
            item {
                ComposeEditorSection(
                    draft = draft,
                    pollEnabled = poll != null,
                    isSending = isSending,
                    isUploadingMedia = isUploadingMedia,
                    isMediaPickerAvailable = isMediaPickerAvailable,
                    isResolvingVisibleUsers = isResolvingVisibleUsers,
                    customEmojis = customEmojis,
                    recentEmojiCodes = recentEmojiCodes,
                    onTextChanged = textUpdater,
                    onCwChanged = cwUpdater,
                    onAddMedia = onAddMedia,
                    onTogglePoll = {
                        if (poll == null) {
                            pollEnabledUpdater(true)
                        } else {
                            removePollDialogOpen = true
                        }
                    },
                    onResolveVisibleUserMentions = onResolveVisibleUserMentions,
                )
            }
            if (draft.fileIds.isNotEmpty()) {
                item {
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
                item {
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
            errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
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
                Text(
                    text = composeEditorStatusParts(draft).joinToString(" · "),
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${draft.text.length}/$maxTextLength",
                    color = if (draft.text.length > maxTextLength) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    pendingRemoveFileId?.let { fileId ->
        AlertDialog(
            onDismissRequest = { pendingRemoveFileId = null },
            title = { Text("移除附件") },
            text = {
                Text(
                    text = "附件会从当前发帖草稿移除，不会删除云端文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFileId(fileId)
                        pendingRemoveFileId = null
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveFileId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (removePollDialogOpen) {
        AlertDialog(
            onDismissRequest = { removePollDialogOpen = false },
            title = { Text("移除投票") },
            text = {
                Text(
                    text = "当前投票选项、截止时间和多选设置会从草稿中移除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pollEnabledUpdater(false)
                        removePollDialogOpen = false
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { removePollDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ComposeEditorSection(
    draft: ComposeDraft,
    pollEnabled: Boolean,
    isSending: Boolean,
    isUploadingMedia: Boolean,
    isMediaPickerAvailable: Boolean,
    isResolvingVisibleUsers: Boolean,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    onTextChanged: (String) -> Unit,
    onCwChanged: (String?) -> Unit,
    onAddMedia: () -> Unit,
    onTogglePoll: () -> Unit,
    onResolveVisibleUserMentions: () -> Unit,
) {
    var emojiPickerOpen by remember { mutableStateOf(false) }
    ComposeSection(
        title = "编辑器",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(composeEditorSurfaceSpec().cornerRadius.dp))
                .background(LocalHhhlColors.current.inputBackground)
                .padding(composeEditorSurfaceSpec().contentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                    HhhlActionChip(
                        label = when {
                            isUploadingMedia -> "上传中"
                            draft.fileIds.isEmpty() -> "文件"
                            else -> "文件 ${draft.fileIds.size}"
                        },
                        enabled = isMediaPickerAvailable && !isSending && !isUploadingMedia,
                        onClick = onAddMedia,
                    )
                    HhhlActionChip(
                        label = if (emojiPickerOpen) "收起表情" else "表情",
                        enabled = !isSending,
                        onClick = { emojiPickerOpen = !emojiPickerOpen },
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
                HhhlOverflowMenu(
                    actions = composeSecondaryActions(
                        cwEnabled = draft.cw != null,
                        pollEnabled = pollEnabled,
                        onToggleCw = { onCwChanged(if (draft.cw == null) "" else null) },
                        onTogglePoll = onTogglePoll,
                    ),
                    enabled = !isSending,
                    label = "编辑器更多操作",
                )
            }
        }
    }
}

@Composable
private fun ComposePlainInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Int,
    singleLine: Boolean,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = LocalHhhlColors.current.subtleText,
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
    cornerRadius = 14,
    contentPadding = 12,
    bodyMinHeight = 172,
)

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
    val summaryText = "$primaryText · $secondaryText"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryText,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    targetPreview?.let { preview ->
        ComposeTargetPreviewCard(preview = preview)
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
                    color = LocalHhhlColors.current.subtleText,
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
                        .background(LocalHhhlColors.current.inputBackground)
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
                            Checkbox(
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
                    Checkbox(
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
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            supportingText?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    color = LocalHhhlColors.current.subtleText,
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
    onToggleCw: () -> Unit,
    onTogglePoll: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (cwEnabled) "关闭内容警告" else "内容警告",
        onClick = onToggleCw,
    ),
    HhhlOverflowMenuAction(
        label = if (pollEnabled) "移除投票" else "添加投票",
        destructive = pollEnabled,
        onClick = onTogglePoll,
    ),
)

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
    if (draft.channelId != null) add("频道")
    if (draft.replyId != null) add("回复")
    if (draft.renoteId != null) add("引用")
    if (draft.cw != null) add("内容警告")
    if (draft.fileIds.isNotEmpty()) add("${draft.fileIds.size} 个文件")
    if (draft.poll != null) add("投票已开启")
}

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
    return ComposeTargetPreview(
        title = "$titlePrefix @${note.author.username}",
        body = note.cw
            ?.takeIf { it.isNotBlank() }
            ?: note.text.takeIf { it.isNotBlank() }
            ?: note.media.takeIf { it.isNotEmpty() }?.let { "${it.size} 个附件" }
            ?: "这条动态",
    )
}

@Composable
private fun ComposeTargetPreviewCard(preview: ComposeTargetPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalHhhlColors.current.inputBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = preview.title,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = preview.body,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun composeTargetPreviewUsesMarkdownQuoteRail(): Boolean = true

private val NoteVisibility.label: String
    get() = when (this) {
        NoteVisibility.Public -> "公开"
        NoteVisibility.Home -> "首页"
        NoteVisibility.Followers -> "关注者"
        NoteVisibility.Specified -> "指定"
    }

private fun NoteVisibility.canSendWithVisibleUsers(visibleUserIds: List<String>): Boolean {
    return this != NoteVisibility.Specified || visibleUserIds.isNotEmpty()
}
