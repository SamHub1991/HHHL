package cc.hhhl.client.state

import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileTypeFilter
import cc.hhhl.client.model.DriveFolder
import cc.hhhl.client.model.matchesTypeFilter
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveFilesRepositoryResult
import cc.hhhl.client.repository.DriveFoldersRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriveFilesUiState(
    val files: List<DriveFile> = emptyList(),
    val folders: List<DriveFolder> = emptyList(),
    val folderId: String? = null,
    val folderPath: List<DriveFolder> = emptyList(),
    val sort: DriveFileSort = DriveFileSort.CreatedDescending,
    val typeFilter: DriveFileTypeFilter = DriveFileTypeFilter.All,
    val searchQuery: String = "",
    val selectedFile: DriveFile? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingMoreFolders: Boolean = false,
    val isUploading: Boolean = false,
    val isManaging: Boolean = false,
    val endReached: Boolean = false,
    val foldersEndReached: Boolean = false,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
) {
    val visibleFiles: List<DriveFile>
        get() = files.filter { it.matchesTypeFilter(typeFilter) }
}

data class DriveFileSelectionItemSpec(
    val id: String,
    val name: String,
    val type: String,
    val sizeLabel: String,
    val createdAtLabel: String,
    val url: String?,
    val thumbnailUrl: String?,
    val isSensitive: Boolean,
    val canPreviewInline: Boolean,
    val canSelect: Boolean,
    val disabledReason: String?,
)

fun DriveFilesUiState.visibleSelectionItems(): List<DriveFileSelectionItemSpec> {
    return visibleFiles.map { it.toDriveSelectionItemSpec() }
}

fun DriveFile.toDriveSelectionItemSpec(): DriveFileSelectionItemSpec {
    val hasUrl = !url.isNullOrBlank()
    return DriveFileSelectionItemSpec(
        id = id,
        name = name.ifBlank { "未命名文件" },
        type = type.ifBlank { "文件" },
        sizeLabel = size.toReadableDriveFileSize(),
        createdAtLabel = createdAtLabel,
        url = url?.takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
        isSensitive = isSensitive,
        canPreviewInline = !isSensitive && type.startsWith("image/"),
        canSelect = hasUrl,
        disabledReason = if (hasUrl) null else "文件链接不可用",
    )
}

fun Long.toReadableDriveFileSize(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return "${kb.roundOne()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${mb.roundOne()} MB"
    return "${(mb / 1024.0).roundOne()} GB"
}

