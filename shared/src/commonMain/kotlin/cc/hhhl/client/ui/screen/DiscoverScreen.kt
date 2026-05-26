package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOrigin
import cc.hhhl.client.state.DiscoverSearchMode
import cc.hhhl.client.state.DiscoverUiState
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun DiscoverScreen(
    state: DiscoverUiState? = null,
    onQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onModeSelected: (DiscoverSearchMode) -> Unit = {},
    onFiltersChanged: (DiscoverAdvancedFilters) -> Unit = {},
    onClearFilters: () -> Unit = {},
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
    onOpenChannels: () -> Unit = {},
    onOpenPages: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenFlash: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    val query = state?.query.orEmpty()
    val selectedMode = state?.selectedMode ?: DiscoverSearchMode.Notes
    var advancedFiltersExpanded by remember(selectedMode) { mutableStateOf(false) }
    val visibleModes = discoverVisibleModes(
        canSearchNotes = state?.canSearchNotes != false,
        canTrend = state?.canTrend == true,
        canViewFederation = state?.canViewFederation == true,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        DiscoverQuickActionRow(
            onOpenChannels = onOpenChannels,
            onOpenPages = onOpenPages,
            onOpenGallery = onOpenGallery,
            onOpenFlash = onOpenFlash,
            onOpenAnnouncements = onOpenAnnouncements,
        )
        HhhlDivider()
        DiscoverModeSelector(
            selectedMode = selectedMode,
            visibleModes = visibleModes,
            onModeSelected = onModeSelected,
        )
        HhhlDivider()
        if (selectedMode != DiscoverSearchMode.Trends && selectedMode != DiscoverSearchMode.Federation) {
            DiscoverSearchRow(
                query = query,
                selectedMode = selectedMode,
                isSearching = state?.isSearching == true,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
            )
            HhhlDivider()
        }
        if (selectedMode != DiscoverSearchMode.Trends) {
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
        DiscoverSummaryRow(state = state)
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state == null) {
                item { DiscoverStatusRow(text = "暂无结果") }
            } else {
                if (
                    state.isSearching &&
                    state.notes.isEmpty() &&
                    state.users.isEmpty() &&
                    state.trends.isEmpty() &&
                    state.federationInstances.isEmpty()
                ) {
                    item {
                        DiscoverStatusRow(
                            text = "加载中...",
                            loading = true,
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    item {
                        DiscoverStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = onSearch,
                        )
                    }
                }
                if (
                    state.hasSearched &&
                    !state.isSearching &&
                    state.notes.isEmpty() &&
                    state.users.isEmpty() &&
                    state.trends.isEmpty() &&
                    state.federationInstances.isEmpty() &&
                    state.errorMessage == null
                ) {
                    item {
                        DiscoverStatusRow(
                            text = when (state.selectedMode) {
                                DiscoverSearchMode.Trends -> "暂无趋势"
                                DiscoverSearchMode.Federation -> "暂无联邦实例"
                                else -> "没有搜索结果"
                            },
                        )
                    }
                }
                items(state.trends, key = { "trend-${it.tag}" }) { trend ->
                    TrendRow(
                        trend = trend,
                        onOpenHashtag = onOpenHashtag,
                    )
                }
                items(state.federationInstances, key = { "federation-${it.id}" }) { instance ->
                    FederationInstanceRow(instance = instance)
                }
                items(state.users, key = { "discover-user-${it.id}" }) { user ->
                    DiscoverUserRow(
                        user = user,
                        onOpenUser = onOpenUser,
                    )
                }
                items(state.notes, key = { "discover-${it.id}" }) { note ->
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
                val canLoadMore = !state.endReached && (
                    (state.selectedMode == DiscoverSearchMode.Notes && state.notes.isNotEmpty()) ||
                        (
                            state.selectedMode == DiscoverSearchMode.Federation &&
                                state.federationInstances.isNotEmpty()
                            )
                )
                if (canLoadMore) {
                    item {
                        DiscoverStatusRow(
                            text = if (state.isLoadingMore) "正在加载更多..." else "加载更多",
                            loading = state.isLoadingMore,
                            onAction = if (state.isLoadingMore) null else onLoadMore,
                        )
                    }
                } else if (state.hasSearched && state.endReached && state.resultCount > 0) {
                    item {
                        DiscoverStatusRow(text = discoverEndReachedText(state))
                    }
                }
            }
        }
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
            HhhlActionChip(label = "频道", emphasized = true, onClick = onOpenChannels)
            HhhlOverflowMenu(
                actions = discoverSecondaryQuickActions(
                    onOpenPages = onOpenPages,
                    onOpenGallery = onOpenGallery,
                    onOpenFlash = onOpenFlash,
                    onOpenAnnouncements = onOpenAnnouncements,
                ),
                label = "更多探索入口",
            )
        }
    }
}

