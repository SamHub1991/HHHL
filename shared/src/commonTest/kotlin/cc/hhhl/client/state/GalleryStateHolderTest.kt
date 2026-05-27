package cc.hhhl.client.state

import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPostDraft
import cc.hhhl.client.repository.GalleryActionRepositoryResult
import cc.hhhl.client.repository.GalleryMutationRepositoryResult
import cc.hhhl.client.repository.GalleryPostRepositoryResult
import cc.hhhl.client.repository.GalleryPostsRepositoryResult
import cc.hhhl.client.repository.GalleryRepository
import cc.hhhl.client.repository.sampleGalleryPost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryStateHolderTest {
    @Test
    fun refreshPostsStoresPosts() = runTest {
        val post = sampleGalleryPost("gallery-1")
        val holder = GalleryStateHolder(
            repository = fakeRepository(
                postsResult = GalleryPostsRepositoryResult.Success(listOf(post)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        assertTrue(holder.state.value.isLoadingPosts)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingPosts)
        assertEquals(listOf(post), holder.state.value.posts)
    }

    @Test
    fun selectKindLoadsThatKindPosts() = runTest {
        val featured = sampleGalleryPost("gallery-featured")
        val mine = sampleGalleryPost("gallery-mine")
        val calls = mutableListOf<GalleryListKind>()
        val holder = GalleryStateHolder(
            repository = sequenceRepository(
                postsResults = listOf(
                    GalleryPostsRepositoryResult.Success(listOf(featured)),
                    GalleryPostsRepositoryResult.Success(listOf(mine)),
                ),
                postResult = GalleryPostRepositoryResult.Success(mine),
                onRefreshPosts = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()
        holder.selectKind(GalleryListKind.Mine)
        advanceUntilIdle()

        assertEquals(listOf(GalleryListKind.Featured, GalleryListKind.Mine), calls)
        assertEquals(GalleryListKind.Mine, holder.state.value.selectedKind)
        assertEquals(listOf(mine), holder.state.value.posts)
    }

    @Test
    fun openPostLoadsDetail() = runTest {
        val post = sampleGalleryPost("gallery-1")
        val calls = mutableListOf<String>()
        val holder = GalleryStateHolder(
            repository = fakeRepository(
                postsResult = GalleryPostsRepositoryResult.Success(listOf(post)),
                postResult = GalleryPostRepositoryResult.Success(post),
                onShowPost = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.openPost("gallery-1")
        assertTrue(holder.state.value.isLoadingDetail)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingDetail)
        assertEquals(post, holder.state.value.selectedPost)
        assertEquals(listOf("gallery-1"), calls)
    }

    @Test
    fun unauthorizedPostsLoadMarksRelogin() = runTest {
        val holder = GalleryStateHolder(
            repository = fakeRepository(
                postsResult = GalleryPostsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRetryClearsReloginAfterUnauthorized() = runTest {
        val post = sampleGalleryPost("gallery-1")
        val holder = GalleryStateHolder(
            repository = sequenceRepository(
                postsResults = listOf(
                    GalleryPostsRepositoryResult.Unauthorized,
                    GalleryPostsRepositoryResult.Success(listOf(post)),
                ),
                postResult = GalleryPostRepositoryResult.Success(post),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.refreshPosts()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(post), holder.state.value.posts)
    }

    @Test
    fun toggleLikeLikesSelectedPostAndUpdatesList() = runTest {
        val post = sampleGalleryPost("gallery-1").copy(isLiked = false, likedCount = 2)
        val calls = mutableListOf<String>()
        val holder = GalleryStateHolder(
            repository = fakeRepository(
                postsResult = GalleryPostsRepositoryResult.Success(listOf(post)),
                postResult = GalleryPostRepositoryResult.Success(post),
                actionResult = GalleryActionRepositoryResult.Success,
                onLikePost = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()
        holder.openPost(post.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedPost()
        assertTrue(holder.state.value.isChangingLike)
        advanceUntilIdle()

        assertFalse(holder.state.value.isChangingLike)
        assertEquals(listOf(post.id), calls)
        assertEquals(true, holder.state.value.selectedPost?.isLiked)
        assertEquals(3, holder.state.value.selectedPost?.likedCount)
        assertEquals(true, holder.state.value.posts.single().isLiked)
        assertEquals(3, holder.state.value.posts.single().likedCount)
    }

    @Test
    fun closeDetailClearsReloginAfterUnauthorizedLike() = runTest {
        val post = sampleGalleryPost("gallery-1")
        val holder = GalleryStateHolder(
            repository = fakeRepository(
                postsResult = GalleryPostsRepositoryResult.Success(listOf(post)),
                postResult = GalleryPostRepositoryResult.Success(post),
                actionResult = GalleryActionRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()
        holder.openPost(post.id)
        advanceUntilIdle()
        holder.toggleLikeSelectedPost()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.closeDetail()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(null, holder.state.value.selectedPost)
    }

    @Test
    fun createUpdateAndDeleteMutateStoredPosts() = runTest {
        val original = sampleGalleryPost("gallery-1")
        val updated = original.copy(title = "更新后的图")
        val created = sampleGalleryPost("gallery-2")
        val holder = GalleryStateHolder(
            repository = sequenceMutationRepository(
                postsResult = GalleryPostsRepositoryResult.Success(listOf(original)),
                postResult = GalleryPostRepositoryResult.Success(original),
                mutationResults = listOf(
                    GalleryMutationRepositoryResult.Success(created),
                    GalleryMutationRepositoryResult.Success(updated),
                ),
                actionResult = GalleryActionRepositoryResult.Success,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshPosts()
        advanceUntilIdle()
        holder.createPost(GalleryPostDraft(title = "新图", fileIds = listOf("file-2")))
        advanceUntilIdle()
        assertEquals(listOf(created, original), holder.state.value.posts)

        holder.openPost(original.id)
        advanceUntilIdle()
        holder.updateSelectedPost(GalleryPostDraft(title = "更新后的图", fileIds = original.fileIds))
        advanceUntilIdle()
        assertEquals(updated, holder.state.value.selectedPost)
        assertEquals(listOf(created, updated), holder.state.value.posts)

        holder.deleteSelectedPost()
        advanceUntilIdle()
        assertEquals(null, holder.state.value.selectedPost)
        assertEquals(listOf(created), holder.state.value.posts)
    }

    private fun fakeRepository(
        postsResult: GalleryPostsRepositoryResult,
        postResult: GalleryPostRepositoryResult = GalleryPostRepositoryResult.Success(sampleGalleryPost("gallery-1")),
        actionResult: GalleryActionRepositoryResult = GalleryActionRepositoryResult.Success,
        onRefreshPosts: (GalleryListKind) -> Unit = {},
        onShowPost: (String) -> Unit = {},
        onLikePost: (String) -> Unit = {},
        onUnlikePost: (String) -> Unit = {},
    ): GalleryRepository {
        return sequenceRepository(
            postsResults = listOf(postsResult),
            postResult = postResult,
            actionResult = actionResult,
            onRefreshPosts = onRefreshPosts,
            onShowPost = onShowPost,
            onLikePost = onLikePost,
            onUnlikePost = onUnlikePost,
        )
    }

    private fun sequenceRepository(
        postsResults: List<GalleryPostsRepositoryResult>,
        postResult: GalleryPostRepositoryResult,
        actionResult: GalleryActionRepositoryResult = GalleryActionRepositoryResult.Success,
        onRefreshPosts: (GalleryListKind) -> Unit = {},
        onShowPost: (String) -> Unit = {},
        onLikePost: (String) -> Unit = {},
        onUnlikePost: (String) -> Unit = {},
    ): GalleryRepository {
        var postResultIndex = 0
        return object : GalleryRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.GalleryApi {
                override suspend fun loadPosts(
                    token: String,
                    kind: GalleryListKind,
                    limit: Int,
                    untilId: String?,
                ): cc.hhhl.client.api.GalleryLoadResult {
                    return cc.hhhl.client.api.GalleryLoadResult.Success(emptyList())
                }

                override suspend fun showPost(
                    token: String,
                    postId: String,
                ): cc.hhhl.client.api.GalleryShowResult {
                    return cc.hhhl.client.api.GalleryShowResult.Success(sampleGalleryPost("gallery-1"))
                }

                override suspend fun likePost(
                    token: String,
                    postId: String,
                ): cc.hhhl.client.api.GalleryActionResult {
                    return cc.hhhl.client.api.GalleryActionResult.Success
                }

                override suspend fun unlikePost(
                    token: String,
                    postId: String,
                ): cc.hhhl.client.api.GalleryActionResult {
                    return cc.hhhl.client.api.GalleryActionResult.Success
                }

                override suspend fun createPost(
                    token: String,
                    draft: GalleryPostDraft,
                ): cc.hhhl.client.api.GalleryMutationResult {
                    return cc.hhhl.client.api.GalleryMutationResult.Success(sampleGalleryPost("gallery-1"))
                }

                override suspend fun updatePost(
                    token: String,
                    postId: String,
                    draft: GalleryPostDraft,
                ): cc.hhhl.client.api.GalleryMutationResult {
                    return cc.hhhl.client.api.GalleryMutationResult.Success(sampleGalleryPost("gallery-1"))
                }

                override suspend fun deletePost(
                    token: String,
                    postId: String,
                ): cc.hhhl.client.api.GalleryActionResult {
                    return cc.hhhl.client.api.GalleryActionResult.Success
                }
            },
        ) {
            override suspend fun refreshPosts(kind: GalleryListKind): GalleryPostsRepositoryResult {
                onRefreshPosts(kind)
                val result = postsResults.getOrElse(postResultIndex) { postsResults.last() }
                postResultIndex += 1
                return result
            }

            override suspend fun showPost(postId: String): GalleryPostRepositoryResult {
                onShowPost(postId)
                return postResult
            }

            override suspend fun likePost(postId: String): GalleryActionRepositoryResult {
                onLikePost(postId)
                return actionResult
            }

            override suspend fun unlikePost(postId: String): GalleryActionRepositoryResult {
                onUnlikePost(postId)
                return actionResult
            }
        }
    }

    private fun sequenceMutationRepository(
        postsResult: GalleryPostsRepositoryResult,
        postResult: GalleryPostRepositoryResult,
        mutationResults: List<GalleryMutationRepositoryResult>,
        actionResult: GalleryActionRepositoryResult,
    ): GalleryRepository {
        var mutationIndex = 0
        return object : GalleryRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.GalleryApi {
                override suspend fun loadPosts(
                    token: String,
                    kind: GalleryListKind,
                    limit: Int,
                    untilId: String?,
                ) = cc.hhhl.client.api.GalleryLoadResult.Success(emptyList())

                override suspend fun showPost(
                    token: String,
                    postId: String,
                ) = cc.hhhl.client.api.GalleryShowResult.Success(sampleGalleryPost("gallery-1"))

                override suspend fun likePost(
                    token: String,
                    postId: String,
                ) = cc.hhhl.client.api.GalleryActionResult.Success

                override suspend fun unlikePost(
                    token: String,
                    postId: String,
                ) = cc.hhhl.client.api.GalleryActionResult.Success

                override suspend fun createPost(
                    token: String,
                    draft: GalleryPostDraft,
                ) = cc.hhhl.client.api.GalleryMutationResult.Success(sampleGalleryPost("gallery-1"))

                override suspend fun updatePost(
                    token: String,
                    postId: String,
                    draft: GalleryPostDraft,
                ) = cc.hhhl.client.api.GalleryMutationResult.Success(sampleGalleryPost("gallery-1"))

                override suspend fun deletePost(
                    token: String,
                    postId: String,
                ) = cc.hhhl.client.api.GalleryActionResult.Success
            },
        ) {
            override suspend fun refreshPosts(kind: GalleryListKind): GalleryPostsRepositoryResult = postsResult

            override suspend fun showPost(postId: String): GalleryPostRepositoryResult = postResult

            override suspend fun createPost(draft: GalleryPostDraft): GalleryMutationRepositoryResult {
                val result = mutationResults.getOrElse(mutationIndex) { mutationResults.last() }
                mutationIndex += 1
                return result
            }

            override suspend fun updatePost(
                postId: String,
                draft: GalleryPostDraft,
            ): GalleryMutationRepositoryResult {
                val result = mutationResults.getOrElse(mutationIndex) { mutationResults.last() }
                mutationIndex += 1
                return result
            }

            override suspend fun deletePost(postId: String): GalleryActionRepositoryResult = actionResult
        }
    }
}
