package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.FavoriteNoteUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun FavoriteNoteScreen(
    state: FavoriteNoteUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
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
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = state.favorites.size,
        isLoadingMore = state.isLoadingMore || state.endReached,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "收藏",
            supportingText = when {
                state.isLoading -> "同步中"
                state.favorites.isEmpty() -> "收藏时间线"
                else -> "${state.favorites.size} 条收藏"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        FavoriteNoteSummaryRow(
            favoriteCount = state.favorites.size,
            isLoading = state.isLoading,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state.errorMessage?.let { message ->
                item(key = "favorite-error", contentType = "favorite-status") {
                    FavoriteNoteStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state.isLoading && state.favorites.isEmpty()) {
                item(key = "favorite-loading", contentType = "favorite-status") {
                    FavoriteNoteStatusRow(text = "正在加载收藏...", loading = true)
                }
            }
            if (!state.isLoading && state.favorites.isEmpty() && state.errorMessage == null) {
                item(key = "favorite-empty", contentType = "favorite-status") { FavoriteNoteStatusRow(text = "暂无收藏") }
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
                    FavoriteNoteStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteNoteSummaryRow(
    favoriteCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val countText = if (favoriteCount == 0) "暂无收藏" else "${favoriteCount} 条已加载"
    val stateText = if (isLoading) "加载中" else "时间线视图"
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
        HhhlIconActionButton(
            icon = Icons.Filled.Refresh,
            contentDescription = if (isLoading) "同步收藏中" else "刷新收藏",
            emphasized = true,
            enabled = !isLoading,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun FavoriteNoteStatusRow(
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
