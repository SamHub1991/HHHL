package cc.hhhl.client.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import cc.hhhl.client.model.AvatarDecoration
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NoteVisibility
import cc.hhhl.client.model.User
import cc.hhhl.client.model.commonReactionOptions
import cc.hhhl.client.theme.LocalHhhlColors
import kotlin.math.abs

internal val HhhlNoteActionMinHeight = HhhlControlMinHeight
internal val HhhlNoteActionMinWidth = HhhlControlMinWidth
internal val HhhlNoteActionHorizontalPadding = 6.dp
internal val HhhlNoteActionVerticalPadding = 5.dp
internal val HhhlNoteActionIconSize = 17.dp
internal val HhhlNoteActionSpacing = 4.dp
internal val HhhlReactionPickerMenuWidth = 260.dp
internal val HhhlReactionPickerItemSize = 42.dp
internal val HhhlReactionPickerGridSpacing = 6.dp
private val HhhlUnifiedReactionPickerMenuWidth = 292.dp
private val HhhlUnifiedReactionPickerMenuHeight = 318.dp
private val HhhlReactionPickerSheetWidth = 360.dp
private val HhhlReactionPickerSheetMaxHeight = 420.dp
private val HhhlReactionPickerContentMaxHeight = 316.dp
internal const val HhhlReactionPickerMaxSectionItems = 48
internal const val HhhlReactionPickerMaxTotalItems = 120

@Immutable
data class NoteRowActions(
    val onShareNote: ((String) -> Unit)? = null,
    val onAiSummarizeNote: (Note) -> Unit = {},
    val onAiReplyDraft: (Note) -> Unit = {},
    val onHideFromList: (String) -> Unit = {},
    val onMuteNote: (String) -> Unit = {},
    val onUnmuteNote: (String) -> Unit = {},
    val onMuteRenotes: (String, String) -> Unit = { _, _ -> },
    val onUnmuteRenotes: (String, String) -> Unit = { _, _ -> },
    val onReportNote: (String, String) -> Unit = { _, _ -> },
)

val LocalNoteRowActions = staticCompositionLocalOf { NoteRowActions() }
val LocalBlockedNoteAuthorIds = staticCompositionLocalOf<Set<String>> { emptySet() }
val LocalMutedNoteFilters = staticCompositionLocalOf { MutedNoteFilters() }
val LocalNoteRowGesturesEnabled = staticCompositionLocalOf { true }
val LocalUserQuickActions = staticCompositionLocalOf { UserQuickActions() }

@Immutable
data class UserQuickActions(
    val onFollowUser: (User) -> Unit = {},
    val onMuteUser: (User) -> Unit = {},
    val onBlockUser: (User) -> Unit = {},
    val onAddUserToList: (User) -> Unit = {},
    val onToggleSpecialCareUser: (User) -> Unit = {},
)

enum class NoteRowSwipeAction(val label: String) {
    Reply("回复"),
    Favorite("稍后看"),
}

@Immutable
data class MutedNoteFilters(
    val mutedWords: List<String> = emptyList(),
    val hardMutedWords: List<String> = emptyList(),
)

