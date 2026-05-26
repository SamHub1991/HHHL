package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.theme.LocalHhhlColors

internal val HhhlNoteActionMinHeight = HhhlControlMinHeight
internal val HhhlNoteActionMinWidth = HhhlControlMinWidth
internal val HhhlNoteActionHorizontalPadding = 6.dp
internal val HhhlNoteActionVerticalPadding = 5.dp
internal val HhhlNoteActionIconSize = 17.dp
internal val HhhlNoteActionSpacing = 4.dp

@Composable
fun NoteRow(
    note: Note,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onReply: (String) -> Unit = {},
    onRenote: (String) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> },
    onDeleteReaction: (String, String) -> Unit = { _, _ -> },
    onVotePoll: (String, Int) -> Unit = { _, _ -> },
    onFavorite: (String) -> Unit = {},
    onAddToClip: ((Note) -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenUrl: (String) -> Unit = onOpenMedia,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    reactionOptions: List<String> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: Boolean = false,
    canDelete: Boolean = false,
    density: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    var reactionMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var reactionSearchQuery by remember(note.id) { mutableStateOf("") }
    var renoteMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var deleteConfirmOpen by remember(note.id) { mutableStateOf(false) }
    var contentExpanded by remember(note.id, note.cw) { mutableStateOf(note.cw.isNullOrBlank()) }
    val reactionSections = remember(reactionOptions, reactionSearchQuery, recentReactions) {
        reactionPickerSections(
            reactionOptions = reactionOptions,
            query = reactionSearchQuery,
            recentReactions = recentReactions,
        )
    }
    val isContentVisible = noteContentVisible(note, expanded = contentExpanded)
    val metrics = noteRowMetrics(density)
    val canAddToClip = onAddToClip != null
    val overflowActions = remember(canAddToClip, canDelete) {
        noteOverflowActions(
            canAddToClip = canAddToClip,
            canDelete = canDelete,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(note.id) },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = metrics.horizontalPadding.dp,
                vertical = metrics.verticalPadding.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                initial = note.author.avatarInitial,
                avatarUrl = note.author.avatarUrl,
                size = metrics.avatarSize.dp,
                modifier = Modifier
                    .clickable { onOpenUser(note.author.id) },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.author.displayName,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(min = 0.dp)
                            .clickable { onOpenUser(note.author.id) },
                    )
                    Text(
                        text = "  @${note.author.username} · ${note.createdAtLabel}",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp)
                            .clickable { onOpenUser(note.author.id) },
                    )
                }
                if (note.isRenote) {
                    Text(
                        text = "转发",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                noteVisibilityBadge(note.visibility)?.let { badge ->
                    Text(
                        text = badge,
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                note.cw?.takeIf { it.isNotBlank() }?.let { cw ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = cw,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (contentExpanded) "收起内容" else "显示内容",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { contentExpanded = !contentExpanded },
                        )
                    }
                }
                if (isContentVisible) {
                    InlineRichText(
                        text = note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        onOpenUrl = onOpenUrl,
                        onOpenMention = onOpenMention,
                        onOpenHashtag = onOpenHashtag,
                    )
                    if (note.media.isNotEmpty()) {
                        MediaStrip(
                            note = note,
                            onOpenMedia = onOpenMedia,
                            onOpenMediaPreview = onOpenMediaPreview,
                            mediaHeight = metrics.mediaHeight,
                        )
                    }
                    if (note.reactions.isNotEmpty()) {
                        ReactionStrip(note)
                    }
                    note.quotedNote?.let { quoted ->
                        QuotedNoteCard(
                            note = quoted,
                            onOpenNote = onClick,
                            onOpenUser = onOpenUser,
                            onOpenUrl = onOpenUrl,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                        )
                    }
                    note.poll?.let {
                        PollStrip(
                            note = note,
                            enabled = !isActionPending,
                            onVotePoll = onVotePoll,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoteActionButton(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        count = note.replyCount,
                        contentDescription = "回复",
                        enabled = !isActionPending,
                        onClick = { onReply(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteActionButton(
                        icon = Icons.Outlined.Repeat,
                        count = note.renoteCount,
                        contentDescription = "转发",
                        enabled = !isActionPending,
                        onClick = { renoteMenuExpanded = true },
                        modifier = Modifier.weight(1f),
                    )
                    DropdownMenu(
                        expanded = renoteMenuExpanded,
                        onDismissRequest = { renoteMenuExpanded = false },
                        offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
                        modifier = Modifier.widthIn(
                            min = HhhlOverflowMenuMinWidth,
                            max = HhhlOverflowMenuMaxWidth,
                        ).heightIn(max = HhhlDropdownMenuMaxHeight),
                    ) {
                        noteRenoteActions().forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    renoteMenuExpanded = false
                                    when (action) {
                                        NoteRenoteAction.Repost -> onRenote(note.id)
                                        NoteRenoteAction.Quote -> onQuote(note.id)
                                    }
                                },
                            )
                        }
                    }
                    NoteActionButton(
                        icon = if (note.myReaction == null) Icons.Outlined.AddReaction else null,
                        reactionText = note.myReaction,
                        count = note.reactionCount,
                        selected = note.myReaction != null,
                        contentDescription = if (note.myReaction != null) "取消反应" else "添加反应",
                        enabled = !isActionPending,
                        onClick = {
                            val myReaction = note.myReaction
                            if (myReaction != null) {
                                onDeleteReaction(note.id, myReaction)
                            } else {
                                reactionMenuExpanded = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DropdownMenu(
                        expanded = reactionMenuExpanded,
                        onDismissRequest = {
                            reactionMenuExpanded = false
                            reactionSearchQuery = ""
                        },
                        offset = DpOffset(x = HhhlOverflowMenuOffsetX, y = HhhlOverflowMenuOffsetY),
                        modifier = Modifier.widthIn(
                            min = HhhlOverflowMenuMinWidth,
                            max = HhhlOverflowMenuMaxWidth,
                        ).heightIn(max = HhhlDropdownMenuMaxHeight),
                    ) {
                        HhhlTextInput(
                            value = reactionSearchQuery,
                            onValueChange = { reactionSearchQuery = it },
                            placeholder = "搜索反应",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        reactionSections.forEach { section ->
                            Text(
                                text = section.label,
                                color = LocalHhhlColors.current.subtleText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            section.reactions.forEach { reaction ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CustomEmojiReactionLabel(reaction = reaction)
                                            if (LocalCustomEmojiUrls.current[reaction] != null) {
                                                Text(
                                                    text = reaction,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        reactionMenuExpanded = false
                                        reactionSearchQuery = ""
                                        onReact(note.id, reaction)
                                    },
                                )
                            }
                        }
                        if (reactionSections.isEmpty()) {
                            Text(
                                text = "没有匹配的反应",
                                color = LocalHhhlColors.current.subtleText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                    NoteActionButton(
                        icon = if (note.isFavorited) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        selected = note.isFavorited,
                        contentDescription = if (note.isFavorited) "取消收藏" else "收藏",
                        enabled = !isActionPending,
                        onClick = { onFavorite(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    if (overflowActions.isNotEmpty()) {
                        val menuActions = remember(overflowActions, note.id, onAddToClip) {
                            overflowActions.map { action ->
                                HhhlOverflowMenuAction(
                                    label = action.label,
                                    destructive = action == NoteOverflowAction.Delete,
                                    onClick = {
                                        when (action) {
                                            NoteOverflowAction.OpenDetail -> onClick(note.id)
                                            NoteOverflowAction.Reply -> onReply(note.id)
                                            NoteOverflowAction.Repost -> onRenote(note.id)
                                            NoteOverflowAction.Quote -> onQuote(note.id)
                                            NoteOverflowAction.Favorite -> onFavorite(note.id)
                                            NoteOverflowAction.AddToClip -> onAddToClip?.invoke(note)
                                            NoteOverflowAction.Delete -> deleteConfirmOpen = true
                                        }
                                    },
                                )
                            }
                        }
                        HhhlOverflowMenu(
                            actions = menuActions,
                            enabled = !isActionPending,
                        )
                    }
                }
            }
        }
        HhhlDivider()
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("删除帖子") },
            text = { Text("这会从实例删除这条帖子，操作完成后不可在客户端恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDelete(note.id)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

enum class NoteRenoteAction(val label: String) {
    Repost("转发"),
    Quote("引用"),
}

enum class NoteOverflowAction(val label: String) {
    OpenDetail("打开详情"),
    Reply("回复"),
    Repost("转发"),
    Quote("引用"),
    Favorite("收藏/取消收藏"),
    AddToClip("添加到剪辑"),
    Delete("删除帖子"),
}

enum class NoteRowDensity {
    Compact,
    Comfortable,
}

data class ReactionPickerSection(
    val label: String,
    val reactions: List<String>,
)

data class NoteRowMetrics(
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val avatarSize: Int,
    val contentSpacing: Int,
    val mediaHeight: Int,
)

fun noteRenoteActions(): List<NoteRenoteAction> {
    return listOf(NoteRenoteAction.Repost, NoteRenoteAction.Quote)
}

fun noteOverflowActions(
    canAddToClip: Boolean,
    canDelete: Boolean,
): List<NoteOverflowAction> {
    return buildList {
        add(NoteOverflowAction.OpenDetail)
        add(NoteOverflowAction.Reply)
        add(NoteOverflowAction.Repost)
        add(NoteOverflowAction.Quote)
        add(NoteOverflowAction.Favorite)
        if (canAddToClip) add(NoteOverflowAction.AddToClip)
        if (canDelete) add(NoteOverflowAction.Delete)
    }
}

fun reactionPickerSections(
    reactionOptions: List<String>,
    query: String = "",
    recentReactions: List<String> = emptyList(),
): List<ReactionPickerSection> {
    val cleanQuery = query.trim()
    val distinctOptions = reactionOptions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf("❤️") }
        .distinct()
    val defaultReaction = distinctOptions.first()
    val cleanRecentReactions = recentReactions
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in distinctOptions }
        .distinct()
    val defaultReactions = listOf(defaultReaction).filterNot { it in cleanRecentReactions }
    val remainingOptions = distinctOptions.drop(1).filterNot { it in cleanRecentReactions }
    val customReactions = remainingOptions.filter { it.isCustomEmojiReaction() }
    val commonReactions = remainingOptions.filterNot { it.isCustomEmojiReaction() }

    return listOfNotNull(
        cleanRecentReactions.takeIf { it.isNotEmpty() }?.let { ReactionPickerSection("最近", it) },
        defaultReactions.takeIf { it.isNotEmpty() }?.let { ReactionPickerSection("默认", it) },
        commonReactions.takeIf { it.isNotEmpty() }?.let { ReactionPickerSection("常用", it) },
        customReactions.takeIf { it.isNotEmpty() }?.let { ReactionPickerSection("自定义", it) },
    ).filterSections(cleanQuery)
}

private fun List<ReactionPickerSection>.filterSections(query: String): List<ReactionPickerSection> {
    if (query.isBlank()) return this
    return mapNotNull { section ->
        val matches = section.reactions.filter { reaction ->
            reaction.contains(query, ignoreCase = true)
        }
        matches.takeIf { it.isNotEmpty() }?.let {
            section.copy(reactions = it)
        }
    }
}

private fun String.isCustomEmojiReaction(): Boolean {
    return startsWith(":") && endsWith(":") && length > 2
}

fun noteRowMetrics(density: NoteRowDensity): NoteRowMetrics {
    return when (density) {
        NoteRowDensity.Compact -> NoteRowMetrics(
            horizontalPadding = 12,
            verticalPadding = 8,
            avatarSize = 36,
            contentSpacing = 4,
            mediaHeight = 72,
        )
        NoteRowDensity.Comfortable -> NoteRowMetrics(
            horizontalPadding = 14,
            verticalPadding = 12,
            avatarSize = 42,
            contentSpacing = 6,
            mediaHeight = 86,
        )
    }
}

fun noteContentVisible(note: Note, expanded: Boolean): Boolean {
    return note.cw.isNullOrBlank() || expanded
}

fun noteMediaVisible(media: NoteMedia, revealed: Boolean): Boolean {
    return !media.isSensitive || revealed
}

fun noteVisibilityBadge(visibility: NoteVisibility): String? {
    return when (visibility) {
        NoteVisibility.Public -> null
        NoteVisibility.Home -> "首页"
        NoteVisibility.Followers -> "关注者"
        NoteVisibility.Specified -> "指定"
    }
}

@Composable
private fun QuotedNoteCard(
    note: Note,
    onOpenNote: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, LocalHhhlColors.current.divider, RoundedCornerShape(8.dp))
            .clickable { onOpenNote(note.id) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.author.displayName,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenUser(note.author.id) },
            )
            Text(
                text = "  @${note.author.username} · ${note.createdAtLabel}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.text.isNotBlank()) {
            InlineRichText(
                text = note.text,
                style = MaterialTheme.typography.bodySmall,
                onOpenUrl = onOpenUrl,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
            )
        }
        if (note.media.isNotEmpty()) {
            Text(
                text = "${note.media.size} 个附件",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PollStrip(
    note: Note,
    enabled: Boolean,
    onVotePoll: (String, Int) -> Unit,
) {
    val poll = note.poll ?: return
    val totalVotes = poll.choices.sumOf { it.votes }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.choices.forEachIndexed { index, choice ->
            val percent = (choice.votes * 100) / totalVotes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalHhhlColors.current.mediaBackground)
                    .clickable(enabled = enabled) { onVotePoll(note.id, index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (choice.isVoted) "✓ ${choice.text}" else choice.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${choice.votes} · $percent%",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val meta = buildList {
            add(if (poll.multiple) "多选" else "单选")
            if (poll.expiresAtLabel.isNotBlank()) add("截止 ${poll.expiresAtLabel}")
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReactionStrip(note: Note) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        note.reactions.take(4).forEach { reaction ->
            ReactionChip(
                reaction = reaction.reaction,
                count = reaction.count,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalHhhlColors.current.mediaBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CustomEmojiReactionLabel(reaction = reaction)
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun Avatar(
    initial: String,
    avatarUrl: String? = null,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    val spec = avatarImageSpec(initial = initial, avatarUrl = avatarUrl)
    var imageLoaded by remember(spec.remoteUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08090B)),
        contentAlignment = Alignment.Center,
    ) {
        spec.remoteUrl?.let { remoteUrl ->
            AsyncImage(
                model = remoteUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!imageLoaded) {
            Text(
                text = spec.fallbackInitial,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.rotate(-12f),
            )
        }
    }
}

@Composable
private fun RemoteMediaImage(
    media: NoteMedia,
    mediaUrl: String,
    mediaVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var imageLoaded by remember(mediaUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LocalHhhlColors.current.mediaBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaVisible) {
            AsyncImage(
                model = media.thumbnailUrl ?: mediaUrl,
                contentDescription = media.description.takeIf { it.isNotBlank() },
                contentScale = ContentScale.Crop,
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!imageLoaded) {
            MediaLabel(media = media)
        }
    }
}

@Composable
private fun MediaLabel(media: NoteMedia) {
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = media.displayLabel,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = media.metaLabel,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaStrip(
    note: Note,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    mediaHeight: Int,
) {
    var revealedMediaIds by remember(note.id) { mutableStateOf(emptySet<String>()) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        note.media.take(3).forEach { media ->
            val mediaUrl = media.url ?: media.thumbnailUrl
            val isMediaVisible = noteMediaVisible(media, revealed = media.id in revealedMediaIds)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(mediaHeight.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalHhhlColors.current.mediaBackground)
                    .then(
                        if (mediaUrl != null) {
                            Modifier.clickable {
                                if (!isMediaVisible) {
                                    revealedMediaIds = revealedMediaIds + media.id
                                } else {
                                    val session = noteMediaPreviewSession(
                                        media = note.media,
                                        selectedId = media.id,
                                    )
                                    if (session.items.isNotEmpty() && onOpenMediaPreview != null) {
                                        onOpenMediaPreview(session)
                                    } else {
                                        onOpenMedia(mediaUrl)
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (mediaUrl != null && media.type.startsWith("image/")) {
                    RemoteMediaImage(
                        media = media,
                        mediaUrl = mediaUrl,
                        mediaVisible = isMediaVisible,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MediaLabel(media = media)
                }
            }
        }
    }
}

private val NoteMedia.displayLabel: String
    get() = when {
        isSensitive -> "敏感内容"
        description.isNotBlank() -> description
        type.startsWith("image/") -> "图片"
        type.startsWith("video/") -> "视频"
        type.startsWith("audio/") -> "音频"
        else -> "附件"
    }

private val NoteMedia.metaLabel: String
    get() = buildList {
        if (type.isNotBlank()) add(type)
        if (url != null || thumbnailUrl != null) add("可打开")
    }.joinToString(" · ").ifBlank { "附件" }

data class NoteActionButtonSpec(
    val countLabel: String?,
    val showCount: Boolean,
)

fun noteActionButtonSpec(count: Int?): NoteActionButtonSpec {
    val countLabel = count?.takeIf { it > 0 }?.let {
        when {
            it >= 1000 -> "${it / 1000}k"
            else -> it.toString()
        }
    }
    return NoteActionButtonSpec(
        countLabel = countLabel,
        showCount = countLabel != null,
    )
}

@Composable
private fun NoteActionButton(
    icon: ImageVector?,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    reactionText: String? = null,
    selected: Boolean = false,
) {
    val spec = noteActionButtonSpec(count)
    val contentColor = when {
        !enabled -> LocalHhhlColors.current.subtleText
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .then(modifier)
            .widthIn(min = 0.dp)
            .defaultMinSize(minHeight = HhhlNoteActionMinHeight, minWidth = HhhlNoteActionMinWidth)
            .clip(RoundedCornerShape(HhhlControlCornerRadius))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    !enabled -> LocalHhhlColors.current.mediaBackground.copy(alpha = 0.36f)
                    else -> Color.Transparent
                },
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(
                horizontal = HhhlNoteActionHorizontalPadding,
                vertical = HhhlNoteActionVerticalPadding,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(HhhlNoteActionIconSize),
            )
        } else if (reactionText != null) {
            CustomEmojiReactionLabel(reaction = reactionText)
        }
        spec.countLabel?.let { label ->
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.padding(start = HhhlNoteActionSpacing),
            )
        }
    }
}
