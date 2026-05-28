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

class SharkeyDiscoverApiTest {
    @Test
    fun searchesNotesFromNotesSearchEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/search", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""query":"Sharkey""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"note-old""""))

                respondNoteArray()
            },
        )

        val result = api.searchNotes(
            token = "token-123",
            query = "Sharkey",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<DiscoverSearchResult.Success>(result)
        assertEquals("note-1", result.notes.single().id)
        assertEquals("search result", result.notes.single().text)
    }

    @Test
    fun mapsAdvancedNoteSearchOptionsToRequestBody() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""query":"Sharkey from:@alice""""))
                assertTrue(body.contains(""""origin":"remote""""))
                assertTrue(body.contains(""""userId":"user-1""""))
                assertTrue(body.contains(""""username":"alice""""))
                assertTrue(body.contains(""""host":"example.social""""))
                assertTrue(body.contains(""""channelId":"channel-1""""))
                assertTrue(body.contains(""""sinceDate":"2026-05-01""""))
                assertTrue(body.contains(""""untilDate":"2026-05-26""""))
                assertTrue(body.contains(""""withFiles":true"""))
                assertTrue(body.contains(""""includeReplies":false"""))

                respondNoteArray()
            },
        )

        val result = api.searchNotes(
            token = "token-123",
            query = "Sharkey from:@alice",
            limit = 20,
            options = DiscoverNoteSearchOptions(
                origin = "remote",
                username = "alice",
                userId = "user-1",
                host = "example.social",
                channelId = "channel-1",
                sinceDate = "2026-05-01",
                untilDate = "2026-05-26",
                withFiles = true,
                includeReplies = false,
            ),
        )

        assertIs<DiscoverSearchResult.Success>(result)
    }

    @Test
    fun mapsUnauthorizedSearchResponse() = runTest {
        val api = SharkeyDiscoverApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"invalid token"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<DiscoverSearchResult.Unauthorized>(
            api.searchNotes("expired", "Sharkey", 20),
        )
    }

    @Test
    fun mapsUnavailableSearchErrorMessage() = runTest {
        val api = SharkeyDiscoverApi(
            client = testClient {
                respond(
                    content = """
                        {
                          "error": {
                            "message": "Search of notes unavailable.",
                            "code": "UNAVAILABLE"
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.searchNotes("token-123", "Sharkey", 20)

        assertIs<DiscoverSearchResult.ServerError>(result)
        assertEquals("Search of notes unavailable.", result.message)
    }

    @Test
    fun loadsRolesWithoutJsonPayload() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/roles/list", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertTrue(request.body !is TextContent)
                respondRoleArray()
            },
        )

        val result = api.loadRoles("")

        assertIs<DiscoverRoleResult.Success>(result)
        assertEquals("role-1", result.roles.single().id)
        assertEquals("Supporter", result.roles.single().name)
    }

    @Test
    fun searchesNotesByTagFromTagSearchEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/notes/search-by-tag", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""tag":"签到""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"note-old""""))

                respondNoteArray()
            },
        )

        val result = api.searchNotesByTag(
            token = "token-123",
            tag = "#签到",
            limit = 20,
            untilId = "note-old",
        )

        assertIs<DiscoverSearchResult.Success>(result)
        assertEquals("note-1", result.notes.single().id)
    }

    @Test
    fun tagSearchCanRunWithoutToken() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                val body = (request.body as TextContent).text
                assertTrue(!body.contains(""""i":"""))
                assertTrue(body.contains(""""tag":"签到""""))

                respondNoteArray()
            },
        )

        val result = api.searchNotesByTag(
            token = null,
            tag = "签到",
            limit = 20,
        )

        assertIs<DiscoverSearchResult.Success>(result)
    }

    @Test
    fun searchesUsersFromUsersSearchEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/users/search", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""query":"Alice""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""origin":"remote""""))

                respondUserArray()
            },
        )

        val result = api.searchUsers(
            token = "token-123",
            query = "Alice",
            limit = 20,
            origin = "remote",
        )

        assertIs<DiscoverUserSearchResult.Success>(result)
        val user = result.users.single()
        assertEquals("user-1", user.id)
        assertEquals("Alice", user.displayName)
        assertEquals("alice", user.username)
        assertEquals("remote.example", user.host)
        assertEquals("bio text", user.bio)
    }

    @Test
    fun loadsTrendingHashtagsFromHashtagsTrendEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/hashtags/trend", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                assertEquals("{}", (request.body as TextContent).text)
                respondTrendArray()
            },
        )

        val result = api.loadTrendingHashtags()

        assertIs<DiscoverTrendResult.Success>(result)
        val trend = result.trends.single()
        assertEquals("AI", trend.tag)
        assertEquals(12, trend.usersCount)
        assertEquals(listOf(1, 3, 8), trend.chart)
    }

    @Test
    fun searchesHashtagsFromStringArrayEndpointWithoutToken() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/hashtags/search", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(!body.contains("\"i\""))
                assertTrue(body.contains("\"query\":\"AI\""))
                assertTrue(body.contains("\"limit\":20"))
                respond(
                    content = """["AI","Compose"]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.searchHashtags(token = "", query = "#AI", limit = 20)

        assertIs<DiscoverTrendResult.Success>(result)
        assertEquals(listOf("AI", "Compose"), result.trends.map { it.tag })
    }

    @Test
    fun loadsHashtagListsWithoutTokenPayload() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/hashtags/list", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(!body.contains("\"i\""))
                assertTrue(body.contains("\"sort\":\"+attachedUsers\""))
                respondTrendArray()
            },
        )

        assertIs<DiscoverTrendResult.Success>(api.loadHashtags(token = "", limit = 20))
    }

    @Test
    fun loadsFederationInstancesFromFederationInstancesEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/instances", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(ContentType.Application.Json, request.body.contentType)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""limit":30"""))
                assertTrue(body.contains(""""offset":10"""))
                assertTrue(body.contains(""""sort":"-users""""))
                assertTrue(body.contains(""""host":"example.social""""))
                respondFederationArray()
            },
        )

        val result = api.loadFederationInstances(limit = 30, offset = 10, host = "example.social")

        assertIs<DiscoverFederationResult.Success>(result)
        val instance = result.instances.single()
        assertEquals("instance-1", instance.id)
        assertEquals("example.social", instance.host)
        assertEquals("Example", instance.name)
        assertEquals("sharkey", instance.softwareName)
        assertEquals(120, instance.usersCount)
        assertEquals(false, instance.isBlocked)
    }

    @Test
    fun loadsFederationInstanceDetailsFromShowEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/show-instance", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""host":"example.social""""))
                respondFederationObject()
            },
        )

        val result = api.loadFederationInstance("example.social")

        assertIs<DiscoverFederationInstanceResult.Success>(result)
        assertEquals("example.social", result.instance.host)
        assertEquals("federated instance", result.instance.description)
        assertEquals("2026-05-25 08:00", result.instance.infoUpdatedAtLabel)
        assertEquals("2026-05-25 09:00", result.instance.latestRequestReceivedAtLabel)
    }

    @Test
    fun loadsFederationFollowersFromFollowersEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/followers", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""host":"example.social""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"follow-old""""))
                assertTrue(body.contains(""""includeFollower":true"""))
                assertTrue(body.contains(""""includeFollowee":true"""))
                respondFederationFollows()
            },
        )

        val result = api.loadFederationFollowers(
            host = "example.social",
            limit = 20,
            untilId = "follow-old",
            includeFollower = true,
            includeFollowee = true,
        )

        assertIs<DiscoverFederationFollowResult.Success>(result)
        val follow = result.follows.single()
        assertEquals("follow-1", follow.id)
        assertEquals("user-followee", follow.followeeId)
        assertEquals("user-follower", follow.followerId)
        assertEquals("Followee", follow.followee?.displayName)
        assertEquals("Follower", follow.follower?.displayName)
        assertEquals("2026-05-25 10:30", follow.createdAtLabel)
    }

    @Test
    fun loadsFederationFollowingFromFollowingEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/following", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""host":"example.social""""))
                assertTrue(body.contains(""""limit":20"""))
                respondFederationFollows()
            },
        )

