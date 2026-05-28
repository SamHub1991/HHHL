@file:OptIn(ExperimentalLayoutApi::class)

package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashDraft
import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.User
import cc.hhhl.client.state.FlashDraftMode
import cc.hhhl.client.state.FlashUiState
import cc.hhhl.client.theme.LocalHhhlColors
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
import cc.hhhl.client.ui.component.InlineRichText

@Composable
fun FlashScreen(
    state: FlashUiState? = null,
    onBack: () -> Unit,
    onRefreshFlashes: () -> Unit = {},
    onKindSelected: (FlashListKind) -> Unit = {},
    onOpenFlash: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onToggleLikeFlash: () -> Unit = {},
    onStartCreateFlash: () -> Unit = {},
    onStartEditFlash: () -> Unit = {},
    onDraftChanged: (FlashDraft) -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onCancelDraft: () -> Unit = {},
    onDeleteFlash: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenFlashInWeb: (String) -> Unit = {},
) {
    val flashes = state?.flashes.orEmpty()
    val selectedFlash = state?.selectedFlash
    val selectedKind = state?.selectedKind ?: FlashListKind.Featured
    val listState = remember(selectedKind) { LazyListState() }

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = flashes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (state?.draftMode != null) {
        FlashDraftView(
            mode = state.draftMode,
            draft = state.draft,
            isSaving = state.isSavingDraft,
            errorMessage = state.draftErrorMessage,
            onDraftChanged = onDraftChanged,
            onSaveDraft = onSaveDraft,
            onCancelDraft = onCancelDraft,
        )
        return
    }

    if (selectedFlash != null) {
        FlashDetailView(
            flash = selectedFlash,
            isLoading = state.isLoadingDetail,
            isChangingLike = state.isChangingLike,
            isDeleting = state.isDeletingFlash,
            errorMessage = state.detailErrorMessage,
            onBack = onCloseDetail,
            onToggleLikeFlash = onToggleLikeFlash,
            onEditFlash = onStartEditFlash,
            onDeleteFlash = onDeleteFlash,
            onOpenUser = onOpenUser,
            onOpenFlashInWeb = onOpenFlashInWeb,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = flashMenuActions(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "Play",
            supportingText = selectedKind.label,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HhhlIconActionButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "新建 Play",
                        emphasized = true,
                        onClick = onStartCreateFlash,
                    )
                    if (overflowActions.isNotEmpty()) {
                        HhhlOverflowMenu(actions = overflowActions, buttonText = "更多")
                    }
                }
            },
        )
        HhhlDivider()
        FlashSummaryRow(
            selectedKind = selectedKind,
            flashCount = flashes.size,
            isLoading = state?.isLoadingFlashes == true,
            onRefreshFlashes = onRefreshFlashes,
        )
        HhhlDivider()
        FlashKindFilterRow(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(key = "flash-error-${selectedKind.name}", contentType = "flash-status") {
                    FlashStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshFlashes,
                    )
                }
            }
            if (state?.isLoadingFlashes == true && flashes.isEmpty()) {
                item(key = "flash-loading-${selectedKind.name}", contentType = "flash-status") {
                    FlashStatusRow(text = "正在加载 Play...", loading = true)
                }
            }
            if (state != null && !state.isLoadingFlashes && flashes.isEmpty() && state.errorMessage == null) {
                item(key = "flash-empty-${selectedKind.name}", contentType = "flash-status") {
                    FlashStatusRow(text = "还没有 Play")
                }
            }
            items(
                items = flashes,
                key = { it.id },
                contentType = { "flash-row" },
            ) { flash ->
                FlashRow(
                    flash = flash,
                    onOpenFlash = onOpenFlash,
                    onOpenUser = onOpenUser,
                )
            }
            if (state != null && flashes.isNotEmpty() && state.isLoadingMore) {
                item(key = "flash-loading-more-${selectedKind.name}", contentType = "flash-status") {
                    FlashStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashDraftView(
    mode: FlashDraftMode,
    draft: FlashDraft,
    isSaving: Boolean,
    errorMessage: String?,
    onDraftChanged: (FlashDraft) -> Unit,
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
) {
    val scriptPreview = remember(draft.script) {
        draft.script.lineSequence()
            .take(80)
            .joinToString("\n")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = if (mode == FlashDraftMode.Create) "新建 Play" else "编辑 Play",
            supportingText = if (isSaving) "保存中" else draft.visibility.ifBlank { "public" },
            navigation = { HhhlBackButton(onClick = onCancelDraft) },
            action = {
                HhhlActionChip(
                    label = if (isSaving) "保存中" else "保存",
                    emphasized = true,
                    enabled = !isSaving,
                    onClick = onSaveDraft,
                )
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            errorMessage?.let { message ->
                item(key = "flash-edit-error", contentType = "flash-edit-status") {
                    FlashStatusRow(text = message)
                }
            }
            item(key = "flash-edit-form", contentType = "flash-edit-form") {
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
                        value = draft.summary,
                        onValueChange = { onDraftChanged(draft.copy(summary = it)) },
                        placeholder = "摘要，可留空",
                        label = "摘要",
                        enabled = !isSaving,
                        minLines = 2,
                        maxLines = 4,
                    )
                    FlashVisibilityEditor(
                        visibility = draft.visibility,
                        enabled = !isSaving,
                        onVisibilityChanged = { onDraftChanged(draft.copy(visibility = it)) },
                    )
                    HhhlTextInput(
                        value = draft.permissions.joinToString(", "),
                        onValueChange = { value ->
                            onDraftChanged(
                                draft.copy(
                                    permissions = value.split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                ),
                            )
                        },
                        placeholder = "权限，用逗号分隔，可留空",
                        label = "权限",
                        enabled = !isSaving,
                        singleLine = true,
                    )
                    HhhlTextInput(
                        value = draft.script,
                        onValueChange = { onDraftChanged(draft.copy(script = it)) },
                        placeholder = "AiScript / Play 脚本",
                        label = "脚本",
                        enabled = !isSaving,
                        minLines = 8,
                        maxLines = 16,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Text(
                        text = "移动端暂不执行 Play 脚本，下方仅显示代码预览。",
                        color = LocalHhhlColors.current.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlashCodeBlock(scriptPreview.ifBlank { "暂无脚本" })
                }
            }
        }
    }
}

@Composable
private fun FlashVisibilityEditor(
    visibility: String,
    enabled: Boolean,
    onVisibilityChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "可见性",
            color = LocalHhhlColors.current.textMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("public" to "公开", "private" to "私密").forEach { (value, label) ->
                HhhlActionChip(
                    label = label,
                    emphasized = visibility == value,
                    enabled = enabled,
                    onClick = { onVisibilityChanged(value) },
                )
            }
        }
    }
}

