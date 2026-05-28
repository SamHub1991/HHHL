package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Announcement
import cc.hhhl.client.repository.AnnouncementDraft
import cc.hhhl.client.state.AnnouncementUiState
import cc.hhhl.client.theme.HhhlColors
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.InlineRichText

@Composable
fun AnnouncementScreen(
    state: AnnouncementUiState? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenAnnouncement: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onMarkRead: (String) -> Unit = {},
    onEnterManagement: () -> Unit = {},
    onExitManagement: () -> Unit = {},
    onRefreshAdmin: () -> Unit = {},
    onCreateAnnouncement: (AnnouncementDraft) -> Unit = {},
    onUpdateAnnouncement: (String, AnnouncementDraft) -> Unit = { _, _ -> },
    onDeleteAnnouncement: (String) -> Unit = {},
) {
    val announcements = state?.announcements.orEmpty()
    val selectedAnnouncement = state?.selectedAnnouncement
    val listState = rememberLazyListState()
    var editorMode by remember { mutableStateOf<AnnouncementEditorMode?>(null) }
    var deleteTarget by remember { mutableStateOf<Announcement?>(null) }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = announcements.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (state?.isManaging == true) {
        AnnouncementManagementView(
            announcements = announcements,
            state = state,
            listState = listState,
            onBack = onExitManagement,
            onRefresh = onRefreshAdmin,
            onCreate = { editorMode = AnnouncementEditorMode.Create },
            onEdit = { editorMode = AnnouncementEditorMode.Edit(it) },
            onDelete = { deleteTarget = it },
        )
        when (val mode = editorMode) {
            AnnouncementEditorMode.Create -> AnnouncementEditorDialog(
                title = "新建公告",
                isMutating = state.isMutatingAnnouncement,
                onDismiss = { editorMode = null },
                onSubmit = {
                    onCreateAnnouncement(it)
                    editorMode = null
                },
            )
            is AnnouncementEditorMode.Edit -> AnnouncementEditorDialog(
                title = "编辑公告",
                initialAnnouncement = mode.announcement,
                isMutating = state.isMutatingAnnouncement,
                onDismiss = { editorMode = null },
                onSubmit = {
                    onUpdateAnnouncement(mode.announcement.id, it)
                    editorMode = null
                },
            )
            null -> Unit
        }
        deleteTarget?.let { announcement ->
            DeleteAnnouncementDialog(
                announcement = announcement,
                isMutating = state.pendingAnnouncementIds.contains(announcement.id),
                onDismiss = { deleteTarget = null },
                onDelete = {
                    onDeleteAnnouncement(announcement.id)
                    deleteTarget = null
                },
            )
        }
        return
    }

    state?.let { currentState ->
        selectedAnnouncement?.let { announcement ->
            AnnouncementDetailView(
                announcement = announcement,
                isLoading = currentState.isLoadingDetail,
                errorMessage = currentState.detailErrorMessage,
                actionErrorMessage = currentState.actionErrorMessage,
                isPending = currentState.pendingAnnouncementIds.contains(announcement.id),
                onBack = onCloseDetail,
                onMarkRead = onMarkRead,
            )
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "公告",
            supportingText = when {
                state?.isLoading == true -> "同步中"
                announcements.isEmpty() -> "站点消息"
                announcements.any { !it.isRead } -> "${announcements.count { !it.isRead }} 条未读 / ${announcements.size} 条"
                else -> "${announcements.size} 条公告"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlActionChip(
                    label = "管理",
                    enabled = state != null,
                    onClick = onEnterManagement,
                )
            },
        )
        HhhlDivider()
        AnnouncementSummaryRow(
            announcementCount = announcements.size,
            unreadCount = announcements.count { !it.isRead },
            isLoading = state?.isLoading == true,
            onRefresh = onRefresh,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(key = "announcement-error", contentType = "announcement-status") {
                    AnnouncementStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state?.isLoading == true && announcements.isEmpty()) {
                item(key = "announcement-loading", contentType = "announcement-status") {
                    AnnouncementStatusRow(text = "正在加载公告...", loading = true)
                }
            }
            if (state != null && !state.isLoading && announcements.isEmpty() && state.errorMessage == null) {
                item(key = "announcement-empty", contentType = "announcement-status") { AnnouncementStatusRow(text = "还没有公告") }
            }
            items(
                items = announcements,
                key = { it.id },
                contentType = { "announcement-row" },
            ) { announcement ->
                AnnouncementRow(
                    announcement = announcement,
                    onOpenAnnouncement = onOpenAnnouncement,
                )
            }
            if (state != null && announcements.isNotEmpty() && state.isLoadingMore) {
                item(key = "announcement-loading-more", contentType = "announcement-status") {
                    AnnouncementStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

private sealed interface AnnouncementEditorMode {
    data object Create : AnnouncementEditorMode
    data class Edit(val announcement: Announcement) : AnnouncementEditorMode
}

@Composable
private fun AnnouncementManagementView(
    announcements: List<Announcement>,
    state: AnnouncementUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Announcement) -> Unit,
    onDelete: (Announcement) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "公告管理",
            supportingText = when {
                state.isLoadingAdmin -> "同步中"
                announcements.isEmpty() -> "创建和维护站点公告"
                else -> "${announcements.size} 条公告"
            },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "新建公告",
                        emphasized = true,
                        enabled = !state.isMutatingAnnouncement,
                        onClick = onCreate,
                    )
                    HhhlIconActionButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = if (state.isLoadingAdmin) "同步中" else "刷新",
                        enabled = !state.isLoadingAdmin,
                        onClick = onRefresh,
                    )
                }
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state.adminActionMessage?.let { message ->
                item(key = "announcement-admin-message", contentType = "announcement-admin-status") { AnnouncementStatusRow(text = message) }
            }
            state.adminErrorMessage?.let { message ->
                item(key = "announcement-admin-error", contentType = "announcement-admin-status") {
                    AnnouncementStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (state.isLoadingAdmin && announcements.isEmpty()) {
                item(key = "announcement-admin-loading", contentType = "announcement-admin-status") {
                    AnnouncementStatusRow(text = "正在加载公告管理列表...", loading = true)
                }
            }
            if (!state.isLoadingAdmin && announcements.isEmpty() && state.adminErrorMessage == null) {
                item(key = "announcement-admin-empty", contentType = "announcement-admin-status") { AnnouncementStatusRow(text = "还没有公告") }
            }
            items(
                items = announcements,
                key = { "admin-announcement-${it.id}" },
                contentType = { "announcement-admin-row" },
            ) { announcement ->
                AnnouncementManagementRow(
                    announcement = announcement,
                    isMutating = state.isMutatingAnnouncement || state.pendingAnnouncementIds.contains(announcement.id),
                    onEdit = { onEdit(announcement) },
                    onDelete = { onDelete(announcement) },
                )
            }
        }
    }
}

@Composable
private fun AnnouncementManagementRow(
    announcement: Announcement,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = announcementIcon(announcement.icon),
            color = announcementIconColor(announcement.icon, colors),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = announcement.title.ifBlank { "公告" },
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InlineRichText(
                text = announcement.text,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxChars = 320,
            )
            Text(
                text = "${announcement.iconLabel} · ${announcement.displayLabel} · ${announcement.createdAtLabel}",
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
            actions = announcementManagementActions(
                isMutating = isMutating,
                onDelete = onDelete,
            ),
        )
    }
    HhhlDivider()
}

