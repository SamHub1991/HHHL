package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyUserListApi
import cc.hhhl.client.api.UserListApi
import cc.hhhl.client.api.UserListActionResult
import cc.hhhl.client.api.UserListLoadResult
import cc.hhhl.client.api.UserListMutationResult
import cc.hhhl.client.api.UserListTimelineLoadResult
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft

open class UserListRepository(
    private val tokenProvider: () -> String?,
    private val api: UserListApi = SharkeyUserListApi(),
) {
    open suspend fun refreshLists(): UserListsRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListsRepositoryResult.Unauthorized

        return when (val result = api.loadLists(token)) {
            is UserListLoadResult.Success -> UserListsRepositoryResult.Success(result.lists)
            UserListLoadResult.Unauthorized -> UserListsRepositoryResult.Unauthorized
            is UserListLoadResult.NetworkError -> {
                UserListsRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserListLoadResult.ServerError -> UserListsRepositoryResult.Error(result.message)
        }
    }

    open suspend fun refreshTimeline(listId: String): UserListTimelineRepositoryResult {
        return loadTimeline(
            listId = listId,
            currentNotes = emptyList(),
            untilId = null,
        )
    }

    open suspend fun loadMoreTimeline(
        listId: String,
        currentNotes: List<Note>,
    ): UserListTimelineRepositoryResult {
        return loadTimeline(
            listId = listId,
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    open suspend fun createList(draft: UserListDraft): UserListMutationRepositoryResult {
        val cleanDraft = draft.cleaned()
        if (cleanDraft.name.isEmpty()) {
            return UserListMutationRepositoryResult.Error("请输入列表名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.createList(token, cleanDraft))
    }

    open suspend fun updateList(
        listId: String,
        draft: UserListDraft,
    ): UserListMutationRepositoryResult {
        val cleanListId = listId.trim()
        val cleanDraft = draft.cleaned()
        if (cleanListId.isEmpty()) {
            return UserListMutationRepositoryResult.Error("无法读取列表")
        }
        if (cleanDraft.name.isEmpty()) {
            return UserListMutationRepositoryResult.Error("请输入列表名称")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListMutationRepositoryResult.Unauthorized

        return mapMutationResult(api.updateList(token, cleanListId, cleanDraft))
    }

    open suspend fun deleteList(listId: String): UserListActionRepositoryResult {
        val cleanListId = listId.trim()
        if (cleanListId.isEmpty()) {
            return UserListActionRepositoryResult.Error("无法读取列表")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListActionRepositoryResult.Unauthorized

        return mapActionResult(api.deleteList(token, cleanListId))
    }

    open suspend fun addUserToList(
        listId: String,
        userId: String,
    ): UserListActionRepositoryResult {
        return mutateListMember(
            listId = listId,
            userId = userId,
            action = api::pushUser,
        )
    }

    open suspend fun removeUserFromList(
        listId: String,
        userId: String,
    ): UserListActionRepositoryResult {
        return mutateListMember(
            listId = listId,
            userId = userId,
            action = api::pullUser,
        )
    }

    private suspend fun mutateListMember(
        listId: String,
        userId: String,
        action: suspend (String, String, String) -> UserListActionResult,
    ): UserListActionRepositoryResult {
        val cleanListId = listId.trim()
        val cleanUserId = userId.trim()
        if (cleanListId.isEmpty()) {
            return UserListActionRepositoryResult.Error("无法读取列表")
        }
        if (cleanUserId.isEmpty()) {
            return UserListActionRepositoryResult.Error("请输入用户 ID")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListActionRepositoryResult.Unauthorized

        return mapActionResult(action(token, cleanListId, cleanUserId))
    }

    private fun mapActionResult(result: UserListActionResult): UserListActionRepositoryResult {
        return when (result) {
            UserListActionResult.Success -> UserListActionRepositoryResult.Success
            UserListActionResult.Unauthorized -> UserListActionRepositoryResult.Unauthorized
            is UserListActionResult.NetworkError -> {
                UserListActionRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserListActionResult.ServerError -> UserListActionRepositoryResult.Error(result.message)
        }
    }

    private suspend fun loadTimeline(
        listId: String,
        currentNotes: List<Note>,
        untilId: String?,
    ): UserListTimelineRepositoryResult {
        val cleanListId = listId.trim()
        if (cleanListId.isEmpty()) {
            return UserListTimelineRepositoryResult.Error("无法读取列表")
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserListTimelineRepositoryResult.Unauthorized

        return when (
            val result = api.loadListTimeline(
                token = token,
                listId = cleanListId,
                limit = DEFAULT_PAGE_SIZE,
                untilId = untilId,
                withRenotes = true,
                withFiles = false,
            )
        ) {
            is UserListTimelineLoadResult.Success -> UserListTimelineRepositoryResult.Success(
                notes = (currentNotes + result.notes).distinctBy { it.id },
                endReached = result.notes.isEmpty(),
            )
            UserListTimelineLoadResult.Unauthorized -> UserListTimelineRepositoryResult.Unauthorized
            is UserListTimelineLoadResult.NetworkError -> {
                UserListTimelineRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserListTimelineLoadResult.ServerError -> UserListTimelineRepositoryResult.Error(result.message)
        }
    }

    private fun mapMutationResult(result: UserListMutationResult): UserListMutationRepositoryResult {
        return when (result) {
            is UserListMutationResult.Success -> UserListMutationRepositoryResult.Success(result.list)
            UserListMutationResult.Unauthorized -> UserListMutationRepositoryResult.Unauthorized
            is UserListMutationResult.NetworkError -> {
                UserListMutationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserListMutationResult.ServerError -> UserListMutationRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

private fun UserListDraft.cleaned(): UserListDraft {
    return copy(name = name.trim())
}

sealed interface UserListsRepositoryResult {
    data class Success(val lists: List<UserList>) : UserListsRepositoryResult

    data object Unauthorized : UserListsRepositoryResult

    data class Error(val message: String) : UserListsRepositoryResult
}

sealed interface UserListTimelineRepositoryResult {
    data class Success(
        val notes: List<Note>,
        val endReached: Boolean = false,
    ) : UserListTimelineRepositoryResult

    data object Unauthorized : UserListTimelineRepositoryResult

    data class Error(val message: String) : UserListTimelineRepositoryResult
}

sealed interface UserListMutationRepositoryResult {
    data class Success(val list: UserList) : UserListMutationRepositoryResult

    data object Unauthorized : UserListMutationRepositoryResult

    data class Error(val message: String) : UserListMutationRepositoryResult
}

sealed interface UserListActionRepositoryResult {
    data object Success : UserListActionRepositoryResult

    data object Unauthorized : UserListActionRepositoryResult

    data class Error(val message: String) : UserListActionRepositoryResult
}
