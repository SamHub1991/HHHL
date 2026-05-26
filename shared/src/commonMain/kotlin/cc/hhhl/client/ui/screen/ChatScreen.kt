package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.state.ChatUiState
import cc.hhhl.client.state.SpecialCareChatToast
import cc.hhhl.client.state.sendableChatAttachmentFileId
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.CustomEmojiPicker
import cc.hhhl.client.ui.component.CustomEmojiReactionLabel
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.chatMessageBodyText
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession

@Composable
fun ChatScreen(
    state: ChatUiState,
    currentUserId: String? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenRoom: (ChatRoom) -> Unit = {},
    onBackToRooms: () -> Unit = {},
    onRefreshMessages: () -> Unit = {},
    onLoadOlderMessages: () -> Unit = {},
    onShowMessages: () -> Unit = {},
    onShowMembers: () -> Unit = {},
    onLoadMoreMembers: () -> Unit = {},
    onMessageDraftChanged: (String) -> Unit = {},
    onSendMessage: () -> Unit = {},
    onQuoteMessage: (String) -> Unit = {},
    onCancelQuoteMessage: () -> Unit = {},
    onReactMessage: (String, String) -> Unit = { _, _ -> },
    onUnreactMessage: (String, String) -> Unit = { _, _ -> },
    onAddMedia: () -> Unit = {},
    onAddFile: () -> Unit = onAddMedia,
    onRemoveAttachedFile: () -> Unit = {},
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
) {
    val selectedRoom = state.selectedRoom
    if (selectedRoom != null) {
        ChatRoomDetailScreen(
            room = selectedRoom,
            state = state,
            onBack = onBackToRooms,
            onRefresh = onRefreshMessages,
            onLoadOlderMessages = onLoadOlderMessages,
            onShowMessages = onShowMessages,
            onShowMembers = onShowMembers,
            onLoadMoreMembers = onLoadMoreMembers,
            onMessageDraftChanged = onMessageDraftChanged,
            onSendMessage = onSendMessage,
            onQuoteMessage = onQuoteMessage,
            onCancelQuoteMessage = onCancelQuoteMessage,
            onReactMessage = onReactMessage,
            onUnreactMessage = onUnreactMessage,
            onAddMedia = onAddMedia,
            onAddFile = onAddFile,
            onRemoveAttachedFile = onRemoveAttachedFile,
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
            currentUserId = currentUserId,
        )
        return
    }

    var roomSearchQuery by remember { mutableStateOf("") }
    val visibleRooms = remember(state.rooms, roomSearchQuery) {
        state.rooms.filterByChatRoomQuery(roomSearchQuery)
    }
    val unreadRoomCount = remember(state.rooms) {
        state.rooms.count { it.unreadCount > 0 }
    }
    val totalUnreadCount = remember(state.rooms) {
        state.rooms.sumOf { it.unreadCount.coerceAtLeast(0) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatRoomSummaryRow(
            state = state,
            onRefresh = onRefresh,
        )
        state.specialCareToast?.let { toast ->
            ChatSpecialCareToast(
                toast = toast,
                onOpen = onOpenSpecialCareToast,
                onDismiss = onDismissSpecialCareToast,
            )
        }
        HhhlDivider()
        ChatRoomSearchPanel(
            query = roomSearchQuery,
            onQueryChanged = { roomSearchQuery = it },
            totalRoomCount = state.rooms.size,
            visibleRoomCount = visibleRooms.size,
            unreadRoomCount = unreadRoomCount,
            totalUnreadCount = totalUnreadCount,
        )
        HhhlDivider()
        LazyColumn {
            if (state.isLoading && state.rooms.isEmpty()) {
                item { ChatStatusRow(text = "正在加载聊天...", loading = true) }
            }
            state.errorMessage?.let { message ->
                item {
                    ChatStatusRow(
                        text = message,
                        actionText = if (state.chatAvailable) "重试" else null,
                        onAction = if (state.chatAvailable) onRefresh else null,
                    )
                }
            }
            if (!state.isLoading && state.rooms.isEmpty() && state.errorMessage == null) {
                item {
                    ChatStatusRow(
                        text = if (state.chatAvailable) "还没有加入的聊天室" else "实例未启用聊天",
                    )
                }
            }
            if (
                roomSearchQuery.isNotBlank() &&
                visibleRooms.isEmpty() &&
                state.rooms.isNotEmpty() &&
                state.errorMessage == null
            ) {
                item { ChatStatusRow(text = "没有匹配的聊天室") }
            }
            items(visibleRooms, key = { it.membershipId }) { room ->
                ChatRoomRow(
                    room = room,
                    onClick = { onOpenRoom(room) },
                )
                HhhlDivider()
            }
            if (roomSearchQuery.isBlank() && state.rooms.isNotEmpty() && !state.endReached) {
                item {
                    ChatStatusRow(
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
private fun ChatRoomSummaryRow(
    state: ChatUiState,
    onRefresh: () -> Unit,
) {
    val titleText = if (state.chatAvailable) "已加入的聊天室" else "聊天不可用"
    val stateText = when {
        state.isLoading -> "正在同步聊天室列表"
        state.isLoadingMore -> "正在加载更多聊天室"
        state.rooms.isEmpty() -> "暂无会话"
        else -> "${state.rooms.size} 个会话"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
            HhhlActionChip(
                label = if (state.isLoading || state.isLoadingMore) "同步中" else "刷新聊天室",
                emphasized = true,
                enabled = state.chatAvailable && !state.isLoading && !state.isLoadingMore,
                onClick = onRefresh,
            )
        }
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
private fun ChatOverviewPill(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f)
                },
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasized) MaterialTheme.colorScheme.primary else LocalHhhlColors.current.subtleText,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatRoomRow(
    room: ChatRoom,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = room.owner.avatarInitial,
            avatarUrl = room.owner.avatarUrl,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = room.name.ifBlank { "聊天室" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (room.isMuted) {
                    Text(
                        text = "已静音",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (room.description.isNotBlank()) {
                Text(
                    text = room.description,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
            Text(
                text = "${room.memberCount} 位成员 · ${room.joinMode.toDisplayJoinMode()}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = room.owner.displayName,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
        )
        if (room.unreadCount > 0) {
            ChatUnreadBadge(room.unreadCount)
        }
    }
}

@Composable
private fun ChatUnreadBadge(
    unreadCount: Int,
) {
    Box(
        modifier = Modifier
            .size(if (unreadCount > 99) 28.dp else 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatRoomDetailScreen(
    room: ChatRoom,
    state: ChatUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onShowMessages: () -> Unit,
    onShowMembers: () -> Unit,
    onLoadMoreMembers: () -> Unit,
    onMessageDraftChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onQuoteMessage: (String) -> Unit,
    onCancelQuoteMessage: () -> Unit,
    onReactMessage: (String, String) -> Unit,
    onUnreactMessage: (String, String) -> Unit,
    onAddMedia: () -> Unit,
    onAddFile: () -> Unit,
    onRemoveAttachedFile: () -> Unit,
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
    currentUserId: String?,
) {
    var removeAttachedFileDialogOpen by remember { mutableStateOf(false) }
    var attachmentPanelOpen by remember { mutableStateOf(false) }
    var emojiPanelOpen by remember { mutableStateOf(false) }
    var messageSearchQuery by remember(room.id) { mutableStateOf("") }
    var selectedSearchResultIndex by remember(room.id) { mutableStateOf(0) }
    val messageSearchResultIds = remember(state.messages, messageSearchQuery) {
        state.messages
            .filterByChatMessageQuery(messageSearchQuery)
            .map { it.id }
    }
    val canRefreshMessages = !state.isLoadingMessages && !state.isLoadingOlderMessages
    val canRefreshMembers = !state.isLoadingMembers && !state.isLoadingMoreMembers
    val canAddMedia = isMediaPickerAvailable && !state.isUploadingMedia
    val canOpenAttachmentPanel = !state.isSendingMessage
    val refreshCurrentPane = if (state.showingMembers) onShowMembers else onRefresh
    LaunchedEffect(messageSearchQuery, messageSearchResultIds.size) {
        selectedSearchResultIndex = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = room.name.ifBlank { "聊天室" },
            supportingText = "${chatDetailStatusText(state)} · ${room.owner.displayName}",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlOverflowMenu(
                    actions = chatDetailSummaryActions(
                        showingMembers = state.showingMembers,
                        isUploadingMedia = state.isUploadingMedia,
                        hasAttachment = state.attachedFile != null,
                        canRefreshCurrent = if (state.showingMembers) canRefreshMembers else canRefreshMessages,
                        canAddMedia = canAddMedia,
                        onRefresh = refreshCurrentPane,
                        onShowMessages = onShowMessages,
                        onShowMembers = onShowMembers,
                        onAddMedia = onAddMedia,
                    ),
                )
            },
        )
        HhhlDivider()
        state.specialCareToast?.let { toast ->
            ChatSpecialCareToast(
                toast = toast,
                onOpen = onOpenSpecialCareToast,
                onDismiss = onDismissSpecialCareToast,
            )
            HhhlDivider()
        }
        if (state.selectedRoomUnreadCount > 0 && !state.showingMembers) {
            ChatUnreadToast(
                unreadCount = state.selectedRoomUnreadCount,
                onClick = {
                    // The actual jump is handled by the list effect once messages are loaded.
                },
            )
            HhhlDivider()
        }
        ChatDetailModeBar(
            showingMembers = state.showingMembers,
            messageCount = state.messages.size,
            memberCount = state.members.size,
            onShowMessages = onShowMessages,
            onShowMembers = onShowMembers,
        )
        if (!state.showingMembers) {
            HhhlDivider()
            ChatMessageSearchPanel(
                query = messageSearchQuery,
                onQueryChanged = { messageSearchQuery = it },
                resultCount = messageSearchResultIds.size,
                selectedResultIndex = selectedSearchResultIndex,
                onPrevious = {
                    if (messageSearchResultIds.isNotEmpty()) {
                        selectedSearchResultIndex =
                            (selectedSearchResultIndex - 1 + messageSearchResultIds.size) % messageSearchResultIds.size
                    }
                },
                onNext = {
                    if (messageSearchResultIds.isNotEmpty()) {
                        selectedSearchResultIndex = (selectedSearchResultIndex + 1) % messageSearchResultIds.size
                    }
                },
                onClear = { messageSearchQuery = "" },
            )
        }
        HhhlDivider()
        if (state.showingMembers) {
            ChatRoomMembersList(
                state = state,
                onRefresh = onShowMembers,
                onLoadMoreMembers = onLoadMoreMembers,
                modifier = Modifier.weight(1f),
            )
        } else {
            val messageListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
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
            LaunchedEffect(latestMessageId, state.isLoadingMessages) {
                if (latestMessageId != null && !state.isLoadingMessages && state.unreadJumpMessageId == null) {
                    val targetIndex = olderLoaderItems + state.messages.lastIndex
                    if (targetIndex >= 0 && messageListState.firstVisibleItemIndex == 0) {
                        messageListState.animateScrollToItem(targetIndex)
                    }
                }
            }
            LaunchedEffect(state.unreadJumpMessageId, state.messages.size) {
                val targetMessageId = state.unreadJumpMessageId ?: return@LaunchedEffect
                val targetIndexInMessages = state.messages.indexOfFirst { it.id == targetMessageId }
                if (targetIndexInMessages >= 0) {
                    messageListState.animateScrollToItem(olderLoaderItems + targetIndexInMessages)
                    onUnreadJumpHandled()
                }
            }
            LaunchedEffect(messageSearchResultIds, selectedSearchResultIndex) {
                if (messageSearchQuery.isBlank() || messageSearchResultIds.isEmpty()) {
                    return@LaunchedEffect
                }
                val targetMessageId = messageSearchResultIds.getOrNull(
                    selectedSearchResultIndex.coerceIn(0, messageSearchResultIds.lastIndex),
                ) ?: return@LaunchedEffect
                val targetIndexInMessages = state.messages.indexOfFirst { it.id == targetMessageId }
                if (targetIndexInMessages >= 0) {
                    messageListState.animateScrollToItem(olderLoaderItems + targetIndexInMessages)
                }
            }
            LaunchedEffect(
                state.messages.size,
                state.messagesEndReached,
                state.isLoadingOlderMessages,
                state.isLoadingMessages,
            ) {
                if (
                    state.messages.isEmpty() ||
                    state.messagesEndReached ||
                    state.isLoadingOlderMessages ||
                    state.isLoadingMessages
                ) {
                    return@LaunchedEffect
                }
                snapshotFlow { messageListState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { firstVisibleItemIndex ->
                        if (firstVisibleItemIndex <= 1) {
                            onLoadOlderMessages()
                        }
                    }
            }
            LaunchedEffect(state.specialCareJumpMessageId, state.messages.size) {
                val targetMessageId = state.specialCareJumpMessageId ?: return@LaunchedEffect
                val targetIndexInMessages = state.messages.indexOfFirst { it.id == targetMessageId }
                if (targetIndexInMessages >= 0) {
                    messageListState.animateScrollToItem(olderLoaderItems + targetIndexInMessages)
                    onSpecialCareJumpHandled()
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = messageListState,
                ) {
                    if (state.isLoadingMessages && state.messages.isEmpty()) {
                        item { ChatStatusRow(text = "正在加载消息...", loading = true) }
                    }
                    state.messageErrorMessage?.let { message ->
                        item {
                            ChatStatusRow(
                                text = message,
                                actionText = "重试",
                                onAction = onRefresh,
                            )
                        }
                    }
                    if (!state.isLoadingMessages && state.messages.isEmpty() && state.messageErrorMessage == null) {
                        item { ChatStatusRow(text = "还没有消息") }
                    }
                    if (state.messages.isNotEmpty() && !state.messagesEndReached) {
                        item {
                            ChatStatusRow(
                                text = if (state.isLoadingOlderMessages) "正在加载更早消息..." else "上滑加载更早消息",
                                loading = state.isLoadingOlderMessages,
                            )
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageRow(
                            message = message,
                            reactionOptions = state.reactionOptions,
                            isReactionPending = state.pendingMessageReactionIds.contains(message.id),
                            alignment = chatMessageAlignment(message, currentUserId),
                            onQuote = onQuoteMessage,
                            onReact = onReactMessage,
                            onUnreact = onUnreactMessage,
                            onOpenUrl = onOpenUrl,
                            onOpenMediaPreview = onOpenMediaPreview,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
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
            }
            HhhlDivider()
            state.quotedMessage?.let { quote ->
                ChatQuoteComposerPreview(
                    quote = quote,
                    onCancel = onCancelQuoteMessage,
                )
                HhhlDivider()
            }
            state.attachedFile?.let { file ->
                ChatComposerAttachmentPreview(
                    file = file,
                    isUploading = state.isUploadingMedia,
                    onRemove = { removeAttachedFileDialogOpen = true },
                )
                HhhlDivider()
            }
            if (attachmentPanelOpen) {
                ChatAttachmentPanel(
                    canAddMedia = canAddMedia,
                    isUploadingMedia = state.isUploadingMedia,
                    hasAttachment = state.attachedFile != null,
                    emojiPanelOpen = emojiPanelOpen,
                    onAddPhoto = {
                        emojiPanelOpen = false
                        attachmentPanelOpen = false
                        onAddMedia()
                    },
                    onAddFile = {
                        emojiPanelOpen = false
                        attachmentPanelOpen = false
                        onAddFile()
                    },
                    onToggleEmoji = { emojiPanelOpen = !emojiPanelOpen },
                )
                if (emojiPanelOpen) {
                    ChatEmojiPanel(
                        customEmojis = customEmojis,
                        recentEmojiCodes = recentEmojiCodes,
                        onEmojiSelected = { emoji ->
                            onMessageDraftChanged(state.messageDraft + emoji)
                        },
                    )
                }
                HhhlDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HhhlTextInput(
                    value = state.messageDraft,
                    onValueChange = onMessageDraftChanged,
                    placeholder = "发送消息",
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    minHeight = 40.dp,
                    verticalPadding = 8.dp,
                )
                ChatComposerIconButton(
                    label = chatComposerAttachmentActionLabel(
                        isUploadingMedia = state.isUploadingMedia,
                        hasAttachment = state.attachedFile != null,
                    ),
                    icon = Icons.Filled.Add,
                    enabled = canOpenAttachmentPanel,
                    selected = attachmentPanelOpen,
                    onClick = {
                        if (!canOpenAttachmentPanel) return@ChatComposerIconButton
                        attachmentPanelOpen = !attachmentPanelOpen
                        if (!attachmentPanelOpen) emojiPanelOpen = false
                    },
                )
                ChatComposerIconButton(
                    label = chatComposerSendActionLabel(state.isSendingMessage),
                    icon = Icons.AutoMirrored.Filled.Send,
                    emphasized = true,
                    enabled = (state.messageDraft.isNotBlank() || state.sendableChatAttachmentFileId() != null) &&
                        !state.isSendingMessage &&
                        !state.isUploadingMedia,
                    onClick = onSendMessage,
                )
            }
        }
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
                TextButton(
                    onClick = {
                        onRemoveAttachedFile()
                        removeAttachedFileDialogOpen = false
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { removeAttachedFileDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
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
    onRefresh: () -> Unit,
    onShowMessages: () -> Unit,
    onShowMembers: () -> Unit,
    onAddMedia: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (showingMembers) "刷新成员" else "刷新消息",
        enabled = canRefreshCurrent,
        onClick = onRefresh,
    ),
    HhhlOverflowMenuAction(
        label = "查看消息",
        enabled = showingMembers,
        onClick = onShowMessages,
    ),
    HhhlOverflowMenuAction(
        label = "查看成员",
        enabled = !showingMembers,
        onClick = onShowMembers,
    ),
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

@Composable
private fun ChatDetailModeBar(
    showingMembers: Boolean,
    messageCount: Int,
    memberCount: Int,
    onShowMessages: () -> Unit,
    onShowMembers: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
                shape = RoundedCornerShape(18.dp),
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
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
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
private fun ChatRoomMembersList(
    state: ChatUiState,
    onRefresh: () -> Unit,
    onLoadMoreMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        if (state.isLoadingMembers && state.members.isEmpty()) {
            item { ChatStatusRow(text = "正在加载成员...", loading = true) }
        }
        state.memberErrorMessage?.let { message ->
            item {
                ChatStatusRow(
                    text = message,
                    actionText = "重试",
                    onAction = onRefresh,
                )
            }
        }
        if (!state.isLoadingMembers && state.members.isEmpty() && state.memberErrorMessage == null) {
            item { ChatStatusRow(text = "还没有成员信息") }
        }
        items(state.members, key = { it.membershipId }) { member ->
            ChatRoomMemberRow(member)
            HhhlDivider()
        }
        if (state.members.isNotEmpty() && !state.membersEndReached) {
            item {
                ChatStatusRow(
                    text = if (state.isLoadingMoreMembers) "正在加载更多成员..." else "加载更多成员",
                    loading = state.isLoadingMoreMembers,
                    onAction = if (state.isLoadingMoreMembers) null else onLoadMoreMembers,
                )
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
private fun ChatMessageRow(
    message: ChatMessage,
    reactionOptions: List<String>,
    isReactionPending: Boolean,
    alignment: ChatMessageAlignment,
    onQuote: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onUnreact: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    val defaultReaction = reactionOptions.firstOrNull() ?: "❤️"
    val isOutgoing = alignment == ChatMessageAlignment.Outgoing
    val presentation = chatMessagePresentation(message)
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)
    }
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        LocalHhhlColors.current.inputBackground
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!isOutgoing) {
            Avatar(
                initial = message.fromUser.avatarInitial,
                avatarUrl = message.fromUser.avatarUrl,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .border(
                    width = 1.dp,
                    color = if (isOutgoing) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                    },
                    shape = bubbleShape,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isOutgoing) {
                        Text(
                            text = message.fromUser.displayName,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = message.createdAtLabel,
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HhhlOverflowMenu(
                    actions = chatMessageOverflowActions(
                        messageId = message.id,
                        defaultReaction = defaultReaction,
                        isReactionPending = isReactionPending,
                        onQuote = onQuote,
                        onReact = onReact,
                    ),
                    label = "消息操作",
                )
            }
            presentation.quote?.let { quote ->
                ChatMessageQuoteBlock(
                    quote = quote,
                    isOutgoing = isOutgoing,
                )
            }
            InlineRichText(
                text = presentation.body,
                style = MaterialTheme.typography.bodyMedium,
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
                        text = file.type.ifBlank { "文件" },
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (message.reactionCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    message.reactions.forEach { reaction ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (!isReactionPending) {
                                    onUnreact(message.id, reaction.reaction)
                                }
                            },
                        ) {
                            CustomEmojiReactionLabel(reaction = reaction.reaction)
                            Text(
                                text = reaction.count.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatUnreadToast(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${unreadCount.coerceAtLeast(1)} 条未读",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChatJumpToLatestButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "跳到最新消息" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ChatQuoteComposerPreview(
    quote: ChatMessageQuote,
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = chatQuoteComposerTitle(quote),
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
            label = "取消引用",
            icon = Icons.Filled.Close,
            onClick = onCancel,
        )
    }
}

@Composable
private fun ChatMessageQuoteBlock(
    quote: ChatRenderedQuote,
    isOutgoing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = quote.author,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = quote.preview,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ChatComposerAttachmentPreview(
    file: cc.hhhl.client.model.DriveFile,
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
                text = "已附加文件",
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
                text = if (isUploading) "上传处理中" else file.type.ifBlank { "未知类型" },
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
    hasAttachment: Boolean,
    emojiPanelOpen: Boolean,
    onAddPhoto: () -> Unit,
    onAddFile: () -> Unit,
    onToggleEmoji: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChatAttachmentAction(
            label = if (hasAttachment) "换照片" else "照片",
            supportingText = if (isUploadingMedia) "上传中" else "相册多选",
            icon = Icons.Filled.Image,
            enabled = canAddMedia,
            onClick = onAddPhoto,
        )
        ChatAttachmentAction(
            label = if (hasAttachment) "换文件" else "文件",
            supportingText = if (isUploadingMedia) "上传中" else "本机文件",
            icon = Icons.Filled.AttachFile,
            enabled = canAddMedia,
            onClick = onAddFile,
        )
        ChatAttachmentAction(
            label = "表情",
            supportingText = if (emojiPanelOpen) "收起" else "常用表情",
            icon = Icons.Filled.EmojiEmotions,
            selected = emojiPanelOpen,
            onClick = onToggleEmoji,
        )
    }
}

@Composable
private fun ChatAttachmentAction(
    label: String,
    supportingText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .widthIn(min = 104.dp, max = 148.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.88f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> LocalHhhlColors.current.subtleText
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.size(19.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatEmojiPanel(
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String>,
    onEmojiSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chatComposerEmojiOptions().forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f))
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        CustomEmojiPicker(
            customEmojis = customEmojis,
            recentEmojiCodes = recentEmojiCodes,
            onEmojiSelected = onEmojiSelected,
        )
    }
}

fun chatComposerEmojiOptions(): List<String> = listOf(
    "😀",
    "😂",
    "🥹",
    "😍",
    "😎",
    "😭",
    "😡",
    "👍",
    "🙏",
    "👏",
    "🔥",
    "✨",
    "🎉",
    "❤️",
    "💔",
    "🤔",
    "👀",
    "✅",
)

@Composable
private fun ChatComposerIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val backgroundColor = when {
        !enabled -> LocalHhhlColors.current.inputBackground.copy(alpha = 0.58f)
        emphasized -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> LocalHhhlColors.current.inputBackground
    }
    val iconTint = when {
        !enabled -> LocalHhhlColors.current.subtleText
        emphasized -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(shape)
            .semantics { contentDescription = label }
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (emphasized || selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
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
    val author: String,
    val preview: String,
)

data class ChatRenderedMessage(
    val quote: ChatRenderedQuote?,
    val body: String,
)

fun chatMessagePresentation(message: ChatMessage): ChatRenderedMessage {
    val raw = chatMessageBodyText(message)
    val lines = raw.lines()
    val first = lines.firstOrNull()?.trim().orEmpty()
    if (!first.startsWith(">")) return ChatRenderedMessage(null, raw)

    val quoteLine = first.removePrefix(">").trim()
    val parts = quoteLine.split(":", limit = 2)
    val author = parts.firstOrNull()?.trim().orEmpty().ifBlank { "引用" }
    val preview = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "引用消息" }
    val body = lines.drop(1).dropWhile { it.isBlank() }.joinToString("\n").ifBlank { preview }
    return ChatRenderedMessage(
        quote = ChatRenderedQuote(author = author, preview = preview),
        body = body,
    )
}

fun chatQuoteComposerTitle(quote: ChatMessageQuote): String {
    return "> ${quote.authorName}"
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
    defaultReaction: String,
    isReactionPending: Boolean,
    onQuote: (String) -> Unit,
    onReact: (String, String) -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "引用",
        onClick = { onQuote(messageId) },
    ),
    HhhlOverflowMenuAction(
        label = if (isReactionPending) "回应处理中" else "回应 $defaultReaction",
        enabled = !isReactionPending,
        onClick = { onReact(messageId, defaultReaction) },
    ),
)

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

private fun String.toDisplayJoinMode(): String {
    return when (this) {
        "open" -> "开放加入"
        "invite" -> "邀请加入"
        "approval" -> "审核加入"
        else -> ifBlank { "未知模式" }
    }
}
