package cc.hhhl.client.repository

import cc.hhhl.client.api.DriveFileApi
import cc.hhhl.client.api.DriveFileListResult
import cc.hhhl.client.api.DriveFileMutationResult
import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.DriveFileUploadResult
import cc.hhhl.client.api.DriveFolderListResult
import cc.hhhl.client.api.DriveFolderMutationResult
import cc.hhhl.client.api.SharkeyDriveFileApi
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFolder

open class DriveFileRepository(
    private val tokenProvider: () -> String?,
    private val api: DriveFileApi = SharkeyDriveFileApi(),
) {
    open suspend fun refreshFiles(
        folderId: String?,
        sort: DriveFileSort,
        searchQuery: String,
    ): DriveFilesRepositoryResult {
        return loadFiles(
            currentFiles = emptyList(),
            folderId = folderId,
            sort = sort,
            searchQuery = searchQuery,
            untilId = null,
        )
    }

    open suspend fun loadMoreFiles(
        currentFiles: List<DriveFile>,
        folderId: String?,
        sort: DriveFileSort,
        searchQuery: String,
    ): DriveFilesRepositoryResult {
        return loadFiles(
            currentFiles = currentFiles,
            folderId = folderId,
            sort = sort,
            searchQuery = searchQuery,
            untilId = currentFiles.lastOrNull()?.id,
        )
    }

    open suspend fun refreshFolders(
        folderId: String?,
        searchQuery: String,
    ): DriveFoldersRepositoryResult {
        return loadFolders(
            currentFolders = emptyList(),
            folderId = folderId,
            searchQuery = searchQuery,
            untilId = null,
        )
    }

    open suspend fun loadMoreFolders(
        currentFolders: List<DriveFolder>,
        folderId: String?,
        searchQuery: String,
    ): DriveFoldersRepositoryResult {
        return loadFolders(
            currentFolders = currentFolders,
            folderId = folderId,
            searchQuery = searchQuery,
            untilId = currentFolders.lastOrNull()?.id,
        )
    }

    open suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult {
        if (upload.bytes.isEmpty()) {
            return DriveFileRepositoryResult.ValidationError("文件内容为空")
        }
        if (upload.fileName.isBlank()) {
            return DriveFileRepositoryResult.ValidationError("文件名不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveFileRepositoryResult.Unauthorized

        return when (val result = api.uploadFile(token, upload)) {
            is DriveFileUploadResult.Success -> DriveFileRepositoryResult.Success(result.file)
            DriveFileUploadResult.Unauthorized -> DriveFileRepositoryResult.Unauthorized
            is DriveFileUploadResult.NetworkError -> {
                DriveFileRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFileUploadResult.ServerError -> {
                DriveFileRepositoryResult.Error(result.message.toFriendlyDriveUploadError())
            }
        }
    }

    open suspend fun updateFile(
        fileId: String,
        name: String? = null,
        comment: String? = null,
        isSensitive: Boolean? = null,
        folderId: String? = null,
    ): DriveManagementRepositoryResult {
        val cleanFileId = fileId.trim()
        if (cleanFileId.isEmpty()) {
            return DriveManagementRepositoryResult.ValidationError("文件 ID 不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveManagementRepositoryResult.Unauthorized

        return when (
            val result = api.updateFile(
                token = token,
                fileId = cleanFileId,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                folderId = folderId?.trim()?.takeIf { it.isNotEmpty() },
                comment = comment?.trim()?.takeIf { it.isNotEmpty() },
                isSensitive = isSensitive,
            )
        ) {
            is DriveFileMutationResult.Success -> DriveManagementRepositoryResult.FileUpdated(result.file)
            DriveFileMutationResult.Deleted -> DriveManagementRepositoryResult.FileDeleted(cleanFileId)
            DriveFileMutationResult.Unauthorized -> DriveManagementRepositoryResult.Unauthorized
            is DriveFileMutationResult.NetworkError -> {
                DriveManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFileMutationResult.ServerError -> DriveManagementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteFile(fileId: String): DriveManagementRepositoryResult {
        val cleanFileId = fileId.trim()
        if (cleanFileId.isEmpty()) {
            return DriveManagementRepositoryResult.ValidationError("文件 ID 不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveManagementRepositoryResult.Unauthorized

        return when (val result = api.deleteFile(token, cleanFileId)) {
            is DriveFileMutationResult.Success -> DriveManagementRepositoryResult.FileUpdated(result.file)
            DriveFileMutationResult.Deleted -> DriveManagementRepositoryResult.FileDeleted(cleanFileId)
            DriveFileMutationResult.Unauthorized -> DriveManagementRepositoryResult.Unauthorized
            is DriveFileMutationResult.NetworkError -> {
                DriveManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFileMutationResult.ServerError -> DriveManagementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun createFolder(
        name: String,
        parentId: String? = null,
    ): DriveManagementRepositoryResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return DriveManagementRepositoryResult.ValidationError("文件夹名不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveManagementRepositoryResult.Unauthorized

        return when (
            val result = api.createFolder(
                token = token,
                name = cleanName,
                parentId = parentId?.trim()?.takeIf { it.isNotEmpty() },
            )
        ) {
            is DriveFolderMutationResult.Success -> DriveManagementRepositoryResult.FolderCreated(result.folder)
            DriveFolderMutationResult.Deleted -> DriveManagementRepositoryResult.Error("文件夹创建失败")
            DriveFolderMutationResult.Unauthorized -> DriveManagementRepositoryResult.Unauthorized
            is DriveFolderMutationResult.NetworkError -> {
                DriveManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFolderMutationResult.ServerError -> DriveManagementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updateFolder(
        folderId: String,
        name: String? = null,
        parentId: String? = null,
    ): DriveManagementRepositoryResult {
        val cleanFolderId = folderId.trim()
        if (cleanFolderId.isEmpty()) {
            return DriveManagementRepositoryResult.ValidationError("文件夹 ID 不能为空")
        }
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveManagementRepositoryResult.Unauthorized

        return when (
            val result = api.updateFolder(
                token = token,
                folderId = cleanFolderId,
                name = cleanName,
                parentId = parentId?.trim()?.takeIf { it.isNotEmpty() },
            )
        ) {
            is DriveFolderMutationResult.Success -> DriveManagementRepositoryResult.FolderUpdated(result.folder)
            DriveFolderMutationResult.Deleted -> DriveManagementRepositoryResult.FolderDeleted(cleanFolderId)
            DriveFolderMutationResult.Unauthorized -> DriveManagementRepositoryResult.Unauthorized
            is DriveFolderMutationResult.NetworkError -> {
                DriveManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFolderMutationResult.ServerError -> DriveManagementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deleteFolder(folderId: String): DriveManagementRepositoryResult {
        val cleanFolderId = folderId.trim()
        if (cleanFolderId.isEmpty()) {
            return DriveManagementRepositoryResult.ValidationError("文件夹 ID 不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveManagementRepositoryResult.Unauthorized

        return when (val result = api.deleteFolder(token, cleanFolderId)) {
            is DriveFolderMutationResult.Success -> DriveManagementRepositoryResult.FolderUpdated(result.folder)
            DriveFolderMutationResult.Deleted -> DriveManagementRepositoryResult.FolderDeleted(cleanFolderId)
            DriveFolderMutationResult.Unauthorized -> DriveManagementRepositoryResult.Unauthorized
            is DriveFolderMutationResult.NetworkError -> {
                DriveManagementRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFolderMutationResult.ServerError -> DriveManagementRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadFolders(
        currentFolders: List<DriveFolder>,
        folderId: String?,
        searchQuery: String,
        untilId: String?,
    ): DriveFoldersRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveFoldersRepositoryResult.Unauthorized

        return when (
            val result = api.loadFolders(
                token = token,
                folderId = folderId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                searchQuery = searchQuery.trim(),
            )
        ) {
            is DriveFolderListResult.Success -> DriveFoldersRepositoryResult.Success(
                folders = (currentFolders + result.folders).distinctBy { it.id },
                endReached = result.folders.isEmpty(),
            )
            DriveFolderListResult.Unauthorized -> DriveFoldersRepositoryResult.Unauthorized
            is DriveFolderListResult.NetworkError -> {
                DriveFoldersRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFolderListResult.ServerError -> DriveFoldersRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadFiles(
        currentFiles: List<DriveFile>,
        folderId: String?,
        sort: DriveFileSort,
        searchQuery: String,
        untilId: String?,
    ): DriveFilesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return DriveFilesRepositoryResult.Unauthorized

        return when (
            val result = api.loadFiles(
                token = token,
                folderId = folderId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                sort = sort,
                searchQuery = searchQuery.trim(),
                showAll = false,
            )
        ) {
            is DriveFileListResult.Success -> DriveFilesRepositoryResult.Success(
                files = (currentFiles + result.files).distinctBy { it.id },
                endReached = result.files.isEmpty(),
            )
            DriveFileListResult.Unauthorized -> DriveFilesRepositoryResult.Unauthorized
            is DriveFileListResult.NetworkError -> {
                DriveFilesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is DriveFileListResult.ServerError -> DriveFilesRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}

private fun String.toFriendlyDriveUploadError(): String {
    return when (this) {
        "Cannot upload the file because you have no free space of drive." -> "Drive 空间不足，无法上传文件"
        "Cannot upload the file because it exceeds the maximum file size." -> "文件超过实例大小限制"
        else -> this
    }
}

sealed interface DriveFileRepositoryResult {
    data class Success(val file: DriveFile) : DriveFileRepositoryResult

    data object Unauthorized : DriveFileRepositoryResult

    data class ValidationError(val message: String) : DriveFileRepositoryResult

    data class Error(val message: String) : DriveFileRepositoryResult
}

sealed interface DriveManagementRepositoryResult {
    data class FileUpdated(val file: DriveFile) : DriveManagementRepositoryResult

    data class FileDeleted(val fileId: String) : DriveManagementRepositoryResult

    data class FolderCreated(val folder: DriveFolder) : DriveManagementRepositoryResult

    data class FolderUpdated(val folder: DriveFolder) : DriveManagementRepositoryResult

    data class FolderDeleted(val folderId: String) : DriveManagementRepositoryResult

    data object Unauthorized : DriveManagementRepositoryResult

    data class ValidationError(val message: String) : DriveManagementRepositoryResult

    data class Error(val message: String) : DriveManagementRepositoryResult
}

sealed interface DriveFilesRepositoryResult {
    data class Success(
        val files: List<DriveFile>,
        val endReached: Boolean = false,
    ) : DriveFilesRepositoryResult

    data object Unauthorized : DriveFilesRepositoryResult

    data class Error(val message: String) : DriveFilesRepositoryResult
}

sealed interface DriveFoldersRepositoryResult {
    data class Success(
        val folders: List<DriveFolder>,
        val endReached: Boolean = false,
    ) : DriveFoldersRepositoryResult

    data object Unauthorized : DriveFoldersRepositoryResult

    data class Error(val message: String) : DriveFoldersRepositoryResult
}
