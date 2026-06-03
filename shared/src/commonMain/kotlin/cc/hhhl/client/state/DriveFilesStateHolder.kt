package cc.hhhl.client.state

import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileDetails
import cc.hhhl.client.model.DriveFileTypeFilter
import cc.hhhl.client.model.DriveFolder
import cc.hhhl.client.model.matchesTypeFilter
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileDetailsRepositoryResult
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveFilesRepositoryResult
import cc.hhhl.client.repository.DriveFoldersRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import cc.hhhl.client.repository.prependDistinctBy
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
    val selectedFileDetails: DriveFileDetails? = null,
    val isStreamMode: Boolean = false,
    val isLoadingFileDetails: Boolean = false,
    val fileDetailsErrorMessage: String? = null,
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
    private var filesPageRequestId = 0
    private var foldersPageRequestId = 0
    private var uploadRequestId = 0
    private val fileDetailsCache = LinkedHashMap<String, DriveFileDetails>()

    fun refresh() {
        val current = state.value
        val requestId = ++refreshRequestId
        filesPageRequestId += 1
        foldersPageRequestId += 1

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
            if (current.isStreamMode) {
                val filesResult = repository.refreshStream(type = current.typeFilter.streamMimeType())
                if (requestId == refreshRequestId) {
                    applyFilesResult(
                        result = filesResult,
                        loadingMore = false,
                    )
                }
                return@launch
            }
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
        val requestId = ++foldersPageRequestId
        val folderId = current.folderId
        val searchQuery = current.searchQuery.trim()

        mutableState.update {
            it.copy(
                isLoadingMoreFolders = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = repository.loadMoreFolders(
                currentFolders = current.folders,
                folderId = folderId,
                searchQuery = searchQuery,
            )
            if (requestId == foldersPageRequestId && state.value.folderId == folderId) {
                applyFoldersResult(result = result, loadingMore = true)
            }
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
        val requestId = ++filesPageRequestId
        val folderId = current.folderId
        val sort = current.sort
        val searchQuery = current.searchQuery.trim()
        val isStreamMode = current.isStreamMode
        val type = current.typeFilter.streamMimeType()

        mutableState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            val result = if (isStreamMode) {
                repository.loadMoreStream(currentFiles = current.files, type = type)
            } else {
                repository.loadMoreFiles(
                    currentFiles = current.files,
                    folderId = folderId,
                    sort = sort,
                    searchQuery = searchQuery,
                )
            }
            val latest = state.value
            if (
                requestId == filesPageRequestId &&
                latest.folderId == folderId &&
                latest.isStreamMode == isStreamMode &&
                latest.sort == sort &&
                latest.searchQuery.trim() == searchQuery
            ) {
                applyFilesResult(result = result, loadingMore = true)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        filesPageRequestId += 1
        foldersPageRequestId += 1
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

    fun setStreamMode(enabled: Boolean) {
        if (state.value.isStreamMode == enabled) return
        filesPageRequestId += 1
        foldersPageRequestId += 1
        uploadRequestId += 1
        mutableState.update {
            it.copy(
                isStreamMode = enabled,
                folderId = if (enabled) null else it.folderId,
                folderPath = if (enabled) emptyList() else it.folderPath,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                selectedFileDetails = null,
                isLoadingFileDetails = false,
                fileDetailsErrorMessage = null,
                endReached = false,
                foldersEndReached = enabled,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun selectSort(sort: DriveFileSort) {
        filesPageRequestId += 1
        mutableState.update {
            it.copy(
                sort = sort,
                requiresRelogin = false,
            )
        }
        refresh()
    }

    fun selectTypeFilter(typeFilter: DriveFileTypeFilter) {
        mutableState.update { current ->
            val selectedFile = current.selectedFile?.takeIf { file -> file.matchesTypeFilter(typeFilter) }
            current.copy(
                typeFilter = typeFilter,
                selectedFile = selectedFile,
                selectedFileDetails = current.selectedFileDetails
                    ?.takeIf { details -> selectedFile?.id == details.file.id && details.file.matchesTypeFilter(typeFilter) },
                isLoadingFileDetails = if (selectedFile == null) false else current.isLoadingFileDetails,
                fileDetailsErrorMessage = if (selectedFile == null) null else current.fileDetailsErrorMessage,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun selectFile(file: DriveFile) {
        fileDetailsCache[file.id]?.let { cached ->
            mutableState.update {
                it.copy(
                    selectedFile = cached.file,
                    selectedFileDetails = cached,
                    isLoadingFileDetails = false,
                    fileDetailsErrorMessage = null,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                selectedFile = file,
                selectedFileDetails = null,
                isLoadingFileDetails = true,
                fileDetailsErrorMessage = null,
                errorMessage = null,
                requiresRelogin = false,
            )
        }
        scope.launch {
            when (val result = repository.loadFileDetails(file.id)) {
                is DriveFileDetailsRepositoryResult.Success -> mutableState.update { current ->
                    if (current.selectedFile?.id != file.id) {
                        current
                    } else {
                        fileDetailsCache[file.id] = result.details
                        trimFileDetailsCache()
                        current.copy(
                            selectedFile = result.details.file,
                            selectedFileDetails = result.details,
                            isLoadingFileDetails = false,
                            fileDetailsErrorMessage = null,
                            requiresRelogin = false,
                        )
                    }
                }
                DriveFileDetailsRepositoryResult.Unauthorized -> mutableState.update { current ->
                    if (current.selectedFile?.id != file.id) current else current.copy(
                        isLoadingFileDetails = false,
                        fileDetailsErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileDetailsRepositoryResult.Error -> mutableState.update { current ->
                    if (current.selectedFile?.id != file.id) current else current.copy(
                        isLoadingFileDetails = false,
                        fileDetailsErrorMessage = result.message.toFriendlyDriveErrorMessage(),
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun clearSelectedFile() {
        mutableState.update {
            it.copy(
                selectedFile = null,
                selectedFileDetails = null,
                isLoadingFileDetails = false,
                fileDetailsErrorMessage = null,
            )
        }
    }

    fun openFolder(folder: DriveFolder) {
        filesPageRequestId += 1
        foldersPageRequestId += 1
        uploadRequestId += 1
        mutableState.update {
            it.copy(
                isStreamMode = false,
                folderId = folder.id,
                folderPath = it.folderPath + folder,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                selectedFileDetails = null,
                isLoadingFileDetails = false,
                fileDetailsErrorMessage = null,
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
        filesPageRequestId += 1
        foldersPageRequestId += 1
        uploadRequestId += 1
        mutableState.update {
            it.copy(
                isStreamMode = false,
                folderId = nextFolderId,
                folderPath = nextPath,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                selectedFileDetails = null,
                isLoadingFileDetails = false,
                fileDetailsErrorMessage = null,
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
        filesPageRequestId += 1
        foldersPageRequestId += 1
        uploadRequestId += 1
        mutableState.update {
            it.copy(
                isStreamMode = false,
                folderId = nextFolderId,
                folderPath = nextPath,
                files = emptyList(),
                folders = emptyList(),
                selectedFile = null,
                selectedFileDetails = null,
                isLoadingFileDetails = false,
                fileDetailsErrorMessage = null,
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
        val requestId = ++uploadRequestId

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
                    if (requestId != uploadRequestId || it.folderId != folderId) {
                        return@update it.copy(isUploading = false)
                    }
                    it.copy(
                        files = it.files.prependDistinctBy(result.file) { file -> file.id },
                        isUploading = false,
                        errorMessage = null,
                        requiresRelogin = false,
                    )
                }
                DriveFileRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != uploadRequestId || it.folderId != folderId) {
                        return@update it.copy(isUploading = false)
                    }
                    it.copy(
                        isUploading = false,
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is DriveFileRepositoryResult.ValidationError -> mutableState.update {
                    if (requestId != uploadRequestId || it.folderId != folderId) {
                        return@update it.copy(isUploading = false)
                    }
                    it.copy(
                        isUploading = false,
                        errorMessage = result.message.toFriendlyDriveErrorMessage(),
                        requiresRelogin = false,
                    )
                }
                is DriveFileRepositoryResult.Error -> mutableState.update {
                    if (requestId != uploadRequestId || it.folderId != folderId) {
                        return@update it.copy(isUploading = false)
                    }
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

    fun moveFile(
        file: DriveFile,
        folderId: String?,
    ) {
        launchManagement {
            repository.moveFile(
                fileId = file.id,
                folderId = folderId,
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
            is DriveManagementRepositoryResult.FileUpdated -> mutableState.update { current ->
                fileDetailsCache[result.file.id]?.let { cached ->
                    fileDetailsCache[result.file.id] = cached.copy(file = result.file)
                }
                val originalFile = current.files.firstOrNull { file -> file.id == result.file.id }
                val movedOutOfCurrentFolder = originalFile != null &&
                    originalFile.folderId != result.file.folderId &&
                    result.file.folderId != current.folderId
                val updatedFiles = if (movedOutOfCurrentFolder) {
                    current.files.filterNot { file -> file.id == result.file.id }
                } else {
                    current.files.map { file ->
                        if (file.id == result.file.id) result.file else file
                    }
                }
                current.copy(
                    files = updatedFiles,
                    selectedFile = current.selectedFile?.let { file ->
                        if (file.id == result.file.id) result.file else file
                    }?.takeIf { file -> !movedOutOfCurrentFolder && file.matchesTypeFilter(current.typeFilter) },
                    selectedFileDetails = current.selectedFileDetails?.let { details ->
                        if (details.file.id == result.file.id) details.copy(file = result.file) else details
                    }?.takeIf { details -> !movedOutOfCurrentFolder && details.file.matchesTypeFilter(current.typeFilter) },
                    isLoadingFileDetails = false,
                    fileDetailsErrorMessage = null,
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FileDeleted -> mutableState.update {
                fileDetailsCache.remove(result.fileId)
                it.copy(
                    files = it.files.filterNot { file -> file.id == result.fileId },
                    selectedFile = it.selectedFile?.takeIf { file -> file.id != result.fileId },
                    selectedFileDetails = it.selectedFileDetails?.takeIf { details -> details.file.id != result.fileId },
                    isLoadingFileDetails = false,
                    fileDetailsErrorMessage = null,
                    isManaging = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            is DriveManagementRepositoryResult.FolderCreated -> mutableState.update {
                val folders = if (result.folder.parentId == it.folderId) {
                    it.folders.prependDistinctBy(result.folder) { folder -> folder.id }
                } else {
                    it.folders
                }
                it.copy(
                    folders = folders,
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
                val deletedInCurrentPath = it.folderPath.any { folder -> folder.id == result.folderId }
                val nextPath = if (deletedInCurrentPath) {
                    it.folderPath.takeWhile { folder -> folder.id != result.folderId }
                } else {
                    it.folderPath
                }
                it.copy(
                    files = if (deletedInCurrentPath) emptyList() else it.files,
                    folders = if (deletedInCurrentPath) {
                        emptyList()
                    } else {
                        it.folders.filterNot { folder -> folder.id == result.folderId }
                    },
                    folderPath = nextPath,
                    folderId = if (deletedInCurrentPath) nextPath.lastOrNull()?.id else it.folderId,
                    selectedFile = if (deletedInCurrentPath) null else it.selectedFile,
                    selectedFileDetails = if (deletedInCurrentPath) null else it.selectedFileDetails,
                    isLoadingFileDetails = false,
                    fileDetailsErrorMessage = null,
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
                val availableIds = result.files.asSequence().map { file -> file.id }.toSet()
                fileDetailsCache.keys.removeAll { cachedId -> cachedId !in availableIds }
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

    private fun trimFileDetailsCache() {
        while (fileDetailsCache.size > MAX_FILE_DETAILS_CACHE_ENTRIES) {
            val oldestKey = fileDetailsCache.keys.firstOrNull() ?: return
            fileDetailsCache.remove(oldestKey)
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

private fun DriveFileTypeFilter.streamMimeType(): String? {
    return when (this) {
        DriveFileTypeFilter.All,
        DriveFileTypeFilter.Document,
        DriveFileTypeFilter.Other -> null
        DriveFileTypeFilter.Image -> "image/*"
        DriveFileTypeFilter.Video -> "video/*"
        DriveFileTypeFilter.Audio -> "audio/*"
    }
}

private const val MAX_FILE_DETAILS_CACHE_ENTRIES = 64
