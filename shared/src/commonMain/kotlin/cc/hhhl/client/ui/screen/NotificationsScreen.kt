package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Announcement
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.model.NotificationFilter
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.state.AnnouncementUiState
import cc.hhhl.client.state.NotificationUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AiResultCommonActionChips
import cc.hhhl.client.ui.component.AiResultPanel
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlAnimatedSegmentedControl
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.presentation.notificationLineText
import cc.hhhl.client.presentation.richTextPlainPreviewText

private const val NotificationCollapsedLineMaxChars = 76
private const val NotificationCollapsedPreviewMaxChars = 62

@Composable
fun NotificationsScreen(
    state: NotificationUiState? = null,
    announcementState: AnnouncementUiState? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    onFlush: () -> Unit = {},
    onMarkNotificationRead: (String) -> Unit = {},
    onFilterSelected: (NotificationFilter) -> Unit = {},
    onRefreshAnnouncements: () -> Unit = {},
    onLoadMoreAnnouncements: () -> Unit = {},
    onOpenAnnouncement: (String) -> Unit = {},
    onCloseAnnouncement: () -> Unit = {},
    onMarkAnnouncementRead: (String) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onReplyToNote: (String) -> Unit = {},
    onReactToNote: (String, String) -> Unit = { _, _ -> },
    onFollowUser: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    pendingFollowRequestUserIds: Set<String> = emptySet(),
    onAcceptFollowRequest: (String) -> Unit = {},
    onRejectFollowRequest: (String) -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenChatUser: (String, String?) -> Unit = { _, _ -> },
    onSendTestNotification: () -> Unit = {},
    onSendReminderNotification: () -> Unit = {},
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiAction: (AiTaskKind, List<NotificationItem>, NotificationFilter) -> Unit = { _, _, _ -> },
    onCopyAiResult: ((String) -> Unit)? = null,
    onAddAiMutedWord: ((String) -> Unit)? = null,
    onAddAiRelatedNoteToWatchLater: ((String, List<NotificationItem>) -> Unit)? = null,
    onOpenAiRelatedNote: ((String, List<NotificationItem>) -> Unit)? = null,
    onDismissAiResult: () -> Unit = {},
    notificationListState: LazyListState = rememberLazyListState(),
    announcementListState: LazyListState = rememberLazyListState(),
    onBackHandlerChanged: (((() -> Boolean)?) -> Unit)? = null,
) {
    val notifications = state?.notifications.orEmpty()
    val selectedFilter = state?.selectedFilter ?: NotificationFilter.All
    val announcements = announcementState?.announcements.orEmpty()
    var selectedSection by remember { mutableStateOf(NotificationInboxSection.Notifications) }

    LaunchedEffect(
        selectedSection,
        announcementState?.announcements?.isEmpty(),
        announcementState?.isLoading,
        announcementState?.errorMessage,
    ) {
        if (
            selectedSection == NotificationInboxSection.Announcements &&
            announcementState != null &&
            announcementState.announcements.isEmpty() &&
            !announcementState.isLoading &&
            announcementState.errorMessage == null
        ) {
            onRefreshAnnouncements()
        }
    }

    val selectedAnnouncement = if (selectedSection == NotificationInboxSection.Announcements) {
        announcementState?.selectedAnnouncement
    } else {
        null
    }
    val latestNotificationsBackHandler by rememberUpdatedState<() -> Boolean> {
        if (selectedAnnouncement != null) {
            onCloseAnnouncement()
            true
        } else {
            false
        }
    }
    DisposableEffect(onBackHandlerChanged) {
        onBackHandlerChanged?.invoke { latestNotificationsBackHandler() }
        onDispose { onBackHandlerChanged?.invoke(null) }
    }
    if (selectedAnnouncement != null && announcementState != null) {
        AnnouncementDetailView(
            announcement = selectedAnnouncement,
            isLoading = announcementState.isLoadingDetail,
            errorMessage = announcementState.detailErrorMessage,
            actionErrorMessage = announcementState.actionErrorMessage,
            isPending = announcementState.pendingAnnouncementIds.contains(selectedAnnouncement.id),
            onBack = onCloseAnnouncement,
            onMarkRead = onMarkAnnouncementRead,
        )
        return
    }

    val unreadNotificationCount = state?.unreadCount ?: notifications.size
    val unreadAnnouncementCount = announcements.count { !it.isRead }
    val isCurrentSectionLoading = when (selectedSection) {
        NotificationInboxSection.Notifications -> state?.isLoading == true
        NotificationInboxSection.Announcements -> announcementState?.isLoading == true
    }
    val headerActions = when (selectedSection) {
        NotificationInboxSection.Notifications -> notificationSummaryActions(
            isLoading = state?.isLoading == true,
            isMarkingAllRead = state?.isMarkingAllRead == true,
            notificationCount = notifications.size,
            onMarkAllAsRead = onMarkAllAsRead,
            onFlush = onFlush,
            onSendTestNotification = onSendTestNotification,
            onSendReminderNotification = onSendReminderNotification,
            aiEnabled = aiEnabled,
            isAiProcessing = isAiProcessing,
            onAiSummary = { onAiAction(AiTaskKind.NotificationSummary, notifications, selectedFilter) },
            onAiFollowUp = { onAiAction(AiTaskKind.NotificationFollowUp, notifications, selectedFilter) },
            onAiPriority = { onAiAction(AiTaskKind.NotificationPriority, notifications, selectedFilter) },
        )
        NotificationInboxSection.Announcements -> listOf(
            HhhlOverflowMenuAction(
                label = if (announcementState?.isLoading == true) "同步中" else "刷新公告",
                icon = Icons.Filled.Refresh,
                enabled = announcementState?.isLoading != true,
                onClick = onRefreshAnnouncements,
            ),
        )
    }

    val screenColors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenColors.pageBackground),
    ) {
        NotificationWeChatHeader(
            selectedSection = selectedSection,
            unreadNotificationCount = unreadNotificationCount,
            unreadAnnouncementCount = unreadAnnouncementCount,
            isRefreshing = isCurrentSectionLoading,
            actions = headerActions,
            onRefresh = when (selectedSection) {
                NotificationInboxSection.Notifications -> onRefresh
                NotificationInboxSection.Announcements -> onRefreshAnnouncements
            },
            onSelected = { selectedSection = it },
        )
        when (selectedSection) {
            NotificationInboxSection.Notifications -> NotificationListContent(
                state = state,
                notifications = notifications,
                selectedFilter = selectedFilter,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onMarkAllAsRead = onMarkAllAsRead,
                onFlush = onFlush,
                onMarkNotificationRead = onMarkNotificationRead,
                onFilterSelected = onFilterSelected,
                onOpenNote = onOpenNote,
                onOpenUser = onOpenUser,
                onReplyToNote = onReplyToNote,
                onReactToNote = onReactToNote,
                onFollowUser = onFollowUser,
                onOpenUrl = onOpenUrl,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
                pendingFollowRequestUserIds = pendingFollowRequestUserIds,
                onAcceptFollowRequest = onAcceptFollowRequest,
                onRejectFollowRequest = onRejectFollowRequest,
                onOpenChat = onOpenChat,
                onOpenChatUser = onOpenChatUser,
                onSendTestNotification = onSendTestNotification,
                onSendReminderNotification = onSendReminderNotification,
                aiEnabled = aiEnabled,
                isAiProcessing = isAiProcessing,
                aiResultText = aiResultText,
                aiResultLabel = aiResultLabel,
                onAiAction = onAiAction,
                onCopyAiResult = onCopyAiResult,
                onAddAiMutedWord = onAddAiMutedWord,
                onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
                onOpenAiRelatedNote = onOpenAiRelatedNote,
                onDismissAiResult = onDismissAiResult,
                listState = notificationListState,
            )
            NotificationInboxSection.Announcements -> AnnouncementNotificationContent(
                state = announcementState,
                announcements = announcements,
                onRefresh = onRefreshAnnouncements,
                onLoadMore = onLoadMoreAnnouncements,
                onOpenAnnouncement = onOpenAnnouncement,
                listState = announcementListState,
            )
        }
    }
}

