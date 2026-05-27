package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyUserSocialApi
import cc.hhhl.client.api.UserSocialApi
import cc.hhhl.client.api.UserSocialLoadResult
import cc.hhhl.client.model.UserSocialItem
import cc.hhhl.client.model.UserSocialKind

open class UserSocialRepository(
    private val tokenProvider: () -> String?,
    private val api: UserSocialApi = SharkeyUserSocialApi(),
) {
    open suspend fun refresh(
        userId: String,
        kind: UserSocialKind,
    ): UserSocialRepositoryResult {
        return load(
            userId = userId,
            kind = kind,
            currentItems = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMore(
        userId: String,
        kind: UserSocialKind,
        currentItems: List<UserSocialItem>,
    ): UserSocialRepositoryResult {
        return load(
            userId = userId,
            kind = kind,
            currentItems = currentItems,
            untilId = currentItems.lastOrNull()?.id,
        )
    }

    private suspend fun load(
        userId: String,
        kind: UserSocialKind,
        currentItems: List<UserSocialItem>,
        untilId: String?,
    ): UserSocialRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserSocialRepositoryResult.Unauthorized
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return UserSocialRepositoryResult.Error("无法读取用户")

        return when (val result = api.loadUsers(token, cleanUserId, kind, DEFAULT_PAGE_SIZE, untilId)) {
            is UserSocialLoadResult.Success -> UserSocialRepositoryResult.Success(
                items = currentItems.appendDistinctBy(result.items) { it.id },
                endReached = result.items.isEmpty(),
            )
            UserSocialLoadResult.Unauthorized -> UserSocialRepositoryResult.Unauthorized
            is UserSocialLoadResult.NetworkError -> {
                UserSocialRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserSocialLoadResult.ServerError -> UserSocialRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface UserSocialRepositoryResult {
    data class Success(
        val items: List<UserSocialItem>,
        val endReached: Boolean = false,
    ) : UserSocialRepositoryResult

    data object Unauthorized : UserSocialRepositoryResult

    data class Error(val message: String) : UserSocialRepositoryResult
}
