package cc.hhhl.client.api

import cc.hhhl.client.model.ChannelListKind
import cc.hhhl.client.model.ChannelDraft
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

class SharkeyChannelApiTest {
    @Test
    fun loadChannelCategoriesPostsJsonToCategoriesEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respond(
                    content = """[{"category":"AI与大模型","channelsCount":13}]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.loadChannelCategories()

        assertIs<ChannelCategoryLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/categories", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("AI与大模型", result.categories.single().name)
        assertEquals(13, result.categories.single().channelsCount)
    }

    @Test
    fun loadFeaturedChannelsPostsJsonToFeaturedEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannels()
            },
        )

        val result = api.loadChannels(
            token = "token-123",
            kind = ChannelListKind.Featured,
            limit = 20,
        )

        assertIs<ChannelLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/featured", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val channel = result.channels.single()
        assertEquals("channel-1", channel.id)
        assertEquals("公告频道", channel.name)
        assertEquals("站内公告", channel.description)
        assertEquals("#40c057", channel.color)
        assertEquals(4, channel.usersCount)
        assertEquals(12, channel.notesCount)
        assertEquals(false, channel.isArchived)
        assertEquals(true, channel.isFollowing)
        assertEquals(true, channel.hasUnreadNote)
        assertEquals("AI与大模型", channel.category)
        assertEquals("2026-05-25 15:00", channel.createdAtLabel)
        assertEquals("2026-05-25 16:00", channel.lastNotedAtLabel)
        assertEquals("pinned-1", channel.pinnedNotes.single().id)
    }

    @Test
    fun loadChannelsByCategoryPostsCategoryAndPagination() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannels()
            },
        )

        val result = api.loadChannelsByCategory(
            category = "AI与大模型",
            uncategorized = false,
            limit = 80,
            offset = 12,
        )

        assertIs<ChannelLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/by-category", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""category":"AI与大模型""""))
        assertTrue(body.contains(""""uncategorized":false"""))
        assertTrue(body.contains(""""limit":50"""))
        assertTrue(body.contains(""""offset":12"""))
    }

