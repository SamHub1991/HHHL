package cc.hhhl.client.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import cc.hhhl.client.ai.AiTaskKind
import cc.hhhl.client.model.AvatarDecoration
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatRoom
import cc.hhhl.client.model.CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX
import cc.hhhl.client.model.ChatRoomInvitation
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.ChatUserConversation
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.User
import cc.hhhl.client.model.commonEmojiOptions
import cc.hhhl.client.state.ChatAttentionKind
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
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlDropdownMenu
import cc.hhhl.client.ui.component.HhhlDropdownMenuItem
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
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.api.toApiInstantOrNull
import cc.hhhl.client.ui.component.containsValidMfmSyntax
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession
import cc.hhhl.client.ui.component.hhhlNeutralControlBorderColor
import cc.hhhl.client.ui.component.hhhlNeutralControlContainerColor
import cc.hhhl.client.ui.component.hhhlReadableOnControlColor
import cc.hhhl.client.ui.component.mediaTypeDisplayName
import cc.hhhl.client.presentation.richTextPlainPreviewText
import kotlinx.datetime.Clock

private data class ChatOlderLoadAnchor(
    val messageId: String,
    val scrollOffset: Int,
)

internal data class ChatSearchAuthorFilter(
    val userId: String,
    val displayName: String,
    val username: String,
    val host: String?,
    val avatarInitial: String,
    val avatarUrl: String?,
    val avatarDecorations: List<AvatarDecoration> = emptyList(),
)

private enum class ChatHomeTab {
    Rooms,
    Users,
}

internal data class ChatMessageUiFilterState(
    val hideMfmSyntaxMessages: Boolean = false,
    val hiddenUserIds: Set<String> = emptySet(),
    val hiddenUserDraft: String = "",
    val regexPatterns: List<String> = emptyList(),
    val regexDraft: String = "",
) {
    val activeCount: Int
        get() = (if (hideMfmSyntaxMessages) 1 else 0) + hiddenUserIds.size + regexPatterns.size

    val isActive: Boolean
        get() = activeCount > 0

    fun reset(): ChatMessageUiFilterState = copy(
        hideMfmSyntaxMessages = false,
        hiddenUserIds = emptySet(),
        hiddenUserDraft = "",
        regexPatterns = emptyList(),
        regexDraft = "",
    )

    fun activeRulesOnly(): ChatMessageUiFilterState = if (hiddenUserDraft.isBlank() && regexDraft.isBlank()) {
        this
    } else {
        copy(
            hiddenUserDraft = "",
            regexDraft = "",
        )
    }
}

private const val CHAT_MESSAGE_UI_FILTER_MAX_HIDDEN_USERS = 64
private const val CHAT_MESSAGE_UI_FILTER_MAX_USER_RULE_LENGTH = 128

private const val CHAT_MESSAGE_UI_FILTER_MAX_REGEX_RULES = 24
private const val CHAT_MESSAGE_UI_FILTER_MAX_REGEX_LENGTH = 160
private const val CHAT_MEMBER_ACTIVE_WINDOW_MILLIS = 30 * 60 * 1000L
private const val CHAT_MEMBER_ACTIVE_FALLBACK_MESSAGE_LIMIT = 48
private const val CHAT_MEMBER_ACTIVE_FALLBACK_USER_LIMIT = 8
private const val CHAT_MESSAGE_UI_FILTER_MAX_MATCH_TEXT_LENGTH = 4_096
private const val CHAT_SEARCH_AUTHOR_FILTER_MAX_USERS = 240
private const val CHAT_SEARCH_AUTHOR_MENU_VISIBLE_USERS = 12

private val ChatMessageTelegramTailWidth = 12.dp
private val ChatMessageTelegramTailHeight = 10.dp
private val ChatMessageTelegramTailAnchorY = 8.dp
private val ChatMessageTelegramBubbleRadius = 21.dp
private val ChatMessageIncomingAvatarTopPadding = 12.dp
private val ChatMessageBubbleMaxWidth = 332.dp
private val ChatMessageMetaNameMaxWidth = 176.dp

