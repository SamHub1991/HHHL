@file:OptIn(ExperimentalLayoutApi::class)

package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageBlock
import cc.hhhl.client.model.PageDraft
import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.model.PageVisibility
import cc.hhhl.client.model.User
import cc.hhhl.client.state.PageUiState
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun PageScreen(
    state: PageUiState? = null,
    onBack: () -> Unit,
    onRefreshPages: () -> Unit = {},
    onKindSelected: (PageListKind) -> Unit = {},
    onOpenPage: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onToggleLikePage: () -> Unit = {},
    onStartCreatingPage: () -> Unit = {},
    onStartEditingPage: () -> Unit = {},
    onCancelEditingPage: () -> Unit = {},
    onDraftChanged: (PageDraft) -> Unit = {},
    onSavePage: () -> Unit = {},
    onDeletePage: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    currentUserId: String? = null,
) {
    val pages = state?.pages.orEmpty()
    val selectedPage = state?.selectedPage
    val selectedKind = state?.selectedKind ?: PageListKind.Featured
    val listState = remember(selectedKind) { LazyListState() }

    state?.editingDraft?.let { draft ->
        PageEditView(
            draft = draft,
            isSaving = state.isSavingPage,
            errorMessage = state.detailErrorMessage,
            title = if (state.editingPageId == null) "新建页面" else "编辑页面",
            onBack = onCancelEditingPage,
            onDraftChanged = onDraftChanged,
            onSave = onSavePage,
        )
        return
    }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = pages.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (selectedPage != null) {
        PageDetailView(
            page = selectedPage,
            isLoading = state.isLoadingDetail,
            isChangingLike = state.isChangingLike,
            errorMessage = state.detailErrorMessage,
            onBack = onCloseDetail,
            onToggleLikePage = onToggleLikePage,
            onStartEditingPage = onStartEditingPage,
            onDeletePage = onDeletePage,
            onOpenUser = onOpenUser,
            canEdit = selectedPage.userId == currentUserId,
            isDeleting = state.isDeletingPage,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = pageMenuActions(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "页面",
            supportingText = selectedKind.label,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = if (overflowActions.isNotEmpty()) {
                {
                    HhhlOverflowMenu(actions = overflowActions)
                }
            } else {
                null
            },
        )
        HhhlDivider()
        PageSummaryRow(
            selectedKind = selectedKind,
            pageCount = pages.size,
            isLoading = state?.isLoadingPages == true,
            onRefreshPages = onRefreshPages,
            onStartCreatingPage = onStartCreatingPage,
        )
        HhhlDivider()
        PageKindFilterRow(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(contentType = "page-status") {
                    PageStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshPages,
                    )
                }
            }
            if (state?.isLoadingPages == true && pages.isEmpty()) {
                item(contentType = "page-status") {
                    PageStatusRow(text = "正在加载页面...", loading = true)
                }
            }
            if (state != null && !state.isLoadingPages && pages.isEmpty() && state.errorMessage == null) {
                item(contentType = "page-status") { PageStatusRow(text = "还没有页面") }
            }
            items(
                items = pages,
                key = { it.id },
                contentType = { "page-row" },
            ) { page ->
                PageRow(
                    page = page,
                    onOpenPage = onOpenPage,
                    onOpenUser = onOpenUser,
                )
            }
            if (state != null && pages.isNotEmpty() && state.isLoadingMore) {
                item(contentType = "page-status") {
                    PageStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageSummaryRow(
    selectedKind: PageListKind,
    pageCount: Int,
    isLoading: Boolean,
    onRefreshPages: () -> Unit,
    onStartCreatingPage: () -> Unit,
) {
    val stateText = if (isLoading) "加载中" else "${pageCount} 项"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${selectedKind.label} · $stateText",
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
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isLoading) "同步页面中" else "刷新页面",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefreshPages,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Add,
                contentDescription = "新建页面",
                onClick = onStartCreatingPage,
            )
        }
    }
}

fun pagePrimaryKinds(): List<PageListKind> = listOf(PageListKind.Featured)

fun pageOverflowKinds(): List<PageListKind> =
    PageListKind.entries - pagePrimaryKinds().toSet()

private fun pageMenuActions(
    selectedKind: PageListKind,
    onKindSelected: (PageListKind) -> Unit,
): List<HhhlOverflowMenuAction> {
    return pageOverflowKinds().map { kind ->
        val prefix = if (kind == selectedKind) "当前" else "切换到"
        HhhlOverflowMenuAction(
            label = "$prefix ${kind.label}",
            enabled = kind != selectedKind,
            onClick = { onKindSelected(kind) },
        )
    }
}

