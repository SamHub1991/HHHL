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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.Flash
import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.User
import cc.hhhl.client.state.FlashUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTopBar

@Composable
fun FlashScreen(
    state: FlashUiState? = null,
    onBack: () -> Unit,
    onRefreshFlashes: () -> Unit = {},
    onKindSelected: (FlashListKind) -> Unit = {},
    onOpenFlash: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onToggleLikeFlash: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
) {
    val flashes = state?.flashes ?: fakeFlashes()
    val selectedFlash = state?.selectedFlash
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = flashes.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (selectedFlash != null) {
        FlashDetailView(
            flash = selectedFlash,
            isLoading = state.isLoadingDetail,
            isChangingLike = state.isChangingLike,
            errorMessage = state.detailErrorMessage,
            onBack = onCloseDetail,
            onToggleLikeFlash = onToggleLikeFlash,
            onOpenUser = onOpenUser,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = flashMenuActions(
            selectedKind = state?.selectedKind ?: FlashListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "Play",
            supportingText = (state?.selectedKind ?: FlashListKind.Featured).label,
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
        FlashSummaryRow(
            selectedKind = state?.selectedKind ?: FlashListKind.Featured,
            flashCount = flashes.size,
            isLoading = state?.isLoadingFlashes == true,
            onRefreshFlashes = onRefreshFlashes,
        )
        HhhlDivider()
        FlashKindFilterRow(
            selectedKind = state?.selectedKind ?: FlashListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item {
                    FlashStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshFlashes,
                    )
                }
            }
            if (state?.isLoadingFlashes == true && flashes.isEmpty()) {
                item { FlashStatusRow(text = "正在加载 Play...", loading = true) }
            }
            if (state != null && !state.isLoadingFlashes && flashes.isEmpty() && state.errorMessage == null) {
                item { FlashStatusRow(text = "还没有 Play") }
            }
            items(flashes, key = { it.id }) { flash ->
                FlashRow(
                    flash = flash,
                    onOpenFlash = onOpenFlash,
                    onOpenUser = onOpenUser,
                )
            }
            if (state != null && flashes.isNotEmpty() && !state.endReached) {
                item {
                    FlashStatusRow(
                        text = if (state.isLoadingMore) "正在加载更多..." else "加载更多",
                        loading = state.isLoadingMore,
                        onAction = if (state.isLoadingMore) null else onLoadMore,
                    )
                }
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
                label = if (isLoading) "同步 Play 中" else "刷新 Play",
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
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = flash.summary,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${flash.author.username} · ${flash.visibilityLabel} · ${flash.likedCount} 喜欢",
                color = LocalHhhlColors.current.subtleText,
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
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleLikeFlash: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "Play",
            supportingText = flash.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item { FlashStatusRow(text = "正在加载 Play...", loading = true) }
            }
            errorMessage?.let { message ->
                item { FlashStatusRow(text = message) }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = flash.title.ifBlank { "未命名 Play" },
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
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "@${flash.author.username} · ${flash.visibilityLabel} · ${flash.updatedAtLabel}",
                                    color = LocalHhhlColors.current.subtleText,
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
                        Text(
                            text = flash.summary,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (flash.scriptPreview.isNotBlank()) {
                        Text(
                            text = flash.scriptPreview,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(LocalHhhlColors.current.mediaBackground)
                                .padding(10.dp),
                        )
                    }
                    Text(
                        text = "${flash.likedCount} 喜欢",
                        color = LocalHhhlColors.current.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HhhlDivider()
            }
        }
    }
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

private fun fakeFlashes(): List<Flash> {
    return listOf(
        Flash(
            id = "flash-featured",
            title = "HHHL Play",
            summary = "站内互动内容",
            script = "Ui:render([Ui:C:text({text: \"Hello HHHL\"})])",
            visibility = "public",
            author = User("me", "HHHL", "me", "H"),
            userId = "me",
            likedCount = 4,
            isLiked = false,
        ),
    )
}
