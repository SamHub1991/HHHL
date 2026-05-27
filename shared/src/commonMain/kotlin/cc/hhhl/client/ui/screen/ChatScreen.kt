package cc.hhhl.client.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.commonEmojiOptions
import cc.hhhl.client.state.ChatUiState
import cc.hhhl.client.state.SpecialCareChatToast
import cc.hhhl.client.state.primaryChatAttachmentFile
import cc.hhhl.client.state.sendableChatAttachmentFileIds
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.toColorOrNull
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.CustomEmojiPicker
import cc.hhhl.client.ui.component.CustomEmojiReactionLabel
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlSegmentedControl
import cc.hhhl.client.ui.component.HhhlSegmentedItem
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.chatMessageBodyText
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession
import cc.hhhl.client.ui.component.hhhlNeutralControlBorderColor
import cc.hhhl.client.ui.component.hhhlNeutralControlContainerColor
import cc.hhhl.client.ui.component.mediaTypeDisplayName

private data class ChatOlderLoadAnchor(
    val messageId: String,
    val scrollOffset: Int,
)

private enum class ChatHomeTab {
    Rooms,
    Users,
}

private val ChatMessageBubbleTailWidth = 9.dp
private val ChatMessageBubbleTailHeight = 8.dp
private val ChatMessageBubbleTailEdgeOverlap = 1.dp

@Composable
private fun rememberChatPresslessInteractionSource(): MutableInteractionSource {
    return remember { MutableInteractionSource() }
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    currentUserId: String? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenRoom: (ChatRoom) -> Unit = {},
    onOpenUserConversation: (ChatUserConversation) -> Unit = {},
    onToggleRoomPinned: (String) -> Unit = {},
    onToggleUserConversationPinned: (String) -> Unit = {},
    onDeleteUserConversation: (String) -> Unit = {},
    onCreateRoom: (String, String) -> Unit = { _, _ -> },
    onBackToRooms: () -> Unit = {},
    onRefreshMessages: () -> Unit = {},
    onLoadOlderMessages: () -> Unit = {},
    onSearchMessages: (String) -> Unit = {},
    onLoadMoreMessageSearch: () -> Unit = {},
    onShowMessages: () -> Unit = {},
    onShowMembers: () -> Unit = {},
    onLoadMoreMembers: () -> Unit = {},
    onUpdateRoom: (String, String) -> Unit = { _, _ -> },
    onInviteRoomMember: (String) -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    onDeleteRoom: () -> Unit = {},
    onMuteRoom: (Boolean) -> Unit = {},
    onMessageDraftChanged: (String) -> Unit = {},
    onSendMessage: () -> Unit = {},
    onQuoteMessage: (String) -> Unit = {},
    onReplyMessage: (String) -> Unit = {},
    onCancelQuoteMessage: () -> Unit = {},
    onReactMessage: (String, String) -> Unit = { _, _ -> },
    onUnreactMessage: (String, String) -> Unit = { _, _ -> },
    onDeleteMessage: (String) -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onReportMessage: (String) -> Unit = {},
    onAddMedia: () -> Unit = {},
    onAddFile: () -> Unit = onAddMedia,
    onOpenDrivePicker: () -> Unit = {},
    onRemoveAttachedFile: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    onOpenSpecialCareToast: () -> Unit = {},
    onDismissSpecialCareToast: () -> Unit = {},
    onSpecialCareJumpHandled: () -> Unit = {},
    onUnreadJumpHandled: () -> Unit = {},
    customEmojis: List<CustomEmoji> = emptyList(),
    recentEmojiCodes: List<String> = emptyList(),
    isMediaPickerAvailable: Boolean = false,
    customTheme: HhhlCustomTheme = HhhlCustomTheme(),
) {
    val selectedRoom = state.selectedRoom
    val selectedUserConversation = state.selectedUserConversation
    if (selectedRoom != null || selectedUserConversation != null) {
        ChatDetailScreen(
            room = selectedRoom,
            userConversation = selectedUserConversation,
            state = state,
            onBack = onBackToRooms,
            onRefresh = onRefreshMessages,
            onLoadOlderMessages = onLoadOlderMessages,
            onSearchMessages = onSearchMessages,
            onLoadMoreMessageSearch = onLoadMoreMessageSearch,
            onShowMessages = onShowMessages,
            onShowMembers = onShowMembers,
            onLoadMoreMembers = onLoadMoreMembers,
            onUpdateRoom = onUpdateRoom,
            onInviteRoomMember = onInviteRoomMember,
            onLeaveRoom = onLeaveRoom,
            onDeleteRoom = onDeleteRoom,
            onMuteRoom = onMuteRoom,
            onMessageDraftChanged = onMessageDraftChanged,
            onSendMessage = onSendMessage,
            onQuoteMessage = onQuoteMessage,
            onReplyMessage = onReplyMessage,
            onCancelQuoteMessage = onCancelQuoteMessage,
            onReactMessage = onReactMessage,
            onUnreactMessage = onUnreactMessage,
            onDeleteMessage = onDeleteMessage,
            onCopyMessage = onCopyMessage,
            onReportMessage = onReportMessage,
            onAddMedia = onAddMedia,
            onAddFile = onAddFile,
            onOpenDrivePicker = onOpenDrivePicker,
            onRemoveAttachedFile = onRemoveAttachedFile,
            onOpenUser = onOpenUser,
            onOpenUrl = onOpenUrl,
            onOpenMediaPreview = onOpenMediaPreview,
            onOpenMention = onOpenMention,
            onOpenHashtag = onOpenHashtag,
            onOpenSpecialCareToast = onOpenSpecialCareToast,
            onDismissSpecialCareToast = onDismissSpecialCareToast,
            onSpecialCareJumpHandled = onSpecialCareJumpHandled,
            onUnreadJumpHandled = onUnreadJumpHandled,
            customEmojis = customEmojis,
            recentEmojiCodes = recentEmojiCodes,
            isMediaPickerAvailable = isMediaPickerAvailable,
            customTheme = customTheme,
            currentUserId = currentUserId,
        )
        return
    }

    var homeTab by remember { mutableStateOf(ChatHomeTab.Rooms) }
    var roomSearchQuery by remember { mutableStateOf("") }
    var userSearchQuery by remember { mutableStateOf("") }
    val visibleRooms = remember(state.rooms, roomSearchQuery) {
        state.rooms.filterByChatRoomQuery(roomSearchQuery)
    }
    val visibleUserConversations = remember(state.userConversations, userSearchQuery) {
        state.userConversations.filterByChatUserConversationQuery(userSearchQuery)
    }
    val unreadRoomCount = remember(state.rooms) {
        state.rooms.count { it.unreadCount > 0 }
    }
    val totalUnreadCount = remember(state.rooms) {
        state.rooms.sumOf { it.unreadCount.coerceAtLeast(0) }
    }
    val homeListState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = homeListState,
        itemCount = visibleRooms.size,
        isLoadingMore = state.isLoadingMore ||
            state.endReached ||
            homeTab != ChatHomeTab.Rooms ||
            roomSearchQuery.isNotBlank() ||
            state.rooms.isEmpty(),
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ChatRoomSummaryRow(
            state = state,
            selectedTab = homeTab,
            onTabSelected = { homeTab = it },
            onRefresh = onRefresh,
            onCreateRoom = onCreateRoom,
        )
        state.roomManagementMessage?.let { message ->
            ChatStatusRow(
                text = message,
                loading = state.isManagingRoom,
            )
        }
        state.specialCareToast?.let { toast ->
            ChatSpecialCareToast(
                toast = toast,
                onOpen = onOpenSpecialCareToast,
                onDismiss = onDismissSpecialCareToast,
            )
        }
        HhhlDivider()
        if (homeTab == ChatHomeTab.Rooms) {
            ChatRoomSearchPanel(
                query = roomSearchQuery,
                onQueryChanged = { roomSearchQuery = it },
                totalRoomCount = state.rooms.size,
                visibleRoomCount = visibleRooms.size,
                unreadRoomCount = unreadRoomCount,
                totalUnreadCount = totalUnreadCount,
            )
        } else {
            ChatUserSearchPanel(
                query = userSearchQuery,
                onQueryChanged = { userSearchQuery = it },
                totalUserCount = state.userConversations.size,
                visibleUserCount = visibleUserConversations.size,
                unreadUserCount = state.userConversations.count { it.unreadCount > 0 },
                totalUnreadCount = state.userConversations.sumOf { it.unreadCount.coerceAtLeast(0) },
            )
        }
        HhhlDivider()
        LazyColumn(state = homeListState) {
            if (state.isLoading && state.rooms.isEmpty() && state.userConversations.isEmpty()) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在加载聊天...", loading = true)
                }
            }
            state.errorMessage?.let { message ->
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = message,
                        actionText = if (state.chatAvailable) "重试" else null,
                        onAction = if (state.chatAvailable) onRefresh else null,
                    )
                }
            }
            if (
                homeTab == ChatHomeTab.Rooms &&
                !state.isLoading &&
                state.rooms.isEmpty() &&
                state.errorMessage == null
            ) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = if (state.chatAvailable) "还没有加入的聊天室" else "实例未启用聊天",
                    )
                }
            }
            if (
                homeTab == ChatHomeTab.Rooms &&
                roomSearchQuery.isNotBlank() &&
                visibleRooms.isEmpty() &&
                state.rooms.isNotEmpty() &&
                state.errorMessage == null
            ) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "没有匹配的聊天室")
                }
            }
            if (
                homeTab == ChatHomeTab.Users &&
                !state.isLoading &&
                state.userConversations.isEmpty() &&
                state.errorMessage == null
            ) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = if (state.chatAvailable) "还没有单聊记录" else "实例未启用聊天")
                }
            }
            if (
                homeTab == ChatHomeTab.Users &&
                userSearchQuery.isNotBlank() &&
                visibleUserConversations.isEmpty() &&
                state.userConversations.isNotEmpty() &&
                state.errorMessage == null
            ) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "没有匹配的用户")
                }
            }
            if (homeTab == ChatHomeTab.Rooms) {
                items(
                    items = visibleRooms,
                    key = { it.membershipId },
                    contentType = { ChatListContentType.Room },
                ) { room ->
                    ChatRoomRow(
                        room = room,
                        isPinned = room.id in state.pinnedRoomIds,
                        onClick = { onOpenRoom(room) },
                        onTogglePinned = { onToggleRoomPinned(room.id) },
                    )
                    HhhlDivider()
                }
            } else {
                items(
                    items = visibleUserConversations,
                    key = { it.user.id },
                    contentType = { ChatListContentType.UserConversation },
                ) { conversation ->
                    ChatUserConversationRow(
                        conversation = conversation,
                        currentUserId = currentUserId,
                        isPinned = conversation.user.id in state.pinnedUserConversationIds,
                        onClick = { onOpenUserConversation(conversation) },
                        onTogglePinned = { onToggleUserConversationPinned(conversation.user.id) },
                        onDeleteConversation = { onDeleteUserConversation(conversation.user.id) },
                    )
                    HhhlDivider()
                }
            }
            if (homeTab == ChatHomeTab.Rooms && roomSearchQuery.isBlank() && state.rooms.isNotEmpty() && !state.endReached) {
                item(contentType = ChatListContentType.Status) {
                    if (state.isLoadingMore) {
                        ChatStatusRow(
                            text = "正在加载更多...",
                            loading = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomSummaryRow(
    state: ChatUiState,
    selectedTab: ChatHomeTab,
    onTabSelected: (ChatHomeTab) -> Unit,
    onRefresh: () -> Unit,
    onCreateRoom: (String, String) -> Unit,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    val titleText = if (state.chatAvailable) "已加入的聊天室" else "聊天不可用"
    val stateText = when {
        state.isLoading -> "正在同步聊天室列表"
        state.isLoadingMore -> "正在加载更多聊天室"
        state.rooms.isEmpty() -> "暂无会话"
        else -> "${state.rooms.size} 个会话"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$titleText · $stateText",
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
                HhhlIconActionButton(
                    icon = Icons.Filled.Add,
                    contentDescription = if (state.isManagingRoom) "处理中" else "新建聊天室",
                    enabled = state.chatAvailable && !state.isManagingRoom,
                    onClick = { createDialogOpen = true },
                )
                HhhlIconActionButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = if (state.isLoading || state.isLoadingMore) "同步中" else "刷新聊天",
                    emphasized = true,
                    enabled = state.chatAvailable && !state.isLoading && !state.isLoadingMore,
                    onClick = onRefresh,
                )
            }
        }
        HhhlSegmentedControl(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            HhhlSegmentedItem(
                label = "聊天室 ${state.rooms.size}",
                selected = selectedTab == ChatHomeTab.Rooms,
                onClick = { onTabSelected(ChatHomeTab.Rooms) },
                modifier = Modifier.weight(1f),
                selectedUsesPrimary = true,
            )
            HhhlSegmentedItem(
                label = "用户 ${state.userConversations.size}",
                selected = selectedTab == ChatHomeTab.Users,
                onClick = { onTabSelected(ChatHomeTab.Users) },
                modifier = Modifier.weight(1f),
                selectedUsesPrimary = true,
            )
        }
    }
    if (createDialogOpen) {
        ChatRoomEditDialog(
            title = "新建聊天室",
            confirmText = "创建",
            initialName = "",
            initialDescription = "",
            isSaving = state.isManagingRoom,
            onDismiss = { createDialogOpen = false },
            onSubmit = { name, description ->
                onCreateRoom(name, description)
                createDialogOpen = false
            },
        )
    }
}

@Composable
private fun ChatRoomSearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    totalRoomCount: Int,
    visibleRoomCount: Int,
    unreadRoomCount: Int,
    totalUnreadCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = "搜索聊天室、成员、简介",
            singleLine = true,
            minHeight = 40.dp,
            verticalPadding = 8.dp,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = LocalHhhlColors.current.subtleText,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatOverviewPill(
                label = if (query.isBlank()) "全部 $totalRoomCount" else "匹配 $visibleRoomCount/$totalRoomCount",
                icon = Icons.Filled.Search,
                modifier = Modifier.weight(1f),
            )
            ChatOverviewPill(
                label = if (totalUnreadCount > 0) "未读 $totalUnreadCount" else "无未读",
                icon = Icons.Filled.Person,
                modifier = Modifier.weight(1f),
                emphasized = unreadRoomCount > 0,
            )
        }
    }
}

