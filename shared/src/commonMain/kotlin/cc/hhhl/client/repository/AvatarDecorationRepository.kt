package cc.hhhl.client.repository

import cc.hhhl.client.api.AvatarDecorationApi
import cc.hhhl.client.api.AvatarDecorationLoadResult
import cc.hhhl.client.api.SharkeyAvatarDecorationApi
import cc.hhhl.client.model.AvatarDecoration

open class AvatarDecorationRepository(
    private val tokenProvider: () -> String?,
    private val api: AvatarDecorationApi = SharkeyAvatarDecorationApi(),
) {
    open suspend fun load(): AvatarDecorationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AvatarDecorationRepositoryResult.Unauthorized
        return when (val result = api.load(token)) {
            is AvatarDecorationLoadResult.Success -> AvatarDecorationRepositoryResult.Success(result.decorations)
            AvatarDecorationLoadResult.Unauthorized -> AvatarDecorationRepositoryResult.Unauthorized
            is AvatarDecorationLoadResult.NetworkError -> AvatarDecorationRepositoryResult.Error("无法连接服务器：${result.message}")
            is AvatarDecorationLoadResult.ServerError -> AvatarDecorationRepositoryResult.Error(result.message)
        }
    }
}

sealed interface AvatarDecorationRepositoryResult {
    data class Success(val decorations: List<AvatarDecoration>) : AvatarDecorationRepositoryResult
    data object Unauthorized : AvatarDecorationRepositoryResult
    data class Error(val message: String) : AvatarDecorationRepositoryResult
}
