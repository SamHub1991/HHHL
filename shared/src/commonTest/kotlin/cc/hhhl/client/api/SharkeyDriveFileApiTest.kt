package cc.hhhl.client.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyDriveFileApiTest {
    @Test
    fun loadFilesPostsJsonToDriveFilesEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFiles()
            },
        )

        val result = api.loadFiles(
            token = "token-123",
            folderId = "folder-1",
            limit = 30,
            untilId = "file-old",
            sort = DriveFileSort.NameDescending,
            searchQuery = "photo",
            showAll = false,
        )

        assertIs<DriveFileListResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/files", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""folderId":"folder-1""""))
        assertTrue(body.contains(""""limit":30"""))
        assertTrue(body.contains(""""untilId":"file-old""""))
        assertTrue(body.contains(""""sort":"-name""""))
        assertTrue(body.contains(""""searchQuery":"photo""""))
        assertTrue(body.contains(""""showAll":false"""))
        val file = result.files.single()
        assertEquals("file-1", file.id)
        assertEquals("photo.png", file.name)
        assertEquals("image/png", file.type)
        assertEquals("2026-05-25 11:00", file.createdAtLabel)
        assertEquals("folder-1", file.folderId)
    }

    @Test
    fun loadFilesMapsUnauthorizedResponse() = runTest {
        val api = SharkeyDriveFileApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<DriveFileListResult.Unauthorized>(
            api.loadFiles("expired", folderId = null, limit = 30),
        )
    }

    @Test
    fun loadFoldersPostsJsonToDriveFoldersEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFolders()
            },
        )

        val result = api.loadFolders(
            token = "token-123",
            folderId = "parent-1",
            limit = 30,
            untilId = "folder-old",
            searchQuery = "assets",
        )

        assertIs<DriveFolderListResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/folders", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""folderId":"parent-1""""))
        assertTrue(body.contains(""""limit":30"""))
        assertTrue(body.contains(""""untilId":"folder-old""""))
        assertTrue(body.contains(""""searchQuery":"assets""""))
        val folder = result.folders.single()
        assertEquals("folder-1", folder.id)
        assertEquals("素材", folder.name)
        assertEquals("parent-1", folder.parentId)
        assertEquals(2, folder.foldersCount)
        assertEquals(5, folder.filesCount)
        assertEquals("2026-05-25 12:00", folder.createdAtLabel)
    }

    @Test
    fun updateFilePostsJsonToDriveFilesUpdateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFile(name = "renamed.png", isSensitive = true)
            },
        )

        val result = api.updateFile(
            token = "token-123",
            fileId = "file-1",
            name = "renamed.png",
            folderId = "folder-1",
            comment = "new alt",
            isSensitive = true,
        )

        assertIs<DriveFileMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/files/update", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""fileId":"file-1""""))
        assertTrue(body.contains(""""name":"renamed.png""""))
        assertTrue(body.contains(""""folderId":"folder-1""""))
        assertTrue(body.contains(""""comment":"new alt""""))
        assertTrue(body.contains(""""isSensitive":true"""))
        assertEquals("renamed.png", result.file.name)
        assertEquals(true, result.file.isSensitive)
    }

    @Test
    fun moveFileToRootPostsExplicitNullFolderId() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFile()
            },
        )

        val result = api.moveFile(
            token = "token-123",
            fileId = "file-1",
            folderId = null,
        )

        assertIs<DriveFileMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/files/update", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""fileId":"file-1""""))
        assertTrue(body.contains(""""folderId":null"""))
        assertFalse(body.contains(""""name""""))
    }

    @Test
    fun deleteFilePostsJsonToDriveFilesDeleteEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.deleteFile("token-123", "file-1")

        assertIs<DriveFileMutationResult.Deleted>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/files/delete", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""fileId":"file-1""""))
    }

    @Test
    fun createFolderPostsJsonToDriveFoldersCreateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFolder(name = "新文件夹", parentId = "parent-1")
            },
        )

        val result = api.createFolder(
            token = "token-123",
            name = "新文件夹",
            parentId = "parent-1",
        )

        assertIs<DriveFolderMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/folders/create", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"新文件夹""""))
        assertTrue(body.contains(""""parentId":"parent-1""""))
        assertEquals("新文件夹", result.folder.name)
        assertEquals("parent-1", result.folder.parentId)
    }

    @Test
    fun updateFolderPostsJsonToDriveFoldersUpdateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFolder(name = "改名文件夹", parentId = null)
            },
        )

        val result = api.updateFolder(
            token = "token-123",
            folderId = "folder-1",
            name = "改名文件夹",
            parentId = null,
        )

        assertIs<DriveFolderMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/folders/update", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""folderId":"folder-1""""))
        assertTrue(body.contains(""""name":"改名文件夹""""))
        assertEquals("改名文件夹", result.folder.name)
    }

    @Test
    fun deleteFolderPostsJsonToDriveFoldersDeleteEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.deleteFolder("token-123", "folder-1")

        assertIs<DriveFolderMutationResult.Deleted>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/folders/delete", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""folderId":"folder-1""""))
    }

    @Test
    fun uploadFilePostsMultipartToDriveFilesCreate() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondDriveFile()
            },
        )

        val result = api.uploadFile(
            token = "token-123",
            upload = DriveFileUpload(
                bytes = byteArrayOf(1, 2, 3),
                fileName = "photo.png",
                contentType = "image/png",
                comment = "alt text",
                isSensitive = true,
                force = true,
            ),
        )

        assertIs<DriveFileUploadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/drive/files/create", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.MultiPart.FormData, request.body.contentType?.withoutParameters())
        assertTrue(request.body is MultiPartFormDataContent)
        assertEquals("file-1", result.file.id)
        assertEquals("photo.png", result.file.name)
        assertEquals("image/png", result.file.type)
        assertEquals("https://dc.hhhl.cc/files/photo.png", result.file.url)
        assertEquals("alt text", result.file.comment)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingNetwork() = runTest {
        var calls = 0
        val api = SharkeyDriveFileApi(
            client = testClient {
                calls += 1
                respondDriveFile()
            },
        )

        val result = api.uploadFile(
            token = " ",
            upload = DriveFileUpload(
                bytes = byteArrayOf(1),
                fileName = "photo.png",
                contentType = "image/png",
            ),
        )

        assertIs<DriveFileUploadResult.Unauthorized>(result)
        assertEquals(0, calls)
    }

    @Test
    fun blankDriveItemIdsDoNotCallNetworkOrMapToUnauthorized() = runTest {
        var calls = 0
        val api = SharkeyDriveFileApi(
            client = testClient {
                calls += 1
                respondDriveFile()
            },
        )

        assertIs<DriveFileMutationResult.ServerError>(
            api.updateFile("token-123", fileId = " ", name = "x", folderId = null, comment = null, isSensitive = null),
        )
        assertIs<DriveFileMutationResult.ServerError>(
            api.deleteFile("token-123", fileId = " "),
        )
        assertIs<DriveFolderMutationResult.ServerError>(
            api.updateFolder("token-123", folderId = " ", name = "x", parentId = null),
        )
        assertIs<DriveFolderMutationResult.ServerError>(
            api.deleteFolder("token-123", folderId = " "),
        )
        assertEquals(0, calls)
    }

    @Test
    fun mapsServerErrorMessageFromErrorEnvelope() = runTest {
        val api = SharkeyDriveFileApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Cannot upload the file because you have no free space of drive."}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.uploadFile(
            token = "token-123",
            upload = DriveFileUpload(
                bytes = byteArrayOf(1),
                fileName = "photo.png",
                contentType = "image/png",
            ),
        )

        assertIs<DriveFileUploadResult.ServerError>(result)
        assertEquals("Cannot upload the file because you have no free space of drive.", result.message)
    }

    @Test
    fun loadFileDetailsLoadsShowAndAttachedNotes() = runTest {
        val requestedUrls = mutableListOf<String>()
        val api = SharkeyDriveFileApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                requestedUrls += request.url.toString()
                when (request.url.toString()) {
                    "https://dc.hhhl.cc/api/drive/files/show" -> respondDriveFile(name = "details.png", isSensitive = false)
                    "https://dc.hhhl.cc/api/drive/files/attached-notes" -> respond(
                        content = """
                            [
                              {
                                "id":"note-1",
                                "createdAt":"2026-05-27T15:49:00.000Z",
                                "text":"used in note",
                                "user":{"id":"user-1","username":"alice","name":"Alice","avatarUrl":null},
                                "files":[]
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                    else -> error("Unexpected request ${request.url}")
                }
            },
        )

        val result = api.loadFileDetails("token-123", "file-1")

        assertIs<DriveFileDetailsResult.Success>(result)
        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/drive/files/show",
                "https://dc.hhhl.cc/api/drive/files/attached-notes",
            ),
            requestedUrls,
        )
        assertEquals("details.png", result.details.file.name)
        assertEquals(1, result.details.attachedNotes.size)
        assertEquals("note-1", result.details.attachedNotes.first().id)
    }

    private fun MockRequestHandleScope.respondDriveFile(
        name: String = "photo.png",
        isSensitive: Boolean = true,
    ): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "file-1",
                  "name": "$name",
                  "type": "image/png",
                  "url": "https://dc.hhhl.cc/files/photo.png",
                  "thumbnailUrl": "https://dc.hhhl.cc/thumb/photo.png",
                  "comment": "alt text",
                  "size": 3,
                  "isSensitive": $isSensitive
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondDriveFiles(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "file-1",
                    "createdAt": "2026-05-25T03:00:00.000Z",
                    "name": "photo.png",
                    "type": "image/png",
                    "url": "https://dc.hhhl.cc/files/photo.png",
                    "thumbnailUrl": "https://dc.hhhl.cc/thumb/photo.png",
                    "comment": "alt text",
                    "size": 2048,
                    "isSensitive": false,
                    "folderId": "folder-1"
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondDriveFolder(
        name: String,
        parentId: String?,
    ): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "folder-1",
                  "createdAt": "2026-05-25T04:00:00.000Z",
                  "name": "$name",
                  "parentId": ${parentId?.let { "\"$it\"" } ?: "null"},
                  "foldersCount": 0,
                  "filesCount": 0,
                  "parent": null
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondDriveFolders(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "folder-1",
                    "createdAt": "2026-05-25T04:00:00.000Z",
                    "name": "素材",
                    "parentId": "parent-1",
                    "foldersCount": 2,
                    "filesCount": 5,
                    "parent": null
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