    @Test
    fun loadChannelsByCategoryCanRequestUncategorizedChannels() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannels()
            },
        )

        assertIs<ChannelLoadResult.Success>(
            api.loadChannelsByCategory(category = null, uncategorized = true, limit = 20, offset = -1),
        )

        val body = (checkNotNull(capturedRequest).body as TextContent).text
        assertTrue(body.contains(""""uncategorized":true"""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""offset":0"""))
    }

    @Test
    fun loadChannelKindsUseExpectedEndpoints() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                respondChannels()
            },
        )

        api.loadChannels("token-123", ChannelListKind.Followed, limit = 20, untilId = "channel-old")
        api.loadChannels("token-123", ChannelListKind.Favorites, limit = 20)
        api.loadChannels("token-123", ChannelListKind.Owned, limit = 20, untilId = "channel-old")

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/channels/followed",
                "https://dc.hhhl.cc/api/channels/my-favorites",
                "https://dc.hhhl.cc/api/channels/owned",
            ),
            paths,
        )
    }

    @Test
    fun loadFollowedChannelsIncludesPaginationBody() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannels()
            },
        )

        api.loadChannels("token-123", ChannelListKind.Followed, limit = 20, untilId = "channel-old")

        val body = (checkNotNull(capturedRequest).body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"channel-old""""))
    }

    @Test
    fun loadChannelTimelinePostsJsonToChannelsTimelineEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondNotes()
            },
        )

        val result = api.loadChannelTimeline(
            token = "token-123",
            channelId = "channel-1",
            limit = 20,
            untilId = "note-old",
            withRenotes = false,
            withFiles = true,
        )

        assertIs<ChannelTimelineLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/timeline", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""channelId":"channel-1""""))
        assertTrue(body.contains(""""limit":20"""))
        assertTrue(body.contains(""""untilId":"note-old""""))
        assertTrue(body.contains(""""withRenotes":false"""))
        assertTrue(body.contains(""""withFiles":true"""))
        assertEquals("note-1", result.notes.single().id)
    }

    @Test
    fun followsAndUnfollowsChannelUsingChannelId() = runTest {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                bodies.add((request.body as TextContent).text)
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<ChannelActionResult.Success>(api.followChannel("token-123", "channel-1"))
        assertIs<ChannelActionResult.Success>(api.unfollowChannel("token-123", "channel-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/channels/follow",
                "https://dc.hhhl.cc/api/channels/unfollow",
            ),
            paths,
        )
        assertTrue(bodies.all { it.contains(""""i":"token-123"""") })
        assertTrue(bodies.all { it.contains(""""channelId":"channel-1"""") })
    }

    @Test
    fun favoritesAndUnfavoritesChannelUsingChannelId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""channelId":"channel-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<ChannelActionResult.Success>(api.favoriteChannel("token-123", "channel-1"))
        assertIs<ChannelActionResult.Success>(api.unfavoriteChannel("token-123", "channel-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/channels/favorite",
                "https://dc.hhhl.cc/api/channels/unfavorite",
            ),
            paths,
        )
    }

    @Test
    fun createChannelPostsDraftToCreateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannel()
            },
        )

        val result = api.createChannel(
            token = "token-123",
            draft = ChannelDraft(
                name = " 新频道 ",
                description = "讨论 AGI",
                color = "#40c057",
                isSensitive = true,
                allowRenoteToExternal = false,
                category = "AI与大模型",
            ),
        )

        assertIs<ChannelMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/create", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""name":"新频道""""))
        assertTrue(body.contains(""""description":"讨论 AGI""""))
        assertTrue(body.contains(""""color":"#40c057""""))
        assertTrue(body.contains(""""isSensitive":true"""))
        assertTrue(body.contains(""""allowRenoteToExternal":false"""))
        assertTrue(body.contains(""""category":"AI与大模型""""))
        assertEquals("channel-1", result.channel.id)
    }

    @Test
    fun updateChannelPostsDraftWithChannelIdToUpdateEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyChannelApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondChannel()
            },
        )

        val result = api.updateChannel(
            token = "token-123",
            channelId = "channel-1",
            draft = ChannelDraft(
                name = "频道改名",
                description = "更新描述",
                color = "#228be6",
                isArchived = true,
                category = "编程开发",
            ),
        )

        assertIs<ChannelMutationResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/channels/update", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""channelId":"channel-1""""))
        assertTrue(body.contains(""""name":"频道改名""""))
        assertTrue(body.contains(""""description":"更新描述""""))
        assertTrue(body.contains(""""color":"#228be6""""))
        assertTrue(body.contains(""""isArchived":true"""))
        assertTrue(body.contains(""""category":"编程开发""""))
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyChannelApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<ChannelLoadResult.Unauthorized>(
            api.loadChannels("expired", ChannelListKind.Featured, limit = 20),
        )
        assertIs<ChannelTimelineLoadResult.Unauthorized>(
            api.loadChannelTimeline("expired", channelId = "channel-1", limit = 20),
        )
    }

    @Test
    fun mapsForbiddenChannelActionToServerErrorInsteadOfRelogin() = runTest {
        val api = SharkeyChannelApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Channel action is not allowed"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        assertEquals(
            ChannelActionResult.ServerError(
                statusCode = 403,
                message = "Channel action is not allowed",
            ),
            api.followChannel("token-123", "channel-1"),
        )
    }

    @Test
    fun blankChannelIdDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyChannelApi(
            client = testClient {
                error("network should not be called for blank channel id")
            },
        )

        val actionResult = api.followChannel("token-123", " ")
        val updateResult = api.updateChannel(
            token = "token-123",
            channelId = " ",
            draft = ChannelDraft(name = "频道", description = "", color = "#40c057"),
        )

        assertIs<ChannelActionResult.ServerError>(actionResult)
        assertIs<ChannelMutationResult.ServerError>(updateResult)
    }

    private fun MockRequestHandleScope.respondChannels(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "channel-1",
                    "createdAt": "2026-05-25T07:00:00.000Z",
                    "lastNotedAt": "2026-05-25T08:00:00.000Z",
                    "name": "公告频道",
                    "description": "站内公告",
                    "userId": "user-1",
                    "bannerUrl": "https://dc.hhhl.cc/banner.webp",
                    "pinnedNoteIds": ["pinned-1"],
                    "color": "#40c057",
                    "isArchived": false,
                    "usersCount": 4,
                    "notesCount": 12,
                    "isSensitive": false,
                    "allowRenoteToExternal": true,
                    "category": "AI与大模型",
                    "isFollowing": true,
                    "isFavorited": false,
                    "hasUnreadNote": true,
                    "pinnedNotes": [
                      {
                        "id": "pinned-1",
                        "createdAt": "2026-05-25T00:12:34.000Z",
                        "text": "pinned",
                        "visibility": "public",
                        "renoteCount": 0,
                        "repliesCount": 0,
                        "reactions": {},
                        "files": [],
                        "user": {
                          "id": "user-1",
                          "username": "alice",
                          "name": "Alice",
                          "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
                        }
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondChannel(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "channel-1",
                  "createdAt": "2026-05-25T07:00:00.000Z",
                  "lastNotedAt": "2026-05-25T08:00:00.000Z",
                  "name": "公告频道",
                  "description": "站内公告",
                  "userId": "user-1",
                  "bannerUrl": "https://dc.hhhl.cc/banner.webp",
                  "pinnedNoteIds": [],
                  "color": "#40c057",
                  "isArchived": false,
                  "usersCount": 4,
                  "notesCount": 12,
                  "isSensitive": false,
                  "allowRenoteToExternal": true,
                  "category": "AI与大模型",
                  "isFollowing": true,
                  "isFavorited": false,
                  "hasUnreadNote": false,
                  "pinnedNotes": []
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
                    "text": "hello from channel",
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
