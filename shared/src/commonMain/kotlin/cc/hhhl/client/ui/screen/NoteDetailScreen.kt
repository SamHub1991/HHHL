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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.NoteDetailUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
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
    val selected = state?.note ?: if (state == null) {
        FakeData.timeline.firstOrNull { it.id == noteId } ?: FakeData.timeline.first()
    } else {
        null
    }
    val replyCount = state?.replies?.size ?: FakeData.timeline.count { it.id != selected?.id }
    val canLoadMoreReplies = when {
        state == null -> replyCount > 0
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
            val replyNotes = state?.replies ?: FakeData.timeline.filterNot { it.id == note.id }
            val childRepliesByParentId = state?.childRepliesByParentId.orEmpty()
            val visibleReplies = visibleReplyThread(
                rootNoteId = note.id,
                replies = replyNotes,
                childRepliesByParentId = childRepliesByParentId,
                expandedReplyIds = state?.expandedReplyIds.orEmpty(),
            )
            val notesById = (listOf(note) + replyNotes + childRepliesByParentId.values.flatten())
                .associateBy { it.id }
            val presentationsByReplyId = visibleReplies.associate { reply ->
                reply.id to noteReplyTreePresentation(note.id, reply, notesById)
            }
            NoteReplyThreadData(
                replies = replyNotes,
                visibleReplies = visibleReplies,
                presentationsByReplyId = presentationsByReplyId,
            )
        }
    }

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
        LazyColumn {
            if (state?.isLoading == true && selected == null) {
                item { NoteDetailStatusRow(text = "正在加载帖子...", loading = true) }
            }
            state?.errorMessage?.let { message ->
                item {
                    NoteDetailStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state != null && !state.isLoading && selected == null && state.errorMessage == null) {
                item { NoteDetailStatusRow(text = "无法打开帖子") }
            }
            selected?.let { note ->
                val replyData = replyThreadData ?: NoteReplyThreadData()
                val replyRowDensity = NoteRowDensity.Compact
                item {
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
                item {
                    NoteReplyTreeHeader(
                        replyCount = replyData.replies.size,
                        isLoading = state?.isLoadingReplies == true || state?.isLoadingMoreReplies == true,
                    )
                }
                if (state == null) {
                    items(replyData.visibleReplies, key = { "reply-${it.id}" }) { reply ->
                        ReplyTreeNoteRow(
                            presentation = replyData.presentationsByReplyId.getValue(reply.id),
                        ) {
                            NoteRow(
                                note = reply,
                                onClick = onOpenNote,
                                onOpenUser = onOpenUser,
                                onOpenMedia = onOpenMedia,
                                onOpenMediaPreview = onOpenMediaPreview,
                                onOpenMention = onOpenMention,
                                onOpenHashtag = onOpenHashtag,
                                onVotePoll = onVotePoll,
                                recentReactions = recentReactions,
                                density = replyRowDensity,
                            )
                        }
                        ReplyTreeChildControl(
                            reply = reply,
                            state = null,
                            onToggleChildReplies = onToggleChildReplies,
                        )
                    }
                } else {
                    if (state.isLoadingReplies && state.replies.isEmpty()) {
                        item { NoteDetailStatusRow(text = "正在加载回复...", loading = true) }
                    }
                    state.repliesErrorMessage?.let { message ->
                        item {
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
                        item { NoteDetailStatusRow(text = "暂无回复") }
                    }
                    items(replyData.visibleReplies, key = { "detail-reply-${it.id}" }) { reply ->
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
                    if (state.replies.isNotEmpty()) {
                        item {
                            NoteDetailStatusRow(
                                text = if (state.isLoadingMoreReplies) "正在加载更多回复..." else "加载更多回复",
                                loading = state.isLoadingMoreReplies,
                                onAction = if (state.isLoadingMoreReplies) null else onLoadMoreReplies,
                            )
                        }
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
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
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
                label = if (isLoading) "同步中" else "刷新帖子",
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
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "回复树",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isLoading) "按父级关系整理中" else "回复直接展示在对应父帖下方",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = if (replyCount == 0) "0" else replyCount.toString(),
            color = MaterialTheme.colorScheme.secondary,
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val dividerColor = LocalHhhlColors.current.divider
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
                                presentation.isDepthCollapsed -> secondaryColor.copy(alpha = 0.10f)
                                isActiveRail -> primaryColor.copy(alpha = 0.16f)
                                else -> dividerColor.copy(alpha = 0.58f)
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
    val loadedReplies = childRepliesByParentId.values.flatten()
    val allReplies = (replies + loadedReplies).distinctBy { it.id }
    val knownIds = allReplies.mapTo(mutableSetOf()) { it.id } + rootNoteId
    val childrenByParent = allReplies
        .filter { it.replyId != null }
        .groupBy { it.replyId.orEmpty() }
    val roots = allReplies.filter { reply ->
        reply.replyId == rootNoteId || reply.replyId !in knownIds
    }
    val visible = mutableListOf<Note>()
    val seen = mutableSetOf<String>()

    fun append(note: Note) {
        if (!seen.add(note.id)) return
        visible.add(note)
        if (note.id !in expandedReplyIds) return
        childrenByParent[note.id].orEmpty().forEach(::append)
    }

    roots.forEach(::append)
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