@Composable
private fun rememberChatPresslessInteractionSource(): MutableInteractionSource {
    return remember { MutableInteractionSource() }
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    currentUserId: String? = null,
    blockedUserIds: Set<String> = emptySet(),
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenRoom: (ChatRoom) -> Unit = {},
    onOpenUserConversation: (ChatUserConversation) -> Unit = {},
    onToggleRoomPinned: (String) -> Unit = {},
    onToggleUserConversationPinned: (String) -> Unit = {},
    onDeleteUserConversation: (String) -> Unit = {},
    onCreateRoom: (String, String) -> Unit = { _, _ -> },
    onRefreshRoomExtras: () -> Unit = {},
    onJoinRoomInvitation: (ChatRoomInvitation) -> Unit = {},
    onIgnoreRoomInvitation: (String) -> Unit = {},
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
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    aiResultText: String? = null,
    aiResultLabel: String? = null,
    onAiAction: (AiTaskKind, ChatUiState, String) -> Unit = { _, _, _ -> },
    onDismissAiResult: () -> Unit = {},
) {
    val selectedRoom = state.selectedRoom
    val selectedUserConversation = state.selectedUserConversation
    if (selectedRoom != null || selectedUserConversation != null) {
        ChatDetailScreen(
            room = selectedRoom,
            userConversation = selectedUserConversation,
            state = state,
            blockedUserIds = blockedUserIds,
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
            aiEnabled = aiEnabled,
            isAiProcessing = isAiProcessing,
            aiResultText = aiResultText,
            aiResultLabel = aiResultLabel,
            onAiAction = onAiAction,
            onDismissAiResult = onDismissAiResult,
        )
        return
    }

    var homeTab by remember { mutableStateOf(ChatHomeTab.Rooms) }
    var roomSearchQuery by remember { mutableStateOf("") }
    var userSearchQuery by remember { mutableStateOf("") }
    val visibleRooms = remember(state.rooms, roomSearchQuery) {
        state.rooms.filterByChatRoomQuery(roomSearchQuery)
    }
    val visibleUserConversations = remember(state.userConversations, blockedUserIds, userSearchQuery) {
        state.userConversations
            .filterNot { conversation -> conversation.user.id in blockedUserIds }
            .filterByChatUserConversationQuery(userSearchQuery)
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
            onRefreshRoomExtras = onRefreshRoomExtras,
        )
        state.roomManagementMessage?.let { message ->
            ChatStatusRow(
                text = message,
                loading = state.isManagingRoom,
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
                item(key = "chat-home-loading", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在加载聊天...", loading = true)
                }
            }
            state.errorMessage?.let { message ->
                item(key = "chat-home-error", contentType = ChatListContentType.Status) {
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
                item(key = "chat-home-rooms-empty", contentType = ChatListContentType.Status) {
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
                item(key = "chat-home-rooms-search-empty", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "没有匹配的聊天室")
                }
            }
            if (
                homeTab == ChatHomeTab.Users &&
                !state.isLoading &&
                state.userConversations.isEmpty() &&
                state.errorMessage == null
            ) {
                item(key = "chat-home-users-empty", contentType = ChatListContentType.Status) {
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
                item(key = "chat-home-users-search-empty", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "没有匹配的用户")
                }
            }
            if (homeTab == ChatHomeTab.Rooms) {
                if (
                    state.roomInvitationInbox.isNotEmpty() ||
                    state.roomInvitationOutbox.isNotEmpty() ||
                    state.ownedRooms.isNotEmpty() ||
                    state.isLoadingRoomExtras
                ) {
                    item(key = "chat-room-extras", contentType = ChatListContentType.Status) {
                        ChatRoomExtrasPanel(
                            state = state,
                            onOpenRoom = onOpenRoom,
                            onJoinRoomInvitation = onJoinRoomInvitation,
                            onIgnoreRoomInvitation = onIgnoreRoomInvitation,
                        )
                        HhhlDivider()
                    }
                }
                itemsIndexed(
                    items = visibleRooms,
                    key = { index, room -> room.stableChatRoomListKey(index) },
                    contentType = { _, _ -> ChatListContentType.Room },
                ) { _, room ->
                    ChatRoomRow(
                        room = room,
                        attentionKind = state.roomAttentionKinds[room.id],
                        isPinned = room.id in state.pinnedRoomIds,
                        onClick = { onOpenRoom(room) },
                        onTogglePinned = { onToggleRoomPinned(room.id) },
                    )
                    HhhlDivider()
                }
            } else {
                itemsIndexed(
                    items = visibleUserConversations,
                    key = { index, conversation -> conversation.stableChatUserConversationListKey(index) },
                    contentType = { _, _ -> ChatListContentType.UserConversation },
                ) { _, conversation ->
                    ChatUserConversationRow(
                        conversation = conversation,
                        currentUserId = currentUserId,
                        attentionKind = state.userConversationAttentionKinds[conversation.user.id],
                        isPinned = conversation.user.id in state.pinnedUserConversationIds,
                        onClick = { onOpenUserConversation(conversation) },
                        onTogglePinned = { onToggleUserConversationPinned(conversation.user.id) },
                        onDeleteConversation = { onDeleteUserConversation(conversation.user.id) },
                    )
                    HhhlDivider()
                }
            }
            if (homeTab == ChatHomeTab.Rooms && roomSearchQuery.isBlank() && state.rooms.isNotEmpty() && !state.endReached) {
                item(key = "chat-home-rooms-loading-more", contentType = ChatListContentType.Status) {
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
    onRefreshRoomExtras: () -> Unit,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
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
                    onClick = {
                        onRefresh()
                        onRefreshRoomExtras()
                    },
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
    val colors = LocalHhhlColors.current
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
                    tint = colors.textMuted,
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
private fun ChatRoomExtrasPanel(
    state: ChatUiState,
    onOpenRoom: (ChatRoom) -> Unit,
    onJoinRoomInvitation: (ChatRoomInvitation) -> Unit,
    onIgnoreRoomInvitation: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isLoadingRoomExtras) {
            Text(
                text = "正在同步邀请和管理的聊天室",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.roomInvitationInbox.isNotEmpty()) {
            ChatExtraSectionTitle(title = "邀请", count = state.roomInvitationInbox.size)
            state.roomInvitationInbox.take(3).forEach { invitation ->
                ChatRoomInvitationRow(
                    invitation = invitation,
                    isManaging = state.isManagingRoom,
                    onJoin = { onJoinRoomInvitation(invitation) },
                    onIgnore = { onIgnoreRoomInvitation(invitation.room.id) },
                )
            }
        }
        if (state.ownedRooms.isNotEmpty()) {
            ChatExtraSectionTitle(title = "我管理的", count = state.ownedRooms.size)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.ownedRooms.take(6).forEach { room ->
                    HhhlActionChip(
                        label = room.name.ifBlank { "聊天室" },
                        onClick = { onOpenRoom(room) },
                    )
                }
            }
        }
        if (state.roomInvitationOutbox.isNotEmpty()) {
            Text(
                text = "已发出 ${state.roomInvitationOutbox.size} 个邀请",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatExtraSectionTitle(
    title: String,
    count: Int,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChatRoomInvitationRow(
    invitation: ChatRoomInvitation,
    isManaging: Boolean,
    onJoin: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.inputBackground.copy(alpha = 0.48f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatRoomAvatar(room = invitation.room, unreadCount = 0)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invitation.room.name.ifBlank { "聊天室" },
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invitation.inviter?.let { "${it.displayName} 邀请你" } ?: "新的聊天室邀请",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HhhlActionChip(
            label = if (isManaging) "处理中" else "加入",
            emphasized = true,
            enabled = !isManaging,
            onClick = onJoin,
        )
        HhhlActionChip(
            label = "忽略",
            enabled = !isManaging,
            onClick = onIgnore,
        )
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
    val colors = LocalHhhlColors.current
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
                    tint = colors.textMuted,
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
    val colors = LocalHhhlColors.current
    val containerColor = hhhlNeutralControlContainerColor(selected = emphasized)
    val borderColor = hhhlNeutralControlBorderColor(selected = emphasized)
    val contentColor = if (emphasized) {
        hhhlReadableOnControlColor(containerColor, colors.accent)
    } else {
        colors.textMuted
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
    attentionKind: ChatAttentionKind?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val interactionSource = rememberChatPresslessInteractionSource()
    val unreadCount = room.unreadCount.coerceAtLeast(0)
    val hasUnread = unreadCount > 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPinned) {
                    colors.buttonSelectedBackground.copy(alpha = 0.48f)
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
                        color = if (hasUnread) colors.accent else colors.textPrimary,
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
                    InlineRichText(
                        text = room.description,
                        color = if (hasUnread) {
                            colors.textPrimary.copy(alpha = 0.74f)
                        } else {
                            colors.textSecondary
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    attentionKind?.let { kind ->
                        ChatAttentionInlineBadge(kind = kind)
                    }
                    Text(
                        text = "${room.memberCount} 位成员 · ${room.joinMode.toDisplayJoinMode()}",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
    attentionKind: ChatAttentionKind?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteConversation: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
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
                    colors.buttonSelectedBackground.copy(alpha = 0.48f)
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
                        color = if (hasUnread) colors.accent else colors.textPrimary,
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
                InlineRichText(
                    text = preview,
                    color = if (hasUnread) {
                        colors.textPrimary.copy(alpha = 0.74f)
                    } else {
                        colors.textSecondary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxChars = 140,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    attentionKind?.let { kind ->
                        ChatAttentionInlineBadge(kind = kind)
                    }
                    Text(
                        text = "@${conversation.user.username}${conversation.user.host?.let { "@$it" }.orEmpty()}",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
    val colors = LocalHhhlColors.current
    Text(
        text = text.ifBlank { " " },
        color = colors.textMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
    )
}

@Composable
private fun ChatAttentionInlineBadge(
    kind: ChatAttentionKind,
) {
    val colors = LocalHhhlColors.current
    val container = colors.accentSoft.copy(alpha = 0.88f)
    val border = colors.focusRing.copy(alpha = 0.24f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = kind.icon,
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = kind.shortLabel,
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
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
    val colors = LocalHhhlColors.current
    HhhlDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = 12.dp, y = (-4).dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = colors.surfaceElevated.copy(alpha = 0.98f),
        borderColor = colors.border.copy(alpha = 0.34f),
        modifier = Modifier
            .widthIn(min = 188.dp, max = 232.dp),
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
    val colors = LocalHhhlColors.current
    val contentColor = if (destructive) colors.danger else colors.textPrimary
    HhhlDropdownMenuItem(
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
        destructive = destructive,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (destructive) {
                    colors.danger.copy(alpha = 0.07f)
                } else {
                    Color.Transparent
                },
            ),
    )
}

@Composable
private fun ChatPinnedBadge() {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.buttonSelectedBackground)
            .border(
                width = 1.dp,
                color = colors.focusRing.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "置顶",
            color = colors.accent,
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
    val colors = LocalHhhlColors.current
    val label = if (unreadCount > 99) "99+" else unreadCount.toString()
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val badgeHeight = 22.dp + ((fontScale - 1f) * 10f).dp
    val minWidth = when {
        unreadCount > 99 -> 34.dp
        unreadCount > 9 -> 30.dp
        else -> 24.dp
    } + ((fontScale - 1f) * 8f).dp
    val badgeBrush = Brush.verticalGradient(
        colors = listOf(
            colors.unreadBadge.copy(alpha = 0.96f),
            colors.unreadBadge.copy(alpha = 0.84f),
        ),
    )
    Box(
        modifier = modifier
            .height(badgeHeight)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(999.dp))
            .background(badgeBrush)
            .border(
                width = 1.dp,
                color = colors.dangerText.copy(alpha = 0.30f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "$label 条未读消息" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.dangerText,
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
    const val MemberHeader = "chat-member-header"
    const val MemberRow = "chat-member-row"
    const val Status = "chat-status"
}

private const val CHAT_ROOM_MEMBERS_PER_ROW = 4

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
    val colors = LocalHhhlColors.current
    val label = if (unreadCount > 99) "99+" else unreadCount.toString()
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
    val badgeHeight = 18.dp + ((fontScale - 1f) * 8f).dp
    val minWidth = when {
        unreadCount > 99 -> 28.dp
        unreadCount > 9 -> 24.dp
        else -> 18.dp
    } + ((fontScale - 1f) * 7f).dp
    val badgeBrush = Brush.verticalGradient(
        colors = listOf(
            colors.unreadBadge.copy(alpha = 0.96f),
            colors.unreadBadge.copy(alpha = 0.84f),
        ),
    )
    Box(
        modifier = modifier
            .height(badgeHeight)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(999.dp))
            .background(badgeBrush)
            .border(
                width = 1.dp,
                color = colors.dangerText.copy(alpha = 0.30f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = if (unreadCount > 9) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.dangerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatRoomMuteGlyph(isMuted: Boolean) {
    val colors = LocalHhhlColors.current
    Text(
        text = if (isMuted) "🔇" else "🔈",
        color = colors.textMuted.copy(alpha = if (isMuted) 0.78f else 0.42f),
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
    blockedUserIds: Set<String>,
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
    aiEnabled: Boolean,
    isAiProcessing: Boolean,
    aiResultText: String?,
    aiResultLabel: String?,
    onAiAction: (AiTaskKind, ChatUiState, String) -> Unit,
    onDismissAiResult: () -> Unit,
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
    var showingMessageFilters by remember(conversationKey) { mutableStateOf(false) }
    var messageUiFilter by remember(conversationKey) { mutableStateOf(ChatMessageUiFilterState()) }
    var memberSearchQuery by remember(conversationKey) { mutableStateOf("") }
    var pendingMessageJumpId by remember(conversationKey) { mutableStateOf<String?>(null) }
    var pendingQuoteJump by remember(conversationKey) { mutableStateOf<ChatRenderedQuote?>(null) }
    var editRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var inviteMemberDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var leaveRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    var deleteRoomDialogOpen by remember(conversationKey) { mutableStateOf(false) }
    val activeMessageUiFilter = remember(
        messageUiFilter.hideMfmSyntaxMessages,
        messageUiFilter.hiddenUserIds,
        messageUiFilter.regexPatterns,
    ) {
        messageUiFilter.activeRulesOnly()
    }
    val compiledFilterRegexes = remember(messageUiFilter.regexPatterns) {
        compileChatMessageUiFilterRegexes(messageUiFilter.regexPatterns)
    }
    val displayBaseMessages = remember(state.messages, blockedUserIds) {
        state.messages.filterNot { message -> message.isHiddenByBlockedChatUser(blockedUserIds) }
    }
    val visibleMessages = remember(displayBaseMessages, activeMessageUiFilter, compiledFilterRegexes) {
        displayBaseMessages
            .filterByChatMessageUiFilter(activeMessageUiFilter, compiledFilterRegexes)
    }
    val filteredMessageCount = displayBaseMessages.size - visibleMessages.size
    val messageIndexById = remember(visibleMessages) {
        visibleMessages.withIndex().associate { (index, message) -> message.id to index }
    }
    val loadedMessageIds = remember(state.messages) {
        state.messages.loadedChatMessageIdSet()
    }
    val loadedMessageFingerprint = remember(state.messages) {
        state.messages.chatMessageIdFingerprint()
    }
    val visibleMessageFingerprint = remember(visibleMessages) {
        visibleMessages.chatMessageIdFingerprint()
    }
    val filterableAuthors = remember(state.messages) {
        state.messages
            .map { it.fromUser }
            .distinctBy { it.id }
            .sortedBy { it.displayName.ifBlank { it.username } }
    }
    val visibleMembers = remember(state.members, memberSearchQuery) {
        state.members.filterByChatRoomMemberQuery(memberSearchQuery)
    }
    val displayBaseSearchResults = remember(state.messageSearchResults, blockedUserIds) {
        state.messageSearchResults.filterNot { message -> message.isHiddenByBlockedChatUser(blockedUserIds) }
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
            messages = displayBaseMessages,
            searchResults = displayBaseSearchResults,
            members = state.members,
            searchQuery = state.messageSearchQuery,
            canLoadOlderMessages = !state.messagesEndReached,
            isLoadingMessages = state.isLoadingMessages,
            isLoadingOlderMessages = state.isLoadingOlderMessages,
            isSearchingMessages = state.isSearchingMessages,
            isLoadingMoreSearch = state.isLoadingMoreMessageSearch,
            canLoadMoreSearch = !state.messageSearchEndReached,
            messageErrorMessage = state.messageErrorMessage,
            searchErrorMessage = state.messageSearchErrorMessage,
            uiFilter = activeMessageUiFilter,
            uiFilterRegexes = compiledFilterRegexes,
            onBack = { showingMessageSearch = false },
            onRefresh = onRefresh,
            onLoadOlderMessages = onLoadOlderMessages,
            onSearch = onSearchMessages,
            onLoadMoreSearch = onLoadMoreMessageSearch,
            onSelectMessage = { messageId ->
                showingMessageSearch = false
                if (messageUiFilter.shouldResetForLoadedHiddenMessage(messageId, loadedMessageIds, messageIndexById)) {
                    messageUiFilter = messageUiFilter.reset()
                }
                pendingMessageJumpId = messageId
                onShowMessages()
            },
            onOpenFilters = {
                showingMessageSearch = false
                showingMessageFilters = true
            },
        )
        return
    }

    if (showingMessageFilters) {
        ChatMessageFilterScreen(
            title = title,
            filter = messageUiFilter,
            filteredMessageCount = filteredMessageCount,
            totalMessageCount = displayBaseMessages.size,
            authors = filterableAuthors,
            onBack = { showingMessageFilters = false },
            onFilterChanged = { messageUiFilter = it },
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
                        aiEnabled = aiEnabled,
                        isAiProcessing = isAiProcessing,
                        onAiSummary = {
                            closeComposerPanel()
                            onAiAction(AiTaskKind.ChatSummary, state, title)
                        },
                        onAiReplyDraft = {
                            closeComposerPanel()
                            onAiAction(AiTaskKind.ChatReplyDraft, state, title)
                        },
                        onAiActionItems = {
                            closeComposerPanel()
                            onAiAction(AiTaskKind.ChatActionItems, state, title)
                        },
                        onAiDecisionSummary = {
                            closeComposerPanel()
                            onAiAction(AiTaskKind.ChatDecisionSummary, state, title)
                        },
                        onOpenFilters = {
                            closeComposerPanel()
                            showingMessageFilters = true
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
            val latestMessageIndex = olderLoaderItems + visibleMessages.lastIndex
            val showJumpToLatest by remember(messageListState, latestMessageIndex) {
                derivedStateOf {
                    val lastVisibleIndex = messageListState.layoutInfo.visibleItemsInfo
                        .maxOfOrNull { it.index }
                        ?: 0
                    latestMessageIndex >= 0 && latestMessageIndex - lastVisibleIndex >= 3
                }
            }
            val latestMessageId = visibleMessages.lastOrNull()?.id
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

                val targetIndex = olderLoaderItems + visibleMessages.lastIndex
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
            LaunchedEffect(state.unreadJumpMessageId, loadedMessageFingerprint, visibleMessageFingerprint) {
                val targetMessageId = state.unreadJumpMessageId ?: return@LaunchedEffect
                if (messageUiFilter.shouldResetForLoadedHiddenMessage(targetMessageId, loadedMessageIds, messageIndexById)) {
                    messageUiFilter = messageUiFilter.reset()
                    return@LaunchedEffect
                }
                val targetIndexInMessages = messageIndexById[targetMessageId]
                if (targetIndexInMessages != null) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    onUnreadJumpHandled()
                }
            }
            LaunchedEffect(pendingMessageJumpId, loadedMessageFingerprint, visibleMessageFingerprint, state.isLoadingOlderMessages) {
                val targetMessageId = pendingMessageJumpId ?: return@LaunchedEffect
                if (messageUiFilter.shouldResetForLoadedHiddenMessage(targetMessageId, loadedMessageIds, messageIndexById)) {
                    messageUiFilter = messageUiFilter.reset()
                    return@LaunchedEffect
                }
                val targetIndexInMessages = messageIndexById[targetMessageId]
                if (targetIndexInMessages != null) {
                    messageListState.animateScrollToItem(
                        index = olderLoaderItems + targetIndexInMessages,
                        scrollOffset = messageListState.centeredChatJumpOffset(),
                    )
                    pendingMessageJumpId = null
                } else if (!state.messagesEndReached && !state.isLoadingOlderMessages && !state.isLoadingMessages) {
                    pendingOlderLoadAnchor = messageListState.currentOlderLoadAnchor(
                        messages = visibleMessages,
                        olderLoaderItems = olderLoaderItems,
                    )
                    onLoadOlderMessages()
                }
            }
            LaunchedEffect(
                loadedMessageFingerprint,
                visibleMessageFingerprint,
                state.messagesEndReached,
                state.isLoadingOlderMessages,
                state.isLoadingMessages,
                pendingOlderLoadAnchor?.messageId,
            ) {
                if (
                    visibleMessages.isEmpty() ||
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
                        val anchorMessageId = visibleMessages.firstOrNull()?.id
                        if (
                            firstVisibleItemIndex <= 1 &&
                            anchorMessageId != null &&
                            anchorMessageId != lastOlderLoadAnchorId &&
                            pendingOlderLoadAnchor == null
                        ) {
                            lastOlderLoadAnchorId = anchorMessageId
                            pendingOlderLoadAnchor = messageListState.currentOlderLoadAnchor(
                                messages = visibleMessages,
                                olderLoaderItems = olderLoaderItems,
                            )
                            onLoadOlderMessages()
                        }
                    }
            }
            LaunchedEffect(visibleMessageFingerprint, state.isLoadingOlderMessages, olderLoaderItems) {
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
                loadedMessageFingerprint,
                visibleMessageFingerprint,
                state.messagesEndReached,
                state.isLoadingMessages,
                state.isLoadingOlderMessages,
            ) {
                val targetMessageId = state.specialCareJumpMessageId ?: return@LaunchedEffect
                if (messageUiFilter.shouldResetForLoadedHiddenMessage(targetMessageId, loadedMessageIds, messageIndexById)) {
                    messageUiFilter = messageUiFilter.reset()
                    return@LaunchedEffect
                }
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
            LaunchedEffect(pendingQuoteJump, loadedMessageFingerprint, visibleMessageFingerprint, state.isLoadingOlderMessages) {
                val quote = pendingQuoteJump ?: return@LaunchedEffect
                if (
                    messageUiFilter.shouldResetForLoadedHiddenQuote(
                        quote = quote,
                        loadedMessages = state.messages,
                        visibleMessages = visibleMessages,
                        loadedMessageIds = loadedMessageIds,
                        visibleMessageIndexById = messageIndexById,
                    )
                ) {
                    messageUiFilter = messageUiFilter.reset()
                    return@LaunchedEffect
                }
                val targetIndexInMessages = quote.messageId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { messageIndexById[it] }
                    ?: visibleMessages.indexOfReferencedQuote(quote)
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
                    .then(
                        if (LocalHhhlColors.current.chatBackground.alpha > 0.01f) {
                            Modifier.background(LocalHhhlColors.current.chatBackground)
                        } else {
                            Modifier
                        },
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
                            .then(
                                if (LocalHhhlColors.current.chatBackground.alpha > 0.01f) {
                                    Modifier.background(LocalHhhlColors.current.chatBackground.copy(alpha = 0.58f))
                                } else {
                                    Modifier.background(LocalHhhlColors.current.pageBackground.copy(alpha = 0.18f))
                                },
                            ),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = messageListState,
                ) {
                    if (state.isLoadingMessages && state.messages.isEmpty()) {
                        item(key = "chat-detail-loading-$conversationKey", contentType = ChatListContentType.Status) {
                            ChatStatusRow(text = "正在加载消息...", loading = true)
                        }
                    }
                    state.messageErrorMessage?.let { message ->
                        item(key = "chat-detail-error-$conversationKey", contentType = ChatListContentType.Status) {
                            ChatStatusRow(
                                text = message,
                                actionText = "重试",
                                onAction = onRefresh,
                            )
                        }
                    }
                    if (!state.isLoadingMessages && state.messages.isEmpty() && state.messageErrorMessage == null) {
                        item(key = "chat-detail-empty-$conversationKey", contentType = ChatListContentType.Status) {
                            ChatStatusRow(text = "还没有消息")
                        }
                    }
                    if (state.messages.isNotEmpty() && visibleMessages.isEmpty() && state.messageErrorMessage == null) {
                        item(key = "chat-detail-filter-empty-$conversationKey", contentType = ChatListContentType.Status) {
                            ChatStatusRow(
                                text = "当前过滤条件隐藏了全部消息",
                                actionText = "重置过滤",
                                onAction = { messageUiFilter = messageUiFilter.reset() },
                            )
                        }
                    }
                    if (filteredMessageCount > 0 && visibleMessages.isNotEmpty()) {
                        item(key = "chat-detail-filter-summary-$conversationKey", contentType = ChatListContentType.Status) {
                            ChatStatusRow(
                                text = "已在本界面隐藏 $filteredMessageCount 条消息",
                                actionText = "过滤设置",
                                onAction = { showingMessageFilters = true },
                            )
                        }
                    }
                    if (state.messages.isNotEmpty() && !state.messagesEndReached) {
                        item(key = "chat-detail-loading-older-$conversationKey", contentType = ChatListContentType.Status) {
                            if (state.isLoadingOlderMessages) {
                                ChatStatusRow(
                                    text = "正在加载更早消息...",
                                    loading = true,
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = visibleMessages,
                        key = { index, message -> message.stableChatMessageListKey(index) },
                        contentType = { _, _ -> ChatListContentType.Message },
                    ) { _, message ->
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
            if (!aiResultText.isNullOrBlank()) {
                ChatAiResultPanel(
                    label = aiResultLabel ?: "AI 结果",
                    text = aiResultText,
                    onUseAsDraft = { onMessageDraftChanged(aiResultText) },
                    onAppendToDraft = { onMessageDraftChanged(state.messageDraft.withAppendedChatText(aiResultText)) },
                    onDismiss = onDismissAiResult,
                )
                HhhlDivider()
            }
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
        HhhlAlertDialog(
            onDismissRequest = { removeAttachedFileDialogOpen = false },
            title = { Text("移除附件") },
            text = {
                val colors = LocalHhhlColors.current
                Text(
                    text = "附件会从当前消息草稿移除，不会删除云端文件。",
                    color = colors.textSecondary,
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
    HhhlAlertDialog(
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
    HhhlAlertDialog(
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
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val colors = LocalHhhlColors.current
            Text(
                text = text,
                color = colors.textSecondary,
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
    aiEnabled: Boolean = false,
    isAiProcessing: Boolean = false,
    onAiSummary: () -> Unit = {},
    onAiReplyDraft: () -> Unit = {},
    onAiActionItems: () -> Unit = {},
    onAiDecisionSummary: () -> Unit = {},
    onOpenFilters: () -> Unit = {},
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
    add(
        HhhlOverflowMenuAction(
            label = if (isAiProcessing) "AI 处理中" else "AI 总结聊天",
            enabled = aiEnabled && !isAiProcessing && !showingMembers,
            icon = Icons.Filled.AutoAwesome,
            onClick = onAiSummary,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "AI 回复草稿",
            enabled = aiEnabled && !isAiProcessing && !showingMembers,
            icon = Icons.Filled.AutoAwesome,
            onClick = onAiReplyDraft,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "AI 待办提取",
            enabled = aiEnabled && !isAiProcessing && !showingMembers,
            icon = Icons.Filled.AutoAwesome,
            onClick = onAiActionItems,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "AI 决策摘要",
            enabled = aiEnabled && !isAiProcessing && !showingMembers,
            icon = Icons.Filled.AutoAwesome,
            onClick = onAiDecisionSummary,
        ),
    )
    add(
        HhhlOverflowMenuAction(
            label = "过滤设置",
            onClick = onOpenFilters,
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
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(colors.surfaceElevated.copy(alpha = 0.98f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = 0.52f),
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
    val colors = LocalHhhlColors.current
    val interactionSource = rememberChatPresslessInteractionSource()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(
                if (selected) {
                    colors.buttonSelectedBackground
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    colors.focusRing.copy(alpha = 0.24f)
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
                colors.accent
            } else {
                colors.textMuted
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
private fun ChatAiResultPanel(
    label: String,
    text: String,
    onUseAsDraft: () -> Unit,
    onAppendToDraft: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.78f))
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HhhlIconActionButton(icon = Icons.Filled.Close, contentDescription = "关闭 AI 结果", onClick = onDismiss)
        }
        Text(
            text = text,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HhhlActionChip(label = "填入输入框", emphasized = true, onClick = onUseAsDraft)
            HhhlActionChip(label = "追加", onClick = onAppendToDraft)
        }
    }
}

@Composable
private fun ChatMessageFilterScreen(
    title: String,
    filter: ChatMessageUiFilterState,
    filteredMessageCount: Int,
    totalMessageCount: Int,
    authors: List<User>,
    onBack: () -> Unit,
    onFilterChanged: (ChatMessageUiFilterState) -> Unit,
) {
    val colors = LocalHhhlColors.current
    val invalidRegex = filter.regexDraft
        .takeIf { it.isNotBlank() }
        ?.let { pattern ->
            pattern.trim().length > CHAT_MESSAGE_UI_FILTER_MAX_REGEX_LENGTH ||
                !pattern.trim().isSafeChatMessageUiFilterRegex()
        }
        ?: false
    val canAddRegex = filter.regexDraft.isNotBlank() &&
        !invalidRegex &&
        filter.regexPatterns.size < CHAT_MESSAGE_UI_FILTER_MAX_REGEX_RULES
    val cleanHiddenUserDraft = filter.hiddenUserDraft.cleanChatMessageUiHiddenUserRule()
    val invalidHiddenUser = filter.hiddenUserDraft.isNotBlank() && cleanHiddenUserDraft == null
    val canAddHiddenUser = cleanHiddenUserDraft != null &&
        cleanHiddenUserDraft !in filter.hiddenUserIds &&
        filter.hiddenUserIds.size < CHAT_MESSAGE_UI_FILTER_MAX_HIDDEN_USERS
    val normalizedHiddenUserIds = remember(filter.hiddenUserIds) {
        filter.hiddenUserIds.normalizedChatMessageUiHiddenUserRules()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        HhhlTopBar(
            title = "过滤设置",
            supportingText = "$title · 仅影响当前界面显示",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlTextButton(
                    onClick = { onFilterChanged(filter.reset()) },
                    enabled = filter.isActive || filter.regexDraft.isNotBlank() || filter.hiddenUserDraft.isNotBlank(),
                ) {
                    Text("重置")
                }
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "filter-summary", contentType = "filter-summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.mediaBackground.copy(alpha = 0.72f))
                        .border(1.dp, colors.border.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = if (filter.isActive) "已启用 ${filter.activeCount} 条过滤条件" else "未启用过滤",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "当前隐藏 $filteredMessageCount / $totalMessageCount 条消息。过滤只影响这个聊天界面，不影响通知、未读、缓存和实际消息。",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "filter-mfm", contentType = "filter-mfm") {
                ChatFilterToggleRow(
                    title = "隐藏 MFM 语法消息",
                    description = "匹配包含 $[ ... ] 或 ${'$'}{ ... } 的消息。",
                    checked = filter.hideMfmSyntaxMessages,
                    onCheckedChange = {
                        onFilterChanged(filter.copy(hideMfmSyntaxMessages = it))
                    },
                )
            }
            item(key = "filter-regex-editor", contentType = "filter-regex-editor") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.mediaBackground.copy(alpha = 0.58f))
                        .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "正则过滤",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HhhlTextInput(
                        value = filter.regexDraft,
                        onValueChange = { onFilterChanged(filter.copy(regexDraft = it)) },
                        placeholder = "输入正则，例如：广告|刷屏",
                        singleLine = true,
                    )
                    if (invalidRegex) {
                        Text(
                            text = "正则语法无效、过长或可能导致列表卡顿，不会添加。",
                            color = colors.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (filter.regexPatterns.size >= CHAT_MESSAGE_UI_FILTER_MAX_REGEX_RULES) {
                        Text(
                            text = "最多添加 ${CHAT_MESSAGE_UI_FILTER_MAX_REGEX_RULES} 条正则规则，避免聊天列表卡顿。",
                            color = colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HhhlActionChip(
                            label = "添加",
                            enabled = canAddRegex,
                            onClick = {
                                val pattern = filter.regexDraft.trim().take(CHAT_MESSAGE_UI_FILTER_MAX_REGEX_LENGTH)
                                if (pattern.isNotEmpty() && pattern !in filter.regexPatterns) {
                                    onFilterChanged(
                                        filter.copy(
                                            regexPatterns = filter.regexPatterns + pattern,
                                            regexDraft = "",
                                        ),
                                    )
                                }
                            },
                        )
                        HhhlActionChip(
                            label = "清空正则",
                            enabled = filter.regexPatterns.isNotEmpty(),
                            onClick = { onFilterChanged(filter.copy(regexPatterns = emptyList())) },
                        )
                    }
                    filter.regexPatterns.forEach { pattern ->
                        ChatFilterRemovableRow(
                            title = pattern,
                            description = "正则规则",
                            onRemove = {
                                onFilterChanged(filter.copy(regexPatterns = filter.regexPatterns - pattern))
                            },
                        )
                    }
                }
            }
            item(key = "filter-users-title", contentType = "filter-users-title") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "隐藏指定用户",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "可从下方勾选，也可手动添加用户 ID、username 或 @username@host。",
                        color = colors.textMuted.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "filter-user-manual-editor", contentType = "filter-user-manual-editor") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.mediaBackground.copy(alpha = 0.52f))
                        .border(1.dp, colors.border.copy(alpha = 0.26f), RoundedCornerShape(22.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HhhlTextInput(
                        value = filter.hiddenUserDraft,
                        onValueChange = { onFilterChanged(filter.copy(hiddenUserDraft = it)) },
                        placeholder = "用户 ID / username / @name@host",
                        singleLine = true,
                    )
                    if (invalidHiddenUser) {
                        Text(
                            text = "用户规则为空、过长或包含空白字符，不能添加。",
                            color = colors.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (filter.hiddenUserIds.size >= CHAT_MESSAGE_UI_FILTER_MAX_HIDDEN_USERS) {
                        Text(
                            text = "最多添加 ${CHAT_MESSAGE_UI_FILTER_MAX_HIDDEN_USERS} 个用户规则，避免列表匹配成本过高。",
                            color = colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HhhlActionChip(
                            label = "添加用户",
                            enabled = canAddHiddenUser,
                            onClick = {
                                val rule = cleanHiddenUserDraft
                                if (rule != null) {
                                    onFilterChanged(
                                        filter.copy(
                                            hiddenUserIds = filter.hiddenUserIds + rule,
                                            hiddenUserDraft = "",
                                        ),
                                    )
                                }
                            },
                        )
                        HhhlActionChip(
                            label = "清空用户",
                            enabled = filter.hiddenUserIds.isNotEmpty(),
                            onClick = { onFilterChanged(filter.copy(hiddenUserIds = emptySet())) },
                        )
                    }
                    filter.hiddenUserIds.sorted().forEach { rule ->
                        ChatFilterRemovableRow(
                            title = rule,
                            description = "用户规则",
                            onRemove = { onFilterChanged(filter.copy(hiddenUserIds = filter.hiddenUserIds - rule)) },
                        )
                    }
                }
            }
            if (authors.isEmpty()) {
                item(key = "filter-users-empty", contentType = "filter-users-empty") {
                    ChatStatusRow(text = "暂无可过滤用户")
                }
            }
            itemsIndexed(
                items = authors,
                key = { index, user -> user.stableChatUserListKey(index) },
                contentType = { _, _ -> "filter-user" },
            ) { _, user ->
                val hidden = user.matchesHiddenChatMessageUiFilterUser(normalizedHiddenUserIds)
                ChatFilterUserRow(
                    user = user,
                    hidden = hidden,
                    onToggle = {
                        onFilterChanged(
                            filter.withToggledHiddenUser(user = user, hidden = hidden),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatFilterToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.mediaBackground.copy(alpha = 0.58f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChatFilterRemovableRow(
    title: String,
    description: String,
    onRemove: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.46f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HhhlTextButton(onClick = onRemove, destructive = true) {
            Text("删除")
        }
    }
}

@Composable
private fun ChatFilterUserRow(
    user: User,
    hidden: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.mediaBackground.copy(alpha = if (hidden) 0.72f else 0.44f))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initial = user.avatarInitial,
            avatarUrl = user.avatarUrl,
            avatarDecorations = user.avatarDecorations,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = user.displayName.ifBlank { user.username },
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (hidden) "当前已隐藏该用户消息" else "@${user.username}",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HhhlActionChip(
            label = if (hidden) "取消隐藏" else "隐藏",
            emphasized = hidden,
            onClick = onToggle,
        )
    }
}

@Composable
private fun ChatMessageSearchScreen(
    title: String,
    messages: List<ChatMessage>,
    searchResults: List<ChatMessage>,
    members: List<ChatRoomMember>,
    searchQuery: String,
    canLoadOlderMessages: Boolean,
    isLoadingMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    isSearchingMessages: Boolean,
    isLoadingMoreSearch: Boolean,
    canLoadMoreSearch: Boolean,
    messageErrorMessage: String?,
    searchErrorMessage: String?,
    uiFilter: ChatMessageUiFilterState,
    uiFilterRegexes: List<Regex>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSearch: (String) -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSelectMessage: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    var query by remember(title) { mutableStateOf("") }
    var dateQuery by remember(title) { mutableStateOf("") }
    var authorFilterUserId by remember(title) { mutableStateOf<String?>(null) }
    var authorFilterExpanded by remember(title) { mutableStateOf(false) }
    var authorFilterQuery by remember(title) { mutableStateOf("") }
    var searchRegexMode by remember(title) { mutableStateOf(false) }
    val cleanQuery = query.trim()
    val cleanSearchQuery = searchQuery.trim()
    val searchRegex = remember(cleanQuery, searchRegexMode) {
        if (searchRegexMode && cleanQuery.isSafeChatMessageSearchRegex()) {
            runCatching { Regex(cleanQuery, RegexOption.IGNORE_CASE) }.getOrNull()
        } else {
            null
        }
    }
    val regexSearchError = searchRegexMode && cleanQuery.isNotBlank() && searchRegex == null
    val hasPendingQuery = !searchRegexMode && cleanQuery.isNotBlank() && cleanQuery != cleanSearchQuery
    val sourceResults = if (!searchRegexMode && cleanQuery.isNotBlank()) {
        searchResults
    } else {
        (messages + searchResults).distinctBy { it.id }
    }
    val baseResults = remember(sourceResults, uiFilter, uiFilterRegexes) {
        sourceResults.filterByChatMessageUiFilter(uiFilter, uiFilterRegexes)
    }
    val uiFilteredSearchCount = sourceResults.size - baseResults.size
    val authorFilters = remember(members, messages, searchResults) {
        buildChatSearchAuthorFilters(members, messages, searchResults)
    }
    val selectedAuthor = authorFilters.firstOrNull { it.userId == authorFilterUserId }
    LaunchedEffect(authorFilters, authorFilterUserId) {
        if (authorFilterUserId != null && authorFilters.none { it.userId == authorFilterUserId }) {
            authorFilterUserId = null
        }
    }
    val results = remember(baseResults, query, dateQuery, authorFilterUserId, searchRegexMode, searchRegex) {
        val filtered = if (cleanQuery.isBlank() && dateQuery.isBlank()) {
            if (authorFilterUserId == null) emptyList() else baseResults
        } else if (searchRegexMode) {
            baseResults.filterByChatMessageSearchRegex(searchRegex, dateQuery)
        } else {
            baseResults.filterByChatMessageSearch("", dateQuery)
        }
        filtered
            .let { messages ->
                authorFilterUserId?.let { userId -> messages.filter { it.fromUser.id == userId } }
                    ?: messages
            }
            .asReversed()
    }
    val dateSuggestions = remember(messages) {
        buildChatMessageDateSuggestions(messages)
    }
    val hasFilter = cleanQuery.isNotBlank() || dateQuery.isNotBlank() || authorFilterUserId != null
    val canSubmitSearch = cleanQuery.isNotBlank() && !searchRegexMode && !isSearchingMessages
    val searchListState = rememberLazyListState()
    var lastOlderSearchAutoLoadCount by remember(title) { mutableStateOf(0) }

    AutoLoadMoreEffect(
        listState = searchListState,
        itemCount = results.size,
        isLoadingMore = isLoadingMoreSearch ||
            !canLoadMoreSearch ||
            searchRegexMode ||
            cleanQuery.isBlank() ||
            searchResults.isEmpty(),
        onLoadMore = onLoadMoreSearch,
    )
    AutoLoadMoreEffect(
        listState = searchListState,
        itemCount = results.size,
        isLoadingMore = isLoadingOlderMessages ||
            !canLoadOlderMessages ||
            (!searchRegexMode && cleanQuery.isNotBlank()) ||
            messages.isEmpty(),
        onLoadMore = {
            if (messages.size != lastOlderSearchAutoLoadCount) {
                lastOlderSearchAutoLoadCount = messages.size
                onLoadOlderMessages()
            }
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        val colors = LocalHhhlColors.current
        HhhlTopBar(
            title = "搜索消息",
            supportingText = title,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.pageBackground)
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
                    placeholder = if (searchRegexMode) "正则搜索已加载消息" else "搜索",
                    singleLine = true,
                    minHeight = 36.dp,
                    verticalPadding = 6.dp,
                    modifier = Modifier.weight(1f),
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                HhhlActionChip(
                    label = if (searchRegexMode) "正则" else "普通",
                    emphasized = searchRegexMode,
                    onClick = {
                        searchRegexMode = !searchRegexMode
                        if (searchRegexMode) onSearch("")
                    },
                )
                Text(
                    text = when {
                        canSubmitSearch -> "搜索"
                        hasFilter -> "完成"
                        else -> "取消"
                    },
                    color = if (canSubmitSearch) {
                        colors.accent
                    } else {
                        colors.textMuted
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
                                authorFilterUserId = null
                                authorFilterQuery = ""
                                searchRegexMode = false
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
                            authorFilterUserId = null
                            authorFilterQuery = ""
                            searchRegexMode = false
                            onSearch("")
                        },
                        size = 32.dp,
                    )
                }
            }
            ChatMessageAuthorFilterPicker(
                authors = authorFilters,
                selectedUserId = authorFilterUserId,
                query = authorFilterQuery,
                expanded = authorFilterExpanded,
                onQueryChanged = { authorFilterQuery = it },
                onExpandedChanged = { authorFilterExpanded = it },
                onSelected = { userId ->
                    authorFilterUserId = userId
                    authorFilterExpanded = false
                    authorFilterQuery = ""
                },
            )
            if (regexSearchError) {
                Text(
                    text = "正则无效或过于复杂",
                    color = colors.danger,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
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
                    isRemoteSearch = !searchRegexMode && cleanQuery.isNotBlank(),
                    hasPendingQuery = hasPendingQuery,
                    authorName = selectedAuthor?.displayName,
                    isSearching = !searchRegexMode && (isSearchingMessages || isLoadingMoreSearch),
                    resultCount = results.size,
                    loadedCount = baseResults.size,
                ),
                color = colors.textMuted,
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
                item(key = "chat-search-loading", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在加载消息...", loading = true)
                }
            }
            messageErrorMessage?.let { message ->
                item(key = "chat-search-message-error", contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            searchErrorMessage?.let { message ->
                item(key = "chat-search-error", contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = message,
                        actionText = if (!searchRegexMode && cleanQuery.isNotBlank()) "重试搜索" else null,
                        onAction = if (!searchRegexMode && cleanQuery.isNotBlank()) {
                            { onSearch(cleanQuery) }
                        } else {
                            null
                        },
                    )
                }
            }
            if (!searchRegexMode && isSearchingMessages && searchResults.isEmpty()) {
                item(key = "chat-search-remote-loading", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "正在搜索服务器消息...", loading = true)
                }
            }
            if (!hasFilter && messages.isNotEmpty()) {
                item(key = "chat-search-hint", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "输入关键词或日期开始搜索")
                }
            }
            if (uiFilteredSearchCount > 0) {
                item(key = "chat-search-ui-filter-summary", contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = "当前过滤条件在搜索范围内隐藏了 $uiFilteredSearchCount 条消息",
                        actionText = "过滤设置",
                        onAction = onOpenFilters,
                    )
                }
            }
            if (
                hasFilter &&
                results.isEmpty() &&
                !isSearchingMessages &&
                messageErrorMessage == null &&
                searchErrorMessage == null
            ) {
                item(key = "chat-search-empty", contentType = ChatListContentType.Status) {
                    ChatStatusRow(
                        text = if (searchRegexMode) {
                            "当前已加载消息里没有匹配结果"
                        } else if (cleanQuery.isNotBlank()) {
                            "没有匹配的服务器消息"
                        } else {
                            "当前已加载消息里没有匹配结果"
                        },
                    )
                }
            }
            if (!isLoadingMessages && messages.isEmpty() && messageErrorMessage == null) {
                item(key = "chat-search-no-messages", contentType = ChatListContentType.Status) {
                    ChatStatusRow(text = "还没有消息")
                }
            }
            itemsIndexed(
                items = results,
                key = { index, message -> message.stableChatMessageListKey(index) },
                contentType = { _, _ -> ChatListContentType.MessageSearchResult },
            ) { _, message ->
                ChatMessageSearchResultRow(
                    message = message,
                    onClick = { onSelectMessage(message.id) },
                )
                HhhlDivider()
            }
            if (messages.isNotEmpty() && canLoadOlderMessages) {
                item(key = "chat-search-loading-older", contentType = ChatListContentType.Status) {
                    if (isLoadingOlderMessages) {
                        ChatStatusRow(
                            text = "正在加载更早消息...",
                            loading = true,
                        )
                    }
                }
            }
            if (!searchRegexMode && cleanQuery.isNotBlank() && searchResults.isNotEmpty() && canLoadMoreSearch) {
                item(key = "chat-search-loading-more", contentType = ChatListContentType.Status) {
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
private fun ChatMessageAuthorFilterPicker(
    authors: List<ChatSearchAuthorFilter>,
    selectedUserId: String?,
    query: String,
    expanded: Boolean,
    onQueryChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onSelected: (String?) -> Unit,
) {
    if (authors.isEmpty()) return
    val selectedAuthor = authors.firstOrNull { it.userId == selectedUserId }
    val visibleAuthors = remember(authors, query) {
        authors.filterByChatSearchAuthorQuery(query).take(CHAT_SEARCH_AUTHOR_MENU_VISIBLE_USERS)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        HhhlActionChip(
            label = selectedAuthor?.let { "作者：${it.displayName}" } ?: "作者：全部",
            emphasized = selectedUserId != null,
            onClick = { onExpandedChanged(true) },
        )
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
            maxHeight = 420.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        ) {
            HhhlTextInput(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = "搜索用户、用户名、域名",
                singleLine = true,
                minHeight = 36.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            ChatMessageAuthorDropdownItem(
                author = null,
                selected = selectedUserId == null,
                onClick = { onSelected(null) },
            )
            if (visibleAuthors.isEmpty()) {
                Text(
                    text = "没有匹配的用户",
                    color = LocalHhhlColors.current.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            visibleAuthors.forEach { author ->
                ChatMessageAuthorDropdownItem(
                    author = author,
                    selected = selectedUserId == author.userId,
                    onClick = { onSelected(author.userId) },
                )
            }
            if (authors.size > visibleAuthors.size) {
                Text(
                    text = "输入关键词可继续缩小范围",
                    color = LocalHhhlColors.current.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatMessageAuthorDropdownItem(
    author: ChatSearchAuthorFilter?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    HhhlDropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = author?.displayName ?: "全部用户",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = author?.chatSearchAuthorSubtitle().orEmpty()
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = LocalHhhlColors.current.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingIcon = author?.let { currentAuthor ->
            {
                Avatar(
                    initial = currentAuthor.avatarInitial,
                    avatarUrl = currentAuthor.avatarUrl,
                    avatarDecorations = currentAuthor.avatarDecorations,
                    size = 28.dp,
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Text(
                    text = "当前",
                    color = LocalHhhlColors.current.accent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

private fun ChatSearchAuthorFilter.chatSearchAuthorSubtitle(): String {
    return listOf(
        username.takeIf { it.isNotBlank() }?.let { "@$it" },
        host?.takeIf { it.isNotBlank() },
    )
        .filterNotNull()
        .joinToString(" · ")
}

internal fun List<ChatSearchAuthorFilter>.filterByChatSearchAuthorQuery(query: String): List<ChatSearchAuthorFilter> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return this
    return filter { author -> author.matchesChatSearchAuthorQuery(cleanQuery) }
}

private fun ChatSearchAuthorFilter.matchesChatSearchAuthorQuery(query: String): Boolean {
    val identityQuery = query.removePrefix("@")
    val acct = host?.takeIf { it.isNotBlank() }?.let { host -> "$username@$host" } ?: username
    return displayName.contains(query, ignoreCase = true) ||
        username.contains(identityQuery, ignoreCase = true) ||
        "@$username".contains(query, ignoreCase = true) ||
        acct.contains(identityQuery, ignoreCase = true) ||
        "@$acct".contains(query, ignoreCase = true) ||
        host.orEmpty().contains(identityQuery, ignoreCase = true) ||
        userId.contains(query, ignoreCase = true)
}

@Composable
private fun ChatMessageSearchResultRow(
    message: ChatMessage,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
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
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = message.createdAtLabel,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            InlineRichText(
                text = previewText,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            message.file?.let { file ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentSoft)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = file.name.ifBlank { mediaTypeDisplayName(file.type) },
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            presentation.quote?.let { quote ->
                InlineRichText(
                    text = "引用 ${quote.author}: ${quote.preview}",
                    color = colors.accent.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
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
    val colors = LocalHhhlColors.current
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
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (query.isNotBlank()) {
            Text(
                text = "$visibleMemberCount/$totalMemberCount",
                color = if (visibleMemberCount > 0) {
                    colors.accent
                } else {
                    colors.textMuted
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
    val recentActiveUserIds = remember(state.selectedRoom?.id, state.messages) {
        state.messages.recentlyActiveChatMemberIds(roomId = state.selectedRoom?.id)
    }
    val onlineMembers = remember(visibleMembers, recentActiveUserIds) {
        visibleMembers
            .filter { member -> member.isOnlineChatMember(recentActiveUserIds) }
            .sortedWith(chatRoomOnlineMemberComparator(recentActiveUserIds))
    }
    val offlineMembers = remember(visibleMembers, recentActiveUserIds) {
        visibleMembers
            .filterNot { member -> member.isOnlineChatMember(recentActiveUserIds) }
            .sortedWith(chatRoomMemberNameComparator)
    }
    val onlineMemberRows = remember(onlineMembers) { onlineMembers.chunked(CHAT_ROOM_MEMBERS_PER_ROW) }
    val offlineMemberRows = remember(offlineMembers) { offlineMembers.chunked(CHAT_ROOM_MEMBERS_PER_ROW) }
    val memberLazyItemCount = chatRoomMembersLazyItemCount(
        isInitialLoading = state.isLoadingMembers && state.members.isEmpty(),
        hasError = state.memberErrorMessage != null,
        isEmpty = !state.isLoadingMembers && state.members.isEmpty() && state.memberErrorMessage == null,
        isSearchEmpty = searchQuery.isNotBlank() &&
            visibleMembers.isEmpty() &&
            state.members.isNotEmpty() &&
            state.memberErrorMessage == null,
        hasMembers = visibleMembers.isNotEmpty(),
        onlineMemberCount = onlineMembers.size,
        offlineMemberCount = offlineMembers.size,
        hasLoadMoreRow = state.members.isNotEmpty() && !state.membersEndReached,
    )

    AutoLoadMoreEffect(
        listState = memberListState,
        itemCount = memberLazyItemCount,
        isLoadingMore = state.isLoadingMoreMembers ||
            state.membersEndReached ||
            state.members.isEmpty() ||
            searchQuery.isNotBlank(),
        onLoadMore = onLoadMoreMembers,
        threshold = 3,
    )

    LazyColumn(
        modifier = modifier,
        state = memberListState,
    ) {
        if (state.isLoadingMembers && state.members.isEmpty()) {
            item(key = "chat-members-loading", contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "正在加载成员...", loading = true)
            }
        }
        state.memberErrorMessage?.let { message ->
            item(key = "chat-members-error", contentType = ChatListContentType.Status) {
                ChatStatusRow(
                    text = message,
                    actionText = "重试",
                    onAction = onRefresh,
                )
            }
        }
        if (!state.isLoadingMembers && state.members.isEmpty() && state.memberErrorMessage == null) {
            item(key = "chat-members-empty", contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "还没有成员信息")
            }
        }
        if (
            searchQuery.isNotBlank() &&
            visibleMembers.isEmpty() &&
            state.members.isNotEmpty() &&
            state.memberErrorMessage == null
        ) {
            item(key = "chat-members-search-empty", contentType = ChatListContentType.Status) {
                ChatStatusRow(text = "没有匹配的成员")
            }
        }
        if (visibleMembers.isNotEmpty()) {
            chatRoomMemberSectionItems(
                title = "在线 ${onlineMembers.size} 人",
                subtitle = "当前活跃成员",
                memberRows = onlineMemberRows,
                emptyText = "当前没有在线成员",
                online = true,
                keyPrefix = "online",
            )
            item(key = "chat-members-offline-divider", contentType = ChatListContentType.Status) {
                HhhlDivider()
            }
            chatRoomMemberSectionItems(
                title = "已加入但不在线 ${offlineMembers.size} 人",
                subtitle = "聊天室成员",
                memberRows = offlineMemberRows,
                emptyText = "没有离线成员",
                online = false,
                keyPrefix = "offline",
            )
        }
        if (state.members.isNotEmpty() && !state.membersEndReached) {
            item(key = "chat-members-loading-more", contentType = ChatListContentType.Status) {
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

private fun androidx.compose.foundation.lazy.LazyListScope.chatRoomMemberSectionItems(
    title: String,
    subtitle: String,
    memberRows: List<List<ChatRoomMember>>,
    emptyText: String,
    online: Boolean,
    keyPrefix: String,
) {
    item(key = "chat-members-$keyPrefix-header", contentType = ChatListContentType.MemberHeader) {
        ChatRoomMemberSectionHeader(
            title = title,
            subtitle = subtitle,
        )
    }
    if (memberRows.isEmpty()) {
        item(key = "chat-members-$keyPrefix-empty", contentType = ChatListContentType.Status) {
            ChatRoomMemberEmptyRow(text = emptyText)
        }
        return
    }
    itemsIndexed(
        items = memberRows,
        key = { index, row -> "chat-members-$keyPrefix-row-${row.firstOrNull()?.stableChatMemberListKey(index) ?: index}" },
        contentType = { _, _ -> ChatListContentType.MemberRow },
    ) { _, rowMembers ->
        ChatRoomMemberGridRow(
            members = rowMembers,
            online = online,
        )
    }
}

private fun chatRoomMembersLazyItemCount(
    isInitialLoading: Boolean,
    hasError: Boolean,
    isEmpty: Boolean,
    isSearchEmpty: Boolean,
    hasMembers: Boolean,
    onlineMemberCount: Int,
    offlineMemberCount: Int,
    hasLoadMoreRow: Boolean,
): Int {
    var count = 0
    if (isInitialLoading) count += 1
    if (hasError) count += 1
    if (isEmpty) count += 1
    if (isSearchEmpty) count += 1
    if (hasMembers) {
        count += chatRoomMemberSectionLazyItemCount(onlineMemberCount)
        count += 1 // divider between online and offline sections
        count += chatRoomMemberSectionLazyItemCount(offlineMemberCount)
    }
    if (hasLoadMoreRow) count += 1
    return count
}

private fun chatRoomMemberSectionLazyItemCount(memberCount: Int): Int {
    val rowCount = if (memberCount == 0) {
        1
    } else {
        (memberCount + CHAT_ROOM_MEMBERS_PER_ROW - 1) / CHAT_ROOM_MEMBERS_PER_ROW
    }
    return 1 + rowCount
}

@Composable
private fun ChatRoomMemberSectionHeader(
    title: String,
    subtitle: String,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ChatRoomMemberEmptyRow(text: String) {
    val colors = LocalHhhlColors.current
    Text(
        text = text,
        color = colors.textMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.mediaBackground.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChatRoomMemberGridRow(
    members: List<ChatRoomMember>,
    online: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        members.forEach { member ->
            ChatRoomMemberCard(
                member = member,
                online = online,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(CHAT_ROOM_MEMBERS_PER_ROW - members.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatRoomMemberCard(
    member: ChatRoomMember,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val borderColor = if (online) colors.success.copy(alpha = 0.34f) else colors.border.copy(alpha = 0.52f)
    val backgroundColor = if (online) colors.success.copy(alpha = 0.07f) else colors.surfaceElevated.copy(alpha = 0.78f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box {
            Avatar(
                initial = member.user.avatarInitial,
                avatarUrl = member.user.avatarUrl,
                avatarDecorations = member.user.avatarDecorations,
                size = 32.dp,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (online) colors.success else colors.textMuted)
                    .border(1.dp, colors.surfaceElevated, RoundedCornerShape(50)),
            )
        }
        Text(
            text = member.user.displayName,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "@${member.user.username}",
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val memberMeta = if (member.isInferredActiveChatMember()) "刚刚活跃" else "加入 ${member.joinedAtLabel}"
        Text(
            text = memberMeta,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun chatDarkMessageBubbleColor(isOutgoing: Boolean): Color {
    return if (isOutgoing) {
        LocalHhhlColors.current.chatOutgoingBubble.copy(alpha = 0.98f)
    } else {
        LocalHhhlColors.current.chatIncomingBubble.copy(alpha = 0.98f)
    }
}

@Composable
private fun ChatMessageTelegramBubbleSurface(
    isOutgoing: Boolean,
    fillBrush: Brush,
    borderColor: Color,
    shadowColor: Color,
    isDarkSurface: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val radius = ChatMessageTelegramBubbleRadius
    val bubbleShape = remember(isOutgoing, radius) {
        ChatMessageTelegramBubbleShape(
            isOutgoing = isOutgoing,
            cornerRadius = radius,
            tailWidth = ChatMessageTelegramTailWidth,
            tailHeight = ChatMessageTelegramTailHeight,
            tailAnchorY = ChatMessageTelegramTailAnchorY,
        )
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDarkSurface) 3.dp else 5.dp,
                shape = bubbleShape,
                clip = false,
                ambientColor = shadowColor.copy(alpha = if (isDarkSurface) 0.20f else 0.13f),
                spotColor = shadowColor.copy(alpha = if (isDarkSurface) 0.30f else 0.20f),
            ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawTelegramChatBubble(
                isOutgoing = isOutgoing,
                fillBrush = fillBrush,
                borderColor = borderColor,
                shadowColor = shadowColor,
                cornerRadius = radius.toPx(),
                tailWidth = ChatMessageTelegramTailWidth.toPx(),
                tailHeight = ChatMessageTelegramTailHeight.toPx(),
                tailAnchorY = ChatMessageTelegramTailAnchorY.toPx(),
                isDarkSurface = isDarkSurface,
            )
        }
        Box(
            modifier = Modifier.padding(
                start = if (isOutgoing) 14.dp else 22.dp,
                top = 9.dp,
                end = if (isOutgoing) 22.dp else 14.dp,
                bottom = 8.dp,
            ),
        ) {
            content()
        }
    }
}

private class ChatMessageTelegramBubbleShape(
    private val isOutgoing: Boolean,
    private val cornerRadius: Dp,
    private val tailWidth: Dp,
    private val tailHeight: Dp,
    private val tailAnchorY: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            with(density) {
                createTelegramChatBubblePath(
                    size = size,
                    isOutgoing = isOutgoing,
                    cornerRadius = cornerRadius.toPx(),
                    tailWidth = tailWidth.toPx(),
                    tailHeight = tailHeight.toPx(),
                    tailAnchorY = tailAnchorY.toPx(),
                    strokeWidth = 0f,
                )
            },
        )
    }
}

private fun DrawScope.drawTelegramChatBubble(
    isOutgoing: Boolean,
    fillBrush: Brush,
    borderColor: Color,
    shadowColor: Color,
    cornerRadius: Float,
    tailWidth: Float,
    tailHeight: Float,
    tailAnchorY: Float,
    isDarkSurface: Boolean,
) {
    val strokeWidth = 0.42.dp.toPx()
    val path = createTelegramChatBubblePath(
        size = size,
        isOutgoing = isOutgoing,
        cornerRadius = cornerRadius,
        tailWidth = tailWidth,
        tailHeight = tailHeight,
        tailAnchorY = tailAnchorY,
        strokeWidth = strokeWidth,
    )
    drawPath(path = path, brush = fillBrush)
    val topGlowHeight = 9.dp.toPx().coerceAtMost(size.height * 0.36f)
    if (topGlowHeight > 0f) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDarkSurface) 0.035f else 0.18f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = topGlowHeight,
            ),
        )
    }
    val bottomShadeHeight = 7.dp.toPx().coerceAtMost(size.height * 0.24f)
    if (bottomShadeHeight > 0f) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    shadowColor.copy(alpha = if (isDarkSurface) 0.040f else 0.026f),
                ),
                startY = size.height - bottomShadeHeight,
                endY = size.height,
            ),
        )
    }
    val bottomEdgeColor = if (borderColor.alpha > 0.01f) {
        borderColor
    } else {
        shadowColor.copy(alpha = if (isDarkSurface) 0.20f else 0.12f)
    }
    drawLine(
        color = bottomEdgeColor,
        start = androidx.compose.ui.geometry.Offset(x = tailWidth + cornerRadius * 0.55f, y = size.height - strokeWidth),
        end = androidx.compose.ui.geometry.Offset(x = size.width - tailWidth - cornerRadius * 0.55f, y = size.height - strokeWidth),
        strokeWidth = 0.55.dp.toPx(),
    )
    if (borderColor.alpha > 0.01f) {
        drawPath(
            path = path,
            color = borderColor,
            style = Stroke(width = strokeWidth),
        )
    }
}

private fun Density.createTelegramChatBubblePath(
    size: Size,
    isOutgoing: Boolean,
    cornerRadius: Float,
    tailWidth: Float,
    tailHeight: Float,
    tailAnchorY: Float,
    strokeWidth: Float,
): Path {
    val left = if (isOutgoing) strokeWidth / 2f else tailWidth
    val right = if (isOutgoing) size.width - tailWidth else size.width - strokeWidth / 2f
    val top = strokeWidth / 2f
    val bottom = size.height - strokeWidth / 2f
    val bodyWidth = (right - left).coerceAtLeast(0f)
    val bodyHeight = (bottom - top).coerceAtLeast(0f)
    val radius = cornerRadius.coerceAtMost(minOf(bodyWidth, bodyHeight) / 2f)
    val tailRootY = (top + tailAnchorY).coerceIn(top + 2.dp.toPx(), top + radius * 0.86f)
    val tailUpperY = (tailRootY - tailHeight * 0.42f).coerceAtLeast(top + 0.4.dp.toPx())
    val tailLowerY = (tailRootY + tailHeight * 0.64f).coerceAtMost(top + radius * 1.10f)
    val tailTipInset = 0.25.dp.toPx() + strokeWidth / 2f
    val tailTipRound = 0.9.dp.toPx()
    val tailShoulder = tailWidth * 0.20f
    val tailNeck = tailWidth * 0.92f
    val cornerK = 0.55228475f
    return Path().apply {
        if (isOutgoing) {
            moveTo(left + radius, top)
            lineTo(right - radius, top)
            cubicTo(
                right - radius * (1f - cornerK),
                top,
                right,
                tailUpperY - radius * 0.03f,
                right,
                tailUpperY,
            )
            cubicTo(
                right + tailShoulder,
                tailUpperY - tailHeight * 0.08f,
                right + tailNeck,
                tailRootY - tailHeight * 0.58f,
                size.width - tailTipInset,
                top + 0.65.dp.toPx(),
            )
            cubicTo(
                size.width + tailTipInset * 0.12f,
                top + tailTipRound * 0.45f,
                size.width + tailTipInset * 0.12f,
                top + tailTipRound * 1.55f,
                size.width - tailTipInset,
                top + tailTipRound * 2.0f,
            )
            cubicTo(
                right + tailNeck,
                tailRootY + tailHeight * 0.12f,
                right + tailShoulder,
                tailLowerY - tailHeight * 0.05f,
                right,
                tailLowerY,
            )
            lineTo(right, bottom - radius)
            cubicTo(
                right,
                bottom - radius * (1f - cornerK),
                right - radius * (1f - cornerK),
                bottom,
                right - radius,
                bottom,
            )
            lineTo(left + radius, bottom)
            cubicTo(
                left + radius * (1f - cornerK),
                bottom,
                left,
                bottom - radius * (1f - cornerK),
                left,
                bottom - radius,
            )
            lineTo(left, top + radius)
            cubicTo(
                left,
                top + radius * (1f - cornerK),
                left + radius * (1f - cornerK),
                top,
                left + radius,
                top,
            )
        } else {
            moveTo(left + radius, top)
            lineTo(right - radius, top)
            cubicTo(
                right - radius * (1f - cornerK),
                top,
                right,
                top + radius * (1f - cornerK),
                right,
                top + radius,
            )
            lineTo(right, bottom - radius)
            cubicTo(
                right,
                bottom - radius * (1f - cornerK),
                right - radius * (1f - cornerK),
                bottom,
                right - radius,
                bottom,
            )
            lineTo(left + radius, bottom)
            cubicTo(
                left + radius * (1f - cornerK),
                bottom,
                left,
                bottom - radius * (1f - cornerK),
                left,
                bottom - radius,
            )
            lineTo(left, tailLowerY)
            cubicTo(
                left - tailShoulder,
                tailLowerY - tailHeight * 0.05f,
                left - tailNeck,
                tailRootY + tailHeight * 0.12f,
                tailTipInset,
                top + tailTipRound * 2.0f,
            )
            cubicTo(
                -tailTipInset * 0.12f,
                top + tailTipRound * 1.55f,
                -tailTipInset * 0.12f,
                top + tailTipRound * 0.45f,
                tailTipInset,
                top + 0.65.dp.toPx(),
            )
            cubicTo(
                left - tailNeck,
                tailRootY - tailHeight * 0.58f,
                left - tailShoulder,
                tailUpperY - tailHeight * 0.08f,
                left,
                tailUpperY,
            )
            cubicTo(
                left,
                tailUpperY - radius * 0.03f,
                left + radius * (1f - cornerK),
                top,
                left + radius,
                top,
            )
        }
        close()
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
    val colors = LocalHhhlColors.current
    val outgoingBubbleColor = colors.chatOutgoingBubble
    val incomingBubbleColor = colors.chatIncomingBubble
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val darkIncomingBubbleColor = chatDarkMessageBubbleColor(isOutgoing = false)
    val bubbleBrush = remember(
        isOutgoing,
        outgoingBubbleColor,
        incomingBubbleColor,
        isDarkSurface,
        darkIncomingBubbleColor,
    ) {
        if (isOutgoing) {
            Brush.verticalGradient(
                listOf(
                    outgoingBubbleColor.copy(alpha = if (isDarkSurface) 0.98f else 1.00f),
                    outgoingBubbleColor.copy(alpha = if (isDarkSurface) 0.94f else 0.96f),
                )
            )
        } else if (isDarkSurface) {
            Brush.verticalGradient(
                listOf(
                    darkIncomingBubbleColor.copy(alpha = 0.99f),
                    darkIncomingBubbleColor.copy(alpha = 0.93f),
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    incomingBubbleColor.copy(alpha = 1.00f),
                    incomingBubbleColor.copy(alpha = 0.95f),
                )
            )
        }
    }
    val bubbleBorderColor = colors.chatBubbleBorder.copy(alpha = if (isDarkSurface) 0.26f else 0.18f)
    val bubbleContentColor = if (isOutgoing) {
        colors.chatOutgoingText
    } else {
        colors.chatIncomingText
    }
    val bubbleMetaColor = if (isOutgoing) {
        colors.chatOutgoingText.copy(alpha = 0.72f)
    } else {
        colors.textMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = if (isOutgoing) {
            Arrangement.End
        } else {
            Arrangement.spacedBy(7.dp, Alignment.Start)
        },
        verticalAlignment = Alignment.Top,
    ) {
        if (!isOutgoing) {
            Box(
                modifier = Modifier
                    .padding(top = ChatMessageIncomingAvatarTopPadding)
                    .combinedClickable(
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
                .widthIn(max = ChatMessageBubbleMaxWidth),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ChatMessageMetaRow(
                message = message,
                isOutgoing = isOutgoing,
                isDarkSurface = isDarkSurface,
                overflowActions = overflowActions,
                onOpenUser = onOpenUser,
                presslessInteractionSource = presslessInteractionSource,
                modifier = Modifier.widthIn(max = ChatMessageBubbleMaxWidth),
            )
            ChatMessageTelegramBubbleSurface(
                isOutgoing = isOutgoing,
                fillBrush = bubbleBrush,
                borderColor = bubbleBorderColor,
                shadowColor = colors.shadow,
                isDarkSurface = isDarkSurface,
                modifier = Modifier.widthIn(max = ChatMessageBubbleMaxWidth),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
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
                                colors.chatOutgoingText
                            } else {
                                colors.accent
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
            }
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
    }
}

@Composable
private fun ChatMessageMetaRow(
    message: ChatMessage,
    isOutgoing: Boolean,
    isDarkSurface: Boolean,
    overflowActions: List<HhhlOverflowMenuAction>,
    onOpenUser: (String) -> Unit,
    presslessInteractionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val name = chatMiddleEllipsize(
        value = message.fromUser.displayName.ifBlank { message.fromUser.username },
        maxLength = 22,
    )
    val metaTextColor = if (isOutgoing) {
        colors.textMuted.copy(alpha = if (isDarkSurface) 0.92f else 0.82f)
    } else {
        colors.textMuted
    }
    val nameColor = if (isOutgoing) {
        colors.textSecondary
    } else {
        colors.accent
    }

    Row(
        modifier = modifier.padding(
            start = if (isOutgoing) 0.dp else 8.dp,
            end = if (isOutgoing) 8.dp else 0.dp,
            bottom = 1.dp,
        ),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = nameColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = ChatMessageMetaNameMaxWidth)
                .clickable(
                    interactionSource = presslessInteractionSource,
                    indication = null,
                ) { onOpenUser(message.fromUser.id) },
        )
        if (message.createdAtLabel.isNotBlank()) {
            Text(
                text = " · ${message.createdAtLabel}",
                color = metaTextColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.widthIn(max = 108.dp),
            )
        }
        ChatMessageTinyOverflowMenu(
            actions = overflowActions,
            isOutgoing = isOutgoing,
            isDarkSurface = isDarkSurface,
        )
    }
}

@Composable
private fun ChatMessageTinyOverflowMenu(
    actions: List<HhhlOverflowMenuAction>,
    isOutgoing: Boolean,
    isDarkSurface: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val buttonShape = RoundedCornerShape(999.dp)
    val buttonContainer = if (isOutgoing) {
        colors.chatOutgoingText.copy(alpha = if (expanded) 0.075f else if (isDarkSurface) 0.040f else 0.032f)
    } else {
        colors.textSecondary.copy(alpha = if (expanded) 0.080f else if (isDarkSurface) 0.040f else 0.030f)
    }
    val dotColor = if (isOutgoing) {
        colors.chatOutgoingText.copy(alpha = if (expanded) 0.52f else 0.34f)
    } else {
        colors.textSecondary.copy(alpha = if (expanded) 0.56f else 0.38f)
    }
    Box(
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .height(13.dp)
                .width(18.dp)
                .shadow(
                    elevation = 0.dp,
                    shape = buttonShape,
                    clip = false,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .clip(buttonShape)
                .background(buttonContainer)
                .clickable(enabled = actions.isNotEmpty()) { expanded = true }
                .semantics { contentDescription = "消息操作" },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 0.9.dp)
                        .size(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(dotColor),
                )
            }
        }
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
                            action.icon?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (action.destructive) colors.danger else colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = action.label,
                                color = if (action.destructive) colors.danger else colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        .background(if (action.destructive) colors.danger.copy(alpha = 0.07f) else Color.Transparent),
                )
            }
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
    val colors = LocalHhhlColors.current
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
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceElevated)
                .border(
                    width = 1.dp,
                    color = colors.border.copy(alpha = 0.42f),
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
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = if (selected) 2.dp else 1.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(
                when {
                    !enabled -> colors.inputBackground.copy(alpha = 0.56f)
                    selected && isOutgoing && isDarkSurface -> colors.chatOutgoingBubble.copy(alpha = 0.14f)
                    selected && isOutgoing -> colors.chatOutgoingText.copy(alpha = 0.18f)
                    selected -> colors.noteReactionBackground.copy(alpha = 0.92f)
                    isDarkSurface -> colors.surfaceElevated.copy(alpha = 0.82f)
                    else -> colors.surface.copy(alpha = 0.96f)
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    selected && isOutgoing && isDarkSurface -> colors.focusRing.copy(alpha = 0.54f)
                    selected && isOutgoing -> colors.chatOutgoingText.copy(alpha = 0.28f)
                    selected -> colors.focusRing.copy(alpha = 0.54f)
                    else -> colors.border.copy(alpha = 0.34f)
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
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    colors.noteReactionBackground.copy(alpha = 0.70f)
                } else if (isOutgoing) {
                    colors.noteReactionBackground.copy(alpha = 0.82f)
                } else if (isDarkSurface) {
                    colors.surfaceElevated.copy(alpha = 0.78f)
                } else {
                    colors.inputBackground.copy(alpha = 0.86f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    colors.focusRing.copy(alpha = 0.46f)
                } else if (isOutgoing) {
                    colors.focusRing.copy(alpha = 0.46f)
                } else {
                    colors.border.copy(alpha = 0.56f)
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
                colors.accent
            } else {
                colors.textPrimary
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
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    colors.quoteBackground.copy(alpha = 0.36f)
                } else if (isOutgoing) {
                    colors.chatOutgoingText.copy(alpha = 0.12f)
                } else if (isDarkSurface) {
                    colors.quoteBackground.copy(alpha = 0.34f)
                } else {
                    colors.quoteBackground.copy(alpha = 0.72f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    colors.chatBubbleBorder.copy(alpha = 0.42f)
                } else if (isOutgoing) {
                    colors.chatOutgoingText.copy(alpha = 0.14f)
                } else if (isDarkSurface) {
                    colors.chatBubbleBorder.copy(alpha = 0.40f)
                } else {
                    colors.border.copy(alpha = 0.56f)
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
                colors.chatOutgoingText.copy(alpha = 0.76f)
            } else {
                colors.accent
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        InlineRichText(
            text = preview,
            color = if (isOutgoing) {
                colors.chatOutgoingText.copy(alpha = 0.78f)
            } else {
                colors.textMuted
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxChars = 180,
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
            .semantics { contentDescription = "跳到${toast.kind.label}消息" }
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .widthIn(max = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = toast.kind.icon,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Text(
            text = "${toast.kind.shortLabel} · ${toast.displayName}",
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
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    return ChatFloatingButtonSurfaceColors(
        container = colors.surfaceElevated.copy(alpha = 0.88f),
        border = colors.border.copy(alpha = 0.58f),
        content = colors.textPrimary.copy(alpha = 0.86f),
        shadow = colors.shadow.copy(alpha = if (isDarkSurface) 0.82f else 0.68f),
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
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.quoteBackground.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = 0.72f),
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
                .background(colors.accent.copy(alpha = 0.34f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = chatQuoteComposerTitle(actionLabel, quote),
                color = colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            InlineRichText(
                text = quote.previewText,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxChars = 220,
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
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val contentColor = if (isOutgoing) {
        colors.chatOutgoingText
    } else {
        colors.chatIncomingText
    }
    val supportingColor = if (isOutgoing) {
        colors.chatOutgoingText.copy(alpha = 0.72f)
    } else {
        colors.textMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isOutgoing && isDarkSurface) {
                    colors.quoteBackground.copy(alpha = 0.36f)
                } else if (isOutgoing) {
                    colors.chatOutgoingText.copy(alpha = 0.12f)
                } else if (isDarkSurface) {
                    colors.quoteBackground.copy(alpha = 0.34f)
                } else {
                    colors.quoteBackground.copy(alpha = 0.72f)
                },
            )
            .border(
                width = 1.dp,
                color = if (isOutgoing && isDarkSurface) {
                    colors.chatBubbleBorder.copy(alpha = 0.42f)
                } else if (isOutgoing) {
                    colors.chatOutgoingText.copy(alpha = 0.14f)
                } else if (isDarkSurface) {
                    colors.chatBubbleBorder.copy(alpha = 0.40f)
                } else {
                    colors.border.copy(alpha = 0.56f)
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
                        colors.accent.copy(alpha = 0.34f)
                    } else if (isOutgoing) {
                        colors.chatOutgoingText.copy(alpha = 0.58f)
                    } else if (isDarkSurface) {
                        colors.accent.copy(alpha = 0.30f)
                    } else {
                        colors.accent.copy(alpha = 0.34f)
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
            InlineRichText(
                text = quote.preview,
                color = supportingColor,
                style = MaterialTheme.typography.bodySmall,
                maxChars = 220,
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
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.chatComposerBackground)
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
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = file.name.ifBlank { "附件" },
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (isUploading) "上传处理中" else mediaTypeDisplayName(file.type, file.name),
                color = colors.textMuted,
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
    val colors = LocalHhhlColors.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chatComposerBackground.copy(alpha = 0.34f))
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
    val colors = LocalHhhlColors.current
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
                        selected -> colors.buttonSelectedBackground
                        else -> colors.surface.copy(alpha = if (enabled) 0.92f else 0.52f)
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        colors.focusRing.copy(alpha = 0.54f)
                    } else {
                        colors.border.copy(alpha = 0.50f)
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
                    !enabled -> colors.textMuted
                    selected -> colors.accent
                    else -> colors.textPrimary
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = if (!enabled) disabledLabel ?: label else label,
            color = if (enabled) colors.textSecondary else colors.textMuted,
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
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(999.dp)
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    val neutralContainer = if (isDarkSurface) {
        Color.White.copy(alpha = if (enabled) 0.075f else 0.035f)
    } else {
        colors.surfaceElevated.copy(alpha = if (enabled) 0.94f else 0.54f)
    }
    val activeContainer = colors.accent.copy(alpha = if (isDarkSurface) 0.16f else 0.10f)
    val backgroundColor = when {
        !enabled -> neutralContainer
        selected || emphasized -> activeContainer
        else -> neutralContainer
    }
    val iconTint = when {
        !enabled -> colors.textMuted.copy(alpha = 0.64f)
        selected || emphasized -> colors.accent
        else -> colors.textSecondary
    }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (enabled) 1.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .semantics { contentDescription = label }
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (selected || emphasized) {
                    colors.focusRing.copy(alpha = if (isDarkSurface) 0.34f else 0.20f)
                } else if (isDarkSurface) {
                    Color.White.copy(alpha = 0.07f)
                } else {
                    colors.border.copy(alpha = 0.24f)
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
    val raw = message.text
    val fallbackBody = message.chatMessageFallbackBody()
    val messageId = raw.lineSequence()
        .mapNotNull { it.trim().chatQuoteMarkerMessageIdOrNull() }
        .firstOrNull()
        ?: return ChatRenderedMessage(null, raw.takeIf { it.isNotBlank() } ?: fallbackBody)
    val firstLineEnd = raw.indexOf('\n').takeIf { it >= 0 } ?: raw.length
    val first = raw.substring(0, firstLineEnd).trim()
    if (!first.startsWith(">")) {
        return ChatRenderedMessage(null, raw.withoutChatQuoteMarkerLines().takeIf { it.isNotBlank() } ?: fallbackBody)
    }

    val quoteLine = first.removePrefix(">").trim()
    val parts = quoteLine.split(":", limit = 2)
    val author = parts.firstOrNull()?.trim().orEmpty().ifBlank { "引用" }
    val preview = parts.getOrNull(1)?.toChatReferencePreviewBody().orEmpty().ifBlank { "引用消息" }
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

private fun ChatMessage.chatMessageFallbackBody(): String {
    return file?.name?.trim().takeIf { !it.isNullOrEmpty() } ?: "[附件消息]"
}

private fun String.withoutChatQuoteMarkerLines(): String {
    if (!contains("hhhl-chat-quote:")) return this
    return lineSequence()
        .filterNot { line -> line.trim().chatQuoteMarkerMessageIdOrNull() != null }
        .joinToString(separator = "\n")
        .trim()
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

internal fun List<ChatMessage>.filterByChatMessageUiFilter(
    filter: ChatMessageUiFilterState,
    regexes: List<Regex>,
): List<ChatMessage> {
    if (!filter.isActive) return this
    val normalizedHiddenUserIds = filter.hiddenUserIds.normalizedChatMessageUiHiddenUserRules()
    return filterNot { message ->
        message.isHiddenByChatMessageUiFilter(filter, regexes, normalizedHiddenUserIds)
    }
}

internal fun List<ChatMessage>.loadedChatMessageIdSet(): Set<String> {
    return asSequence().map { it.id }.toSet()
}

private fun ChatRoom.stableChatRoomListKey(index: Int): String {
    return "room-${membershipId.ifBlank { id.ifBlank { name } }}-$index"
}

private fun ChatUserConversation.stableChatUserConversationListKey(index: Int): String {
    return "user-${user.id.ifBlank { user.username.ifBlank { user.displayName } }}-$index"
}

private fun ChatMessage.stableChatMessageListKey(index: Int): String {
    return "message-${id.ifBlank { chatMessageFallbackListKey(index) }}-$index"
}

private fun ChatRoomMember.stableChatMemberListKey(index: Int): String {
    return "${membershipId.ifBlank { user.id.ifBlank { roomId } }}-$index"
}

private fun User.stableChatUserListKey(index: Int): String {
    return "${id.ifBlank { username.ifBlank { displayName } }}-$index"
}

private fun ChatMessage.chatMessageFallbackListKey(index: Int): String {
    val seed = listOf(roomId, toUserId.orEmpty(), fromUser.id, createdAt, createdAtLabel, text, file?.id.orEmpty(), index.toString())
        .joinToString(separator = "\u0000")
    return seed.stableChatListHash()
}

private fun String.stableChatListHash(): String {
    var hash = 1125899906842597L
    for (char in this) {
        hash = 31L * hash + char.code
    }
    return hash.toULong().toString(36)
}

internal fun ChatMessageUiFilterState.shouldResetForLoadedHiddenMessage(
    messageId: String?,
    loadedMessageIds: Set<String>,
    visibleMessageIndexById: Map<String, Int>,
): Boolean {
    val cleanId = messageId?.takeIf { it.isNotBlank() } ?: return false
    return isActive && cleanId in loadedMessageIds && cleanId !in visibleMessageIndexById
}

internal fun ChatMessageUiFilterState.shouldResetForLoadedHiddenQuote(
    quote: ChatRenderedQuote,
    loadedMessages: List<ChatMessage>,
    visibleMessages: List<ChatMessage>,
    loadedMessageIds: Set<String>,
    visibleMessageIndexById: Map<String, Int>,
): Boolean {
    quote.messageId?.takeIf { it.isNotBlank() }?.let { messageId ->
        return shouldResetForLoadedHiddenMessage(messageId, loadedMessageIds, visibleMessageIndexById)
    }
    return isActive && loadedMessages.indexOfReferencedQuote(quote) >= 0 && visibleMessages.indexOfReferencedQuote(quote) < 0
}

internal fun List<ChatMessage>.chatMessageIdFingerprint(): String {
    if (isEmpty()) return "0"
    val firstId = first().id
    val middleId = this[size / 2].id
    val lastId = last().id
    return "$size\u0000$firstId\u0000$middleId\u0000$lastId"
}

internal fun compileChatMessageUiFilterRegexes(patterns: List<String>): List<Regex> {
    return patterns
        .asSequence()
        .map { it.trim() }
        .filter { it.isSafeChatMessageUiFilterRegex() }
        .distinct()
        .take(CHAT_MESSAGE_UI_FILTER_MAX_REGEX_RULES)
        .mapNotNull { pattern -> runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() }
        .toList()
}

internal fun String.isSafeChatMessageUiFilterRegex(): Boolean {
    val pattern = trim()
    if (pattern.isEmpty() || pattern.length > CHAT_MESSAGE_UI_FILTER_MAX_REGEX_LENGTH) return false
    if (runCatching { Regex(pattern) }.isFailure) return false
    return !pattern.hasNestedOrAmbiguousRegexQuantifier() && !pattern.hasAdvancedBacktrackingRegexConstruct()
}

private fun String.hasAdvancedBacktrackingRegexConstruct(): Boolean {
    var escaped = false
    var inCharacterClass = false
    forEachIndexed { index, char ->
        if (escaped) {
            escaped = false
            if (char.isDigit() || char == 'k') return true
            return@forEachIndexed
        }
        when (char) {
            '\\' -> {
                escaped = true
                return@forEachIndexed
            }
            '[' -> if (!inCharacterClass) {
                inCharacterClass = true
                return@forEachIndexed
            }
            ']' -> if (inCharacterClass) {
                inCharacterClass = false
                return@forEachIndexed
            }
        }
        if (inCharacterClass) return@forEachIndexed
        if (char == '(' && getOrNull(index + 1) == '?') return true
    }
    return false
}

private fun String.hasNestedOrAmbiguousRegexQuantifier(): Boolean {
    var escaped = false
    var inCharacterClass = false
    var groupDepth = 0
    var groupStart = -1
    var groupHasQuantifier = false
    var lastTokenWasQuantified = false
    forEachIndexed { index, char ->
        if (escaped) {
            escaped = false
            lastTokenWasQuantified = false
            return@forEachIndexed
        }
        when (char) {
            '\\' -> {
                escaped = true
                return@forEachIndexed
            }
            '[' -> if (!inCharacterClass) {
                inCharacterClass = true
                lastTokenWasQuantified = false
                return@forEachIndexed
            }
            ']' -> if (inCharacterClass) {
                inCharacterClass = false
                lastTokenWasQuantified = false
                return@forEachIndexed
            }
        }
        if (inCharacterClass) return@forEachIndexed
        when (char) {
            '(' -> if (groupDepth == 0) {
                groupStart = index
                groupHasQuantifier = false
                lastTokenWasQuantified = false
                groupDepth = 1
            } else {
                groupDepth += 1
                lastTokenWasQuantified = false
            }
            ')' -> if (groupDepth > 0) {
                groupDepth -= 1
                lastTokenWasQuantified = false
            }
            '*', '+', '?' -> {
                if (lastTokenWasQuantified) return true
                if (groupDepth > 0) groupHasQuantifier = true
                if (groupDepth == 0 && groupStart >= 0 && groupHasQuantifier && index > groupStart) return true
                lastTokenWasQuantified = true
            }
            '{' -> {
                val closeIndex = indexOf('}', startIndex = index + 1)
                if (closeIndex > index && substring(index + 1, closeIndex).isRegexRepeatRange()) {
                    if (lastTokenWasQuantified) return true
                    if (groupDepth > 0) groupHasQuantifier = true
                    if (groupDepth == 0 && groupStart >= 0 && groupHasQuantifier && index > groupStart) return true
                    lastTokenWasQuantified = true
                } else {
                    lastTokenWasQuantified = false
                }
            }
            '|', '.' -> {
                if (char == '|' && groupDepth > 0) groupHasQuantifier = true
                lastTokenWasQuantified = false
            }
            else -> lastTokenWasQuantified = false
        }
    }
    return false
}

private fun String.isRegexRepeatRange(): Boolean {
    if (isEmpty()) return false
    val commaIndex = indexOf(',')
    return if (commaIndex < 0) {
        all { it.isDigit() }
    } else {
        substring(0, commaIndex).all { it.isDigit() } &&
            substring(commaIndex + 1).all { it.isDigit() }
    }
}

internal fun ChatMessage.isHiddenByChatMessageUiFilter(
    filter: ChatMessageUiFilterState,
    regexes: List<Regex>,
    normalizedHiddenUserIds: Set<String> = filter.hiddenUserIds.normalizedChatMessageUiHiddenUserRules(),
): Boolean {
    if (fromUser.matchesHiddenChatMessageUiFilterUser(normalizedHiddenUserIds)) return true
    if (filter.hideMfmSyntaxMessages && containsChatMessageMfmSyntax()) return true
    if (regexes.isEmpty()) return false
    val matchText = chatMessageUiFilterMatchText()
    return regexes.any { regex -> regex.containsMatchIn(matchText) }
}

internal fun ChatMessageUiFilterState.withToggledHiddenUser(
    user: User,
    hidden: Boolean,
): ChatMessageUiFilterState {
    return copy(
        hiddenUserIds = if (hidden) {
            hiddenUserIds.withoutChatMessageUiRulesForUser(user)
        } else {
            hiddenUserIds + user.id
        },
    )
}

private fun Set<String>.withoutChatMessageUiRulesForUser(user: User): Set<String> {
    if (isEmpty()) return this
    val userKeys = user.chatMessageUiFilterUserKeys().normalizedChatMessageUiHiddenUserRules()
    return filterNot { rule ->
        val cleanRule = rule.trim()
        cleanRule in userKeys || cleanRule.lowercase() in userKeys
    }.toSet()
}

private fun User.matchesHiddenChatMessageUiFilterUser(hiddenUserIds: Set<String>): Boolean {
    if (hiddenUserIds.isEmpty()) return false
    return chatMessageUiFilterUserKeys().any { key ->
        key in hiddenUserIds || key.lowercase() in hiddenUserIds
    }
}

private fun Set<String>.normalizedChatMessageUiHiddenUserRules(): Set<String> {
    if (isEmpty()) return emptySet()
    return asSequence()
        .flatMap { rule -> sequenceOf(rule, rule.lowercase()) }
        .toSet()
}

private fun User.chatMessageUiFilterUserKeys(): Set<String> {
    return buildSet {
        id.trim().takeIf { it.isNotEmpty() }?.let(::add)
        username.trim().takeIf { it.isNotEmpty() }?.let { name ->
            add(name)
            add("@$name")
            add(name.lowercase())
            add("@${name.lowercase()}")
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { host ->
                add("$name@$host")
                add("@$name@$host")
                add("${name.lowercase()}@${host.lowercase()}")
                add("@${name.lowercase()}@${host.lowercase()}")
            }
        }
    }
}

internal fun String.cleanChatMessageUiHiddenUserRule(): String? {
    val clean = trim()
    if (clean.length > CHAT_MESSAGE_UI_FILTER_MAX_USER_RULE_LENGTH) return null
    if (clean.isBlank() || clean.any { it.isWhitespace() }) return null
    return clean
}

private fun ChatMessage.containsChatMessageMfmSyntax(): Boolean {
    return text.containsValidMfmSyntax() ||
        reply?.text?.containsValidMfmSyntax() == true ||
        quote?.text?.containsValidMfmSyntax() == true
}

private fun ChatMessage.chatMessageUiFilterMatchText(): String {
    val builder = ChatMessageUiFilterMatchTextBuilder(CHAT_MESSAGE_UI_FILTER_MAX_MATCH_TEXT_LENGTH)
    builder.appendPart(text)
    reply?.text?.takeIf { it.isNotBlank() }?.let { referenceText ->
        builder.appendPart(referenceText)
    }
    quote?.text?.takeIf { it.isNotBlank() }?.let { referenceText ->
        builder.appendPart(referenceText)
    }
    file?.name?.takeIf { it.isNotBlank() }?.let { fileName ->
        builder.appendPart(fileName)
    }
    if (builder.isFullTextEmpty) {
        builder.appendPart(chatMessageBodyText(this))
    }
    return builder.build()
}

private class ChatMessageUiFilterMatchTextBuilder(
    private val maxLength: Int,
) {
    private val head = StringBuilder(maxLength + 1)
    private val tail = StringBuilder(maxLength / 2 + 1)
    private val edgeLength = maxLength / 2
    private var fullLength = 0

    val isFullTextEmpty: Boolean
        get() = fullLength == 0

    fun appendPart(value: String) {
        if (value.isBlank()) return
        if (fullLength > 0) appendChunk("\n")
        appendChunk(value)
    }

    fun build(): String {
        if (fullLength <= maxLength) return head.toString()
        return head.substring(0, edgeLength.coerceAtMost(head.length)) + "\n" + tail.toString()
    }

    private fun appendChunk(value: String) {
        if (value.isEmpty()) return
        val headRemaining = maxLength - head.length
        if (headRemaining > 0) {
            head.append(value.take(headRemaining))
        }
        if (value.length >= edgeLength) {
            tail.clear()
            tail.append(value.takeLast(edgeLength))
        } else {
            tail.append(value)
            if (tail.length > edgeLength) {
                tail.delete(0, tail.length - edgeLength)
            }
        }
        fullLength += value.length
    }
}

internal fun ChatMessage.isHiddenByBlockedChatUser(blockedUserIds: Set<String>): Boolean {
    if (blockedUserIds.isEmpty()) return false
    return fromUser.id in blockedUserIds ||
        toUser?.id?.let { it in blockedUserIds } == true ||
        reply?.fromUser?.id?.let { it in blockedUserIds } == true ||
        quote?.fromUser?.id?.let { it in blockedUserIds } == true
}

internal fun buildChatSearchAuthorFilters(
    members: List<ChatRoomMember>,
    messages: List<ChatMessage>,
    searchResults: List<ChatMessage>,
): List<ChatSearchAuthorFilter> {
    val seenUserIds = HashSet<String>(CHAT_SEARCH_AUTHOR_FILTER_MAX_USERS)
    val filters = ArrayList<ChatSearchAuthorFilter>(CHAT_SEARCH_AUTHOR_FILTER_MAX_USERS)
    fun visit(user: User) {
        if (filters.size >= CHAT_SEARCH_AUTHOR_FILTER_MAX_USERS) return
        if (user.id.isBlank() || !seenUserIds.add(user.id)) return
        filters += user.toChatSearchAuthorFilter()
    }
    members.forEach { member -> visit(member.user) }
    for (index in searchResults.indices.reversed()) visit(searchResults[index].fromUser)
    for (index in messages.indices.reversed()) visit(messages[index].fromUser)
    return filters
}

private fun User.toChatSearchAuthorFilter(): ChatSearchAuthorFilter {
    return ChatSearchAuthorFilter(
        userId = id,
        displayName = displayName.ifBlank { username.ifBlank { id } },
        username = username,
        host = host,
        avatarInitial = avatarInitial,
        avatarUrl = avatarUrl,
        avatarDecorations = avatarDecorations,
    )
}

private fun buildChatMessageDateSuggestions(messages: List<ChatMessage>): List<String> {
    val seenDates = HashSet<String>(6)
    val suggestions = ArrayList<String>(6)
    for (index in messages.indices.reversed()) {
        val suggestion = messages[index].chatMessageSearchDateSuggestion() ?: continue
        if (seenDates.add(suggestion)) {
            suggestions += suggestion
            if (suggestions.size >= 6) break
        }
    }
    return suggestions
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
    val body = text.toChatReferencePreviewBody().takeIf { it.isNotBlank() }
        ?: file?.name?.takeIf { it.isNotBlank() }
        ?: "附件"
    return if (author.isNullOrBlank()) body else "$author: $body"
}

private fun String.withAppendedChatText(text: String): String {
    val cleanText = text.trim()
    if (cleanText.isEmpty()) return this
    val base = trimEnd()
    return if (base.isBlank()) cleanText else "$base\n$cleanText"
}

fun cc.hhhl.client.model.ChatMessageReference.toRenderedQuote(): ChatRenderedQuote {
    val author = fromUser?.displayName?.takeIf { it.isNotBlank() }
        ?: fromUser?.username?.takeIf { it.isNotBlank() }
        ?: "引用"
    val preview = text.toChatReferencePreviewBody().takeIf { it.isNotBlank() }
        ?: file?.name?.takeIf { it.isNotBlank() }
        ?: "附件"
    return ChatRenderedQuote(
        messageId = id.takeIf { it.isNotBlank() },
        author = author,
        preview = preview,
    )
}

private fun String.toChatReferencePreviewBody(): String {
    return richTextPlainPreviewText(this)
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

internal fun List<ChatMessage>.filterByChatMessageSearch(
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

internal fun List<ChatMessage>.filterByChatMessageSearchRegex(
    regex: Regex?,
    dateQuery: String,
): List<ChatMessage> {
    val cleanDateQuery = dateQuery.trim()
    if (regex == null && cleanDateQuery.isBlank()) return emptyList()
    return filter { message ->
        (regex == null || message.matchesChatMessageRegex(regex)) &&
            (cleanDateQuery.isBlank() || message.matchesChatMessageDateQuery(cleanDateQuery))
    }
}

internal fun String.isSafeChatMessageSearchRegex(): Boolean {
    val pattern = trim()
    if (pattern.isEmpty() || pattern.length > CHAT_MESSAGE_UI_FILTER_MAX_REGEX_LENGTH) return false
    if (runCatching { Regex(pattern) }.isFailure) return false
    return !pattern.hasNestedOrAmbiguousRegexQuantifier() && !pattern.hasAdvancedBacktrackingRegexConstruct()
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

private fun ChatMessage.matchesChatMessageRegex(regex: Regex): Boolean {
    return regex.containsMatchIn(chatMessageSearchMatchText())
}

private fun ChatMessage.chatMessageSearchMatchText(): String {
    val presentation = chatMessagePresentation(this)
    val builder = ChatMessageUiFilterMatchTextBuilder(CHAT_MESSAGE_UI_FILTER_MAX_MATCH_TEXT_LENGTH)
    builder.appendPart(text)
    builder.appendPart(presentation.body)
    presentation.quote?.let { quote ->
        builder.appendPart(quote.author)
        builder.appendPart(quote.preview)
    }
    builder.appendPart(fromUser.displayName)
    builder.appendPart(fromUser.username)
    hostSearchText()?.let { builder.appendPart(it) }
    reply?.let { reference -> builder.appendPart(reference.chatMessageReferenceSearchText()) }
    quote?.let { reference -> builder.appendPart(reference.chatMessageReferenceSearchText()) }
    file?.let { file ->
        builder.appendPart(file.name)
        builder.appendPart(file.type)
    }
    return builder.build()
}

private fun ChatMessage.hostSearchText(): String? {
    return fromUser.host?.takeIf { it.isNotBlank() }
}

private fun cc.hhhl.client.model.ChatMessageReference.chatMessageReferenceSearchText(): String {
    val builder = ChatMessageUiFilterMatchTextBuilder(CHAT_MESSAGE_UI_FILTER_MAX_MATCH_TEXT_LENGTH / 2)
    fromUser?.let { user ->
        builder.appendPart(user.displayName)
        builder.appendPart(user.username)
        user.host?.let { builder.appendPart(it) }
    }
    builder.appendPart(text)
    file?.let { file ->
        builder.appendPart(file.name)
        builder.appendPart(file.type)
    }
    return builder.build()
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

internal fun chatUserConversationPreview(
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
    authorName: String? = null,
    isSearching: Boolean,
    resultCount: Int,
    loadedCount: Int,
): String {
    return when {
        isSearching -> "正在搜索..."
        hasPendingQuery -> "关键词已修改，点击搜索同步服务器"
        !authorName.isNullOrBlank() && hasFilter && isRemoteSearch -> {
            "服务器结果 $resultCount 条 · $authorName · 已取回 $loadedCount 条"
        }
        !authorName.isNullOrBlank() && hasFilter -> "找到 $resultCount 条 · $authorName · 已加载 $loadedCount 条消息"
        hasFilter && isRemoteSearch -> "服务器结果 $resultCount 条 · 已取回 $loadedCount 条"
        hasFilter -> "找到 $resultCount 条 · 已加载 $loadedCount 条消息"
        else -> "已加载 $loadedCount 条消息"
    }
}

private fun chatMessageSearchPreview(
    message: ChatMessage,
    presentation: ChatRenderedMessage,
): String {
    val body = presentation.body.toChatReferencePreviewBody()
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

private fun ChatRoomMember.isOnlineChatMember(recentlyActiveUserIds: Set<String>): Boolean {
    val status = user.onlineStatus.trim()
    val recentlyActive = user.id in recentlyActiveUserIds || isInferredActiveChatMember()
    return when {
        recentlyActive -> true
        status.equals("online", ignoreCase = true) ||
            status.equals("active", ignoreCase = true) -> true
        status.equals("offline", ignoreCase = true) -> false
        else -> false
    }
}

private fun ChatRoomMember.isInferredActiveChatMember(): Boolean {
    return membershipId.startsWith(CHAT_ROOM_INFERRED_ACTIVE_MEMBER_PREFIX)
}

private fun chatRoomOnlineMemberComparator(recentlyActiveUserIds: Set<String>): Comparator<ChatRoomMember> {
    return compareByDescending<ChatRoomMember> { member -> member.user.onlineStatus.equals("online", ignoreCase = true) }
        .thenByDescending { member -> member.user.id in recentlyActiveUserIds || member.isInferredActiveChatMember() }
        .then(chatRoomMemberNameComparator)
}

private val chatRoomMemberNameComparator = compareBy<ChatRoomMember, String>(String.CASE_INSENSITIVE_ORDER) { member ->
    member.user.displayName.ifBlank { member.user.username }
}.thenBy { member -> member.user.id }

private fun List<ChatMessage>.recentlyActiveChatMemberIds(roomId: String?): Set<String> {
    if (isEmpty()) return emptySet()
    val currentRoomId = roomId?.trim().orEmpty()

    val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
    val recentIds = linkedSetOf<String>()
    var fallbackScannedMessages = 0
    val fallbackIds = linkedSetOf<String>()
    var matchedRoomMessage = false
    for (index in indices.reversed()) {
        val message = this[index]
        if (currentRoomId.isNotBlank() && message.roomId != currentRoomId) continue
        matchedRoomMessage = true
        val userId = message.fromUser.id.takeIf { it.isNotBlank() } ?: continue
        if (fallbackScannedMessages < CHAT_MEMBER_ACTIVE_FALLBACK_MESSAGE_LIMIT) {
            fallbackScannedMessages += 1
            fallbackIds += userId
        }
        val createdAtEpochMillis = message.createdAt.toApiInstantOrNull()?.toEpochMilliseconds()
        if (createdAtEpochMillis != null) {
            if (nowEpochMillis - createdAtEpochMillis <= CHAT_MEMBER_ACTIVE_WINDOW_MILLIS) {
                recentIds += userId
                if (recentIds.size >= CHAT_MEMBER_ACTIVE_FALLBACK_USER_LIMIT) break
                continue
            }
            if (recentIds.isNotEmpty()) break
        }
        if (fallbackScannedMessages >= CHAT_MEMBER_ACTIVE_FALLBACK_MESSAGE_LIMIT &&
            fallbackIds.size >= CHAT_MEMBER_ACTIVE_FALLBACK_USER_LIMIT
        ) {
            break
        }
    }
    if (!matchedRoomMessage) return emptySet()
    if (recentIds.isNotEmpty()) return recentIds
    if (fallbackIds.size > CHAT_MEMBER_ACTIVE_FALLBACK_USER_LIMIT) {
        return fallbackIds.take(CHAT_MEMBER_ACTIVE_FALLBACK_USER_LIMIT).toSet()
    }
    return fallbackIds
}

internal fun List<ChatMessage>.indexOfReferencedQuote(quote: ChatRenderedQuote): Int {
    quote.messageId?.takeIf { it.isNotBlank() }?.let { messageId ->
        val exactIndex = indexOfFirst { it.id == messageId }
        if (exactIndex >= 0) return exactIndex
    }
    val cleanPreview = quote.preview.trim()
    val cleanAuthor = quote.author.trim()
    if (cleanPreview.isBlank()) return -1
    val previewIndexWithAuthor = indexOfFirst { message ->
        val presentation = chatMessagePresentation(message)
        val plainBody = presentation.body.toChatReferencePreviewBody()
        val authorMatches = cleanAuthor.isBlank() ||
            message.fromUser.displayName.equals(cleanAuthor, ignoreCase = true) ||
            message.fromUser.username.equals(cleanAuthor, ignoreCase = true)
        authorMatches && (
            presentation.body.contains(cleanPreview, ignoreCase = true) ||
                plainBody.contains(cleanPreview, ignoreCase = true)
            )
    }
    if (previewIndexWithAuthor >= 0) return previewIndexWithAuthor
    return indexOfLast { message ->
        val presentation = chatMessagePresentation(message)
        val plainBody = presentation.body.toChatReferencePreviewBody()
        presentation.body.contains(cleanPreview, ignoreCase = true) ||
            plainBody.contains(cleanPreview, ignoreCase = true)
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
