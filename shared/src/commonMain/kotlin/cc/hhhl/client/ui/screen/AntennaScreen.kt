package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import cc.hhhl.client.ui.component.HhhlAlertDialog
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
import cc.hhhl.client.model.Antenna
import cc.hhhl.client.model.AntennaDraft
import cc.hhhl.client.model.Note
import cc.hhhl.client.state.AntennaUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlCheckbox
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlProgressIndicator
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlInlinePanel
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.NoteRow
import cc.hhhl.client.ui.component.NoteRowDensity

@Composable
fun AntennaScreen(
    state: AntennaUiState? = null,
    onBack: () -> Unit,
    onRefreshAntennas: () -> Unit = {},
    onRefreshNotes: () -> Unit = {},
    onCreateAntenna: (AntennaDraft) -> Unit = {},
    onUpdateSelectedAntenna: (AntennaDraft) -> Unit = {},
    onDeleteSelectedAntenna: () -> Unit = {},
    onSelectAntenna: (Antenna) -> Unit = {},
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
    val antennas = state?.antennas.orEmpty()
    val selectedAntenna = state?.selectedAntenna ?: antennas.firstOrNull()
    val notes = state?.notes.orEmpty()
    val listState = rememberLazyListState()
    var editorMode by remember { mutableStateOf<AntennaEditorMode?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = notes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "天线",
            supportingText = selectedAntenna?.name?.ifBlank { null } ?: "关键词流",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AntennaCreateButton(
                        isMutating = state?.isMutatingAntenna == true,
                        onClick = { editorMode = AntennaEditorMode.Create },
                    )
                    HhhlOverflowMenu(
                        actions = antennaSummaryActions(
                            hasSelectedAntenna = selectedAntenna != null,
                            isLoadingAntennas = state?.isLoadingAntennas == true,
                            isLoadingNotes = state?.isLoadingNotes == true,
                            onRefreshAntennas = onRefreshAntennas,
                            onRefreshNotes = onRefreshNotes,
                        ),
                    )
                }
            },
        )
        HhhlDivider()
        AntennaSummaryRow(
            antennaCount = antennas.size,
            selectedAntenna = selectedAntenna,
            isLoadingAntennas = state?.isLoadingAntennas == true,
            isLoadingNotes = state?.isLoadingNotes == true,
        )
        HhhlDivider()
        if (antennas.isNotEmpty()) {
            AntennaPickerRow(
                antennas = antennas,
                selectedAntenna = selectedAntenna,
                isLoading = state?.isLoadingAntennas == true,
                onSelectAntenna = onSelectAntenna,
            )
            HhhlDivider()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(key = "antenna-error", contentType = "antenna-status") {
                    AntennaStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshAntennas,
                    )
                }
            }
            if (state?.isLoadingAntennas == true && antennas.isEmpty()) {
                item(key = "antenna-loading", contentType = "antenna-status") {
                    AntennaStatusRow(text = "正在加载天线...", loading = true)
                }
            }
            if (state != null && !state.isLoadingAntennas && antennas.isEmpty() && state.errorMessage == null) {
                item(key = "antenna-empty", contentType = "antenna-status") { AntennaStatusRow(text = "还没有天线") }
            }
            if (state?.isLoadingNotes == true && notes.isEmpty()) {
                item(key = "antenna-notes-loading-${selectedAntenna?.id.orEmpty()}", contentType = "antenna-status") {
                    AntennaStatusRow(text = "正在加载天线动态...", loading = true)
                }
            }
            selectedAntenna?.let { antenna ->
                item(key = "antenna-header-${antenna.id}", contentType = "antenna-header") {
                    AntennaHeaderRow(
                        antenna = antenna,
                        isMutating = state?.isMutatingAntenna == true,
                        onEdit = { editorMode = AntennaEditorMode.Edit },
                        onDelete = { deleteDialogOpen = true },
                    )
                }
            }
            state?.notesErrorMessage?.let { message ->
                item(key = "antenna-notes-error-${selectedAntenna?.id.orEmpty()}", contentType = "antenna-status") {
                    AntennaStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshNotes,
                    )
                }
            }
            if (
                state != null &&
                selectedAntenna != null &&
                !state.isLoadingNotes &&
                notes.isEmpty() &&
                state.notesErrorMessage == null
            ) {
                item(key = "antenna-notes-empty-${selectedAntenna.id}", contentType = "antenna-status") { AntennaStatusRow(text = "这条天线还没有动态") }
            }
            items(
                items = notes,
                key = { "antenna-note-${it.id}" },
                contentType = { "antenna-note" },
            ) { note ->
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
            if (state != null && notes.isNotEmpty() && state.isLoadingMore) {
                item(key = "antenna-loading-more-${selectedAntenna?.id.orEmpty()}", contentType = "antenna-status") {
                    AntennaStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }

    when (editorMode) {
        AntennaEditorMode.Create -> AntennaEditorDialog(
            title = "新建天线",
            isMutating = state?.isMutatingAntenna == true,
            onDismiss = { editorMode = null },
            onSubmit = {
                onCreateAntenna(it)
                editorMode = null
            },
        )
        AntennaEditorMode.Edit -> selectedAntenna?.let { antenna ->
            AntennaEditorDialog(
                title = "编辑天线",
                initialAntenna = antenna,
                isMutating = state?.isMutatingAntenna == true,
                onDismiss = { editorMode = null },
                onSubmit = {
                    onUpdateSelectedAntenna(it)
                    editorMode = null
                },
            )
        }
        null -> Unit
    }
    if (deleteDialogOpen && selectedAntenna != null) {
        DeleteAntennaDialog(
            antenna = selectedAntenna,
            isMutating = state?.isMutatingAntenna == true,
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                onDeleteSelectedAntenna()
                deleteDialogOpen = false
            },
        )
    }
}

