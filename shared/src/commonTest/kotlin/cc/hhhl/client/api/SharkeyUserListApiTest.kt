package cc.hhhl.client.api

import cc.hhhl.client.model.UserListDraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class SharkeyUserListApiTest {
    @Test
    fun loadListsPostsJsonToUsersListsListEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondUserLists()
            },
        )

        val result = api.loadLists(token = "token-123")

        assertIs<UserListLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/list", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val list = result.lists.single()
        assertEquals("list-1", list.id)
        assertEquals("朋友", list.name)
        assertEquals(2, list.memberCount)
        assertEquals(true, list.isPublic)
        assertEquals("2026-05-25 14:00", list.createdAtLabel)
    }

    @Test
    fun loadListTimelinePostsJsonToNotesUserListTimelineEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondNotes()
            },
        )

        val result = api.loadListTimeline(
            token = "token-123",
            listId = "list-1",
            limit = 20,
            untilId = "note-old",
            withRenotes = false,
            withFiles = true,
        )

        assertIs<UserListTimelineLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/notes/user-list-timeline", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""listId":"list-1""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"note-old""""))
        assertTrue(body.contains(""""withRenotes":false"""))
        assertTrue(body.contains(""""withFiles":true"""))
        assertEquals("note-1", result.notes.single().id)
    }

    @Test
    fun createListPostsDraftToCreateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondUserList()
            },
        )

        val result = api.createList("token-123", UserListDraft(name = "朋友", isPublic = true))

        assertIs<UserListMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/create", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"朋友""""))
        assertTrue(body.contains(""""isPublic":true"""))
        assertEquals("list-1", result.list.id)
    }

    @Test
    fun updateListPostsDraftToUpdateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondUserList()
            },
        )

        val result = api.updateList(
            token = "token-123",
            listId = "list-1",
            draft = UserListDraft(name = "同事", isPublic = false),
        )

        assertIs<UserListMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/update", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""listId":"list-1""""))
        assertTrue(body.contains(""""name":"同事""""))
        assertTrue(body.contains(""""isPublic":false"""))
    }

    @Test
    fun deleteListPostsListIdToDeleteEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.deleteList("token-123", "list-1")

        assertIs<UserListActionResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/delete", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""listId":"list-1""""))
    }

    @Test
    fun pushUserToListPostsJsonToUsersListsPushEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.pushUser(token = "token-123", listId = "list-1", userId = "user-3")

        assertIs<UserListActionResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/push", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""listId":"list-1""""))
        assertTrue(body.contains(""""userId":"user-3""""))
    }

    @Test
    fun pullUserFromListPostsJsonToUsersListsPullEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyUserListApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        val result = api.pullUser(token = "token-123", listId = "list-1", userId = "user-2")

        assertIs<UserListActionResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/users/lists/pull", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""listId":"list-1""""))
        assertTrue(body.contains(""""userId":"user-2""""))
    }

    @Test
    fun blankListOrUserIdsDoNotMapToUnauthorized() = runTest {
        val api = SharkeyUserListApi(
            client = testClient {
                error("network should not be called for blank local ids")
            },
        )

        val timelineResult = api.loadListTimeline("token-123", listId = " ", limit = 20)
        val updateResult = api.updateList(
            token = "token-123",
            listId = " ",
            draft = UserListDraft(name = "同事", isPublic = false),
        )
        val deleteResult = api.deleteList("token-123", listId = " ")
        val pushResult = api.pushUser("token-123", listId = "list-1", userId = " ")

        assertIs<UserListTimelineLoadResult.ServerError>(timelineResult)
        assertIs<UserListMutationResult.ServerError>(updateResult)
        assertIs<UserListActionResult.ServerError>(deleteResult)
        assertIs<UserListActionResult.ServerError>(pushResult)
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyUserListApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<UserListLoadResult.Unauthorized>(api.loadLists("expired"))
        assertIs<UserListTimelineLoadResult.Unauthorized>(
            api.loadListTimeline("expired", listId = "list-1", limit = 20),
        )
    }

    private fun MockRequestHandleScope.respondUserLists(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "list-1",
                    "createdAt": "2026-05-25T06:00:00.000Z",
                    "createdBy": "user-me",
                    "name": "朋友",
                    "userIds": ["user-1", "user-2"],
                    "isPublic": true,
                    "isLiked": false,
                    "likedCount": 3
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondUserList(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "list-1",
                  "createdAt": "2026-05-25T06:00:00.000Z",
                  "createdBy": "user-me",
                  "name": "朋友",
                  "userIds": ["user-1", "user-2"],
                  "isPublic": true,
                  "isLiked": false,
                  "likedCount": 3
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondNotes(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T00:12:34.000Z",
                    "text": "hello from list",
                    "visibility": "home",
                    "renoteCount": 1,
                    "repliesCount": 0,
                    "reactions": {"👍": 2},
                    "files": [],
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice",
                      "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                    }
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
