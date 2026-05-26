package cc.hhhl.client.repository

import cc.hhhl.client.api.ComposeApi
import cc.hhhl.client.api.ComposeCreateResult
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.SharkeyComposeApi

open class ComposeRepository(
    private val tokenProvider: () -> String?,
    private val api: ComposeApi = SharkeyComposeApi(),
) {
    open suspend fun send(draft: ComposeDraft): ComposeRepositoryResult {
        if (draft.text.isBlank() && draft.fileIds.isEmpty()) {
            return ComposeRepositoryResult.ValidationError("内容不能为空")
        }

        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return ComposeRepositoryResult.Unauthorized

        return when (val result = api.createNote(token, draft)) {
            is ComposeCreateResult.Success -> ComposeRepositoryResult.Success(result.createdNoteId)
            ComposeCreateResult.Unauthorized -> ComposeRepositoryResult.Unauthorized
            is ComposeCreateResult.NetworkError -> ComposeRepositoryResult.Error("无法连接服务器：${result.message}")
            is ComposeCreateResult.ServerError -> ComposeRepositoryResult.Error(result.message)
        }
    }
}

sealed interface ComposeRepositoryResult {
    data class Success(val createdNoteId: String?) : ComposeRepositoryResult

    data object Unauthorized : ComposeRepositoryResult

    data class ValidationError(val message: String) : ComposeRepositoryResult

    data class Error(val message: String) : ComposeRepositoryResult
}