private enum class NotificationInboxSection {
    Notifications,
    Announcements,
}

@Composable
private fun NotificationWeChatHeader(
    selectedSection: NotificationInboxSection,
    unreadNotificationCount: Int,
    unreadAnnouncementCount: Int,
    isRefreshing: Boolean,
    actions: List<HhhlOverflowMenuAction>,
    onRefresh: () -> Unit,
    onSelected: (NotificationInboxSection) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlAnimatedSegmentedControl(
            labels = listOf("通知", "公告"),
            selectedIndex = if (selectedSection == NotificationInboxSection.Notifications) 0 else 1,
            badgeCounts = listOf(unreadNotificationCount, unreadAnnouncementCount),
            onSelected = { index ->
                onSelected(
                    if (index == 0) {
                        NotificationInboxSection.Notifications
                    } else {
                        NotificationInboxSection.Announcements
                    },
                )
            },
            modifier = Modifier
                .weight(1f),
            itemBaseHeight = 32.dp,
        )
        HhhlIconActionButton(
            icon = Icons.Filled.Refresh,
            contentDescription = if (isRefreshing) "同步中" else "刷新",
            enabled = !isRefreshing,
            onClick = onRefresh,
        )
        HhhlOverflowMenu(
            actions = actions,
            label = "通知操作",
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

private val NotificationInboxSection.label: String
    get() = when (this) {
        NotificationInboxSection.Notifications -> "通知"
        NotificationInboxSection.Announcements -> "公告"
    }

@Composable
private fun NotificationInboxSectionRow(
    selectedSection: NotificationInboxSection,
    unreadNotificationCount: Int,
    unreadAnnouncementCount: Int,
    onSelected: (NotificationInboxSection) -> Unit,
) {
    HhhlAnimatedSegmentedControl(
        labels = listOf("通知", "公告"),
        selectedIndex = if (selectedSection == NotificationInboxSection.Notifications) 0 else 1,
        badgeCounts = listOf(unreadNotificationCount, unreadAnnouncementCount),
        onSelected = { index ->
            onSelected(
                if (index == 0) {
                    NotificationInboxSection.Notifications
                } else {
                    NotificationInboxSection.Announcements
                },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun NotificationListContent(
    state: NotificationUiState?,
    notifications: List<NotificationItem>,
    selectedFilter: NotificationFilter,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onFlush: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onFilterSelected: (NotificationFilter) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReplyToNote: (String) -> Unit,
    onReactToNote: (String, String) -> Unit,
    onFollowUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    pendingFollowRequestUserIds: Set<String>,
    onAcceptFollowRequest: (String) -> Unit,
    onRejectFollowRequest: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenChatUser: (String, String?) -> Unit,
    onSendTestNotification: () -> Unit,
    onSendReminderNotification: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    aiResultText: String?,
    aiResultLabel: String?,
    onAiAction: (AiTaskKind, List<NotificationItem>, NotificationFilter) -> Unit,
    onCopyAiResult: ((String) -> Unit)?,
    onAddAiMutedWord: ((String) -> Unit)?,
    onAddAiRelatedNoteToWatchLater: ((String, List<NotificationItem>) -> Unit)?,
    onOpenAiRelatedNote: ((String, List<NotificationItem>) -> Unit)?,
    onDismissAiResult: () -> Unit,
    listState: LazyListState,
) {
    NotificationPrimaryFilterRow(
        selectedFilter = selectedFilter,
        specialCareUnreadCount = state?.specialCareUnreadCount ?: notifications.countSpecialCareUnread(),
        onFilterSelected = onFilterSelected,
    )
    if (!aiResultText.isNullOrBlank()) {
        NotificationAiResultPanel(
            label = aiResultLabel ?: "AI 结果",
            text = aiResultText,
            notifications = notifications,
            onCopyAiResult = onCopyAiResult,
            onAddAiMutedWord = onAddAiMutedWord,
            onAddAiRelatedNoteToWatchLater = onAddAiRelatedNoteToWatchLater,
            onOpenAiRelatedNote = onOpenAiRelatedNote,
            onDismiss = onDismissAiResult,
        )
        HhhlDivider()
    }
    LazyColumn(state = listState) {
        state?.message?.let { message ->
            item(key = "notification-message", contentType = "notification-status") {
                NotificationStatusRow(text = message)
            }
        }
        if (selectedFilter == NotificationFilter.SpecialCare) {
            item(key = "notification-special-care-help", contentType = "notification-special-care-help") {
                NotificationStatusRow(
                    text = specialCareHelperText(
                        isLoading = state?.isLoading == true,
                        notificationCount = notifications.size,
                    ),
                )
            }
        }
        if (state?.isLoading == true && notifications.isEmpty()) {
            item(key = "notification-loading-${selectedFilter.name}", contentType = "notification-status") {
                NotificationSkeletonList()
            }
        }
        state?.errorMessage?.let { message ->
            item(key = "notification-error", contentType = "notification-status") {
                NotificationStatusRow(
                    text = message,
                    actionText = "重试",
                    onAction = onRefresh,
                )
            }
        }
        if (state != null && !state.isLoading && notifications.isEmpty() && state.errorMessage == null) {
            item(key = "notification-empty-${selectedFilter.name}", contentType = "notification-status") {
                NotificationStatusRow(text = notificationEmptyText(selectedFilter))
            }
        }
        items(
            items = notifications,
            key = { it.id },
            contentType = { "notification-row" },
        ) { notification ->
            NotificationRow(
                notification = notification,
                pendingFollowRequestUserIds = pendingFollowRequestUserIds,
                onOpenNote = onOpenNote,
                onOpenUser = onOpenUser,
                onReplyToNote = onReplyToNote,
                onReactToNote = onReactToNote,
                onFollowUser = onFollowUser,
                onOpenUrl = onOpenUrl,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
                onMarkNotificationRead = onMarkNotificationRead,
                onAcceptFollowRequest = onAcceptFollowRequest,
                onRejectFollowRequest = onRejectFollowRequest,
                onOpenChat = onOpenChat,
                onOpenChatUser = onOpenChatUser,
            )
        }
        if (state != null && notifications.isNotEmpty() && state.isLoadingMore) {
            item(key = "notification-loading-more", contentType = "notification-status") {
                NotificationStatusRow(
                    text = "正在加载更多...",
                    loading = state.isLoadingMore,
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationItem,
    pendingFollowRequestUserIds: Set<String>,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReplyToNote: (String) -> Unit,
    onReactToNote: (String, String) -> Unit,
    onFollowUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onAcceptFollowRequest: (String) -> Unit,
    onRejectFollowRequest: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenChatUser: (String, String?) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var expanded by remember(notification.id) { mutableStateOf(false) }
    val lineText = remember(notification.id, notification.type, notification.actor.displayName, notification.text) {
        notificationLineText(notification)
    }
    val notePreviewText = remember(notification.notePreviewText) {
        notification.notePreviewText.orEmpty().normalizeNotificationNotePreviewText()
    }
    val canExpand = remember(lineText, notePreviewText, notification.hasNotePreview) {
        lineText.length > NotificationCollapsedLineMaxChars ||
            (notification.hasNotePreview && notePreviewText.length > NotificationCollapsedPreviewMaxChars)
    }
    val rowClick = {
        onMarkNotificationRead(notification.id)
        when (val target = notification.navigationTarget) {
            is NotificationNavigationTarget.NoteDetail -> onOpenNote(target.noteId)
            is NotificationNavigationTarget.UserProfile -> onOpenUser(target.userId)
            is NotificationNavigationTarget.ChatUser -> onOpenChatUser(target.userId, target.messageId)
            NotificationNavigationTarget.Chat -> onOpenChat()
            null -> {
                // 没有导航目标时，才切换展开状态
                if (canExpand) {
                    expanded = !expanded
                }
            }
        }
    }
    val createdAtLabel = notification.createdAtLabel.ifBlank { "刚刚" }
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
                text = lineText,
                style = MaterialTheme.typography.bodyMedium,
                maxChars = if (expanded) null else NotificationCollapsedLineMaxChars,
                color = if (notification.isRead) {
                    colors.textMuted
                } else {
                    colors.textPrimary
                },
                onOpenUrl = onOpenUrl,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
            )
            if (notification.hasNotePreview) {
                InlineRichText(
                    text = notePreviewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxChars = if (expanded) null else NotificationCollapsedPreviewMaxChars,
                    onOpenUrl = onOpenUrl,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                )
            }
            if (canExpand) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { expanded = !expanded },
                )
            }
            Text(
                if (notification.isRead) "$createdAtLabel · 已读" else createdAtLabel,
                color = colors.textMuted,
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
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val quickActions = notificationQuickActions(
                notification = notification,
                onReplyToNote = onReplyToNote,
                onReactToNote = onReactToNote,
                onFollowUser = onFollowUser,
                onOpenNote = onOpenNote,
            )
            if (quickActions.isNotEmpty()) {
                NotificationQuickActionRow(actions = quickActions)
            }
        }
    }
    HhhlDivider()
}

@Composable
private fun AnnouncementNotificationContent(
    state: AnnouncementUiState?,
    announcements: List<Announcement>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    AutoLoadMoreEffect(
        listState = listState,
        itemCount = announcements.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

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
            item(key = "announcement-error", contentType = "announcement-status") {
                NotificationStatusRow(
                    text = message,
                    actionText = "重试",
                    onAction = onRefresh,
                )
            }
        }
        if (state?.isLoading == true && announcements.isEmpty()) {
            item(key = "announcement-loading", contentType = "announcement-status") {
                NotificationSkeletonList(count = 3)
            }
        }
        if (state != null && !state.isLoading && announcements.isEmpty() && state.errorMessage == null) {
            item(key = "announcement-empty", contentType = "announcement-status") {
                NotificationStatusRow(text = "还没有公告")
            }
        }
        items(
            items = announcements,
            key = { it.id },
            contentType = { "announcement-row" },
        ) { announcement ->
            AnnouncementRow(
                announcement = announcement,
                onOpenAnnouncement = onOpenAnnouncement,
            )
        }
        if (state != null && announcements.isNotEmpty() && state.isLoadingMore) {
            item(key = "announcement-loading-more", contentType = "announcement-status") {
                NotificationStatusRow(
                    text = "正在加载更多...",
                    loading = state.isLoadingMore,
                )
            }
        }
    }
}

@Composable
private fun NotificationPrimaryFilterRow(
    selectedFilter: NotificationFilter,
    specialCareUnreadCount: Int,
    onFilterSelected: (NotificationFilter) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        notificationPrimaryFilters().forEach { filter ->
            NotificationFilterChip(
                selected = filter == selectedFilter,
                label = notificationFilterLabel(filter, specialCareUnreadCount),
                onClick = { onFilterSelected(filter) },
            )
        }
        HhhlOverflowMenu(
            actions = notificationOverflowActions(
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
            ),
            label = "更多筛选",
            buttonContainerColor = Color.Transparent,
            iconTint = colors.textSecondary,
            buttonWidth = 34.dp,
            buttonHeight = 30.dp,
            buttonIconSize = 17.dp,
            buttonCornerRadius = 999.dp,
            buttonBorderAlpha = 0f,
            buttonElevation = 0.dp,
        )
    }
}

@Composable
private fun NotificationFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.26f) else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent else colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotificationSummaryRow(
    selectedFilter: NotificationFilter,
    notificationCount: Int,
    unreadCount: Int,
    specialCareNotificationCount: Int,
    specialCareUnreadCount: Int,
    isLoading: Boolean,
    isMarkingAllRead: Boolean,
    onRefresh: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onFlush: () -> Unit,
    onSendTestNotification: () -> Unit,
    onSendReminderNotification: () -> Unit,
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    onAiSummary: () -> Unit,
    onAiFollowUp: () -> Unit,
    onAiPriority: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val stateText = when {
        isLoading -> "加载中"
        selectedFilter == NotificationFilter.SpecialCare && specialCareUnreadCount > 0 ->
            "${specialCareUnreadCount} 条未读 / ${specialCareNotificationCount} 条特别关心"
        selectedFilter == NotificationFilter.SpecialCare -> "${specialCareNotificationCount} 条特别关心"
        unreadCount > 0 -> "${unreadCount} 条未读 / ${notificationCount} 条"
        else -> "${notificationCount} 条通知"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${selectedFilter.label} · $stateText",
            color = colors.textPrimary,
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
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isLoading) "同步中" else "刷新",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
            HhhlOverflowMenu(
                actions = notificationSummaryActions(
                    isLoading = isLoading,
                    isMarkingAllRead = isMarkingAllRead,
                    notificationCount = notificationCount,
                    onMarkAllAsRead = onMarkAllAsRead,
                    onFlush = onFlush,
                    onSendTestNotification = onSendTestNotification,
                    onSendReminderNotification = onSendReminderNotification,
                    aiEnabled = aiEnabled,
                    isAiProcessing = isAiProcessing,
                    onAiSummary = onAiSummary,
                    onAiFollowUp = onAiFollowUp,
                    onAiPriority = onAiPriority,
                ),
                enabled = !isMarkingAllRead,
                label = "通知操作",
            )
        }
    }
}

internal fun notificationMarkAllReadEnabled(
    isLoading: Boolean,
    isMarkingAllRead: Boolean,
): Boolean = !isLoading && !isMarkingAllRead

fun notificationSummaryActions(
    isLoading: Boolean = false,
    isMarkingAllRead: Boolean,
    notificationCount: Int = 0,
    onMarkAllAsRead: () -> Unit = {},
    onFlush: () -> Unit = {},
    onSendTestNotification: () -> Unit,
    onSendReminderNotification: () -> Unit,
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    onAiSummary: () -> Unit = {},
    onAiFollowUp: () -> Unit = {},
    onAiPriority: () -> Unit = {},
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isMarkingAllRead) "处理中" else "全部已读",
        enabled = notificationMarkAllReadEnabled(
            isLoading = isLoading,
            isMarkingAllRead = isMarkingAllRead,
        ),
        onClick = onMarkAllAsRead,
    ),
    HhhlOverflowMenuAction(
        label = "清空",
        enabled = !isMarkingAllRead && notificationCount > 0,
        destructive = true,
        onClick = onFlush,
    ),
    HhhlOverflowMenuAction(
        label = if (isAiProcessing) "AI 处理中" else "AI",
        enabled = aiEnabled && !isAiProcessing && !isMarkingAllRead,
        icon = Icons.Filled.AutoAwesome,
        onClick = {},
        children = listOf(
            HhhlOverflowMenuAction(label = "总结通知", icon = Icons.Filled.AutoAwesome, onClick = onAiSummary),
            HhhlOverflowMenuAction(label = "待处理", icon = Icons.Filled.AutoAwesome, onClick = onAiFollowUp),
            HhhlOverflowMenuAction(label = "优先级", icon = Icons.Filled.AutoAwesome, onClick = onAiPriority),
        ),
    ),
    HhhlOverflowMenuAction(
        label = "测试通知",
        enabled = !isMarkingAllRead,
        onClick = onSendTestNotification,
    ),
    HhhlOverflowMenuAction(
        label = "提醒自己",
        enabled = !isMarkingAllRead,
        onClick = onSendReminderNotification,
    ),
)

