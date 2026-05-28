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
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
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
import cc.hhhl.client.model.User
import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind
import cc.hhhl.client.state.UserSocialUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText

@Composable
fun UserSocialScreen(
    state: UserSocialUiState? = null,
    kind: UserSocialKind = state?.kind ?: UserSocialKind.Following,
    displayName: String? = state?.displayName,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onKindSelected: (UserSocialKind) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onUnfollowUser: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onBlockUser: ((String) -> Unit)? = null,
    onReportUser: ((String) -> Unit)? = null,
    isSpecialCareUser: (String) -> Boolean = { false },
    onToggleSpecialCareUser: ((String) -> Unit)? = null,
) {
    val items = state?.items.orEmpty()
    val title = when {
        displayName.isNullOrBlank() -> kind.label
        else -> "${displayName}的${kind.label}"
    }
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = items.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = title,
            supportingText = when {
                state?.isLoading == true -> "同步中"
                items.isEmpty() -> kind.label
                else -> "${items.size} 位用户"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        UserSocialSummaryRow(
            kind = kind,
            userCount = items.size,
            isLoading = state?.isLoading == true,
            isChanging = state?.isRelationshipChanging == true,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        UserSocialKindRow(
            selectedKind = kind,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            if (state?.isLoading == true && items.isEmpty()) {
                item(key = "user-social-loading-${kind.name}", contentType = "user-social-status") {
                    UserSocialStatusRow(text = "加载中...", loading = true)
                }
            }
            state?.message?.let { message ->
                item(key = "user-social-message-${kind.name}", contentType = "user-social-status") {
                    UserSocialStatusRow(text = message)
                }
            }
            state?.errorMessage?.let { message ->
                item(key = "user-social-error-${kind.name}", contentType = "user-social-status") {
                    UserSocialStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state != null && !state.isLoading && items.isEmpty() && state.errorMessage == null) {
                item(key = "user-social-empty-${kind.name}", contentType = "user-social-status") {
                    UserSocialStatusRow(text = "这里还没有用户")
                }
            }
            items(
                items = items,
                key = { "${kind.name}-${it.id}" },
                contentType = { "user-social-row" },
            ) { item ->
                UserSocialRow(
                    kind = kind,
                    user = item.user,
                    isChanging = state?.isRelationshipChanging == true,
                    onOpenUser = onOpenUser,
                    onUnfollowUser = onUnfollowUser,
                    onMuteUser = onMuteUser,
                    onBlockUser = onBlockUser,
                    onReportUser = onReportUser,
                    isSpecialCareUser = isSpecialCareUser,
                    onToggleSpecialCareUser = onToggleSpecialCareUser,
                )
            }
            if (state != null && items.isNotEmpty() && state.isLoadingMore) {
                item(key = "user-social-loading-more-${kind.name}", contentType = "user-social-status") {
                    UserSocialStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSocialSummaryRow(
    kind: UserSocialKind,
    userCount: Int,
    isLoading: Boolean,
    isChanging: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val stateText = when {
        isLoading -> "加载中"
        isChanging -> "处理中"
        userCount == 0 -> "暂无用户"
        else -> "${userCount} 位用户"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${kind.label} · $stateText",
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
                contentDescription = if (isLoading) "同步中" else "刷新",
                emphasized = true,
                enabled = !isLoading && !isChanging,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun UserSocialKindRow(
    selectedKind: UserSocialKind,
    onKindSelected: (UserSocialKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        userSocialKinds().forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = kind == selectedKind,
                enabled = kind != selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
    }
}

fun userSocialKinds(): List<UserSocialKind> = UserSocialKind.entries.toList()

@Composable
private fun UserSocialRow(
    kind: UserSocialKind,
    user: User,
    isChanging: Boolean,
    onOpenUser: (String) -> Unit,
    onUnfollowUser: ((String) -> Unit)?,
    onMuteUser: ((String) -> Unit)?,
    onBlockUser: ((String) -> Unit)?,
    onReportUser: ((String) -> Unit)?,
    isSpecialCareUser: (String) -> Boolean,
    onToggleSpecialCareUser: ((String) -> Unit)?,
) {
    var pendingAction by remember(user.id) { mutableStateOf<UserSocialPendingAction?>(null) }
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "  @${user.username}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (user.bio.isNotBlank()) {
                InlineRichText(
                    text = user.bio,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${user.followingCount} 关注  ${user.followersCount} 关注者",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HhhlOverflowMenu(
            enabled = !isChanging,
            actions = userSocialRowActions(
                kind = kind,
                onOpenUser = { onOpenUser(user.id) },
                onUnfollow = onUnfollowUser?.let {
                    { pendingAction = UserSocialPendingAction.Unfollow }
                },
                onMute = onMuteUser?.let {
                    { pendingAction = UserSocialPendingAction.Mute }
                },
                onBlock = onBlockUser?.let {
                    { pendingAction = UserSocialPendingAction.Block }
                },
                onReport = onReportUser?.let {
                    { pendingAction = UserSocialPendingAction.Report }
                },
                isSpecialCare = isSpecialCareUser(user.id),
                onToggleSpecialCare = onToggleSpecialCareUser?.let {
                    { it(user.id) }
                },
            ),
        )
    }
    HhhlDivider()

    pendingAction?.let { action ->
        UserSocialActionDialog(
            action = action,
            displayName = user.displayName,
            isChanging = isChanging,
            onConfirm = {
                pendingAction = null
                when (action) {
                    UserSocialPendingAction.Unfollow -> onUnfollowUser?.invoke(user.id)
                    UserSocialPendingAction.Mute -> onMuteUser?.invoke(user.id)
                    UserSocialPendingAction.Block -> onBlockUser?.invoke(user.id)
                    UserSocialPendingAction.Report -> onReportUser?.invoke(user.id)
                }
            },
            onDismiss = { pendingAction = null },
        )
    }
}

private enum class UserSocialPendingAction(
    val label: String,
    val description: String,
) {
    Unfollow("取消关注", "将「%s」从关注列表移除。"),
    Mute("静音", "静音「%s」后，将减少看到对方内容。"),
    Block("屏蔽", "屏蔽「%s」后，你将不再看到对方的帖子和群聊消息。"),
    Report("举报", "将「%s」提交给实例管理员处理。"),
}

@Composable
private fun UserSocialActionDialog(
    action: UserSocialPendingAction,
    displayName: String,
    isChanging: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = displayName.ifBlank { "该用户" }
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.label) },
        text = {
            Text(
                text = action.description.replace("%s", name),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onConfirm, enabled = !isChanging) {
                Text(if (isChanging) "处理中" else action.label)
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isChanging) {
                Text("取消")
            }
        },
    )
}

fun userSocialRowActions(
    kind: UserSocialKind,
    onOpenUser: () -> Unit,
    onUnfollow: (() -> Unit)?,
    onMute: (() -> Unit)?,
    onBlock: (() -> Unit)?,
    onReport: (() -> Unit)?,
    isSpecialCare: Boolean = false,
    onToggleSpecialCare: (() -> Unit)? = null,
): List<HhhlOverflowMenuAction> {
    return buildList {
        add(HhhlOverflowMenuAction("查看资料", onClick = onOpenUser))
        onToggleSpecialCare?.let {
            add(HhhlOverflowMenuAction(userSocialSpecialCareActionLabel(isSpecialCare), onClick = it))
        }
        if (kind == UserSocialKind.Following && onUnfollow != null) {
            add(HhhlOverflowMenuAction("取消关注", onClick = onUnfollow))
        }
        onMute?.let { add(HhhlOverflowMenuAction("静音", onClick = it)) }
        onBlock?.let { add(HhhlOverflowMenuAction("屏蔽", destructive = true, onClick = it)) }
        onReport?.let { add(HhhlOverflowMenuAction("举报", destructive = true, onClick = it)) }
    }
}

fun userSocialSpecialCareActionLabel(isSpecialCare: Boolean): String {
    return if (isSpecialCare) "取消特别关心" else "设为特别关心"
}

@Composable
private fun UserSocialStatusRow(
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
