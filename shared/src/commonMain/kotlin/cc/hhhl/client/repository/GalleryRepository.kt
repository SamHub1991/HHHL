package cc.hhhl.client.repository

import cc.hhhl.client.api.GalleryApi
import cc.hhhl.client.api.GalleryActionResult
import cc.hhhl.client.api.GalleryLoadResult
import cc.hhhl.client.api.GalleryMutationResult
import cc.hhhl.client.api.GalleryShowResult
import cc.hhhl.client.api.SharkeyGalleryApi
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.GalleryPost
import cc.hhhl.client.model.GalleryPostDraft

open class GalleryRepository(
    private val tokenProvider: () -> String?,
    private val api: GalleryApi = SharkeyGalleryApi(),
) {
    open suspend fun refreshPosts(kind: GalleryListKind): GalleryPostsRepositoryResult {
        return loadPosts(
            kind = kind,
            currentPosts = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMorePosts(
        kind: GalleryListKind,
        currentPosts: List<GalleryPost>,
    ): GalleryPostsRepositoryResult {
        return loadPosts(
            kind = kind,
            currentPosts = currentPosts,
            untilId = currentPosts.lastOrNull()?.id,
        )
    }

    open suspend fun showPost(postId: String): GalleryPostRepositoryResult {
        val cleanPostId = postId.trim()
        if (cleanPostId.isEmpty()) {
            return GalleryPostRepositoryResult.Error("无法读取图库帖子")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return GalleryPostRepositoryResult.Unauthorized

        return when (val result = api.showPost(token, cleanPostId)) {
            is GalleryShowResult.Success -> GalleryPostRepositoryResult.Success(result.post)
            GalleryShowResult.Unauthorized -> GalleryPostRepositoryResult.Unauthorized
            is GalleryShowResult.NetworkError -> {
                GalleryPostRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is GalleryShowResult.ServerError -> GalleryPostRepositoryResult.Error(result.message)
        }
    }

    open suspend fun likePost(postId: String): GalleryActionRepositoryResult {
        return performGalleryAction(postId) { token, cleanPostId ->
            api.likePost(token, cleanPostId)
        }
    }

    open suspend fun unlikePost(postId: String): GalleryActionRepositoryResult {
        return performGalleryAction(postId) { token, cleanPostId ->
            api.unlikePost(token, cleanPostId)
        }
    }

    open suspend fun createPost(draft: GalleryPostDraft): GalleryMutationRepositoryResult {
        val cleanDraft = draft.cleaned()
        cleanDraft.validationMessage()?.let { return GalleryMutationRepositoryResult.Error(it) }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return GalleryMutationRepositoryResult.Unauthorized

        return when (val result = api.createPost(token, cleanDraft)) {
            is GalleryMutationResult.Success -> GalleryMutationRepositoryResult.Success(result.post)
            GalleryMutationResult.Unauthorized -> GalleryMutationRepositoryResult.Unauthorized
            is GalleryMutationResult.NetworkError -> {
                GalleryMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is GalleryMutationResult.ServerError -> GalleryMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updatePost(
        postId: String,
        draft: GalleryPostDraft,
    ): GalleryMutationRepositoryResult {
        val cleanPostId = postId.trim()
        val cleanDraft = draft.cleaned()
        if (cleanPostId.isEmpty()) return GalleryMutationRepositoryResult.Error("无法读取图库帖子")
        cleanDraft.validationMessage()?.let { return GalleryMutationRepositoryResult.Error(it) }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return GalleryMutationRepositoryResult.Unauthorized

        return when (val result = api.updatePost(token, cleanPostId, cleanDraft)) {
            is GalleryMutationResult.Success -> GalleryMutationRepositoryResult.Success(result.post)
            GalleryMutationResult.Unauthorized -> GalleryMutationRepositoryResult.Unauthorized
            is GalleryMutationResult.NetworkError -> {
                GalleryMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is GalleryMutationResult.ServerError -> GalleryMutationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun deletePost(postId: String): GalleryActionRepositoryResult {
        return performGalleryAction(postId) { token, cleanPostId ->
            api.deletePost(token, cleanPostId)
        }
    }

    private suspend fun loadPosts(
        kind: GalleryListKind,
        currentPosts: List<GalleryPost>,
        untilId: String?,
    ): GalleryPostsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return GalleryPostsRepositoryResult.Unauthorized

        return when (
            val result = api.loadPosts(
                token = token,
                kind = kind,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
            )
        ) {
            is GalleryLoadResult.Success -> GalleryPostsRepositoryResult.Success(
                posts = currentPosts.appendDistinctBy(result.posts) { it.id },
                endReached = result.posts.isEmpty(),
            )
            GalleryLoadResult.Unauthorized -> GalleryPostsRepositoryResult.Unauthorized
            is GalleryLoadResult.NetworkError -> {
                GalleryPostsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is GalleryLoadResult.ServerError -> GalleryPostsRepositoryResult.Error(result.message)
        }
    }

    private suspend fun performGalleryAction(
        postId: String,
        action: suspend (String, String) -> GalleryActionResult,
    ): GalleryActionRepositoryResult {
        val cleanPostId = postId.trim()
        if (cleanPostId.isEmpty()) {
            return GalleryActionRepositoryResult.Error("无法读取图库帖子")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return GalleryActionRepositoryResult.Unauthorized

        return when (val result = action(token, cleanPostId)) {
            GalleryActionResult.Success -> GalleryActionRepositoryResult.Success
            GalleryActionResult.Unauthorized -> GalleryActionRepositoryResult.Unauthorized
            is GalleryActionResult.NetworkError -> {
                GalleryActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is GalleryActionResult.ServerError -> GalleryActionRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface GalleryPostsRepositoryResult {
    data class Success(
        val posts: List<GalleryPost>,
        val endReached: Boolean = false,
    ) : GalleryPostsRepositoryResult

    data object Unauthorized : GalleryPostsRepositoryResult

    data class Error(val message: String) : GalleryPostsRepositoryResult
}

sealed interface GalleryPostRepositoryResult {
    data class Success(val post: GalleryPost) : GalleryPostRepositoryResult

    data object Unauthorized : GalleryPostRepositoryResult

    data class Error(val message: String) : GalleryPostRepositoryResult
}

sealed interface GalleryActionRepositoryResult {
    data object Success : GalleryActionRepositoryResult

    data object Unauthorized : GalleryActionRepositoryResult

    data class Error(val message: String) : GalleryActionRepositoryResult
}

sealed interface GalleryMutationRepositoryResult {
    data class Success(val post: GalleryPost) : GalleryMutationRepositoryResult

    data object Unauthorized : GalleryMutationRepositoryResult

    data class Error(val message: String) : GalleryMutationRepositoryResult
}

private fun GalleryPostDraft.cleaned(): GalleryPostDraft {
    return copy(
        title = title.trim(),
        description = description.trim(),
        fileIds = fileIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )
}

private fun GalleryPostDraft.validationMessage(): String? {
    return when {
        title.isBlank() -> "请输入标题"
        fileIds.isEmpty() -> "请至少选择一个文件"
        else -> null
    }
}
