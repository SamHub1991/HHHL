package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyUserNotesApi
import cc.hhhl.client.api.UserNotesApi
import cc.hhhl.client.api.UserNotesLoadResult
import cc.hhhl.client.model.Note

open class UserNotesRepository(
    private val tokenProvider: () -> String?,
    private val userIdProvider: () -> String?,
    private val api: UserNotesApi = SharkeyUserNotesApi(),
) {
    open suspend fun refresh(): UserNotesRepositoryResult {
        return load(currentNotes = emptyList(), untilId = null)
    }

    open suspend fun loadMore(
        currentNotes: List<Note>,
    ): UserNotesRepositoryResult {
        return load(
            currentNotes = currentNotes,
            untilId = currentNotes.lastOrNull()?.id,
        )
    }

    private suspend fun load(
        currentNotes: List<Note>,
        untilId: String?,
    ): UserNotesRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserNotesRepositoryResult.Unauthorized
        val userId = userIdProvider()?.takeIf { it.isNotBlank() }
            ?: return UserNotesRepositoryResult.Error("无法读取当前账号")

        return when (val result = api.loadUserNotes(token, userId, DEFAULT_PAGE_SIZE, untilId)) {
            is UserNotesLoadResult.Success -> UserNotesRepositoryResult.Success(
                notes = currentNotes.appendDistinctBy(result.notes) { it.id },
            )
            UserNotesLoadResult.Unauthorized -> UserNotesRepositoryResult.Unauthorized
            is UserNotesLoadResult.NetworkError -> {
                UserNotesRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserNotesLoadResult.ServerError -> UserNotesRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface UserNotesRepositoryResult {
    data class Success(val notes: List<Note>) : UserNotesRepositoryResult

    data object Unauthorized : UserNotesRepositoryResult

    data class Error(val message: String) : UserNotesRepositoryResult
}