private enum class AntennaEditorMode {
    Create,
    Edit,
}

@Composable
private fun AntennaSummaryRow(
    antennaCount: Int,
    selectedAntenna: Antenna?,
    isLoadingAntennas: Boolean,
    isLoadingNotes: Boolean,
) {
    val colors = LocalHhhlColors.current
    val titleText = listOfNotNull(
        selectedAntenna?.name?.ifBlank { "未命名天线" } ?: "未选择",
        selectedAntenna?.let { "${it.sourceLabel} · ${it.keywordPreview}" },
    ).joinToString(" · ")
    val stateText = when {
        isLoadingAntennas -> "加载天线中"
        isLoadingNotes -> "加载动态中"
        else -> "$antennaCount 条天线"
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
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AntennaCreateButton(
    isMutating: Boolean,
    onClick: () -> Unit,
) {
    HhhlIconActionButton(
        icon = Icons.Filled.Add,
        contentDescription = if (isMutating) "正在新建天线" else "新建天线",
        onClick = onClick,
        enabled = !isMutating,
        emphasized = true,
    )
}

fun antennaSummaryActions(
    hasSelectedAntenna: Boolean,
    isLoadingAntennas: Boolean,
    isLoadingNotes: Boolean,
    onRefreshAntennas: () -> Unit,
    onRefreshNotes: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = if (isLoadingAntennas) "同步天线中" else "刷新天线",
        enabled = !isLoadingAntennas,
        onClick = onRefreshAntennas,
    ),
    HhhlOverflowMenuAction(
        label = if (isLoadingNotes) "同步动态中" else "刷新动态",
        enabled = hasSelectedAntenna && !isLoadingNotes,
        onClick = onRefreshNotes,
    ),
)

@Composable
private fun AntennaHeaderRow(
    antenna: Antenna,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlInlinePanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = antenna.name.ifBlank { "未命名天线" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${antenna.sourceLabel} · ${antenna.keywordPreview}",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlActionChip(
                label = if (isMutating) "处理中" else "编辑",
                enabled = !isMutating,
                onClick = onEdit,
            )
            HhhlOverflowMenu(
                enabled = !isMutating,
                actions = antennaHeaderActions(
                    isMutating = isMutating,
                    onDelete = onDelete,
                ),
            )
        }
    }
    HhhlDivider()
}

fun antennaHeaderActions(
    isMutating: Boolean,
    onDelete: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "删除",
        enabled = !isMutating,
        destructive = true,
        onClick = onDelete,
    ),
)

