@file:OptIn(ExperimentalLayoutApi::class)

package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cc.hhhl.client.ui.component.HhhlTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.GalleryPostDraft
import cc.hhhl.client.model.User
import cc.hhhl.client.state.GalleryUiState
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.AutoLoadMoreEffect
import cc.hhhl.client.ui.component.Avatar
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlStatusRow
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
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
    onCreatePost: (GalleryPostDraft) -> Unit = {},
    onUpdatePost: (GalleryPostDraft) -> Unit = {},
    onDeletePost: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    currentUserId: String? = null,
) {
    val posts = state?.posts.orEmpty()
    val selectedPost = state?.selectedPost
    val selectedKind = state?.selectedKind ?: GalleryListKind.Featured
    val listState = remember(selectedKind) { LazyListState() }
    var createDialogOpen by remember { mutableStateOf(false) }

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
            isMutatingPost = state.isMutatingPost,
            errorMessage = state.detailErrorMessage,
            onBack = onCloseDetail,
            onToggleLikePost = onToggleLikePost,
            onUpdatePost = onUpdatePost,
            onDeletePost = onDeletePost,
            onOpenUser = onOpenUser,
            onOpenMedia = onOpenMedia,
            onOpenMediaPreview = onOpenMediaPreview,
            currentUserId = currentUserId,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val overflowActions = galleryMenuActions(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlTopBar(
            title = "图库",
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
        GallerySummaryRow(
            selectedKind = selectedKind,
            postCount = posts.size,
            isLoading = state?.isLoadingPosts == true,
            isMutating = state?.isMutatingPost == true,
            canCreate = state != null && state.requiresRelogin.not(),
            onRefreshPosts = onRefreshPosts,
            onCreatePost = { createDialogOpen = true },
        )
        HhhlDivider()
        GalleryKindFilterRow(
            selectedKind = selectedKind,
            onKindSelected = onKindSelected,
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            state?.errorMessage?.let { message ->
                item(contentType = "gallery-status") {
                    GalleryStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefreshPosts,
                    )
                }
            }
            if (state?.isLoadingPosts == true && posts.isEmpty()) {
                item(contentType = "gallery-status") {
                    GalleryStatusRow(text = "正在加载图库...", loading = true)
                }
            }
            if (state != null && !state.isLoadingPosts && posts.isEmpty() && state.errorMessage == null) {
                item(contentType = "gallery-status") { GalleryStatusRow(text = "还没有图库作品") }
            }
            items(
                items = posts,
                key = { it.id },
                contentType = { "gallery-post" },
            ) { post ->
                GalleryPostRow(
                    post = post,
                    onOpenPost = onOpenPost,
                    onOpenUser = onOpenUser,
                    onOpenMedia = onOpenMedia,
                    onOpenMediaPreview = onOpenMediaPreview,
                )
            }
            if (state != null && posts.isNotEmpty() && state.isLoadingMore) {
                item(contentType = "gallery-status") {
                    GalleryStatusRow(
                        text = "正在加载更多...",
                        loading = state.isLoadingMore,
                    )
                }
            }
        }
    }

    if (createDialogOpen) {
        GalleryEditorDialog(
            title = "发布图库",
            isSaving = state?.isMutatingPost == true,
            onDismiss = { createDialogOpen = false },
            onSubmit = { draft ->
                onCreatePost(draft)
                createDialogOpen = false
            },
        )
    }
}