@Composable
private fun ChatUserSearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    totalUserCount: Int,
    visibleUserCount: Int,
    unreadUserCount: Int,
    totalUnreadCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = "搜索用户、用户名、最近消息",
            singleLine = true,
            minHeight = 40.dp,
            verticalPadding = 8.dp,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = LocalHhhlColors.current.subtleText,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatOverviewPill(
                label = if (query.isBlank()) "全部 $totalUserCount" else "匹配 $visibleUserCount/$totalUserCount",
                icon = Icons.Filled.Search,
                modifier = Modifier.weight(1f),
            )
            ChatOverviewPill(
                label = if (totalUnreadCount > 0) "未读 $totalUnreadCount" else "无未读",
                icon = Icons.Filled.Person,
                modifier = Modifier.weight(1f),
                emphasized = unreadUserCount > 0,
            )
        }
    }
}

@Composable
private fun ChatOverviewPill(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val containerColor = hhhlNeutralControlContainerColor(selected = emphasized)
    val borderColor = hhhlNeutralControlBorderColor(selected = emphasized)
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        LocalHhhlColors.current.subtleText
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRoomRow(
    room: ChatRoom,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = rememberChatPresslessInteractionSource()
    val unreadCount = room.unreadCount.coerceAtLeast(0)
    val hasUnread = unreadCount > 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPinned) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatRoomAvatar(
                room = room,
                unreadCount = unreadCount,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = room.name.ifBlank { "聊天室" },
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPinned) {
                        ChatPinnedBadge()
                    }
                    ChatRoomMuteGlyph(isMuted = room.isMuted)
                }
                if (room.description.isNotBlank()) {
                    Text(
                        text = room.description,
                        color = if (hasUnread) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f)
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${room.memberCount} 位成员 · ${room.joinMode.toDisplayJoinMode()}",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 46.dp, max = 96.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ChatConversationTimeText(text = room.latestMessageAtLabel)
                if (hasUnread) {
                    ChatRoomUnreadCountBadge(unreadCount = unreadCount)
                }
            }
        }
        ChatConversationContextMenu(
            expanded = menuExpanded,
            isPinned = isPinned,
            includeDelete = false,
            onDismiss = { menuExpanded = false },
            onTogglePinned = onTogglePinned,
            onDelete = {},
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatUserConversationRow(
    conversation: ChatUserConversation,
    currentUserId: String?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteConversation: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = rememberChatPresslessInteractionSource()
    val unreadCount = conversation.unreadCount.coerceAtLeast(0)
    val hasUnread = unreadCount > 0
    val latestMessage = conversation.latestMessage
    val sentByMe = latestMessage?.fromUser?.id == currentUserId
    val latestMessageAtLabel = latestMessage?.createdAtLabel.orEmpty()
    val preview = latestMessage
        ?.let { message -> chatUserConversationPreview(message, sentByMe) }
        ?: "开始对话"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPinned) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Avatar(
                    initial = conversation.user.avatarInitial,
                    avatarUrl = conversation.user.avatarUrl,
                    avatarDecorations = conversation.user.avatarDecorations,
                )
                if (hasUnread) {
                    ChatAvatarUnreadBadge(
                        unreadCount = unreadCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.user.displayName.ifBlank { conversation.user.username },
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPinned) {
                        ChatPinnedBadge()
                    }
                }
                Text(
                    text = preview,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f)
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${conversation.user.username}${conversation.user.host?.let { "@$it" }.orEmpty()}",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.widthIn(min = 46.dp, max = 96.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ChatConversationTimeText(text = latestMessageAtLabel)
                if (hasUnread) {
                    ChatRoomUnreadCountBadge(unreadCount = unreadCount)
                }
            }
        }
        ChatConversationContextMenu(
            expanded = menuExpanded,
            isPinned = isPinned,
            includeDelete = true,
            onDismiss = { menuExpanded = false },
            onTogglePinned = onTogglePinned,
            onDelete = onDeleteConversation,
        )
    }
}

@Composable
private fun ChatConversationTimeText(
    text: String,
) {
    Text(
        text = text.ifBlank { " " },
        color = LocalHhhlColors.current.subtleText,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
    )
}

@Composable
private fun ChatConversationContextMenu(
    expanded: Boolean,
    isPinned: Boolean,
    includeDelete: Boolean,
    onDismiss: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = 12.dp, y = (-4).dp),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .widthIn(min = 188.dp, max = 232.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        ChatConversationContextMenuItem(
            label = if (isPinned) "取消置顶" else "置顶",
            icon = Icons.Filled.PushPin,
            onClick = {
                onDismiss()
                onTogglePinned()
            },
        )
        if (includeDelete) {
            ChatConversationContextMenuItem(
                label = "删除对话",
                icon = Icons.Filled.Delete,
                destructive = true,
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ChatConversationContextMenuItem(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.86f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (destructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.07f)
                } else {
                    Color.Transparent
                },
            ),
    )
}

