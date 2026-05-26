package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Announcement
import cc.hhhl.client.state.AnnouncementUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun AnnouncementScreen(
    state: AnnouncementUiState? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenAnnouncement: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onMarkRead: (String) -> Unit = {},
) {
    val announcements = state?.announcements ?: fakeAnnouncements()
    val selectedAnnouncement = state?.selectedAnnouncement
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = announcements.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (selectedAnnouncement != null) {
        AnnouncementDetailView(
            announcement = selectedAnnouncement,
            isLoading = state.isLoadingDetail,
            errorMessage = state.detailErrorMessage,
            actionErrorMessage = state.actionErrorMessage,
            isPending = state.pendingAnnouncementIds.contains(selectedAnnouncement.id),
            onBack = onCloseDetail,
            onMarkRead = onMarkRead,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "公告",
            supportingText = when {
                state?.isLoading == true -> "同步中"
                announcements.isEmpty() -> "站点消息"
                announcements.any { !it.isRead } -> "${announcements.count { !it.isRead }} 条未读 / ${announcements.size} 条"
                else -> "${announcements.size} 条公告"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        AnnouncementSummaryRow(
            announcementCount = announcements.size,
            unreadCount = announcements.count { !it.isRead },
            isLoading = state?.isLoading == true,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item {
                    AnnouncementStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state?.isLoading == true && announcements.isEmpty()) {
                item { AnnouncementStatusRow(text = "正在加载公告...", loading = true) }
            }
            if (state != null && !state.isLoading && announcements.isEmpty() && state.errorMessage == null) {
                item { AnnouncementStatusRow(text = "还没有公告") }
            }
            items(announcements, key = { it.id }) { announcement ->
                AnnouncementRow(
                    announcement = announcement,
                    onOpenAnnouncement = onOpenAnnouncement,
                )
            }
            if (state != null && announcements.isNotEmpty() && !state.endReached) {
                item {
                    AnnouncementStatusRow(
                        text = if (state.isLoadingMore) "正在加载更多..." else "加载更多",
                        loading = state.isLoadingMore,
                        onAction = if (state.isLoadingMore) null else onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementSummaryRow(
    announcementCount: Int,
    unreadCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val countText = if (announcementCount == 0) "暂无公告" else "${announcementCount} 条公告"
    val stateText = when {
        isLoading -> "加载中"
        unreadCount > 0 -> "${unreadCount} 条未读"
        else -> "全部已读"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$countText · $stateText",
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
                label = if (isLoading) "同步中" else "刷新公告",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun AnnouncementRow(
    announcement: Announcement,
    onOpenAnnouncement: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAnnouncement(announcement.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = announcementIcon(announcement.icon),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = announcement.title.ifBlank { "公告" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!announcement.isRead) {
                    Text(
                        text = "未读",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = announcement.text,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${announcement.displayLabel} · ${announcement.createdAtLabel}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HhhlDivider()
}

@Composable
private fun AnnouncementDetailView(
    announcement: Announcement,
    isLoading: Boolean,
    errorMessage: String?,
    actionErrorMessage: String?,
    isPending: Boolean,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "公告",
            supportingText = listOf(
                announcement.displayLabel,
                announcement.createdAtLabel,
            ).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                if (!announcement.isRead) {
                    HhhlActionChip(
                        label = if (isPending) "处理中" else "标记已读",
                        onClick = { onMarkRead(announcement.id) },
                        enabled = !isPending,
                        emphasized = true,
                    )
                }
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item { AnnouncementStatusRow(text = "正在加载公告...", loading = true) }
            }
            errorMessage?.let { message ->
                item { AnnouncementStatusRow(text = message) }
            }
            actionErrorMessage?.let { message ->
                item { AnnouncementStatusRow(text = message) }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = announcement.title.ifBlank { "公告" },
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${announcement.iconLabel} · ${announcement.displayLabel} · ${announcement.createdAtLabel}",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = announcement.text,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    announcement.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        Text(
                            text = imageUrl,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HhhlDivider()
            }
        }
    }
}

@Composable
private fun AnnouncementStatusRow(
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

private fun announcementIcon(icon: String): String {
    return when (icon) {
        "warning" -> "!"
        "error" -> "×"
        "success" -> "✓"
        else -> "i"
    }
}

private fun fakeAnnouncements(): List<Announcement> {
    return listOf(
        Announcement(
            id = "ann-featured",
            title = "HHHL 公告",
            text = "站内公告会显示在这里",
            imageUrl = null,
            icon = "info",
            display = "normal",
            needConfirmationToRead = false,
            silence = false,
            confetti = false,
            forYou = true,
            isRead = false,
        ),
    )
}
