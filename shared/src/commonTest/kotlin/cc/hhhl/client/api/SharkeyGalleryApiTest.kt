package cc.hhhl.client.api

import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPostDraft
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

class SharkeyGalleryApiTest {
    @Test
    fun loadFeaturedGalleryPostsPostsToFeaturedEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyGalleryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPosts()
            },
        )

        val result = api.loadPosts("token-123", GalleryListKind.Featured, limit = 20)

        assertIs<GalleryLoadResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/gallery/featured", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        val post = result.posts.single()
        assertEquals("gallery-1", post.id)
        assertEquals("第一张图", post.title)
        assertEquals("Alice", post.author.displayName)
        assertEquals(listOf("photo", "hhhl"), post.tags)
        assertEquals(2, post.likedCount)
        assertEquals(false, post.isSensitive)
        assertEquals("file-1", post.files.single().id)
        assertEquals("2026-05-25 14:00", post.createdAtLabel)
    }

    @Test
    fun loadMineAndLikedUseExpectedEndpoints() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyGalleryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                if (request.url.toString().endsWith("/i/gallery/likes")) {
                    respondLikedPosts()
                } else {
                    respondPosts()
                }
            },
        )

        api.loadPosts("token-123", GalleryListKind.Mine, limit = 20, untilId = "gallery-old")
        api.loadPosts("token-123", GalleryListKind.Liked, limit = 20)

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/i/gallery/posts",
                "https://dc.hhhl.cc/api/i/gallery/likes",
            ),
            paths,
        )
    }

    @Test
    fun showPostPostsPostIdToShowEndpoint() = runTest {
        var capturedRequest: HttpRequestData? = null
        val api = SharkeyGalleryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                capturedRequest = request
                respondPost()
            },
        )

        val result = api.showPost("token-123", postId = "gallery-1")

        assertIs<GalleryShowResult.Success>(result)
        val request = checkNotNull(capturedRequest)
        assertEquals("https://dc.hhhl.cc/api/gallery/posts/show", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""i":"token-123""""))
        assertTrue(body.contains(""""postId":"gallery-1""""))
        assertEquals("gallery-1", result.post.id)
    }

    @Test
    fun likesAndUnlikesGalleryPostUsingPostId() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyGalleryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                assertTrue(body.contains(""""postId":"gallery-1""""))
                respond(content = "", status = HttpStatusCode.NoContent)
            },
        )

        assertIs<GalleryActionResult.Success>(api.likePost("token-123", postId = "gallery-1"))
        assertIs<GalleryActionResult.Success>(api.unlikePost("token-123", postId = "gallery-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/gallery/posts/like",
                "https://dc.hhhl.cc/api/gallery/posts/unlike",
            ),
            paths,
        )
    }

    @Test
    fun createsUpdatesAndDeletesGalleryPostsUsingExpectedEndpoints() = runTest {
        val paths = mutableListOf<String>()
        val api = SharkeyGalleryApi(
            baseUrl = "https://dc.hhhl.cc/",
            client = testClient { request ->
                paths.add(request.url.toString())
                val body = (request.body as TextContent).text
                assertTrue(body.contains(""""i":"token-123""""))
                when {
                    request.url.toString().endsWith("/delete") -> {
                        assertTrue(body.contains(""""postId":"gallery-1""""))
                        respond(content = "", status = HttpStatusCode.NoContent)
                    }
                    else -> {
                        assertTrue(body.contains(""""title":"第一张图""""))
                        assertTrue(body.contains(""""fileIds":["file-1"]"""))
                        assertTrue(body.contains(""""isSensitive":true"""))
                        assertTrue(body.contains(""""isPublic":false"""))
                        if (request.url.toString().endsWith("/update")) {
                            assertTrue(body.contains(""""postId":"gallery-1""""))
                        }
                        respondPost()
                    }
                }
            },
        )
        val draft = GalleryPostDraft(
            title = "第一张图",
            description = "来自图库",
            fileIds = listOf("file-1"),
            isSensitive = true,
            isPublic = false,
        )

        assertIs<GalleryMutationResult.Success>(api.createPost("token-123", draft))
        assertIs<GalleryMutationResult.Success>(api.updatePost("token-123", "gallery-1", draft))
        assertIs<GalleryActionResult.Success>(api.deletePost("token-123", "gallery-1"))

        assertEquals(
            listOf(
                "https://dc.hhhl.cc/api/gallery/posts/create",
                "https://dc.hhhl.cc/api/gallery/posts/update",
                "https://dc.hhhl.cc/api/gallery/posts/delete",
            ),
            paths,
        )
    }

    @Test
    fun blankDraftDoesNotCallNetwork() = runTest {
        val api = SharkeyGalleryApi(
            client = testClient {
                error("network should not be called for invalid gallery draft")
            },
        )

        assertIs<GalleryMutationResult.ServerError>(
            api.createPost("token-123", GalleryPostDraft(title = " ", fileIds = emptyList())),
        )
    }

    @Test
    fun mapsUnauthorizedToUnauthorizedResult() = runTest {
        val api = SharkeyGalleryApi(
            client = testClient {
                respond(
                    content = """{"error":{"message":"Authentication failed."}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
            },
        )

        assertIs<GalleryLoadResult.Unauthorized>(api.loadPosts("expired", GalleryListKind.Featured, 20))
        assertIs<GalleryShowResult.Unauthorized>(api.showPost("expired", postId = "gallery-1"))
    }

    @Test
    fun blankPostIdActionDoesNotMapToUnauthorized() = runTest {
        val api = SharkeyGalleryApi(
            client = testClient {
                error("network should not be called for blank gallery post id")
            },
        )

        assertIs<GalleryActionResult.ServerError>(api.likePost("token-123", " "))
    }

    private fun MockRequestHandleScope.respondPosts(): HttpResponseData {
        return respond(
            content = "[${postJson()}]",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondLikedPosts(): HttpResponseData {
        return respond(
            content = """[{"id":"like-1","post":${postJson()}}]""",
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun MockRequestHandleScope.respondPost(): HttpResponseData {
        return respond(
            content = postJson(),
            status = HttpStatusCode.OK,
            headers = jsonHeaders,
        )
    }

    private fun postJson(): String {
        return """
            {
              "id": "gallery-1",
              "createdAt": "2026-05-25T06:00:00.000Z",
              "updatedAt": "2026-05-25T07:00:00.000Z",
              "userId": "user-1",
              "user": {
                "id": "user-1",
                "username": "alice",
                "name": "Alice",
                "avatarUrl": "https://dc.hhhl.cc/avatar.webp"
              },
              "title": "第一张图",
              "description": "来自图库",
              "fileIds": ["file-1"],
              "files": [
                {
                  "id": "file-1",
                  "createdAt": "2026-05-25T05:00:00.000Z",
                  "name": "photo.webp",
                  "type": "image/webp",
                  "url": "https://dc.hhhl.cc/files/photo.webp",
                  "thumbnailUrl": "https://dc.hhhl.cc/files/thumb.webp",
                  "comment": "photo",
                  "size": 12345,
                  "isSensitive": false
                }
              ],
              "tags": ["photo", "hhhl"],
              "isSensitive": false,
              "isPublic": true,
              "likedCount": 2,
              "isLiked": true
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
