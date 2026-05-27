package cc.hhhl.client.state

import cc.hhhl.client.model.Achievement
import cc.hhhl.client.repository.AchievementClaimRepositoryResult
import cc.hhhl.client.repository.AchievementRepository
import cc.hhhl.client.repository.AchievementRepositoryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementUiState(
    val achievements: List<Achievement> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val requiresRelogin: Boolean = false,
) {
    val unlockedCount: Int
        get() = achievements.count { it.isUnlocked }

    val totalCount: Int
        get() = achievements.size
}

class AchievementStateHolder(
    private val repository: AchievementRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AchievementUiState())
    val state: StateFlow<AchievementUiState> = mutableState

    private var viewAchievementClaimRequested = false

    fun refresh() {
        if (state.value.isLoading) return

        mutableState.update {
            it.copy(isLoading = true, errorMessage = null, requiresRelogin = false)
        }

        scope.launch {
            applyResult(repository.refresh())
        }
    }

    fun claimViewAchievementsMilestone() {
        if (viewAchievementClaimRequested) return
        viewAchievementClaimRequested = true

        scope.launch {
            when (repository.claim("viewAchievements3min")) {
                AchievementClaimRepositoryResult.Success -> refresh()
                AchievementClaimRepositoryResult.Unauthorized -> mutableState.update {
                    it.copy(
                        errorMessage = "登录已失效，请重新登录",
                        requiresRelogin = true,
                    )
                }
                is AchievementClaimRepositoryResult.Error -> Unit
            }
        }
    }

    private fun applyResult(result: AchievementRepositoryResult) {
        when (result) {
            is AchievementRepositoryResult.Success -> mutableState.update {
                it.copy(
                    achievements = result.achievements,
                    isLoading = false,
                    errorMessage = null,
                    requiresRelogin = false,
                )
            }
            AchievementRepositoryResult.Unauthorized -> mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "登录已失效，请重新登录",
                    requiresRelogin = true,
                )
            }
            is AchievementRepositoryResult.Error -> mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.message,
                    requiresRelogin = false,
                )
            }
        }
    }
}
