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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelCategory
import cc.hhhl.client.model.ChannelDefaultColorHex
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.ChannelUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun ChannelScreen(
    state: ChannelUiState? = null,
    onBack: () -> Unit,
    onRefreshChannels: () -> Unit = {},
    onRefreshTimeline: () -> Unit = {},
    onKindSelected: (ChannelListKind) -> Unit = {},
    onCategorySelected: (ChannelCategory) -> Unit = {},
    onSelectChannel: (Channel) -> Unit = {},
    onToggleFollowChannel: () -> Unit = {},
    onToggleFavoriteChannel: () -> Unit = {},
    onCreateChannel: (ChannelDraft) -> Unit = {},
    onUpdateSelectedChannel: (ChannelDraft) -> Unit = {},
    onArchiveSelectedChannel: () -> Unit = {},
    onComposeInChannel: (Channel) -> Unit = {},
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
    val channels = state?.channels.orEmpty()
    val selectedChannel = state?.selectedChannel ?: channels.firstOrNull()
    val notes = state?.notes.orEmpty()
    val visibleTimelineNotes = channelVisibleTimelineNotes(
        notes = notes,
        pinnedNotes = selectedChannel?.pinnedNotes.orEmpty(),
    )
    val listState = rememberLazyListState()
    var editorMode by remember { mutableStateOf<ChannelEditorMode?>(null) }
    var archiveDialogOpen by remember { mutableStateOf(false) }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = visibleTimelineNotes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "频道",
            supportingText = selectedChannel?.name?.ifBlank { null }
                ?: state?.selectedCategory?.label
                ?: (state?.selectedKind ?: ChannelListKind.Featured).label,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        ChannelSummaryRow(
            selectedKind = state?.selectedKind ?: ChannelListKind.Featured,
            selectedCategory = state?.selectedCategory,
            channelCount = channels.size,
            selectedChannel = selectedChannel,
            isLoadingCategories = state?.isLoadingCategories == true,
            isLoadingChannels = state?.isLoadingChannels == true,
            isLoadingTimeline = state?.isLoadingTimeline == true,
            isMutatingChannel = state?.isMutatingChannel == true,
            onRefreshChannels = onRefreshChannels,
            onRefreshTimeline = onRefreshTimeline,
            onCreateChannel = { editorMode = ChannelEditorMode.Create },
        )
        HhhlDivider()
        ChannelKindFilterRow(
            selectedKind = state?.selectedKind ?: ChannelListKind.Featured,
            selectedCategory = state?.selectedCategory,
            onKindSelected = onKindSelected,
        )
        if (state?.isLoadingCategories == true || state?.categories?.isNotEmpty() == true) {
            HhhlDivider()
            ChannelCategoryFilterRow(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                isLoadingCategories = state.isLoadingCategories,
                onCategorySelected = onCategorySelected,
            )
        }
        HhhlDivider()
        ChannelPickerRow(
            channels = channels,
            selectedChannel = selectedChannel,
            onSelectChannel = onSelectChannel,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            selectedChannel?.let { channel ->
                item(key = "channel-header-${channel.id}", contentType = "channel-header") { ChannelHeader(channel = channel) }
                item(key = "channel-actions-${channel.id}", contentType = "channel-actions") {
                    ChannelActionRow(
                        channel = channel,
                        isChangingFollow = state?.isChangingFollow == true,
                        isChangingFavorite = state?.isChangingFavorite == true,
                        isMutatingChannel = state?.isMutatingChannel == true,
                        onToggleFollowChannel = onToggleFollowChannel,
                        onToggleFavoriteChannel = onToggleFavoriteChannel,
                        onEditChannel = { editorMode = ChannelEditorMode.Edit },
                        onArchiveChannel = { archiveDialogOpen = true },
                        onComposeInChannel = { onComposeInChannel(channel) },
                    )
                }
                if (channel.pinnedNotes.isNotEmpty()) {
                    item(key = "channel-pinned-title-${channel.id}", contentType = "channel-status") { ChannelStatusRow(text = "置顶动态") }
                    items(
                        items = channel.pinnedNotes,
                        key = { "channel-pinned-${it.id}" },
                        contentType = { "channel-pinned-note" },
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
                            isActionPending = isActionPending(note.id),
                            canDelete = canDeleteAuthor(note.author.id),
                            density = noteRowDensity,
                        )
                    }
                }
            }
            state?.errorMessage?.let { message ->
                item(key = "channel-error", contentType = "channel-status") {
                    ChannelStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshChannels,
                    )
                }
            }
            if (state?.isLoadingChannels == true && channels.isEmpty()) {
                item(key = "channel-loading", contentType = "channel-status") {
                    ChannelStatusRow(text = "正在加载频道...", loading = true)
                }
            }
            if (state != null && !state.isLoadingChannels && channels.isEmpty() && state.errorMessage == null) {
                item(key = "channel-empty", contentType = "channel-status") { ChannelStatusRow(text = "还没有频道") }
            }
            if (state?.isLoadingTimeline == true && notes.isEmpty()) {
                item(key = "channel-timeline-loading-${selectedChannel?.id.orEmpty()}", contentType = "channel-status") {
                    ChannelStatusRow(text = "正在加载频道动态...", loading = true)
                }
            }
            state?.timelineErrorMessage?.let { message ->
                item(key = "channel-timeline-error-${selectedChannel?.id.orEmpty()}", contentType = "channel-status") {
                    ChannelStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshTimeline,
                    )
                }
            }
            if (
                state != null &&
                selectedChannel != null &&
                !state.isLoadingTimeline &&
                notes.isEmpty() &&
                state.timelineErrorMessage == null
            ) {
                item(key = "channel-timeline-empty-${selectedChannel.id}", contentType = "channel-status") { ChannelStatusRow(text = "这个频道还没有动态") }
            }
            items(
                items = visibleTimelineNotes,
                key = { "channel-note-${it.id}" },
                contentType = { "channel-note" },
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
                    isActionPending = isActionPending(note.id),
                    canDelete = canDeleteAuthor(note.author.id),
                    density = noteRowDensity,
                )
            }
            if (state != null && notes.isNotEmpty() && state.isLoadingMore) {
                item(key = "channel-loading-more-${selectedChannel?.id.orEmpty()}", contentType = "channel-status") {
                    ChannelStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }

    when (editorMode) {
        ChannelEditorMode.Create -> ChannelEditorDialog(
            title = "新建频道",
            isMutating = state?.isMutatingChannel == true,
            onDismiss = { editorMode = null },
            onSubmit = {
                onCreateChannel(it)
                editorMode = null
            },
        )
        ChannelEditorMode.Edit -> selectedChannel?.let { channel ->
            ChannelEditorDialog(
                title = "编辑频道",
                initialChannel = channel,
                isMutating = state?.isMutatingChannel == true,
                onDismiss = { editorMode = null },
                onSubmit = {
                    onUpdateSelectedChannel(it)
                    editorMode = null
                },
            )
        }
        null -> Unit
    }

    if (archiveDialogOpen && selectedChannel != null) {
        ArchiveChannelDialog(
            channel = selectedChannel,
            isMutating = state?.isMutatingChannel == true,
            onDismiss = { archiveDialogOpen = false },
            onArchive = {
                onArchiveSelectedChannel()
                archiveDialogOpen = false
            },
        )
    }
}