@Composable
private fun PageRow(
    page: Page,
    onOpenPage: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPage(page.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            initial = page.author.avatarInitial,
            avatarUrl = page.author.avatarUrl,
            modifier = Modifier.clickable { onOpenUser(page.author.id) },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = page.title.ifBlank { page.name.ifBlank { "未命名页面" } },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = page.summary.ifBlank { page.pathLabel },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${page.author.username} · ${page.likedCount} 喜欢 · ${page.updatedAtLabel}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HhhlDivider()
}

@Composable
private fun PageDetailView(
    page: Page,
    isLoading: Boolean,
    isChangingLike: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleLikePage: () -> Unit,
    onStartEditingPage: () -> Unit,
    onDeletePage: () -> Unit,
    onOpenUser: (String) -> Unit,
    canEdit: Boolean,
    isDeleting: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val actions = if (canEdit) {
            listOf(
                HhhlOverflowMenuAction(
                    label = "编辑",
                    enabled = !isDeleting,
                    onClick = onStartEditingPage,
                ),
                HhhlOverflowMenuAction(
                    label = if (isDeleting) "删除中" else "删除",
                    enabled = !isDeleting,
                    onClick = onDeletePage,
                ),
            )
        } else {
            emptyList()
        }
        HhhlTopBar(
            title = "页面",
            supportingText = page.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = if (actions.isNotEmpty()) {
                { HhhlOverflowMenu(actions = actions) }
            } else {
                null
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item(contentType = "page-detail-status") {
                    PageStatusRow(text = "正在加载页面...", loading = true)
                }
            }
            errorMessage?.let { message ->
                item(contentType = "page-detail-status") { PageStatusRow(text = message) }
            }
            item(contentType = "page-detail-header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = page.title.ifBlank { "未命名页面" },
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenUser(page.author.id) },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                initial = page.author.avatarInitial,
                                avatarUrl = page.author.avatarUrl,
                            )
                            Column {
                                Text(
                                    text = page.author.displayName,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "@${page.author.username} · ${page.updatedAtLabel}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        HhhlActionChip(
                            label = when {
                                isChangingLike -> "处理中"
                                page.isLiked -> "已喜欢"
                                else -> "喜欢"
                            },
                            enabled = !isChangingLike,
                            emphasized = page.isLiked,
                            onClick = onToggleLikePage,
                        )
                    }
                    if (page.summary.isNotBlank()) {
                        Text(
                            text = page.summary,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                HhhlDivider()
            }
            items(
                items = page.blocks,
                key = { it.id },
                contentType = { "page-block" },
            ) { block ->
                PageBlockRow(block = block)
            }
        }
    }
}

@Composable
private fun PageEditView(
    draft: PageDraft,
    isSaving: Boolean,
    errorMessage: String?,
    title: String,
    onBack: () -> Unit,
    onDraftChanged: (PageDraft) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = title,
            supportingText = draft.name.ifBlank { "草稿" },
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            errorMessage?.let { message ->
                item(contentType = "page-edit-status") { PageStatusRow(text = message) }
            }
            item(contentType = "page-edit-form") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HhhlTextInput(
                        value = draft.title,
                        onValueChange = { onDraftChanged(draft.copy(title = it)) },
                        placeholder = "标题",
                        label = "标题",
                        enabled = !isSaving,
                        singleLine = true,
                    )
                    HhhlTextInput(
                        value = draft.name,
                        onValueChange = { onDraftChanged(draft.copy(name = it)) },
                        placeholder = "page-name",
                        label = "路径名",
                        enabled = !isSaving,
                        singleLine = true,
                    )
                    HhhlTextInput(
                        value = draft.summary,
                        onValueChange = { onDraftChanged(draft.copy(summary = it)) },
                        placeholder = "摘要",
                        label = "摘要",
                        enabled = !isSaving,
                        minLines = 2,
                        maxLines = 3,
                    )
                    HhhlTextInput(
                        value = draft.content,
                        onValueChange = { onDraftChanged(draft.copy(content = it)) },
                        placeholder = "正文",
                        label = "正文",
                        enabled = !isSaving,
                        minLines = 6,
                    )
                    HhhlTextInput(
                        value = draft.fileIds.joinToString(", "),
                        onValueChange = { value ->
                            onDraftChanged(
                                draft.copy(
                                    fileIds = value
                                        .split(',', '\n')
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                ),
                            )
                        },
                        placeholder = "fileId-1, fileId-2",
                        label = "文件 ID",
                        enabled = !isSaving,
                        singleLine = true,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PageVisibility.entries.forEach { visibility ->
                            HhhlActionChip(
                                label = visibility.label,
                                emphasized = draft.visibility == visibility,
                                enabled = !isSaving,
                                onClick = { onDraftChanged(draft.copy(visibility = visibility)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HhhlActionChip(
                            label = if (isSaving) "保存中" else "保存",
                            emphasized = true,
                            enabled = !isSaving && draft.canSubmit,
                            onClick = onSave,
                        )
                        HhhlActionChip(
                            label = "取消",
                            enabled = !isSaving,
                            onClick = onBack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageKindFilterRow(
    selectedKind: PageListKind,
    onKindSelected: (PageListKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pagePrimaryKinds().forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = kind == selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
    }
}

@Composable
private fun PageBlockRow(block: PageBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (block.type != "text") {
            Text(
                text = block.type,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = block.text.ifBlank { "空白块" },
            color = MaterialTheme.colorScheme.onBackground,
            style = if (block.type == "section") {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (block.type == "section") FontWeight.SemiBold else FontWeight.Normal,
        )
    }
    HhhlDivider()
}

@Composable
private fun PageStatusRow(
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
