package cc.hhhl.client.repository

import cc.hhhl.client.api.AchievementApi
import cc.hhhl.client.api.AchievementClaimResult
import cc.hhhl.client.api.AchievementLoadResult
import cc.hhhl.client.api.SharkeyAchievementApi
import cc.hhhl.client.model.Achievement

open class AchievementRepository(
    private val tokenProvider: () -> String?,
    private val userIdProvider: () -> String?,
    private val api: AchievementApi = SharkeyAchievementApi(),
) {
    open suspend fun refresh(): AchievementRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AchievementRepositoryResult.Unauthorized
        val userId = userIdProvider()?.takeIf { it.isNotBlank() }
            ?: return AchievementRepositoryResult.Unauthorized

        return when (val result = api.loadAchievements(token = token, userId = userId)) {
            is AchievementLoadResult.Success -> AchievementRepositoryResult.Success(result.achievements)
            AchievementLoadResult.Unauthorized -> AchievementRepositoryResult.Unauthorized
            is AchievementLoadResult.NetworkError -> AchievementRepositoryResult.Error("无法连接服务器：${result.message}")
            is AchievementLoadResult.ServerError -> AchievementRepositoryResult.Error(result.message)
        }
    }

    open suspend fun claim(name: String): AchievementClaimRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return AchievementClaimRepositoryResult.Unauthorized

        return when (val result = api.claimAchievement(token = token, name = name)) {
            AchievementClaimResult.Success -> AchievementClaimRepositoryResult.Success
            AchievementClaimResult.Unauthorized -> AchievementClaimRepositoryResult.Unauthorized
            is AchievementClaimResult.NetworkError -> AchievementClaimRepositoryResult.Error("无法连接服务器：${result.message}")
            is AchievementClaimResult.ServerError -> AchievementClaimRepositoryResult.Error(result.message)
        }
    }
}

sealed interface AchievementRepositoryResult {
    data class Success(val achievements: List<Achievement>) : AchievementRepositoryResult
    data object Unauthorized : AchievementRepositoryResult
    data class Error(val message: String) : AchievementRepositoryResult
}

sealed interface AchievementClaimRepositoryResult {
    data object Success : AchievementClaimRepositoryResult
    data object Unauthorized : AchievementClaimRepositoryResult
    data class Error(val message: String) : AchievementClaimRepositoryResult
}