internal fun channelVisibleTimelineNotes(
    notes: List<Note>,
    pinnedNotes: List<Note>,
): List<Note> {
    val pinnedIds = pinnedNotes.map { it.id }.toSet()
    return notes.filterNot { it.id in pinnedIds }
}

private enum class ChannelEditorMode {
    Create,
    Edit,
}

@Composable
private fun ChannelActionRow(
    channel: Channel,
    isChangingFollow: Boolean,
    isChangingFavorite: Boolean,
    isMutatingChannel: Boolean,
    onToggleFollowChannel: () -> Unit,
    onToggleFavoriteChannel: () -> Unit,
    onEditChannel: () -> Unit,
    onArchiveChannel: () -> Unit,
    onComposeInChannel: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlActionChip(
                label = when {
                    isChangingFollow -> "处理中"
                    channel.isFollowing -> "已关注"
                    else -> "关注"
                },
                enabled = !isChangingFollow,
                emphasized = channel.isFollowing,
                onClick = onToggleFollowChannel,
            )
            HhhlActionChip(
                label = when {
                    isChangingFavorite -> "处理中"
                    channel.isFavorited -> "已收藏"
                    else -> "收藏"
                },
                enabled = !isChangingFavorite,
                emphasized = channel.isFavorited,
                onClick = onToggleFavoriteChannel,
            )
            HhhlActionChip(
                label = "发动态",
                emphasized = true,
                onClick = onComposeInChannel,
            )
            HhhlOverflowMenu(
                enabled = !isMutatingChannel,
                actions = channelManagementActions(
                    isMutatingChannel = isMutatingChannel,
                    isArchived = channel.isArchived,
                    onEditChannel = onEditChannel,
                    onArchiveChannel = onArchiveChannel,
                ),
                buttonText = "更多",
            )
        }
        Text(
            text = "${channel.usersCount} 人关注 · ${channel.notesCount} 条动态",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HhhlDivider()
}

