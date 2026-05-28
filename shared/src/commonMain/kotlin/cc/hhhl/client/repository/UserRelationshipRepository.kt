package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyUserRelationshipApi
import cc.hhhl.client.api.UserRelationshipApi
import cc.hhhl.client.api.UserRelationshipLoadResult
import cc.hhhl.client.api.UserRelationshipListResult
import cc.hhhl.client.api.UserRelationshipResult
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.model.UserRelationshipListEntry

open class UserRelationshipRepository(
    private val tokenProvider: () -> String?,
    private val api: UserRelationshipApi = SharkeyUserRelationshipApi(),
) {
    open suspend fun loadRelation(userId: String): UserRelationshipRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserRelationshipRepositoryResult.Unauthorized
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return UserRelationshipRepositoryResult.Error("无法操作用户")

        return when (val result = api.loadRelation(token, cleanUserId)) {
            is UserRelationshipLoadResult.Success -> {
                UserRelationshipRepositoryResult.RelationLoaded(result.relationship)
            }
            UserRelationshipLoadResult.Unauthorized -> UserRelationshipRepositoryResult.Unauthorized
            is UserRelationshipLoadResult.NetworkError -> {
                UserRelationshipRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserRelationshipLoadResult.ServerError -> UserRelationshipRepositoryResult.Error(result.message)
        }
    }

    open suspend fun loadMutedUsers(
        currentEntries: List<UserRelationshipListEntry> = emptyList(),
    ): UserRelationshipListRepositoryResult {
        return loadList(currentEntries) { token, untilId ->
            api.loadMutedUsers(token, untilId = untilId)
        }
    }

    open suspend fun loadBlockedUsers(
        currentEntries: List<UserRelationshipListEntry> = emptyList(),
    ): UserRelationshipListRepositoryResult {
        return loadList(currentEntries) { token, untilId ->
            api.loadBlockedUsers(token, untilId = untilId)
        }
    }

    open suspend fun loadRenoteMutedUsers(
        currentEntries: List<UserRelationshipListEntry> = emptyList(),
    ): UserRelationshipListRepositoryResult {
        return loadList(currentEntries) { token, untilId ->
            api.loadRenoteMutedUsers(token, untilId = untilId)
        }
    }

    open suspend fun follow(
        userId: String,
        withReplies: Boolean? = null,
    ): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.follow(token, cleanUserId, withReplies = withReplies) }
    }

    open suspend fun unfollow(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.unfollow(token, cleanUserId) }
    }

    open suspend fun updateFollowing(
        userId: String,
        notify: String? = null,
        withReplies: Boolean? = null,
    ): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId ->
            api.updateFollowing(token, cleanUserId, notify = notify, withReplies = withReplies)
        }
    }

    open suspend fun updateAllFollowing(
        notify: String? = null,
        withReplies: Boolean? = null,
    ): UserRelationshipRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserRelationshipRepositoryResult.Unauthorized
        return when (val result = api.updateAllFollowing(token, notify = notify, withReplies = withReplies)) {
            UserRelationshipResult.Success -> UserRelationshipRepositoryResult.Success
            UserRelationshipResult.Unauthorized -> UserRelationshipRepositoryResult.Unauthorized
            is UserRelationshipResult.NetworkError -> {
                UserRelationshipRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserRelationshipResult.ServerError -> UserRelationshipRepositoryResult.Error(result.message)
        }
    }

    open suspend fun invalidateFollowing(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.invalidateFollowing(token, cleanUserId) }
    }

    open suspend fun mute(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.mute(token, cleanUserId) }
    }

    open suspend fun unmute(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.unmute(token, cleanUserId) }
    }

    open suspend fun block(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.block(token, cleanUserId) }
    }

    open suspend fun unblock(userId: String): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.unblock(token, cleanUserId) }
    }

    open suspend fun reportUser(
        userId: String,
        comment: String = "客户端举报用户",
    ): UserRelationshipRepositoryResult {
        return perform(userId) { token, cleanUserId -> api.reportUser(token, cleanUserId, comment) }
    }

    private suspend fun perform(
        userId: String,
        action: suspend (String, String) -> UserRelationshipResult,
    ): UserRelationshipRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserRelationshipRepositoryResult.Unauthorized
        val cleanUserId = userId.takeIf { it.isNotBlank() }
            ?: return UserRelationshipRepositoryResult.Error("无法操作用户")

        return when (val result = action(token, cleanUserId)) {
            UserRelationshipResult.Success -> UserRelationshipRepositoryResult.Success
            UserRelationshipResult.Unauthorized -> UserRelationshipRepositoryResult.Unauthorized
            is UserRelationshipResult.NetworkError -> {
                UserRelationshipRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserRelationshipResult.ServerError -> UserRelationshipRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadList(
        currentEntries: List<UserRelationshipListEntry>,
        action: suspend (String, String?) -> UserRelationshipListResult,
    ): UserRelationshipListRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserRelationshipListRepositoryResult.Unauthorized
        val untilId = currentEntries.lastOrNull()?.id

        return when (val result = action(token, untilId)) {
            is UserRelationshipListResult.Success -> {
                val merged = currentEntries.appendDistinctBy(result.entries) { it.user.id }
                UserRelationshipListRepositoryResult.Success(
                    entries = merged,
                    endReached = result.entries.isEmpty(),
                )
            }
            UserRelationshipListResult.Unauthorized -> UserRelationshipListRepositoryResult.Unauthorized
            is UserRelationshipListResult.NetworkError -> {
                UserRelationshipListRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserRelationshipListResult.ServerError -> {
                UserRelationshipListRepositoryResult.Error(result.message)
            }
        }
    }
}

sealed interface UserRelationshipRepositoryResult {
    data object Success : UserRelationshipRepositoryResult

    data class RelationLoaded(val relationship: UserRelationship) : UserRelationshipRepositoryResult

    data object Unauthorized : UserRelationshipRepositoryResult

    data class Error(val message: String) : UserRelationshipRepositoryResult
}

sealed interface UserRelationshipListRepositoryResult {
    data class Success(
        val entries: List<UserRelationshipListEntry>,
        val endReached: Boolean,
    ) : UserRelationshipListRepositoryResult

    data object Unauthorized : UserRelationshipListRepositoryResult

    data class Error(val message: String) : UserRelationshipListRepositoryResult
}
