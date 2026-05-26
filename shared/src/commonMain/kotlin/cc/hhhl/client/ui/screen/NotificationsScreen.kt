package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.state.NotificationUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.notificationLineText

@Composable
fun NotificationsScreen(
    state: NotificationUiState? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    onFilterSelected: (NotificationFilter) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    pendingFollowRequestUserIds: Set<String> = emptySet(),
    onAcceptFollowRequest: (String) -> Unit = {},
    onRejectFollowRequest: (String) -> Unit = {},
    onOpenChat: () -> Unit = {},
) {
    val notifications = state?.notifications ?: FakeData.notifications
    val selectedFilter = state?.selectedFilter ?: NotificationFilter.All

    Column(modifier = Modifier.fillMaxSize()) {
        NotificationPrimaryFilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
        )
        HhhlDivider()
        NotificationSummaryRow(
            selectedFilter = selectedFilter,
            notificationCount = notifications.size,
            unreadCount = state?.unreadCount ?: notifications.size,
            isLoading = state?.isLoading == true,
            isMarkingAllRead = state?.isMarkingAllRead == true,
            onRefresh = onRefresh,
            onMarkAllAsRead = onMarkAllAsRead,
        )
        HhhlDivider()
        LazyColumn {
            state?.message?.let { message ->
                item { NotificationStatusRow(text = message) }
            }
            if (state?.isLoading == true && notifications.isEmpty()) {
                item { NotificationStatusRow(text = "正在加载通知...", loading = true) }
            }
            state?.errorMessage?.let { message ->
                item {
                    NotificationStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state != null && !state.isLoading && notifications.isEmpty() && state.errorMessage == null) {
                item { NotificationStatusRow(text = "暂时没有通知") }
            }
            items(notifications, key = { it.id }) { notification ->
                val rowClick = {
                    when (val target = notification.navigationTarget) {
                        is NotificationNavigationTarget.NoteDetail -> onOpenNote(target.noteId)
                        is NotificationNavigationTarget.UserProfile -> onOpenUser(target.userId)
                        NotificationNavigationTarget.Chat -> onOpenChat()
                        null -> Unit
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rowClick() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NotificationTypeBadge(type = notification.type)
                    Avatar(
                        initial = notification.actor.avatarInitial,
                        avatarUrl = notification.actor.avatarUrl,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        InlineRichText(
                            text = notificationLineText(notification),
                            style = MaterialTheme.typography.bodyMedium,
                            onOpenUrl = onOpenUrl,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                        )
                        if (notification.hasNotePreview) {
                            InlineRichText(
                                text = notification.notePreviewText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalHhhlColors.current.subtleText,
                                onOpenUrl = onOpenUrl,
                                onOpenMention = onOpenMention,
                                onOpenHashtag = onOpenHashtag,
                            )
                        }
                        Text(
                            notification.createdAtLabel,
                            color = LocalHhhlColors.current.subtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (notification.canActOnFollowRequest) {
                            val actorId = notification.actor.id
                            val pending = actorId in pendingFollowRequestUserIds
                            NotificationActionRow(
                                primaryLabel = if (pending) "处理中" else "接受",
                                primaryEnabled = !pending,
                                onPrimaryClick = { onAcceptFollowRequest(actorId) },
                                secondaryLabel = "拒绝",
                                secondaryEnabled = !pending,
                                onSecondaryClick = { onRejectFollowRequest(actorId) },
                            )
                        } else if (notification.canOpenChat) {
                            Text(
                                text = "点按进入聊天",
                                color = LocalHhhlColors.current.subtleText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                HhhlDivider()
            }
            if (state != null && notifications.isNotEmpty() && !state.endReached) {
                item {
                    NotificationStatusRow(
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
private fun NotificationPrimaryFilterRow(
    selectedFilter: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notificationVisiblePrimaryFilters().forEach { filter ->
            NotificationFilterChip(
                filter = filter,
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun NotificationSummaryRow(
    selectedFilter: NotificationFilter,
    notificationCount: Int,
    unreadCount: Int,
    isLoading: Boolean,
    isMarkingAllRead: Boolean,
    onRefresh: () -> Unit,
    onMarkAllAsRead: () -> Unit,
) {
    val stateText = when {
        isLoading -> "加载中"
        unreadCount > 0 -> "${unreadCount} 条未读 / ${notificationCount} 条"
        else -> "${notificationCount} 条通知"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${selectedFilter.label} · $stateText",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhhlActionChip(
                label = if (isLoading) "同步中" else "刷新",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
            HhhlActionChip(
                label = if (isMarkingAllRead) "处理中" else "全部已读",
                enabled = !isMarkingAllRead && notificationCount > 0,
                onClick = onMarkAllAsRead,
            )
        }
    }
}

fun notificationSummaryActions(
    notificationCount: Int,
    isMarkingAllRead: Boolean,
    onMarkAllAsRead: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isMarkingAllRead) "处理中" else "全部已读",
        enabled = !isMarkingAllRead && notificationCount > 0,
        onClick = onMarkAllAsRead,
    ),
)

@Composable
private fun NotificationFilterChip(
    filter: NotificationFilter,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else LocalHhhlColors.current.inputBackground,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = filter.label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun notificationPrimaryFilters(): List<NotificationFilter> = listOf(
    NotificationFilter.All,
    NotificationFilter.Mentions,
    NotificationFilter.Reactions,
)

fun notificationVisiblePrimaryFilters(): List<NotificationFilter> = NotificationFilter.entries

fun notificationOverflowFilters(): List<NotificationFilter> =
    NotificationFilter.entries - notificationPrimaryFilters().toSet()

private fun notificationOverflowActions(
    selectedFilter: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit,
): List<HhhlOverflowMenuAction> {
    return notificationOverflowFilters().map { filter ->
        val prefix = if (filter == selectedFilter) "当前" else "切换到"
        HhhlOverflowMenuAction(
            label = "$prefix ${filter.label}",
            enabled = filter != selectedFilter,
            onClick = { onFilterSelected(filter) },
        )
    }
}

@Composable
private fun NotificationTypeBadge(type: NotificationType) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = type.icon,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NotificationActionRow(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryEnabled: Boolean,
    onSecondaryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotificationActionChip(
            label = primaryLabel,
            emphasized = true,
            enabled = primaryEnabled,
            onClick = onPrimaryClick,
        )
        NotificationActionChip(
            label = secondaryLabel,
            emphasized = false,
            enabled = secondaryEnabled,
            onClick = onSecondaryClick,
        )
    }
}

@Composable
private fun NotificationActionChip(
    label: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else LocalHhhlColors.current.inputBackground,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (!enabled) {
                LocalHhhlColors.current.subtleText
            } else if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NotificationStatusRow(
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

val NotificationItem.canActOnFollowRequest: Boolean
    get() = type == NotificationType.FollowRequestReceived

val NotificationItem.canOpenChat: Boolean
    get() = type == NotificationType.ChatRoomInvitation || !chatRoomId.isNullOrBlank()

val NotificationItem.hasNotePreview: Boolean
    get() = !notePreviewText.isNullOrBlank()

sealed interface NotificationNavigationTarget {
    data class NoteDetail(val noteId: String) : NotificationNavigationTarget

    data class UserProfile(val userId: String) : NotificationNavigationTarget

    data object Chat : NotificationNavigationTarget
}

val NotificationItem.navigationTarget: NotificationNavigationTarget?
    get() {
        noteId?.takeIf { it.isNotBlank() }?.let { return NotificationNavigationTarget.NoteDetail(it) }
        if (type == NotificationType.ChatRoomInvitation || !chatRoomId.isNullOrBlank()) {
            return NotificationNavigationTarget.Chat
        }
        if (type.opensActorProfile && actor.id.isNotBlank()) {
            return NotificationNavigationTarget.UserProfile(actor.id)
        }
        return null
    }

private val NotificationType.opensActorProfile: Boolean
    get() = when (this) {
        NotificationType.Note,
        NotificationType.Reply,
        NotificationType.Mention,
        NotificationType.Reaction,
        NotificationType.Follow,
        NotificationType.Renote,
        NotificationType.Quote,
        NotificationType.FollowRequestReceived,
        NotificationType.FollowRequestAccepted,
        NotificationType.ReactionGrouped,
        NotificationType.RenoteGrouped -> true
        NotificationType.PollEnded,
        NotificationType.RoleAssigned,
        NotificationType.ChatRoomInvitation,
        NotificationType.AchievementEarned,
        NotificationType.ExportCompleted,
        NotificationType.ImportCompleted,
        NotificationType.Login,
        NotificationType.CreateToken,
        NotificationType.App,
        NotificationType.Edited,
        NotificationType.ScheduledNoteFailed,
        NotificationType.ScheduledNotePosted,
        NotificationType.SharedAccessGranted,
        NotificationType.SharedAccessRevoked,
        NotificationType.SharedAccessLogin,
        NotificationType.Test,
        NotificationType.Unknown -> false
    }

private val NotificationType.icon: String
    get() = when (this) {
        NotificationType.Note -> "帖"
        NotificationType.Reply -> "回"
        NotificationType.Mention -> "@"
        NotificationType.Reaction -> "赞"
        NotificationType.Follow -> "关"
        NotificationType.Renote -> "转"
        NotificationType.Quote -> "引"
        NotificationType.PollEnded -> "票"
        NotificationType.FollowRequestReceived -> "请"
        NotificationType.FollowRequestAccepted -> "准"
        NotificationType.RoleAssigned -> "角"
        NotificationType.ChatRoomInvitation -> "聊"
        NotificationType.AchievementEarned -> "成"
        NotificationType.ExportCompleted -> "出"
        NotificationType.ImportCompleted -> "入"
        NotificationType.Login -> "登"
        NotificationType.CreateToken -> "令"
        NotificationType.App -> "应"
        NotificationType.Edited -> "编"
        NotificationType.ScheduledNoteFailed -> "误"
        NotificationType.ScheduledNotePosted -> "定"
        NotificationType.SharedAccessGranted -> "权"
        NotificationType.SharedAccessRevoked -> "撤"
        NotificationType.SharedAccessLogin -> "共"
        NotificationType.ReactionGrouped -> "赞"
        NotificationType.RenoteGrouped -> "转"
        NotificationType.Test -> "测"
        NotificationType.Unknown -> "通"
    }