fun notificationPrimaryFilters(): List<NotificationFilter> = listOf(
    NotificationFilter.All,
    NotificationFilter.Mentions,
    NotificationFilter.Reactions,
)

fun notificationVisiblePrimaryFilters(): List<NotificationFilter> = notificationPrimaryFilters()

fun notificationOverflowFilters(): List<NotificationFilter> =
    listOf(
        NotificationFilter.SpecialCare,
        NotificationFilter.Replies,
        NotificationFilter.Quotes,
        NotificationFilter.Achievements,
        NotificationFilter.Follows,
        NotificationFilter.System,
    )

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

fun notificationFilterLabel(
    filter: NotificationFilter,
    specialCareUnreadCount: Int,
): String {
    return if (filter == NotificationFilter.SpecialCare && specialCareUnreadCount > 0) {
        "${filter.label} $specialCareUnreadCount"
    } else {
        filter.label
    }
}

fun notificationEmptyText(selectedFilter: NotificationFilter): String {
    return if (selectedFilter == NotificationFilter.SpecialCare) {
        "还没有特别关心通知；特别关心用户发布新帖子或发来聊天消息后，会在这里显示。"
    } else {
        "暂时没有通知"
    }
}

@Composable
private fun NotificationAiResultPanel(
    label: String,
    text: String,
    notifications: List<NotificationItem>,
    onCopyAiResult: ((String) -> Unit)?,
    onAddAiMutedWord: ((String) -> Unit)?,
    onAddAiRelatedNoteToWatchLater: ((String, List<NotificationItem>) -> Unit)?,
    onOpenAiRelatedNote: ((String, List<NotificationItem>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AiResultPanel(
        label = label,
        text = text,
        onDismiss = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        actions = {
            AiResultCommonActionChips(
                text = text,
                onCopyChecklist = onCopyAiResult,
                onAddMutedWord = onAddAiMutedWord,
                onAddToWatchLater = onAddAiRelatedNoteToWatchLater?.let { add -> { add(text, notifications) } },
                onOpenRelatedNote = onOpenAiRelatedNote?.let { open -> { open(text, notifications) } },
            )
        },
    )
}

fun specialCareHelperText(
    isLoading: Boolean,
    notificationCount: Int,
): String {
    return when {
        isLoading -> "正在同步本地特别关心提醒"
        notificationCount > 0 -> "这里汇总特别关心用户的新帖子和聊天消息，点按可跳转到对应内容。"
        else -> "特别关心通知来自本地特别关心用户集合，不需要额外服务器接口。"
    }
}

private fun List<NotificationItem>.countSpecialCareUnread(): Int {
    return count { it.isSpecialCare && !it.isRead }
}

data class NotificationQuickAction(
    val label: String,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

fun notificationQuickActionLabels(notification: NotificationItem): List<String> {
    return notificationQuickActionSpecs(notification).map { it.label }
}

@Composable
private fun NotificationQuickActionRow(actions: List<NotificationQuickAction>) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            NotificationActionChip(
                label = action.label,
                emphasized = action.emphasized,
                enabled = true,
                onClick = action.onClick,
            )
        }
    }
}