class DriveFilesStateHolder(
    private val repository: DriveFileRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(DriveFilesUiState())
    val state: StateFlow<DriveFilesUiState> = mutableState
    private var refreshRequestId = 0

    fun refresh() {
        val current = state.value
        val requestId = ++refreshRequestId

        mutableState.update {
            it.copy(
                isLoading = true,
                endReached = false,
                foldersEndReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (
                val foldersResult = repository.refreshFolders(
                    folderId = current.folderId,
                    searchQuery = current.searchQuery.trim(),
                )
            ) {
                is DriveFoldersRepositoryResult.Success -> {
                    if (requestId != refreshRequestId) return@launch
                    applyFoldersResult(foldersResult, loadingMore = false)
                    val filesResult = repository.refreshFiles(
                        folderId = current.folderId,
                        sort = current.sort,
                        searchQuery = current.searchQuery.trim(),
                    )
                    if (requestId == refreshRequestId) {
                        applyFilesResult(
                            result = filesResult,
                            loadingMore = false,
                        )
                    }
                }
                DriveFoldersRepositoryResult.Unauthorized -> {
                    if (requestId == refreshRequestId) {
                        applyFoldersResult(foldersResult, loadingMore = false)
                    }
                }
                is DriveFoldersRepositoryResult.Error -> {
                    if (requestId == refreshRequestId) {
                        applyFoldersResult(foldersResult, loadingMore = false)
                    }
                }
            }
        }
    }

    fun loadMoreFolders() {
        val current = state.value
        if (
            current.isLoading ||
            current.isLoadingMoreFolders ||
            current.foldersEndReached ||
            current.folders.isEmpty()
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMoreFolders = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyFoldersResult(
                result = repository.loadMoreFolders(
                    currentFolders = current.folders,
                    folderId = current.folderId,
                    searchQuery = current.searchQuery.trim(),
                ),
                loadingMore = true,
            )
        }
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoading ||
            current.isLoadingMore ||
            current.endReached ||
            current.files.isEmpty()
        ) {
            return
        }

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyFilesResult(
                result = repository.loadMoreFiles(
                    currentFiles = current.files,
                    folderId = current.folderId,
                    sort = current.sort,
                    searchQuery = current.searchQuery.trim(),
                ),
                loadingMore = true,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        mutableState.update {
            it.copy(
                searchQuery = query,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun search() {
        refresh()
    }

    fun selectSort(sort: DriveFileSort) {
        mutableState.update {
            it.copy(
                sort = sort,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun selectTypeFilter(typeFilter: DriveFileTypeFilter) {
        mutableState.update {
            it.copy(
                typeFilter = typeFilter,
                selectedFile = it.selectedFile?.takeIf { file -> file.matchesTypeFilter(typeFilter) },
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun selectFile(file: DriveFile) {
        mutableState.update {
            it.copy(
                selectedFile = file,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun clearSelectedFile() {
        mutableState.update { it.copy(selectedFile = null) }
    }

    fun openFolder(folder: DriveFolder) {
        mutableState.update {
            it.copy(
                folderId = folder.id,
                folderPath = it.folderPath + folder,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                endReached = false,
                foldersEndReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun navigateUp() {
        val current = state.value
        if (current.folderPath.isEmpty()) return
        val nextPath = current.folderPath.dropLast(1)
        val nextFolderId = nextPath.lastOrNull()?.id
        mutableState.update {
            it.copy(
                folderId = nextFolderId,
                folderPath = nextPath,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                endReached = false,
                foldersEndReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun navigateToPathIndex(index: Int) {
        val current = state.value
        val nextPath = when {
            index < 0 -> emptyList()
            index >= current.folderPath.lastIndex -> return
            else -> current.folderPath.take(index + 1)
        }
        val nextFolderId = nextPath.lastOrNull()?.id
        mutableState.update {
            it.copy(
                folderId = nextFolderId,
                folderPath = nextPath,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                endReached = false,
                foldersEndReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun upload(upload: DriveFileUpload) {
        if (state.value.isUploading) return
        val folderId = state.value.folderId

        mutableState.update {
            it.copy(isUploading = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val scopedUpload = if (upload.folderId == null && folderId != null) {
                upload.copy(folderId = folderId)
            } else {
                upload
            }
            when (val result = repository.upload(scopedUpload)) {
                is DriveFileRepositoryResult.Success -> mutableState.update {
                    it.copy(
                        files = (listOf(result.file) + it.files).distinctBy { file -> file.id },
                        isUploading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = result.message.toFriendlyDriveErrorMessage(),
                        requiresRelogin = false,
                    )
                }
                is DriveFileRepositoryResult.Error -> mutableState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = result.message.toFriendlyDriveErrorMessage(),
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun updateFile(
        file: DriveFile,
        name: String = file.name,
        comment: String? = file.comment,
        isSensitive: Boolean = file.isSensitive,
    ) {
        launchManagement {
            repository.updateFile(
                fileId = file.id,
                name = name,
                comment = comment,
                isSensitive = isSensitive,
                folderId = file.folderId,
            )
        }
    }

    fun deleteFile(fileId: String) {
        launchManagement {
            repository.deleteFile(fileId)
        }
    }

    fun createFolder(name: String) {
        val parentId = state.value.folderId
        launchManagement {
            repository.createFolder(name = name, parentId = parentId)
        }
    }

    fun updateFolder(
        folder: DriveFolder,
        name: String = folder.name,
    ) {
        launchManagement {
            repository.updateFolder(
                folderId = folder.id,
                name = name,
                parentId = folder.parentId,
            )
        }
    }

    fun deleteFolder(folderId: String) {
        launchManagement {
            repository.deleteFolder(folderId)
        }
    }

    fun reportUploadError(message: String) {
        val cleanMessage = message.trim().takeIf { it.isNotEmpty() } ?: "无法读取所选文件"
        mutableState.update {
            it.copy(
                isUploading = false,
                errorMessage = cleanMessage.toFriendlyDriveErrorMessage(),
                requiresRelogin = false,
            )
        }
    }

    private fun launchManagement(
        action: suspend () -> DriveManagementRepositoryResult,
    ) {
        if (state.value.isManaging) return

        mutableState.update {
            it.copy(isManaging = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyManagementResult(action())
        }
    }

    private fun applyManagementResult(result: DriveManagementRepositoryResult) {
        when (result) {
            is DriveManagementRepositoryResult.FileUpdated -> mutableState.update {
                it.copy(
                    files = it.files.map { file ->
                        if (file.id == result.file.id) result.file else file
                    },
                    selectedFile = it.selectedFile?.let { file ->
                        if (file.id == result.file.id) result.file else file
                    },
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FileDeleted -> mutableState.update {
                it.copy(
                    files = it.files.filterNot { file -> file.id == result.fileId },
                    selectedFile = it.selectedFile?.takeIf { file -> file.id != result.fileId },
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FolderCreated -> mutableState.update {
                it.copy(
                    folders = (listOf(result.folder) + it.folders).distinctBy { folder -> folder.id },
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FolderUpdated -> mutableState.update {
                it.copy(
                    folders = it.folders.map { folder ->
                        if (folder.id == result.folder.id) result.folder else folder
                    },
                    folderPath = it.folderPath.map { folder ->
                        if (folder.id == result.folder.id) result.folder else folder
                    },
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FolderDeleted -> mutableState.update {
                val nextPath = it.folderPath.filterNot { folder -> folder.id == result.folderId }
                it.copy(
                    folders = it.folders.filterNot { folder -> folder.id == result.folderId },
                    folderPath = nextPath,
                    folderId = if (it.folderId == result.folderId) nextPath.lastOrNull()?.id else it.folderId,
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            DriveManagementRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isManaging = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DriveManagementRepositoryResult.ValidationError -> mutableState.update {
                it.copy(
                    isManaging = false,
                    errorMessage = result.message.toFriendlyDriveErrorMessage(),
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isManaging = false,
                    errorMessage = result.message.toFriendlyDriveErrorMessage(),
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyFilesResult(
        result: DriveFilesRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is DriveFilesRepositoryResult.Success -> mutableState.update {
                it.copy(
                    files = result.files,
                    isLoading = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            DriveFilesRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DriveFilesRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMore = false,
                    errorMessage = result.message.toFriendlyDriveErrorMessage(),
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyFoldersResult(
        result: DriveFoldersRepositoryResult,
        loadingMore: Boolean,
    ) {
        when (result) {
            is DriveFoldersRepositoryResult.Success -> mutableState.update {
                it.copy(
                    folders = result.folders,
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMoreFolders = false,
                    foldersEndReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            DriveFoldersRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMoreFolders = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is DriveFoldersRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = if (loadingMore) it.isLoading else false,
                    isLoadingMoreFolders = false,
                    errorMessage = result.message.toFriendlyDriveErrorMessage(),
                    requiresRelogin = false,
                )
            }
        }
    }
}

private fun String.toFriendlyDriveErrorMessage(): String {
    val clean = trim()
    if (clean.isEmpty()) return "Drive 操作失败，请稍后重试"

    return when {
        clean.contains("/api/drive/", ignoreCase = true) ||
            clean.contains("drive/files", ignoreCase = true) ||
            clean.contains("drive/folders", ignoreCase = true) ||
            clean.contains("endpoint", ignoreCase = true) -> {
            "Drive 接口暂时不可用，请稍后重试"
        }
        clean.contains("timeout", ignoreCase = true) -> "Drive 请求超时，请检查网络后重试"
        clean.contains("connection", ignoreCase = true) -> "无法连接服务器，请检查网络后重试"
        else -> clean
    }
}

private fun Double.roundOne(): String {
    val rounded = kotlin.math.round(this * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
