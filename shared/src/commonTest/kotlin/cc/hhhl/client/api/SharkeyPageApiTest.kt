package cc.hhhl.client.api

import cc.hhhl.client.model.PageListKind
import cc.hhhl.client.model.PageDraft
import cc.hhhl.client.model.PageVisibility
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

class SharkeyPageApiTest {
    @Test
    fun loadFeaturedPagesPostsToPagesFeaturedEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPages()
            },
        )

        val result = api.loadPages("token-123", PageListKind.Featured, limit = 20)

        assertIs<PageLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/pages/featured", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val page = result.pages.single()
        assertEquals("page-1", page.id)
        assertEquals("HHHL 指南", page.title)
        assertEquals("guide", page.name)
        assertEquals("Alice", page.author.displayName)
        assertEquals("欢迎来到 HHHL", page.summary)
        assertEquals(2, page.blocks.size)
        assertEquals("第一段", page.blocks.first().text)
        assertEquals(4, page.likedCount)
        assertEquals(true, page.isLiked)
        assertEquals("2026-05-25 14:00", page.createdAtLabel)
        assertEquals("2026-05-25 15:00", page.updatedAtLabel)
    }

    @Test
    fun loadMyPagesUsesIPagesEndpointWithPagination() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPages()
            },
        )

        api.loadPages("token-123", PageListKind.Mine, limit = 20, untilId = "page-old")

        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/i/pages", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"page-old""""))
    }

    @Test
    fun showPagePostsPageIdToPagesShowEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPage()
            },
        )

        val result = api.showPage("token-123", pageId = "page-1")

        assertIs<PageShowResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/pages/show", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""pageId":"page-1""""))
        assertEquals("page-1", result.page.id)
    }

    @Test
    fun showPageByPathPostsUsernameAndNameToPagesShowEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPage()
            },
        )

        val result = api.showPageByPath("token-123", username = "@alice", name = "guide")

        assertIs<PageShowResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/pages/show", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""username":"alice""""))
        assertTrue(body.contains(""""name":"guide""""))
        assertTrue(!body.contains(""""pageId""""))
        assertEquals("page-1", result.page.id)
    }

    @Test
    fun likesAndUnlikesPageUsingPageId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""pageId":"page-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<PageActionResult.Success>(api.likePage("token-123", pageId = "page-1"))
        assertIs<PageActionResult.Success>(api.unlikePage("token-123", pageId = "page-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/pages/like",
                "https://dc.hhhl.cc/api/pages/unlike",
            ),
            paths,
        )
    }

    @Test
    fun createUpdateAndDeletePageUsePageEndpoints() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val api = SharkeyPageApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                calls.add(request.url.toString() to (request.body as TextContent).text)
                if (request.url.encodedPath.endsWith("/delete")) {
                    respond(content = "", status = HttpStatusCode.NoContent)
                } else {
                    respondPage()
                }
            },
        )
        val draft = PageDraft(
            title = "Title",
            name = "title",
            summary = "Summary",
            content = "第一段\n\n第二段",
            visibility = PageVisibility.Followers,
            fileIds = listOf("file-1"),
        )

        assertIs<PageMutationResult.Success>(api.createPage("token-123", draft))
        assertIs<PageMutationResult.Success>(api.updatePage("token-123", "page-1", draft))
        assertIs<PageActionResult.Success>(api.deletePage("token-123", "page-1"))

        assertEquals("https://dc.hhhl.cc/api/pages/create", calls[0].first)
        assertTrue(calls[0].second.contains(""""title":"Title""""))
        assertTrue(calls[0].second.contains(""""name":"title""""))
        assertTrue(calls[0].second.contains(""""summary":"Summary""""))
        assertTrue(calls[0].second.contains(""""visibility":"followers""""))
        assertTrue(calls[0].second.contains(""""fileIds":["file-1"]"""))
        assertEquals("https://dc.hhhl.cc/api/pages/update", calls[1].first)
        assertTrue(calls[1].second.contains(""""pageId":"page-1""""))
        assertEquals("https://dc.hhhl.cc/api/pages/delete", calls[2].first)
        assertTrue(calls[2].second.contains(""""pageId":"page-1""""))
    }


    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyPageApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<PageLoadResult.Unauthorized>(api.loadPages("expired", PageListKind.Featured, limit = 20))
        assertIs<PageShowResult.Unauthorized>(api.showPage("expired", pageId = "page-1"))
        assertIs<PageShowResult.Unauthorized>(api.showPageByPath("expired", username = "alice", name = "guide"))
        assertIs<PageMutationResult.Unauthorized>(
            api.createPage("expired", PageDraft(title = "Title", name = "title")),
        )
    }

    @Test
    fun blankPageIdActionDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyPageApi(
            client = testClient {
                error("network should not be called for blank page id")
            },
        )

        assertIs<PageActionResult.ServerError>(api.likePage("token-123", " "))
    }

    private fun MockRequestHandleScope.respondPages(): HttpResponseData {
        return respond(
            content = "[${pageJson()}]",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondPage(): HttpResponseData {
        return respond(
            content = pageJson(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun pageJson(): String {
        return """
            {
              "id": "page-1",
              "createdAt": "2026-05-25T06:00:00.000Z",
              "updatedAt": "2026-05-25T07:00:00.000Z",
              "userId": "user-1",
              "user": {
                "id": "user-1",
                "username": "alice",
                "name": "Alice",
                "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
              },
              "content": [
                {"id": "block-1", "type": "text", "text": "第一段"},
                {"id": "block-2", "type": "section", "title": "章节", "children": []}
              ],
              "variables": [],
              "title": "HHHL 指南",
              "name": "guide",
              "summary": "欢迎来到 HHHL",
              "hideTitleWhenPinned": false,
              "alignCenter": false,
              "font": "serif",
              "script": "",
              "eyeCatchingImageId": null,
              "eyeCatchingImage": null,
              "attachedFiles": [],
              "likedCount": 4,
              "isLiked": true
            }
        """.trimIndent()
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