private fun notificationQuickActions(
    notification: NotificationItem,
    onReplyToNote: (String) -> Unit,
    onReactToNote: (String, String) -> Unit,
    onFollowUser: (String) -> Unit,
    onOpenNote: (String) -> Unit,
): List<NotificationQuickAction> {
    return notificationQuickActionSpecs(notification).map { spec ->
        NotificationQuickAction(
            label = spec.label,
            emphasized = spec.emphasized,
            onClick = {
                when (spec.kind) {
                    NotificationQuickActionKind.Reply -> notification.noteId?.let(onReplyToNote)
                    NotificationQuickActionKind.React -> notification.noteId?.let { noteId -> onReactToNote(noteId, "❤️") }
                    NotificationQuickActionKind.FollowBack -> onFollowUser(notification.actor.id)
                    NotificationQuickActionKind.OpenNote -> notification.noteId?.let(onOpenNote)
                }
            },
        )
    }
}

private data class NotificationQuickActionSpec(
    val kind: NotificationQuickActionKind,
    val label: String,
    val emphasized: Boolean = false,
)

private enum class NotificationQuickActionKind {
    Reply,
    React,
    FollowBack,
    OpenNote,
}

private fun notificationQuickActionSpecs(notification: NotificationItem): List<NotificationQuickActionSpec> {
    return buildList {
        if (!notification.noteId.isNullOrBlank()) {
            if (notification.type == NotificationType.Reply || notification.type == NotificationType.Mention) {
                add(NotificationQuickActionSpec(NotificationQuickActionKind.Reply, "回复", emphasized = true))
            }
            if (notification.type != NotificationType.Reaction && notification.type != NotificationType.ReactionGrouped) {
                add(NotificationQuickActionSpec(NotificationQuickActionKind.React, "回应"))
            }
            if (notification.type == NotificationType.Reaction || notification.type == NotificationType.Renote) {
                add(NotificationQuickActionSpec(NotificationQuickActionKind.OpenNote, "查看帖子"))
            }
        }
        if (notification.type == NotificationType.Follow && notification.actor.id.isNotBlank()) {
            add(NotificationQuickActionSpec(NotificationQuickActionKind.FollowBack, "关注回去", emphasized = true))
        }
    }
}