@Composable
private fun FlashSummaryRow(
    selectedKind: FlashListKind,
    flashCount: Int,
    isLoading: Boolean,
    onRefreshFlashes: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val stateText = if (isLoading) "加载中" else "${flashCount} 项"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${selectedKind.label} · $stateText",
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
                contentDescription = if (isLoading) "同步 Play 中" else "刷新 Play",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefreshFlashes,
            )
        }
    }
}

fun flashPrimaryKinds(): List<FlashListKind> = listOf(
    FlashListKind.Featured,
)

fun flashOverflowKinds(): List<FlashListKind> =
    FlashListKind.entries - flashPrimaryKinds().toSet()

private fun flashMenuActions(
    selectedKind: FlashListKind,
    onKindSelected: (FlashListKind) -> Unit,
): List<HhhlOverflowMenuAction> {
    return flashOverflowKinds().map { kind ->
        val prefix = if (kind == selectedKind) "当前" else "切换到"
        HhhlOverflowMenuAction(
            label = "$prefix ${kind.label}",
            enabled = kind != selectedKind,
            onClick = { onKindSelected(kind) },
        )
    }
}

@Composable
private fun FlashRow(
    flash: Flash,
    onOpenFlash: (String) -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFlash(flash.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            initial = flash.author.avatarInitial,
            avatarUrl = flash.author.avatarUrl,
            modifier = Modifier.clickable { onOpenUser(flash.author.id) },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = flash.title.ifBlank { "未命名 Play" },
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (flash.summary.isNotBlank()) {
                InlineRichText(
                    text = flash.summary,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxChars = 280,
                )
            }
            Text(
                text = "@${flash.author.username} · ${flash.visibilityLabel} · ${flash.likedCount} 喜欢",
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
private fun FlashDetailView(
    flash: Flash,
    isLoading: Boolean,
    isChangingLike: Boolean,
    isDeleting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleLikeFlash: () -> Unit,
    onEditFlash: () -> Unit,
    onDeleteFlash: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenFlashInWeb: (String) -> Unit,
) {
    var deleteConfirmOpen by remember(flash.id) { mutableStateOf(false) }
    val colors = LocalHhhlColors.current
    val codePreview = remember(flash.script) {
        flash.script.lineSequence()
            .take(120)
            .joinToString("\n")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "Play",
            supportingText = flash.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlOverflowMenu(
                    actions = listOf(
                        HhhlOverflowMenuAction(
                            label = "网页版运行",
                            onClick = { onOpenFlashInWeb(flash.id) },
                        ),
                        HhhlOverflowMenuAction(
                            label = "编辑",
                            enabled = !isDeleting,
                            onClick = onEditFlash,
                        ),
                        HhhlOverflowMenuAction(
                            label = if (isDeleting) "删除中" else "删除",
                            enabled = !isDeleting,
                            onClick = { deleteConfirmOpen = true },
                        ),
                    ),
                    buttonText = "更多",
                )
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item(key = "flash-detail-loading-${flash.id}", contentType = "flash-detail-status") {
                    FlashStatusRow(text = "正在加载 Play...", loading = true)
                }
            }
            errorMessage?.let { message ->
                item(key = "flash-detail-error-${flash.id}", contentType = "flash-detail-status") {
                    FlashStatusRow(text = message)
                }
            }
            item(key = "flash-detail-${flash.id}", contentType = "flash-detail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = flash.title.ifBlank { "未命名 Play" },
                        color = colors.textPrimary,
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
                                .clickable { onOpenUser(flash.author.id) },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                initial = flash.author.avatarInitial,
                                avatarUrl = flash.author.avatarUrl,
                            )
                            Column {
                                Text(
                                    text = flash.author.displayName,
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "@${flash.author.username} · ${flash.visibilityLabel} · ${flash.updatedAtLabel}",
                                    color = colors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        HhhlActionChip(
                            label = when {
                                isChangingLike -> "处理中"
                                flash.isLiked -> "已喜欢"
                                else -> "喜欢"
                            },
                            enabled = !isChangingLike,
                            emphasized = flash.isLiked,
                            onClick = onToggleLikeFlash,
                        )
                    }
                    if (flash.summary.isNotBlank()) {
                        InlineRichText(
                            text = flash.summary,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    FlashRuntimePreview(
                        script = codePreview,
                        hasScript = flash.script.isNotBlank(),
                        onOpenInWeb = { onOpenFlashInWeb(flash.id) },
                    )
                    Text(
                        text = "${flash.likedCount} 喜欢 · 创建 ${flash.createdAtLabel.ifBlank { "未知" }}",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HhhlDivider()
            }
        }
    }
    if (deleteConfirmOpen) {
        HhhlAlertDialog(
            onDismissRequest = { if (!isDeleting) deleteConfirmOpen = false },
            title = { Text("删除 Play") },
            text = {
                Text(
                    text = "删除后无法在移动端恢复。服务器会校验权限。",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                HhhlTextButton(
                    onClick = {
                        deleteConfirmOpen = false
                        onDeleteFlash()
                    },
                    enabled = !isDeleting,
                ) {
                    Text(if (isDeleting) "删除中" else "删除")
                }
            },
            dismissButton = {
                HhhlTextButton(onClick = { deleteConfirmOpen = false }, enabled = !isDeleting) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun FlashRuntimePreview(
    script: String,
    hasScript: Boolean,
    onOpenInWeb: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.mediaBackground)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "运行预览",
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "移动端暂未内置 AiScript Play 运行器，当前不会执行脚本。",
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        HhhlActionChip(
            label = "网页版运行",
            emphasized = true,
            onClick = onOpenInWeb,
        )
        FlashCodeBlock(if (hasScript) script else "暂无脚本")
    }
}

fun flashWebPath(flashId: String): String {
    val cleanId = flashId.trim()
    return if (cleanId.isBlank()) "/play" else "/play/$cleanId"
}

@Composable
private fun FlashCodeBlock(text: String) {
    val colors = LocalHhhlColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.72f))
            .padding(10.dp),
    )
}

@Composable
private fun FlashKindFilterRow(
    selectedKind: FlashListKind,
    onKindSelected: (FlashListKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        flashPrimaryKinds().forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = kind == selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
        if (selectedKind !in flashPrimaryKinds()) {
            HhhlActionChip(
                label = selectedKind.label,
                emphasized = true,
                onClick = { onKindSelected(selectedKind) },
            )
        }
    }
}

@Composable
private fun FlashStatusRow(
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
