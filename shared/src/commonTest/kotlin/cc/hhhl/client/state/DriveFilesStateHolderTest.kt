package cc.hhhl.client.state

import cc.hhhl.client.api.DriveFileApi
import cc.hhhl.client.api.DriveFileListResult
import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.api.DriveFileUpload
import cc.hhhl.client.api.DriveFileUploadResult
import cc.hhhl.client.api.DriveFileMutationResult
import cc.hhhl.client.api.DriveFolderMutationResult
import cc.hhhl.client.api.DriveFolderListResult
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileDetails
import cc.hhhl.client.model.DriveFileTypeFilter
import cc.hhhl.client.model.DriveFolder
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.User
import cc.hhhl.client.repository.DriveFileRepository
import cc.hhhl.client.repository.DriveFileDetailsRepositoryResult
import cc.hhhl.client.repository.DriveFileRepositoryResult
import cc.hhhl.client.repository.DriveFilesRepositoryResult
import cc.hhhl.client.repository.DriveFoldersRepositoryResult
import cc.hhhl.client.repository.DriveManagementRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DriveFilesStateHolderTest {
    @Test
    fun refreshLoadsDriveFiles() = runTest {
        val file = sampleFile("file-1")
        val folder = sampleFolder("folder-1")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(file)),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(listOf(folder)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(file), holder.state.value.files)
        assertEquals(listOf(folder), holder.state.value.folders)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun updateQueryAndSearchRefreshesWithQuery() = runTest {
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.updateSearchQuery(" photo ")
        holder.search()
        advanceUntilIdle()

        assertEquals(" photo ", holder.state.value.searchQuery)
        assertEquals(listOf(QueryCall(null, DriveFileSort.CreatedDescending, "photo")), calls)
    }

    @Test
    fun selectSortRefreshesWithNewSort() = runTest {
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.selectSort(DriveFileSort.NameAscending)
        advanceUntilIdle()

        assertEquals(DriveFileSort.NameAscending, holder.state.value.sort)
        assertEquals(listOf(QueryCall(null, DriveFileSort.NameAscending, "")), calls)
    }

    @Test
    fun selectTypeFilterNarrowsVisibleFilesWithoutReloading() = runTest {
        val image = sampleFile("file-image").copy(type = "image/png")
        val audio = sampleFile("file-audio").copy(type = "audio/mpeg")
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(image, audio)),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectTypeFilter(DriveFileTypeFilter.Image)

        assertEquals(DriveFileTypeFilter.Image, holder.state.value.typeFilter)
        assertEquals(listOf(image), holder.state.value.visibleFiles)
        assertEquals(1, calls.size)
    }

    @Test
    fun visibleSelectionItemsExposePickerReadyFileMetadata() {
        val selectable = sampleFile("file-image").copy(
            name = "",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/file-image.png",
            thumbnailUrl = "https://dc.hhhl.cc/files/file-image-thumb.webp",
            size = 1536,
        )
        val missingUrl = sampleFile("file-audio").copy(
            type = "audio/mpeg",
            url = null,
            thumbnailUrl = null,
        )
        val state = DriveFilesUiState(
            files = listOf(selectable, missingUrl),
            typeFilter = DriveFileTypeFilter.All,
        )

        val specs = state.visibleSelectionItems()

        assertEquals(listOf("file-image", "file-audio"), specs.map { it.id })
        assertEquals("未命名文件", specs.first().name)
        assertEquals("1.5 KB", specs.first().sizeLabel)
        assertTrue(specs.first().canPreviewInline)
        assertTrue(specs.first().canSelect)
        assertFalse(specs[1].canSelect)
        assertEquals("文件链接不可用", specs[1].disabledReason)
    }

    @Test
    fun selectingMismatchedTypeClearsSelectedFileDetails() = runTest {
        val image = sampleFile("file-image").copy(type = "image/png")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(image)),
                fileDetailsResult = DriveFileDetailsRepositoryResult.Success(DriveFileDetails(image)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFile(image)
        holder.selectTypeFilter(DriveFileTypeFilter.Audio)

        assertEquals(null, holder.state.value.selectedFile)
        assertEquals(null, holder.state.value.selectedFileDetails)
        assertFalse(holder.state.value.isLoadingFileDetails)
        assertEquals(emptyList(), holder.state.value.visibleFiles)
    }

    @Test
    fun changingTypeFilterWhileDetailsLoadClearsPendingSelectionState() = runTest {
        val image = sampleFile("file-image").copy(type = "image/png")
        val pendingDetails = CompletableDeferred<DriveFileDetailsRepositoryResult>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(image)),
                fileDetailsResultProvider = { pendingDetails.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFile(image)
        runCurrent()
        assertTrue(holder.state.value.isLoadingFileDetails)

        holder.selectTypeFilter(DriveFileTypeFilter.Audio)
        pendingDetails.complete(DriveFileDetailsRepositoryResult.Success(DriveFileDetails(image)))
        advanceUntilIdle()

        assertEquals(null, holder.state.value.selectedFile)
        assertEquals(null, holder.state.value.selectedFileDetails)
        assertFalse(holder.state.value.isLoadingFileDetails)
        assertEquals(null, holder.state.value.fileDetailsErrorMessage)
    }

    @Test
    fun selectFileLoadsRealFileDetailsAndAttachedNotes() = runTest {
        val file = sampleFile("file-1")
        val detailed = file.copy(comment = "real comment")
        val note = Note(
            id = "note-1",
            author = User("user-1", "Alice", "alice", "A"),
            text = "used here",
            createdAtLabel = "2026-05-27 23:49",
        )
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(file)),
                fileDetailsResult = DriveFileDetailsRepositoryResult.Success(
                    DriveFileDetails(file = detailed, attachedNotes = listOf(note)),
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFile(file)

        assertTrue(holder.state.value.isLoadingFileDetails)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingFileDetails)
        assertEquals(detailed, holder.state.value.selectedFile)
        assertEquals(listOf(note), holder.state.value.selectedFileDetails?.attachedNotes)
    }

    @Test
    fun selectedFileTracksFileUpdateAndDeletion() = runTest {
        val existing = sampleFile("file-1")
        val updated = existing.copy(name = "renamed.png")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(existing)),
                updateFileResult = DriveManagementRepositoryResult.FileUpdated(updated),
                deleteFileResult = DriveManagementRepositoryResult.FileDeleted("file-1"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.selectFile(existing)
        holder.updateFile(existing, name = "renamed.png")
        advanceUntilIdle()

        assertEquals(updated, holder.state.value.selectedFile)

        holder.deleteFile("file-1")
        advanceUntilIdle()

        assertEquals(null, holder.state.value.selectedFile)
    }

    @Test
    fun loadMoreAppendsAndMarksEndReached() = runTest {
        val first = sampleFile("file-1")
        val second = sampleFile("file-2")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(first)),
                loadMoreResult = DriveFilesRepositoryResult.Success(
                    files = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertTrue(holder.state.value.endReached)
        assertEquals(listOf(first, second), holder.state.value.files)
    }

    @Test
    fun changingFolderInvalidatesPendingLoadMoreFiles() = runTest {
        val first = sampleFile("file-1")
        val stale = sampleFile("file-stale")
        val folder = sampleFolder("folder-next")
        val pending = CompletableDeferred<DriveFilesRepositoryResult>()
        var refreshCount = 0
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(first)),
                refreshResultProvider = {
                    refreshCount += 1
                    DriveFilesRepositoryResult.Success(if (refreshCount == 1) listOf(first) else emptyList())
                },
                loadMoreResultProvider = { pending.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.loadMore()
        runCurrent()
        assertTrue(holder.state.value.isLoadingMore)

        holder.openFolder(folder)
        pending.complete(DriveFilesRepositoryResult.Success(listOf(first, stale)))
        advanceUntilIdle()

        assertEquals(folder.id, holder.state.value.folderId)
        assertFalse(holder.state.value.isLoadingMore)
        assertEquals(emptyList(), holder.state.value.files)
        assertEquals(false, holder.state.value.files.any { it.id == stale.id })
    }

    @Test
    fun openFolderLoadsFolderContentsAndTracksPath() = runTest {
        val rootFolder = sampleFolder("folder-root")
        val childFile = sampleFile("file-child")
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(childFile)),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(rootFolder)
        assertEquals("folder-root", holder.state.value.folderId)
        assertEquals(listOf(rootFolder), holder.state.value.folderPath)
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(listOf(childFile), holder.state.value.files)
        assertEquals(listOf(QueryCall("folder-root", DriveFileSort.CreatedDescending, "")), calls)
    }

    @Test
    fun navigateUpReturnsToParentFolderAndRefreshes() = runTest {
        val rootFolder = sampleFolder("folder-root")
        val childFolder = sampleFolder("folder-child", parentId = "folder-root")
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(rootFolder)
        advanceUntilIdle()
        holder.openFolder(childFolder)
        advanceUntilIdle()
        holder.navigateUp()
        advanceUntilIdle()

        assertEquals("folder-root", holder.state.value.folderId)
        assertEquals(listOf(rootFolder), holder.state.value.folderPath)
        assertEquals(
            listOf(
                QueryCall("folder-root", DriveFileSort.CreatedDescending, ""),
                QueryCall("folder-child", DriveFileSort.CreatedDescending, ""),
                QueryCall("folder-root", DriveFileSort.CreatedDescending, ""),
            ),
            calls,
        )
    }

    @Test
    fun navigateToPathIndexReturnsToSelectedAncestorAndRefreshes() = runTest {
        val rootFolder = sampleFolder("folder-root")
        val childFolder = sampleFolder("folder-child", parentId = rootFolder.id)
        val grandChildFolder = sampleFolder("folder-grandchild", parentId = childFolder.id)
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(rootFolder)
        advanceUntilIdle()
        holder.openFolder(childFolder)
        advanceUntilIdle()
        holder.openFolder(grandChildFolder)
        advanceUntilIdle()

        holder.navigateToPathIndex(0)
        advanceUntilIdle()

        assertEquals(rootFolder.id, holder.state.value.folderId)
        assertEquals(listOf(rootFolder), holder.state.value.folderPath)
        assertEquals(
            listOf(
                QueryCall("folder-root", DriveFileSort.CreatedDescending, ""),
                QueryCall("folder-child", DriveFileSort.CreatedDescending, ""),
                QueryCall("folder-grandchild", DriveFileSort.CreatedDescending, ""),
                QueryCall("folder-root", DriveFileSort.CreatedDescending, ""),
            ),
            calls,
        )
    }

    @Test
    fun navigateToRootFromPathClearsFolderAndRefreshes() = runTest {
        val rootFolder = sampleFolder("folder-root")
        val calls = mutableListOf<QueryCall>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                onRefresh = { folderId, sort, query -> calls.add(QueryCall(folderId, sort, query)) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(rootFolder)
        advanceUntilIdle()
        holder.navigateToPathIndex(-1)
        advanceUntilIdle()

        assertEquals(null, holder.state.value.folderId)
        assertEquals(emptyList(), holder.state.value.folderPath)
        assertEquals(
            listOf(
                QueryCall("folder-root", DriveFileSort.CreatedDescending, ""),
                QueryCall(null, DriveFileSort.CreatedDescending, ""),
            ),
            calls,
        )
    }

    @Test
    fun uploadInsideFolderPassesCurrentFolderId() = runTest {
        val folder = sampleFolder("folder-1")
        val calls = mutableListOf<DriveFileUpload>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                uploadResult = DriveFileRepositoryResult.Success(sampleFile("uploaded")),
                onUpload = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(folder)
        advanceUntilIdle()
        holder.upload(sampleUpload())
        advanceUntilIdle()

        assertEquals("folder-1", calls.single().folderId)
    }

    @Test
    fun uploadAddsFileToTop() = runTest {
        val existing = sampleFile("file-1")
        val uploaded = sampleFile("file-uploaded")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(existing)),
                uploadResult = DriveFileRepositoryResult.Success(uploaded),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.upload(sampleUpload())
        assertTrue(holder.state.value.isUploading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isUploading)
        assertEquals(listOf(uploaded, existing), holder.state.value.files)
    }

    @Test
    fun changingFolderInvalidatesPendingUploadResult() = runTest {
        val folder = sampleFolder("folder-next")
        val uploaded = sampleFile("uploaded")
        val pending = CompletableDeferred<DriveFileRepositoryResult>()
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                uploadResultProvider = { pending.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.upload(sampleUpload())
        runCurrent()
        assertTrue(holder.state.value.isUploading)

        holder.openFolder(folder)
        pending.complete(DriveFileRepositoryResult.Success(uploaded))
        advanceUntilIdle()

        assertEquals(folder.id, holder.state.value.folderId)
        assertFalse(holder.state.value.isUploading)
        assertEquals(emptyList(), holder.state.value.files)
    }

    @Test
    fun updateFileReplacesItemInList() = runTest {
        val existing = sampleFile("file-1")
        val updated = existing.copy(name = "renamed.png", comment = "alt", isSensitive = true)
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(existing)),
                updateFileResult = DriveManagementRepositoryResult.FileUpdated(updated),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.updateFile(existing, name = "renamed.png", comment = "alt", isSensitive = true)
        assertTrue(holder.state.value.isManaging)
        advanceUntilIdle()

        assertFalse(holder.state.value.isManaging)
        assertEquals(listOf(updated), holder.state.value.files)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun moveFileOutOfCurrentFolderRemovesItemAndClearsDetails() = runTest {
        val folder = sampleFolder("folder-1")
        val existing = sampleFile("file-1").copy(folderId = folder.id)
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(existing)),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(folder)
        advanceUntilIdle()
        holder.selectFile(existing)
        holder.moveFile(existing, folderId = null)
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.files)
        assertEquals(null, holder.state.value.selectedFile)
    }

    @Test
    fun deleteFileRemovesItemFromList() = runTest {
        val first = sampleFile("file-1")
        val second = sampleFile("file-2")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(first, second)),
                deleteFileResult = DriveManagementRepositoryResult.FileDeleted("file-1"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.deleteFile("file-1")
        assertTrue(holder.state.value.isManaging)
        advanceUntilIdle()

        assertFalse(holder.state.value.isManaging)
        assertEquals(listOf(second), holder.state.value.files)
    }

    @Test
    fun createFolderAddsFolderToTop() = runTest {
        val existing = sampleFolder("folder-1")
        val created = sampleFolder("folder-new").copy(name = "新文件夹")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(listOf(existing)),
                createFolderResult = DriveManagementRepositoryResult.FolderCreated(created),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.createFolder("新文件夹")
        assertTrue(holder.state.value.isManaging)
        advanceUntilIdle()

        assertFalse(holder.state.value.isManaging)
        assertEquals(listOf(created, existing), holder.state.value.folders)
    }

    @Test
    fun updateFolderReplacesItemInList() = runTest {
        val existing = sampleFolder("folder-1")
        val updated = existing.copy(name = "改名文件夹")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(listOf(existing)),
                updateFolderResult = DriveManagementRepositoryResult.FolderUpdated(updated),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.updateFolder(existing, name = "改名文件夹")
        advanceUntilIdle()

        assertEquals(listOf(updated), holder.state.value.folders)
    }

    @Test
    fun updateFolderReplacesPathEntry() = runTest {
        val folder = sampleFolder("folder-1")
        val updated = folder.copy(name = "改名文件夹")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                updateFolderResult = DriveManagementRepositoryResult.FolderUpdated(updated),
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(folder)
        advanceUntilIdle()
        holder.updateFolder(folder, name = "改名文件夹")
        advanceUntilIdle()

        assertEquals(listOf(updated), holder.state.value.folderPath)
        assertEquals("folder-1", holder.state.value.folderId)
    }

    @Test
    fun deleteFolderRemovesItemFromList() = runTest {
        val first = sampleFolder("folder-1")
        val second = sampleFolder("folder-2")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(listOf(first, second)),
                deleteFolderResult = DriveManagementRepositoryResult.FolderDeleted("folder-1"),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.deleteFolder("folder-1")
        advanceUntilIdle()

        assertEquals(listOf(second), holder.state.value.folders)
    }

    @Test
    fun unauthorizedManagementActionMarksRelogin() = runTest {
        val existing = sampleFile("file-1")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(listOf(existing)),
                updateFileResult = DriveManagementRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        holder.updateFile(existing, name = "renamed.png", comment = null, isSensitive = false)
        advanceUntilIdle()

        assertFalse(holder.state.value.isManaging)
        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun unauthorizedRefreshMarksRelogin() = runTest {
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun endpointErrorShowsFriendlyDriveMessage() = runTest {
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Error(
                    "POST https://dc.hhhl.cc/api/drive/files failed: Endpoint unavailable",
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("Drive 接口暂时不可用，请稍后重试", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorized() = runTest {
        val file = sampleFile("file-1")
        var refreshResult: DriveFilesRepositoryResult = DriveFilesRepositoryResult.Unauthorized
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshResultProvider = { refreshResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshResult = DriveFilesRepositoryResult.Success(listOf(file))
        holder.refresh()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(file), holder.state.value.files)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun updateSearchQueryClearsReloginAfterUnauthorized() = runTest {
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.updateSearchQuery("new query")

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals("new query", holder.state.value.searchQuery)
    }

    @Test
    fun selectSortClearsReloginImmediatelyBeforeRefreshCompletes() = runTest {
        var refreshResult: DriveFilesRepositoryResult = DriveFilesRepositoryResult.Unauthorized
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshResultProvider = { refreshResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshResult = DriveFilesRepositoryResult.Success(emptyList())
        holder.selectSort(DriveFileSort.NameAscending)

        assertFalse(holder.state.value.requiresRelogin)
        assertTrue(holder.state.value.isLoading)
        assertEquals(DriveFileSort.NameAscending, holder.state.value.sort)
    }

    @Test
    fun openFolderClearsReloginImmediatelyBeforeRefreshCompletes() = runTest {
        var refreshResult: DriveFilesRepositoryResult = DriveFilesRepositoryResult.Unauthorized
        val folder = sampleFolder("folder-next")
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                refreshResultProvider = { refreshResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshResult = DriveFilesRepositoryResult.Success(emptyList())
        holder.openFolder(folder)

        assertFalse(holder.state.value.requiresRelogin)
        assertTrue(holder.state.value.isLoading)
        assertEquals(folder.id, holder.state.value.folderId)
    }

    @Test
    fun navigateUpClearsReloginImmediatelyBeforeRefreshCompletes() = runTest {
        var refreshResult: DriveFilesRepositoryResult = DriveFilesRepositoryResult.Success(emptyList())
        val rootFolder = sampleFolder("folder-root")
        val childFolder = sampleFolder("folder-child", parentId = rootFolder.id)
        val holder = DriveFilesStateHolder(
            repository = fakeRepository(
                refreshResult = DriveFilesRepositoryResult.Success(emptyList()),
                refreshFoldersResult = DriveFoldersRepositoryResult.Success(emptyList()),
                refreshResultProvider = { refreshResult },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openFolder(rootFolder)
        advanceUntilIdle()
        holder.openFolder(childFolder)
        advanceUntilIdle()

        refreshResult = DriveFilesRepositoryResult.Unauthorized
        holder.refresh()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        refreshResult = DriveFilesRepositoryResult.Success(emptyList())
        holder.navigateUp()

        assertFalse(holder.state.value.requiresRelogin)
        assertTrue(holder.state.value.isLoading)
        assertEquals(rootFolder.id, holder.state.value.folderId)
    }

    private fun fakeRepository(
        refreshResult: DriveFilesRepositoryResult,
        refreshFoldersResult: DriveFoldersRepositoryResult = DriveFoldersRepositoryResult.Success(emptyList()),
        loadMoreFoldersResult: DriveFoldersRepositoryResult = refreshFoldersResult,
        loadMoreResult: DriveFilesRepositoryResult = refreshResult,
        uploadResult: DriveFileRepositoryResult = DriveFileRepositoryResult.Success(sampleFile("uploaded")),
        updateFileResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FileUpdated(sampleFile("managed")),
        deleteFileResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FileDeleted("file-1"),
        createFolderResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FolderCreated(sampleFolder("folder-created")),
        updateFolderResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FolderUpdated(sampleFolder("folder-updated")),
        deleteFolderResult: DriveManagementRepositoryResult = DriveManagementRepositoryResult.FolderDeleted("folder-1"),
        fileDetailsResult: DriveFileDetailsRepositoryResult = DriveFileDetailsRepositoryResult.Error("unused"),
        onRefresh: (String?, DriveFileSort, String) -> Unit = { _, _, _ -> },
        onUpload: (DriveFileUpload) -> Unit = {},
        refreshResultProvider: (suspend () -> DriveFilesRepositoryResult)? = null,
        refreshFoldersResultProvider: (suspend () -> DriveFoldersRepositoryResult)? = null,
        loadMoreResultProvider: suspend () -> DriveFilesRepositoryResult = { loadMoreResult },
        loadMoreFoldersResultProvider: suspend () -> DriveFoldersRepositoryResult = { loadMoreFoldersResult },
        uploadResultProvider: suspend () -> DriveFileRepositoryResult = { uploadResult },
        fileDetailsResultProvider: suspend (String) -> DriveFileDetailsRepositoryResult = { fileDetailsResult },
    ): DriveFileRepository {
        return object : DriveFileRepository(
            tokenProvider = { "token-123" },
            api = object : DriveFileApi {
                override suspend fun uploadFile(
                    token: String,
                    upload: DriveFileUpload,
                ): DriveFileUploadResult = DriveFileUploadResult.Success(sampleFile("uploaded"))

                override suspend fun loadFiles(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    sort: DriveFileSort,
                    searchQuery: String,
                    showAll: Boolean,
                ): DriveFileListResult = DriveFileListResult.Success(emptyList())

                override suspend fun loadFolders(
                    token: String,
                    folderId: String?,
                    limit: Int,
                    untilId: String?,
                    searchQuery: String,
                ): DriveFolderListResult = DriveFolderListResult.Success(emptyList())

                override suspend fun updateFile(
                    token: String,
                    fileId: String,
                    name: String?,
                    folderId: String?,
                    comment: String?,
                    isSensitive: Boolean?,
                ): DriveFileMutationResult = DriveFileMutationResult.Success(sampleFile(fileId))

                override suspend fun deleteFile(
                    token: String,
                    fileId: String,
                ): DriveFileMutationResult = DriveFileMutationResult.Deleted

                override suspend fun createFolder(
                    token: String,
                    name: String,
                    parentId: String?,
                ): DriveFolderMutationResult = DriveFolderMutationResult.Success(sampleFolder("folder-created"))

                override suspend fun updateFolder(
                    token: String,
                    folderId: String,
                    name: String?,
                    parentId: String?,
                ): DriveFolderMutationResult = DriveFolderMutationResult.Success(sampleFolder(folderId))

                override suspend fun deleteFolder(
                    token: String,
                    folderId: String,
                ): DriveFolderMutationResult = DriveFolderMutationResult.Deleted

                override suspend fun loadFileDetails(
                    token: String,
                    fileId: String,
                ): cc.hhhl.client.api.DriveFileDetailsResult {
                    return cc.hhhl.client.api.DriveFileDetailsResult.Success(
                        cc.hhhl.client.model.DriveFileDetails(sampleFile(fileId)),
                    )
                }
            },
        ) {
            override suspend fun refreshFiles(
                folderId: String?,
                sort: DriveFileSort,
                searchQuery: String,
            ): DriveFilesRepositoryResult {
                onRefresh(folderId, sort, searchQuery)
                return refreshResultProvider?.invoke() ?: refreshResult
            }

            override suspend fun loadMoreFiles(
                currentFiles: List<DriveFile>,
                folderId: String?,
                sort: DriveFileSort,
                searchQuery: String,
            ): DriveFilesRepositoryResult {
                return loadMoreResultProvider()
            }

            override suspend fun refreshFolders(
                folderId: String?,
                searchQuery: String,
            ): DriveFoldersRepositoryResult {
                return refreshFoldersResultProvider?.invoke() ?: refreshFoldersResult
            }

            override suspend fun loadMoreFolders(
                currentFolders: List<DriveFolder>,
                folderId: String?,
                searchQuery: String,
            ): DriveFoldersRepositoryResult {
                return loadMoreFoldersResultProvider()
            }

            override suspend fun upload(upload: DriveFileUpload): DriveFileRepositoryResult {
                onUpload(upload)
                return uploadResultProvider()
            }

            override suspend fun updateFile(
                fileId: String,
                name: String?,
                comment: String?,
                isSensitive: Boolean?,
                folderId: String?,
            ): DriveManagementRepositoryResult {
                return updateFileResult
            }

            override suspend fun deleteFile(fileId: String): DriveManagementRepositoryResult {
                return deleteFileResult
            }

            override suspend fun createFolder(
                name: String,
                parentId: String?,
            ): DriveManagementRepositoryResult {
                return createFolderResult
            }

            override suspend fun updateFolder(
                folderId: String,
                name: String?,
                parentId: String?,
            ): DriveManagementRepositoryResult {
                return updateFolderResult
            }

            override suspend fun deleteFolder(folderId: String): DriveManagementRepositoryResult {
                return deleteFolderResult
            }

            override suspend fun loadFileDetails(fileId: String): DriveFileDetailsRepositoryResult {
                return fileDetailsResultProvider(fileId)
            }
        }
    }

    private fun sampleFile(id: String): DriveFile {
        return DriveFile(
            id = id,
            name = "$id.png",
            type = "image/png",
            url = "https://dc.hhhl.cc/files/$id.png",
            thumbnailUrl = null,
            comment = null,
            size = 1024,
            isSensitive = false,
            createdAtLabel = "2026-05-25 03:00",
        )
    }

    private fun sampleFolder(
        id: String,
        parentId: String? = null,
    ): DriveFolder {
        return DriveFolder(
            id = id,
            name = "Folder $id",
            parentId = parentId,
            foldersCount = 0,
            filesCount = 1,
            createdAtLabel = "2026-05-25 04:00",
        )
    }

    private fun sampleUpload(): DriveFileUpload {
        return DriveFileUpload(
            bytes = byteArrayOf(1, 2, 3),
            fileName = "photo.png",
            contentType = "image/png",
        )
    }

    private data class QueryCall(
        val folderId: String?,
        val sort: DriveFileSort,
        val query: String,
    )
}
