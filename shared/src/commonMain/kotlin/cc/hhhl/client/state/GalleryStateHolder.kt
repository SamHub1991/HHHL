package cc.hhhl.client.state

import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.GalleryPostDraft
import cc.hhhl.client.repository.GalleryActionRepositoryResult
import cc.hhhl.client.repository.GalleryMutationRepositoryResult
import cc.hhhl.client.repository.GalleryPostRepositoryResult
import cc.hhhl.client.repository.GalleryPostsRepositoryResult
import cc.hhhl.client.repository.GalleryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val selectedKind: GalleryListKind = GalleryListKind.Featured,
    val posts: List<GalleryPost> = emptyList(),
    val selectedPost: GalleryPost? = null,
    val isLoadingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isChangingLike: Boolean = false,
    val isMutatingPost: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    val detailErrorMessage: String? = null,
    val requiresRelogin: Boolean = false,
)

class GalleryStateHolder(
    private val repository: GalleryRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = mutableState
    private var postsRequestId = 0
    private var detailRequestId = 0

    fun refreshPosts(kind: GalleryListKind = state.value.selectedKind) {
        if (state.value.isLoadingPosts) return
        val requestId = ++postsRequestId

        mutableState.update {
            it.copy(
                selectedKind = kind,
                posts = if (it.selectedKind == kind) it.posts else emptyList(),
                isLoadingPosts = true,
                isLoadingMore = false,
                endReached = false,
                errorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            applyPostsResult(
                result = repository.refreshPosts(kind),
                loadingMore = false,
                requestId = requestId,
                kind = kind,
            )
        }
    }

    fun selectKind(kind: GalleryListKind) {
        if (state.value.selectedKind == kind && state.value.posts.isNotEmpty()) return
        refreshPosts(kind)
    }

    fun loadMore() {
        val current = state.value
        if (
            current.isLoadingPosts ||
            current.isLoadingMore ||
            current.posts.isEmpty() ||
            current.endReached
        ) {
            return
        }
        val requestId = ++postsRequestId
        val kind = current.selectedKind

        mutableState.update {
            it.copy(isLoadingMore = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyPostsResult(
                result = repository.loadMorePosts(kind, current.posts),
                loadingMore = true,
                requestId = requestId,
                kind = kind,
            )
        }
    }

    fun openPost(postId: String) {
        if (postId.isBlank()) return
        val requestId = ++detailRequestId

        mutableState.update {
            it.copy(
                selectedPost = it.selectedPost?.takeIf { post -> post.id == postId },
                isLoadingDetail = true,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }

        scope.launch {
            when (val result = repository.showPost(postId)) {
                is GalleryPostRepositoryResult.Success -> mutableState.update {
                    if (requestId != detailRequestId || result.post.id != postId) return@update it
                    it.copy(
                        selectedPost = result.post,
                        isLoadingDetail = false,
                        detailErrorMessage = null,
                        requiresRelogin = false,
                    )
                }
                GalleryPostRepositoryResult.Unauthorized -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is GalleryPostRepositoryResult.Error -> mutableState.update {
                    if (requestId != detailRequestId) return@update it
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = result.message,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    fun closeDetail() {
        detailRequestId += 1
        mutableState.update {
            it.copy(
                selectedPost = null,
                isLoadingDetail = false,
                detailErrorMessage = null,
                requiresRelogin = false,
            )
        }
    }

    fun toggleLikeSelectedPost() {
        val post = state.value.selectedPost ?: return
        if (state.value.isChangingLike) return

        mutableState.update {
            it.copy(isChangingLike = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            val result = if (post.isLiked) {
                repository.unlikePost(post.id)
            } else {
                repository.likePost(post.id)
            }
            applyLikeResult(post, result)
        }
    }

    fun createPost(draft: GalleryPostDraft) {
        if (state.value.isMutatingPost) return
        mutableState.update {
            it.copy(isMutatingPost = true, errorMessage = null, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyMutationResult(repository.createPost(draft), closeDetail = false)
        }
    }

    fun updateSelectedPost(draft: GalleryPostDraft) {
        val post = state.value.selectedPost ?: return
        if (state.value.isMutatingPost) return
        mutableState.update {
            it.copy(isMutatingPost = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyMutationResult(repository.updatePost(post.id, draft), closeDetail = false)
        }
    }

    fun deleteSelectedPost() {
        val post = state.value.selectedPost ?: return
        if (state.value.isMutatingPost) return
        mutableState.update {
            it.copy(isMutatingPost = true, detailErrorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            when (val result = repository.deletePost(post.id)) {
                GalleryActionRepositoryResult.Success -> mutableState.update { current ->
                    val deletedSelectedPost = current.selectedPost?.id == post.id
                    current.copy(
                        posts = current.posts.filterNot { it.id == post.id },
                        selectedPost = current.selectedPost?.takeIf { it.id != post.id },
                        isMutatingPost = false,
                        detailErrorMessage = if (deletedSelectedPost) null else current.detailErrorMessage,
                        requiresRelogin = false,
                    )
                }
                GalleryActionRepositoryResult.Unauthorized -> mutableState.update { current ->
                    val deletingSelectedPost = current.selectedPost?.id == post.id
                    current.copy(
                        isMutatingPost = false,
                        detailErrorMessage = if (deletingSelectedPost) "登录已失效，请重新登录" else current.detailErrorMessage,
                        requiresRelogin = true,
                    )
                }
                is GalleryActionRepositoryResult.Error -> mutableState.update { current ->
                    val deletingSelectedPost = current.selectedPost?.id == post.id
                    current.copy(
                        isMutatingPost = false,
                        detailErrorMessage = if (deletingSelectedPost) result.message else current.detailErrorMessage,
                        requiresRelogin = false,
                    )
                }
            }
        }
    }

    private fun applyPostsResult(
        result: GalleryPostsRepositoryResult,
        loadingMore: Boolean,
        requestId: Int,
        kind: GalleryListKind,
    ) {
        when (result) {
            is GalleryPostsRepositoryResult.Success -> mutableState.update {
                if (requestId != postsRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    posts = result.posts,
                    isLoadingPosts = false,
                    isLoadingMore = false,
                    endReached = result.endReached,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            GalleryPostsRepositoryResult.Unauthorized -> mutableState.update {
                if (requestId != postsRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    isLoadingPosts = false,
                    isLoadingMore = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is GalleryPostsRepositoryResult.Error -> mutableState.update {
                if (requestId != postsRequestId || it.selectedKind != kind) return@update it
                it.copy(
                    isLoadingPosts = if (loadingMore) it.isLoadingPosts else false,
                    isLoadingMore = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyLikeResult(
        originalPost: GalleryPost,
        result: GalleryActionRepositoryResult,
    ) {
        when (result) {
            GalleryActionRepositoryResult.Success -> mutableState.update { current ->
                val nowLiked = !originalPost.isLiked
                val delta = if (nowLiked) 1 else -1
                current.copy(
                    posts = current.posts.map { post ->
                        if (post.id == originalPost.id) {
                            post.copy(
                                isLiked = nowLiked,
                                likedCount = (post.likedCount + delta).coerceAtLeast(0),
                            )
                        } else {
                            post
                        }
                    },
                    selectedPost = current.selectedPost?.takeIf { it.id == originalPost.id }?.copy(
                        isLiked = nowLiked,
                        likedCount = (current.selectedPost.likedCount + delta).coerceAtLeast(0),
                    ) ?: current.selectedPost,
                    isChangingLike = false,
                    detailErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            GalleryActionRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is GalleryActionRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isChangingLike = false,
                    detailErrorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }

    private fun applyMutationResult(
        result: GalleryMutationRepositoryResult,
        closeDetail: Boolean,
    ) {
        when (result) {
            is GalleryMutationRepositoryResult.Success -> mutableState.update { current ->
                val existing = current.posts.any { it.id == result.post.id }
                current.copy(
                    posts = if (existing) {
                        current.posts.map { if (it.id == result.post.id) result.post else it }
                    } else {
                        listOf(result.post) + current.posts
                    },
                    selectedPost = if (closeDetail) null else {
                        current.selectedPost?.takeIf { it.id == result.post.id }?.let { result.post }
                            ?: current.selectedPost
                    },
                    isMutatingPost = false,
                    errorMessage = null,
                    detailErrorMessage = null,
                    requiresRelogin = false,
                )
            }
            GalleryMutationRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isMutatingPost = false,
                    detailErrorMessage = "登录已失效，请重新登录",
                    errorMessage = if (it.selectedPost == null) "登录已失效，请重新登录" else it.errorMessage,
                    requiresRelogin = true,
                )
            }
            is GalleryMutationRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isMutatingPost = false,
                    detailErrorMessage = if (it.selectedPost != null) result.message else it.detailErrorMessage,
                    errorMessage = if (it.selectedPost == null) result.message else it.errorMessage,
                    requiresRelogin = false,
                )
            }
        }
    }
}
