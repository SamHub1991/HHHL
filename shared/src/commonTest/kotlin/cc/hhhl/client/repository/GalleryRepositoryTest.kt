package cc.hhhl.client.repository

import cc.hhhl.client.api.GalleryApi
import cc.hhhl.client.api.GalleryActionResult
import cc.hhhl.client.api.GalleryLoadResult
import cc.hhhl.client.api.GalleryMutationResult
import cc.hhhl.client.api.GalleryShowResult
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.GalleryPostDraft
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class GalleryRepositoryTest {
    @Test
    fun refreshPostsUsesTokenAndKind() = runTest {
        val posts = listOf(sampleGalleryPost("gallery-1"))
        val calls = mutableListOf<PostCall>()
        val repository = GalleryRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                postCalls = calls,
                postResult = GalleryLoadResult.Success(posts),
            ),
        )

        val result = repository.refreshPosts(GalleryListKind.Mine)

        assertIs<GalleryPostsRepositoryResult.Success>(result)
        assertEquals(listOf(PostCall("token-123", GalleryListKind.Mine, null)), calls)
        assertEquals(posts, result.posts)
    }

    @Test
    fun loadMorePostsUsesLastPostIdAndDeduplicates() = runTest {
        val first = sampleGalleryPost("gallery-1")
        val second = sampleGalleryPost("gallery-2")
        val calls = mutableListOf<PostCall>()
        val repository = GalleryRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                postCalls = calls,
                postResult = GalleryLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMorePosts(GalleryListKind.Featured, currentPosts = listOf(first))

        assertIs<GalleryPostsRepositoryResult.Success>(result)
        assertEquals(listOf(PostCall("token-123", GalleryListKind.Featured, first.id)), calls)
        assertEquals(listOf(first, second), result.posts)
    }

    @Test
    fun showPostUsesTokenAndPostId() = runTest {
        val calls = mutableListOf<ShowCall>()
        val post = sampleGalleryPost("gallery-1")
        val repository = GalleryRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                showCalls = calls,
                showResult = GalleryShowResult.Success(post),
            ),
        )

        val result = repository.showPost("gallery-1")

        assertIs<GalleryPostRepositoryResult.Success>(result)
        assertEquals(listOf(ShowCall("token-123", "gallery-1")), calls)
        assertEquals(post, result.post)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = GalleryRepository(
            tokenProvider = { null },
            api = fakeApi(onCall = { calls += 1 }),
        )

        assertIs<GalleryPostsRepositoryResult.Unauthorized>(repository.refreshPosts(GalleryListKind.Featured))
        assertIs<GalleryPostRepositoryResult.Unauthorized>(repository.showPost("gallery-1"))
        assertEquals(0, calls)
    }

    @Test
    fun likeAndUnlikePostUseTokenAndPostId() = runTest {
        val calls = mutableListOf<ActionCall>()
        val repository = GalleryRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                actionCalls = calls,
                actionResult = GalleryActionResult.Success,
            ),
        )

        assertEquals(GalleryActionRepositoryResult.Success, repository.likePost("gallery-1"))
        assertEquals(GalleryActionRepositoryResult.Success, repository.unlikePost("gallery-1"))
        assertEquals(
            listOf(
                ActionCall("like", "token-123", "gallery-1"),
                ActionCall("unlike", "token-123", "gallery-1"),
            ),
            calls,
        )
    }

    @Test
    fun createUpdateAndDeletePostUseTokenDraftAndPostId() = runTest {
        val mutationCalls = mutableListOf<MutationCall>()
        val actionCalls = mutableListOf<ActionCall>()
        val post = sampleGalleryPost("gallery-1")
        val repository = GalleryRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = mutationCalls,
                actionCalls = actionCalls,
                mutationResult = GalleryMutationResult.Success(post),
                actionResult = GalleryActionResult.Success,
            ),
        )
        val draft = GalleryPostDraft(
            title = " 第一张图 ",
            fileIds = listOf(" file-1 ", "file-1"),
        )

        assertIs<GalleryMutationRepositoryResult.Success>(repository.createPost(draft))
        assertIs<GalleryMutationRepositoryResult.Success>(repository.updatePost("gallery-1", draft))
        assertEquals(GalleryActionRepositoryResult.Success, repository.deletePost("gallery-1"))
        assertEquals(
            listOf(
                MutationCall("create", "token-123", null, draft.copy(title = "第一张图", fileIds = listOf("file-1"))),
                MutationCall("update", "token-123", "gallery-1", draft.copy(title = "第一张图", fileIds = listOf("file-1"))),
            ),
            mutationCalls,
        )
        assertEquals(listOf(ActionCall("delete", "token-123", "gallery-1")), actionCalls)
    }

    private fun fakeApi(
        postCalls: MutableList<PostCall> = mutableListOf(),
        showCalls: MutableList<ShowCall> = mutableListOf(),
        actionCalls: MutableList<ActionCall> = mutableListOf(),
        mutationCalls: MutableList<MutationCall> = mutableListOf(),
        postResult: GalleryLoadResult = GalleryLoadResult.Success(emptyList()),
        showResult: GalleryShowResult = GalleryShowResult.Success(sampleGalleryPost("gallery-1")),
        actionResult: GalleryActionResult = GalleryActionResult.Success,
        mutationResult: GalleryMutationResult = GalleryMutationResult.Success(sampleGalleryPost("gallery-1")),
        onCall: () -> Unit = {},
    ): GalleryApi {
        return object : GalleryApi {
            override suspend fun loadPosts(
                token: String,
                kind: GalleryListKind,
                limit: Int,
                untilId: String?,
            ): GalleryLoadResult {
                onCall()
                postCalls.add(PostCall(token, kind, untilId))
                return postResult
            }

            override suspend fun showPost(
                token: String,
                postId: String,
            ): GalleryShowResult {
                onCall()
                showCalls.add(ShowCall(token, postId))
                return showResult
            }

            override suspend fun likePost(
                token: String,
                postId: String,
            ): GalleryActionResult {
                onCall()
                actionCalls.add(ActionCall("like", token, postId))
                return actionResult
            }

            override suspend fun unlikePost(
                token: String,
                postId: String,
            ): GalleryActionResult {
                onCall()
                actionCalls.add(ActionCall("unlike", token, postId))
                return actionResult
            }

            override suspend fun createPost(
                token: String,
                draft: GalleryPostDraft,
            ): GalleryMutationResult {
                onCall()
                mutationCalls.add(MutationCall("create", token, null, draft))
                return mutationResult
            }

            override suspend fun updatePost(
                token: String,
                postId: String,
                draft: GalleryPostDraft,
            ): GalleryMutationResult {
                onCall()
                mutationCalls.add(MutationCall("update", token, postId, draft))
                return mutationResult
            }

            override suspend fun deletePost(
                token: String,
                postId: String,
            ): GalleryActionResult {
                onCall()
                actionCalls.add(ActionCall("delete", token, postId))
                return actionResult
            }
        }
    }

    private data class PostCall(
        val token: String,
        val kind: GalleryListKind,
        val untilId: String?,
    )

    private data class ShowCall(
        val token: String,
        val postId: String,
    )

    private data class ActionCall(
        val action: String,
        val token: String,
        val postId: String,
    )

    private data class MutationCall(
        val action: String,
        val token: String,
        val postId: String?,
        val draft: GalleryPostDraft,
    )
}

fun sampleGalleryPost(id: String): GalleryPost {
    return GalleryPost(
        id = id,
        title = "第一张图",
        description = "来自图库",
        author = User("user-1", "Alice", "alice", "A"),
        userId = "user-1",
        fileIds = listOf("file-1"),
        files = listOf(
            DriveFile(
                id = "file-1",
                name = "photo.webp",
                type = "image/webp",
                url = "https://dc.hhhl.cc/files/photo.webp",
                thumbnailUrl = "https://dc.hhhl.cc/files/thumb.webp",
                comment = "photo",
                size = 12345,
                isSensitive = false,
            ),
        ),
        tags = listOf("photo", "hhhl"),
        isSensitive = false,
        isPublic = true,
        likedCount = 2,
        isLiked = true,
        createdAtLabel = "2026-05-25 06:00",
        updatedAtLabel = "2026-05-25 07:00",
    )
}
