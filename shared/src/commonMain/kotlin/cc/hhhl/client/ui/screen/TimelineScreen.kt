package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import cc.hhhl.client.ui.component.AiResultCommonActionChips
import cc.hhhl.client.ui.component.AiResultPanel
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlAnimatedSegmentedControl
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.LocalBlockedNoteAuthorIds
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.hhhlReadableOnControlColor
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
    onCopyAiResult: ((String) -> Unit)? = null,
    onAddAiMutedWord: ((String) -> Unit)? = null,
    onAddAiRelatedNoteToWatchLater: ((String, List<Note>) -> Unit)? = null,
    onOpenAiRelatedNote: ((String, List<Note>) -> Unit)? = null,
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
    var expandedTimelineRootId by remember(visibleSelectedKind) { mutableStateOf<String?>(null) }
    val timelineListEntries = remember(indexedTimelineThreadItems, expandedTimelineRootId, visibleSelectedKind) {
        if (visibleSelectedKind == TimelineKind.Home) {
            timelineAccordionEntries(
                indexedItems = indexedTimelineThreadItems,
                expandedRootId = expandedTimelineRootId,
            )
        } else {
            indexedTimelineThreadItems.map { TimelineAccordionEntry.NoteEntry(it) }
        }
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

    val toolbarActions = if (showTrends) {
        timelineSummaryActions(
            isRefreshing = isRefreshingTrends,
            onSearch = onSearch,
            onRefresh = onRefreshTrends,
        )
    } else {
        timelineSummaryActions(
            isRefreshing = selectedTabState.isLoading || selectedTabState.isLoadingMore,
            onSearch = onSearch,
            onRefresh = { onRefresh(visibleSelectedKind) },
            aiEnabled = aiEnabled,
            aiActionEnabled = aiEnabled && !isAiProcessing && selectedTabState.notes.isNotEmpty(),
            isAiProcessing = isAiProcessing,
            onAiDigest = { onAiAction(AiTaskKind.TimelineDigest, visibleSelectedKind, visibleNotes) },
            onAiReplyOpportunities = {
                onAiAction(AiTaskKind.TimelineReplyOpportunities, visibleSelectedKind, visibleNotes)
            },
            onAiFilterSuggestions = {
                onAiAction(AiTaskKind.TimelineFilterSuggestions, visibleSelectedKind, visibleNotes)
            },
        )
    }
    val jumpToNewNotes: (() -> Unit)? = if (!showTrends && firstUnreadIndex >= 0 && selectedTabState.newNoteCount > 0) {
        {
            coroutineScope.launch {
                listState.animateScrollToItem(firstUnreadIndex)
                onNewNotesMarkerConsumed(visibleSelectedKind)
            }
            Unit
        }
    } else {
        null
    }
    val screenColors = LocalHhhlColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenColors.pageBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TimelineTabStrip(
                availableKinds = availableKinds,
                visibleKinds = visibleKinds,
                selectedKind = visibleSelectedKind,
                showTrends = showTrends,
                hasTrendTab = hasTrendTab,
                toolbarActions = toolbarActions,
                newNoteCount = selectedTabState.newNoteCount,
                onJumpToNewNotes = jumpToNewNotes,
                onTimelineSelected = onTimelineSelected,
                onTrendSelected = onTrendSelected,
            )
            HhhlDivider()
            if (!showTrends && !aiResultText.isNullOrBlank()) {
                TimelineAiResultPanel(
                    label = aiResultLabel ?: "AI 速览",
                    text = aiResultText,
                    notes = visibleNotes,
                    onCopyAiResult = onCopyAiResult,
                    onAddAiMutedWord = onAddAiMutedWord,
                    onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
                    onOpenAiRelatedNote = onOpenAiRelatedNote,
                    onDismiss = onDismissAiResult,
                )
                HhhlDivider()
            }
            if (showTrends) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = trendListState,
                    contentPadding = PaddingValues(bottom = 76.dp),
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
                    contentPadding = PaddingValues(bottom = 76.dp),
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
                        items = timelineListEntries,
                        key = { it.key },
                        contentType = { it.contentType },
                    ) { entry ->
                        when (entry) {
                            is TimelineAccordionEntry.NoteEntry -> {
                                val indexedItem = entry.indexedItem
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
                            is TimelineAccordionEntry.ToggleEntry -> {
                                TimelineAccordionToggleRow(
                                    replyCount = entry.replyCount,
                                    expanded = entry.expanded,
                                    onClick = {
                                        expandedTimelineRootId = if (entry.expanded) null else entry.rootId
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        TimelineComposeFab(
            onCompose = onCompose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun TimelineTabStrip(
    availableKinds: List<TimelineKind>,
    visibleKinds: List<TimelineKind>,
    selectedKind: TimelineKind,
    showTrends: Boolean,
    hasTrendTab: Boolean,
    toolbarActions: List<HhhlOverflowMenuAction>,
    newNoteCount: Int,
    onJumpToNewNotes: (() -> Unit)?,
    onTimelineSelected: (TimelineKind) -> Unit,
    onTrendSelected: () -> Unit,
) {
    val overflowActions = timelineToolbarActions(
        toolbarActions = toolbarActions,
        timelineActions = timelineOverflowActions(
            availableKinds = availableKinds,
            selectedKind = selectedKind,
            onTimelineSelected = onTimelineSelected,
        ),
    )
    val tabs = buildList {
        addAll(visibleKinds.map { TimelineTabItem(label = it.label, kind = it) })
        if (hasTrendTab) add(TimelineTabItem(label = "趋势", kind = null))
    }
    val colors = LocalHhhlColors.current
    val selectedTabIndex = tabs.indexOfFirst { tab ->
        val isTrend = tab.kind == null
        if (isTrend) showTrends else !showTrends && selectedKind == tab.kind
    }.coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            modifier = Modifier
                .weight(1f),
            itemBaseHeight = 32.dp,
        )
        if (onJumpToNewNotes != null && newNoteCount > 0) {
            HhhlActionChip(
                label = "新 $newNoteCount",
                emphasized = true,
                onClick = onJumpToNewNotes,
            )
        }
        if (overflowActions.isNotEmpty()) {
            HhhlOverflowMenu(
                actions = overflowActions,
                label = if (showTrends) "趋势操作" else "时间线操作",
                buttonContainerColor = Color.Transparent,
                iconTint = colors.textPrimary,
                buttonWidth = 42.dp,
                buttonHeight = 42.dp,
                buttonIconSize = 21.dp,
                buttonCornerRadius = 999.dp,
                buttonBorderAlpha = 0f,
                buttonElevation = 0.dp,
            )
        }
    }
}

private data class TimelineTabItem(
    val label: String,
    val kind: TimelineKind?,
)

@Composable
private fun TimelineComposeFab(
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(999.dp)
    val containerColor = colors.accent
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(
                elevation = 7.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow.copy(alpha = 0.36f),
                spotColor = colors.shadow.copy(alpha = 0.42f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor,
                        containerColor.copy(alpha = 0.82f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = colors.focusRing.copy(alpha = 0.44f),
                shape = shape,
            )
            .clickable(onClick = onCompose)
            .semantics { contentDescription = "写帖" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = hhhlReadableOnControlColor(containerColor, colors.textPrimary),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun TimelineAiResultPanel(
    label: String,
    text: String,
    notes: List<Note>,
    onCopyAiResult: ((String) -> Unit)?,
    onAddAiMutedWord: ((String) -> Unit)?,
    onAddAiRelatedNoteToWatchLater: ((String, List<Note>) -> Unit)?,
    onOpenAiRelatedNote: ((String, List<Note>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        actions = {
            AiResultCommonActionChips(
                text = text,
                onCopyChecklist = onCopyAiResult,
                onAddMutedWord = onAddAiMutedWord,
                onAddToWatchLater = onAddAiRelatedNoteToWatchLater?.let { add -> { add(text, notes) } },
                onOpenRelatedNote = onOpenAiRelatedNote?.let { open -> { open(text, notes) } },
            )
        },
    )
}

@Composable
private fun TimelineNewContentDivider(newNoteCount: Int) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
private fun TimelineTrendRow(
    trend: TrendingHashtag,
    onOpenHashtag: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenHashtag(trend.tag) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    aiEnabled: Boolean = false,
    aiActionEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    onAiDigest: () -> Unit = {},
    onAiReplyOpportunities: () -> Unit = {},
    onAiFilterSuggestions: () -> Unit = {},
): List<HhhlOverflowMenuAction> = buildList {
    add(
        HhhlOverflowMenuAction(
            label = "搜索",
            onClick = onSearch,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = if (isRefreshing) "刷新中" else "刷新",
            enabled = !isRefreshing,
            onClick = onRefresh,
        ),
    )
    if (aiEnabled) {
        addAll(
            timelineAiActions(
                enabled = aiActionEnabled,
                isProcessing = isAiProcessing,
                onAiDigest = onAiDigest,
                onAiReplyOpportunities = onAiReplyOpportunities,
                onAiFilterSuggestions = onAiFilterSuggestions,
            ),
        )
    }
}

fun timelineToolbarActions(
    toolbarActions: List<HhhlOverflowMenuAction>,
    timelineActions: List<HhhlOverflowMenuAction>,
): List<HhhlOverflowMenuAction> = buildList {
    addAll(toolbarActions)
    addAll(timelineActions)
}

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
        label = if (isProcessing) "AI 处理中" else "AI",
        enabled = enabled,
        icon = Icons.Filled.AutoAwesome,
        onClick = {},
        children = listOf(
            HhhlOverflowMenuAction(label = "时间线速览", icon = Icons.Filled.AutoAwesome, onClick = onAiDigest),
            HhhlOverflowMenuAction(label = "互动建议", icon = Icons.Filled.AutoAwesome, onClick = onAiReplyOpportunities),
            HhhlOverflowMenuAction(label = "过滤建议", icon = Icons.Filled.AutoAwesome, onClick = onAiFilterSuggestions),
        ),
    ),
)

data class TimelineThreadItem(
    val note: Note,
    val depth: Int,
)

private sealed interface TimelineAccordionEntry {
    val key: String
    val contentType: String

    data class NoteEntry(
        val indexedItem: IndexedValue<TimelineThreadItem>,
    ) : TimelineAccordionEntry {
        override val key: String = indexedItem.value.note.id
        override val contentType: String = "timeline-note"
    }

    data class ToggleEntry(
        val rootId: String,
        val replyCount: Int,
        val expanded: Boolean,
    ) : TimelineAccordionEntry {
        override val key: String = "timeline-thread-toggle-$rootId"
        override val contentType: String = "timeline-thread-toggle"
    }
}

private fun timelineAccordionEntries(
    indexedItems: List<IndexedValue<TimelineThreadItem>>,
    expandedRootId: String?,
): List<TimelineAccordionEntry> = buildList {
    var index = 0
    while (index < indexedItems.size) {
        val root = indexedItems[index]
        add(TimelineAccordionEntry.NoteEntry(root))
        if (root.value.depth != 1) {
            index += 1
            continue
        }

        var endExclusive = index + 1
        while (endExclusive < indexedItems.size && indexedItems[endExclusive].value.depth > 1) {
            endExclusive += 1
        }
        val replyCount = endExclusive - index - 1
        if (replyCount > 0) {
            val expanded = root.value.note.id == expandedRootId
            if (expanded) {
                for (childIndex in index + 1 until endExclusive) {
                    add(TimelineAccordionEntry.NoteEntry(indexedItems[childIndex]))
                }
            }
            add(
                TimelineAccordionEntry.ToggleEntry(
                    rootId = root.value.note.id,
                    replyCount = replyCount,
                    expanded = expanded,
                ),
            )
            index = endExclusive
        } else {
            index += 1
        }
    }
}

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

@Composable
private fun TimelineAccordionToggleRow(
    replyCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 76.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.inputBackground.copy(alpha = 0.54f))
            .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "收起回复" else "展开 $replyCount 条回复",
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
