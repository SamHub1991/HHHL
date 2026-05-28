package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteReactionUser
import cc.hhhl.client.model.NoteVersion
import cc.hhhl.client.state.NoteDetailUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun NoteDetailScreen(
    noteId: String,
    onBack: () -> Unit,
    state: NoteDetailUiState? = null,
    onRefresh: () -> Unit = {},
    onLoadMoreReplies: () -> Unit = {},
    onLoadConversation: () -> Unit = {},
    onLoadRenotes: () -> Unit = {},
    onLoadReactionUsers: () -> Unit = {},
    onLoadVersions: () -> Unit = {},
    onRefreshPollRecommendation: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onToggleChildReplies: (String) -> Unit = {},
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
    val selected = state?.note
    val replyCount = state?.replies?.size ?: 0
    val canLoadMoreReplies = when {
        state == null -> false
        state.isLoadingMoreReplies -> false
        else -> replyCount > 0
    }
    val replyThreadData = remember(
        selected?.id,
        state?.replies,
        state?.childRepliesByParentId,
        state?.expandedReplyIds,
    ) {
        selected?.let { note ->
            val replyNotes = state?.replies.orEmpty()
            val childRepliesByParentId = state?.childRepliesByParentId.orEmpty()
            noteReplyThreadData(
                rootNote = note,
                replies = replyNotes,
                childRepliesByParentId = childRepliesByParentId,
                expandedReplyIds = state?.expandedReplyIds.orEmpty(),
            )
        }
    }
    val listState = rememberLazyListState()
    var lastAutoLoadReplyCount by remember(noteId) { mutableStateOf(0) }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = replyThreadData?.visibleReplies?.size ?: 0,
        isLoadingMore = state?.isLoadingMoreReplies == true || !canLoadMoreReplies,
        onLoadMore = {
            val currentCount = state?.replies?.size ?: 0
            if (currentCount != lastAutoLoadReplyCount) {
                lastAutoLoadReplyCount = currentCount
                onLoadMoreReplies()
            }
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "帖子",
            supportingText = selected?.author?.displayName,
            navigation = {
                HhhlBackButton(onClick = onBack)
            },
        )
        HhhlDivider()
        NoteDetailSummaryRow(
            note = selected,
            isLoading = state?.isLoading == true,
            replyCount = replyCount,
            isLoadingReplies = state?.isLoadingReplies == true || state?.isLoadingMoreReplies == true,
            canLoadMoreReplies = canLoadMoreReplies,
            onRefresh = onRefresh,
            onLoadMoreReplies = onLoadMoreReplies,
        )
        HhhlDivider()
        LazyColumn(state = listState) {
            if (state?.isLoading == true && selected == null) {
                item(key = "note-detail-loading", contentType = "note-detail-status") {
                    NoteDetailStatusRow(text = "正在加载帖子...", loading = true)
                }
            }
            state?.errorMessage?.let { message ->
                item(key = "note-detail-error", contentType = "note-detail-status") {
                    NoteDetailStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state != null && !state.isLoading && selected == null && state.errorMessage == null) {
                item(key = "note-detail-missing", contentType = "note-detail-status") {
                    NoteDetailStatusRow(text = "无法打开帖子")
                }
            }
            selected?.let { note ->
                val replyData = replyThreadData ?: NoteReplyThreadData()
                val replyRowDensity = NoteRowDensity.Compact
                item(key = "note-detail-root-${note.id}", contentType = "note-detail-root") {
                    NoteRow(
                        note = note,
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
                        isActionPending = isActionPending(note.id),
                        canDelete = canDeleteAuthor(note.author.id),
                        density = noteRowDensity,
                    )
                }
                item(key = "note-detail-actions", contentType = "note-detail-actions") {
                    NoteDetailActionPanel(
                        state = state,
                        onLoadConversation = onLoadConversation,
                        onLoadRenotes = onLoadRenotes,
                        onLoadReactionUsers = onLoadReactionUsers,
                        onLoadVersions = onLoadVersions,
                        onRefreshPollRecommendation = onRefreshPollRecommendation,
                        onTranslate = onTranslate,
                    )
                }
                state.translation?.let { translation ->
                    item(key = "note-detail-translation", contentType = "note-detail-panel") {
                        NoteDetailTextPanel(
                            title = "翻译",
                            text = translation.text.ifBlank { "暂无翻译内容" },
                        )
                    }
                }
                if (state.conversationNotes.isNotEmpty()) {
                    item(key = "note-detail-context-title", contentType = "note-detail-section") {
                        NoteDetailSectionTitle(title = "上下文", count = state.conversationNotes.size)
                    }
                    items(
                        items = state.conversationNotes,
                        key = { "detail-context-${it.id}" },
                        contentType = { "note-detail-context" },
                    ) { contextNote ->
                        NoteRow(
                            note = contextNote,
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
                            isActionPending = isActionPending(contextNote.id),
                            canDelete = canDeleteAuthor(contextNote.author.id),
                            density = NoteRowDensity.Compact,
                        )
                    }
                }
                if (state.reactionUsers.isNotEmpty()) {
                    item(key = "note-detail-reaction-users", contentType = "note-detail-panel") {
                        NoteReactionUsersPanel(
                            users = state.reactionUsers,
                            onOpenUser = onOpenUser,
                        )
                    }
                }
                if (state.versions.isNotEmpty()) {
                    item(key = "note-detail-versions", contentType = "note-detail-panel") {
                        NoteVersionsPanel(versions = state.versions)
                    }
                }
                if (state.renoteNotes.isNotEmpty()) {
                    item(key = "note-detail-renotes-title", contentType = "note-detail-section") {
                        NoteDetailSectionTitle(title = "转发", count = state.renoteNotes.size)
                    }
                    items(
                        items = state.renoteNotes,
                        key = { "detail-renote-${it.id}" },
                        contentType = { "note-detail-renote" },
                    ) { renote ->
                        NoteRow(
                            note = renote,
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
                            isActionPending = isActionPending(renote.id),
                            canDelete = canDeleteAuthor(renote.author.id),
                            density = NoteRowDensity.Compact,
                        )
                    }
                }
                item(key = "note-detail-header", contentType = "note-detail-header") {
                    NoteReplyTreeHeader(
                        replyCount = replyData.replies.size,
                    )
                }
                if (state.isLoadingReplies && state.replies.isEmpty()) {
                    item(key = "note-detail-loading-replies", contentType = "note-detail-status") {
                        NoteDetailStatusRow(text = "正在加载回复...", loading = true)
                    }
                }
                state.repliesErrorMessage?.let { message ->
                    item(key = "note-detail-replies-error", contentType = "note-detail-status") {
                        NoteDetailStatusRow(
                            text = message,
                            actionText = "重试",
                            onAction = onRefresh,
                        )
                    }
                }
                if (
                    !state.isLoadingReplies &&
                    state.replies.isEmpty() &&
                    state.repliesErrorMessage == null
                ) {
                    item(key = "note-detail-empty-replies", contentType = "note-detail-status") {
                        NoteDetailStatusRow(text = "暂无回复")
                    }
                }
                items(
                    items = replyData.visibleReplies,
                    key = { "detail-reply-${it.id}" },
                    contentType = { "note-detail-reply" },
                ) { reply ->
                    ReplyTreeNoteRow(
                        presentation = replyData.presentationsByReplyId.getValue(reply.id),
                    ) {
                        NoteRow(
                            note = reply,
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
                            isActionPending = isActionPending(reply.id),
                            canDelete = canDeleteAuthor(reply.author.id),
                            density = replyRowDensity,
                        )
                    }
                    ReplyTreeChildControl(
                        reply = reply,
                        state = state,
                        onToggleChildReplies = onToggleChildReplies,
                    )
                }
                if (state.replies.isNotEmpty() && state.isLoadingMoreReplies) {
                    item(key = "note-detail-load-more-replies", contentType = "note-detail-status") {
                        NoteDetailStatusRow(
                            text = "正在加载更多回复...",
                            loading = true,
                        )
                    }
                }
            }
        }
    }
}