fun announcementManagementActions(
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
internal fun AnnouncementSummaryRow(
    announcementCount: Int,
    unreadCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val countText = if (announcementCount == 0) "暂无公告" else "${announcementCount} 条公告"
    val stateText = when {
        isLoading -> "加载中"
        unreadCount > 0 -> "${unreadCount} 条未读"
        else -> "全部已读"
    }
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isLoading) "同步中" else "刷新公告",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
internal fun AnnouncementRow(
    announcement: Announcement,
    onOpenAnnouncement: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAnnouncement(announcement.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = announcementIcon(announcement.icon),
            color = announcementIconColor(announcement.icon, colors),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = announcement.title.ifBlank { "公告" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!announcement.isRead) {
                    Text(
                        text = "未读",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            InlineRichText(
                text = announcement.text,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxChars = 320,
            )
            Text(
                text = "${announcement.displayLabel} · ${announcement.createdAtLabel}",
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HhhlDivider()
}

@Composable
internal fun AnnouncementDetailView(
    announcement: Announcement,
    isLoading: Boolean,
    errorMessage: String?,
    actionErrorMessage: String?,
    isPending: Boolean,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "公告",
            supportingText = listOf(
                announcement.displayLabel,
                announcement.createdAtLabel,
            ).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                if (!announcement.isRead) {
                    HhhlActionChip(
                        label = if (isPending) "处理中" else "标记已读",
                        onClick = { onMarkRead(announcement.id) },
                        enabled = !isPending,
                        emphasized = true,
                    )
                }
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item(key = "announcement-detail-loading", contentType = "announcement-detail-status") {
                    AnnouncementStatusRow(text = "正在加载公告...", loading = true)
                }
            }
            errorMessage?.let { message ->
                item(key = "announcement-detail-error", contentType = "announcement-detail-status") { AnnouncementStatusRow(text = message) }
            }
            actionErrorMessage?.let { message ->
                item(key = "announcement-detail-action-error", contentType = "announcement-detail-status") { AnnouncementStatusRow(text = message) }
            }
            item(key = "announcement-detail-${announcement.id}", contentType = "announcement-detail") {
                val colors = LocalHhhlColors.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = announcement.title.ifBlank { "公告" },
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${announcement.iconLabel} · ${announcement.displayLabel} · ${announcement.createdAtLabel}",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    InlineRichText(
                        text = announcement.text,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    announcement.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        Text(
                            text = imageUrl,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HhhlDivider()
            }
        }
    }
}

@Composable
private fun AnnouncementEditorDialog(
    title: String,
    initialAnnouncement: Announcement? = null,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AnnouncementDraft) -> Unit,
) {
    var announcementTitle by remember(initialAnnouncement?.id) {
        mutableStateOf(initialAnnouncement?.title.orEmpty())
    }
    var text by remember(initialAnnouncement?.id) {
        mutableStateOf(initialAnnouncement?.text.orEmpty())
    }
    var icon by remember(initialAnnouncement?.id) {
        mutableStateOf(initialAnnouncement?.icon ?: "info")
    }
    var display by remember(initialAnnouncement?.id) {
        mutableStateOf(initialAnnouncement?.display ?: "normal")
    }
    val canSubmit = announcementTitle.isNotBlank() && text.isNotBlank() && !isMutating

    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = announcementTitle,
                    onValueChange = { announcementTitle = it },
                    label = "标题",
                    placeholder = "公告标题",
                    singleLine = true,
                    enabled = !isMutating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = text,
                    onValueChange = { text = it },
                    label = "内容",
                    placeholder = "公告内容",
                    enabled = !isMutating,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                AnnouncementOptionChips(
                    label = "图标",
                    options = announcementIconOptions(),
                    selected = icon,
                    enabled = !isMutating,
                    onSelected = { icon = it },
                )
                AnnouncementOptionChips(
                    label = "显示",
                    options = announcementDisplayOptions(),
                    selected = display,
                    enabled = !isMutating,
                    onSelected = { display = it },
                )
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = {
                    onSubmit(
                        AnnouncementDraft(
                            title = announcementTitle,
                            text = text,
                            icon = icon,
                            display = display,
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
private fun AnnouncementOptionChips(
    label: String,
    options: List<AnnouncementOption>,
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                HhhlActionChip(
                    label = option.label,
                    emphasized = option.value == selected,
                    enabled = enabled && option.value != selected,
                    onClick = { onSelected(option.value) },
                )
            }
        }
    }
}

@Composable
private fun DeleteAnnouncementDialog(
    announcement: Announcement,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    HhhlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除公告") },
        text = {
            Text(
                text = "删除「${announcement.title.ifBlank { "公告" }}」后，用户侧公告列表会同步移除。",
                color = LocalHhhlColors.current.textSecondary,
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
private fun AnnouncementStatusRow(
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

private fun announcementIcon(icon: String): String {
    return when (icon) {
        "warning" -> "!"
        "error" -> "×"
        "success" -> "✓"
        else -> "i"
    }
}

private fun announcementIconColor(icon: String, colors: HhhlColors): Color {
    return when (icon) {
        "warning" -> colors.warning
        "error" -> colors.danger
        "success" -> colors.success
        else -> colors.accent
    }
}

private data class AnnouncementOption(
    val value: String,
    val label: String,
)

private fun announcementIconOptions(): List<AnnouncementOption> = listOf(
    AnnouncementOption("info", "信息"),
    AnnouncementOption("warning", "警告"),
    AnnouncementOption("success", "成功"),
    AnnouncementOption("error", "错误"),
)

private fun announcementDisplayOptions(): List<AnnouncementOption> = listOf(
    AnnouncementOption("normal", "普通"),
    AnnouncementOption("banner", "横幅"),
    AnnouncementOption("dialog", "弹窗"),
)