@Composable
private fun AntennaEditorDialog(
    title: String,
    initialAntenna: Antenna? = null,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AntennaDraft) -> Unit,
) {
    val colors = LocalHhhlColors.current
    var name by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.name.orEmpty()) }
    var keywords by remember(initialAntenna?.id) {
        mutableStateOf(initialAntenna?.keywords?.toKeywordText().orEmpty())
    }
    var source by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.source ?: "all") }
    var withReplies by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.withReplies == true) }
    var withFile by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.withFile == true) }
    var localOnly by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.localOnly == true) }
    var excludeBots by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.excludeBots ?: true) }
    var notify by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.notify == true) }
    var isActive by remember(initialAntenna?.id) { mutableStateOf(initialAntenna?.isActive != false) }
    val canSubmit = name.isNotBlank() && !isMutating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = "天线名称",
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = "关键词",
                    placeholder = "每行一组关键词，空格表示 AND",
                    enabled = !isMutating,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "来源",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        antennaSourceOptions().forEach { option ->
                            HhhlActionChip(
                                label = option.label,
                                emphasized = option.value == source,
                                enabled = !isMutating && option.value != source,
                                onClick = { source = option.value },
                            )
                        }
                    }
                }
                AntennaCheckRow("包含回复", withReplies, !isMutating) { withReplies = it }
                AntennaCheckRow("仅带附件", withFile, !isMutating) { withFile = it }
                AntennaCheckRow("仅本站", localOnly, !isMutating) { localOnly = it }
                AntennaCheckRow("排除机器人", excludeBots, !isMutating) { excludeBots = it }
                AntennaCheckRow("通知", notify, !isMutating) { notify = it }
                AntennaCheckRow("启用", isActive, !isMutating) { isActive = it }
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = {
                    onSubmit(
                        AntennaDraft(
                            name = name,
                            source = source,
                            keywords = keywords.toKeywordGroups(),
                            excludeKeywords = initialAntenna?.excludeKeywords ?: emptyList(),
                            userListId = initialAntenna?.userListId,
                            users = initialAntenna?.users ?: emptyList(),
                            caseSensitive = initialAntenna?.caseSensitive == true,
                            localOnly = localOnly,
                            excludeBots = excludeBots,
                            withReplies = withReplies,
                            withFile = withFile,
                            isActive = isActive,
                            notify = notify,
                            excludeNotesInSensitiveChannel = initialAntenna?.excludeNotesInSensitiveChannel ?: true,
                        ),
                    )
                },
                enabled = canSubmit,
            ) {
                Text(if (isMutating) "处理中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AntennaCheckRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhhlCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = text,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeleteAntennaDialog(
    antenna: Antenna,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除天线") },
        text = {
            Text(
                text = "删除「${antenna.name.ifBlank { "未命名天线" }}」后，天线时间线会从列表移除。",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onDelete, enabled = !isMutating, destructive = true) {
                Text(if (isMutating) "删除中" else "删除")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isMutating) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AntennaPickerRow(
    antennas: List<Antenna>,
    selectedAntenna: Antenna?,
    isLoading: Boolean,
    onSelectAntenna: (Antenna) -> Unit,
) {
    val colors = LocalHhhlColors.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading && antennas.isNotEmpty()) {
            item(key = "antenna-picker-loading", contentType = "antenna-picker-status") {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        text = "加载中",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(
            items = antennas,
            key = { it.id },
            contentType = { "antenna-picker" },
        ) { antenna ->
            val active = selectedAntenna?.id == antenna.id
            AntennaPickerChip(
                antenna = antenna,
                active = active,
                onClick = { onSelectAntenna(antenna) },
            )
        }
    }
}

@Composable
private fun AntennaPickerChip(
    antenna: Antenna,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 132.dp, max = 220.dp)
            .clip(shape)
            .background(
                if (active) colors.buttonSelectedBackground
                else colors.buttonBackground,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = antenna.name.ifBlank { "未命名天线" },
                    color = if (active) {
                        colors.accent
                    } else {
                        colors.textPrimary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (antenna.hasUnreadNote) {
                    Text(
                        text = "新",
                        color = colors.danger,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = "${antenna.sourceLabel} · ${antenna.keywordPreview}",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AntennaStatusRow(
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

private val Antenna.sourceLabel: String
    get() = antennaSourceLabel(source)

private data class AntennaSourceOption(
    val value: String,
    val label: String,
)

private fun antennaSourceOptions(): List<AntennaSourceOption> = listOf(
    AntennaSourceOption("all", "全部"),
    AntennaSourceOption("home", "首页"),
    AntennaSourceOption("users", "指定用户"),
    AntennaSourceOption("list", "列表"),
    AntennaSourceOption("users_blacklist", "排除用户"),
)

private fun antennaSourceLabel(source: String): String {
    return antennaSourceOptions().firstOrNull { it.value == source }?.label ?: source
}

private fun List<List<String>>.toKeywordText(): String {
    return joinToString("\n") { group ->
        group.filter { it.isNotBlank() }.joinToString(" ")
    }
}

private fun String.toKeywordGroups(): List<List<String>> {
    return lineSequence()
        .mapNotNull { line ->
            line.split(antennaWhitespaceRegex)
                .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                .takeIf { it.isNotEmpty() }
        }
        .toList()
}

private val antennaWhitespaceRegex = Regex("\\s+")