private data class NoteReplyThreadData(
    val replies: List<Note> = emptyList(),
    val visibleReplies: List<Note> = emptyList(),
    val presentationsByReplyId: Map<String, NoteReplyTreePresentation> = emptyMap(),
)

@Composable
private fun NoteDetailActionPanel(
    state: NoteDetailUiState,
    onLoadConversation: () -> Unit,
    onLoadRenotes: () -> Unit,
    onLoadReactionUsers: () -> Unit,
    onLoadVersions: () -> Unit,
    onRefreshPollRecommendation: () -> Unit,
    onTranslate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HhhlActionChip(
                label = if (state.isTranslating) "翻译中" else "翻译",
                enabled = !state.isTranslating,
                onClick = onTranslate,
            )
            HhhlActionChip(
                label = if (state.isLoadingConversation) "同步上下文" else "上下文",
                enabled = !state.isLoadingConversation,
                onClick = onLoadConversation,
            )
            HhhlActionChip(
                label = if (state.isLoadingReactions) "同步反应" else "反应用户",
                enabled = !state.isLoadingReactions,
                onClick = onLoadReactionUsers,
            )
            HhhlActionChip(
                label = if (state.isLoadingRenotes) "同步转发" else "转发列表",
                enabled = !state.isLoadingRenotes,
                onClick = onLoadRenotes,
            )
            HhhlActionChip(
                label = if (state.isLoadingVersions) "同步编辑" else "编辑记录",
                enabled = !state.isLoadingVersions,
                onClick = onLoadVersions,
            )
            if (state.note?.poll != null) {
                HhhlActionChip(
                    label = if (state.isRefreshingPollRecommendation) "刷新中" else "投票推荐",
                    enabled = !state.isRefreshingPollRecommendation,
                    onClick = onRefreshPollRecommendation,
                )
            }
        }
        state.detailActionMessage?.let { message ->
            Text(
                text = message,
                color = LocalHhhlColors.current.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HhhlDivider()
}

@Composable
private fun NoteVersionsPanel(versions: List<NoteVersion>) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoteDetailSectionTitle(title = "编辑记录", count = versions.size, padded = false)
        versions.take(5).forEach { version ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = version.createdAtLabel.ifBlank { "编辑记录" },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = version.text.ifBlank { version.cw ?: "无正文" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NoteDetailTextPanel(
    title: String,
    text: String,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        emphasized = true,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NoteReactionUsersPanel(
    users: List<NoteReactionUser>,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoteDetailSectionTitle(title = "反应用户", count = users.size, padded = false)
        users.take(12).forEach { reactionUser ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUser(reactionUser.user.id) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    initial = reactionUser.user.displayName,
                    avatarUrl = reactionUser.user.avatarUrl,
                    avatarDecorations = reactionUser.user.avatarDecorations,
                    size = 32.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reactionUser.user.displayName,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${reactionUser.user.username}",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = reactionUser.reaction,
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteDetailSectionTitle(
    title: String,
    count: Int,
    padded: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (padded) Modifier.padding(horizontal = 14.dp, vertical = 8.dp) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
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

private data class NoteReplyThreadIndex(
    val rootNoteId: String,
    val allReplies: List<Note>,
    val knownIds: Set<String>,
    val childrenByParentId: Map<String, List<Note>>,
)

private fun noteReplyThreadData(
    rootNote: Note,
    replies: List<Note>,
    childRepliesByParentId: Map<String, List<Note>>,
    expandedReplyIds: Set<String>,
): NoteReplyThreadData {
    val index = noteReplyThreadIndex(rootNote.id, replies, childRepliesByParentId)
    val visibleReplies = visibleReplyThread(index, expandedReplyIds)
    val notesById = LinkedHashMap<String, Note>(index.allReplies.size + 1)
    notesById[rootNote.id] = rootNote
    index.allReplies.forEach { reply ->
        if (!notesById.containsKey(reply.id)) {
            notesById[reply.id] = reply
        }
    }
    val presentationsByReplyId = LinkedHashMap<String, NoteReplyTreePresentation>(visibleReplies.size)
    visibleReplies.forEach { reply ->
        presentationsByReplyId[reply.id] = noteReplyTreePresentation(rootNote.id, reply, notesById)
    }
    return NoteReplyThreadData(
        replies = replies,
        visibleReplies = visibleReplies,
        presentationsByReplyId = presentationsByReplyId,
    )
}

@Composable
private fun ReplyTreeChildControl(
    reply: Note,
    state: NoteDetailUiState?,
    onToggleChildReplies: (String) -> Unit,
) {
    val isExpanded = state?.expandedReplyIds?.contains(reply.id) == true
    val isLoading = state?.loadingChildReplyIds?.contains(reply.id) == true
    val errorMessage = state?.childReplyErrors?.get(reply.id)
    val loadedChildrenCount = state?.childRepliesByParentId?.get(reply.id)?.size ?: 0
    val canShowControl = reply.replyCount > 0 || isExpanded || isLoading || errorMessage != null || loadedChildrenCount > 0
    if (!canShowControl) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp, end = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlActionChip(
            label = when {
                isLoading -> "加载子回复中"
                isExpanded -> "收起子回复"
                loadedChildrenCount > 0 -> "展开 $loadedChildrenCount 条子回复"
                reply.replyCount > 0 -> "展开 ${reply.replyCount} 条子回复"
                else -> "重试子回复"
            },
            enabled = !isLoading,
            onClick = { onToggleChildReplies(reply.id) },
        )
        errorMessage?.let {
            val colors = LocalHhhlColors.current
            Text(
                text = it,
                color = colors.danger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NoteDetailSummaryRow(
    note: Note?,
    isLoading: Boolean,
    replyCount: Int,
    isLoadingReplies: Boolean,
    canLoadMoreReplies: Boolean,
    onRefresh: () -> Unit,
    onLoadMoreReplies: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val authorText = note?.author?.displayName ?: "帖子详情"
    val replyText = when {
        isLoading -> "正在同步帖子"
        isLoadingReplies -> "正在同步回复"
        replyCount == 0 -> "暂无回复"
        else -> "$replyCount 条回复"
    }
    val usernameText = note?.let { " · @${it.author.username}" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$authorText$usernameText · $replyText",
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
                contentDescription = if (isLoading) "同步中" else "刷新帖子",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
            HhhlActionChip(
                label = if (isLoadingReplies) "回复同步中" else "更多回复",
                enabled = canLoadMoreReplies && !isLoadingReplies,
                onClick = onLoadMoreReplies,
            )
        }
    }
}

@Composable
private fun NoteReplyTreeHeader(
    replyCount: Int,
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
            text = "回复树",
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (replyCount == 0) "0" else replyCount.toString(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HhhlDivider()
}

@Composable
private fun ReplyTreeNoteRow(
    presentation: NoteReplyTreePresentation,
    content: @Composable () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val treeLineColor = colors.noteTreeLine
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = presentation.startPaddingDp.dp)
            .drawBehind {
                if (presentation.railCount > 0) {
                    val strokeWidth = NOTE_REPLY_RAIL_STROKE_DP.dp.toPx()
                    val railSpacing = NOTE_REPLY_RAIL_SPACING_DP.dp.toPx()
                    val railTopInset = 4.dp.toPx()
                    val railBottomInset = 4.dp.toPx()
                    repeat(presentation.railCount) { index ->
                        val isActiveRail = index == presentation.railCount - 1
                        val x = (strokeWidth / 2f) + index * (strokeWidth + railSpacing)
                        drawLine(
                            color = when {
                                presentation.isDepthCollapsed -> treeLineColor.copy(alpha = 0.18f)
                                isActiveRail -> treeLineColor.copy(alpha = 0.78f)
                                else -> treeLineColor.copy(alpha = 0.46f)
                            },
                            start = Offset(x, railTopInset),
                            end = Offset(x, size.height - railBottomInset),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    ) {
        if (presentation.railCount > 0) {
            Spacer(modifier = Modifier.width((presentation.railGutterWidthDp + 10).dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

data class NoteReplyTreePresentation(
    val depth: Int,
    val visualDepth: Int,
    val railCount: Int,
    val startPaddingDp: Int,
    val railGutterWidthDp: Int,
    val isDepthCollapsed: Boolean,
)

fun noteReplyTreePresentation(
    rootNoteId: String,
    reply: Note,
    notesById: Map<String, Note>,
    maxDepth: Int = 4,
    maxVisualDepth: Int = 3,
): NoteReplyTreePresentation {
    val safeMaxVisualDepth = maxVisualDepth.coerceAtLeast(1)
    val depth = noteReplyDepth(rootNoteId, reply, notesById, maxDepth = maxDepth.coerceAtLeast(1))
    val visualDepth = depth.coerceIn(1, safeMaxVisualDepth)
    val railCount = visualDepth
    return NoteReplyTreePresentation(
        depth = depth,
        visualDepth = visualDepth,
        railCount = railCount,
        startPaddingDp = replyTreeStartPaddingDp(depth, maxVisualDepth = safeMaxVisualDepth),
        railGutterWidthDp = replyTreeRailGutterWidthDp(
            railCount = railCount,
            strokeWidthDp = NOTE_REPLY_RAIL_STROKE_DP,
            railSpacingDp = NOTE_REPLY_RAIL_SPACING_DP,
        ),
        isDepthCollapsed = depth > visualDepth,
    )
}

fun visibleReplyThread(
    rootNoteId: String,
    replies: List<Note>,
    childRepliesByParentId: Map<String, List<Note>>,
    expandedReplyIds: Set<String>,
): List<Note> {
    return visibleReplyThread(
        index = noteReplyThreadIndex(rootNoteId, replies, childRepliesByParentId),
        expandedReplyIds = expandedReplyIds,
    )
}

private fun noteReplyThreadIndex(
    rootNoteId: String,
    replies: List<Note>,
    childRepliesByParentId: Map<String, List<Note>>,
): NoteReplyThreadIndex {
    val repliesById = LinkedHashMap<String, Note>()
    replies.forEach { reply ->
        if (!repliesById.containsKey(reply.id)) {
            repliesById[reply.id] = reply
        }
    }
    childRepliesByParentId.values.forEach { childReplies ->
        childReplies.forEach { reply ->
            if (!repliesById.containsKey(reply.id)) {
                repliesById[reply.id] = reply
            }
        }
    }

    val knownIds = HashSet<String>(repliesById.size + 1)
    knownIds.add(rootNoteId)
    knownIds.addAll(repliesById.keys)
    val childrenByParent = LinkedHashMap<String, MutableList<Note>>()
    repliesById.values.forEach { reply ->
        val parentId = reply.replyId
        if (!parentId.isNullOrBlank()) {
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(reply)
        }
    }

    return NoteReplyThreadIndex(
        rootNoteId = rootNoteId,
        allReplies = repliesById.values.toList(),
        knownIds = knownIds,
        childrenByParentId = childrenByParent,
    )
}

private fun visibleReplyThread(
    index: NoteReplyThreadIndex,
    expandedReplyIds: Set<String>,
): List<Note> {
    val visible = ArrayList<Note>(index.allReplies.size)
    val seen = HashSet<String>(index.allReplies.size)

    fun append(note: Note) {
        if (!seen.add(note.id)) return
        visible.add(note)
        if (note.id !in expandedReplyIds) return
        index.childrenByParentId[note.id].orEmpty().forEach(::append)
    }

    index.allReplies.forEach { reply ->
        if (reply.replyId == index.rootNoteId || reply.replyId !in index.knownIds) {
            append(reply)
        }
    }
    return visible
}

fun noteReplyDepth(
    rootNoteId: String,
    reply: Note,
    notesById: Map<String, Note>,
    maxDepth: Int = 4,
): Int {
    var depth = 0
    var parentId = reply.replyId
    val seen = mutableSetOf(reply.id)
    while (!parentId.isNullOrBlank() && parentId !in seen && depth < maxDepth) {
        depth += 1
        if (parentId == rootNoteId) return depth
        seen += parentId
        parentId = notesById[parentId]?.replyId
    }
    return depth.coerceAtLeast(1).coerceAtMost(maxDepth)
}

fun replyTreeStartPaddingDp(
    depth: Int,
    maxVisualDepth: Int = 4,
    indentDp: Int = NOTE_REPLY_START_INDENT_DP,
): Int {
    return when {
        depth <= 1 -> 0
        else -> ((depth - 1).coerceAtMost(maxVisualDepth.coerceAtLeast(1) - 1)) * indentDp.coerceAtLeast(0)
    }
}

fun replyTreeRailGutterWidthDp(
    railCount: Int,
    strokeWidthDp: Int = 2,
    railSpacingDp: Int = 6,
): Int {
    return when {
        railCount <= 0 -> 0
        else -> (railCount * strokeWidthDp.coerceAtLeast(1)) + ((railCount - 1) * railSpacingDp.coerceAtLeast(0))
    }
}

const val NOTE_REPLY_START_INDENT_DP = 8
const val NOTE_REPLY_RAIL_STROKE_DP = 1
const val NOTE_REPLY_RAIL_SPACING_DP = 7

@Composable
private fun NoteDetailStatusRow(
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
