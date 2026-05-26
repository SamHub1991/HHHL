package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.User
import cc.hhhl.client.state.GalleryUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession

@Composable
fun GalleryScreen(
    state: GalleryUiState? = null,
    onBack: () -> Unit,
    onRefreshPosts: () -> Unit = {},
    onKindSelected: (GalleryListKind) -> Unit = {},
    onOpenPost: (String) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    onToggleLikePost: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
) {
    val posts = state?.posts ?: fakeGalleryPosts()
    val selectedPost = state?.selectedPost
    val listState = rememberLazyListState()

    AutoLoadMoreEffect(
        listState = listState,
        itemCount = posts.size,
        isLoadingMore = state?.isLoadingMore == true || state?.endReached == true,
        onLoadMore = onLoadMore,
    )

    if (selectedPost != null) {
        GalleryDetailView(
            post = selectedPost,
            isLoading = state.isLoadingDetail,
            isChangingLike = state.isChangingLike,
            errorMessage = state.detailErrorMessage,
            onBack = onCloseDetail,
            onToggleLikePost = onToggleLikePost,
            onOpenUser = onOpenUser,
            onOpenMedia = onOpenMedia,
            onOpenMediaPreview = onOpenMediaPreview,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = galleryMenuActions(
            selectedKind = state?.selectedKind ?: GalleryListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "图库",
            supportingText = (state?.selectedKind ?: GalleryListKind.Featured).label,
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
        GallerySummaryRow(
            selectedKind = state?.selectedKind ?: GalleryListKind.Featured,
            postCount = posts.size,
            isLoading = state?.isLoadingPosts == true,
            onRefreshPosts = onRefreshPosts,
        )
        HhhlDivider()
        GalleryKindFilterRow(
            selectedKind = state?.selectedKind ?: GalleryListKind.Featured,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item {
                    GalleryStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshPosts,
                    )
                }
            }
            if (state?.isLoadingPosts == true && posts.isEmpty()) {
                item { GalleryStatusRow(text = "正在加载图库...", loading = true) }
            }
            if (state != null && !state.isLoadingPosts && posts.isEmpty() && state.errorMessage == null) {
                item { GalleryStatusRow(text = "还没有图库作品") }
            }
            items(posts, key = { it.id }) { post ->
                GalleryPostRow(
                    post = post,
                    onOpenPost = onOpenPost,
                    onOpenUser = onOpenUser,
                    onOpenMedia = onOpenMedia,
                    onOpenMediaPreview = onOpenMediaPreview,
                )
            }
            if (state != null && posts.isNotEmpty() && !state.endReached) {
                item {
                    GalleryStatusRow(
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
private fun GallerySummaryRow(
    selectedKind: GalleryListKind,
    postCount: Int,
    isLoading: Boolean,
    onRefreshPosts: () -> Unit,
) {
    val stateText = if (isLoading) "加载中" else "${postCount} 项"
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
                label = if (isLoading) "同步作品中" else "刷新作品",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefreshPosts,
            )
        }
    }
}

fun galleryPrimaryKinds(): List<GalleryListKind> = listOf(
    GalleryListKind.Featured,
    GalleryListKind.Popular,
    GalleryListKind.Recent,
)

fun galleryOverflowKinds(): List<GalleryListKind> =
    GalleryListKind.entries - galleryPrimaryKinds().toSet()

private fun galleryMenuActions(
    selectedKind: GalleryListKind,
    onKindSelected: (GalleryListKind) -> Unit,
): List<HhhlOverflowMenuAction> {
    return galleryOverflowKinds().map { kind ->
        val prefix = if (kind == selectedKind) "当前" else "切换到"
        HhhlOverflowMenuAction(
            label = "$prefix ${kind.label}",
            enabled = kind != selectedKind,
            onClick = { onKindSelected(kind) },
        )
    }
}

@Composable
private fun GalleryPostRow(
    post: GalleryPost,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPost(post.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(
                initial = post.author.avatarInitial,
                avatarUrl = post.author.avatarUrl,
                modifier = Modifier.clickable { onOpenUser(post.author.id) },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = post.title.ifBlank { "未命名作品" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${post.author.username} · ${post.imageCountLabel} · ${post.likedCount} 喜欢",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (post.description.isNotBlank()) {
                    Text(
                        text = post.description,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        GalleryFileStrip(
            files = post.files,
            onOpenMedia = onOpenMedia,
            onOpenMediaPreview = onOpenMediaPreview,
        )
        GalleryTagRow(post = post)
    }
    HhhlDivider()
}

@Composable
private fun GalleryDetailView(
    post: GalleryPost,
    isLoading: Boolean,
    isChangingLike: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleLikePost: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "图库",
            supportingText = post.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item { GalleryStatusRow(text = "正在加载作品...", loading = true) }
            }
            errorMessage?.let { message ->
                item { GalleryStatusRow(text = message) }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = post.title.ifBlank { "未命名作品" },
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
                                .clickable { onOpenUser(post.author.id) },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                initial = post.author.avatarInitial,
                                avatarUrl = post.author.avatarUrl,
                            )
                            Column {
                                Text(
                                    text = post.author.displayName,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "@${post.author.username} · ${post.likedCount} 喜欢",
                                    color = LocalHhhlColors.current.subtleText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        HhhlActionChip(
                            label = when {
                                isChangingLike -> "处理中"
                                post.isLiked -> "已喜欢"
                                else -> "喜欢"
                            },
                            enabled = !isChangingLike,
                            emphasized = post.isLiked,
                            onClick = onToggleLikePost,
                        )
                    }
                    GalleryFileStrip(
                        files = post.files,
                        onOpenMedia = onOpenMedia,
                        onOpenMediaPreview = onOpenMediaPreview,
                    )
                    if (post.description.isNotBlank()) {
                        Text(
                            text = post.description,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    GalleryTagRow(post = post)
                }
                HhhlDivider()
            }
        }
    }
}

@Composable
private fun GalleryKindFilterRow(
    selectedKind: GalleryListKind,
    onKindSelected: (GalleryListKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        galleryPrimaryKinds().forEach { kind ->
            HhhlActionChip(
                label = kind.label,
                emphasized = kind == selectedKind,
                onClick = { onKindSelected(kind) },
            )
        }
        if (selectedKind !in galleryPrimaryKinds()) {
            HhhlActionChip(
                label = selectedKind.label,
                emphasized = true,
                onClick = { onKindSelected(selectedKind) },
            )
        }
    }
}

@Composable
private fun GalleryFileStrip(
    files: List<DriveFile>,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        files.take(3).forEach { file ->
            DriveFilePreview(
                file = file,
                onOpenUrl = { openUrl ->
                    val session = driveFileMediaPreviewSession(
                        files = files,
                        selectedId = file.id,
                    )
                    if (session.items.isNotEmpty() && onOpenMediaPreview != null) {
                        onOpenMediaPreview(session)
                    } else {
                        onOpenMedia(openUrl)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(104.dp),
            )
        }
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(104.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LocalHhhlColors.current.mediaBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无图片",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun GalleryTagRow(post: GalleryPost) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (post.isSensitive) {
            GalleryTag(text = "敏感内容")
        }
        post.tags.take(4).forEach { tag ->
            GalleryTag(text = "#$tag")
        }
    }
}

@Composable
private fun GalleryTag(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LocalHhhlColors.current.mediaBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun GalleryStatusRow(
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

private fun fakeGalleryPosts(): List<GalleryPost> {
    return listOf(
        GalleryPost(
            id = "gallery-featured",
            title = "HHHL 图库作品",
            description = "站内精选图片集合",
            author = User("me", "HHHL", "me", "H"),
            userId = "me",
            fileIds = listOf("file-1"),
            files = listOf(
                DriveFile(
                    id = "file-1",
                    name = "featured.webp",
                    type = "image/webp",
                    url = null,
                    thumbnailUrl = null,
                    comment = null,
                    size = 0L,
                    isSensitive = false,
                ),
            ),
            tags = listOf("HHHL"),
            isSensitive = false,
            likedCount = 4,
            isLiked = false,
        ),
    )
}
