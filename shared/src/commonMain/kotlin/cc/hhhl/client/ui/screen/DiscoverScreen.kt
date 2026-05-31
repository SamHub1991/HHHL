package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.DiscoverRecommendationFeedbackEvent
import cc.hhhl.client.api.DiscoverRecommendedTimelineCategory
import cc.hhhl.client.api.DiscoverRecommendedTimelineScope
import cc.hhhl.client.model.Channel
import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.NoteSearchTrends
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.RoleSummary
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOperator
import cc.hhhl.client.state.DiscoverSearchOrigin
import cc.hhhl.client.state.DiscoverSearchMode
import cc.hhhl.client.state.DiscoverUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlDropdownMenu
import cc.hhhl.client.ui.component.HhhlDropdownMenuItem
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlSegmentedControl
import cc.hhhl.client.ui.component.HhhlSegmentedItem
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.presentation.notePreviewText

@Composable
fun DiscoverScreen(
    state: DiscoverUiState? = null,
    onQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onModeSelected: (DiscoverSearchMode) -> Unit = {},
    onFiltersChanged: (DiscoverAdvancedFilters) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onLoadMoreRecommended: () -> Unit = {},
    onRecommendedScopeSelected: (DiscoverRecommendedTimelineScope) -> Unit = {},
    onRecommendedCategorySelected: (DiscoverRecommendedTimelineCategory) -> Unit = {},
    onToggleRecommendedWithFiles: () -> Unit = {},
    onRecommendationFeedback: (String, DiscoverRecommendationFeedbackEvent) -> Unit = { _, _ -> },
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
    onOpenChannels: () -> Unit = {},
    onOpenPages: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenFlash: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onOpenChannel: (Channel) -> Unit = {},
    onOpenFederationInstance: (FederationInstance) -> Unit = {},
    onCloseFederationInstanceDetails: () -> Unit = {},
    onToggleFederationSilence: (String) -> Unit = {},
    onToggleFederationBlock: (String) -> Unit = {},
    onOpenRole: (String) -> Unit = {},
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = LocalHhhlColors.current
    val query = state?.query.orEmpty()
    val selectedMode = state?.selectedMode ?: DiscoverSearchMode.Notes
    var advancedFiltersExpanded by remember(selectedMode) { mutableStateOf(false) }
    val visibleModes = discoverVisibleModes(
        canSearchNotes = state?.canSearchNotes != false,
        canTrend = state?.canTrend == true,
        canViewFederation = state?.canViewFederation == true,
    )
    val canLoadMore = state != null &&
        !state.endReached &&
        (
            (state.selectedMode == DiscoverSearchMode.Notes && state.notes.isNotEmpty()) ||
                (
                    state.selectedMode == DiscoverSearchMode.Hashtags &&
                        state.query.isBlank() &&
                        state.trends.isNotEmpty()
                    ) ||
                (
                    state.selectedMode == DiscoverSearchMode.Federation &&
                        state.federationInstances.isNotEmpty()
                    )
            )
    val autoLoadItemCount = when (state?.selectedMode) {
        DiscoverSearchMode.Notes -> state.notes.size
        DiscoverSearchMode.Hashtags -> state.trends.size
        DiscoverSearchMode.Federation -> state.federationInstances.size
        else -> 0
    }
    val canLoadMoreRecommended = state != null &&
        state.selectedMode == DiscoverSearchMode.Notes &&
        state.query.isBlank() &&
        !state.hasSearched &&
        state.recommendedNotes.isNotEmpty() &&
        !state.recommendedEndReached
    val recommendationItemCount = if (state?.selectedMode == DiscoverSearchMode.Notes && state.query.isBlank()) {
        state.recommendedNotes.size
    } else {
        0
    }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = autoLoadItemCount,
        isLoadingMore = state?.isLoadingMore == true || !canLoadMore,
        onLoadMore = onLoadMore,
    )
    AutoLoadMoreEffect(
        listState = listState,
        itemCount = recommendationItemCount,
        isLoadingMore = state?.isLoadingMoreRecommendedNotes == true || !canLoadMoreRecommended,
        onLoadMore = onLoadMoreRecommended,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        DiscoverSearchHeader(
            query = query,
            selectedMode = selectedMode,
            visibleModes = visibleModes,
            isSearching = state?.isSearching == true,
            onQueryChanged = onQueryChanged,
            onSearch = onSearch,
            onModeSelected = onModeSelected,
            onOpenChannels = onOpenChannels,
            onOpenPages = onOpenPages,
            onOpenGallery = onOpenGallery,
            onOpenFlash = onOpenFlash,
            onOpenAnnouncements = onOpenAnnouncements,
        )
        if (selectedMode != DiscoverSearchMode.Trends && selectedMode != DiscoverSearchMode.Hashtags && selectedMode != DiscoverSearchMode.Roles) {
            DiscoverFilterRow(
                selectedMode = selectedMode,
                filters = state?.filters ?: DiscoverAdvancedFilters(),
                expanded = advancedFiltersExpanded,
                onExpandedChanged = { advancedFiltersExpanded = it },
                onFiltersChanged = onFiltersChanged,
                onClearFilters = onClearFilters,
                onApply = onSearch,
            )
            HhhlDivider()
        }
        if (selectedMode == DiscoverSearchMode.Notes && query.isBlank() && state?.hasSearched != true) {
            DiscoverRecommendationControls(
                scope = state?.recommendedScope ?: DiscoverRecommendedTimelineScope.Mixed,
                category = state?.recommendedCategory ?: DiscoverRecommendedTimelineCategory.ForYou,
                withFiles = state?.recommendedWithFiles == true,
                onScopeSelected = onRecommendedScopeSelected,
                onCategorySelected = onRecommendedCategorySelected,
                onToggleWithFiles = onToggleRecommendedWithFiles,
            )
            HhhlDivider()
        }
        DiscoverSummaryRow(state = state)
        HhhlDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground),
            state = listState,
        ) {
            if (state == null) {
                item(key = "discover-null", contentType = "discover-status") {
                    DiscoverStatusRow(text = "暂无结果")
                }
            } else {
                if (
                    state.isSearching &&
                    state.notes.isEmpty() &&
                    state.users.isEmpty() &&
                    state.trends.isEmpty() &&
                    state.roles.isEmpty() &&
                    state.federationInstances.isEmpty()
                ) {
                    item(key = "discover-loading-${state.selectedMode.name}", contentType = "discover-status") {
                        DiscoverStatusRow(
                            text = "加载中...",
                            loading = true,
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    item(key = "discover-error-${state.selectedMode.name}", contentType = "discover-status") {
                        DiscoverStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = onSearch,
                        )
                    }
                }
                if (state.selectedMode == DiscoverSearchMode.Notes && state.query.isBlank() && !state.hasSearched) {
                    item(key = "discover-search-trends", contentType = "discover-search-trends") {
                        SearchTrendsPanel(
                            trends = state.searchTrends,
                            isLoading = state.isLoadingSearchTrends,
                            message = state.searchTrendsMessage,
                            onSearchTerm = { term ->
                                onQueryChanged(term)
                                onSearch()
                            },
                            onOpenHashtag = onOpenHashtag,
                        )
                    }
                    if (
                        state.discoverySections.coverNotes.isNotEmpty() ||
                        state.discoverySections.hotNotes.isNotEmpty() ||
                        state.discoverySections.tutorialNotes.isNotEmpty() ||
                        state.isLoadingDiscoverySections ||
                        state.discoveryMessage != null
                    ) {
                        item(key = "discover-sections-notes", contentType = "discover-sections-notes") {
                            DiscoverySectionsPanel(
                                state = state,
                                onOpenNote = onOpenNote,
                                onRecommendationFeedback = onRecommendationFeedback,
                            )
                        }
                    }
                    if (state.discoverySections.channels.isNotEmpty()) {
                        item(key = "discover-section-channels", contentType = "discover-section-channels") {
                            DiscoveryChannelsPanel(
                                channels = state.discoverySections.channels,
                                onOpenChannel = onOpenChannel,
                            )
                        }
                    }
                    if (state.discoverySections.users.isNotEmpty()) {
                        item(key = "discover-section-users", contentType = "discover-section-users") {
                            PinnedUsersPanel(
                                users = state.discoverySections.users,
                                isLoading = false,
                                message = "发现页推荐用户",
                                onOpenUser = onOpenUser,
                                title = "发现用户",
                            )
                        }
                    }
                    if (state.recommendedNotes.isNotEmpty() || state.isLoadingRecommendedNotes || state.recommendedMessage != null) {
                        item(key = "discover-recommended-header", contentType = "discover-recommended-header") {
                            RecommendedTimelineHeader(
                                noteCount = state.recommendedNotes.size,
                                isLoading = state.isLoadingRecommendedNotes,
                                message = state.recommendedMessage,
                            )
                        }
                    }
                    items(
                        items = state.recommendedNotes,
                        key = { "discover-recommended-${it.id}" },
                        contentType = { "discover-recommended-note" },
                    ) { note ->
                        LaunchedEffect(note.id) {
                            onRecommendationFeedback(note.id, DiscoverRecommendationFeedbackEvent.Impression)
                        }
                        NoteRow(
                            note = note,
                            onClick = { noteId ->
                                onRecommendationFeedback(noteId, DiscoverRecommendationFeedbackEvent.Click)
                                onOpenNote(noteId)
                            },
                            onOpenUser = onOpenUser,
                            onReply = { noteId ->
                                onRecommendationFeedback(noteId, DiscoverRecommendationFeedbackEvent.Reply)
                                onReply(noteId)
                            },
                            onRenote = { noteId ->
                                onRecommendationFeedback(noteId, DiscoverRecommendationFeedbackEvent.Renote)
                                onRenote(noteId)
                            },
                            onQuote = onQuote,
                            onReact = { noteId, reaction ->
                                onRecommendationFeedback(noteId, DiscoverRecommendationFeedbackEvent.React)
                                onReact(noteId, reaction)
                            },
                            onDeleteReaction = onDeleteReaction,
                            onFavorite = onFavorite,
                            onAddToClip = onAddToClip?.let { addToClip ->
                                { target ->
                                    onRecommendationFeedback(target.id, DiscoverRecommendationFeedbackEvent.Clip)
                                    addToClip(target)
                                }
                            },
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
                    if (state.isLoadingMoreRecommendedNotes) {
                        item(key = "discover-recommended-loading-more", contentType = "discover-status") {
                            DiscoverStatusRow(text = "正在加载更多推荐...", loading = true)
                        }
                    } else if (state.recommendedEndReached && state.recommendedNotes.isNotEmpty()) {
                        item(key = "discover-recommended-end", contentType = "discover-status") {
                            DiscoverStatusRow(text = "已显示全部 ${state.recommendedNotes.size} 条推荐")
                        }
                    }
                }
                if (state.pinnedUsers.isNotEmpty() || state.isLoadingPinnedUsers || state.pinnedUsersMessage != null) {
                    item(key = "discover-pinned-users", contentType = "discover-pinned-users") {
                        PinnedUsersPanel(
                            users = state.pinnedUsers,
                            isLoading = state.isLoadingPinnedUsers,
                            message = state.pinnedUsersMessage,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
                state.selectedFederationInstance?.let { instance ->
                    item(
                        key = "federation-detail-${instance.host}",
                        contentType = "discover-federation-detail",
                    ) {
                        FederationInstanceDetail(
                            instance = instance,
                            isLoading = state.isLoadingFederationDetail,
                            actionPending = state.federationActionHost == instance.host,
                            message = state.federationDetailMessage,
                            onClose = onCloseFederationInstanceDetails,
                            onToggleSilence = { onToggleFederationSilence(instance.host) },
                            onToggleBlock = { onToggleFederationBlock(instance.host) },
                        )
                    }
                }
                state.selectedHashtag?.let { hashtag ->
                    item(
                        key = "hashtag-detail-${hashtag.tag}",
                        contentType = "discover-hashtag-detail",
                    ) {
                        HashtagDetailPanel(
                            hashtag = hashtag,
                            users = state.hashtagUsers,
                            isLoadingUsers = state.isLoadingHashtagDetails,
                            message = state.hashtagDetailMessage,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
                if (
                    state.hasSearched &&
                    !state.isSearching &&
                    state.notes.isEmpty() &&
                    state.users.isEmpty() &&
                    state.trends.isEmpty() &&
                    state.roles.isEmpty() &&
                    state.federationInstances.isEmpty() &&
                    state.errorMessage == null
                ) {
                    item(key = "discover-empty-${state.selectedMode.name}", contentType = "discover-status") {
                        DiscoverStatusRow(
                            text = when (state.selectedMode) {
                                DiscoverSearchMode.Trends -> "暂无趋势"
                                DiscoverSearchMode.Federation -> "暂无联邦实例"
                                DiscoverSearchMode.Hashtags -> "暂无话题"
                                DiscoverSearchMode.Roles -> "暂无角色"
                                else -> "没有搜索结果"
                            },
                        )
                    }
                }
                items(
                    items = state.roles,
                    key = { "role-${it.id}" },
                    contentType = { "discover-role" },
                ) { role ->
                    RoleRow(
                        role = role,
                        selected = state.selectedRole?.id == role.id,
                        onOpen = { onOpenRole(role.id) },
                    )
                }
                state.selectedRole?.let { role ->
                    item(key = "role-detail-${role.id}", contentType = "discover-role-detail") {
                        RoleDetailPanel(
                            role = role,
                            users = state.roleUsers,
                            notes = state.roleNotes,
                            isLoading = state.isLoadingRoleDetails,
                            message = state.roleDetailMessage,
                            onOpenUser = onOpenUser,
                            onOpenNote = onOpenNote,
                        )
                    }
                }
                items(
                    items = state.trends,
                    key = { "trend-${it.tag}" },
                    contentType = { "discover-trend" },
                ) { trend ->
                    TrendRow(
                        trend = trend,
                        onOpenHashtag = onOpenHashtag,
                    )
                }
                items(
                    items = state.federationInstances,
                    key = { "federation-${it.id}" },
                    contentType = { "discover-federation" },
                ) { instance ->
                    FederationInstanceRow(
                        instance = instance,
                        onOpen = { onOpenFederationInstance(instance) },
                    )
                }
                items(
                    items = state.users,
                    key = { "discover-user-${it.id}" },
                    contentType = { "discover-user" },
                ) { user ->
                    DiscoverUserRow(
                        user = user,
                        onOpenUser = onOpenUser,
                    )
                }
                items(
                    items = state.notes,
                    key = { "discover-${it.id}" },
                    contentType = { "discover-note" },
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
                if (canLoadMore && state.isLoadingMore) {
                    item(key = "discover-loading-more-${state.selectedMode.name}", contentType = "discover-status") {
                        DiscoverStatusRow(
                            text = "正在加载更多...",
                            loading = true,
                        )
                    }
                } else if (state.hasSearched && state.endReached && state.resultCount > 0) {
                    item(key = "discover-end-${state.selectedMode.name}", contentType = "discover-status") {
                        DiscoverStatusRow(text = discoverEndReachedText(state))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSearchHeader(
    query: String,
    selectedMode: DiscoverSearchMode,
    visibleModes: List<DiscoverSearchMode>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onModeSelected: (DiscoverSearchMode) -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(22.dp)
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val containerColor = if (isDarkSurface) {
        Color.White.copy(alpha = 0.045f)
    } else {
        colors.surfaceElevated.copy(alpha = 0.78f)
    }
    val borderColor = if (isDarkSurface) {
        Color.White.copy(alpha = 0.07f)
    } else {
        colors.border.copy(alpha = 0.46f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectedMode != DiscoverSearchMode.Trends && selectedMode != DiscoverSearchMode.Federation && selectedMode != DiscoverSearchMode.Roles) {
            DiscoverSearchRow(
                query = query,
                selectedMode = selectedMode,
                isSearching = isSearching,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
            )
        } else {
            DiscoverPassiveSearchPanel(
                selectedMode = selectedMode,
                isSearching = isSearching,
                onRefresh = onSearch,
            )
        }
        DiscoverModeSelector(
            selectedMode = selectedMode,
            visibleModes = visibleModes,
            onModeSelected = onModeSelected,
        )
        DiscoverQuickActionRow(
            onOpenChannels = onOpenChannels,
            onOpenPages = onOpenPages,
            onOpenGallery = onOpenGallery,
            onOpenFlash = onOpenFlash,
            onOpenAnnouncements = onOpenAnnouncements,
        )
    }
}

@Composable
private fun DiscoverQuickActionRow(
    onOpenChannels: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiscoverQuickAction(
            label = "频道",
            icon = Icons.Filled.Forum,
            onClick = onOpenChannels,
        )
        DiscoverQuickAction(
            label = "页面",
            icon = Icons.AutoMirrored.Outlined.Article,
            onClick = onOpenPages,
        )
        DiscoverQuickAction(
            label = "图库",
            icon = Icons.Filled.Image,
            onClick = onOpenGallery,
        )
        DiscoverQuickAction(
            label = "Play",
            icon = Icons.Filled.Apps,
            onClick = onOpenFlash,
        )
        DiscoverQuickActionOverflow(
            actions = discoverSecondaryQuickActions(
                onOpenPages = onOpenPages,
                onOpenGallery = onOpenGallery,
                onOpenFlash = onOpenFlash,
                onOpenAnnouncements = onOpenAnnouncements,
            ),
        )
    }
}

@Composable
private fun DiscoverQuickAction(
    label: String,
    icon: ImageVector,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier.width(DiscoverQuickActionWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HhhlIconActionButton(
            icon = icon,
            contentDescription = label,
            emphasized = emphasized,
            onClick = onClick,
        )
        Text(
            text = label,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiscoverQuickActionOverflow(
    actions: List<HhhlOverflowMenuAction>,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    Box {
        DiscoverQuickAction(
            label = "更多",
            icon = Icons.Filled.MoreVert,
            onClick = { if (actions.isNotEmpty()) expanded = true },
        )
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 0.dp, y = 6.dp),
            modifier = Modifier.widthIn(min = 184.dp, max = 240.dp),
        ) {
            actions.forEach { action ->
                HhhlDropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.width(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                action.icon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (action.destructive) colors.danger else colors.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = action.label,
                                color = if (action.destructive) colors.danger else colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                        }
                    },
                    enabled = action.enabled,
                    destructive = action.destructive,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (action.destructive) colors.danger.copy(alpha = 0.07f) else Color.Transparent,
                        ),
                )
            }
        }
    }
}

private val DiscoverQuickActionWidth = 54.dp

@Composable
private fun DiscoverRecommendationControls(
    scope: DiscoverRecommendedTimelineScope,
    category: DiscoverRecommendedTimelineCategory,
    withFiles: Boolean,
    onScopeSelected: (DiscoverRecommendedTimelineScope) -> Unit,
    onCategorySelected: (DiscoverRecommendedTimelineCategory) -> Unit,
    onToggleWithFiles: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HhhlSegmentedControl(modifier = Modifier.fillMaxWidth()) {
            DiscoverRecommendedTimelineScope.entries.forEach { item ->
                HhhlSegmentedItem(
                    label = item.label,
                    selected = item == scope,
                    onClick = { onScopeSelected(item) },
                    modifier = Modifier.weight(1f),
                    selectedUsesPrimary = true,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoverRecommendedTimelineCategory.entries.forEach { item ->
                HhhlActionChip(
                    label = item.label,
                    emphasized = item == category,
                    enabled = item != category,
                    onClick = { onCategorySelected(item) },
                )
            }
            HhhlActionChip(
                label = "带附件",
                emphasized = withFiles,
                onClick = onToggleWithFiles,
            )
        }
    }
}

@Composable
private fun DiscoverPassiveSearchPanel(
    selectedMode: DiscoverSearchMode,
    isSearching: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.inputBackground.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = 0.46f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.buttonSelectedBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selectedMode == DiscoverSearchMode.Trends) {
                    Icons.Filled.Search
                } else {
                    Icons.Filled.Notifications
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selectedMode == DiscoverSearchMode.Trends) "趋势" else "联邦实例",
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isSearching) "正在同步" else "点击刷新获取最新内容",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        HhhlActionChip(
            label = if (isSearching) "同步中" else "刷新",
            emphasized = true,
            enabled = !isSearching,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun DiscoverModeSelector(
    selectedMode: DiscoverSearchMode,
    visibleModes: List<DiscoverSearchMode>,
    onModeSelected: (DiscoverSearchMode) -> Unit,
) {
    HhhlSegmentedControl(modifier = Modifier.fillMaxWidth()) {
        visibleModes.forEach { mode ->
            HhhlSegmentedItem(
                label = mode.label,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
                selectedUsesPrimary = true,
            )
        }
    }
}

@Composable
private fun DiscoverFilterRow(
    selectedMode: DiscoverSearchMode,
    filters: DiscoverAdvancedFilters,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onFiltersChanged: (DiscoverAdvancedFilters) -> Unit,
    onClearFilters: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedMode == DiscoverSearchMode.Notes || selectedMode == DiscoverSearchMode.Users) {
                HhhlActionChip(
                    label = if (expanded) "收起筛选" else discoverAdvancedFilterTriggerLabel(filters),
                    emphasized = filters.isActive || expanded,
                    onClick = { onExpandedChanged(!expanded) },
                )
            }
            if (selectedMode == DiscoverSearchMode.Notes || selectedMode == DiscoverSearchMode.Users) {
                HhhlOverflowMenu(
                    actions = discoverOriginFilterActions(filters, onFiltersChanged),
                    label = "来源筛选",
                    buttonText = "更多",
                )
            }
            if (filters.isActive) {
                discoverActiveFilterChips(filters, selectedMode).forEach { chip ->
                    HhhlActionChip(
                        label = chip.label,
                        onClick = { onFiltersChanged(chip.clear(filters)) },
                    )
                }
                if (!expanded) {
                    HhhlActionChip(label = "应用筛选", emphasized = true, onClick = onApply)
                    HhhlActionChip(label = "清除", onClick = onClearFilters)
                }
            }
        }
        if ((selectedMode == DiscoverSearchMode.Notes || selectedMode == DiscoverSearchMode.Users) && expanded) {
            if (selectedMode == DiscoverSearchMode.Notes) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiscoverSearchOperator.entries.forEach { operator ->
                        HhhlActionChip(
                            label = discoverOperatorFilterLabel(operator),
                            emphasized = filters.operator == operator,
                            enabled = filters.operator != operator,
                            onClick = { onFiltersChanged(filters.copy(operator = operator)) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = filters.username,
                    onValueChange = { onFiltersChanged(filters.copy(username = it)) },
                    placeholder = "用户名",
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                HhhlTextInput(
                    value = filters.domain,
                    onValueChange = { onFiltersChanged(filters.copy(domain = it)) },
                    placeholder = "域名",
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = filters.sinceDate,
                    onValueChange = { onFiltersChanged(filters.copy(sinceDate = it)) },
                    placeholder = "起始日期",
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                HhhlTextInput(
                    value = filters.untilDate,
                    onValueChange = { onFiltersChanged(filters.copy(untilDate = it)) },
                    placeholder = "结束日期",
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            if (selectedMode == DiscoverSearchMode.Notes) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlTextInput(
                        value = filters.userId,
                        onValueChange = { onFiltersChanged(filters.copy(userId = it)) },
                        placeholder = "用户 ID",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    HhhlTextInput(
                        value = filters.channelId,
                        onValueChange = { onFiltersChanged(filters.copy(channelId = it)) },
                        placeholder = "频道 ID",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                HhhlTextInput(
                    value = filters.excludeWords,
                    onValueChange = { onFiltersChanged(filters.copy(excludeWords = it)) },
                    placeholder = "排除词",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HhhlActionChip(
                        label = "带附件",
                        emphasized = filters.withFiles,
                        onClick = { onFiltersChanged(filters.copy(withFiles = !filters.withFiles)) },
                    )
                    HhhlActionChip(
                        label = "含回复",
                        emphasized = filters.includeReplies,
                        onClick = { onFiltersChanged(filters.copy(includeReplies = !filters.includeReplies)) },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HhhlActionChip(label = "应用筛选", emphasized = true, onClick = onApply)
                HhhlActionChip(label = "清除", onClick = onClearFilters)
            }
        } else if (selectedMode == DiscoverSearchMode.Federation) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = filters.domain,
                    onValueChange = { onFiltersChanged(filters.copy(domain = it)) },
                    placeholder = discoverFederationFilterPlaceholder(),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                HhhlActionChip(label = "筛选", emphasized = true, onClick = onApply)
                if (filters.domain.isNotBlank()) {
                    HhhlActionChip(label = "清除", onClick = onClearFilters)
                }
            }
        }
    }
}

@Composable
private fun DiscoverSearchRow(
    query: String,
    selectedMode: DiscoverSearchMode,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = discoverSearchPlaceholder(selectedMode),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
            singleLine = true,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            },
        )
        HhhlActionChip(
            label = discoverSearchActionLabel(isSearching),
            emphasized = query.isNotBlank(),
            enabled = !isSearching,
            onClick = onSearch,
        )
    }
}

@Composable
private fun DiscoverSummaryRow(state: DiscoverUiState?) {
    val colors = LocalHhhlColors.current
    val selectedMode = state?.selectedMode ?: DiscoverSearchMode.Notes
    val primaryText = when (selectedMode) {
        DiscoverSearchMode.Notes -> state?.query?.takeIf { it.isNotBlank() } ?: "帖子"
        DiscoverSearchMode.Users -> state?.query?.takeIf { it.isNotBlank() } ?: "用户"
        DiscoverSearchMode.Hashtags -> state?.query?.takeIf { it.isNotBlank() } ?: "话题"
        DiscoverSearchMode.Roles -> "角色"
        DiscoverSearchMode.Trends -> "趋势"
        DiscoverSearchMode.Federation -> state?.filters?.domain?.takeIf { it.isNotBlank() } ?: "联邦"
    }
    val secondaryText = when {
        state == null -> "未开始搜索"
        state.isSearching -> when (selectedMode) {
            DiscoverSearchMode.Trends -> "同步趋势中"
            DiscoverSearchMode.Federation -> "同步实例中"
            else -> "搜索中"
        }
        selectedMode == DiscoverSearchMode.Notes -> "${state.notes.size} 条帖子"
        selectedMode == DiscoverSearchMode.Users -> "${state.users.size} 个用户"
        selectedMode == DiscoverSearchMode.Hashtags -> "${state.trends.size} 个话题"
        selectedMode == DiscoverSearchMode.Roles -> "${state.roles.size} 个角色"
        selectedMode == DiscoverSearchMode.Trends -> "${state.trends.size} 个趋势"
        selectedMode == DiscoverSearchMode.Federation -> "${state.federationInstances.size} 个实例"
        else -> "未开始搜索"
    }
    val filterText = state?.filters?.let { discoverFilterSummary(it, selectedMode) }.orEmpty()
    val summaryText = listOf(primaryText, secondaryText, filterText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryText,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val DiscoverUiState.resultCount: Int
    get() = when (selectedMode) {
        DiscoverSearchMode.Notes -> notes.size
        DiscoverSearchMode.Users -> users.size
        DiscoverSearchMode.Hashtags -> trends.size
        DiscoverSearchMode.Roles -> roles.size
        DiscoverSearchMode.Trends -> trends.size
        DiscoverSearchMode.Federation -> federationInstances.size
    }

fun discoverEndReachedText(state: DiscoverUiState): String {
    return when (state.selectedMode) {
        DiscoverSearchMode.Notes -> "已显示全部 ${state.notes.size} 条帖子"
        DiscoverSearchMode.Users -> "已显示全部 ${state.users.size} 个用户"
        DiscoverSearchMode.Hashtags -> "已显示全部 ${state.trends.size} 个话题"
        DiscoverSearchMode.Roles -> "已显示全部 ${state.roles.size} 个角色"
        DiscoverSearchMode.Trends -> "已显示全部 ${state.trends.size} 个趋势"
        DiscoverSearchMode.Federation -> "已显示全部 ${state.federationInstances.size} 个实例"
    }
}

fun federationDetailMetrics(instance: FederationInstance): String {
    return federationDetailMetricRows(instance)
        .joinToString(" · ") { "${it.value} ${it.label}" }
}

fun federationDetailMeta(instance: FederationInstance): String {
    val software = listOfNotNull(
        instance.softwareName?.takeIf { it.isNotBlank() },
        instance.softwareVersion?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    val maintainer = instance.maintainerName?.takeIf { it.isNotBlank() }
        ?: instance.maintainerEmail?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        software.takeIf { it.isNotBlank() },
        maintainer?.let { "维护者 $it" },
        instance.infoUpdatedAtLabel.takeIf { it.isNotBlank() }?.let { "信息更新 $it" },
        instance.latestRequestReceivedAtLabel.takeIf { it.isNotBlank() }?.let { "最近请求 $it" },
    ).joinToString(" · ").ifBlank { "暂无更多实例信息" }
}

fun federationInstanceStatusLabel(instance: FederationInstance): String {
    return when {
        instance.isBlocked || instance.isSuspended -> "已阻止"
        instance.isNotResponding -> "无响应"
        instance.isSilenced -> "已静音"
        else -> "联邦中"
    }
}

data class FederationDetailField(
    val label: String,
    val value: String,
)

fun federationDetailMetricRows(instance: FederationInstance): List<FederationDetailField> {
    return listOf(
        FederationDetailField("用户", instance.usersCount.toGroupedNumber()),
        FederationDetailField("帖子", instance.notesCount.toGroupedNumber()),
        FederationDetailField("关注", instance.followingCount.toGroupedNumber()),
        FederationDetailField("粉丝", instance.followersCount.toGroupedNumber()),
    )
}

fun federationDetailInfoRows(instance: FederationInstance): List<FederationDetailField> {
    val software = listOfNotNull(
        instance.softwareName?.takeIf { it.isNotBlank() },
        instance.softwareVersion?.takeIf { it.isNotBlank() },
    ).joinToString(" ")
    val maintainer = instance.maintainerName?.takeIf { it.isNotBlank() }
        ?: instance.maintainerEmail?.takeIf { it.isNotBlank() }
    return buildList {
        add(FederationDetailField("域名", instance.host))
        software.takeIf { it.isNotBlank() }?.let { add(FederationDetailField("软件", it)) }
        maintainer?.let { add(FederationDetailField("维护者", it)) }
        add(FederationDetailField("状态", federationInstanceStatusLabel(instance)))
    }
}

fun federationDetailTimeRows(instance: FederationInstance): List<FederationDetailField> {
    return buildList {
        instance.infoUpdatedAtLabel.takeIf { it.isNotBlank() }?.let {
            add(FederationDetailField("信息更新", it))
        }
        instance.latestRequestReceivedAtLabel.takeIf { it.isNotBlank() }?.let {
            add(FederationDetailField("最近请求", it))
        }
    }.ifEmpty {
        listOf(FederationDetailField("时间", "暂无本地时间信息"))
    }
}

fun discoverAdvancedFilterTriggerLabel(filters: DiscoverAdvancedFilters): String {
    return if (filters.isActive) {
        "筛选 ${discoverActiveFilterCount(filters)}"
    } else {
        "高级筛选"
    }
}

private fun discoverActiveFilterCount(filters: DiscoverAdvancedFilters): Int {
    return listOf(
        filters.username,
        filters.userId,
        filters.domain,
        filters.channelId,
        filters.sinceDate,
        filters.untilDate,
        filters.excludeWords,
    ).count { it.isNotBlank() }
        .plus(if (filters.operator != DiscoverSearchOperator.AllWords) 1 else 0)
        .plus(if (filters.withFiles) 1 else 0)
        .plus(if (!filters.includeReplies) 1 else 0)
}

fun discoverQuickActionLabels(): List<String> = listOf("频道", "页面", "图库", "Play", "公告")

fun discoverPrimaryQuickActionLabels(): List<String> = listOf("频道", "页面", "图库", "Play")

fun discoverPrimarySearchModes(
    visibleModes: List<DiscoverSearchMode>,
    selectedMode: DiscoverSearchMode,
): List<DiscoverSearchMode> {
    val modes = buildList {
        listOf(DiscoverSearchMode.Notes, DiscoverSearchMode.Users).forEach { mode ->
            if (mode in visibleModes) add(mode)
        }
        if (selectedMode in visibleModes && selectedMode !in this) {
            add(selectedMode)
        }
    }
    return modes.ifEmpty { visibleModes.take(1) }
}

fun discoverOverflowSearchModes(
    visibleModes: List<DiscoverSearchMode>,
    primaryModes: List<DiscoverSearchMode>,
): List<DiscoverSearchMode> = visibleModes.filterNot { it in primaryModes }

fun discoverSearchPlaceholder(selectedMode: DiscoverSearchMode): String {
    return when (selectedMode) {
        DiscoverSearchMode.Users -> "搜索用户、@用户名"
        DiscoverSearchMode.Hashtags -> "搜索话题、#标签"
        DiscoverSearchMode.Roles -> "查看站点角色"
        DiscoverSearchMode.Notes -> "搜索帖子、话题、关键词"
        DiscoverSearchMode.Trends -> "趋势会自动加载"
        DiscoverSearchMode.Federation -> "联邦实例会自动加载"
    }
}

fun discoverSearchActionLabel(isSearching: Boolean): String {
    return if (isSearching) "搜索中" else "搜索"
}

fun discoverFilterOriginLabels(): List<String> {
    return DiscoverSearchOrigin.entries.map(::discoverOriginFilterLabel)
}

fun discoverFederationFilterPlaceholder(): String = "按实例域名筛选"

fun discoverOriginFilterLabel(origin: DiscoverSearchOrigin): String {
    return when (origin) {
        DiscoverSearchOrigin.Combined -> "全部来源"
        DiscoverSearchOrigin.Local -> "本地"
        DiscoverSearchOrigin.Remote -> "远程"
    }
}

private fun Int.toGroupedNumber(): String {
    val raw = toString()
    return raw.reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()
}

fun discoverOperatorFilterLabel(operator: DiscoverSearchOperator): String {
    return when (operator) {
        DiscoverSearchOperator.AllWords -> "全部词"
        DiscoverSearchOperator.AnyWord -> "任一词"
        DiscoverSearchOperator.ExactPhrase -> "精确短语"
    }
}

fun discoverActiveFilterLabels(
    filters: DiscoverAdvancedFilters,
    selectedMode: DiscoverSearchMode,
): List<String> {
    return discoverActiveFilterChips(filters, selectedMode).map { it.label }
}

private fun discoverSecondaryQuickActions(
    onOpenPages: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFlash: () -> Unit,
    onOpenAnnouncements: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return listOf(
        HhhlOverflowMenuAction("公告", onClick = onOpenAnnouncements),
    )
}

private fun discoverOriginFilterActions(
    filters: DiscoverAdvancedFilters,
    onFiltersChanged: (DiscoverAdvancedFilters) -> Unit,
): List<HhhlOverflowMenuAction> {
    return DiscoverSearchOrigin.entries.map { origin ->
        HhhlOverflowMenuAction(
            label = if (filters.origin == origin) {
                "当前：${discoverOriginFilterLabel(origin)}"
            } else {
                discoverOriginFilterLabel(origin)
            },
            enabled = filters.origin != origin,
            onClick = { onFiltersChanged(filters.copy(origin = origin)) },
        )
    }
}

private fun discoverOverflowSearchModeActions(
    visibleModes: List<DiscoverSearchMode>,
    primaryModes: List<DiscoverSearchMode>,
    selectedMode: DiscoverSearchMode,
    onModeSelected: (DiscoverSearchMode) -> Unit,
): List<HhhlOverflowMenuAction> {
    return discoverOverflowSearchModes(visibleModes, primaryModes).map { mode ->
        HhhlOverflowMenuAction(
            label = if (mode == selectedMode) "当前：${mode.label}" else mode.label,
            enabled = mode != selectedMode,
            onClick = { onModeSelected(mode) },
        )
    }
}

fun discoverVisibleModes(
    canSearchNotes: Boolean,
    canTrend: Boolean,
    canViewFederation: Boolean,
): List<DiscoverSearchMode> {
    return DiscoverSearchMode.entries.filter { mode ->
        when (mode) {
            DiscoverSearchMode.Notes -> canSearchNotes
            DiscoverSearchMode.Hashtags -> true
            DiscoverSearchMode.Roles -> true
            DiscoverSearchMode.Trends -> canTrend
            DiscoverSearchMode.Federation -> canViewFederation
            DiscoverSearchMode.Users -> true
        }
    }
}

@Composable
private fun FederationInstanceRow(
    instance: FederationInstance,
    onOpen: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name?.takeIf { it.isNotBlank() } ?: instance.host,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${instance.host} · ${instance.softwareName.orEmpty()} ${instance.softwareVersion.orEmpty()}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${instance.usersCount} 用户 · ${instance.notesCount} 帖子",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val status = federationInstanceStatusLabel(instance)
        Text(
            text = status,
            color = if (status == "联邦中") {
                colors.accent
            } else {
                colors.textSecondary
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
    HhhlDivider()
}

@Composable
private fun RoleRow(
    role: RoleSummary,
    selected: Boolean,
    onOpen: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .background(
                if (selected) colors.buttonSelectedBackground.copy(alpha = 0.52f) else colors.pageBackground,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = role.name.trim().firstOrNull()?.toString()?.uppercase() ?: "R",
                color = colors.accent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = role.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = role.description.ifBlank { "${role.usersCount} 人拥有" },
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (role.isAdministrator) HhhlActionChip(label = "管理", emphasized = true, enabled = false, onClick = {})
            if (role.isModerator) HhhlActionChip(label = "审核", emphasized = true, enabled = false, onClick = {})
            HhhlActionChip(label = "${role.usersCount} 人", enabled = false, onClick = {})
        }
    }
    HhhlDivider()
}

@Composable
private fun RoleDetailPanel(
    role: RoleSummary,
    users: List<cc.hhhl.client.model.User>,
    notes: List<Note>,
    isLoading: Boolean,
    message: String?,
    onOpenUser: (String) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = role.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = role.description.ifBlank { "角色详情" },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlActionChip(
                label = if (isLoading) "同步中" else "${users.size} 成员",
                emphasized = true,
                enabled = false,
                onClick = {},
            )
        }
        message?.takeIf { it.isNotBlank() }?.let { text ->
            Text(text = text, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (users.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                users.take(8).forEach { user ->
                    HhhlActionChip(
                        label = user.displayName.ifBlank { "@${user.username}" },
                        emphasized = false,
                        onClick = { onOpenUser(user.id) },
                    )
                }
            }
        }
        if (notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "角色动态",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                notes.take(3).forEach { note ->
                    InlineRichText(
                        text = notePreviewText(note),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxChars = 120,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(discoverInfoPillColor())
                            .clickable { onOpenNote(note.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FederationInstanceDetail(
    instance: FederationInstance,
    isLoading: Boolean,
    actionPending: Boolean,
    message: String?,
    onClose: () -> Unit,
    onToggleSilence: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.name?.takeIf { it.isNotBlank() } ?: instance.host,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = instance.host,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HhhlActionChip(label = "关闭", onClick = onClose)
        }
        instance.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FederationDetailSection(
            title = "状态",
            rows = listOf(FederationDetailField("联邦状态", federationInstanceStatusLabel(instance))),
        )
        FederationDetailSection(
            title = "统计",
            rows = federationDetailMetricRows(instance),
        )
        FederationDetailSection(
            title = "实例详情",
            rows = federationDetailInfoRows(instance),
        )
        FederationDetailSection(
            title = "时间",
            rows = federationDetailTimeRows(instance),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HhhlActionChip(
                label = if (instance.isSilenced) "取消静音" else "静音实例",
                emphasized = instance.isSilenced,
                enabled = !actionPending,
                onClick = onToggleSilence,
            )
            HhhlActionChip(
                label = if (instance.isSuspended || instance.isBlocked) "取消阻止" else "阻止实例",
                emphasized = instance.isSuspended || instance.isBlocked,
                enabled = !actionPending,
                onClick = onToggleBlock,
            )
        }
        val statusText = when {
            actionPending -> "正在更新联邦设置..."
            isLoading -> "正在读取实例详情..."
            message != null -> message
            else -> "管理操作需要管理员权限；服务端不支持时会显示降级提示"
        }
        Text(
            text = statusText,
            color = if (message == null && !isLoading && !actionPending) {
                colors.textSecondary
            } else {
                colors.accent
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HhhlDivider()
}

@Composable
private fun FederationDetailSection(
    title: String,
    rows: List<FederationDetailField>,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.label,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(72.dp),
                )
                Text(
                    text = row.value,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TrendRow(
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
            )
            Text(
                text = "${trend.usersCount} 人正在使用",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun PinnedUsersPanel(
    users: List<cc.hhhl.client.model.User>,
    isLoading: Boolean,
    message: String?,
    onOpenUser: (String) -> Unit,
    title: String = "推荐用户",
) {
    val colors = LocalHhhlColors.current
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isLoading) "同步中" else message ?: "站点置顶的用户",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (users.isNotEmpty()) {
                HhhlActionChip(
                    label = "${users.size} 人",
                    emphasized = true,
                    enabled = false,
                    onClick = {},
                )
            }
        }
        if (users.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                users.take(12).forEach { user ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(discoverInfoPillColor())
                            .border(1.dp, discoverInfoPillBorderColor(), RoundedCornerShape(999.dp))
                            .clickable { onOpenUser(user.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            initial = user.avatarInitial,
                            avatarUrl = user.avatarUrl,
                            avatarDecorations = user.avatarDecorations,
                            size = 24.dp,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Text(
                                text = user.displayName.ifBlank { "@${user.username}" },
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${user.notesCount} 条 · ${user.followersCount} 粉丝",
                                color = colors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashtagDetailPanel(
    hashtag: TrendingHashtag,
    users: List<cc.hhhl.client.model.User>,
    isLoadingUsers: Boolean,
    message: String?,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "#${hashtag.tag}",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${hashtag.usersCount} 人使用 · ${trendChartSummary(hashtag)}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlActionChip(
                label = if (isLoadingUsers) "同步中" else "使用者 ${users.size}",
                enabled = false,
                emphasized = users.isNotEmpty(),
                onClick = {},
            )
        }
        message?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (users.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                users.take(8).forEach { user ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(discoverInfoPillColor())
                            .border(1.dp, discoverInfoPillBorderColor(), RoundedCornerShape(999.dp))
                            .clickable { onOpenUser(user.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            initial = user.avatarInitial,
                            avatarUrl = user.avatarUrl,
                            avatarDecorations = user.avatarDecorations,
                            size = 24.dp,
                        )
                        Text(
                            text = user.displayName.ifBlank { "@${user.username}" },
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrendsPanel(
    trends: NoteSearchTrends,
    isLoading: Boolean,
    message: String?,
    onSearchTerm: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "搜索趋势",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isLoading) {
                Text(
                    text = "同步中",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        message?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SearchTrendGroup("热门搜索", trends.popularSearches, onClick = onSearchTerm)
        SearchTrendGroup("最近搜索", trends.recentTerms, onClick = onSearchTerm)
        SearchTrendGroup("话题", trends.hashtags, prefix = "#", onClick = onOpenHashtag)
    }
}

@Composable
private fun SearchTrendGroup(
    title: String,
    terms: List<String>,
    prefix: String = "",
    onClick: (String) -> Unit,
) {
    if (terms.isEmpty()) return
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            terms.take(10).forEach { term ->
                HhhlActionChip(
                    label = "$prefix$term",
                    onClick = { onClick(term) },
                )
            }
        }
    }
}

@Composable
private fun DiscoverySectionsPanel(
    state: DiscoverUiState,
    onOpenNote: (String) -> Unit,
    onRecommendationFeedback: (String, DiscoverRecommendationFeedbackEvent) -> Unit,
) {
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DiscoveryPanelHeader(
            title = "发现内容",
            meta = when {
                state.isLoadingDiscoverySections -> "同步中"
                state.discoveryMessage != null -> state.discoveryMessage
                else -> "封面、热门和教程"
            },
            icon = Icons.Filled.AutoAwesome,
        )
        DiscoveryNoteGroup(
            title = "封面",
            notes = state.discoverySections.coverNotes,
            onOpenNote = onOpenNote,
            onRecommendationFeedback = onRecommendationFeedback,
        )
        DiscoveryNoteGroup(
            title = "热门",
            notes = state.discoverySections.hotNotes,
            onOpenNote = onOpenNote,
            onRecommendationFeedback = onRecommendationFeedback,
        )
        DiscoveryNoteGroup(
            title = "教程",
            notes = state.discoverySections.tutorialNotes,
            onOpenNote = onOpenNote,
            onRecommendationFeedback = onRecommendationFeedback,
        )
    }
}

@Composable
private fun DiscoveryNoteGroup(
    title: String,
    notes: List<Note>,
    onOpenNote: (String) -> Unit,
    onRecommendationFeedback: (String, DiscoverRecommendationFeedbackEvent) -> Unit,
) {
    if (notes.isEmpty()) return
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        notes.take(4).forEach { note ->
            DiscoveryCompactNoteRow(
                note = note,
                onOpen = {
                    onRecommendationFeedback(note.id, DiscoverRecommendationFeedbackEvent.Click)
                    onOpenNote(note.id)
                },
            )
        }
    }
}

@Composable
private fun DiscoveryCompactNoteRow(
    note: Note,
    onOpen: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(discoverInfoPillColor())
            .border(1.dp, discoverInfoPillBorderColor(), RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = note.author.avatarInitial,
            avatarUrl = note.author.avatarUrl,
            avatarDecorations = note.author.avatarDecorations,
            size = 28.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = note.author.displayName.ifBlank { "@${note.author.username}" },
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notePreviewText(note).ifBlank { "无正文" },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = note.reactionCount.toString(),
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DiscoveryChannelsPanel(
    channels: List<Channel>,
    onOpenChannel: (Channel) -> Unit,
) {
    val colors = LocalHhhlColors.current
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DiscoveryPanelHeader(
            title = "发现频道",
            meta = "${channels.size} 个频道",
            icon = Icons.Filled.Forum,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channels.take(8).forEach { channel ->
                Column(
                    modifier = Modifier
                        .widthIn(min = 132.dp, max = 190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(discoverInfoPillColor())
                        .border(1.dp, discoverInfoPillBorderColor(), RoundedCornerShape(14.dp))
                        .clickable { onOpenChannel(channel) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = channel.name.ifBlank { "未命名频道" },
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${channel.usersCount} 人 · ${channel.notesCount} 条",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (channel.description.isNotBlank()) {
                        Text(
                            text = channel.description,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedTimelineHeader(
    noteCount: Int,
    isLoading: Boolean,
    message: String?,
) {
    DiscoverInfoPanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DiscoveryPanelHeader(
            title = "推荐帖子",
            meta = when {
                isLoading -> "同步中"
                message != null -> message
                else -> "$noteCount 条"
            },
            icon = Icons.Filled.AutoAwesome,
        )
    }
}

@Composable
private fun DiscoveryPanelHeader(
    title: String,
    meta: String?,
    icon: ImageVector,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        meta?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiscoverInfoPanel(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val shape = RoundedCornerShape(18.dp)
    val containerColor = if (isDarkSurface) {
        Color.White.copy(alpha = 0.050f)
    } else {
        colors.surface.copy(alpha = 0.82f)
    }
    val borderColor = if (isDarkSurface) {
        Color.White.copy(alpha = 0.075f)
    } else {
        colors.border.copy(alpha = 0.42f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
private fun discoverInfoPillColor(): Color {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    return if (isDarkSurface) {
        Color.White.copy(alpha = 0.075f)
    } else {
        colors.inputBackground.copy(alpha = 0.68f)
    }
}

@Composable
private fun discoverInfoPillBorderColor(): Color {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    return if (isDarkSurface) {
        Color.White.copy(alpha = 0.070f)
    } else {
        colors.border.copy(alpha = 0.30f)
    }
}

fun trendChartSummary(trend: TrendingHashtag): String {
    val latest = trend.chart.lastOrNull() ?: 0
    val total = trend.chart.sum()
    return when {
        latest > 0 -> "最近 +$latest"
        total > 0 -> "热度 $total"
        else -> "活跃中"
    }
}

@Composable
private fun DiscoverStatusRow(
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
private fun DiscoverUserRow(
    user: cc.hhhl.client.model.User,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(user.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = user.avatarInitial,
            avatarUrl = user.avatarUrl,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = user.discoverHandle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (user.bio.isNotBlank()) {
                InlineRichText(
                    text = user.bio,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxChars = 180,
                )
            }
        }
    }
    HhhlDivider()
}

private val cc.hhhl.client.model.User.discoverHandle: String
    get() = host?.takeIf { it.isNotBlank() }?.let { "@$username@$it" } ?: "@$username"

fun discoverFilterSummary(
    filters: DiscoverAdvancedFilters,
    selectedMode: DiscoverSearchMode,
): String {
    val parts = buildList {
        if (
            (selectedMode == DiscoverSearchMode.Notes || selectedMode == DiscoverSearchMode.Users) &&
            filters.origin != DiscoverSearchOrigin.Combined
        ) {
            add(filters.origin.label)
        }
        if (selectedMode == DiscoverSearchMode.Notes && filters.operator != DiscoverSearchOperator.AllWords) {
            add(filters.operator.label)
        }
        filters.username.takeIf { it.isNotBlank() }?.let { add("@${it.trim().removePrefix("@")}") }
        filters.userId.takeIf { it.isNotBlank() }?.let { add("用户 $it") }
        filters.domain.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        filters.channelId.takeIf { it.isNotBlank() }?.let { add("频道 $it") }
        filters.sinceDate.takeIf { it.isNotBlank() }?.let { add("自 $it") }
        filters.untilDate.takeIf { it.isNotBlank() }?.let { add("至 $it") }
        filters.excludeWords.takeIf { it.isNotBlank() }?.let { add("排除 $it") }
        if (filters.withFiles) add("带附件")
        if (!filters.includeReplies) add("不含回复")
    }
    return parts.joinToString(" / ")
}

private data class DiscoverFilterChip(
    val label: String,
    val clear: (DiscoverAdvancedFilters) -> DiscoverAdvancedFilters,
)

private fun discoverActiveFilterChips(
    filters: DiscoverAdvancedFilters,
    selectedMode: DiscoverSearchMode,
): List<DiscoverFilterChip> {
    return buildList {
        if (
            (selectedMode == DiscoverSearchMode.Notes || selectedMode == DiscoverSearchMode.Users) &&
            filters.origin != DiscoverSearchOrigin.Combined
        ) {
            add(
                DiscoverFilterChip(filters.origin.label) {
                    it.copy(origin = DiscoverSearchOrigin.Combined)
                },
            )
        }
        if (selectedMode == DiscoverSearchMode.Notes && filters.operator != DiscoverSearchOperator.AllWords) {
            add(
                DiscoverFilterChip(filters.operator.label) {
                    it.copy(operator = DiscoverSearchOperator.AllWords)
                },
            )
        }
        filters.username.takeIf { it.isNotBlank() }?.let { username ->
            add(
                DiscoverFilterChip("@${username.trim().removePrefix("@")}") {
                    it.copy(username = "")
                },
            )
        }
        filters.userId.takeIf { it.isNotBlank() }?.let { userId ->
            add(
                DiscoverFilterChip("用户 $userId") {
                    it.copy(userId = "")
                },
            )
        }
        filters.domain.takeIf { it.isNotBlank() }?.let { domain ->
            add(
                DiscoverFilterChip(domain.trim()) {
                    it.copy(domain = "")
                },
            )
        }
        filters.channelId.takeIf { it.isNotBlank() }?.let { channelId ->
            add(
                DiscoverFilterChip("频道 $channelId") {
                    it.copy(channelId = "")
                },
            )
        }
        filters.sinceDate.takeIf { it.isNotBlank() }?.let { date ->
            add(
                DiscoverFilterChip("自 $date") {
                    it.copy(sinceDate = "")
                },
            )
        }
        filters.untilDate.takeIf { it.isNotBlank() }?.let { date ->
            add(
                DiscoverFilterChip("至 $date") {
                    it.copy(untilDate = "")
                },
            )
        }
        filters.excludeWords.takeIf { it.isNotBlank() }?.let { words ->
            add(
                DiscoverFilterChip("排除 $words") {
                    it.copy(excludeWords = "")
                },
            )
        }
        if (filters.withFiles) {
            add(
                DiscoverFilterChip("带附件") {
                    it.copy(withFiles = false)
                },
            )
        }
        if (!filters.includeReplies) {
            add(
                DiscoverFilterChip("不含回复") {
                    it.copy(includeReplies = true)
                },
            )
        }
    }
}
