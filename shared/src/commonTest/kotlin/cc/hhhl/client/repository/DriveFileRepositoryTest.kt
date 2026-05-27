package cc.hhhl.client.repository

import cc.hhhl.client.api.DriveFileApi
import cc.hhhl.client.api.DriveFileDetailsResult
import cc.hhhl.client.api.DriveFileListResult
import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.DriveFileUploadResult
import cc.hhhl.client.api.DriveFolderListResult
import cc.hhhl.client.api.DriveFileMutationResult
import cc.hhhl.client.api.DriveFolderMutationResult
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileDetails
import cc.hhhl.client.model.DriveFolder
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class DriveFileRepositoryTest {
    @Test
    fun refreshFilesLoadsWithTokenAndQueryOptions() = runTest {
        val calls = mutableListOf<ListCall>()
        val file = sampleFile("file-1")
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                listCalls = calls,
                listResult = DriveFileListResult.Success(listOf(file)),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.refreshFiles(
            folderId = "folder-1",
            sort = DriveFileSort.NameDescending,
            searchQuery = "photo",
        )

        assertIs<DriveFilesRepositoryResult.Success>(result)
        assertEquals(listOf(ListCall("token-123", "folder-1", null, DriveFileSort.NameDescending, "photo")), calls)
        assertEquals(listOf(file), result.files)
    }

    @Test
    fun loadMoreFilesUsesLastFileIdAndDeduplicates() = runTest {
        val first = sampleFile("file-1")
        val second = sampleFile("file-2")
        val calls = mutableListOf<ListCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                listCalls = calls,
                listResult = DriveFileListResult.Success(listOf(second, first)),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.loadMoreFiles(
            currentFiles = listOf(first),
            folderId = null,
            sort = DriveFileSort.CreatedDescending,
            searchQuery = "",
        )

        assertIs<DriveFilesRepositoryResult.Success>(result)
        assertEquals(listOf(ListCall("token-123", null, "file-1", DriveFileSort.CreatedDescending, "")), calls)
        assertEquals(listOf(first, second), result.files)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingListApi() = runTest {
        var calls = 0
        val repository = DriveFileRepository(
            tokenProvider = { null },
            api = fakeApi(
                onListCall = { calls += 1 },
                listResult = DriveFileListResult.Success(emptyList()),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        assertIs<DriveFilesRepositoryResult.Unauthorized>(
            repository.refreshFiles(folderId = null, sort = DriveFileSort.CreatedDescending, searchQuery = ""),
        )
        assertEquals(0, calls)
    }

    @Test
    fun refreshFoldersLoadsWithTokenAndParentFolder() = runTest {
        val calls = mutableListOf<FolderCall>()
        val folder = sampleFolder("folder-1")
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                folderCalls = calls,
                folderResult = DriveFolderListResult.Success(listOf(folder)),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.refreshFolders(
            folderId = "parent-1",
            searchQuery = "assets",
        )

        assertIs<DriveFoldersRepositoryResult.Success>(result)
        assertEquals(listOf(FolderCall("token-123", "parent-1", null, "assets")), calls)
        assertEquals(listOf(folder), result.folders)
    }

    @Test
    fun loadMoreFoldersUsesLastFolderIdAndDeduplicates() = runTest {
        val first = sampleFolder("folder-1")
        val second = sampleFolder("folder-2")
        val calls = mutableListOf<FolderCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                folderCalls = calls,
                folderResult = DriveFolderListResult.Success(listOf(second, first)),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.loadMoreFolders(
            currentFolders = listOf(first),
            folderId = null,
            searchQuery = "",
        )

        assertIs<DriveFoldersRepositoryResult.Success>(result)
        assertEquals(listOf(FolderCall("token-123", null, "folder-1", "")), calls)
        assertEquals(listOf(first, second), result.folders)
    }

    @Test
    fun updateFileUsesTokenAndMapsUpdatedFile() = runTest {
        val updated = sampleFile("file-1").copy(name = "renamed.png", isSensitive = true)
        val calls = mutableListOf<FileUpdateCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                fileUpdateCalls = calls,
                fileMutationResult = DriveFileMutationResult.Success(updated),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.updateFile(
            fileId = "file-1",
            name = " renamed.png ",
            comment = " alt ",
            isSensitive = true,
            folderId = "folder-1",
        )

        assertEquals(
            listOf(FileUpdateCall("token-123", "file-1", "renamed.png", "alt", true, "folder-1")),
            calls,
        )
        assertEquals(DriveManagementRepositoryResult.FileUpdated(updated), result)
    }

    @Test
    fun moveFileUsesTokenAndTargetFolder() = runTest {
        val updated = sampleFile("file-1").copy(folderId = "folder-2")
        val calls = mutableListOf<FileUpdateCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                fileUpdateCalls = calls,
                fileMutationResult = DriveFileMutationResult.Success(updated),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.moveFile(
            fileId = " file-1 ",
            folderId = " folder-2 ",
        )

        assertEquals(
            listOf(FileUpdateCall("token-123", "file-1", null, null, null, "folder-2")),
            calls,
        )
        assertEquals(DriveManagementRepositoryResult.FileUpdated(updated), result)
    }

    @Test
    fun deleteFileUsesToken() = runTest {
        val calls = mutableListOf<String>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteFileCalls = calls,
                fileMutationResult = DriveFileMutationResult.Deleted,
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.deleteFile("file-1")

        assertEquals(listOf("token-123:file-1"), calls)
        assertEquals(DriveManagementRepositoryResult.FileDeleted("file-1"), result)
    }

    @Test
    fun createFolderUsesTokenAndParent() = runTest {
        val folder = sampleFolder("folder-new")
        val calls = mutableListOf<FolderCreateCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                folderCreateCalls = calls,
                folderMutationResult = DriveFolderMutationResult.Success(folder),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.createFolder(" 新文件夹 ", parentId = "parent-1")

        assertEquals(listOf(FolderCreateCall("token-123", "新文件夹", "parent-1")), calls)
        assertEquals(DriveManagementRepositoryResult.FolderCreated(folder), result)
    }

    @Test
    fun updateFolderUsesToken() = runTest {
        val folder = sampleFolder("folder-1").copy(name = "改名文件夹")
        val calls = mutableListOf<FolderUpdateCall>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                folderUpdateCalls = calls,
                folderMutationResult = DriveFolderMutationResult.Success(folder),
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.updateFolder("folder-1", " 改名文件夹 ", parentId = null)

        assertEquals(listOf(FolderUpdateCall("token-123", "folder-1", "改名文件夹", null)), calls)
        assertEquals(DriveManagementRepositoryResult.FolderUpdated(folder), result)
    }

    @Test
    fun deleteFolderUsesToken() = runTest {
        val calls = mutableListOf<String>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteFolderCalls = calls,
                folderMutationResult = DriveFolderMutationResult.Deleted,
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.deleteFolder("folder-1")

        assertEquals(listOf("token-123:folder-1"), calls)
        assertEquals(DriveManagementRepositoryResult.FolderDeleted("folder-1"), result)
    }

    @Test
    fun uploadUsesTokenAndReturnsUploadedFile() = runTest {
        val calls = mutableListOf<ApiCall>()
        val file = sampleFile()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = DriveFileUploadResult.Success(file),
            ),
        )
        val upload = sampleUpload()

        val result = repository.upload(upload)

        assertEquals(listOf(ApiCall("token-123", upload)), calls)
        assertEquals(DriveFileRepositoryResult.Success(file), result)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = DriveFileRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.upload(sampleUpload())

        assertIs<DriveFileRepositoryResult.Unauthorized>(result)
        assertEquals(0, calls)
    }

    @Test
    fun emptyFileReturnsValidationErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = DriveFileUploadResult.Success(sampleFile()),
            ),
        )

        val result = repository.upload(sampleUpload(bytes = byteArrayOf()))

        assertEquals(DriveFileRepositoryResult.ValidationError("文件内容为空"), result)
        assertEquals(0, calls)
    }

    @Test
    fun serverNoSpaceErrorMapsToFriendlyChineseMessage() = runTest {
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DriveFileUploadResult.ServerError(
                    statusCode = 400,
                    message = "Cannot upload the file because you have no free space of drive.",
                ),
            ),
        )

        val result = repository.upload(sampleUpload())

        assertEquals(DriveFileRepositoryResult.Error("Drive 空间不足，无法上传文件"), result)
    }

    @Test
    fun loadFileDetailsUsesTokenAndReturnsDetails() = runTest {
        val file = sampleFile("file-1")
        val details = DriveFileDetails(
            file = file,
            attachedNotes = listOf(
                Note(
                    id = "note-1",
                    author = User("user-1", "Alice", "alice", "A"),
                    text = "hello",
                    createdAtLabel = "2026-05-27 23:49",
                ),
            ),
        )
        val calls = mutableListOf<String>()
        val repository = DriveFileRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                result = DriveFileUploadResult.Success(sampleFile()),
                fileDetailsResult = DriveFileDetailsResult.Success(details),
                fileDetailsCalls = calls,
            ),
        )

        val result = repository.loadFileDetails("file-1")

        assertEquals(listOf("token-123:file-1"), calls)
        assertEquals(DriveFileDetailsRepositoryResult.Success(details), result)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        listCalls: MutableList<ListCall> = mutableListOf(),
        folderCalls: MutableList<FolderCall> = mutableListOf(),
        fileUpdateCalls: MutableList<FileUpdateCall> = mutableListOf(),
        deleteFileCalls: MutableList<String> = mutableListOf(),
        folderCreateCalls: MutableList<FolderCreateCall> = mutableListOf(),
        folderUpdateCalls: MutableList<FolderUpdateCall> = mutableListOf(),
        deleteFolderCalls: MutableList<String> = mutableListOf(),
        fileDetailsCalls: MutableList<String> = mutableListOf(),
        result: DriveFileUploadResult,
        listResult: DriveFileListResult = DriveFileListResult.Success(emptyList()),
        folderResult: DriveFolderListResult = DriveFolderListResult.Success(emptyList()),
        fileMutationResult: DriveFileMutationResult = DriveFileMutationResult.Success(sampleFile()),
        folderMutationResult: DriveFolderMutationResult = DriveFolderMutationResult.Success(sampleFolder("folder-1")),
        fileDetailsResult: DriveFileDetailsResult = DriveFileDetailsResult.Success(DriveFileDetails(sampleFile())),
        onCall: () -> Unit = {},
        onListCall: () -> Unit = {},
    ): DriveFileApi {
        return object : DriveFileApi {
            override suspend fun uploadFile(
                token: String,
                upload: DriveFileUpload,
            ): DriveFileUploadResult {
                onCall()
                calls.add(ApiCall(token, upload))
                return result
            }

            override suspend fun loadFiles(
                token: String,
                folderId: String?,
                limit: Int,
                untilId: String?,
                sort: DriveFileSort,
                searchQuery: String,
                showAll: Boolean,
            ): DriveFileListResult {
                onListCall()
                listCalls.add(ListCall(token, folderId, untilId, sort, searchQuery))
                return listResult
            }

            override suspend fun loadFolders(
                token: String,
                folderId: String?,
                limit: Int,
                untilId: String?,
                searchQuery: String,
            ): DriveFolderListResult {
                folderCalls.add(FolderCall(token, folderId, untilId, searchQuery))
                return folderResult
            }

            override suspend fun updateFile(
                token: String,
                fileId: String,
                name: String?,
                folderId: String?,
                comment: String?,
                isSensitive: Boolean?,
            ): DriveFileMutationResult {
                fileUpdateCalls.add(FileUpdateCall(token, fileId, name, comment, isSensitive, folderId))
                return fileMutationResult
            }

            override suspend fun deleteFile(
                token: String,
                fileId: String,
            ): DriveFileMutationResult {
                deleteFileCalls.add("$token:$fileId")
                return fileMutationResult
            }

            override suspend fun createFolder(
                token: String,
                name: String,
                parentId: String?,
            ): DriveFolderMutationResult {
                folderCreateCalls.add(FolderCreateCall(token, name, parentId))
                return folderMutationResult
            }

            override suspend fun updateFolder(
                token: String,
                folderId: String,
                name: String?,
                parentId: String?,
            ): DriveFolderMutationResult {
                folderUpdateCalls.add(FolderUpdateCall(token, folderId, name, parentId))
                return folderMutationResult
            }

            override suspend fun deleteFolder(
                token: String,
                folderId: String,
            ): DriveFolderMutationResult {
                deleteFolderCalls.add("$token:$folderId")
                return folderMutationResult
            }

            override suspend fun loadFileDetails(
                token: String,
                fileId: String,
            ): DriveFileDetailsResult {
                fileDetailsCalls.add("$token:$fileId")
                return fileDetailsResult
            }
        }
    }

    private fun sampleUpload(bytes: ByteArray = byteArrayOf(1, 2, 3)): DriveFileUpload {
        return DriveFileUpload(
            bytes = bytes,
            fileName = "photo.png",
            contentType = "image/png",
            comment = "alt text",
            isSensitive = false,
        )
    }

    private fun sampleFile(id: String = "file-1"): DriveFile {
        return DriveFile(
            id = id,
            name = "photo.png",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/photo.png",
            thumbnailUrl = "https://dc.hhhl.cc/thumb/photo.png",
            comment = "alt text",
            size = 3,
            isSensitive = false,
        )
    }

    private fun sampleFolder(id: String): DriveFolder {
        return DriveFolder(
            id = id,
            name = "Folder $id",
            parentId = null,
            foldersCount = 1,
            filesCount = 2,
            createdAtLabel = "2026-05-25 04:00",
        )
    }

    private data class ApiCall(
        val token: String,
        val upload: DriveFileUpload,
    )

    private data class ListCall(
        val token: String,
        val folderId: String?,
        val untilId: String?,
        val sort: DriveFileSort,
        val searchQuery: String,
    )

    private data class FolderCall(
        val token: String,
        val folderId: String?,
        val untilId: String?,
        val searchQuery: String,
    )

    private data class FileUpdateCall(
        val token: String,
        val fileId: String,
        val name: String?,
        val comment: String?,
        val isSensitive: Boolean?,
        val folderId: String?,
    )

    private data class FolderCreateCall(
        val token: String,
        val name: String,
        val parentId: String?,
    )

    private data class FolderUpdateCall(
        val token: String,
        val folderId: String,
        val name: String?,
        val parentId: String?,
    )
}