@Composable
private fun GallerySummaryRow(
    selectedKind: GalleryListKind,
    postCount: Int,
    isLoading: Boolean,
    isMutating: Boolean,
    canCreate: Boolean,
    onRefreshPosts: () -> Unit,
    onCreatePost: () -> Unit,
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
            HhhlIconActionButton(
                icon = Icons.Filled.Refresh,
                contentDescription = if (isLoading) "同步作品中" else "刷新作品",
                emphasized = true,
                enabled = !isLoading,
                onClick = onRefreshPosts,
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Add,
                contentDescription = if (isMutating) "发布中" else "发布作品",
                enabled = canCreate && !isMutating,
                onClick = onCreatePost,
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
    isMutatingPost: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleLikePost: () -> Unit,
    onUpdatePost: (GalleryPostDraft) -> Unit,
    onDeletePost: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)?,
    currentUserId: String?,
) {
    var editDialogOpen by remember(post.id) { mutableStateOf(false) }
    var deleteDialogOpen by remember(post.id) { mutableStateOf(false) }
    val canManagePost = !currentUserId.isNullOrBlank() && post.userId == currentUserId

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "图库",
            supportingText = post.author.displayName,
            navigation = { HhhlBackButton(onClick = onBack) },
            action = if (canManagePost) {
                {
                    HhhlOverflowMenu(
                        actions = listOf(
                            HhhlOverflowMenuAction(
                                label = "编辑作品",
                                enabled = !isMutatingPost,
                                onClick = { editDialogOpen = true },
                            ),
                            HhhlOverflowMenuAction(
                                label = "删除作品",
                                enabled = !isMutatingPost,
                                onClick = { deleteDialogOpen = true },
                            ),
                        ),
                    )
                }
            } else {
                null
            },
        )
        HhhlDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                item(contentType = "gallery-detail-status") {
                    GalleryStatusRow(text = "正在加载作品...", loading = true)
                }
            }
            errorMessage?.let { message ->
                item(contentType = "gallery-detail-status") { GalleryStatusRow(text = message) }
            }
            item(contentType = "gallery-detail") {
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

    if (editDialogOpen) {
        GalleryEditorDialog(
            title = "编辑图库",
            initialPost = post,
            isSaving = isMutatingPost,
            onDismiss = { editDialogOpen = false },
            onSubmit = { draft ->
                onUpdatePost(draft)
                editDialogOpen = false
            },
        )
    }
    if (deleteDialogOpen) {
        DeleteGalleryPostDialog(
            post = post,
            isDeleting = isMutatingPost,
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                onDeletePost()
                deleteDialogOpen = false
            },
        )
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
            key(file.id) {
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
    HhhlStatusRow(
        text = text,
        loading = loading,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
private fun GalleryEditorDialog(
    title: String,
    initialPost: GalleryPost? = null,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (GalleryPostDraft) -> Unit,
) {
    var postTitle by remember(initialPost?.id) { mutableStateOf(initialPost?.title.orEmpty()) }
    var description by remember(initialPost?.id) { mutableStateOf(initialPost?.description.orEmpty()) }
    var fileIdsText by remember(initialPost?.id) {
        mutableStateOf(initialPost?.fileIds?.joinToString("\n").orEmpty())
    }
    var isSensitive by remember(initialPost?.id) { mutableStateOf(initialPost?.isSensitive == true) }
    var isPublic by remember(initialPost?.id) { mutableStateOf(initialPost?.isPublic != false) }
    val fileIds = parseGalleryFileIds(fileIdsText)
    val canSubmit = !isSaving && postTitle.isNotBlank() && fileIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HhhlTextInput(
                    value = postTitle,
                    onValueChange = { postTitle = it },
                    label = "标题",
                    placeholder = "图库标题",
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述",
                    placeholder = "作品描述",
                    enabled = !isSaving,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                HhhlTextInput(
                    value = fileIdsText,
                    onValueChange = { fileIdsText = it },
                    label = "Drive 文件 ID",
                    placeholder = "每行一个文件 ID，或用逗号分隔",
                    enabled = !isSaving,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                GalleryDraftCheckboxRow(
                    checked = isSensitive,
                    enabled = !isSaving,
                    label = "敏感内容",
                    onCheckedChange = { isSensitive = it },
                )
                GalleryDraftCheckboxRow(
                    checked = isPublic,
                    enabled = !isSaving,
                    label = "公开作品",
                    onCheckedChange = { isPublic = it },
                )
            }
        },
        confirmButton = {
            HhhlTextButton(
                onClick = {
                    onSubmit(
                        GalleryPostDraft(
                            title = postTitle,
                            description = description,
                            fileIds = fileIds,
                            isSensitive = isSensitive,
                            isPublic = isPublic,
                        ),
                    )
                },
                enabled = canSubmit,
            ) {
                Text(if (isSaving) "处理中" else "保存")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun GalleryDraftCheckboxRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeleteGalleryPostDialog(
    post: GalleryPost,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除图库") },
        text = {
            Text(
                text = "删除「${post.title.ifBlank { "未命名作品" }}」后，图库列表会移除它，Drive 文件不会被删除。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            HhhlTextButton(onClick = onDelete, enabled = !isDeleting, destructive = true) {
                Text(if (isDeleting) "删除中" else "删除")
            }
        },
        dismissButton = {
            HhhlTextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("取消")
            }
        },
    )
}

private fun parseGalleryFileIds(text: String): List<String> {
    return text.split('\n', ',', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}
