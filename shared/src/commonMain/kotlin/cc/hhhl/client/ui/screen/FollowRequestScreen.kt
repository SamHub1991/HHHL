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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.FollowRequest
import cc.hhhl.client.state.FollowRequestUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun FollowRequestScreen(
    state: FollowRequestUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onAccept: (String) -> Unit = {},
    onReject: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = state.requests.size,
        isLoadingMore = state.isLoadingMore || state.endReached,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "关注请求",
            supportingText = when {
                state.isLoading -> "同步中"
                state.requests.isEmpty() && state.sentRequests.isEmpty() -> "待处理关注"
                else -> "收到 ${state.requests.size} · 发出 ${state.sentRequests.size}"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        FollowRequestSummaryRow(
            requestCount = state.requests.size,
            sentCount = state.sentRequests.size,
            pendingCount = state.pendingUserIds.size,
            isLoading = state.isLoading || state.isLoadingSent,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state.errorMessage?.let { message ->
                item(key = "follow-request-error", contentType = "follow-request-status") {
                    FollowRequestStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            state.actionErrorMessage?.let { message ->
                item(key = "follow-request-action-error", contentType = "follow-request-status") {
                    FollowRequestStatusRow(text = message)
                }
            }
            if (state.isLoading && state.requests.isEmpty()) {
                item(key = "follow-request-loading", contentType = "follow-request-status") {
                    FollowRequestStatusRow(text = "正在加载关注请求...", loading = true)
                }
            }
            if (!state.isLoading && state.requests.isEmpty() && state.errorMessage == null) {
                item(key = "follow-request-empty", contentType = "follow-request-status") {
                    FollowRequestStatusRow(text = "暂无收到的关注请求")
                }
            }
            if (state.requests.isNotEmpty()) {
                item(key = "follow-request-received-title", contentType = "follow-request-title") {
                    FollowRequestSectionTitle(title = "收到", count = state.requests.size)
                }
            }
            items(
                items = state.requests,
                key = { it.id },
                contentType = { "follow-request-row" },
            ) { request ->
                FollowRequestRow(
                    request = request,
                    isPending = state.pendingUserIds.contains(request.user.id),
                    onAccept = onAccept,
                    onReject = onReject,
                    onOpenUser = onOpenUser,
                )
            }
            if (state.isLoadingSent && state.sentRequests.isEmpty()) {
                item(key = "follow-request-sent-loading", contentType = "follow-request-status") {
                    FollowRequestStatusRow(text = "正在加载发出的请求...", loading = true)
                }
            }
            if (state.sentRequests.isNotEmpty()) {
                item(key = "follow-request-sent-title", contentType = "follow-request-title") {
                    FollowRequestSectionTitle(title = "发出", count = state.sentRequests.size)
                }
            }
            items(
                items = state.sentRequests,
                key = { "sent-${it.id}" },
                contentType = { "follow-request-sent-row" },
            ) { request ->
                FollowRequestSentRow(
                    request = request,
                    isPending = state.pendingUserIds.contains(request.user.id),
                    onCancel = onCancel,
                    onOpenUser = onOpenUser,
                )
            }
            if (state.requests.isNotEmpty() && state.isLoadingMore) {
                item(key = "follow-request-loading-more", contentType = "follow-request-status") {
                    FollowRequestStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowRequestSentRow(
    request: FollowRequest,
    isPending: Boolean,
    onCancel: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(request.user.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initial = request.user.avatarInitial, avatarUrl = request.user.avatarUrl)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = request.user.displayName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${request.user.username}",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlActionChip(
                label = if (isPending) "处理中" else "取消请求",
                enabled = !isPending,
                onClick = { onCancel(request.user.id) },
            )
        }
    }
    HhhlDivider()
}

@Composable
private fun FollowRequestRow(
    request: FollowRequest,
    isPending: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(request.user.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = request.user.avatarInitial,
            avatarUrl = request.user.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = request.user.displayName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "@${request.user.username}",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlActionChip(
                    label = if (isPending) "处理中" else "接受",
                    emphasized = true,
                    enabled = !isPending,
                    onClick = { onAccept(request.user.id) },
                )
                HhhlOverflowMenu(
                    enabled = !isPending,
                    actions = followRequestActions(
                        isPending = isPending,
                        userId = request.user.id,
                        onReject = onReject,
                    ),
                )
            }
        }
    }
    HhhlDivider()
}

fun followRequestActions(
    isPending: Boolean,
    userId: String,
    onReject: (String) -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "拒绝",
        enabled = !isPending,
        destructive = true,
        onClick = { onReject(userId) },
    ),
)

@Composable
private fun FollowRequestSummaryRow(
    requestCount: Int,
    sentCount: Int,
    pendingCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val countText = "收到 $requestCount · 发出 $sentCount"
    val stateText = when {
        isLoading -> "加载中"
        pendingCount > 0 -> "${pendingCount} 项处理中"
        else -> "可快速处理"
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
                contentDescription = if (isLoading) "同步请求中" else "刷新请求",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun FollowRequestSectionTitle(
    title: String,
    count: Int,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FollowRequestStatusRow(
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