@Composable
private fun ChatPinnedBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "置顶",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatRoomUnreadCountBadge(
    unreadCount: Int,
    modifier: Modifier = Modifier,
) {
    val label = if (unreadCount > 99) "99+" else unreadCount.toString()
    val badgeBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.error.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.84f),
        ),
    )
    Box(
        modifier = modifier
            .height(22.dp)
            .widthIn(min = if (unreadCount > 99) 34.dp else if (unreadCount > 9) 30.dp else 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(badgeBrush)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.30f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "$label 条未读消息" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private object ChatListContentType {
    const val Room = "chat-room"
    const val UserConversation = "chat-user-conversation"
    const val Message = "chat-message"
    const val MessageSearchResult = "chat-message-search-result"
    const val Member = "chat-member"
    const val Status = "chat-status"
}

private enum class ChatComposerPanel {
    Attachment,
    Emoji,
}

@Composable
private fun ChatRoomAvatar(
    room: ChatRoom,
    unreadCount: Int,
) {
    Box {
        Avatar(
            initial = room.owner.avatarInitial,
            avatarUrl = room.owner.avatarUrl,
            avatarDecorations = room.owner.avatarDecorations,
        )
        if (unreadCount > 0) {
            ChatAvatarUnreadBadge(
                unreadCount = unreadCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp),
            )
        }
    }
}

@Composable
private fun ChatAvatarUnreadBadge(
    unreadCount: Int,
    modifier: Modifier = Modifier,
) {
    val badgeBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.error.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.84f),
        ),
    )
    Box(
        modifier = modifier
            .height(18.dp)
            .widthIn(min = if (unreadCount > 99) 28.dp else if (unreadCount > 9) 24.dp else 18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(badgeBrush)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.30f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = if (unreadCount > 9) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatRoomMuteGlyph(isMuted: Boolean) {
    Text(
        text = if (isMuted) "🔇" else "🔈",
        color = LocalHhhlColors.current.subtleText.copy(alpha = if (isMuted) 0.78f else 0.42f),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.semantics {
            contentDescription = if (isMuted) "已静音" else "未静音"
        },
    )
}

@Composable
private fun ChatDetailScreen(
    room: ChatRoom?,
    userConversation: ChatUserConversation?,
    state: ChatUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSearchMessages: (String) -> Unit,
    onLoadMoreMessageSearch: () -> Unit,
    onShowMessages: () -> Unit,
    onShowMembers: () -> Unit,
    onLoadMoreMembers: () -> Unit,
    onUpdateRoom: (String, String) -> Unit,
    onInviteRoomMember: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    onDeleteRoom: () -> Unit,
    onMuteRoom: (Boolean) -> Unit,
    onMessageDraftChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onQuoteMessage: (String) -> Unit,
    onReplyMessage: (String) -> Unit,
    onCancelQuoteMessage: () -> Unit,
    onReactMessage: (String, String) -> Unit,
    onUnreactMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onReportMessage: (String) -> Unit,
    onAddMedia: () -> Unit,
    onAddFile: () -> Unit,
    onOpenDrivePicker: () -> Unit,
    onRemoveAttachedFile: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenSpecialCareToast: () -> Unit,
    onDismissSpecialCareToast: () -> Unit,
    onSpecialCareJumpHandled: () -> Unit,
    onUnreadJumpHandled: () -> Unit,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    isMediaPickerAvailable: Boolean,
    customTheme: HhhlCustomTheme,
    currentUserId: String?,
) {
    val conversationKey = room?.id ?: userConversation?.user?.id ?: "chat"
    val title = room?.name?.ifBlank { "聊天室" }
        ?: userConversation?.user?.displayName?.ifBlank { userConversation.user.username }
        ?: "聊天"
    val supportingText = if (room != null) {
        "${chatDetailStatusText(state)} · ${room.owner.displayName}"
    } else {
        listOfNotNull(
            userConversation?.user?.username?.let { "@$it" },
            chatDetailStatusText(state),
        ).joinToString(" · ")
    }
    var removeAttachedFileDialogOpen by remember { mutableStateOf(false) }
    var composerPanel by remember(conversationKey) { mutableStateOf<ChatComposerPanel?>(null) }
    val attachmentPanelOpen = composerPanel == ChatComposerPanel.Attachment
    val emojiPanelOpen = composerPanel == ChatComposerPanel.Emoji
    val focusManager = LocalFocusManager.current
    fun closeComposerPanel() {
        composerPanel = null
    }
    fun openComposerPanel(panel: ChatComposerPanel) {
        focusManager.clearFocus()
        composerPanel = panel
    }
    fun toggleComposerPanel(panel: ChatComposerPanel) {
        if (composerPanel == panel) {
            closeComposerPanel()
        } else {
            openComposerPanel(panel)
        }
    }
    var showingMessageSearch by remember(conversationKey) { mutableStateOf(false) }
    var memberSearchQuery by remember(conversationKey) { mutableStateOf("") }
    var pendingMessageJumpId by remember(conversationKey) { mutableStateOf<String?>(null) }
    var pendingQuoteJump by remember(conversationKey) { mutableStateOf<ChatRenderedQuote?>(null) }
    var editRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var inviteMemberDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var leaveRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var deleteRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    val messageIndexById = remember(state.messages) {
        state.messages.withIndex().associate { (index, message) -> message.id to index }
    }
    val visibleMembers = remember(state.members, memberSearchQuery) {
        state.members.filterByChatRoomMemberQuery(memberSearchQuery)
    }
    val canRefreshMessages = !state.isLoadingMessages && !state.isLoadingOlderMessages
    val canRefreshMembers = !state.isLoadingMembers && !state.isLoadingMoreMembers
    val canAddMedia = isMediaPickerAvailable && !state.isUploadingMedia
    val canOpenAttachmentPanel = !state.isSendingMessage
    val sendableAttachmentFileIds = remember(state.attachments, state.attachedFile) {
        state.sendableChatAttachmentFileIds()
    }
    val attachmentCount = sendableAttachmentFileIds.size
    val hasAttachment = attachmentCount > 0
    val primaryAttachmentFile = state.primaryChatAttachmentFile()
    val refreshCurrentPane = if (state.showingMembers) onShowMembers else onRefresh
    val canManageRoom = room != null && currentUserId != null && currentUserId == room.owner.id
    val canLeaveRoom = room != null && !canManageRoom

    if (showingMessageSearch) {
        ChatMessageSearchScreen(
            title = title,
            messages = state.messages,
            searchResults = state.messageSearchResults,
            searchQuery = state.messageSearchQuery,
            canLoadOlderMessages = !state.messagesEndReached,
            isLoadingMessages = state.isLoadingMessages,
            isLoadingOlderMessages = state.isLoadingOlderMessages,
            isSearchingMessages = state.isSearchingMessages,
            isLoadingMoreSearch = state.isLoadingMoreMessageSearch,
            canLoadMoreSearch = !state.messageSearchEndReached,
            messageErrorMessage = state.messageErrorMessage,
            searchErrorMessage = state.messageSearchErrorMessage,
            onBack = { showingMessageSearch = false },
            onRefresh = onRefresh,
            onLoadOlderMessages = onLoadOlderMessages,
            onSearch = onSearchMessages,
            onLoadMoreSearch = onLoadMoreMessageSearch,
            onSelectMessage = { messageId ->
                showingMessageSearch = false
                pendingMessageJumpId = messageId
                onShowMessages()
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = title,
            supportingText = supportingText,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlOverflowMenu(
                    actions = chatDetailSummaryActions(
                        showingMembers = state.showingMembers,
                        isUploadingMedia = state.isUploadingMedia,
                        hasAttachment = hasAttachment,
                        canRefreshCurrent = if (state.showingMembers) canRefreshMembers else canRefreshMessages,
                        canAddMedia = canAddMedia,
                        isManagingRoom = state.isManagingRoom,
                        isMuted = room?.isMuted == true,
                        onRefresh = {
                            closeComposerPanel()
                            refreshCurrentPane()
                        },
                        onAddMedia = {
                            closeComposerPanel()
                            onAddMedia()
                        },
                        onSearchMessages = {
                            closeComposerPanel()
                            showingMessageSearch = true
                        },
                        onEditRoom = {
                            closeComposerPanel()
                            editRoomDialogOpen = true
                        },
                        onInviteMember = {
                            closeComposerPanel()
                            inviteMemberDialogOpen = true
                        },
                        onLeaveRoom = {
                            closeComposerPanel()
                            leaveRoomDialogOpen = true
                        },
                        onDeleteRoom = {
                            closeComposerPanel()
                            deleteRoomDialogOpen = true
                        },
                        onToggleMute = {
                            closeComposerPanel()
                            room?.let { onMuteRoom(!it.isMuted) }
                        },
                        canManageRoom = canManageRoom,
                        canLeaveRoom = canLeaveRoom,
                        canShowMembers = room != null,
                    ),
                )
            },
        )
        HhhlDivider()
        state.roomManagementMessage?.let { message ->
            ChatStatusRow(
                text = message,
                loading = state.isManagingRoom,
            )
        }
        if (room != null) {
            ChatDetailModeBar(
                showingMembers = state.showingMembers,
                messageCount = state.messages.size,
                memberCount = state.members.size,
                onShowMessages = {
                    closeComposerPanel()
                    onShowMessages()
                },
                onShowMembers = {
                    closeComposerPanel()
                    onShowMembers()
                },
            )
        }
        if (state.showingMembers) {
            HhhlDivider()
            ChatMemberSearchPanel(
                query = memberSearchQuery,
                onQueryChanged = { memberSearchQuery = it },
                totalMemberCount = state.members.size,
                visibleMemberCount = visibleMembers.size,
                onClear = { memberSearchQuery = "" },
            )
        }
        HhhlDivider()
        if (state.showingMembers) {
            ChatRoomMembersList(
                state = state,
                visibleMembers = visibleMembers,
                searchQuery = memberSearchQuery,
                onRefresh = onShowMembers,
                onLoadMoreMembers = onLoadMoreMembers,
                modifier = Modifier.weight(1f),
            )
        } else {
            val messageListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            var previousLatestMessageId by remember(conversationKey) { mutableStateOf<String?>(null) }
            var scrollToLatestAfterSend by remember(conversationKey) { mutableStateOf(false) }
            var lastOlderLoadAnchorId by remember(conversationKey) { mutableStateOf<String?>(null) }
            var pendingOlderLoadAnchor by remember(conversationKey) { mutableStateOf<ChatOlderLoadAnchor?>(null) }
            val olderLoaderItems = if (!state.messagesEndReached) 1 else 0
            val latestMessageIndex = olderLoaderItems + state.messages.lastIndex
            val showJumpToLatest by remember(messageListState, latestMessageIndex) {
                derivedStateOf {
                    val lastVisibleIndex = messageListState.layoutInfo.visibleItemsInfo
                        .maxOfOrNull { it.index }
                        ?: 0
                    latestMessageIndex >= 0 && latestMessageIndex - lastVisibleIndex >= 3
                }
            }
            val latestMessageId = state.messages.lastOrNull()?.id
            LaunchedEffect(state.isSendingMessage) {
                if (state.isSendingMessage) {
                    scrollToLatestAfterSend = true
                }
            }
            LaunchedEffect(state.messageErrorMessage, state.isSendingMessage) {
                if (!state.isSendingMessage && state.messageErrorMessage != null) {
                    scrollToLatestAfterSend = false
                }
            }
            LaunchedEffect(latestMessageId, state.isLoadingMessages, state.isSendingMessage) {
                val targetMessageId = latestMessageId ?: return@LaunchedEffect
                val shouldForceScrollAfterSend = scrollToLatestAfterSend && !state.isSendingMessage
                if (
                    state.isLoadingMessages ||
                    (!shouldForceScrollAfterSend && state.unreadJumpMessageId != null)
                ) {
                    return@LaunchedEffect
                }

                val targetIndex = olderLoaderItems + state.messages.lastIndex
                if (targetIndex < 0) return@LaunchedEffect

                val shouldAutoScroll = shouldForceScrollAfterSend ||
                    previousLatestMessageId == null ||
                    previousLatestMessageId
                        ?.let { messageIndexById[it] }
                        ?.let { previousIndexInMessages ->
                            val previousIndexInList = olderLoaderItems + previousIndexInMessages
                            messageListState.layoutInfo.visibleItemsInfo.any { it.index == previousIndexInList }
                        }
                        ?: false

                if (shouldAutoScroll) {
                    messageListState.scrollToItem(targetIndex)
                    scrollToLatestAfterSend = false
                }

                previousLatestMessageId = targetMessageId
            }
            LaunchedEffect(state.unreadJumpMessageId, state.messages.size) {
                val targetMessageId = state.unreadJumpMessageId ?: return@LaunchedEffect
                val targetIndexInMessages = messageIndexById[targetMessageId]
                if (targetIndexInMessages != null) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    onUnreadJumpHandled()
                }
            }
            LaunchedEffect(pendingMessageJumpId, state.messages.size, state.isLoadingOlderMessages) {
                val targetMessageId = pendingMessageJumpId ?: return@LaunchedEffect
                val targetIndexInMessages = messageIndexById[targetMessageId]
                if (targetIndexInMessages != null) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    pendingMessageJumpId = null
                } else if (!state.messagesEndReached && !state.isLoadingOlderMessages && !state.isLoadingMessages) {
                    pendingOlderLoadAnchor = messageListState.currentOlderLoadAnchor(
                        messages = state.messages,
                        olderLoaderItems = olderLoaderItems,
                    )
                    onLoadOlderMessages()
                }
            }
            LaunchedEffect(
                state.messages.size,
                state.messagesEndReached,
                state.isLoadingOlderMessages,
                state.isLoadingMessages,
                pendingOlderLoadAnchor?.messageId,
            ) {
                if (
                    state.messages.isEmpty() ||
                    state.messagesEndReached ||
                    state.isLoadingOlderMessages ||
                    state.isLoadingMessages ||
                    pendingOlderLoadAnchor != null
                ) {
                    return@LaunchedEffect
                }
                snapshotFlow { messageListState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { firstVisibleItemIndex ->
                        val anchorMessageId = state.messages.firstOrNull()?.id
                        if (
                            firstVisibleItemIndex <= 1 &&
                            anchorMessageId != null &&
                            anchorMessageId != lastOlderLoadAnchorId &&
                            pendingOlderLoadAnchor == null
                        ) {
                            lastOlderLoadAnchorId = anchorMessageId
                            pendingOlderLoadAnchor = messageListState.currentOlderLoadAnchor(
                                messages = state.messages,
                                olderLoaderItems = olderLoaderItems,
                            )
                            onLoadOlderMessages()
                        }
                    }
            }
            LaunchedEffect(state.messages.size, state.isLoadingOlderMessages, olderLoaderItems) {
                val anchor = pendingOlderLoadAnchor ?: return@LaunchedEffect
                if (state.isLoadingOlderMessages) return@LaunchedEffect
                val anchorIndexInMessages = messageIndexById[anchor.messageId] ?: return@LaunchedEffect
                messageListState.scrollToItem(
                    index = olderLoaderItems + anchorIndexInMessages,
                    scrollOffset = anchor.scrollOffset,
                )
                pendingOlderLoadAnchor = null
            }
            LaunchedEffect(
                state.specialCareJumpMessageId,
                state.messages.size,
                state.messagesEndReached,
                state.isLoadingMessages,
                state.isLoadingOlderMessages,
            ) {
                val targetMessageId = state.specialCareJumpMessageId ?: return@LaunchedEffect
                val targetIndexInMessages = messageIndexById[targetMessageId]
                if (targetIndexInMessages != null) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    onSpecialCareJumpHandled()
                } else if (!state.messagesEndReached && !state.isLoadingOlderMessages && !state.isLoadingMessages) {
                    onLoadOlderMessages()
                } else if (state.messagesEndReached && !state.isLoadingOlderMessages && !state.isLoadingMessages) {
                    onSpecialCareJumpHandled()
                }
            }
            LaunchedEffect(pendingQuoteJump, state.messages.size, state.isLoadingOlderMessages) {
                val quote = pendingQuoteJump ?: return@LaunchedEffect
                val targetIndexInMessages = quote.messageId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { messageIndexById[it] }
                    ?: state.messages.indexOfReferencedQuote(quote)
                if (targetIndexInMessages >= 0) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    pendingQuoteJump = null
                } else if (!state.messagesEndReached && !state.isLoadingOlderMessages && !state.isLoadingMessages) {
                    onLoadOlderMessages()
                }
            }
            val dismissPanelInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        customTheme.chatBackgroundColorHex.toColorOrNull()
                            ?: MaterialTheme.colorScheme.background,
                    )
                    .clickable(
                        enabled = composerPanel != null,
                        indication = null,
                        interactionSource = dismissPanelInteractionSource,
                        onClick = { closeComposerPanel() },
                    ),
            ) {
                if (customTheme.chatBackgroundImageDataUri.isNotBlank()) {
                    AsyncImage(
                        model = customTheme.chatBackgroundImageDataUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.58f)),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = messageListState,
                ) {
                    if (state.isLoadingMessages && state.messages.isEmpty()) {
                        item(contentType = ChatListContentType.Status) {
                            ChatStatusRow(text = "正在加载消息...", loading = true)
                        }
                    }
                    state.messageErrorMessage?.let { message ->
                        item(contentType = ChatListContentType.Status) {
                            ChatStatusRow(
                                text = message,
                                actionText = "重试",
                                onAction = onRefresh,
                            )
                        }
                    }
                    if (!state.isLoadingMessages && state.messages.isEmpty() && state.messageErrorMessage == null) {
                        item(contentType = ChatListContentType.Status) {
                            ChatStatusRow(text = "还没有消息")
                        }
                    }
                    if (state.messages.isNotEmpty() && !state.messagesEndReached) {
                        item(contentType = ChatListContentType.Status) {
                            if (state.isLoadingOlderMessages) {
                                ChatStatusRow(
                                    text = "正在加载更早消息...",
                                    loading = true,
                                )
                            }
                        }
                    }
                    items(
                        items = state.messages,
                        key = { it.id },
                        contentType = { ChatListContentType.Message },
                    ) { message ->
                        ChatMessageRow(
                            message = message,
                            reactionOptions = state.reactionOptions,
                            customEmojis = customEmojis,
                            recentEmojiCodes = recentEmojiCodes,
                            isReactionPending = state.pendingMessageReactionIds.contains(message.id),
                            alignment = chatMessageAlignment(message, currentUserId),
                            onQuote = onQuoteMessage,
                            onReply = onReplyMessage,
                            onReact = onReactMessage,
                            onUnreact = onUnreactMessage,
                            onDelete = onDeleteMessage,
                            onCopy = onCopyMessage,
                            onReport = onReportMessage,
                            currentUserId = currentUserId,
                            onOpenUser = onOpenUser,
                            onOpenUrl = onOpenUrl,
                            onOpenMediaPreview = onOpenMediaPreview,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                            onOpenQuote = { quote -> pendingQuoteJump = quote },
                            onMentionUser = { mention ->
                                onMessageDraftChanged(state.messageDraft.withAppendedChatMention(mention))
                            },
                        )
                    }
                }
                if (showJumpToLatest) {
                    ChatJumpToLatestButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 14.dp),
                        onClick = {
                            if (latestMessageIndex >= 0) {
                                coroutineScope.launch {
                                    messageListState.animateScrollToItem(latestMessageIndex)
                                }
                            }
                        },
                    )
                }
                state.specialCareToast?.let { toast ->
                    ChatSpecialCareJumpButton(
                        toast = toast,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp),
                        onClick = onOpenSpecialCareToast,
                    )
                }
            }
            HhhlDivider()
            state.replyingMessage?.let { reply ->
                ChatQuoteComposerPreview(
                    quote = reply,
                    actionLabel = "回复",
                    cancelLabel = "取消回复",
                    onCancel = onCancelQuoteMessage,
                )
                HhhlDivider()
            }
            state.quotedMessage?.let { quote ->
                ChatQuoteComposerPreview(
                    quote = quote,
                    actionLabel = "引用",
                    cancelLabel = "取消引用",
                    onCancel = onCancelQuoteMessage,
                )
                HhhlDivider()
            }
            primaryAttachmentFile?.let { file ->
                ChatComposerAttachmentPreview(
                    file = file,
                    attachmentCount = attachmentCount,
                    isUploading = state.isUploadingMedia,
                    onRemove = { removeAttachedFileDialogOpen = true },
                )
                HhhlDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatComposerIconButton(
                    label = chatComposerAttachmentActionLabel(
                        isUploadingMedia = state.isUploadingMedia,
                        hasAttachment = hasAttachment,
                    ),
                    icon = Icons.Filled.Add,
                    size = 38.dp,
                    enabled = canOpenAttachmentPanel,
                    selected = attachmentPanelOpen && !emojiPanelOpen,
                    onClick = {
                        if (!canOpenAttachmentPanel) return@ChatComposerIconButton
                        toggleComposerPanel(ChatComposerPanel.Attachment)
                    },
                )
                ChatComposerIconButton(
                    label = "表情",
                    icon = Icons.Filled.EmojiEmotions,
                    size = 38.dp,
                    enabled = canOpenAttachmentPanel,
                    selected = emojiPanelOpen,
                    onClick = {
                        if (!canOpenAttachmentPanel) return@ChatComposerIconButton
                        toggleComposerPanel(ChatComposerPanel.Emoji)
                    },
                )
                HhhlTextInput(
                    value = state.messageDraft,
                    onValueChange = onMessageDraftChanged,
                    placeholder = "发送消息",
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    minHeight = 34.dp,
                    verticalPadding = 6.dp,
                )
                ChatComposerIconButton(
                    label = chatComposerSendActionLabel(state.isSendingMessage),
                    icon = Icons.AutoMirrored.Filled.Send,
                    size = 38.dp,
                    enabled = (state.messageDraft.isNotBlank() || sendableAttachmentFileIds.isNotEmpty()) &&
                        !state.isSendingMessage &&
                        !state.isUploadingMedia,
                    onClick = {
                        closeComposerPanel()
                        onSendMessage()
                    },
                )
            }
            if (attachmentPanelOpen) {
                HhhlDivider()
                ChatAttachmentPanel(
                    canAddMedia = canAddMedia,
                    isUploadingMedia = state.isUploadingMedia,
                    onAddPhoto = {
                        closeComposerPanel()
                        onAddMedia()
                    },
                    onAddFile = {
                        closeComposerPanel()
                        onAddFile()
                    },
                    onOpenDrivePicker = {
                        closeComposerPanel()
                        onOpenDrivePicker()
                    },
                    onOpenEmoji = {
                        openComposerPanel(ChatComposerPanel.Emoji)
                    },
                )
            }
            if (emojiPanelOpen) {
                HhhlDivider()
                ChatEmojiPanel(
                    customEmojis = customEmojis,
                    recentEmojiCodes = recentEmojiCodes,
                    onEmojiSelected = { emoji ->
                        onMessageDraftChanged(state.messageDraft + emoji)
                    },
                )
            }
        }
    }

    if (editRoomDialogOpen && room != null) {
        ChatRoomEditDialog(
            title = "编辑聊天室",
            confirmText = "保存",
            initialName = room.name,
            initialDescription = room.description,
            isSaving = state.isManagingRoom,
            onDismiss = { editRoomDialogOpen = false },
            onSubmit = { name, description ->
                onUpdateRoom(name, description)
                editRoomDialogOpen = false
            },
        )
    }
    if (inviteMemberDialogOpen && room != null) {
        ChatRoomInviteDialog(
            isSaving = state.isManagingRoom,
            onDismiss = { inviteMemberDialogOpen = false },
            onSubmit = { userId ->
                onInviteRoomMember(userId)
                inviteMemberDialogOpen = false
            },
        )
    }
    if (leaveRoomDialogOpen && room != null) {
        ChatRoomConfirmDialog(
            title = "退出聊天室",
            text = "退出后将从已加入聊天室列表中移除。",
            confirmText = "退出",
            onDismiss = { leaveRoomDialogOpen = false },
            onConfirm = {
                onLeaveRoom()
                leaveRoomDialogOpen = false
            },
        )
    }
    if (deleteRoomDialogOpen && room != null) {
        ChatRoomConfirmDialog(
            title = "删除聊天室",
            text = "删除聊天室会移除房间和聊天记录。只有有权限的用户可以执行。",
            confirmText = "删除",
            onDismiss = { deleteRoomDialogOpen = false },
            onConfirm = {
                onDeleteRoom()
                deleteRoomDialogOpen = false
            },
        )
    }
    if (removeAttachedFileDialogOpen) {
        AlertDialog(
            onDismissRequest = { removeAttachedFileDialogOpen = false },
            title = { Text("移除附件") },
            text = {
                Text(
                    text = "附件会从当前消息草稿移除，不会删除云端文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        onRemoveAttachedFile()
                        removeAttachedFileDialogOpen = false
                    },
                    destructive = true,
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { removeAttachedFileDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ChatRoomEditDialog(
    title: String,
    confirmText: String,
    initialName: String,
    initialDescription: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "聊天室名称",
                    singleLine = true,
                )
                HhhlTextInput(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "聊天室描述",
                    minHeight = 72.dp,
                )
            }
        },
        confirmButton = {
            HhhlTextButton(
                enabled = !isSaving && name.isNotBlank(),
                onClick = { onSubmit(name, description) },
            ) {
                Text(if (isSaving) "处理中" else confirmText)
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChatRoomInviteDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var userId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邀请成员") },
        text = {
            HhhlTextInput(
                value = userId,
                onValueChange = { userId = it },
                placeholder = "用户 ID",
                singleLine = true,
            )
        },
        confirmButton = {
            HhhlTextButton(
                enabled = !isSaving && userId.isNotBlank(),
                onClick = { onSubmit(userId) },
            ) {
                Text(if (isSaving) "处理中" else "邀请")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChatRoomConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(
                onClick = onConfirm,
                destructive = confirmText.contains("删除") || confirmText.contains("移除"),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

fun chatDetailStatusText(state: ChatUiState): String {
    return when {
        state.showingMembers && state.isLoadingMembers -> "加载成员中"
        state.showingMembers && state.isLoadingMoreMembers -> "加载更多成员中"
        state.showingMembers -> "${state.members.size} 位成员"
        state.isLoadingMessages -> "加载消息中"
        state.isLoadingOlderMessages -> "加载更早消息中"
        state.isSendingMessage -> "发送消息中"
        else -> "${state.messages.size} 条消息"
    }
}

fun chatDetailSummaryActions(
    showingMembers: Boolean,
    isUploadingMedia: Boolean,
    hasAttachment: Boolean,
    canRefreshCurrent: Boolean,
    canAddMedia: Boolean,
    isManagingRoom: Boolean = false,
    isMuted: Boolean = false,
    onRefresh: () -> Unit,
    onAddMedia: () -> Unit,
    onSearchMessages: () -> Unit = {},
    onEditRoom: () -> Unit = {},
    onInviteMember: () -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    onDeleteRoom: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    canManageRoom: Boolean = true,
    canLeaveRoom: Boolean = true,
    canShowMembers: Boolean = true,
): List<HhhlOverflowMenuAction> = buildList {
    add(
        HhhlOverflowMenuAction(
            label = if (showingMembers) "刷新成员" else "刷新消息",
            enabled = canRefreshCurrent,
            onClick = onRefresh,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "搜索消息",
            onClick = onSearchMessages,
        ),
    )
    if (canManageRoom) {
        add(
            HhhlOverflowMenuAction(
                label = "编辑聊天室",
                enabled = !isManagingRoom,
                onClick = onEditRoom,
            ),
        )
        add(
            HhhlOverflowMenuAction(
                label = "邀请成员",
                enabled = !isManagingRoom,
                onClick = onInviteMember,
            ),
        )
    }
    if (canShowMembers) {
        add(
            HhhlOverflowMenuAction(
                label = if (isMuted) "取消静音" else "静音聊天室",
                enabled = !isManagingRoom,
                onClick = onToggleMute,
            ),
        )
    }
    if (canLeaveRoom) {
        add(
            HhhlOverflowMenuAction(
                label = "退出聊天室",
                enabled = !isManagingRoom,
                onClick = onLeaveRoom,
            ),
        )
    }
    if (canManageRoom) {
        add(
            HhhlOverflowMenuAction(
                label = "删除聊天室",
                enabled = !isManagingRoom,
                onClick = onDeleteRoom,
            ),
        )
    }
    add(
        HhhlOverflowMenuAction(
            label = when {
                isUploadingMedia -> "上传中"
                hasAttachment -> "更换附件"
                else -> "添加附件"
            },
            enabled = canAddMedia,
            onClick = onAddMedia,
        ),
    )
}

@Composable
private fun ChatDetailModeBar(
    showingMembers: Boolean,
    messageCount: Int,
    memberCount: Int,
    onShowMessages: () -> Unit,
    onShowMembers: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .border(
                width = 1.dp,
                color = LocalHhhlColors.current.divider.copy(alpha = 0.52f),
                shape = shape,
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatDetailModeItem(
            text = chatDetailModeLabel("消息", messageCount),
            selected = !showingMembers,
            onClick = onShowMessages,
            modifier = Modifier.weight(1f),
        )
        ChatDetailModeItem(
            text = chatDetailModeLabel("成员", memberCount),
            selected = showingMembers,
            onClick = onShowMembers,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChatDetailModeItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = rememberChatPresslessInteractionSource()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                } else {
                    Color.Transparent
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalHhhlColors.current.subtleText
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

fun chatDetailModeLabel(label: String, count: Int): String {
    return if (count > 0) "$label $count" else label
}

@Composable
private fun ChatMessageSearchScreen(
    title: String,
    messages: List<ChatMessage>,
    searchResults: List<ChatMessage>,
    searchQuery: String,
    canLoadOlderMessages: Boolean,
    isLoadingMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    isSearchingMessages: Boolean,
    isLoadingMoreSearch: Boolean,
    canLoadMoreSearch: Boolean,
    messageErrorMessage: String?,
    searchErrorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSearch: (String) -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSelectMessage: (String) -> Unit,
) {
    var query by remember(title) { mutableStateOf("") }
    var dateQuery by remember(title) { mutableStateOf("") }
    val cleanQuery = query.trim()
    val cleanSearchQuery = searchQuery.trim()
    val hasPendingQuery = cleanQuery.isNotBlank() && cleanQuery != cleanSearchQuery
    val baseResults = if (cleanQuery.isBlank()) messages else searchResults
    val results = remember(baseResults, query, dateQuery) {
        if (cleanQuery.isBlank() && dateQuery.isBlank()) {
            emptyList()
        } else if (dateQuery.isBlank()) {
            baseResults
        } else {
            baseResults.filterByChatMessageSearch("", dateQuery)
        }
    }
    val dateSuggestions = remember(messages) {
        messages
            .asReversed()
            .mapNotNull { it.chatMessageSearchDateSuggestion() }
            .distinct()
            .take(6)
    }
    val hasFilter = cleanQuery.isNotBlank() || dateQuery.isNotBlank()
    val canSubmitSearch = cleanQuery.isNotBlank() && !isSearchingMessages
    val searchListState = rememberLazyListState()
    var lastOlderSearchAutoLoadCount by remember(title) { mutableStateOf(0) }

    AutoLoadMoreEffect(
        listState = searchListState,
        itemCount = results.size,
        isLoadingMore = isLoadingMoreSearch ||
            !canLoadMoreSearch ||
            cleanQuery.isBlank() ||
            searchResults.isEmpty(),
        onLoadMore = onLoadMoreSearch,
    )
    AutoLoadMoreEffect(
        listState = searchListState,
        itemCount = results.size,
        isLoadingMore = isLoadingOlderMessages ||
            !canLoadOlderMessages ||
            cleanQuery.isNotBlank() ||
            messages.isEmpty(),
        onLoadMore = {
            if (messages.size != lastOlderSearchAutoLoadCount) {
                lastOlderSearchAutoLoadCount = messages.size
                onLoadOlderMessages()
            }
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "搜索消息",
            supportingText = title,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索",
                    singleLine = true,
                    minHeight = 36.dp,
                    verticalPadding = 6.dp,
                    modifier = Modifier.weight(1f),
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = LocalHhhlColors.current.subtleText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Text(
                    text = if (cleanQuery.isBlank() && dateQuery.isBlank()) "取消" else "搜索",
                    color = if (canSubmitSearch) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalHhhlColors.current.subtleText
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (canSubmitSearch) {
                                onSearch(cleanQuery)
                            } else {
                                query = ""
                                dateQuery = ""
                                onSearch("")
                                onBack()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = dateQuery,
                    onValueChange = { dateQuery = it },
                    placeholder = "日期",
                    singleLine = true,
                    minHeight = 32.dp,
                    verticalPadding = 5.dp,
                    modifier = Modifier.weight(1f),
                )
                if (hasFilter) {
                    ChatComposerIconButton(
                        label = "清除搜索条件",
                        icon = Icons.Filled.Close,
                        onClick = {
                            query = ""
                            dateQuery = ""
                            onSearch("")
                        },
                        size = 32.dp,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HhhlActionChip(
                    label = "全部",
                    emphasized = dateQuery.isBlank(),
                    onClick = { dateQuery = "" },
                )
                dateSuggestions.forEach { suggestion ->
                    HhhlActionChip(
                        label = suggestion,
                        emphasized = dateQuery == suggestion,
                        onClick = { dateQuery = suggestion },
                    )
                }
                if (dateSuggestions.isEmpty() && dateQuery.isBlank()) {
                    listOf("今天", "本月").forEach { label ->
                        HhhlActionChip(
                            label = label,
                            emphasized = false,
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
            Text(
                text = chatMessageSearchSummary(
                    hasFilter = hasFilter,
                    isRemoteSearch = cleanQuery.isNotBlank(),
                    hasPendingQuery = hasPendingQuery,
                    isSearching = isSearchingMessages || isLoadingMoreSearch,
                    resultCount = results.size,
                    loadedCount = if (cleanQuery.isBlank()) messages.size else searchResults.size,
                ),
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = searchListState,
        ) {
            if (isLoadingMessages && messages.isEmpty()) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在加载消息...", loading = true)
                }
            }
            messageErrorMessage?.let { message ->
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            searchErrorMessage?.let { message ->
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = message,
                        actionText = if (cleanQuery.isNotBlank()) "重试搜索" else null,
                        onAction = if (cleanQuery.isNotBlank()) {
                            { onSearch(cleanQuery) }
                        } else {
                            null
                        },
                    )
                }
            }
            if (isSearchingMessages && searchResults.isEmpty()) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在搜索服务器消息...", loading = true)
                }
            }
            if (!hasFilter && messages.isNotEmpty()) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "输入关键词或日期开始搜索")
                }
            }
            if (
                hasFilter &&
                results.isEmpty() &&
                !isSearchingMessages &&
                messageErrorMessage == null &&
                searchErrorMessage == null
            ) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = if (cleanQuery.isNotBlank()) {
                            "没有匹配的服务器消息"
                        } else {
                            "当前已加载消息里没有匹配结果"
                        },
                    )
                }
            }
            if (!isLoadingMessages && messages.isEmpty() && messageErrorMessage == null) {
                item(contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "还没有消息")
                }
            }
            items(
                items = results,
                key = { it.id },
                contentType = { ChatListContentType.MessageSearchResult },
            ) { message ->
                ChatMessageSearchResultRow(
                    message = message,
                    onClick = { onSelectMessage(message.id) },
                )
                HhhlDivider()
            }
            if (messages.isNotEmpty() && canLoadOlderMessages) {
                item(contentType = ChatListContentType.Status) {
                    if (isLoadingOlderMessages) {
                        ChatStatusRow(
                            text = "正在加载更早消息...",
                            loading = true,
                        )
                    }
                }
            }
            if (cleanQuery.isNotBlank() && searchResults.isNotEmpty() && canLoadMoreSearch) {
                item(contentType = ChatListContentType.Status) {
                    if (isLoadingMoreSearch) {
                        ChatStatusRow(
                            text = "正在加载更多搜索结果...",
                            loading = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageSearchResultRow(
    message: ChatMessage,
    onClick: () -> Unit,
) {
    val presentation = remember(message.id, message.text, message.file?.id, message.file?.name) {
        chatMessagePresentation(message)
    }
    val previewText = remember(presentation.body, message.file?.name, message.file?.type) {
        chatMessageSearchPreview(message, presentation)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            initial = message.fromUser.avatarInitial,
            avatarUrl = message.fromUser.avatarUrl,
            avatarDecorations = message.fromUser.avatarDecorations,
            size = 38.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.fromUser.displayName.ifBlank { message.fromUser.username },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = message.createdAtLabel,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                    text = previewText,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            message.file?.let { file ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = file.name.ifBlank { mediaTypeDisplayName(file.type) },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            presentation.quote?.let { quote ->
                Text(
                    text = "引用 ${quote.author}: ${quote.preview}",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChatMemberSearchPanel(
    query: String,
    onQueryChanged: (String) -> Unit,
    totalMemberCount: Int,
    visibleMemberCount: Int,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = "搜索成员昵称、用户名、ID",
            modifier = Modifier.weight(1f),
            singleLine = true,
            minHeight = 38.dp,
            verticalPadding = 7.dp,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = LocalHhhlColors.current.subtleText,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (query.isNotBlank()) {
            Text(
                text = "$visibleMemberCount/$totalMemberCount",
                color = if (visibleMemberCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalHhhlColors.current.subtleText
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 42.dp),
                maxLines = 1,
            )
            ChatComposerIconButton(
                label = "清除成员搜索",
                icon = Icons.Filled.Close,
                onClick = onClear,
                size = 38.dp,
            )
        }
    }
}

@Composable
private fun ChatRoomMembersList(
    state: ChatUiState,
    visibleMembers: List<ChatRoomMember>,
    searchQuery: String,
    onRefresh: () -> Unit,
    onLoadMoreMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val memberListState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = memberListState,
        itemCount = visibleMembers.size,
        isLoadingMore = state.isLoadingMoreMembers ||
            state.membersEndReached ||
            state.members.isEmpty() ||
            searchQuery.isNotBlank(),
        onLoadMore = onLoadMoreMembers,
    )

    LazyColumn(
        modifier = modifier,
        state = memberListState,
    ) {
        if (state.isLoadingMembers && state.members.isEmpty()) {
            item(contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "正在加载成员...", loading = true)
            }
        }
        state.memberErrorMessage?.let { message ->
            item(contentType = ChatListContentType.Status) {
                ChatStatusRow(
                    text = message,
                    actionText = "重试",
                    onAction = onRefresh,
                )
            }
        }
        if (!state.isLoadingMembers && state.members.isEmpty() && state.memberErrorMessage == null) {
            item(contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "还没有成员信息")
            }
        }
        if (
            searchQuery.isNotBlank() &&
            visibleMembers.isEmpty() &&
            state.members.isNotEmpty() &&
            state.memberErrorMessage == null
        ) {
            item(contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "没有匹配的成员")
            }
        }
        items(
            items = visibleMembers,
            key = { it.membershipId },
            contentType = { ChatListContentType.Member },
        ) { member ->
            ChatRoomMemberRow(member)
            HhhlDivider()
        }
        if (state.members.isNotEmpty() && !state.membersEndReached) {
            item(contentType = ChatListContentType.Status) {
                if (state.isLoadingMoreMembers) {
                    ChatStatusRow(
                        text = "正在加载更多成员...",
                        loading = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomMemberRow(member: ChatRoomMember) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = member.user.avatarInitial,
            avatarUrl = member.user.avatarUrl,
            avatarDecorations = member.user.avatarDecorations,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = member.user.displayName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "@${member.user.username} · 加入 ${member.joinedAtLabel}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChatMessageBubbleTail(
    isOutgoing: Boolean,
    fillBrush: Brush,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = ChatMessageBubbleTailWidth, height = ChatMessageBubbleTailHeight)) {
        val strokeWidth = 1.dp.toPx()
        val strokeInset = strokeWidth / 2f
        val left = strokeInset
        val right = size.width - strokeInset
        val top = strokeInset
        val bottom = size.height - strokeInset
        val path = Path().apply {
            if (isOutgoing) {
                moveTo(0f, 0f)
                cubicTo(
                    size.width * 0.24f,
                    size.height * 0.02f,
                    size.width * 0.46f,
                    size.height * 0.28f,
                    size.width * 0.58f,
                    size.height * 0.48f,
                )
                cubicTo(
                    size.width * 0.72f,
                    size.height * 0.70f,
                    size.width * 0.88f,
                    size.height * 0.80f,
                    size.width,
                    size.height * 0.76f,
                )
                cubicTo(
                    size.width * 0.70f,
                    size.height * 1.02f,
                    size.width * 0.26f,
                    size.height * 0.94f,
                    0f,
                    size.height,
                )
            } else {
                moveTo(size.width, 0f)
                cubicTo(
                    size.width * 0.76f,
                    size.height * 0.02f,
                    size.width * 0.54f,
                    size.height * 0.28f,
                    size.width * 0.42f,
                    size.height * 0.48f,
                )
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.70f,
                    size.width * 0.12f,
                    size.height * 0.80f,
                    0f,
                    size.height * 0.76f,
                )
                cubicTo(
                    size.width * 0.30f,
                    size.height * 1.02f,
                    size.width * 0.74f,
                    size.height * 0.94f,
                    size.width,
                    size.height,
                )
            }
            close()
        }
        drawPath(path = path, brush = fillBrush)
        val borderPath = Path().apply {
            if (isOutgoing) {
                moveTo(left, top)
                cubicTo(
                    size.width * 0.24f,
                    top,
                    size.width * 0.46f,
                    size.height * 0.28f,
                    size.width * 0.58f,
                    size.height * 0.48f,
                )
                cubicTo(
                    size.width * 0.72f,
                    size.height * 0.70f,
                    size.width * 0.88f,
                    size.height * 0.80f,
                    right,
                    size.height * 0.76f,
                )
                cubicTo(
                    size.width * 0.70f,
                    bottom,
                    size.width * 0.26f,
                    size.height * 0.94f,
                    left,
                    bottom,
                )
            } else {
                moveTo(right, top)
                cubicTo(
                    size.width * 0.76f,
                    top,
                    size.width * 0.54f,
                    size.height * 0.28f,
                    size.width * 0.42f,
                    size.height * 0.48f,
                )
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.70f,
                    size.width * 0.12f,
                    size.height * 0.80f,
                    left,
                    size.height * 0.76f,
                )
                cubicTo(
                    size.width * 0.30f,
                    bottom,
                    size.width * 0.74f,
                    size.height * 0.94f,
                    right,
                    bottom,
                )
            }
        }
        drawPath(
            path = borderPath,
            color = borderColor,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun chatDarkMessageBubbleColor(isOutgoing: Boolean): Color {
    return if (isOutgoing) {
        LocalHhhlColors.current.cardBackground.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    reactionOptions: List<String>,
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    isReactionPending: Boolean,
    alignment: ChatMessageAlignment,
    onQuote: (String) -> Unit,
    onReply: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onUnreact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String) -> Unit,
    onReport: (String) -> Unit,
    currentUserId: String?,
    onOpenUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenQuote: (ChatRenderedQuote) -> Unit,
    onMentionUser: (cc.hhhl.client.model.User) -> Unit,
) {
    val presslessInteractionSource = rememberChatPresslessInteractionSource()
    val menuReactionOptions = remember(reactionOptions) { reactionOptions.chatMessageMenuReactionOptions() }
    val isOutgoing = alignment == ChatMessageAlignment.Outgoing
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var reactionPickerExpanded by remember(message.id) { mutableStateOf(false) }
    val presentation = remember(message.id, message.text, message.file?.id, message.file?.name) {
        chatMessagePresentation(message)
    }
    val overflowActions = remember(
        message.id,
        menuReactionOptions,
        isReactionPending,
        isOutgoing,
        onReply,
        onQuote,
        onReact,
        onDelete,
        onCopy,
        onReport,
    ) {
        chatMessageOverflowActions(
            messageId = message.id,
            reactionOptions = menuReactionOptions,
            isReactionPending = isReactionPending,
            isOutgoing = isOutgoing,
            onReply = onReply,
            onQuote = onQuote,
            onOpenReactionPicker = { reactionPickerExpanded = true },
            onDelete = onDelete,
            onCopy = { messageId ->
                clipboardManager.setText(AnnotatedString(chatMessageCopyText(message)))
                onCopy(messageId)
            },
            onReport = onReport,
        )
    }
    val bubbleShape = remember(isOutgoing) {
        if (isOutgoing) {
            RoundedCornerShape(19.dp, 9.dp, 19.dp, 19.dp)
        } else {
            RoundedCornerShape(9.dp, 19.dp, 19.dp, 19.dp)
        }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val inputBackgroundColor = LocalHhhlColors.current.inputBackground
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    val darkIncomingBubbleColor = chatDarkMessageBubbleColor(isOutgoing = false)
    val bubbleBrush = remember(
        isOutgoing,
        primaryColor,
        surfaceColor,
        inputBackgroundColor,
        isDarkSurface,
        darkIncomingBubbleColor,
    ) {
        if (isOutgoing) {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = if (isDarkSurface) 0.98f else 0.96f),
                    primaryColor.copy(alpha = if (isDarkSurface) 0.92f else 0.88f),
                    primaryColor.copy(alpha = if (isDarkSurface) 0.86f else 0.82f),
                )
            )
        } else if (isDarkSurface) {
            SolidColor(darkIncomingBubbleColor)
        } else {
            Brush.verticalGradient(
                listOf(
                    surfaceColor.copy(alpha = 1.00f),
                    surfaceColor.copy(alpha = 0.94f),
                    inputBackgroundColor.copy(alpha = 0.84f),
                ),
            )
        }
    }
    val bubbleBorderColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isDarkSurface) 0.20f else 0.16f)
    } else if (isDarkSurface) {
        Color.White.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.66f)
    }
    val bubbleContentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val bubbleMetaColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    } else {
        LocalHhhlColors.current.subtleText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = if (isOutgoing) {
            Arrangement.spacedBy(10.dp, Alignment.End)
        } else {
            Arrangement.spacedBy(10.dp)
        },
        verticalAlignment = Alignment.Top,
    ) {
        if (!isOutgoing) {
            Box(
                modifier = Modifier.combinedClickable(
                    interactionSource = presslessInteractionSource,
                    indication = null,
                    onClick = { onOpenUser(message.fromUser.id) },
                    onLongClick = { onMentionUser(message.fromUser) },
                ),
            ) {
                Avatar(
                    initial = message.fromUser.avatarInitial,
                    avatarUrl = message.fromUser.avatarUrl,
                    avatarDecorations = message.fromUser.avatarDecorations,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .shadow(
                            elevation = if (isDarkSurface) 1.dp else 3.dp,
                            shape = bubbleShape,
                            clip = false,
                        )
                        .clip(bubbleShape)
                        .background(bubbleBrush)
                        .border(
                            width = 1.dp,
                            color = bubbleBorderColor,
                            shape = bubbleShape,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!isOutgoing) {
                                Text(
                                    text = chatMiddleEllipsize(
                                        value = message.fromUser.displayName.ifBlank { message.fromUser.username },
                                        maxLength = 16,
                                    ),
                                    color = bubbleContentColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.clickable(
                                        interactionSource = presslessInteractionSource,
                                        indication = null,
                                    ) { onOpenUser(message.fromUser.id) },
                                )
                            }
                            Text(
                                text = message.createdAtLabel,
                                color = bubbleMetaColor,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                            )
                        }
                        Box {
                            HhhlOverflowMenu(
                                actions = overflowActions,
                                label = "消息操作",
                                buttonContainerColor = if (isOutgoing) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isDarkSurface) 0.16f else 0.12f)
                                } else {
                                    null
                                },
                                iconTint = if (isOutgoing) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                                } else {
                                    null
                                },
                            )
                            ChatMessageReactionMenu(
                                customEmojis = customEmojis,
                                recentEmojiCodes = recentEmojiCodes,
                                expanded = reactionPickerExpanded,
                                isOutgoing = isOutgoing,
                                onDismiss = { reactionPickerExpanded = false },
                                onSelectReaction = { reaction ->
                                    reactionPickerExpanded = false
                                    val existingReaction = message.reactions.firstOrNull { it.reaction == reaction }
                                    if (existingReaction?.isReactedBy(currentUserId) == true) {
                                        onUnreact(message.id, reaction)
                                    } else {
                                        onReact(message.id, reaction)
                                    }
                                },
                            )
                        }
                    }
                    presentation.quote?.let { quote ->
                        ChatMessageQuoteBlock(
                            quote = quote,
                            isOutgoing = isOutgoing,
                            onClick = { onOpenQuote(quote) },
                        )
                    }
                    message.reply?.let { reference ->
                        ChatMessageReferenceBlock(
                            label = "回复",
                            reference = reference,
                            isOutgoing = isOutgoing,
                            onClick = { onOpenQuote(reference.toRenderedQuote()) },
                        )
                    } ?: if (message.replyUnavailable) {
                        ChatMessageMissingReferenceBlock(label = "回复", isOutgoing = isOutgoing)
                    } else {
                        null
                    }
                    message.quote?.let { reference ->
                        ChatMessageReferenceBlock(
                            label = "引用",
                            reference = reference,
                            isOutgoing = isOutgoing,
                            onClick = { onOpenQuote(reference.toRenderedQuote()) },
                        )
                    } ?: if (message.quoteUnavailable) {
                        ChatMessageMissingReferenceBlock(label = "引用", isOutgoing = isOutgoing)
                    } else {
                        null
                    }
                    InlineRichText(
                        text = presentation.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bubbleContentColor,
                        accentColor = if (isOutgoing) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        onOpenUrl = onOpenUrl,
                        onOpenMention = onOpenMention,
                        onOpenHashtag = onOpenHashtag,
                    )
                    message.file?.let { file ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DriveFilePreview(
                                file = file,
                                onOpenUrl = { openUrl ->
                                    val session = driveFileMediaPreviewSession(
                                        files = listOf(file),
                                        selectedId = file.id,
                                    )
                                    if (session.items.isNotEmpty() && onOpenMediaPreview != null) {
                                        onOpenMediaPreview(session)
                                    } else {
                                        onOpenUrl(openUrl)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp),
                            )
                            Text(
                                text = mediaTypeDisplayName(file.type, file.name),
                                color = bubbleMetaColor,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                ChatMessageBubbleTail(
                    isOutgoing = isOutgoing,
                    fillBrush = bubbleBrush,
                    borderColor = bubbleBorderColor,
                    modifier = Modifier
                        .align(if (isOutgoing) Alignment.TopEnd else Alignment.TopStart)
                        .offset(
                            x = if (isOutgoing) {
                                ChatMessageBubbleTailWidth - ChatMessageBubbleTailEdgeOverlap
                            } else {
                                -ChatMessageBubbleTailWidth + ChatMessageBubbleTailEdgeOverlap
                            },
                            y = 9.dp,
                        ),
                )
            }
            if (message.reactionCount > 0) {
                ChatMessageReactionStrip(
                    message = message,
                    isOutgoing = isOutgoing,
                    currentUserId = currentUserId,
                    isReactionPending = isReactionPending,
                    onReact = onReact,
                    onUnreact = onUnreact,
                )
            }
        }
        if (isOutgoing) {
            Avatar(
                initial = message.fromUser.avatarInitial,
                avatarUrl = message.fromUser.avatarUrl,
                avatarDecorations = message.fromUser.avatarDecorations,
                modifier = Modifier.clickable(
                    interactionSource = presslessInteractionSource,
                    indication = null,
                ) { onOpenUser(message.fromUser.id) },
            )
        }
    }
}

@Composable
private fun ChatMessageReactionStrip(
    message: ChatMessage,
    isOutgoing: Boolean,
    currentUserId: String?,
    isReactionPending: Boolean,
    onReact: (String, String) -> Unit,
    onUnreact: (String, String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.reactions.forEach { reaction ->
            ChatMessageReactionChip(
                reaction = reaction,
                isOutgoing = isOutgoing,
                enabled = !isReactionPending,
                onClick = {
                    if (reaction.isReactedBy(currentUserId)) {
                        onUnreact(message.id, reaction.reaction)
                    } else {
                        onReact(message.id, reaction.reaction)
                    }
                },
            )
        }
    }
}

@Composable
private fun ChatMessageReactionMenu(
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    expanded: Boolean,
    isOutgoing: Boolean,
    onDismiss: () -> Unit,
    onSelectReaction: (String) -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    Popup(
        onDismissRequest = onDismiss,
        alignment = if (isOutgoing) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(
            x = if (isOutgoing) with(density) { -292.dp.roundToPx() } else 0,
            y = with(density) { 40.dp.roundToPx() },
        ),
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .width(292.dp)
                .height(318.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = LocalHhhlColors.current.divider.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(20.dp),
                ),
        ) {
            CustomEmojiPicker(
                customEmojis = customEmojis,
                recentEmojiCodes = recentEmojiCodes,
                onEmojiSelected = onSelectReaction,
                modifier = Modifier.fillMaxWidth(),
                maxPerCategory = 28,
                compact = true,
            )
        }
    }
}

@Composable
private fun ChatMessageReactionMenuButton(
    reaction: String,
    selected: Boolean,
    enabled: Boolean,
    isOutgoing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(if (selected) 2.dp else 1.dp, shape, clip = false)
            .clip(shape)
            .background(
                when {
                    !enabled -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.56f)
                    selected && isOutgoing && isDarkSurface -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    selected && isOutgoing -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    isDarkSurface -> MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    selected && isOutgoing && isDarkSurface -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    selected && isOutgoing -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    else -> LocalHhhlColors.current.divider.copy(alpha = 0.34f)
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CustomEmojiReactionLabel(reaction = reaction)
    }
}

@Composable
private fun ChatMessageReactionChip(
    reaction: cc.hhhl.client.model.ChatMessageReaction,
    isOutgoing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else if (isDarkSurface) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                } else {
                    LocalHhhlColors.current.inputBackground.copy(alpha = 0.86f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        CustomEmojiReactionLabel(reaction = reaction.reaction)
        Text(
            text = reaction.reactionSummaryLabel(),
            color = if (isOutgoing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatMessageReferenceBlock(
    label: String,
    reference: cc.hhhl.client.model.ChatMessageReference,
    isOutgoing: Boolean,
    onClick: () -> Unit,
) {
    ChatMessageReferenceShell(
        label = label,
        preview = reference.toReferencePreviewText(),
        isOutgoing = isOutgoing,
        onClick = onClick,
    )
}

@Composable
private fun ChatMessageMissingReferenceBlock(
    label: String,
    isOutgoing: Boolean,
) {
    ChatMessageReferenceShell(
        label = label,
        preview = "原消息不可用",
        isOutgoing = isOutgoing,
    )
}

@Composable
private fun ChatMessageReferenceShell(
    label: String,
    preview: String,
    isOutgoing: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.36f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else if (isDarkSurface) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.34f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    Color.White.copy(alpha = 0.06f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                } else if (isDarkSurface) {
                    Color.White.copy(alpha = 0.055f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)
                },
                shape = shape,
            )
            .let { modifier ->
                if (onClick != null) {
                    modifier.clickable(onClick = onClick)
                } else {
                    modifier
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = preview,
            color = if (isOutgoing) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            } else {
                LocalHhhlColors.current.subtleText
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatJumpToLatestButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = rememberChatPresslessInteractionSource()
    val shape = RoundedCornerShape(999.dp)
    val surface = chatFloatingButtonSurfaceColors()
    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(8.dp, shape, clip = false, ambientColor = surface.shadow, spotColor = surface.shadow)
            .clip(shape)
            .background(surface.container)
            .border(
                width = 1.dp,
                color = surface.border,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "跳到最新消息" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = surface.content,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ChatSpecialCareJumpButton(
    toast: SpecialCareChatToast,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = rememberChatPresslessInteractionSource()
    val shape = RoundedCornerShape(999.dp)
    val surface = chatFloatingButtonSurfaceColors()
    Row(
        modifier = modifier
            .shadow(8.dp, shape, clip = false, ambientColor = surface.shadow, spotColor = surface.shadow)
            .clip(shape)
            .background(surface.container)
            .border(
                width = 1.dp,
                color = surface.border,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = "跳到特别关心消息" }
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .widthIn(max = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "💗",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Text(
            text = toast.displayName,
            color = surface.content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun chatFloatingButtonSurfaceColors(): ChatFloatingButtonSurfaceColors {
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    return ChatFloatingButtonSurfaceColors(
        container = if (isDarkSurface) {
            Color(0xFF171A1F).copy(alpha = 0.86f)
        } else {
            Color.White.copy(alpha = 0.88f)
        },
        border = if (isDarkSurface) {
            Color.White.copy(alpha = 0.13f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
        },
        content = if (isDarkSurface) {
            Color.White.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f)
        },
        shadow = if (isDarkSurface) {
            Color.Black.copy(alpha = 0.38f)
        } else {
            Color.Black.copy(alpha = 0.16f)
        },
    )
}

private data class ChatFloatingButtonSurfaceColors(
    val container: Color,
    val border: Color,
    val content: Color,
    val shadow: Color,
)

@Composable
private fun ChatQuoteComposerPreview(
    quote: ChatMessageQuote,
    actionLabel: String,
    cancelLabel: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = chatQuoteComposerTitle(actionLabel, quote),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = quote.previewText,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
        ChatComposerIconButton(
            label = cancelLabel,
            icon = Icons.Filled.Close,
            onClick = onCancel,
        )
    }
}

@Composable
private fun ChatMessageQuoteBlock(
    quote: ChatRenderedQuote,
    isOutgoing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.18f
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val supportingColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    } else {
        LocalHhhlColors.current.subtleText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.36f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else if (isDarkSurface) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.34f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    Color.White.copy(alpha = 0.06f)
                } else if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                } else if (isDarkSurface) {
                    Color.White.copy(alpha = 0.055f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)
                },
                shape = shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isOutgoing && isDarkSurface) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                    } else if (isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.58f)
                    } else if (isDarkSurface) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                    },
                ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = quote.author,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = quote.preview,
                color = supportingColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ChatComposerAttachmentPreview(
    file: cc.hhhl.client.model.DriveFile,
    attachmentCount: Int,
    isUploading: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (attachmentCount > 1) "已附加 $attachmentCount 个文件" else "已附加文件",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = file.name.ifBlank { "附件" },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (isUploading) "上传处理中" else mediaTypeDisplayName(file.type, file.name),
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HhhlActionChip(
            label = "移除",
            onClick = onRemove,
        )
    }
}

@Composable
private fun ChatAttachmentPanel(
    canAddMedia: Boolean,
    isUploadingMedia: Boolean,
    onAddPhoto: () -> Unit,
    onAddFile: () -> Unit,
    onOpenDrivePicker: () -> Unit,
    onOpenEmoji: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.34f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        ChatAttachmentAction(
            label = "照片",
            icon = Icons.Filled.Image,
            enabled = canAddMedia,
            disabledLabel = if (isUploadingMedia) "上传中" else null,
            onClick = onAddPhoto,
        )
        ChatAttachmentAction(
            label = "文件",
            icon = Icons.Filled.AttachFile,
            enabled = canAddMedia,
            disabledLabel = if (isUploadingMedia) "上传中" else null,
            onClick = onAddFile,
        )
        ChatAttachmentAction(
            label = "Drive",
            icon = Icons.Filled.Folder,
            enabled = !isUploadingMedia,
            onClick = onOpenDrivePicker,
        )
        ChatAttachmentAction(
            label = "表情",
            icon = Icons.Filled.EmojiEmotions,
            onClick = onOpenEmoji,
        )
    }
}

@Composable
private fun ChatAttachmentAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    disabledLabel: String? = null,
) {
    Column(
        modifier = modifier
            .width(46.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.92f else 0.52f)
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
                    },
                    shape = RoundedCornerShape(11.dp),
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> LocalHhhlColors.current.subtleText
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = if (!enabled) disabledLabel ?: label else label,
            color = if (enabled) MaterialTheme.colorScheme.secondary else LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatEmojiPanel(
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    onEmojiSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CustomEmojiPicker(
            customEmojis = customEmojis,
            recentEmojiCodes = recentEmojiCodes,
            onEmojiSelected = onEmojiSelected,
            modifier = Modifier.weight(1f),
            maxPerCategory = 24,
        )
    }
}

fun chatComposerEmojiOptions(): List<String> = commonEmojiOptions

@Composable
private fun ChatComposerIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val backgroundColor = when {
        !enabled -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.56f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val iconTint = when {
        !enabled -> LocalHhhlColors.current.subtleText
        emphasized -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(if (enabled) 1.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .semantics { contentDescription = label }
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                } else {
                    LocalHhhlColors.current.divider.copy(alpha = 0.28f)
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ChatSpecialCareToast(
    toast: SpecialCareChatToast,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "特别关心 · ${toast.displayName}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = toast.previewText,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChatComposerIconButton(
            label = "关闭特别关心提醒",
            icon = Icons.Filled.Close,
            onClick = onDismiss,
        )
    }
}

data class ChatRenderedQuote(
    val messageId: String?,
    val author: String,
    val preview: String,
)

data class ChatRenderedMessage(
    val quote: ChatRenderedQuote?,
    val body: String,
)

fun chatMessagePresentation(message: ChatMessage): ChatRenderedMessage {
    val raw = chatMessageBodyText(message)
    val messageId = raw.lineSequence()
        .mapNotNull { it.trim().chatQuoteMarkerMessageIdOrNull() }
        .firstOrNull()
        ?: return ChatRenderedMessage(null, raw)
    val firstLineEnd = raw.indexOf('\n').takeIf { it >= 0 } ?: raw.length
    val first = raw.substring(0, firstLineEnd).trim()
    if (!first.startsWith(">")) return ChatRenderedMessage(null, raw)

    val quoteLine = first.removePrefix(">").trim()
    val parts = quoteLine.split(":", limit = 2)
    val author = parts.firstOrNull()?.trim().orEmpty().ifBlank { "引用" }
    val preview = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "引用消息" }
    val remaining = if (firstLineEnd < raw.length) raw.substring(firstLineEnd + 1) else ""
    var bodyStarted = false
    val bodyBuilder = StringBuilder()
    remaining.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.chatQuoteMarkerMessageIdOrNull() != null) {
            return@forEach
        }
        if (!bodyStarted && line.isBlank()) return@forEach
        if (bodyStarted) bodyBuilder.append('\n')
        bodyBuilder.append(line)
        bodyStarted = true
    }
    val body = bodyBuilder.toString().ifBlank { preview }
    return ChatRenderedMessage(
        quote = ChatRenderedQuote(messageId = messageId, author = author, preview = preview),
        body = body,
    )
}

fun chatQuoteComposerTitle(
    actionLabel: String,
    quote: ChatMessageQuote,
): String {
    return "$actionLabel ${quote.authorName}"
}

private fun androidx.compose.foundation.lazy.LazyListState.currentOlderLoadAnchor(
    messages: List<ChatMessage>,
    olderLoaderItems: Int,
): ChatOlderLoadAnchor? {
    if (messages.isEmpty()) return null
    val firstVisibleMessageItem = layoutInfo.visibleItemsInfo
        .firstOrNull { item ->
            val messageIndex = item.index - olderLoaderItems
            messageIndex in messages.indices
        }
        ?: return null
    val messageIndex = firstVisibleMessageItem.index - olderLoaderItems
    val message = messages.getOrNull(messageIndex) ?: return null
    return ChatOlderLoadAnchor(
        messageId = message.id,
        scrollOffset = -firstVisibleMessageItem.offset,
    )
}

private fun androidx.compose.foundation.lazy.LazyListState.centeredChatJumpOffset(): Int {
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return -(viewportHeight * 0.34f).toInt()
}

fun chatComposerAttachmentActionLabel(
    isUploadingMedia: Boolean,
    hasAttachment: Boolean,
): String {
    return when {
        isUploadingMedia -> "上传中"
        hasAttachment -> "更换附件"
        else -> "更多"
    }
}

fun chatComposerSendActionLabel(isSendingMessage: Boolean): String {
    return if (isSendingMessage) "发送中" else "发送"
}

fun chatSpecialCareToastMessage(
    displayName: String,
    isSpecialCare: Boolean,
): String {
    val name = displayName.ifBlank { "该用户" }
    return if (isSpecialCare) {
        "已将 $name 设为特别关心"
    } else {
        "已取消对 $name 的特别关心"
    }
}

fun chatMessageOverflowActions(
    messageId: String,
    reactionOptions: List<String>,
    isReactionPending: Boolean,
    isOutgoing: Boolean = false,
    onReply: (String) -> Unit = {},
    onQuote: (String) -> Unit,
    onOpenReactionPicker: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onReport: (String) -> Unit = {},
): List<HhhlOverflowMenuAction> = buildList {
    add(
        HhhlOverflowMenuAction(
            label = "回复",
            onClick = { onReply(messageId) },
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "引用",
            onClick = { onQuote(messageId) },
        ),
    )
    val menuReactions = reactionOptions.chatMessageMenuReactionOptions()
    if (isReactionPending) {
        add(
            HhhlOverflowMenuAction(
                label = "回应处理中",
                enabled = false,
                onClick = {},
            ),
        )
    } else {
        if (menuReactions.isNotEmpty()) {
            add(
                HhhlOverflowMenuAction(
                    label = "回应",
                    onClick = { onOpenReactionPicker(messageId) },
                ),
            )
        }
    }
    add(
        HhhlOverflowMenuAction(
            label = "复制",
            onClick = { onCopy(messageId) },
        ),
    )
    if (isOutgoing) {
        add(
            HhhlOverflowMenuAction(
                label = "删除",
                icon = Icons.Filled.Delete,
                destructive = true,
                onClick = { onDelete(messageId) },
            ),
        )
    }
    if (!isOutgoing) {
        add(
            HhhlOverflowMenuAction(
                label = "举报",
                destructive = true,
                onClick = { onReport(messageId) },
            ),
        )
    }
}

private fun List<String>.chatMessageMenuReactionOptions(): List<String> {
    return mapNotNull { reaction -> reaction.trim().takeIf { it.isNotEmpty() } }
        .distinct()
        .ifEmpty { listOf("❤️", "👍", "🎉", "😆", "😮", "😢") }
}

fun chatMessageCopyText(message: ChatMessage): String {
    return message.text.takeIf { it.isNotBlank() }
        ?: message.file?.name?.takeIf { it.isNotBlank() }
        ?: "附件消息"
}

fun String.withAppendedChatMention(user: cc.hhhl.client.model.User): String {
    val name = user.displayName.trim().ifBlank { user.username.trim() }.ifBlank { return this }
    val mention = "@$name "
    if (isBlank()) return mention
    return trimEnd() + " " + mention
}

private fun cc.hhhl.client.model.ChatMessageReaction.reactionSummaryLabel(): String {
    val names = users
        .map { user -> user.displayName.ifBlank { user.username } }
        .filter { it.isNotBlank() }
        .distinct()
    return when {
        names.isEmpty() -> count.toString()
        names.size == 1 && count == 1 -> names.single()
        names.size == 1 -> "${names.single()} $count"
        else -> "${names.first()} 等 $count"
    }
}

private fun cc.hhhl.client.model.ChatMessageReaction.isReactedBy(currentUserId: String?): Boolean {
    if (currentUserId.isNullOrBlank()) return false
    return users.any { it.id == currentUserId }
}

private fun cc.hhhl.client.model.ChatMessageReference.toReferencePreviewText(): String {
    val author = fromUser?.displayName?.takeIf { it.isNotBlank() }
        ?: fromUser?.username?.takeIf { it.isNotBlank() }
    val body = text.trim().takeIf { it.isNotBlank() }
        ?: file?.name?.takeIf { it.isNotBlank() }
        ?: "附件"
    return if (author.isNullOrBlank()) body else "$author: $body"
}

private fun cc.hhhl.client.model.ChatMessageReference.toRenderedQuote(): ChatRenderedQuote {
    val author = fromUser?.displayName?.takeIf { it.isNotBlank() }
        ?: fromUser?.username?.takeIf { it.isNotBlank() }
        ?: "引用"
    val preview = text.trim().takeIf { it.isNotBlank() }
        ?: file?.name?.takeIf { it.isNotBlank() }
        ?: "附件"
    return ChatRenderedQuote(
        messageId = id.takeIf { it.isNotBlank() },
        author = author,
        preview = preview,
    )
}

enum class ChatMessageAlignment {
    Incoming,
    Outgoing,
}

fun chatMessageAlignment(
    message: ChatMessage,
    currentUserId: String?,
): ChatMessageAlignment {
    return if (!currentUserId.isNullOrBlank() && message.fromUser.id == currentUserId) {
        ChatMessageAlignment.Outgoing
    } else {
        ChatMessageAlignment.Incoming
    }
}

@Composable
private fun ChatStatusRow(
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

private fun String.toDisplayJoinMode(): String {
    return when (this) {
        "open" -> "开放加入"
        "invite" -> "邀请加入"
        "approval" -> "审核加入"
        else -> ifBlank { "未知模式" }
    }
}

private fun List<ChatRoom>.filterByChatRoomQuery(query: String): List<ChatRoom> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return this
    return filter { room -> room.matchesChatRoomQuery(cleanQuery) }
}

private fun ChatRoom.matchesChatRoomQuery(query: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        owner.displayName.contains(query, ignoreCase = true) ||
        owner.username.contains(query, ignoreCase = true) ||
        owner.host.orEmpty().contains(query, ignoreCase = true)
}

private fun List<ChatMessage>.filterByChatMessageSearch(
    query: String,
    dateQuery: String,
): List<ChatMessage> {
    val cleanQuery = query.trim()
    val cleanDateQuery = dateQuery.trim()
    if (cleanQuery.isBlank() && cleanDateQuery.isBlank()) return emptyList()
    return filter { message ->
        (cleanQuery.isBlank() || message.matchesChatMessageQuery(cleanQuery)) &&
            (cleanDateQuery.isBlank() || message.matchesChatMessageDateQuery(cleanDateQuery))
    }
}

private fun ChatMessage.matchesChatMessageQuery(query: String): Boolean {
    val presentation = chatMessagePresentation(this)
    return text.contains(query, ignoreCase = true) ||
        presentation.body.contains(query, ignoreCase = true) ||
        presentation.quote?.author.orEmpty().contains(query, ignoreCase = true) ||
        presentation.quote?.preview.orEmpty().contains(query, ignoreCase = true) ||
        fromUser.displayName.contains(query, ignoreCase = true) ||
        fromUser.username.contains(query, ignoreCase = true) ||
        fromUser.host.orEmpty().contains(query, ignoreCase = true) ||
        file?.name.orEmpty().contains(query, ignoreCase = true) ||
        file?.type.orEmpty().contains(query, ignoreCase = true)
}

private fun List<ChatUserConversation>.filterByChatUserConversationQuery(
    query: String,
): List<ChatUserConversation> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return this
    return filter { it.matchesChatUserConversationQuery(cleanQuery) }
}

private fun ChatUserConversation.matchesChatUserConversationQuery(query: String): Boolean {
    val message = latestMessage
    return user.displayName.contains(query, ignoreCase = true) ||
        user.username.contains(query, ignoreCase = true) ||
        user.host.orEmpty().contains(query, ignoreCase = true) ||
        message?.text?.contains(query, ignoreCase = true) == true ||
        message?.file?.name.orEmpty().contains(query, ignoreCase = true)
}

private fun chatUserConversationPreview(
    message: ChatMessage,
    sentByMe: Boolean,
): String {
    val prefix = if (sentByMe) "我：" else ""
    val body = chatMessageBodyText(message).trim()
    val preview = body.ifBlank {
        message.file?.name?.takeIf { it.isNotBlank() } ?: "附件消息"
    }
    return prefix + preview
}

private fun ChatMessage.matchesChatMessageDateQuery(query: String): Boolean {
    return createdAt.contains(query, ignoreCase = true) ||
        createdAtLabel.contains(query, ignoreCase = true)
}

private fun ChatMessage.chatMessageSearchDateSuggestion(): String? {
    val rawDate = createdAt.takeIf { it.length >= 10 }?.take(10)
    if (rawDate != null && rawDate[4] == '-' && rawDate[7] == '-') return rawDate
    return createdAtLabel
        .takeIf { it.length >= 10 }
        ?.take(10)
        ?.takeIf { it[4] == '-' && it[7] == '-' }
}

private fun chatMessageSearchSummary(
    hasFilter: Boolean,
    isRemoteSearch: Boolean,
    hasPendingQuery: Boolean,
    isSearching: Boolean,
    resultCount: Int,
    loadedCount: Int,
): String {
    return when {
        isSearching -> "正在搜索..."
        hasPendingQuery -> "关键词已修改，点击搜索同步服务器"
        hasFilter && isRemoteSearch -> "服务器结果 $resultCount 条 · 已取回 $loadedCount 条"
        hasFilter -> "找到 $resultCount 条 · 已加载 $loadedCount 条消息"
        else -> "已加载 $loadedCount 条消息"
    }
}

private fun chatMessageSearchPreview(
    message: ChatMessage,
    presentation: ChatRenderedMessage,
): String {
    val body = presentation.body.trim()
    if (body.isNotBlank()) return body
    return message.file?.name?.takeIf { it.isNotBlank() }
        ?: message.file?.type?.takeIf { it.isNotBlank() }
        ?: "附件消息"
}

private fun List<ChatRoomMember>.filterByChatRoomMemberQuery(query: String): List<ChatRoomMember> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return this
    return filter { member -> member.matchesChatRoomMemberQuery(cleanQuery) }
}

private fun ChatRoomMember.matchesChatRoomMemberQuery(query: String): Boolean {
    return membershipId.contains(query, ignoreCase = true) ||
        user.id.contains(query, ignoreCase = true) ||
        user.displayName.contains(query, ignoreCase = true) ||
        user.username.contains(query, ignoreCase = true) ||
        user.host.orEmpty().contains(query, ignoreCase = true)
}

private fun List<ChatMessage>.indexOfReferencedQuote(quote: ChatRenderedQuote): Int {
    quote.messageId?.takeIf { it.isNotBlank() }?.let { messageId ->
        val exactIndex = indexOfFirst { it.id == messageId }
        if (exactIndex >= 0) return exactIndex
    }
    val cleanPreview = quote.preview.trim()
    val cleanAuthor = quote.author.trim()
    if (cleanPreview.isBlank()) return -1
    return indexOfFirst { message ->
        val presentation = chatMessagePresentation(message)
        val authorMatches = cleanAuthor.isBlank() ||
            message.fromUser.displayName.equals(cleanAuthor, ignoreCase = true) ||
            message.fromUser.username.equals(cleanAuthor, ignoreCase = true)
        authorMatches && presentation.body.contains(cleanPreview, ignoreCase = true)
    }
}

private fun String.chatQuoteMarkerMessageIdOrNull(): String? {
    val prefix = "<!-- hhhl-chat-quote:"
    val suffix = " -->"
    if (!startsWith(prefix) || !endsWith(suffix)) return null
    return removePrefix(prefix).removeSuffix(suffix).trim().takeIf { it.isNotBlank() }
}

private fun chatMiddleEllipsize(
    value: String,
    maxLength: Int,
): String {
    val clean = value.trim()
    if (maxLength < 4 || clean.length <= maxLength) return clean
    val sideLength = (maxLength - 1) / 2
    val tailLength = maxLength - 1 - sideLength
    return clean.take(sideLength) + "…" + clean.takeLast(tailLength)
}
