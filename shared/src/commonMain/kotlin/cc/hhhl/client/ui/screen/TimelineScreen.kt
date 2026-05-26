package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.TimelineTabState
import cc.hhhl.client.state.TimelineUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun TimelineScreen(
    state: TimelineUiState? = null,
    onTimelineSelected: (TimelineKind) -> Unit = {},
    onRefresh: (TimelineKind) -> Unit = {},
    onLoadMore: (TimelineKind) -> Unit = {},
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
    capabilities: InstanceCapabilities = InstanceCapabilities(),
    listStates: Map<TimelineKind, LazyListState> = emptyMap(),
    onCompose: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val kinds = availableTimelineKinds(capabilities)
    val selectedKind = state?.selectedKind ?: TimelineKind.Home
    val visibleSelectedKind = selectedKind.takeIf { it in kinds } ?: TimelineKind.Home
    val visibleKinds = kinds
    val selectedTab = visibleKinds.indexOf(visibleSelectedKind).coerceAtLeast(0)
    val selectedTabState = state?.tabs?.get(visibleSelectedKind)
        ?: TimelineTabState(notes = FakeData.timeline)
    val fallbackListState = rememberLazyListState()
    val listState = listStates[visibleSelectedKind] ?: fallbackListState
    val timelineThreadItems = remember(selectedTabState.notes) {
        timelineThreadItems(selectedTabState.notes)
    }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = selectedTabState.notes.size,
        isLoadingMore = selectedTabState.isLoadingMore || selectedTabState.endReached,
        onLoadMore = { onLoadMore(visibleSelectedKind) },
    )

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TimelineSummaryRow(
            selectedKind = visibleSelectedKind,
            selectedTabState = selectedTabState,
            onRefresh = { onRefresh(visibleSelectedKind) },
            onLoadMore = { onLoadMore(visibleSelectedKind) },
            onCompose = onCompose,
            onSearch = onSearch,
        )
        HhhlDivider()
        TabRow(selectedTabIndex = selectedTab) {
            visibleKinds.forEachIndexed { index, kind ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTimelineSelected(kind) },
                    text = { Text(kind.label) },
                )
            }
        }
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            if (selectedTabState.isLoading && selectedTabState.notes.isEmpty()) {
                item { TimelineStatusRow(text = "正在加载时间线...", loading = true) }
            }
            selectedTabState.errorMessage?.let { message ->
                item {
                    TimelineStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = { onRefresh(visibleSelectedKind) },
                    )
                }
            }
            if (!selectedTabState.isLoading && selectedTabState.notes.isEmpty() && selectedTabState.errorMessage == null) {
                item { TimelineStatusRow(text = "这里还没有内容") }
            }
            items(timelineThreadItems, key = { it.note.id }) { item ->
                TimelineThreadNoteRow(
                    item = item,
                ) {
                    NoteRow(
                        note = item.note,
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
                        isActionPending = isActionPending(item.note.id),
                        canDelete = canDeleteAuthor(item.note.author.id),
                        density = noteRowDensity,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineSummaryRow(
    selectedKind: TimelineKind,
    selectedTabState: TimelineTabState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    selectedTabState.isLoading -> "${selectedKind.label} · 加载中"
                    selectedTabState.notes.isEmpty() -> "${selectedKind.label} · 暂无内容"
                    else -> "${selectedKind.label} · ${selectedTabState.notes.size} 条"
                },
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlActionChip(
                label = "搜索",
                onClick = onSearch,
            )
            HhhlActionChip(
                label = "写帖",
                emphasized = true,
                onClick = onCompose,
            )
            if (selectedTabState.notes.isNotEmpty() && !selectedTabState.endReached) {
                HhhlActionChip(
                    label = if (selectedTabState.isLoadingMore) "加载中" else "加载更多",
                    enabled = !selectedTabState.isLoadingMore,
                    onClick = onLoadMore,
                )
            }
            HhhlActionChip(
                label = if (selectedTabState.isLoading || selectedTabState.isLoadingMore) "刷新中" else "刷新",
                enabled = !selectedTabState.isLoading && !selectedTabState.isLoadingMore,
                onClick = onRefresh,
            )
        }
    }
}

fun timelineSummaryActions(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isRefreshing) "刷新中" else "刷新",
        enabled = !isRefreshing,
        onClick = onRefresh,
    ),
)

fun availableTimelineKinds(capabilities: InstanceCapabilities): List<TimelineKind> {
    return buildList {
        add(TimelineKind.Home)
        add(TimelineKind.Social)
        if (capabilities.localTimelineAvailable) add(TimelineKind.Local)
        if (capabilities.globalTimelineAvailable) add(TimelineKind.Global)
        if (capabilities.bubbleTimelineAvailable) add(TimelineKind.Bubble)
    }
}

fun timelinePrimaryKinds(availableKinds: List<TimelineKind>): List<TimelineKind> {
    val primaryOrder = listOf(TimelineKind.Home, TimelineKind.Social, TimelineKind.Local)
    return primaryOrder.filter { it in availableKinds }
}

fun timelineVisibleKinds(
    availableKinds: List<TimelineKind>,
    selectedKind: TimelineKind,
): List<TimelineKind> {
    val primary = timelinePrimaryKinds(availableKinds)
    return if (selectedKind in primary || selectedKind !in availableKinds) {
        primary
    } else {
        listOf(selectedKind) + primary
    }
}

fun timelineOverflowKinds(availableKinds: List<TimelineKind>): List<TimelineKind> {
    return availableKinds - timelinePrimaryKinds(availableKinds).toSet()
}

