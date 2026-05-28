package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Clip
import cc.hhhl.client.model.ClipListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.state.ClipUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlProgressIndicator
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun ClipScreen(
    state: ClipUiState? = null,
    onBack: () -> Unit,
    onRefreshClips: () -> Unit = {},
    onRefreshNotes: () -> Unit = {},
    onCreateClip: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onUpdateSelectedClip: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteSelectedClip: () -> Unit = {},
    onKindSelected: (ClipListKind) -> Unit = {},
    onSelectClip: (Clip) -> Unit = {},
    onToggleFavoriteClip: () -> Unit = {},
    onRemoveNoteFromClip: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onReply: (String) -> Unit = {},
    onRenote: (String) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> },
    onDeleteReaction: (String, String) -> Unit = { _, _ -> },
    onFavorite: (String) -> Unit = {},
    onAddToClip: ((Note) -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onVotePoll: (String, Int) -> Unit = { _, _ -> },
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    val clips = state?.clips.orEmpty()
    val selectedClip = state?.selectedClip ?: clips.firstOrNull()
    val notes = state?.notes.orEmpty()
    val listState = rememberLazyListState()
    var createDialogOpen by remember { mutableStateOf(false) }
    var editDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var removeNoteId by remember(selectedClip?.id) { mutableStateOf<String?>(null) }
    val canManageSelectedClip = state?.selectedKind == ClipListKind.Owned && selectedClip != null

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = notes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "剪辑",
            supportingText = selectedClip?.name?.ifBlank { null } ?: (state?.selectedKind ?: ClipListKind.Owned).label,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipCreateButton(
                        isCreating = state?.isCreatingClip == true,
                        onClick = { createDialogOpen = true },
                    )
                    HhhlOverflowMenu(
                        actions = clipSummaryActions(
                            hasSelectedClip = selectedClip != null,
                            isLoadingClips = state?.isLoadingClips == true,
                            isLoadingNotes = state?.isLoadingNotes == true,
                            onRefreshClips = onRefreshClips,
                            onRefreshNotes = onRefreshNotes,
                        ),
                    )
                }
            },
        )
        HhhlDivider()
        ClipSummaryRow(
            selectedKind = state?.selectedKind ?: ClipListKind.Owned,
            clipCount = clips.size,
            selectedClip = selectedClip,
            isLoadingClips = state?.isLoadingClips == true,
            isLoadingNotes = state?.isLoadingNotes == true,
        )
        HhhlDivider()
        ClipKindFilterRow(
            selectedKind = state?.selectedKind ?: ClipListKind.Owned,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        if (clips.isNotEmpty()) {
            ClipPickerRow(
                clips = clips,
                selectedClip = selectedClip,
                isLoading = state?.isLoadingClips == true,
                onSelectClip = onSelectClip,
            )
            HhhlDivider()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(key = "clip-error", contentType = "clip-status") {
                    ClipStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshClips,
                    )
                }
            }
            if (state?.isLoadingClips == true && clips.isEmpty()) {
                item(key = "clip-loading", contentType = "clip-status") {
                    ClipStatusRow(text = "正在加载剪辑...", loading = true)
                }
            }
            if (state != null && !state.isLoadingClips && clips.isEmpty() && state.errorMessage == null) {
                item(key = "clip-empty", contentType = "clip-status") { ClipStatusRow(text = "还没有剪辑") }
            }
            if (state?.isLoadingNotes == true && notes.isEmpty()) {
                item(key = "clip-notes-loading-${selectedClip?.id.orEmpty()}", contentType = "clip-status") {
                    ClipStatusRow(text = "正在加载剪辑动态...", loading = true)
                }
            }
            selectedClip?.let { clip ->
                item(key = "clip-header-${clip.id}", contentType = "clip-header") {
                    ClipHeaderRow(
                        clip = clip,
                        isChangingFavorite = state?.isChangingFavorite == true,
                        canManageClip = canManageSelectedClip,
                        isManagingClip = state?.isUpdatingClip == true || state?.isDeletingClip == true,
                        onToggleFavoriteClip = onToggleFavoriteClip,
                        onEditClip = { editDialogOpen = true },
                        onDeleteClip = { deleteDialogOpen = true },
                    )
                }
            }
            state?.notesErrorMessage?.let { message ->
                item(key = "clip-notes-error-${selectedClip?.id.orEmpty()}", contentType = "clip-status") {
                    ClipStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshNotes,
                    )
                }
            }
            if (
                state != null &&
                selectedClip != null &&
                !state.isLoadingNotes &&
                notes.isEmpty() &&
                state.notesErrorMessage == null
            ) {
                item(key = "clip-notes-empty-${selectedClip.id}", contentType = "clip-status") { ClipStatusRow(text = "这个剪辑还没有动态") }
            }
            items(
                items = notes,
                key = { "clip-note-${it.id}" },
                contentType = { "clip-note" },
            ) { note ->
                NoteRow(
                    note = note,
                    onClick = onOpenNote,
                    onOpenUser = onOpenUser,
                    onReply = onReply,
                    onRenote = onRenote,
                    onQuote = onQuote,
                    onReact = onReact,
                    onDeleteReaction = onDeleteReaction,
                    onFavorite = onFavorite,
                    onAddToClip = onAddToClip,
                    onDelete = onDelete,
                    onOpenMedia = onOpenMedia,
                    onOpenMediaPreview = onOpenMediaPreview,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    onVotePoll = onVotePoll,
                    reactionOptions = reactionOptions,
                    recentReactions = recentReactions,
                    isActionPending = isActionPending(note.id) || state?.isChangingClipNote == true,
                    canDelete = canDeleteAuthor(note.author.id),
                    density = noteRowDensity,
                )
                if (canManageSelectedClip) {
                    ClipNoteActionRow(
                        noteId = note.id,
                        enabled = state?.isChangingClipNote != true,
                        onRemoveNoteFromClip = { removeNoteId = it },
                    )
                }
            }
            if (state != null && notes.isNotEmpty() && state.isLoadingMore) {
                item(key = "clip-loading-more-${selectedClip?.id.orEmpty()}", contentType = "clip-status") {
                    ClipStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }

    if (createDialogOpen) {
        ClipEditorDialog(
            title = "新建剪辑",
            isCreating = state?.isCreatingClip == true,
            onDismiss = { createDialogOpen = false },
            onSubmit = { name, description, isPublic ->
                onCreateClip(name, description, isPublic)
                createDialogOpen = false
            },
        )
    }
    if (editDialogOpen && selectedClip != null) {
        ClipEditorDialog(
            title = "编辑剪辑",
            initialClip = selectedClip,
            isCreating = state?.isUpdatingClip == true,
            onDismiss = { editDialogOpen = false },
            onSubmit = { name, description, isPublic ->
                onUpdateSelectedClip(name, description, isPublic)
                editDialogOpen = false
            },
        )
    }
    if (deleteDialogOpen && selectedClip != null) {
        DeleteClipDialog(
            clip = selectedClip,
            isDeleting = state?.isDeletingClip == true,
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                onDeleteSelectedClip()
                deleteDialogOpen = false
            },
        )
    }
    removeNoteId?.let { noteId ->
        RemoveClipNoteDialog(
            isRemoving = state?.isChangingClipNote == true,
            onDismiss = { removeNoteId = null },
            onRemove = {
                onRemoveNoteFromClip(noteId)
                removeNoteId = null
            },
        )
    }
}

