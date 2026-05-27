package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
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
import cc.hhhl.client.model.UserRelationshipListEntry
import cc.hhhl.client.state.RelationshipManagementTab
import cc.hhhl.client.state.RelationshipManagementUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlSegmentedControl
import cc.hhhl.client.ui.component.HhhlSegmentedItem
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun RelationshipManagementScreen(
    state: RelationshipManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onTabSelected: (RelationshipManagementTab) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onRemoveRelationship: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val entries = state.visibleEntries

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = entries.size,
        isLoadingMore = state.isLoadingMore || state.currentEndReached,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "关系管理",
            supportingText = relationshipManagementStatusText(state),
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlOverflowMenu(
                    actions = relationshipManagementTopActions(
                        isLoading = state.isLoading,
                        onRefresh = onRefresh,
                    ),
                )
            },
        )
        HhhlDivider()
        RelationshipManagementTabs(
            selectedTab = state.selectedTab,
            mutedCount = state.mutedUsers.size,
            blockedCount = state.blockedUsers.size,
            onTabSelected = onTabSelected,
        )
        HhhlDivider()
        RelationshipManagementSummaryRow(
            selectedTab = state.selectedTab,
            count = entries.size,
            isLoading = state.isLoading,
            isMutating = state.isMutating,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state.message?.let { message ->
                item(contentType = "relationship-status") { RelationshipManagementStatusRow(text = message) }
            }
            state.errorMessage?.let { message ->
                item(contentType = "relationship-status") {
                    RelationshipManagementStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state.isLoading && entries.isEmpty()) {
                item(contentType = "relationship-status") {
                    RelationshipManagementStatusRow(text = "加载中...", loading = true)
                }
            }
            if (!state.isLoading && entries.isEmpty() && state.errorMessage == null) {
                item(contentType = "relationship-status") {
                    RelationshipManagementStatusRow(text = relationshipManagementEmptyText(state.selectedTab))
                }
            }
            items(
                items = entries,
                key = { it.id },
                contentType = { "relationship-row" },
            ) { entry ->
                RelationshipManagementRow(
                    entry = entry,
                    selectedTab = state.selectedTab,
                    isMutating = state.isMutating,
                    onOpenUser = onOpenUser,
                    onRemoveRelationship = onRemoveRelationship,
                )
            }
            if (entries.isNotEmpty() && state.isLoadingMore) {
                item(contentType = "relationship-status") {
                    RelationshipManagementStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelationshipManagementTabs(
    selectedTab: RelationshipManagementTab,
    mutedCount: Int,
    blockedCount: Int,
    onTabSelected: (RelationshipManagementTab) -> Unit,
) {
    HhhlSegmentedControl(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        HhhlSegmentedItem(
            label = relationshipManagementTabLabel(RelationshipManagementTab.Muted, mutedCount),
            selected = selectedTab == RelationshipManagementTab.Muted,
            onClick = { onTabSelected(RelationshipManagementTab.Muted) },
            modifier = Modifier.weight(1f),
        )
        HhhlSegmentedItem(
            label = relationshipManagementTabLabel(RelationshipManagementTab.Blocked, blockedCount),
            selected = selectedTab == RelationshipManagementTab.Blocked,
            onClick = { onTabSelected(RelationshipManagementTab.Blocked) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RelationshipManagementSummaryRow(
    selectedTab: RelationshipManagementTab,
    count: Int,
    isLoading: Boolean,
    isMutating: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = selectedTab.label,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = relationshipManagementSummaryText(selectedTab, count, isLoading, isMutating),
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HhhlActionChip(
            label = when {
                isLoading -> "同步中"
                isMutating -> "处理中"
                else -> "刷新"
            },
            emphasized = true,
            enabled = !isLoading && !isMutating,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun RelationshipManagementRow(
    entry: UserRelationshipListEntry,
    selectedTab: RelationshipManagementTab,
    isMutating: Boolean,
    onOpenUser: (String) -> Unit,
    onRemoveRelationship: (String) -> Unit,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(entry.user.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = entry.user.avatarInitial,
            avatarUrl = entry.user.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.user.displayName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relationshipManagementUserMeta(entry),
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HhhlOverflowMenu(
            enabled = !isMutating,
            actions = relationshipManagementRowActions(
                tab = selectedTab,
                onOpenUser = { onOpenUser(entry.user.id) },
                onRemove = { confirmOpen = true },
            ),
        )
    }
    HhhlDivider()

    if (confirmOpen) {
        RelationshipManagementRemoveDialog(
            tab = selectedTab,
            displayName = entry.user.displayName,
            isMutating = isMutating,
            onConfirm = {
                confirmOpen = false
                onRemoveRelationship(entry.user.id)
            },
            onDismiss = { confirmOpen = false },
        )
    }
}

@Composable
private fun RelationshipManagementRemoveDialog(
    tab: RelationshipManagementTab,
    displayName: String,
    isMutating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(relationshipManagementRemoveLabel(tab)) },
        text = {
            Text(
                text = "将「${displayName.ifBlank { "该用户" }}」从${tab.label}列表移除。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onConfirm, enabled = !isMutating) {
                Text(if (isMutating) "处理中" else relationshipManagementRemoveLabel(tab))
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

fun relationshipManagementStatusText(state: RelationshipManagementUiState): String {
    return when {
        state.isLoading -> "同步中"
        state.isMutating -> "处理中"
        else -> "${state.selectedTab.label} · ${state.visibleEntries.size} 人"
    }
}

fun relationshipManagementTabLabel(tab: RelationshipManagementTab, count: Int): String {
    return if (count > 0) "${tab.label} $count" else tab.label
}

fun relationshipManagementSummaryText(
    tab: RelationshipManagementTab,
    count: Int,
    isLoading: Boolean,
    isMutating: Boolean,
): String {
    return when {
        isLoading -> "同步中"
        isMutating -> "处理中"
        count > 0 -> "$count 位用户"
        else -> relationshipManagementEmptyText(tab)
    }
}

fun relationshipManagementEmptyText(tab: RelationshipManagementTab): String {
    return when (tab) {
        RelationshipManagementTab.Muted -> "暂无静音用户"
        RelationshipManagementTab.Blocked -> "暂无拉黑用户"
    }
}

fun relationshipManagementRemoveLabel(tab: RelationshipManagementTab): String {
    return when (tab) {
        RelationshipManagementTab.Muted -> "取消静音"
        RelationshipManagementTab.Blocked -> "取消拉黑"
    }
}

fun relationshipManagementUserMeta(entry: UserRelationshipListEntry): String {
    return listOf(
        "@${entry.user.username}",
        entry.createdAtLabel.takeIf { it.isNotBlank() }?.let { "加入 $it" },
    ).filterNotNull().joinToString(" · ")
}

fun relationshipManagementTopActions(
    isLoading: Boolean,
    onRefresh: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return listOf(
        HhhlOverflowMenuAction(
            label = if (isLoading) "同步中" else "刷新列表",
            enabled = !isLoading,
            onClick = onRefresh,
        ),
    )
}

fun relationshipManagementRowActions(
    tab: RelationshipManagementTab,
    onOpenUser: () -> Unit,
    onRemove: () -> Unit,
): List<HhhlOverflowMenuAction> {
    return listOf(
        HhhlOverflowMenuAction("查看资料", onClick = onOpenUser),
        HhhlOverflowMenuAction(
            label = relationshipManagementRemoveLabel(tab),
            destructive = tab == RelationshipManagementTab.Blocked,
            onClick = onRemove,
        ),
    )
}

@Composable
private fun RelationshipManagementStatusRow(
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