@Composable
private fun NotificationTypeBadge(type: NotificationType) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accentSoft)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = type.icon,
            color = colors.textSecondary,
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
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (emphasized) colors.buttonSelectedBackground
                else colors.buttonBackground,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (!enabled) {
                colors.textMuted
            } else if (emphasized) {
                colors.accent
            } else {
                colors.textPrimary
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
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
private fun NotificationSkeletonList(count: Int = 5) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            NotificationSkeletonRow(compact = index % 2 == 1)
        }
    }
}

@Composable
private fun NotificationSkeletonRow(compact: Boolean) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 38.dp, max = 38.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.inputBackground.copy(alpha = 0.72f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotificationSkeletonBar(widthFraction = if (compact) 0.42f else 0.56f, height = 12.dp)
            NotificationSkeletonBar(widthFraction = 1f, height = 11.dp)
            NotificationSkeletonBar(widthFraction = if (compact) 0.58f else 0.78f, height = 11.dp)
        }
    }
    HhhlDivider()
}

@Composable
private fun NotificationSkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.inputBackground.copy(alpha = 0.72f)),
    )
}

val NotificationItem.canActOnFollowRequest: Boolean
    get() = type == NotificationType.FollowRequestReceived

val NotificationItem.canOpenChat: Boolean
    get() = type == NotificationType.ChatRoomInvitation || !chatRoomId.isNullOrBlank() || !chatUserId.isNullOrBlank()

val NotificationItem.hasNotePreview: Boolean
    get() = !notePreviewText.isNullOrBlank()

internal fun String.normalizeNotificationNotePreviewText(): String {
    return richTextPlainPreviewText(this)
}

sealed interface NotificationNavigationTarget {
    data class NoteDetail(val noteId: String) : NotificationNavigationTarget

    data class UserProfile(val userId: String) : NotificationNavigationTarget

    data class ChatUser(
        val userId: String,
        val messageId: String? = null,
    ) : NotificationNavigationTarget

    data object Chat : NotificationNavigationTarget
}

val NotificationItem.navigationTarget: NotificationNavigationTarget?
    get() {
        noteId?.takeIf { it.isNotBlank() }?.let { return NotificationNavigationTarget.NoteDetail(it) }
        chatUserId?.takeIf { it.isNotBlank() }?.let {
            return NotificationNavigationTarget.ChatUser(
                userId = it,
                messageId = chatMessageId?.takeIf { messageId -> messageId.isNotBlank() },
            )
        }
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