        assertIs<DiscoverFederationFollowResult.Success>(
            api.loadFederationFollowing(host = "example.social", limit = 20),
        )
    }

    @Test
    fun loadsFederationUsersFromUsersEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/users", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""host":"example.social""""))
                assertTrue(body.contains(""""limit":20"""))
                assertTrue(body.contains(""""untilId":"user-old""""))
                respondUserArray()
            },
        )

        val result = api.loadFederationUsers(
            host = "example.social",
            limit = 20,
            untilId = "user-old",
        )

        assertIs<DiscoverUserSearchResult.Success>(result)
        assertEquals("user-1", result.users.single().id)
    }

    @Test
    fun loadsFederationStatsFromStatsEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/stats", request.url.toString())
                assertEquals("""{"limit":10}""", (request.body as TextContent).text)
                respondFederationStats()
            },
        )

        val result = api.loadFederationStats(limit = 10)

        assertIs<DiscoverFederationStatsResult.Success>(result)
        assertEquals(1, result.stats.topSubInstances.size)
        assertEquals(7, result.stats.otherFollowersCount)
        assertEquals(1, result.stats.topPubInstances.size)
        assertEquals(9, result.stats.otherFollowingCount)
    }

    @Test
    fun updatesFederationInstanceFromAdminEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/admin/federation/update-instance", request.url.toString())
                assertEquals(HttpMethod.Post, request.method)
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""host":"example.social""""))
                assertTrue(body.contains(""""isSilenced":true"""))
                assertTrue(body.contains(""""isSuspended":false"""))
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<DiscoverFederationActionResult.Success>(
            api.updateFederationInstance(
                token = "token-123",
                host = "example.social",
                isSilenced = true,
                isSuspended = false,
            ),
        )
    }

    @Test
    fun updatesRemoteUserFromFederationUpdateRemoteUserEndpoint() = runTest {
        val api = SharkeyDiscoverApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                assertEquals("https://dc.hhhl.cc/api/federation/update-remote-user", request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""userId":"user-remote""""))
                respond("", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<DiscoverFederationActionResult.Success>(
            api.updateRemoteUser(token = "token-123", userId = "user-remote"),
        )
    }

    @Test
    fun permissionDeniedFederationUpdateMapsToServerErrorInsteadOfUnauthorized() = runTest {
        val api = SharkeyDiscoverApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Your app does not have the necessary permissions to use this endpoint."}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            },
        )

        val result = api.updateFederationInstance(
            token = "token-123",
            host = "example.social",
            isSilenced = true,
            isSuspended = false,
        )

        val error = assertIs<DiscoverFederationActionResult.ServerError>(result)
        assertEquals("当前登录缺少此功能权限，请检查应用授权或账号权限", error.message)
    }

    private fun MockRequestHandleScope.respondNoteArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "note-1",
                    "createdAt": "2026-05-25T03:00:00.000Z",
                    "text": "search result",
                    "visibility": "public",
                    "user": {
                      "id": "user-1",
                      "username": "alice",
                      "name": "Alice"
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondUserArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "user-1",
                    "username": "alice",
                    "host": "remote.example",
                    "name": "Alice",
                    "description": "bio text",
                    "followersCount": 22,
                    "followingCount": 11,
                    "notesCount": 33,
                    "isFollowing": false
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondTrendArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "tag": "AI",
                    "chart": [1, 3, 8],
                    "usersCount": 12
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondRoleArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "role-1",
                    "name": "Supporter",
                    "description": "Public role",
                    "usersCount": 12,
                    "isPublic": true,
                    "isModerator": false,
                    "isAdministrator": false,
                    "color": "#ff8800"
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFederationArray(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "instance-1",
                    "firstRetrievedAt": "2026-05-25T00:00:00.000Z",
                    "host": "example.social",
                    "usersCount": 120,
                    "notesCount": 900,
                    "followingCount": 10,
                    "followersCount": 11,
                    "isNotResponding": false,
                    "isSuspended": false,
                    "suspensionState": "none",
                    "isBlocked": false,
                    "softwareName": "sharkey",
                    "softwareVersion": "2025.5.2",
                    "openRegistrations": true,
                    "name": "Example",
                    "description": "federated instance",
                    "maintainerName": null,
                    "maintainerEmail": null,
                    "isSilenced": false,
                    "isMediaSilenced": false,
                    "iconUrl": null,
                    "faviconUrl": null,
                    "themeColor": null,
                    "infoUpdatedAt": null,
                    "latestRequestReceivedAt": null,
                    "rejectReports": false,
                    "rejectQuotes": false,
                    "isBubbled": false,
                    "mandatoryCW": null
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFederationObject(): HttpResponseData {
        return respond(
            content = """
                {
                  "id": "instance-1",
                  "host": "example.social",
                  "usersCount": 120,
                  "notesCount": 900,
                  "followingCount": 10,
                  "followersCount": 11,
                  "isNotResponding": false,
                  "isSuspended": false,
                  "isBlocked": false,
                  "softwareName": "sharkey",
                  "softwareVersion": "2025.5.2",
                  "name": "Example",
                  "description": "federated instance",
                  "maintainerName": "Admin",
                  "maintainerEmail": "admin@example.social",
                  "isSilenced": false,
                  "iconUrl": "https://example.social/icon.png",
                  "faviconUrl": "https://example.social/favicon.ico",
                  "infoUpdatedAt": "2026-05-25T00:00:00.000Z",
                  "latestRequestReceivedAt": "2026-05-25T01:00:00.000Z"
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFederationFollows(): HttpResponseData {
        return respond(
            content = """
                [
                  {
                    "id": "follow-1",
                    "createdAt": "2026-05-25T02:30:00.000Z",
                    "followeeId": "user-followee",
                    "followerId": "user-follower",
                    "followee": {
                      "id": "user-followee",
                      "username": "followee",
                      "host": "example.social",
                      "name": "Followee"
                    },
                    "follower": {
                      "id": "user-follower",
                      "username": "follower",
                      "host": "remote.example",
                      "name": "Follower"
                    }
                  }
                ]
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondFederationStats(): HttpResponseData {
        return respond(
            content = """
                {
                  "topSubInstances": [
                    {
                      "id": "sub-1",
                      "host": "sub.example",
                      "usersCount": 10,
                      "notesCount": 20,
                      "followingCount": 3,
                      "followersCount": 4,
                      "isNotResponding": false,
                      "isSuspended": false,
                      "isBlocked": false,
                      "softwareName": "sharkey",
                      "softwareVersion": "2025.5.2",
                      "name": "Sub",
                      "isSilenced": false
                    }
                  ],
                  "otherFollowersCount": 7,
                  "topPubInstances": [
                    {
                      "id": "pub-1",
                      "host": "pub.example",
                      "usersCount": 30,
                      "notesCount": 40,
                      "followingCount": 5,
                      "followersCount": 6,
                      "isNotResponding": false,
                      "isSuspended": false,
                      "isBlocked": false,
                      "softwareName": "misskey",
                      "softwareVersion": "2025.5.2",
                      "name": "Pub",
                      "isSilenced": false
                    }
                  ],
                  "otherFollowingCount": 9
                }
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
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