@Composable
private fun ChannelKindFilterRow(
    selectedKind: ChannelListKind,
    selectedCategory: ChannelCategory?,
    onKindSelected: (ChannelListKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        channelPrimaryKinds().forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = selectedCategory == null && kind == selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
        if (selectedCategory == null && selectedKind !in channelPrimaryKinds()) {
            HhhlActionChip(
                label = selectedKind.label,
                emphasized = true,
                onClick = { onKindSelected(selectedKind) },
            )
        }
    }
}

@Composable
private fun ChannelCategoryFilterRow(
    categories: List<ChannelCategory>,
    selectedCategory: ChannelCategory?,
    isLoadingCategories: Boolean,
    onCategorySelected: (ChannelCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoadingCategories && categories.isEmpty()) {
            item(key = "channel-category-loading", contentType = "channel-category-status") {
                Text(
                    text = "加载分类中",
                    color = LocalHhhlColors.current.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        items(
            items = categories,
            key = { if (it.uncategorized) "__uncategorized__" else it.name },
            contentType = { "channel-category-chip" },
        ) { category ->
            HhhlActionChip(
                label = channelCategoryLabel(category),
                emphasized = selectedCategory.sameCategory(category),
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

fun channelCategoryLabel(category: ChannelCategory): String {
    return if (category.channelsCount > 0) {
        "${category.label} ${category.channelsCount}"
    } else {
        category.label
    }
}

fun channelManagementActions(
    isMutatingChannel: Boolean,
    isArchived: Boolean,
    onEditChannel: () -> Unit,
    onArchiveChannel: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isMutatingChannel) "处理中" else "编辑",
        enabled = !isMutatingChannel,
        onClick = onEditChannel,
    ),
    HhhlOverflowMenuAction(
        label = "归档",
        enabled = !isMutatingChannel && !isArchived,
        destructive = true,
        onClick = onArchiveChannel,
    ),
)

@Composable
private fun ChannelEditorDialog(
    title: String,
    initialChannel: Channel? = null,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ChannelDraft) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var name by remember(initialChannel?.id) { mutableStateOf(initialChannel?.name.orEmpty()) }
    var description by remember(initialChannel?.id) { mutableStateOf(initialChannel?.description.orEmpty()) }
    var color by remember(initialChannel?.id) { mutableStateOf(initialChannel?.color.ifNullOrBlank(ChannelDefaultColorHex)) }
    var category by remember(initialChannel?.id) { mutableStateOf(initialChannel?.category.orEmpty()) }
    var isSensitive by remember(initialChannel?.id) { mutableStateOf(initialChannel?.isSensitive == true) }
    var allowRenoteToExternal by remember(initialChannel?.id) {
        mutableStateOf(initialChannel?.allowRenoteToExternal != false)
    }
    val canSubmit = name.isNotBlank() && !isMutating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = "频道名称",
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述",
                    placeholder = "支持 Markdown 风格描述",
                    enabled = !isMutating,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = color,
                    onValueChange = { color = it },
                    label = "颜色",
                    placeholder = ChannelDefaultColorHex,
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = category,
                    onValueChange = { category = it },
                    label = "分类",
                    placeholder = "如 AI与大模型",
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChannelCheckRow("敏感频道", isSensitive, !isMutating) { isSensitive = it }
                ChannelCheckRow("允许站外转发", allowRenoteToExternal, !isMutating) {
                    allowRenoteToExternal = it
                }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = {
                    onSubmit(
                        ChannelDraft(
                            name = name,
                            description = description,
                            color = color,
                            isArchived = initialChannel?.isArchived == true,
                            isSensitive = isSensitive,
                            allowRenoteToExternal = allowRenoteToExternal,
                            category = category,
                        ),
                    )
                },
                enabled = canSubmit,
            ) {
                Text(if (isMutating) "处理中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChannelCheckRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = text,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ArchiveChannelDialog(
    channel: Channel,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("归档频道") },
        text = {
            Text(
                text = "归档「${channel.name.ifBlank { "未命名频道" }}」后，它会从当前频道列表移除。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onArchive, enabled = !isMutating) {
                Text(if (isMutating) "归档中" else "归档")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChannelSummaryRow(
    selectedKind: ChannelListKind,
    selectedCategory: ChannelCategory?,
    channelCount: Int,
    selectedChannel: Channel?,
    isLoadingCategories: Boolean,
    isLoadingChannels: Boolean,
    isLoadingTimeline: Boolean,
    isMutatingChannel: Boolean,
    onRefreshChannels: () -> Unit,
    onRefreshTimeline: () -> Unit,
    onCreateChannel: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val titleText = listOfNotNull(
        selectedCategory?.label ?: selectedKind.label,
        selectedChannel?.name?.ifBlank { "未命名频道" },
    ).joinToString(" · ")
    val stateText = when {
        isLoadingCategories -> "加载分类中"
        isLoadingChannels -> "加载频道中"
        isLoadingTimeline -> "加载时间线中"
        selectedChannel != null -> "${channelCount} 个频道 · ${selectedChannel.notesCount} 条"
        else -> "${channelCount} 个频道"
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isLoadingTimeline) "同步动态中" else "刷新动态",
                emphasized = true,
                enabled = selectedChannel != null && !isLoadingTimeline,
                onClick = onRefreshTimeline,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Add,
                contentDescription = if (isMutatingChannel) "处理中" else "新建频道",
                emphasized = true,
                enabled = !isMutatingChannel,
                onClick = onCreateChannel,
            )
            HhhlOverflowMenu(
                actions = channelSummaryActions(
                    isLoadingChannels = isLoadingChannels,
                    onRefreshChannels = onRefreshChannels,
                ),
                label = "频道操作",
                buttonText = "更多",
            )
        }
    }
}

fun channelSummaryActions(
    isLoadingChannels: Boolean,
    onRefreshChannels: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isLoadingChannels) "同步频道中" else "刷新频道列表",
        icon = Icons.Filled.Refresh,
        enabled = !isLoadingChannels,
        onClick = onRefreshChannels,
    ),
)

fun channelPrimaryKinds(): List<ChannelListKind> = listOf(
    ChannelListKind.Featured,
    ChannelListKind.Followed,
)

fun channelOverflowKinds(): List<ChannelListKind> =
    ChannelListKind.entries - channelPrimaryKinds().toSet()

@Composable
private fun ChannelPickerRow(
    channels: List<Channel>,
    selectedChannel: Channel?,
    onSelectChannel: (Channel) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = channels,
            key = { it.id },
            contentType = { "channel-picker" },
        ) { channel ->
            val active = selectedChannel?.id == channel.id
            ChannelPickerChip(
                channel = channel,
                active = active,
                onClick = { onSelectChannel(channel) },
            )
        }
    }
}

@Composable
private fun ChannelPickerChip(
    channel: Channel,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .widthIn(min = 128.dp, max = 196.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) colors.buttonSelectedBackground
                else colors.buttonBackground,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = channel.name.ifBlank { "未命名频道" },
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
                if (channel.hasUnreadNote) {
                    Text(
                        text = "新",
                        color = colors.danger,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = "${channel.usersCount} 人 · ${channel.notesCount} 条",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChannelHeader(channel: Channel) {
    val colors = LocalHhhlColors.current
    val metaText = listOfNotNull(
        channel.statusLabel,
        channel.category.takeIf { it.isNotBlank() },
        "${channel.usersCount} 人关注",
        "${channel.notesCount} 条动态",
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = channel.name.ifBlank { "未命名频道" },
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (channel.description.isNotBlank()) {
            InlineRichText(
                text = channel.description,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = metaText,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HhhlDivider()
}

@Composable
private fun ChannelStatusRow(
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

private fun String?.ifNullOrBlank(default: String): String {
    return this?.takeIf { it.isNotBlank() } ?: default
}

private fun ChannelCategory?.sameCategory(other: ChannelCategory): Boolean {
    return this != null &&
        uncategorized == other.uncategorized &&
        name.trim() == other.name.trim()
}
