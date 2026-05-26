package cc.hhhl.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Page
import cc.hhhl.client.model.PageBlock
import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.model.User
import cc.hhhl.client.state.PageUiState
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
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
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
) {
    val pages = state?.pages ?: fakePages()
    val selectedPage = state?.selectedPage
    val listState = rememberLazyListState()

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
            onOpenUser = onOpenUser,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = pageMenuActions(
            selectedKind = state?.selectedKind ?: PageListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "页面",
            supportingText = (state?.selectedKind ?: PageListKind.Featured).label,
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
            selectedKind = state?.selectedKind ?: PageListKind.Featured,
            pageCount = pages.size,
            isLoading = state?.isLoadingPages == true,
            onRefreshPages = onRefreshPages,
        )
        HhhlDivider()
        PageKindFilterRow(
            selectedKind = state?.selectedKind ?: PageListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item {
                    PageStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshPages,
                    )
                }
            }
            if (state?.isLoadingPages == true && pages.isEmpty()) {
                item { PageStatusRow(text = "正在加载页面...", loading = true) }
            }
            if (state != null && !state.isLoadingPages && pages.isEmpty() && state.errorMessage == null) {
                item { PageStatusRow(text = "还没有页面") }
            }
            items(pages, key = { it.id }) { page ->
                PageRow(
                    page = page,
                    onOpenPage = onOpenPage,
                    onOpenUser = onOpenUser,
                )
            }
            if (state != null && pages.isNotEmpty() && !state.endReached) {
                item {
                    PageStatusRow(
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
private fun PageSummaryRow(
    selectedKind: PageListKind,
    pageCount: Int,
    isLoading: Boolean,
    onRefreshPages: () -> Unit,
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
            HhhlActionChip(
                label = if (isLoading) "同步页面中" else "刷新页面",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefreshPages,
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
    onOpenUser: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "页面",
            supportingText = page.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item { PageStatusRow(text = "正在加载页面...", loading = true) }
            }
            errorMessage?.let { message ->
                item { PageStatusRow(text = message) }
            }
            item {
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
            items(page.blocks, key = { it.id }) { block ->
                PageBlockRow(block = block)
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

private fun fakePages(): List<Page> {
    return listOf(
        Page(
            id = "page-guide",
            title = "HHHL 指南",
            name = "guide",
            summary = "站内使用说明",
            author = User("me", "HHHL", "me", "H"),
            userId = "me",
            blocks = listOf(PageBlock("block-1", "text", "欢迎来到 HHHL")),
            likedCount = 4,
            isLiked = false,
        ),
    )
}