@OptIn(ExperimentalFoundationApi::class)
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
    onShareNote: ((String) -> Unit)? = null,
    onHideFromList: ((String) -> Unit)? = null,
    onMuteNote: ((String) -> Unit)? = null,
    onReportNote: ((String, String) -> Unit)? = null,
    onDelete: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenUrl: (String) -> Unit = onOpenMedia,
    onOpenMention: (String) -> Unit = {},
    onOpenHashtag: (String) -> Unit = {},
    reactionOptions: List<String> = emptyList(),
    customEmojis: List<CustomEmoji> = emptyList(),
    recentReactions: List<String> = emptyList(),
    isActionPending: Boolean = false,
    canDelete: Boolean = false,
    isSpecialCareAuthor: Boolean = false,
    density: NoteRowDensity = NoteRowDensity.Comfortable,
) {
    if (note.isHiddenByBlockedAuthor(LocalBlockedNoteAuthorIds.current)) return
    val mutedFilters = LocalMutedNoteFilters.current
    if (note.isHiddenByHardMutedWords(mutedFilters.hardMutedWords)) return
    var reactionMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var deleteConfirmOpen by remember(note.id) { mutableStateOf(false) }
    var reportConfirmOpen by remember(note.id) { mutableStateOf(false) }
    var userQuickPanelOpen by remember(note.author.id) { mutableStateOf(false) }
    var blockUserConfirmOpen by remember(note.author.id) { mutableStateOf(false) }
    var contentExpanded by remember(note.id, note.cw) { mutableStateOf(note.cw.isNullOrBlank()) }
    var mutedContentExpanded by remember(note.id, mutedFilters.mutedWords) {
        mutableStateOf(!note.matchesMutedWords(mutedFilters.mutedWords))
    }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val rowActions = LocalNoteRowActions.current
    val userQuickActions = LocalUserQuickActions.current
    val listGesturesEnabled = LocalNoteRowGesturesEnabled.current
    val effectiveOnShareNote = onShareNote ?: rowActions.onShareNote
    val effectiveOnHideFromList = onHideFromList ?: rowActions.onHideFromList
    val effectiveOnMuteNote = onMuteNote ?: rowActions.onMuteNote
    val effectiveOnUnmuteNote = rowActions.onUnmuteNote
    val effectiveOnMuteRenotes = rowActions.onMuteRenotes
    val effectiveOnUnmuteRenotes = rowActions.onUnmuteRenotes
    val effectiveOnReportNote = onReportNote ?: rowActions.onReportNote
    val pickerSections = remember(reactionOptions, recentReactions) {
        reactionPickerSections(
            reactionOptions = reactionOptions,
            recentReactions = recentReactions,
        )
    }
    val isContentVisible = noteContentVisible(note, expanded = contentExpanded)
    val metrics = remember(density) { noteRowMetrics(density) }
    val canAddToClip = onAddToClip != null
    val overflowActions = remember(canAddToClip, canDelete) {
        noteOverflowActions(
            canAddToClip = canAddToClip,
            canDelete = canDelete,
        )
    }
    val noteLink = remember(note.id) { notePermalink(note.id) }
    val noteCopyText = remember(note.text, note.cw) { noteClipboardText(note) }
    val noteEmbedCode = remember(note.id) { noteEmbedCode(note.id) }
    val rowSizeAnimationModifier = if (note.cw.isNullOrBlank()) {
        Modifier
    } else {
        Modifier.animateContentSize()
    }
    val mutedPhrase = remember(note, mutedFilters.mutedWords) {
        note.firstMatchedMutedWord(mutedFilters.mutedWords)
    }
    val localDensity = LocalDensity.current
    val swipeThresholdPx = with(localDensity) { 76.dp.toPx() }
    val swipeMaxPx = with(localDensity) { 118.dp.toPx() }
    var horizontalDragOffset by remember(note.id, listGesturesEnabled) { mutableStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = horizontalDragOffset,
        animationSpec = tween(durationMillis = 130),
        label = "note-row-swipe-offset",
    )
    fun openUserQuickPanel() {
        if (note.author.id.isNotBlank()) {
            userQuickPanelOpen = true
        }
    }
    val userClickModifier = Modifier.combinedClickable(
        onClick = { onOpenUser(note.author.id) },
        onLongClick = ::openUserQuickPanel,
    )
    val swipeModifier = if (listGesturesEnabled && !isActionPending) {
        Modifier.pointerInput(note.id, listGesturesEnabled, isActionPending) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val action = noteRowSwipeAction(horizontalDragOffset, swipeThresholdPx)
                    horizontalDragOffset = 0f
                    when (action) {
                        NoteRowSwipeAction.Reply -> onReply(note.id)
                        NoteRowSwipeAction.Favorite -> onFavorite(note.id)
                        null -> Unit
                    }
                },
                onDragCancel = { horizontalDragOffset = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    horizontalDragOffset = (horizontalDragOffset + dragAmount)
                        .coerceIn(-swipeMaxPx, swipeMaxPx)
                },
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (listGesturesEnabled) {
            NoteRowSwipeBackground(
                offset = animatedSwipeOffset,
                threshold = swipeThresholdPx,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowSizeAnimationModifier)
                .offset(x = with(localDensity) { animatedSwipeOffset.toDp() })
                .then(swipeModifier)
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
                    avatarDecorations = note.author.avatarDecorations,
                    size = metrics.avatarSize.dp,
                    modifier = userClickModifier,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(metrics.contentSpacing.dp),
                ) {
                    NoteAuthorLine(
                        note = note,
                        onOpenUser = onOpenUser,
                        onOpenQuickActions = ::openUserQuickPanel,
                        isSpecialCareAuthor = isSpecialCareAuthor,
                        displayStyle = MaterialTheme.typography.bodyMedium,
                        metaStyle = MaterialTheme.typography.bodySmall,
                    )
                if (note.isRenote) {
                    Text(
                        text = "转发",
                        color = LocalHhhlColors.current.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                noteVisibilityBadge(note.visibility)?.let { badge ->
                    Text(
                        text = badge,
                        color = LocalHhhlColors.current.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                note.cw?.takeIf { it.isNotBlank() }?.let { cw ->
                    val colors = LocalHhhlColors.current
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InlineRichText(
                            text = cw,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxChars = 260,
                            onOpenUrl = onOpenUrl,
                            onOpenMention = onOpenMention,
                            onOpenHashtag = onOpenHashtag,
                        )
                        Text(
                            text = if (contentExpanded) "收起内容" else "显示内容",
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { contentExpanded = !contentExpanded },
                        )
                    }
                }
                if (mutedPhrase != null && !mutedContentExpanded) {
                    MutedNoteCollapseRow(
                        phrase = mutedPhrase,
                        onShow = { mutedContentExpanded = true },
                    )
                } else if (isContentVisible) {
                    InlineRichText(
                        text = note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxChars = if (density == NoteRowDensity.Compact) 700 else null,
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
                        ReactionStrip(
                            note = note,
                            enabled = !isActionPending,
                            onReact = onReact,
                        )
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
                        showDisabledState = false,
                        onClick = { onReply(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteActionButton(
                        icon = Icons.Outlined.Repeat,
                        count = note.renoteCount,
                        contentDescription = "转发",
                        enabled = !isActionPending,
                        showDisabledState = false,
                        onClick = { onRenote(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteActionButton(
                        icon = Icons.Outlined.FormatQuote,
                        contentDescription = "引用",
                        enabled = !isActionPending,
                        showDisabledState = false,
                        onClick = { onQuote(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteActionButton(
                        icon = Icons.Outlined.Bookmark,
                        selected = note.isFavorited,
                        contentDescription = if (note.isFavorited) "取消保存" else "保存到收藏",
                        enabled = !isActionPending,
                        showDisabledState = false,
                        onClick = { onFavorite(note.id) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteActionButton(
                        icon = if (note.myReaction == null) Icons.Outlined.AddReaction else null,
                        reactionText = note.myReaction,
                        count = note.reactionCount,
                        selected = note.myReaction != null,
                        selectedEmphasis = NoteActionSelectedEmphasis.Soft,
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
                    if (reactionMenuExpanded) {
                        ReactionPickerDialog(
                            sections = pickerSections,
                            onDismiss = { reactionMenuExpanded = false },
                            onReactionSelected = { reaction ->
                                reactionMenuExpanded = false
                                onReact(note.id, reaction)
                            },
                        )
                    }
                    if (overflowActions.isNotEmpty()) {
                        fun handleOverflowAction(action: NoteOverflowAction) {
                            when (action) {
                                NoteOverflowAction.OpenDetail -> onClick(note.id)
                                NoteOverflowAction.CopyContent -> {
                                    clipboardManager.setText(AnnotatedString(noteCopyText))
                                }
                                NoteOverflowAction.CopyLink -> {
                                    clipboardManager.setText(AnnotatedString(noteLink))
                                }
                                NoteOverflowAction.Embed -> {
                                    clipboardManager.setText(AnnotatedString(noteEmbedCode))
                                }
                                NoteOverflowAction.Share -> {
                                    val shareNote = effectiveOnShareNote
                                    if (shareNote != null) {
                                        shareNote(noteLink)
                                    } else {
                                        clipboardManager.setText(AnnotatedString(noteLink))
                                    }
                                }
                                NoteOverflowAction.AiSummary -> rowActions.onAiSummarizeNote(note)
                                NoteOverflowAction.AiReplyDraft -> rowActions.onAiReplyDraft(note)
                                NoteOverflowAction.Favorite -> onFavorite(note.id)
                                NoteOverflowAction.AddToClip -> onAddToClip?.invoke(note)
                                NoteOverflowAction.HideFromList -> effectiveOnHideFromList(note.id)
                                NoteOverflowAction.MuteNote -> effectiveOnMuteNote(note.id)
                                NoteOverflowAction.UnmuteNote -> effectiveOnUnmuteNote(note.id)
                                NoteOverflowAction.MuteRenotes -> effectiveOnMuteRenotes(note.id, note.author.id)
                                NoteOverflowAction.UnmuteRenotes -> effectiveOnUnmuteRenotes(note.id, note.author.id)
                                NoteOverflowAction.User -> onOpenUser(note.author.id)
                                NoteOverflowAction.Report -> reportConfirmOpen = true
                                NoteOverflowAction.Delete -> deleteConfirmOpen = true
                            }
                        }

                        val aiOverflowActions = overflowActions.filter { action -> action.isAiAction }
                        val menuActions = buildList {
                            var aiGroupAdded = false
                            overflowActions.forEach { action ->
                                if (action.isAiAction) {
                                    if (!aiGroupAdded) {
                                        add(
                                            HhhlOverflowMenuAction(
                                                label = "AI",
                                                icon = Icons.Filled.AutoAwesome,
                                                onClick = {},
                                                children = aiOverflowActions.map { aiAction ->
                                                    HhhlOverflowMenuAction(
                                                        label = aiAction.aiChildLabel,
                                                        icon = Icons.Filled.AutoAwesome,
                                                        onClick = { handleOverflowAction(aiAction) },
                                                    )
                                                },
                                            ),
                                        )
                                        aiGroupAdded = true
                                    }
                                } else {
                                    add(
                                        HhhlOverflowMenuAction(
                                            label = action.label,
                                            destructive = action == NoteOverflowAction.Report ||
                                                action == NoteOverflowAction.Delete,
                                            onClick = { handleOverflowAction(action) },
                                        ),
                                    )
                                }
                            }
                        }
                        HhhlOverflowMenu(
                            actions = menuActions,
                            enabled = !isActionPending,
                            showDisabledState = false,
                        )
                    }
                }
            }
        }
            HhhlDivider()
        }
    }

    if (userQuickPanelOpen) {
        UserQuickActionDialog(
            user = note.author,
            isSpecialCareUser = isSpecialCareAuthor,
            onDismiss = { userQuickPanelOpen = false },
            onOpenUser = {
                userQuickPanelOpen = false
                onOpenUser(note.author.id)
            },
            onFollowUser = {
                userQuickPanelOpen = false
                userQuickActions.onFollowUser(note.author)
            },
            onMuteUser = {
                userQuickPanelOpen = false
                userQuickActions.onMuteUser(note.author)
            },
            onBlockUser = {
                userQuickPanelOpen = false
                blockUserConfirmOpen = true
            },
            onAddUserToList = {
                userQuickPanelOpen = false
                userQuickActions.onAddUserToList(note.author)
            },
            onToggleSpecialCareUser = {
                userQuickPanelOpen = false
                userQuickActions.onToggleSpecialCareUser(note.author)
            },
        )
    }

    if (blockUserConfirmOpen) {
        HhhlAlertDialog(
            onDismissRequest = { blockUserConfirmOpen = false },
            title = { Text("屏蔽用户") },
            text = { Text("会屏蔽 @${note.author.username}，同时隐藏来自该用户的帖子。") },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        blockUserConfirmOpen = false
                        userQuickActions.onBlockUser(note.author)
                    },
                    destructive = true,
                ) {
                    Text("屏蔽")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { blockUserConfirmOpen = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (deleteConfirmOpen) {
        HhhlAlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("删除帖子") },
            text = { Text("这会从实例删除这条帖子，操作完成后不可在客户端恢复。") },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDelete(note.id)
                    },
                    destructive = true,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { deleteConfirmOpen = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (reportConfirmOpen) {
        HhhlAlertDialog(
            onDismissRequest = { reportConfirmOpen = false },
            title = { Text("举报帖子") },
            text = { Text("会把这条帖子提交给实例管理员处理。") },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        reportConfirmOpen = false
                        effectiveOnReportNote(note.id, note.author.id)
                    },
                    destructive = true,
                ) {
                    Text("举报")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { reportConfirmOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

fun Note.isHiddenByBlockedAuthor(blockedUserIds: Set<String>): Boolean {
    if (blockedUserIds.isEmpty()) return false
    return author.id in blockedUserIds || quotedNote?.isHiddenByBlockedAuthor(blockedUserIds) == true
}

fun Note.matchesMutedWords(words: List<String>): Boolean {
    return firstMatchedMutedWord(words) != null
}

fun Note.isHiddenByHardMutedWords(words: List<String>): Boolean {
    return matchesMutedWords(words) || quotedNote?.isHiddenByHardMutedWords(words) == true
}

fun Note.firstMatchedMutedWord(words: List<String>): String? {
    val haystack = noteSearchableText().lowercase()
    return words
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .firstOrNull { phrase -> haystack.contains(phrase.lowercase()) }
}

@Composable
private fun NoteRowSwipeBackground(
    offset: Float,
    threshold: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val action = noteRowSwipeAction(offset, threshold)
    val revealProgress = (abs(offset) / threshold).coerceIn(0f, 1f)
    if (revealProgress <= 0.08f) return
    val positive = offset > 0f
    val label = action?.label ?: if (positive) NoteRowSwipeAction.Reply.label else NoteRowSwipeAction.Favorite.label
    val backgroundColor = when {
        action == NoteRowSwipeAction.Favorite -> colors.accentSoft.copy(alpha = 0.86f * revealProgress)
        action == NoteRowSwipeAction.Reply -> colors.inputBackground.copy(alpha = 0.82f * revealProgress)
        else -> colors.inputBackground.copy(alpha = 0.42f * revealProgress)
    }

    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = if (positive) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(
            text = label,
            color = (if (action == null) colors.textMuted else colors.textPrimary).copy(alpha = revealProgress),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

fun noteRowSwipeAction(
    offset: Float,
    threshold: Float,
): NoteRowSwipeAction? {
    if (abs(offset) < threshold) return null
    return if (offset > 0f) NoteRowSwipeAction.Reply else NoteRowSwipeAction.Favorite
}

@Composable
private fun UserQuickActionDialog(
    user: User,
    isSpecialCareUser: Boolean,
    onDismiss: () -> Unit,
    onOpenUser: () -> Unit,
    onFollowUser: () -> Unit,
    onMuteUser: () -> Unit,
    onBlockUser: () -> Unit,
    onAddUserToList: () -> Unit,
    onToggleSpecialCareUser: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val colors = LocalHhhlColors.current
        val isDarkSurface = colors.surface.luminance() < 0.2f
        val panelShape = RoundedCornerShape(26.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 390.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 18.dp,
                        shape = panelShape,
                        clip = false,
                        ambientColor = colors.shadow,
                        spotColor = colors.shadow,
                    )
                    .clip(panelShape)
                    .background(
                        if (isDarkSurface) colors.surfaceElevated.copy(alpha = 0.97f) else colors.surface.copy(alpha = 0.99f),
                    )
                    .border(1.dp, colors.border.copy(alpha = if (isDarkSurface) 0.34f else 0.48f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.border.copy(alpha = 0.72f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        initial = user.avatarInitial,
                        avatarUrl = user.avatarUrl,
                        avatarDecorations = user.avatarDecorations,
                        size = 42.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = user.displayName,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${user.username}",
                            color = colors.textMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.inputBackground.copy(alpha = if (isDarkSurface) 0.58f else 0.78f))
                            .border(1.dp, colors.border.copy(alpha = 0.36f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HhhlActionChip(label = "资料", emphasized = true, onClick = onOpenUser)
                    HhhlActionChip(label = if (user.isFollowing) "已关注" else "关注", onClick = onFollowUser)
                    HhhlActionChip(label = "加入列表", onClick = onAddUserToList)
                    HhhlActionChip(
                        label = if (isSpecialCareUser) "取消特别关心" else "特别关心",
                        emphasized = isSpecialCareUser,
                        onClick = onToggleSpecialCareUser,
                    )
                    HhhlActionChip(label = "静音", onClick = onMuteUser)
                    HhhlActionChip(label = "屏蔽", emphasized = true, onClick = onBlockUser)
                }
            }
        }
    }
}

private fun Note.noteSearchableText(): String {
    return buildString {
        append(text)
        cw?.let { append('\n').append(it) }
        append('\n').append(author.displayName)
        append('\n').append(author.username)
        quotedNote?.let { append('\n').append(it.noteSearchableText()) }
    }
}

enum class NoteOverflowAction(val label: String) {
    OpenDetail("详情"),
    CopyContent("复制内容"),
    CopyLink("复制链接"),
    Embed("嵌入"),
    Share("分享"),
    AiSummary("AI 总结"),
    AiReplyDraft("AI 回复草稿"),
    Favorite("收藏"),
    AddToClip("便签"),
    HideFromList("隐藏帖子列表"),
    MuteNote("静音帖子"),
    UnmuteNote("取消帖子静音"),
    MuteRenotes("静音此用户转发"),
    UnmuteRenotes("取消转发静音"),
    User("用户"),
    Report("举报"),
    Delete("删除帖子"),
}

private val NoteOverflowAction.isAiAction: Boolean
    get() = this == NoteOverflowAction.AiSummary || this == NoteOverflowAction.AiReplyDraft

private val NoteOverflowAction.aiChildLabel: String
    get() = when (this) {
        NoteOverflowAction.AiSummary -> "总结"
        NoteOverflowAction.AiReplyDraft -> "回复草稿"
        else -> label
    }

enum class NoteRenoteAction(val label: String) {
    Repost("转发"),
    Quote("引用"),
}

enum class NoteRowDensity {
    Compact,
    UltraCompact,
    Comfortable,
}

@Immutable
data class ReactionPickerSection(
    val label: String,
    val reactions: List<String>,
)

@Composable
private fun ReactionPickerDialog(
    sections: List<ReactionPickerSection>,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val colors = LocalHhhlColors.current
        val isDarkSurface = colors.surface.luminance() < 0.2f
        val panelShape = RoundedCornerShape(28.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HhhlReactionPickerSheetWidth)
                    .fillMaxWidth()
                    .heightIn(max = HhhlReactionPickerSheetMaxHeight)
                    .shadow(
                        elevation = 18.dp,
                        shape = panelShape,
                        clip = false,
                        ambientColor = colors.shadow,
                        spotColor = colors.shadow,
                    )
                    .clip(panelShape)
                    .background(
                        if (isDarkSurface) colors.surfaceElevated.copy(alpha = 0.96f) else colors.surface.copy(alpha = 0.98f),
                    )
                    .border(1.dp, colors.border.copy(alpha = if (isDarkSurface) 0.34f else 0.48f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.border.copy(alpha = 0.72f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "选择回应",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "轻点一个表情发送",
                            color = colors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.inputBackground.copy(alpha = if (isDarkSurface) 0.58f else 0.78f))
                            .border(1.dp, colors.border.copy(alpha = 0.36f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (sections.isEmpty()) {
                    Text(
                        text = "暂无可用表情",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = HhhlReactionPickerContentMaxHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.inputBackground.copy(alpha = if (isDarkSurface) 0.28f else 0.40f))
                            .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        ReactionPickerGrid(
                            sections = sections,
                            onReactionSelected = onReactionSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionPickerGrid(
    sections: List<ReactionPickerSection>,
    onReactionSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { section ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = section.label,
                    color = LocalHhhlColors.current.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HhhlReactionPickerGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(HhhlReactionPickerGridSpacing),
                ) {
                    section.reactions.forEach { reaction ->
                        ReactionPickerGridItem(
                            reaction = reaction,
                            onClick = { onReactionSelected(reaction) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionPickerGridItem(
    reaction: String,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(HhhlReactionPickerItemSize)
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.68f))
            .border(
                width = 1.dp,
                color = colors.border.copy(alpha = 0.34f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CustomEmojiReactionLabel(reaction = reaction)
    }
}

@Immutable
data class NoteRowMetrics(
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val avatarSize: Int,
    val contentSpacing: Int,
    val mediaHeight: Int,
)

fun noteRenoteActions(): List<NoteRenoteAction> = noteRenoteActionList

private val noteRenoteActionList: List<NoteRenoteAction> = listOf(NoteRenoteAction.Repost, NoteRenoteAction.Quote)

fun noteOverflowActions(
    canAddToClip: Boolean,
    canDelete: Boolean,
): List<NoteOverflowAction> = buildList {
    add(NoteOverflowAction.OpenDetail)
    add(NoteOverflowAction.CopyContent)
    add(NoteOverflowAction.CopyLink)
    add(NoteOverflowAction.Embed)
    add(NoteOverflowAction.Share)
    add(NoteOverflowAction.AiSummary)
    add(NoteOverflowAction.AiReplyDraft)
    add(NoteOverflowAction.Favorite)
    if (canAddToClip) {
        add(NoteOverflowAction.AddToClip)
    }
    add(NoteOverflowAction.HideFromList)
    add(NoteOverflowAction.MuteNote)
    add(NoteOverflowAction.UnmuteNote)
    add(NoteOverflowAction.MuteRenotes)
    add(NoteOverflowAction.UnmuteRenotes)
    add(NoteOverflowAction.User)
    add(NoteOverflowAction.Report)
    if (canDelete) {
        add(NoteOverflowAction.Delete)
    }
}

private fun notePermalink(noteId: String): String {
    return "https://dc.hhhl.cc/notes/${noteId.trim()}"
}

private fun noteEmbedCode(noteId: String): String {
    val url = notePermalink(noteId)
    return """<iframe src="$url/embed" data-misskey-note="$url"></iframe>"""
}

private fun noteClipboardText(note: Note): String {
    return buildList {
        note.cw?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        note.text.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        if (isEmpty()) add(notePermalink(note.id))
    }.joinToString("\n\n")
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
        .ifEmpty { commonReactionOptions }
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
    ).filterSections(cleanQuery).boundedReactionPickerSections()
}

private fun List<ReactionPickerSection>.boundedReactionPickerSections(): List<ReactionPickerSection> {
    var remaining = HhhlReactionPickerMaxTotalItems
    return mapNotNull { section ->
        if (remaining <= 0) return@mapNotNull null
        val limit = minOf(section.reactions.size, HhhlReactionPickerMaxSectionItems, remaining)
        remaining -= limit
        section.copy(reactions = section.reactions.take(limit)).takeIf { it.reactions.isNotEmpty() }
    }
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

fun customEmojisForReactionPicker(
    reactionOptions: List<String>,
    customEmojis: List<CustomEmoji>,
    customEmojiUrls: Map<String, String>,
): List<CustomEmoji> {
    val resultByName = LinkedHashMap<String, CustomEmoji>()
    customEmojis
        .asSequence()
        .filter { !it.isSensitive && it.name.isNotBlank() && it.url.isNotBlank() }
        .forEach { resultByName.putIfAbsent(it.name, it) }

    val optionCodes = reactionOptions.asSequence()
    val urlCodes = customEmojiUrls.keys.asSequence()
    (optionCodes + urlCodes)
        .mapNotNull { code -> code.customEmojiReactionName() }
        .distinct()
        .forEach { name ->
            if (!resultByName.containsKey(name)) {
                val url = customEmojiUrls[":$name:"] ?: customEmojiUrls[":$name@.:"]
                if (!url.isNullOrBlank()) {
                    resultByName[name] = CustomEmoji(
                        name = name,
                        category = "实例",
                        url = url,
                        aliases = emptyList(),
                        localOnly = true,
                        isSensitive = false,
                    )
                }
            }
        }

    return resultByName.values
        .sortedWith(compareBy<CustomEmoji> { it.category.orEmpty() }.thenBy { it.name })
}

private fun String.customEmojiReactionName(): String? {
    val clean = trim()
    if (!clean.startsWith(":") || !clean.endsWith(":") || clean.length <= 2) return null
    return clean.removePrefix(":")
        .removeSuffix(":")
        .substringBefore("@.")
        .takeIf { it.isNotBlank() }
}

fun noteRowMetrics(density: NoteRowDensity): NoteRowMetrics {
    return when (density) {
        NoteRowDensity.UltraCompact -> NoteRowMetrics(
            horizontalPadding = 12,
            verticalPadding = 6,
            avatarSize = 34,
            contentSpacing = 3,
            mediaHeight = 56,
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteAuthorLine(
    note: Note,
    onOpenUser: (String) -> Unit,
    onOpenQuickActions: () -> Unit = { onOpenUser(note.author.id) },
    isSpecialCareAuthor: Boolean = false,
    displayStyle: TextStyle,
    metaStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val displayColor = colors.textPrimary

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = displayColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(note.author.displayName)
                }
                withStyle(
                    SpanStyle(
                        color = colors.textMuted,
                        fontSize = metaStyle.fontSize,
                        fontWeight = FontWeight.Normal,
                    ),
                ) {
                    append("  @${note.author.username}")
                }
            },
            style = displayStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp)
                .combinedClickable(
                    onClick = { onOpenUser(note.author.id) },
                    onLongClick = onOpenQuickActions,
                ),
        )
        if (isSpecialCareAuthor) {
            Text(
                text = "特别关心",
                color = colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentSoft.copy(alpha = 0.78f))
                    .border(
                        width = 1.dp,
                        color = colors.focusRing.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (note.createdAtLabel.isNotBlank()) {
            Text(
                text = " · ${note.createdAtLabel}",
                color = colors.textMuted,
                style = metaStyle,
                maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier.combinedClickable(
                onClick = { onOpenUser(note.author.id) },
                onLongClick = onOpenQuickActions,
            ),
        )
    }
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
    val colors = LocalHhhlColors.current
    val previewText = notePreviewText(note, fallback = "")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.quoteBackground.copy(alpha = 0.62f))
            .border(1.dp, colors.border.copy(alpha = 0.66f), RoundedCornerShape(14.dp))
            .clickable { onOpenNote(note.id) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NoteAuthorLine(
            note = note,
            onOpenUser = onOpenUser,
            displayStyle = MaterialTheme.typography.bodySmall,
            metaStyle = MaterialTheme.typography.labelSmall,
        )
        if (previewText.isNotBlank()) {
            InlineRichText(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                maxChars = 260,
                onOpenUrl = onOpenUrl,
                onOpenMention = onOpenMention,
                onOpenHashtag = onOpenHashtag,
            )
        }
        if (note.media.isNotEmpty()) {
            Text(
                text = "${note.media.size} 个附件",
                color = colors.textMuted,
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
    val colors = LocalHhhlColors.current
    val totalVotes = poll.choices.sumOf { it.votes }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.choices.forEachIndexed { index, choice ->
            val percent = (choice.votes * 100) / totalVotes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.mediaBackground)
                    .clickable(enabled = enabled) { onVotePoll(note.id, index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineRichText(
                    text = if (choice.isVoted) "✓ ${choice.text}" else choice.text,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxChars = 180,
                )
                Text(
                    text = "${choice.votes} · $percent%",
                    color = colors.textMuted,
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
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReactionStrip(
    note: Note,
    enabled: Boolean,
    onReact: (String, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(minOf(4, note.reactions.size)) { index ->
            val reaction = note.reactions[index]
            val selected = note.myReaction == reaction.reaction
            ReactionChip(
                reaction = reaction.reaction,
                count = reaction.count,
                selected = selected,
                enabled = enabled && !selected,
                onClick = { onReact(note.id, reaction.reaction) },
            )
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val colors = LocalHhhlColors.current
    val background = if (selected) {
        colors.noteReactionBackground
    } else {
        colors.mediaBackground
    }
    val borderColor = if (selected) {
        colors.focusRing.copy(alpha = 0.62f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CustomEmojiReactionLabel(reaction = reaction)
        Text(
            text = count.toString(),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MutedNoteCollapseRow(
    phrase: String,
    onShow: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.inputBackground.copy(alpha = 0.46f))
            .border(1.dp, colors.border.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
            .clickable(onClick = onShow)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已折叠包含「$phrase」的帖子",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "显示",
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Avatar(
    initial: String,
    avatarUrl: String? = null,
    avatarDecorations: List<AvatarDecoration> = emptyList(),
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    val spec = avatarImageSpec(initial = initial, avatarUrl = avatarUrl)

    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(if (spec.remoteUrl == null) colors.avatarBackground else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            spec.fallbackUrl?.let { fallbackUrl ->
                AsyncImage(
                    model = fallbackUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            spec.remoteUrl?.let { remoteUrl ->
                AsyncImage(
                    model = remoteUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        avatarDecorations.forEach { decoration ->
            AsyncImage(
                model = decoration.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * 1.32f)
                    .offset(
                        x = size * decoration.offsetX,
                        y = size * decoration.offsetY,
                    )
                    .graphicsLayer {
                        rotationZ = decoration.angle
                        scaleX = if (decoration.flipH) -1f else 1f
                    },
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
    val colors = LocalHhhlColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.mediaBackground),
        contentAlignment = Alignment.Center,
    ) {
        MediaLabel(media = media)
        if (mediaVisible) {
            AsyncImage(
                model = media.thumbnailUrl ?: mediaUrl,
                contentDescription = media.description.takeIf { it.isNotBlank() },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MediaLabel(media: NoteMedia) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = media.displayLabel,
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = media.metaLabel,
            color = colors.textMuted,
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
    val colors = LocalHhhlColors.current
    var revealedMediaIds by remember(note.id) { mutableStateOf(emptySet<String>()) }
    val visibleMedia = remember(note.media) { note.media.take(4) }
    val hiddenMediaCount = (note.media.size - visibleMedia.size).coerceAtLeast(0)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleMedia.forEachIndexed { index, media ->
            val mediaUrl = media.url ?: media.thumbnailUrl
            val isMediaVisible = noteMediaVisible(media, revealed = media.id in revealedMediaIds)
            val showOverflowBadge = index == visibleMedia.lastIndex && hiddenMediaCount > 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(mediaHeight.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.mediaBackground)
                    .border(
                        width = 1.dp,
                        color = colors.border.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(12.dp),
                    )
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
                if (showOverflowBadge) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.overlayScrim.copy(alpha = 0.64f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+$hiddenMediaCount",
                            color = colors.textInverse,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
        add(mediaTypeDisplayName(type))
        if (url != null || thumbnailUrl != null) add("可打开")
    }.joinToString(" · ").ifBlank { "附件" }

data class NoteActionButtonSpec(
    val countLabel: String?,
    val showCount: Boolean,
)

private enum class NoteActionSelectedEmphasis {
    Normal,
    Soft,
}

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
    showDisabledState: Boolean = true,
    selectedEmphasis: NoteActionSelectedEmphasis = NoteActionSelectedEmphasis.Normal,
) {
    val spec = noteActionButtonSpec(count)
    val colors = LocalHhhlColors.current
    val visuallyEnabled = enabled || !showDisabledState
    val softSelected = selected && selectedEmphasis == NoteActionSelectedEmphasis.Soft
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> colors.accent.copy(alpha = if (softSelected) 0.82f else 1f)
            !visuallyEnabled -> colors.textMuted
            else -> colors.textSecondary
        },
        animationSpec = tween(durationMillis = 150),
        label = "note-action-content",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            softSelected -> colors.noteActionBackground.copy(alpha = 0.62f)
            selected -> colors.noteActionBackground.copy(alpha = 0.92f)
            !visuallyEnabled -> colors.noteActionBackground.copy(alpha = 0.36f)
            else -> colors.noteActionBackground
        },
        animationSpec = tween(durationMillis = 150),
        label = "note-action-container",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            softSelected -> colors.border.copy(alpha = 0.24f)
            selected -> colors.focusRing.copy(alpha = 0.48f)
            !visuallyEnabled -> colors.border.copy(alpha = 0.18f)
            else -> colors.border.copy(alpha = 0.22f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "note-action-border",
    )
    val shape = RoundedCornerShape(HhhlControlCornerRadius)
    Row(
        modifier = Modifier
            .then(modifier)
            .widthIn(min = 0.dp)
            .defaultMinSize(minHeight = HhhlNoteActionMinHeight, minWidth = HhhlNoteActionMinWidth)
            .shadow(
                elevation = if (selected && !softSelected) HhhlIconActionIdleElevation else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
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