@Composable
private fun RemoveClipNoteDialog(
    isRemoving: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移出剪辑") },
        text = {
            Text(
                text = "这条动态会从当前剪辑移除，动态本身不会被删除。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(
                onClick = onRemove,
                enabled = !isRemoving,
                destructive = true,
            ) {
                Text(if (isRemoving) "移除中" else "移出")
            }
        },
        dismissButton = {
            HhhlTextButton(
                onClick = onDismiss,
                enabled = !isRemoving,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ClipEditorDialog(
    title: String,
    initialClip: Clip? = null,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var name by remember(initialClip?.id) { mutableStateOf(initialClip?.name.orEmpty()) }
    var description by remember(initialClip?.id) { mutableStateOf(initialClip?.description.orEmpty()) }
    var isPublic by remember(initialClip?.id) { mutableStateOf(initialClip?.isPublic == true) }
    val canSubmit = name.isNotBlank() && !isCreating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = "剪辑名称",
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述",
                    placeholder = "支持 Markdown 风格描述",
                    enabled = !isCreating,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlCheckbox(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        enabled = !isCreating,
                    )
                    Text(
                        text = "公开剪辑",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = { onSubmit(name, description, isPublic) },
                enabled = canSubmit,
            ) {
                Text(if (isCreating) "处理中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DeleteClipDialog(
    clip: Clip,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除剪辑") },
        text = {
            Text(
                text = "删除「${clip.name.ifBlank { "未命名剪辑" }}」后，剪辑列表会移除它，动态本身不会被删除。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(
                onClick = onDelete,
                enabled = !isDeleting,
                destructive = true,
            ) {
                Text(if (isDeleting) "删除中" else "删除")
            }
        },
        dismissButton = {
            HhhlTextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ClipNoteActionRow(
    noteId: String,
    enabled: Boolean,
    onRemoveNoteFromClip: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlOverflowMenu(
            enabled = true,
            actions = listOf(
                HhhlOverflowMenuAction(
                    label = if (enabled) "移出剪辑" else "处理中",
                    enabled = enabled,
                    onClick = { onRemoveNoteFromClip(noteId) },
                ),
            ),
        )
    }
    HhhlDivider()
}

@Composable
private fun ClipHeaderRow(
    clip: Clip,
    isChangingFavorite: Boolean,
    canManageClip: Boolean,
    isManagingClip: Boolean,
    onToggleFavoriteClip: () -> Unit,
    onEditClip: () -> Unit,
    onDeleteClip: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.name.ifBlank { "未命名剪辑" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${clip.notesCount} 条 · ${clip.favoritedCount} 人收藏 · ${clip.visibilityLabel}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                clip.description.takeIf { it.isNotBlank() }?.let { description ->
                    InlineRichText(
                        text = description,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HhhlActionChip(
                label = when {
                    isChangingFavorite -> "处理中"
                    clip.isFavorited -> "已收藏"
                    else -> "收藏"
                },
                emphasized = clip.isFavorited,
                enabled = !isChangingFavorite,
                onClick = onToggleFavoriteClip,
            )
            if (canManageClip) {
                HhhlActionChip(
                    label = if (isManagingClip) "处理中" else "编辑",
                    enabled = !isManagingClip,
                    onClick = onEditClip,
                )
                HhhlOverflowMenu(
                    enabled = !isManagingClip,
                    actions = clipHeaderActions(
                        isManagingClip = isManagingClip,
                        onDeleteClip = onDeleteClip,
                    ),
                )
            }
        }
    }
    HhhlDivider()
}

fun clipHeaderActions(
    isManagingClip: Boolean,
    onDeleteClip: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "删除",
        enabled = !isManagingClip,
        destructive = true,
        onClick = onDeleteClip,
    ),
)

@Composable
private fun ClipKindFilterRow(
    selectedKind: ClipListKind,
    onKindSelected: (ClipListKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipListKind.entries.forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = kind == selectedKind,
                modifier = Modifier.weight(1f),
                onClick = { onKindSelected(kind) },
            )
        }
    }
}

@Composable
private fun ClipSummaryRow(
    selectedKind: ClipListKind,
    clipCount: Int,
    selectedClip: Clip?,
    isLoadingClips: Boolean,
    isLoadingNotes: Boolean,
) {
    val colors = LocalHhhlColors.current
    val titleText = listOfNotNull(
        selectedKind.label,
        selectedClip?.name?.ifBlank { "未命名剪辑" },
    ).joinToString(" · ")
    val stateText = when {
        isLoadingClips -> "加载剪辑中"
        isLoadingNotes -> "加载动态中"
        selectedClip != null -> "${clipCount} 个剪辑 · ${selectedClip.notesCount} 条"
        else -> "${clipCount} 个剪辑"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$titleText · $stateText",
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClipCreateButton(
    isCreating: Boolean,
    onClick: () -> Unit,
) {
    HhhlIconActionButton(
        icon = Icons.Filled.Add,
        contentDescription = if (isCreating) "正在新建剪辑" else "新建剪辑",
        onClick = onClick,
        enabled = !isCreating,
        emphasized = !isCreating,
    )
}

fun clipSummaryActions(
    hasSelectedClip: Boolean,
    isLoadingClips: Boolean,
    isLoadingNotes: Boolean,
    onRefreshClips: () -> Unit,
    onRefreshNotes: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isLoadingClips) "同步剪辑中" else "刷新剪辑",
        enabled = !isLoadingClips,
        onClick = onRefreshClips,
    ),
    HhhlOverflowMenuAction(
        label = if (isLoadingNotes) "同步动态中" else "刷新动态",
        enabled = hasSelectedClip && !isLoadingNotes,
        onClick = onRefreshNotes,
    ),
)

@Composable
private fun ClipPickerRow(
    clips: List<Clip>,
    selectedClip: Clip?,
    isLoading: Boolean,
    onSelectClip: (Clip) -> Unit,
) {
    val colors = LocalHhhlColors.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading && clips.isNotEmpty()) {
            item(key = "clip-picker-loading", contentType = "clip-picker-status") {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        text = "加载中",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(
            items = clips,
            key = { it.id },
            contentType = { "clip-picker" },
        ) { clip ->
            val active = selectedClip?.id == clip.id
            ClipPickerChip(
                clip = clip,
                active = active,
                onClick = { onSelectClip(clip) },
            )
        }
    }
}

@Composable
private fun ClipPickerChip(
    clip: Clip,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 188.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) colors.buttonSelectedBackground
                else colors.buttonBackground,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = clip.name.ifBlank { "未命名剪辑" },
                color = if (active) {
                    colors.accent
                } else {
                    colors.textPrimary
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${clip.notesCount} 条 · ${clip.visibilityLabel}",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClipStatusRow(
    text: String,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}