@Composable
private fun DiscoverModeSelector(
    selectedMode: DiscoverSearchMode,
    visibleModes: List<DiscoverSearchMode>,
    onModeSelected: (DiscoverSearchMode) -> Unit,
) {
    val primaryModes = discoverPrimarySearchModes(visibleModes, selectedMode)
    val overflowActions = discoverOverflowSearchModeActions(
        visibleModes = visibleModes,
        primaryModes = primaryModes,
        selectedMode = selectedMode,
        onModeSelected = onModeSelected,
    )
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
            primaryModes.forEach { mode ->
                HhhlActionChip(
                    label = mode.label,
                    emphasized = mode == selectedMode,
                    enabled = mode != selectedMode,
                    onClick = { onModeSelected(mode) },
                )
            }
            if (overflowActions.isNotEmpty()) {
                HhhlOverflowMenu(
                    actions = overflowActions,
                    label = "更多搜索范围",
                )
            }
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
            if (selectedMode == DiscoverSearchMode.Users) {
                DiscoverSearchOrigin.entries.forEach { origin ->
                    HhhlActionChip(
                        label = discoverOriginFilterLabel(origin),
                        emphasized = filters.origin == origin,
                        enabled = filters.origin != origin,
                        onClick = { onFiltersChanged(filters.copy(origin = origin)) },
                    )
                }
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HhhlActionChip(label = "应用筛选", emphasized = true, onClick = onApply)
                HhhlActionChip(label = "清除", onClick = onClearFilters)
            }
        } else if (selectedMode == DiscoverSearchMode.Federation) {
            HhhlTextInput(
                value = filters.domain,
                onValueChange = { onFiltersChanged(filters.copy(domain = it)) },
                placeholder = "域名",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                    tint = MaterialTheme.colorScheme.secondary,
                )
            },
        )
        HhhlActionChip(
            label = discoverSearchActionLabel(isSearching),
            emphasized = true,
            enabled = !isSearching,
            onClick = onSearch,
        )
    }
}

@Composable
private fun DiscoverSummaryRow(state: DiscoverUiState?) {
    val selectedMode = state?.selectedMode ?: DiscoverSearchMode.Notes
    val primaryText = when (selectedMode) {
        DiscoverSearchMode.Notes -> state?.query?.takeIf { it.isNotBlank() } ?: "帖子"
        DiscoverSearchMode.Users -> state?.query?.takeIf { it.isNotBlank() } ?: "用户"
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
            color = MaterialTheme.colorScheme.onBackground,
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
        DiscoverSearchMode.Trends -> trends.size
        DiscoverSearchMode.Federation -> federationInstances.size
    }

fun discoverEndReachedText(state: DiscoverUiState): String {
    return when (state.selectedMode) {
        DiscoverSearchMode.Notes -> "已显示全部 ${state.notes.size} 条帖子"
        DiscoverSearchMode.Users -> "已显示全部 ${state.users.size} 个用户"
        DiscoverSearchMode.Trends -> "已显示全部 ${state.trends.size} 个趋势"
        DiscoverSearchMode.Federation -> "已显示全部 ${state.federationInstances.size} 个实例"
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
        filters.domain,
        filters.sinceDate,
        filters.untilDate,
    ).count { it.isNotBlank() }
}

fun discoverQuickActionLabels(): List<String> = listOf("频道", "页面", "图库", "Play", "公告")

fun discoverPrimaryQuickActionLabels(): List<String> = listOf("频道")

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

fun discoverOriginFilterLabel(origin: DiscoverSearchOrigin): String {
    return when (origin) {
        DiscoverSearchOrigin.Combined -> "全部来源"
        DiscoverSearchOrigin.Local -> "本地"
        DiscoverSearchOrigin.Remote -> "远程"
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
        HhhlOverflowMenuAction("页面", onClick = onOpenPages),
        HhhlOverflowMenuAction("图库", onClick = onOpenGallery),
        HhhlOverflowMenuAction("Play", onClick = onOpenFlash),
        HhhlOverflowMenuAction("公告", onClick = onOpenAnnouncements),
    )
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
            DiscoverSearchMode.Trends -> canTrend
            DiscoverSearchMode.Federation -> canViewFederation
            DiscoverSearchMode.Users -> true
        }
    }
}

@Composable
private fun FederationInstanceRow(
    instance: cc.hhhl.client.model.FederationInstance,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name?.takeIf { it.isNotBlank() } ?: instance.host,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${instance.host} · ${instance.softwareName.orEmpty()} ${instance.softwareVersion.orEmpty()}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${instance.usersCount} 用户 · ${instance.notesCount} 帖子",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val status = when {
            instance.isBlocked -> "已封锁"
            instance.isSuspended -> "已暂停"
            instance.isNotResponding -> "无响应"
            instance.isSilenced -> "已静音"
            else -> "联邦中"
        }
        Text(
            text = status,
            color = if (status == "联邦中") {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
    HhhlDivider()
}

@Composable
private fun TrendRow(
    trend: cc.hhhl.client.model.TrendingHashtag,
    onOpenHashtag: (String) -> Unit,
) {
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
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${trend.usersCount} 人正在使用",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = trend.chart.takeLast(4).joinToString(" "),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    HhhlDivider()
}

@Composable
private fun DiscoverStatusRow(
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

@Composable
private fun DiscoverUserRow(
    user: cc.hhhl.client.model.User,
    onOpenUser: (String) -> Unit,
) {
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
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = user.discoverHandle,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (user.bio.isNotBlank()) {
                Text(
                    text = user.bio,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
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
            selectedMode == DiscoverSearchMode.Users &&
            filters.origin != DiscoverSearchOrigin.Combined
        ) {
            add(filters.origin.label)
        }
        filters.username.takeIf { it.isNotBlank() }?.let { add("@${it.trim().removePrefix("@")}") }
        filters.domain.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        filters.sinceDate.takeIf { it.isNotBlank() }?.let { add("自 $it") }
        filters.untilDate.takeIf { it.isNotBlank() }?.let { add("至 $it") }
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
            selectedMode == DiscoverSearchMode.Users &&
            filters.origin != DiscoverSearchOrigin.Combined
        ) {
            add(
                DiscoverFilterChip(filters.origin.label) {
                    it.copy(origin = DiscoverSearchOrigin.Combined)
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
        filters.domain.takeIf { it.isNotBlank() }?.let { domain ->
            add(
                DiscoverFilterChip(domain.trim()) {
                    it.copy(domain = "")
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
    }
}