fun timelineOverflowActions(
    availableKinds: List<TimelineKind>,
    selectedKind: TimelineKind,
    onTimelineSelected: (TimelineKind) -> Unit,
): List<HhhlOverflowMenuAction> {
    return timelineOverflowKinds(availableKinds).map { kind ->
        HhhlOverflowMenuAction(
            label = kind.label,
            enabled = kind != selectedKind,
            onClick = { onTimelineSelected(kind) },
        )
    }
}

data class TimelineThreadItem(
    val note: Note,
    val depth: Int,
)

fun timelineThreadItems(notes: List<Note>): List<TimelineThreadItem> {
    val distinctNotes = notes.distinctBy { it.id }
    val knownIds = distinctNotes.mapTo(mutableSetOf()) { it.id }
    val childrenByParent = distinctNotes
        .filter { it.replyId in knownIds }
        .groupBy { it.replyId.orEmpty() }
    val roots = distinctNotes.filter { it.replyId !in knownIds }
    val visible = mutableListOf<TimelineThreadItem>()
    val seen = mutableSetOf<String>()
    val notesById = distinctNotes.associateBy { it.id }

    fun append(note: Note, depth: Int) {
        if (!seen.add(note.id)) return
        visible += TimelineThreadItem(note = note, depth = depth.coerceAtLeast(1))
        childrenByParent[note.id].orEmpty().forEach { child ->
            append(child, depth + 1)
        }
    }

    roots.forEach { append(it, 1) }
    distinctNotes.forEach { append(it, timelineReplyDepth(it, notesById)) }
    return visible
}

fun timelineReplyDepth(
    note: Note,
    notesById: Map<String, Note>,
    maxDepth: Int = 4,
): Int {
    var depth = 1
    var parentId = note.replyId
    val seen = mutableSetOf(note.id)
    while (!parentId.isNullOrBlank() && parentId !in seen && parentId in notesById && depth < maxDepth) {
        depth += 1
        seen += parentId
        parentId = notesById[parentId]?.replyId
    }
    return depth.coerceIn(1, maxDepth.coerceAtLeast(1))
}

@Composable
private fun TimelineThreadNoteRow(
    item: TimelineThreadItem,
    content: @Composable () -> Unit,
) {
    val presentation = timelineThreadPresentation(item.depth)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val dividerColor = LocalHhhlColors.current.divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = presentation.startPaddingDp.dp)
            .drawBehind {
                if (presentation.railCount > 0) {
                    val strokeWidth = TIMELINE_REPLY_RAIL_STROKE_DP.dp.toPx()
                    val railSpacing = TIMELINE_REPLY_RAIL_SPACING_DP.dp.toPx()
                    val railTopInset = 4.dp.toPx()
                    val railBottomInset = 4.dp.toPx()
                    repeat(presentation.railCount) { index ->
                        val isActiveRail = index == presentation.railCount - 1
                        val x = 1.dp.toPx() + index * (strokeWidth + railSpacing)
                        drawLine(
                            color = when {
                                presentation.isDepthCollapsed -> secondaryColor.copy(alpha = 0.10f)
                                isActiveRail -> primaryColor.copy(alpha = 0.16f)
                                else -> dividerColor.copy(alpha = 0.58f)
                            },
                            start = Offset(x, railTopInset),
                            end = Offset(x, size.height - railBottomInset),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    ) {
        if (presentation.railCount > 0) {
            Spacer(modifier = Modifier.width((presentation.railGutterWidthDp + 10).dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

data class TimelineThreadPresentation(
    val depth: Int,
    val visualDepth: Int,
    val railCount: Int,
    val startPaddingDp: Int,
    val railGutterWidthDp: Int,
    val isDepthCollapsed: Boolean,
)

fun timelineThreadPresentation(
    depth: Int,
    maxVisualDepth: Int = TIMELINE_MAX_VISUAL_REPLY_DEPTH,
    startIndentDp: Int = TIMELINE_REPLY_START_INDENT_DP,
    railStrokeDp: Int = TIMELINE_REPLY_RAIL_STROKE_DP,
    railSpacingDp: Int = TIMELINE_REPLY_RAIL_SPACING_DP,
): TimelineThreadPresentation {
    val safeDepth = depth.coerceAtLeast(1)
    val safeMaxVisualDepth = maxVisualDepth.coerceAtLeast(1)
    val visualDepth = safeDepth.coerceIn(1, safeMaxVisualDepth)
    val railCount = if (safeDepth <= 1) 0 else visualDepth - 1
    return TimelineThreadPresentation(
        depth = safeDepth,
        visualDepth = visualDepth,
        railCount = railCount,
        startPaddingDp = ((safeDepth - 1).coerceAtMost(safeMaxVisualDepth - 1)) * startIndentDp.coerceAtLeast(0),
        railGutterWidthDp = replyTreeRailGutterWidthDp(
            railCount = railCount,
            strokeWidthDp = railStrokeDp,
            railSpacingDp = railSpacingDp,
        ),
        isDepthCollapsed = safeDepth > visualDepth,
    )
}

fun timelineThreadStartPaddingDp(depth: Int): Int {
    return timelineThreadPresentation(depth).startPaddingDp
}

const val TIMELINE_MAX_VISUAL_REPLY_DEPTH = 3
const val TIMELINE_REPLY_START_INDENT_DP = 8
const val TIMELINE_REPLY_RAIL_STROKE_DP = 1
const val TIMELINE_REPLY_RAIL_SPACING_DP = 7

@Composable
private fun TimelineStatusRow(
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
