package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class Achievement(
    val name: String,
    val title: String,
    val description: String,
    val flavor: String = "",
    val rank: AchievementRank = AchievementRank.Bronze,
    val iconUrl: String? = null,
    val unlockedAt: String? = null,
    val unlockedAtLabel: String = "",
) {
    val isUnlocked: Boolean
        get() = unlockedAt != null
}

enum class AchievementRank(val label: String) {
    Bronze("铜"),
    Silver("银"),
    Gold("金"),
    Platinum("铂金"),
}
