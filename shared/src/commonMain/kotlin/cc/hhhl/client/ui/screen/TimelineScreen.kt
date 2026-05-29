package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.api.TimelineKind
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.state.TimelineTabState
import cc.hhhl.client.state.TimelineUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlAnimatedSegmentedControl
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.LocalBlockedNoteAuthorIds
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.isHiddenByBlockedAuthor
import kotlinx.coroutines.launch

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
    isSpecialCareAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
    capabilities: InstanceCapabilities = InstanceCapabilities(),
    listStates: Map<TimelineKind, LazyListState> = emptyMap(),
    isTrendSelected: Boolean = false,
    trends: List<TrendingHashtag> = emptyList(),
    isRefreshingTrends: Boolean = false,
    trendErrorMessage: String? = null,
    onTrendSelected: () -> Unit = {},
    onRefreshTrends: () -> Unit = {},
    onNewNotesMarkerConsumed: (TimelineKind) -> Unit = {},
    onCompose: () -> Unit = {},
    onSearch: () -> Unit = {},
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiAction: (AiTaskKind, TimelineKind, List<Note>) -> Unit = { _, _, _ -> },
    onDismissAiResult: () -> Unit = {},
) {
    val availableKinds = availableTimelineKinds(capabilities)
    val selectedKind = state?.selectedKind ?: TimelineKind.Home
    val visibleSelectedKind = selectedKind.takeIf { it in availableKinds } ?: TimelineKind.Home
    val visibleKinds = timelineVisibleKinds(availableKinds, visibleSelectedKind)
    val hasTrendTab = capabilities.canTrend
    val showTrends = isTrendSelected && hasTrendTab
    val selectedTabState = state?.tabs?.get(visibleSelectedKind)
        ?: TimelineTabState()
    val fallbackListState = rememberLazyListState()
    val listState = listStates[visibleSelectedKind] ?: fallbackListState
    val trendListState = rememberLazyListState()
    val blockedNoteAuthorIds = LocalBlockedNoteAuthorIds.current
    val visibleNotes = remember(selectedTabState.notes, blockedNoteAuthorIds) {
        selectedTabState.notes.filterNot { note -> note.isHiddenByBlockedAuthor(blockedNoteAuthorIds) }
    }
    val timelineThreadItems = remember(visibleNotes) {
        timelineThreadItems(visibleNotes)
    }
    val indexedTimelineThreadItems = remember(timelineThreadItems) {
        timelineThreadItems.withIndex().toList()
    }
    val coroutineScope = rememberCoroutineScope()
    val firstUnreadIndex = remember(timelineThreadItems, selectedTabState.firstUnreadNoteId) {
        val markerId = selectedTabState.firstUnreadNoteId
        if (markerId == null) -1 else timelineThreadItems.indexOfFirst { it.note.id == markerId }
    }
    val newContentSeparatorIndex = remember(timelineThreadItems, selectedTabState.newNoteCount) {
        selectedTabState.newNoteCount
            .takeIf { it > 0 && it < timelineThreadItems.size }
            ?: -1
    }

    LaunchedEffect(visibleSelectedKind, selectedTabState.firstUnreadNoteId, listState.firstVisibleItemIndex) {
        val markerId = selectedTabState.firstUnreadNoteId ?: return@LaunchedEffect
        val markerIndex = timelineThreadItems.indexOfFirst { it.note.id == markerId }
        if (markerIndex >= 0 && listState.firstVisibleItemIndex <= markerIndex) {
            onNewNotesMarkerConsumed(visibleSelectedKind)
        }
    }

    if (!showTrends) {
        AutoLoadMoreEffect(
            listState = listState,
            itemCount = timelineThreadItems.size,
            isLoadingMore = selectedTabState.isLoadingMore || selectedTabState.endReached,
            onLoadMore = { onLoadMore(visibleSelectedKind) },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showTrends) {
            TimelineTrendSummaryRow(
                trendCount = trends.size,
                isRefreshing = isRefreshingTrends,
                onRefresh = onRefreshTrends,
                onCompose = onCompose,
                onSearch = onSearch,
            )
        } else {
            TimelineSummaryRow(
                selectedKind = visibleSelectedKind,
                selectedTabState = selectedTabState,
                onRefresh = { onRefresh(visibleSelectedKind) },
                aiEnabled = aiEnabled,
                isAiProcessing = isAiProcessing,
                onAiDigest = { onAiAction(AiTaskKind.TimelineDigest, visibleSelectedKind, visibleNotes) },
                onAiReplyOpportunities = {
                    onAiAction(AiTaskKind.TimelineReplyOpportunities, visibleSelectedKind, visibleNotes)
                },
                onAiFilterSuggestions = {
                    onAiAction(AiTaskKind.TimelineFilterSuggestions, visibleSelectedKind, visibleNotes)
                },
                onJumpToNewNotes = if (firstUnreadIndex >= 0) {
                    {
                        coroutineScope.launch {
                            listState.animateScrollToItem(firstUnreadIndex)
                            onNewNotesMarkerConsumed(visibleSelectedKind)
                        }
                    }
                } else {
                    null
                },
                onCompose = onCompose,
                onSearch = onSearch,
            )
        }
        TimelineTabStrip(
            availableKinds = availableKinds,
            visibleKinds = visibleKinds,
            selectedKind = visibleSelectedKind,
            showTrends = showTrends,
            hasTrendTab = hasTrendTab,
            onTimelineSelected = onTimelineSelected,
            onTrendSelected = onTrendSelected,
        )
        HhhlDivider()
        if (!showTrends && !aiResultText.isNullOrBlank()) {
            TimelineAiResultPanel(
                label = aiResultLabel ?: "AI 速览",
                text = aiResultText,
                onDismiss = onDismissAiResult,
            )
            HhhlDivider()
        }
        if (showTrends) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = trendListState,
            ) {
                if (isRefreshingTrends && trends.isEmpty()) {
                    item(key = "timeline-trends-loading", contentType = "timeline-status") {
                        TimelineStatusRow(text = "正在加载趋势...", loading = true)
                    }
                }
                trendErrorMessage?.let { message ->
                    item(key = "timeline-trends-error", contentType = "timeline-status") {
                        TimelineStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = onRefreshTrends,
                        )
                    }
                }
                if (!isRefreshingTrends && trends.isEmpty() && trendErrorMessage == null) {
                    item(key = "timeline-trends-empty", contentType = "timeline-status") {
                        TimelineStatusRow(text = "暂无趋势")
                    }
                }
                items(
                    items = trends,
                    key = { "timeline-trend-${it.tag}" },
                    contentType = { "timeline-trend" },
                ) { trend ->
                    TimelineTrendRow(
                        trend = trend,
                        onOpenHashtag = onOpenHashtag,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                if (selectedTabState.isLoading && visibleNotes.isEmpty()) {
                    item(key = "timeline-notes-loading-${visibleSelectedKind.name}", contentType = "timeline-status") {
                        TimelineSkeletonList()
                    }
                }
                selectedTabState.errorMessage?.let { message ->
                    item(key = "timeline-notes-error-${visibleSelectedKind.name}", contentType = "timeline-status") {
                        TimelineStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = { onRefresh(visibleSelectedKind) },
                        )
                    }
                }
                if (!selectedTabState.isLoading && visibleNotes.isEmpty() && selectedTabState.errorMessage == null) {
                    item(key = "timeline-notes-empty-${visibleSelectedKind.name}", contentType = "timeline-status") {
                        TimelineStatusRow(text = "这里还没有内容")
                    }
                }
                items(
                    items = indexedTimelineThreadItems,
                    key = { it.value.note.id },
                    contentType = { "timeline-note" },
                ) { indexedItem ->
                    val itemIndex = indexedItem.index
                    val item = indexedItem.value
                    if (itemIndex == newContentSeparatorIndex) {
                        TimelineNewContentDivider(newNoteCount = selectedTabState.newNoteCount)
                    }
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
                            isSpecialCareAuthor = isSpecialCareAuthor(item.note.author.id),
                            density = noteRowDensity,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTabStrip(
    availableKinds: List<TimelineKind>,
    visibleKinds: List<TimelineKind>,
    selectedKind: TimelineKind,
    showTrends: Boolean,
    hasTrendTab: Boolean,
    onTimelineSelected: (TimelineKind) -> Unit,
    onTrendSelected: () -> Unit,
) {
    val overflowActions = timelineOverflowActions(
        availableKinds = availableKinds,
        selectedKind = selectedKind,
        onTimelineSelected = onTimelineSelected,
    )
    val tabs = buildList {
        addAll(visibleKinds.map { TimelineTabItem(label = it.label, kind = it) })
        if (hasTrendTab) add(TimelineTabItem(label = "趋势", kind = null))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val selectedTabIndex = tabs.indexOfFirst { tab ->
            val isTrend = tab.kind == null
            if (isTrend) showTrends else !showTrends && selectedKind == tab.kind
        }.coerceAtLeast(0)
        HhhlAnimatedSegmentedControl(
            labels = tabs.map { it.label },
            selectedIndex = selectedTabIndex,
            onSelected = { index ->
                val kind = tabs.getOrNull(index)?.kind
                if (kind == null) {
                    onTrendSelected()
                } else {
                    onTimelineSelected(kind)
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (overflowActions.isNotEmpty()) {
            HhhlOverflowMenu(
                actions = overflowActions,
                label = "更多时间线",
            )
        }
    }
}

private data class TimelineTabItem(
    val label: String,
    val kind: TimelineKind?,
)

@Composable
private fun TimelineSummaryRow(
    selectedKind: TimelineKind,
    selectedTabState: TimelineTabState,
    onRefresh: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiDigest: () -> Unit,
    onAiReplyOpportunities: () -> Unit,
    onAiFilterSuggestions: () -> Unit,
    onJumpToNewNotes: (() -> Unit)?,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = LocalHhhlColors.current
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
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Search,
                contentDescription = "搜索",
                onClick = onSearch,
            )
            if (onJumpToNewNotes != null && selectedTabState.newNoteCount > 0) {
                HhhlActionChip(
                    label = "新 ${selectedTabState.newNoteCount}",
                    emphasized = true,
                    onClick = onJumpToNewNotes,
                )
            }
            HhhlOverflowMenu(
                actions = timelineAiActions(
                    enabled = aiEnabled && !isAiProcessing && selectedTabState.notes.isNotEmpty(),
                    isProcessing = isAiProcessing,
                    onAiDigest = onAiDigest,
                    onAiReplyOpportunities = onAiReplyOpportunities,
                    onAiFilterSuggestions = onAiFilterSuggestions,
                ),
                enabled = aiEnabled && selectedTabState.notes.isNotEmpty(),
                label = "AI 时间线",
                buttonContainerColor = colors.buttonBackground,
                iconTint = colors.textSecondary,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Edit,
                contentDescription = "写帖",
                emphasized = true,
                onClick = onCompose,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (selectedTabState.isLoading || selectedTabState.isLoadingMore) "刷新中" else "刷新",
                enabled = !selectedTabState.isLoading && !selectedTabState.isLoadingMore,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun TimelineAiResultPanel(
    label: String,
    text: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.78f))
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Close,
                contentDescription = "关闭 AI 结果",
                onClick = onDismiss,
            )
        }
        Text(
            text = text,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineNewContentDivider(newNoteCount: Int) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(colors.border.copy(alpha = 0.72f)),
        )
        Text(
            text = if (newNoteCount > 0) "以上 $newNoteCount 条新内容" else "以上是新内容",
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(colors.border.copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun TimelineTrendSummaryRow(
    trendCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = LocalHhhlColors.current
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
                    isRefreshing && trendCount == 0 -> "趋势 · 加载中"
                    else -> "趋势 · $trendCount 个"
                },
                color = colors.textMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Search,
                contentDescription = "搜索",
                onClick = onSearch,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Edit,
                contentDescription = "写帖",
                emphasized = true,
                onClick = onCompose,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isRefreshing) "刷新中" else "刷新",
                enabled = !isRefreshing,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun TimelineTrendRow(
    trend: TrendingHashtag,
    onOpenHashtag: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenHashtag(trend.tag) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "#${trend.tag}",
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${trend.usersCount} 人正在使用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = trendChartSummary(trend),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HhhlDivider()
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
        add(TimelineKind.Featured)
        add(TimelineKind.Mentions)
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

fun timelineAiActions(
    enabled: Boolean,
    isProcessing: Boolean,
    onAiDigest: () -> Unit,
    onAiReplyOpportunities: () -> Unit,
    onAiFilterSuggestions: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isProcessing) "AI 处理中" else "AI 时间线速览",
        enabled = enabled,
        icon = Icons.Filled.AutoAwesome,
        onClick = onAiDigest,
    ),
    HhhlOverflowMenuAction(
        label = "AI 互动建议",
        enabled = enabled,
        icon = Icons.Filled.AutoAwesome,
        onClick = onAiReplyOpportunities,
    ),
    HhhlOverflowMenuAction(
        label = "AI 过滤建议",
        enabled = enabled,
        icon = Icons.Filled.AutoAwesome,
        onClick = onAiFilterSuggestions,
    ),
)

data class TimelineThreadItem(
    val note: Note,
    val depth: Int,
)

fun timelineThreadItems(notes: List<Note>): List<TimelineThreadItem> {
    if (notes.isEmpty()) return emptyList()

    val notesById = LinkedHashMap<String, Note>()
    notes.forEach { note ->
        if (!notesById.containsKey(note.id)) {
            notesById[note.id] = note
        }
    }

    val childrenByParent = LinkedHashMap<String, MutableList<Note>>()
    val roots = ArrayList<Note>()
    notesById.values.forEach { note ->
        val parentId = note.replyId
        if (!parentId.isNullOrBlank() && notesById.containsKey(parentId)) {
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(note)
        } else {
            roots += note
        }
    }

    val visible = ArrayList<TimelineThreadItem>(notesById.size)
    val seen = HashSet<String>(notesById.size)

    fun append(note: Note, depth: Int) {
        if (!seen.add(note.id)) return
        visible += TimelineThreadItem(note = note, depth = depth.coerceAtLeast(1))
        childrenByParent[note.id].orEmpty().forEach { child ->
            append(child, depth + 1)
        }
    }

    roots.forEach { append(it, 1) }
    notesById.values.forEach { append(it, timelineReplyDepth(it, notesById)) }
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
    val colors = LocalHhhlColors.current
    val treeLineColor = colors.noteTreeLine
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
                                presentation.isDepthCollapsed -> treeLineColor.copy(alpha = 0.18f)
                                isActiveRail -> treeLineColor.copy(alpha = 0.78f)
                                else -> treeLineColor.copy(alpha = 0.46f)
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
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
private fun TimelineSkeletonList(count: Int = 4) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            TimelineSkeletonRow(compact = index % 2 == 1)
        }
    }
}

@Composable
private fun TimelineSkeletonRow(compact: Boolean) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.inputBackground.copy(alpha = 0.72f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimelineSkeletonBar(widthFraction = if (compact) 0.46f else 0.58f, height = 12.dp)
            TimelineSkeletonBar(widthFraction = 1f, height = 11.dp)
            TimelineSkeletonBar(widthFraction = if (compact) 0.62f else 0.82f, height = 11.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) {
                    TimelineSkeletonBar(height = 10.dp, modifier = Modifier.width(34.dp))
                }
            }
        }
    }
    HhhlDivider()
}

@Composable
private fun TimelineSkeletonBar(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float? = null,
) {
    val colors = LocalHhhlColors.current
    val sizeModifier = if (widthFraction == null) modifier else modifier.fillMaxWidth(widthFraction)
    Box(
        modifier = sizeModifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.inputBackground.copy(alpha = 0.72f)),
    )
}
