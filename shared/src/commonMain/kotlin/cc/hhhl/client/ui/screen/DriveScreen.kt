package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileTypeFilter
import cc.hhhl.client.model.DriveFolder
import cc.hhhl.client.state.DriveFileSelectionItemSpec
import cc.hhhl.client.state.DriveFilesUiState
import cc.hhhl.client.state.toDriveSelectionItemSpec
import cc.hhhl.client.state.toReadableDriveFileSize
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.DriveFilePreview
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlOverflowMenu
import cc.hhhl.client.ui.component.HhhlOverflowMenuAction
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.MediaPreviewSession
import cc.hhhl.client.ui.component.driveFileMediaPreviewSession

@Composable
fun DriveScreen(
    state: DriveFilesUiState,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onLoadMoreFolders: () -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSortSelected: (DriveFileSort) -> Unit = {},
    onTypeFilterSelected: (DriveFileTypeFilter) -> Unit = {},
    onUpload: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onOpenFile: (DriveFile) -> Unit = {},
    onSelectFile: (DriveFile) -> Unit = {},
    onCloseFileDetails: () -> Unit = {},
    onOpenMediaPreview: ((MediaPreviewSession) -> Unit)? = null,
    onOpenFolder: (DriveFolder) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onNavigateToPathIndex: (Int) -> Unit = {},
    onRenameFile: (DriveFile, String) -> Unit = { _, _ -> },
    onToggleFileSensitive: (DriveFile) -> Unit = {},
    onDeleteFile: (DriveFile) -> Unit = {},
    onRenameFolder: (DriveFolder, String) -> Unit = { _, _ -> },
    onDeleteFolder: (DriveFolder) -> Unit = {},
    isMediaPickerAvailable: Boolean = false,
) {
    var editingFileId by remember { mutableStateOf<String?>(null) }
    var editingFolderId by remember { mutableStateOf<String?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var confirmDeleteFileId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFolderId by remember { mutableStateOf<String?>(null) }
    val actionsEnabled = !state.isManaging
    val visibleFiles = remember(state.files, state.typeFilter) { state.visibleFiles }

    fun clearInlineActionState() {
        editingFileId = null
        editingFolderId = null
        creatingFolder = false
        nameDraft = ""
        confirmDeleteFileId = null
        confirmDeleteFolderId = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "Drive",
            supportingText = state.folderPath.lastOrNull()?.name ?: "根目录",
            navigation = { HhhlBackButton(onClick = onBack) },
        )
        HhhlDivider()
        DriveHeaderTools(
            state = state,
            actionsEnabled = actionsEnabled,
            isMediaPickerAvailable = isMediaPickerAvailable,
            onCreateFolder = {
                clearInlineActionState()
                creatingFolder = true
                nameDraft = ""
            },
            onUpload = {
                clearInlineActionState()
                onUpload()
            },
            onRefresh = onRefresh,
            onNavigateUp = onNavigateUp,
            onQueryChanged = onQueryChanged,
            onSearch = onSearch,
            onSortSelected = onSortSelected,
            onTypeFilterSelected = onTypeFilterSelected,
        )
        if (state.folderPath.isNotEmpty()) {
            HhhlDivider()
            DrivePathRow(
                path = state.folderPath,
                actionsEnabled = actionsEnabled,
                onNavigateToPathIndex = onNavigateToPathIndex,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.isManaging) {
                item { DriveStatusRow(text = "正在处理 Drive 操作...", loading = true) }
            }
            if (state.isLoading && state.files.isEmpty() && state.folders.isEmpty()) {
                item { DriveStatusRow(text = "正在加载 Drive...", loading = true) }
            }
            state.errorMessage?.let { message ->
                item {
                    DriveStatusRow(
                        text = message,
                        actionText = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            if (
                !state.isLoading &&
                visibleFiles.isEmpty() &&
                state.folders.isEmpty() &&
                state.errorMessage == null &&
                !creatingFolder
            ) {
                item { DriveStatusRow(text = if (state.files.isEmpty()) "Drive 里还没有文件" else "没有匹配此类型的文件") }
            }
            state.selectedFile?.let { file ->
                item {
                    DriveFileDetailsRow(
                        file = file,
                        onClose = onCloseFileDetails,
                    )
                }
            }
            if (creatingFolder) {
                item {
                    DriveNewFolderRow(
                        draftName = nameDraft,
                        actionsEnabled = actionsEnabled,
                        onDraftChanged = { nameDraft = it },
                        onCancel = {
                            creatingFolder = false
                            nameDraft = ""
                        },
                        onSave = {
                            val cleanName = nameDraft.trim()
                            if (cleanName.isNotEmpty()) {
                                onCreateFolder(cleanName)
                                creatingFolder = false
                                nameDraft = ""
                            }
                        },
                    )
                }
            }
            items(state.folders, key = { "folder-${it.id}" }) { folder ->
                DriveFolderRow(
                    folder = folder,
                    canOpen = actionsEnabled && editingFolderId != folder.id,
                    isEditing = editingFolderId == folder.id,
                    draftName = if (editingFolderId == folder.id) nameDraft else folder.name,
                    actionsEnabled = actionsEnabled,
                    confirmingDelete = confirmDeleteFolderId == folder.id,
                    onDraftChanged = { nameDraft = it },
                    onClick = {
                        clearInlineActionState()
                        onOpenFolder(folder)
                    },
                    onStartEdit = {
                        editingFolderId = folder.id
                        editingFileId = null
                        nameDraft = folder.name
                        confirmDeleteFileId = null
                        confirmDeleteFolderId = null
                    },
                    onCancelEdit = {
                        editingFolderId = null
                        nameDraft = ""
                    },
                    onSaveEdit = {
                        val cleanName = nameDraft.trim()
                        if (cleanName.isNotEmpty()) {
                            onRenameFolder(folder, cleanName)
                            editingFolderId = null
                            nameDraft = ""
                        }
                    },
                    onDelete = {
                        if (confirmDeleteFolderId == folder.id) {
                            onDeleteFolder(folder)
                            confirmDeleteFolderId = null
                        } else {
                            confirmDeleteFolderId = folder.id
                            confirmDeleteFileId = null
                        }
                    },
                )
            }
            if (state.folders.isNotEmpty() && !state.foldersEndReached) {
                item {
                    DriveLoadMoreEffect(
                        enabled = !state.isLoadingMoreFolders,
                        onLoadMore = onLoadMoreFolders,
                    )
                    DriveStatusRow(
                        text = if (state.isLoadingMoreFolders) "正在加载更多文件夹..." else "加载更多文件夹",
                        loading = state.isLoadingMoreFolders,
                        onAction = if (state.isLoadingMoreFolders) null else onLoadMoreFolders,
                    )
                }
            }
            items(visibleFiles, key = { it.id }) { file ->
                DriveFileRow(
                    file = file,
                    canOpen = !file.url.isNullOrBlank() && editingFileId != file.id,
                    isEditing = editingFileId == file.id,
                    draftName = if (editingFileId == file.id) nameDraft else file.name,
                    actionsEnabled = actionsEnabled,
                    confirmingDelete = confirmDeleteFileId == file.id,
                    onDraftChanged = { nameDraft = it },
                    onClick = {
                        clearInlineActionState()
                        val session = driveFileMediaPreviewSession(
                            files = visibleFiles,
                            selectedId = file.id,
                        )
                        if (session.items.isNotEmpty() && onOpenMediaPreview != null) {
                            onOpenMediaPreview(session)
                        } else {
                            onOpenFile(file)
                        }
                    },
                    onDetails = { onSelectFile(file) },
                    onStartEdit = {
                        editingFileId = file.id
                        editingFolderId = null
                        nameDraft = file.name
                        confirmDeleteFileId = null
                        confirmDeleteFolderId = null
                    },
                    onCancelEdit = {
                        editingFileId = null
                        nameDraft = ""
                    },
                    onSaveEdit = {
                        val cleanName = nameDraft.trim()
                        if (cleanName.isNotEmpty()) {
                            onRenameFile(file, cleanName)
                            editingFileId = null
                            nameDraft = ""
                        }
                    },
                    onToggleSensitive = { onToggleFileSensitive(file) },
                    onDelete = {
                        if (confirmDeleteFileId == file.id) {
                            onDeleteFile(file)
                            confirmDeleteFileId = null
                        } else {
                            confirmDeleteFileId = file.id
                            confirmDeleteFolderId = null
                        }
                    },
                )
            }
            if (state.files.isNotEmpty() && !state.endReached) {
                item {
                    DriveLoadMoreEffect(
                        enabled = !state.isLoadingMore,
                        onLoadMore = onLoadMore,
                    )
                    DriveStatusRow(
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
private fun DriveHeaderTools(
    state: DriveFilesUiState,
    actionsEnabled: Boolean,
    isMediaPickerAvailable: Boolean,
    onCreateFolder: () -> Unit,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSortSelected: (DriveFileSort) -> Unit,
    onTypeFilterSelected: (DriveFileTypeFilter) -> Unit,
) {
    val folderText = state.folderPath.lastOrNull()?.name ?: "根目录"
    val visibleCount = remember(state.files, state.typeFilter) { state.visibleFiles.size }
    val countText = "${state.folders.size} 文件夹 · $visibleCount/${state.files.size} 文件"
    val stateText = when {
        state.isUploading -> "上传中"
        state.isManaging -> "处理中"
        state.isLoading -> "加载中"
        !isMediaPickerAvailable -> "上传不可用"
        state.searchQuery.isNotBlank() -> "搜索：${state.searchQuery.trim()}"
        state.typeFilter != DriveFileTypeFilter.All -> "类型：${state.typeFilter.label}"
        else -> state.sort.label
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$folderText · $countText · $stateText",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DrivePrimaryAction(
                label = if (state.isUploading) "上传中" else "上传文件",
                icon = Icons.Filled.Add,
                emphasized = true,
                enabled = actionsEnabled && isMediaPickerAvailable && !state.isUploading,
                onClick = onUpload,
            )
            DrivePrimaryAction(
                label = "新建",
                icon = Icons.Filled.Add,
                enabled = actionsEnabled,
                onClick = onCreateFolder,
            )
            HhhlOverflowMenu(
                actions = driveSummaryActions(
                    inFolder = state.folderPath.isNotEmpty(),
                    isLoading = state.isLoading,
                    actionsEnabled = actionsEnabled,
                    onRefresh = onRefresh,
                    onNavigateUp = onNavigateUp,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhhlTextInput(
                value = state.searchQuery,
                onValueChange = onQueryChanged,
                placeholder = "搜索文件或文件夹",
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            HhhlOverflowMenu(
                actions = driveControlActions(
                    selectedSort = state.sort,
                    selectedTypeFilter = state.typeFilter,
                    onSortSelected = onSortSelected,
                    onTypeFilterSelected = onTypeFilterSelected,
                ),
                label = "筛选与排序",
            )
        }
    }
}

fun driveSummaryActions(
    inFolder: Boolean,
    isLoading: Boolean,
    actionsEnabled: Boolean,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
): List<HhhlOverflowMenuAction> = buildList {
    add(
        HhhlOverflowMenuAction(
            label = if (isLoading) "同步中" else "刷新",
            enabled = !isLoading,
            onClick = onRefresh,
        ),
    )
    if (inFolder) {
        add(
            HhhlOverflowMenuAction(
                label = "返回上级",
                enabled = actionsEnabled,
                onClick = onNavigateUp,
            ),
        )
    }
}

@Composable
private fun DrivePathRow(
    path: List<DriveFolder>,
    actionsEnabled: Boolean,
    onNavigateToPathIndex: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "当前位置",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DriveBreadcrumbText(
                text = "根目录",
                selected = path.isEmpty(),
                enabled = actionsEnabled && path.isNotEmpty(),
                onClick = { onNavigateToPathIndex(-1) },
            )
            path.forEachIndexed { index, folder ->
                Text(
                    text = "/",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                DriveBreadcrumbText(
                    text = folder.name.ifBlank { "未命名文件夹" },
                    selected = index == path.lastIndex,
                    enabled = actionsEnabled && index != path.lastIndex,
                    onClick = { onNavigateToPathIndex(index) },
                )
            }
        }
    }
}

fun driveSortOptions(): List<DriveFileSort> = DriveFileSort.entries

fun driveTypeFilterOptions(): List<DriveFileTypeFilter> = DriveFileTypeFilter.entries

fun driveControlActions(
    selectedSort: DriveFileSort,
    selectedTypeFilter: DriveFileTypeFilter,
    onSortSelected: (DriveFileSort) -> Unit,
    onTypeFilterSelected: (DriveFileTypeFilter) -> Unit,
): List<HhhlOverflowMenuAction> {
    return buildList {
        driveSortOptions().forEach { sort ->
            add(
                HhhlOverflowMenuAction(
                    label = if (sort == selectedSort) "排序：${sort.label}" else "排序改为 ${sort.label}",
                    enabled = sort != selectedSort,
                    onClick = { onSortSelected(sort) },
                ),
            )
        }
        driveTypeFilterOptions().forEach { filter ->
            add(
                HhhlOverflowMenuAction(
                    label = if (filter == selectedTypeFilter) "类型：${filter.label}" else "只看${filter.label}",
                    enabled = filter != selectedTypeFilter,
                    onClick = { onTypeFilterSelected(filter) },
                ),
            )
        }
    }
}

fun driveFolderRowActions(
    actionsEnabled: Boolean,
    confirmingDelete: Boolean,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "改名",
        enabled = actionsEnabled,
        onClick = onStartEdit,
    ),
    HhhlOverflowMenuAction(
        label = if (confirmingDelete) "确认删除" else "删除",
        enabled = actionsEnabled,
        destructive = true,
        onClick = onDelete,
    ),
)

fun driveFileRowActions(
    isSensitive: Boolean,
    actionsEnabled: Boolean,
    confirmingDelete: Boolean,
    onDetails: () -> Unit,
    onStartEdit: () -> Unit,
    onToggleSensitive: () -> Unit,
    onDelete: () -> Unit,
): List<HhhlOverflowMenuAction> = listOf(
    HhhlOverflowMenuAction(
        label = "详情",
        enabled = actionsEnabled,
        onClick = onDetails,
    ),
    HhhlOverflowMenuAction(
        label = "改名",
        enabled = actionsEnabled,
        onClick = onStartEdit,
    ),
    HhhlOverflowMenuAction(
        label = if (isSensitive) "取消敏感" else "敏感",
        enabled = actionsEnabled,
        onClick = onToggleSensitive,
    ),
    HhhlOverflowMenuAction(
        label = if (confirmingDelete) "确认删除" else "删除",
        enabled = actionsEnabled,
        destructive = true,
        onClick = onDelete,
    ),
)

@Composable
private fun DriveFolderRow(
    folder: DriveFolder,
    canOpen: Boolean,
    isEditing: Boolean,
    draftName: String,
    actionsEnabled: Boolean,
    confirmingDelete: Boolean,
    onDraftChanged: (String) -> Unit,
    onClick: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.42f))
            .clickable(enabled = canOpen) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DriveFolderGlyph()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (isEditing) {
                DriveInlineNameEditor(
                    value = draftName,
                    placeholder = "文件夹名称",
                    onValueChanged = onDraftChanged,
                    onSave = onSaveEdit,
                    onCancel = onCancelEdit,
                    enabled = actionsEnabled,
                )
            } else {
                Text(
                    text = folder.name.ifBlank { "未命名文件夹" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${folder.foldersCount} 文件夹 · ${folder.filesCount} 文件 · ${folder.createdAtLabel}",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isEditing) {
            HhhlOverflowMenu(
                enabled = actionsEnabled,
                actions = driveFolderRowActions(
                    actionsEnabled = actionsEnabled,
                    confirmingDelete = confirmingDelete,
                    onStartEdit = onStartEdit,
                    onDelete = onDelete,
                ),
            )
        }
    }
}

@Composable
private fun DriveNewFolderRow(
    draftName: String,
    actionsEnabled: Boolean,
    onDraftChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DriveFolderGlyph()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "新建文件夹",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DriveInlineNameEditor(
                value = draftName,
                placeholder = "文件夹名称",
                onValueChanged = onDraftChanged,
                onSave = onSave,
                onCancel = onCancel,
                enabled = actionsEnabled,
            )
        }
    }
}

@Composable
private fun DriveFileRow(
    file: DriveFile,
    canOpen: Boolean,
    isEditing: Boolean,
    draftName: String,
    actionsEnabled: Boolean,
    confirmingDelete: Boolean,
    onDraftChanged: (String) -> Unit,
    onClick: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onToggleSensitive: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val selectionSpec = remember(file) { file.toDriveSelectionItemSpec() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.34f))
            .clickable(enabled = canOpen) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DriveFilePreview(
            file = file,
            onOpenUrl = { onClick() },
            modifier = Modifier.size(46.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (isEditing) {
                DriveInlineNameEditor(
                    value = draftName,
                    placeholder = "文件名",
                    onValueChanged = onDraftChanged,
                    onSave = onSaveEdit,
                    onCancel = onCancelEdit,
                    enabled = actionsEnabled,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectionSpec.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (file.isSensitive) {
                        Text(
                            text = "敏感",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                text = buildFileMeta(selectionSpec),
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            file.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Text(
                    text = comment,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!isEditing) {
            HhhlOverflowMenu(
                enabled = actionsEnabled,
                actions = driveFileRowActions(
                    isSensitive = file.isSensitive,
                    actionsEnabled = actionsEnabled,
                    confirmingDelete = confirmingDelete,
                    onDetails = onDetails,
                    onStartEdit = onStartEdit,
                    onToggleSensitive = onToggleSensitive,
                    onDelete = onDelete,
                ),
            )
        }
    }
}

@Composable
private fun DriveBreadcrumbText(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onBackground
        else -> colors.subtleText
    }
    Text(
        text = text,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else colors.inputBackground.copy(alpha = 0.42f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun DriveFileDetailsRow(
    file: DriveFile,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.52f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "文件详情",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            HhhlActionChip(
                label = "关闭",
                onClick = onClose,
            )
        }
        DriveDetailLine(label = "名称", value = file.name.ifBlank { "未命名文件" })
        DriveDetailLine(label = "类型", value = file.type.ifBlank { "未知类型" })
        DriveDetailLine(label = "大小", value = file.size.toReadableDriveFileSize())
        DriveDetailLine(label = "创建", value = file.createdAtLabel.ifBlank { "未知" })
        DriveDetailLine(label = "文件 ID", value = file.id)
        file.url?.takeIf { it.isNotBlank() }?.let { DriveDetailLine(label = "链接", value = it) }
        file.comment?.takeIf { it.isNotBlank() }?.let { DriveDetailLine(label = "备注", value = it) }
    }
}

@Composable
private fun DriveDetailLine(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.size(width = 56.dp, height = 18.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrivePrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val containerColor = when {
        !enabled -> colors.inputBackground.copy(alpha = 0.50f)
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        else -> colors.inputBackground.copy(alpha = 0.72f)
    }
    val contentColor = when {
        !enabled -> colors.subtleText
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DriveFolderGlyph() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DIR",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DriveInlineNameEditor(
    value: String,
    placeholder: String,
    onValueChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HhhlTextInput(
            value = value,
            onValueChange = onValueChanged,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            HhhlActionChip(
                label = "保存",
                emphasized = true,
                enabled = enabled && value.trim().isNotEmpty(),
                onClick = onSave,
            )
            HhhlActionChip(
                label = "取消",
                enabled = enabled,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun DriveStatusRow(
    text: String,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.48f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            style = MaterialTheme.typography.bodySmall,
            modifier = if (onAction != null) Modifier.clickable { onAction() } else Modifier,
        )
        if (actionText != null) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DriveLoadMoreEffect(
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(enabled) {
        if (enabled) onLoadMore()
    }
}

private fun buildFileMeta(file: DriveFileSelectionItemSpec): String {
    val date = file.createdAtLabel.takeIf { it.isNotBlank() }
    return listOfNotNull(file.type, file.sizeLabel, date, file.disabledReason).joinToString(" · ")
}
