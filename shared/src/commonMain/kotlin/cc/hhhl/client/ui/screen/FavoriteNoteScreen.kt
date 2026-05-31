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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Note
import cc.hhhl.client.presentation.richTextPlainPreviewText
import cc.hhhl.client.state.FavoriteMessage
import cc.hhhl.client.state.FavoriteNoteUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.CustomEmojiReactionLabel
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlSegmentedControl
import cc.hhhl.client.ui.component.HhhlSegmentedItem
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession
import cc.hhhl.client.ui.component.mediaTypeDisplayName

private enum class FavoriteTab(val label: String) {
    Posts("帖子"),
    Messages("信息"),
}

@Composable
fun FavoriteNoteScreen(
    state: FavoriteNoteUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRemoveFavoriteMessage: (String) -> Unit = {},
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
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: (String) -> Boolean = { false },
    canDeleteAuthor: (String) -> Boolean = { false },
    noteRowDensity: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    var selectedTab by remember { mutableStateOf(FavoriteTab.Posts) }
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = state.favorites.size,
        isLoadingMore = selectedTab != FavoriteTab.Posts || state.isLoadingMore || state.endReached,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "收藏",
            supportingText = favoriteScreenSupportingText(state, selectedTab),
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        FavoriteSummaryRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            postCount = state.favorites.size,
            messageCount = state.favoriteMessages.size,
            isLoading = state.isLoading || state.isLoadingFavoriteMessages,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state.errorMessage?.let { message ->
                item(key = "favorite-error", contentType = "favorite-status") {
                    FavoriteStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            state.favoriteMessage?.let { message ->
                item(key = "favorite-message", contentType = "favorite-status") {
                    FavoriteStatusRow(text = message)
                }
            }
            if (selectedTab == FavoriteTab.Posts) {
                favoritePostItems(
                    state = state,
                    onRefresh = onRefresh,
                    onOpenNote = onOpenNote,
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
                    isActionPending = isActionPending,
                    canDeleteAuthor = canDeleteAuthor,
                    noteRowDensity = noteRowDensity,
                )
            } else {
                favoriteMessageItems(
                    state = state,
                    onOpenUser = onOpenUser,
                    onOpenMediaPreview = onOpenMediaPreview,
                    onOpenMention = onOpenMention,
                    onOpenHashtag = onOpenHashtag,
                    onRemoveFavoriteMessage = onRemoveFavoriteMessage,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoritePostItems(
    state: FavoriteNoteUiState,
    onRefresh: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onReply: (String) -> Unit,
    onRenote: (String) -> Unit,
    onQuote: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onDeleteReaction: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
    onAddToClip: ((Note) -> Unit)?,
    onDelete: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onVotePoll: (String, Int) -> Unit,
    reactionOptions: List<String>,
    recentReactions: List<String>,
    isActionPending: (String) -> Boolean,
    canDeleteAuthor: (String) -> Boolean,
    noteRowDensity: NoteRowDensity,
) {
    if (state.isLoading && state.favorites.isEmpty()) {
        item(key = "favorite-loading", contentType = "favorite-status") {
            FavoriteStatusRow(text = "正在加载收藏帖子...", loading = true)
        }
    }
    if (!state.isLoading && state.favorites.isEmpty() && state.errorMessage == null) {
        item(key = "favorite-empty", contentType = "favorite-status") { FavoriteStatusRow(text = "暂无收藏帖子") }
    }
    items(
        items = state.favorites,
        key = { it.id },
        contentType = { "favorite-note" },
    ) { favorite ->
        NoteRow(
            note = favorite.note,
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
            isActionPending = isActionPending(favorite.note.id),
            canDelete = canDeleteAuthor(favorite.note.author.id),
            density = noteRowDensity,
        )
    }
    if (state.favorites.isNotEmpty() && state.isLoadingMore) {
        item(key = "favorite-loading-more", contentType = "favorite-status") {
            FavoriteStatusRow(
                text = "正在加载更多...",
                loading = state.isLoadingMore,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.favoriteMessageItems(
    state: FavoriteNoteUiState,
    onOpenUser: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRemoveFavoriteMessage: (String) -> Unit,
) {
    if (state.isLoadingFavoriteMessages && state.favoriteMessages.isEmpty()) {
        item(key = "favorite-message-loading", contentType = "favorite-status") {
            FavoriteStatusRow(text = "正在加载收藏信息...", loading = true)
        }
    }
    if (!state.isLoadingFavoriteMessages && state.favoriteMessages.isEmpty()) {
        item(key = "favorite-message-empty", contentType = "favorite-status") {
            FavoriteStatusRow(text = "暂无收藏信息")
        }
    }
    items(
        items = state.favoriteMessages,
        key = { it.id },
        contentType = { "favorite-message" },
    ) { favorite ->
        FavoriteMessageRow(
            favorite = favorite,
            onOpenUser = onOpenUser,
            onOpenMediaPreview = onOpenMediaPreview,
            onOpenMention = onOpenMention,
            onOpenHashtag = onOpenHashtag,
            onRemove = { onRemoveFavoriteMessage(favorite.id) },
        )
        HhhlDivider()
    }
}

@Composable
private fun FavoriteSummaryRow(
    selectedTab: FavoriteTab,
    onTabSelected: (FavoriteTab) -> Unit,
    postCount: Int,
    messageCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlSegmentedControl(modifier = Modifier.weight(1f)) {
            HhhlSegmentedItem(
                label = FavoriteTab.Posts.label,
                selected = selectedTab == FavoriteTab.Posts,
                badgeCount = postCount,
                onClick = { onTabSelected(FavoriteTab.Posts) },
                modifier = Modifier.weight(1f),
            )
            HhhlSegmentedItem(
                label = FavoriteTab.Messages.label,
                selected = selectedTab == FavoriteTab.Messages,
                badgeCount = messageCount,
                onClick = { onTabSelected(FavoriteTab.Messages) },
                modifier = Modifier.weight(1f),
            )
        }
        HhhlIconActionButton(
            icon = Icons.Filled.Refresh,
            contentDescription = if (isLoading) "同步收藏中" else "刷新收藏",
            emphasized = true,
            enabled = !isLoading,
            onClick = onRefresh,
        )
    }
    val summary = when (selectedTab) {
        FavoriteTab.Posts -> if (postCount == 0) "暂无收藏帖子" else "$postCount 条收藏帖子"
        FavoriteTab.Messages -> if (messageCount == 0) "暂无收藏信息" else "$messageCount 条收藏信息"
    }
    Text(
        text = summary,
        color = colors.textMuted,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FavoriteMessageRow(
    favorite: FavoriteMessage,
    onOpenUser: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val message = favorite.message
    val body = favoriteMessageBody(message)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.fromUser.displayName.ifBlank { message.fromUser.username.ifBlank { "未知用户" } },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenUser(message.fromUser.id) },
                )
                Text(
                    text = listOf(favorite.conversationType.label, favorite.conversationTitle, message.createdAtLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlIconActionButton(
                icon = Icons.Filled.Delete,
                contentDescription = "取消收藏信息",
                onClick = onRemove,
            )
        }
        FavoriteMessageReferenceBlocks(message = message)
        InlineRichText(
            text = body,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
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
                        val session = driveFileMediaPreviewSession(files = listOf(file), selectedId = file.id)
                        if (session.items.isNotEmpty() && onOpenMediaPreview != null) {
                            onOpenMediaPreview(session)
                        } else {
                            // Keep the preview clickable even when the caller cannot open a full overlay.
                            openUrl.isNotBlank()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp),
                )
                Text(
                    text = mediaTypeDisplayName(file.type, file.name),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (message.reactions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.reactions.forEach { reaction ->
                    FavoriteMessageReactionChip(reaction.reaction, reaction.count)
                }
            }
        }
        Text(
            text = "收藏于 ${favorite.savedAtLabel}",
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FavoriteMessageReferenceBlocks(message: ChatMessage) {
    message.reply?.let { reference ->
        FavoriteMessageReferenceBlock(label = "回复", text = reference.text, fallback = reference.file?.name)
    }
    message.quote?.let { reference ->
        FavoriteMessageReferenceBlock(label = "引用", text = reference.text, fallback = reference.file?.name)
    }
}

@Composable
private fun FavoriteMessageReferenceBlock(
    label: String,
    text: String,
    fallback: String?,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.quoteBackground.copy(alpha = 0.72f))
            .border(1.dp, colors.border.copy(alpha = 0.42f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        InlineRichText(
            text = richTextPlainPreviewText(text).ifBlank { fallback.orEmpty().ifBlank { "原消息不可用" } },
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxChars = 180,
        )
    }
}

@Composable
private fun FavoriteMessageReactionChip(
    reaction: String,
    count: Int,
) {
    val colors = LocalHhhlColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.noteReactionBackground.copy(alpha = 0.86f))
            .border(1.dp, colors.border.copy(alpha = 0.46f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        CustomEmojiReactionLabel(reaction = reaction)
        Text(
            text = count.toString(),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FavoriteStatusRow(
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

private fun favoriteScreenSupportingText(
    state: FavoriteNoteUiState,
    selectedTab: FavoriteTab,
): String {
    return when {
        state.isLoading || state.isLoadingFavoriteMessages -> "同步中"
        selectedTab == FavoriteTab.Posts && state.favorites.isEmpty() -> "收藏帖子"
        selectedTab == FavoriteTab.Posts -> "${state.favorites.size} 条收藏帖子"
        state.favoriteMessages.isEmpty() -> "本机收藏信息"
        else -> "${state.favoriteMessages.size} 条收藏信息"
    }
}

private fun favoriteMessageBody(message: ChatMessage): String {
    return message.text.takeIf { it.isNotBlank() }
        ?: message.file?.name?.takeIf { it.isNotBlank() }
        ?: "附件消息"
}

fun favoriteTabLabels(): List<String> = FavoriteTab.entries.map { it.label }
