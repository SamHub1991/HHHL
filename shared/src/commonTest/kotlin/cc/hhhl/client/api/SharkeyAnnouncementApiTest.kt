package cc.hhhl.client.api

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

class SharkeyAnnouncementApiTest {
    @Test
    fun loadAnnouncementsPostsToAnnouncementsEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAnnouncementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAnnouncements()
            },
        )

        val result = api.loadAnnouncements("token-123", limit = 20, untilId = "ann-old")

        assertIs<AnnouncementLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/announcements", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"ann-old""""))
        assertTrue(body.contains(""""isActive":true"""))
        val announcement = result.announcements.single()
        assertEquals("ann-1", announcement.id)
        assertEquals("维护通知", announcement.title)
        assertEquals("今晚维护", announcement.text)
        assertEquals("warning", announcement.icon)
        assertEquals("banner", announcement.display)
        assertEquals(true, announcement.needConfirmationToRead)
        assertEquals(false, announcement.isRead)
        assertEquals("2026-05-25 14:00", announcement.createdAtLabel)
    }

    @Test
    fun showAnnouncementPostsIdToShowEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAnnouncementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAnnouncement()
            },
        )

        val result = api.showAnnouncement("token-123", announcementId = "ann-1")

        assertIs<AnnouncementShowResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/announcements/show", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""announcementId":"ann-1""""))
        assertEquals("ann-1", result.announcement.id)
    }

    @Test
    fun markReadPostsAnnouncementIdToReadEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAnnouncementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(
                    content = "null",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.markRead("token-123", announcementId = "ann-1")

        assertIs<AnnouncementReadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/i/read-announcement", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""announcementId":"ann-1""""))
    }

    @Test
    fun adminListPostsToAdminAnnouncementListEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyAnnouncementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondAnnouncements()
            },
        )

        val result = api.loadAdminAnnouncements("token-123", limit = 50)

        assertIs<AnnouncementAdminLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/admin/announcements/list", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":50"""))
    }

    @Test
    fun adminCreateUpdateDeletePostToAdminAnnouncementEndpoints() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val api = SharkeyAnnouncementApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                requests.add(request)
                if (request.url.encodedPath.endsWith("/delete")) {
                    respond(content = "null", status = HttpStatusCode.OK, headers = jsonHeaders)
                } else {
                    respondAnnouncement()
                }
            },
        )

        assertIs<AnnouncementMutationResult.Success>(
            api.createAnnouncement("token-123", "标题", "内容", "warning", "banner"),
        )
        assertIs<AnnouncementMutationResult.Success>(
            api.updateAnnouncement("token-123", "ann-1", "标题", "内容", "warning", "banner"),
        )
        assertIs<AnnouncementDeleteResult.Success>(api.deleteAnnouncement("token-123", "ann-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/admin/announcements/create",
                "https://dc.hhhl.cc/api/admin/announcements/update",
                "https://dc.hhhl.cc/api/admin/announcements/delete",
            ),
            requests.map { it.url.toString() },
        )
        val updateBody = (requests[1].body as TextContent).text
        assertTrue(updateBody.contains(""""i":"token-123""""))
        assertTrue(updateBody.contains(""""id":"ann-1""""))
        assertTrue(updateBody.contains(""""title":"标题""""))
        assertTrue(updateBody.contains(""""text":"内容""""))
        assertTrue(updateBody.contains(""""icon":"warning""""))
        assertTrue(updateBody.contains(""""display":"banner""""))
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyAnnouncementApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<AnnouncementLoadResult.Unauthorized>(api.loadAnnouncements("expired", 20))
        assertIs<AnnouncementShowResult.Unauthorized>(api.showAnnouncement("expired", announcementId = "ann-1"))
        assertIs<AnnouncementReadResult.Unauthorized>(api.markRead("expired", announcementId = "ann-1"))
        assertIs<AnnouncementAdminLoadResult.Unauthorized>(api.loadAdminAnnouncements("expired", 50))
        assertIs<AnnouncementMutationResult.Unauthorized>(
            api.createAnnouncement("expired", "标题", "内容", "info", "normal"),
        )
        assertIs<AnnouncementDeleteResult.Unauthorized>(api.deleteAnnouncement("expired", announcementId = "ann-1"))
    }

    @Test
    fun mapsAdminForbiddenToPermissionErrorInsteadOfUnauthorized() = runTest {
        val api = SharkeyAnnouncementApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint."}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        val listResult = api.loadAdminAnnouncements("token-123", 50)
        val createResult = api.createAnnouncement("token-123", "标题", "内容", "info", "normal")
        val deleteResult = api.deleteAnnouncement("token-123", announcementId = "ann-1")

        assertEquals(
            "当前登录缺少此功能权限，请检查应用授权或账号权限",
            assertIs<AnnouncementAdminLoadResult.ServerError>(listResult).message,
        )
        assertEquals(
            "当前登录缺少此功能权限，请检查应用授权或账号权限",
            assertIs<AnnouncementMutationResult.ServerError>(createResult).message,
        )
        assertEquals(
            "当前登录缺少此功能权限，请检查应用授权或账号权限",
            assertIs<AnnouncementDeleteResult.ServerError>(deleteResult).message,
        )
    }

    private fun MockRequestHandleScope.respondAnnouncements(): HttpResponseData {
        return respond(
            content = "[${announcementJson()}]",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondAnnouncement(): HttpResponseData {
        return respond(
            content = announcementJson(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun announcementJson(): String {
        return """
            {
              "id": "ann-1",
              "createdAt": "2026-05-25T06:00:00.000Z",
              "updatedAt": null,
              "title": "维护通知",
              "text": "今晚维护",
              "imageUrl": "https://dc.hhhl.cc/files/maintenance.webp",
              "icon": "warning",
              "display": "banner",
              "needConfirmationToRead": true,
              "silence": false,
              "confetti": false,
              "forYou": true,
              "isRead": false
            }
        """.trimIndent()
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
