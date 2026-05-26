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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.ChannelDraft
import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.ChannelUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
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
    val channels = state?.channels ?: fakeChannels()
    val selectedChannel = state?.selectedChannel ?: channels.firstOrNull()
    val notes = state?.notes ?: FakeData.timeline
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
                ?: (state?.selectedKind ?: ChannelListKind.Featured).label,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        ChannelSummaryRow(
            selectedKind = state?.selectedKind ?: ChannelListKind.Featured,
            channelCount = channels.size,
            selectedChannel = selectedChannel,
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
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        ChannelPickerRow(
            channels = channels,
            selectedChannel = selectedChannel,
            isLoading = state?.isLoadingChannels == true,
            onSelectChannel = onSelectChannel,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            selectedChannel?.let { channel ->
                item { ChannelHeader(channel = channel) }
                item {
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
                    item { ChannelStatusRow(text = "置顶动态") }
                    items(channel.pinnedNotes, key = { "channel-pinned-${it.id}" }) { note ->
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
                item {
                    ChannelStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshChannels,
                    )
                }
            }
            if (state?.isLoadingChannels == true && channels.isEmpty()) {
                item { ChannelStatusRow(text = "正在加载频道...", loading = true) }
            }
            if (state != null && !state.isLoadingChannels && channels.isEmpty() && state.errorMessage == null) {
                item { ChannelStatusRow(text = "还没有频道") }
            }
            if (state?.isLoadingTimeline == true && notes.isEmpty()) {
                item { ChannelStatusRow(text = "正在加载频道动态...", loading = true) }
            }
            state?.timelineErrorMessage?.let { message ->
                item {
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
                item { ChannelStatusRow(text = "这个频道还没有动态") }
            }
            items(visibleTimelineNotes, key = { "channel-note-${it.id}" }) { note ->
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
            if (state != null && notes.isNotEmpty() && !state.endReached) {
                item {
                    ChannelStatusRow(
                        text = if (state.isLoadingMore) "正在加载更多..." else "加载更多",
                        loading = state.isLoadingMore,
                        onAction = if (state.isLoadingMore) null else onLoadMore,
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
            )
        }
        Text(
            text = "${channel.usersCount} 人关注 · ${channel.notesCount} 条动态",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HhhlDivider()
}

@Composable
private fun ChannelKindFilterRow(
    selectedKind: ChannelListKind,
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
                emphasized = kind == selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
        if (selectedKind !in channelPrimaryKinds()) {
            HhhlActionChip(
                label = selectedKind.label,
                emphasized = true,
                onClick = { onKindSelected(selectedKind) },
            )
        }
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
    var name by remember(initialChannel?.id) { mutableStateOf(initialChannel?.name.orEmpty()) }
    var description by remember(initialChannel?.id) { mutableStateOf(initialChannel?.description.orEmpty()) }
    var color by remember(initialChannel?.id) { mutableStateOf(initialChannel?.color.ifNullOrBlank("#40c057")) }
    var isSensitive by remember(initialChannel?.id) { mutableStateOf(initialChannel?.isSensitive == true) }
    var allowRenoteToExternal by remember(initialChannel?.id) {
        mutableStateOf(initialChannel?.allowRenoteToExternal != false)
    }
    val canSubmit = name.isNotBlank() && !isMutating

    AlertDialog(
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
                    placeholder = "#40c057",
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
            TextButton(
                onClick = {
                    onSubmit(
                        ChannelDraft(
                            name = name,
                            description = description,
                            color = color,
                            isArchived = initialChannel?.isArchived == true,
                            isSensitive = isSensitive,
                            allowRenoteToExternal = allowRenoteToExternal,
                        ),
                    )
                },
                enabled = canSubmit,
            ) {
                Text(if (isMutating) "处理中" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("归档频道") },
        text = {
            Text(
                text = "归档「${channel.name.ifBlank { "未命名频道" }}」后，它会从当前频道列表移除。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onArchive, enabled = !isMutating) {
                Text(if (isMutating) "归档中" else "归档")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChannelSummaryRow(
    selectedKind: ChannelListKind,
    channelCount: Int,
    selectedChannel: Channel?,
    isLoadingChannels: Boolean,
    isLoadingTimeline: Boolean,
    isMutatingChannel: Boolean,
    onRefreshChannels: () -> Unit,
    onRefreshTimeline: () -> Unit,
    onCreateChannel: () -> Unit,
) {
    val titleText = listOfNotNull(
        selectedKind.label,
        selectedChannel?.name?.ifBlank { "未命名频道" },
    ).joinToString(" · ")
    val stateText = when {
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
            color = MaterialTheme.colorScheme.onBackground,
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
            HhhlActionChip(
                label = if (isLoadingChannels) "同步频道中" else "刷新频道",
                emphasized = true,
                enabled = !isLoadingChannels,
                onClick = onRefreshChannels,
            )
            HhhlActionChip(
                label = if (isLoadingTimeline) "同步动态中" else "刷新动态",
                enabled = selectedChannel != null && !isLoadingTimeline,
                onClick = onRefreshTimeline,
            )
            HhhlActionChip(
                label = if (isMutatingChannel) "处理中" else "新建频道",
                emphasized = true,
                enabled = !isMutatingChannel,
                onClick = onCreateChannel,
            )
        }
    }
}

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
    isLoading: Boolean,
    onSelectChannel: (Channel) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        text = "加载中",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(channels, key = { it.id }) { channel ->
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
    Box(
        modifier = Modifier
            .widthIn(min = 128.dp, max = 196.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else LocalHhhlColors.current.inputBackground,
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.hasUnreadNote) {
                    Text(
                        text = "新",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = "${channel.usersCount} 人 · ${channel.notesCount} 条",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChannelHeader(channel: Channel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = channel.name.ifBlank { "未命名频道" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (channel.description.isNotBlank()) {
            Text(
                text = channel.description,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "${channel.statusLabel} · ${channel.usersCount} 人关注 · ${channel.notesCount} 条动态",
            color = MaterialTheme.colorScheme.secondary,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        Text(
            text = actionText ?: text,
            color = if (onAction != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = if (onAction != null) Modifier.clickable { onAction() } else Modifier,
        )
        if (actionText != null) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HhhlDivider()
}

private fun fakeChannels(): List<Channel> {
    return listOf(
        Channel(
            id = "channel-featured",
            name = "公告频道",
            description = "HHHL 站内公告和讨论",
            color = "#40c057",
            userId = "me",
            bannerUrl = null,
            pinnedNoteIds = emptyList(),
            pinnedNotes = emptyList(),
            isArchived = false,
            isSensitive = false,
            allowRenoteToExternal = true,
            isFollowing = true,
            isFavorited = false,
            hasUnreadNote = true,
            usersCount = 4,
            notesCount = FakeData.timeline.size,
        ),
    )
}

private fun String?.ifNullOrBlank(default: String): String {
    return this?.takeIf { it.isNotBlank() } ?: default
}
