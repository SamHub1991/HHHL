package cc.hhhl.client.navigation

import cc.hhhl.client.repository.UserProfileRepositoryResult

sealed interface MentionNavigationTarget {
    data class UserProfile(val userId: String) : MentionNavigationTarget
    data class DiscoverSearch(val username: String) : MentionNavigationTarget
}

fun mentionNavigationTarget(
    username: String,
    result: UserProfileRepositoryResult,
): MentionNavigationTarget {
    return when (result) {
        is UserProfileRepositoryResult.Success -> MentionNavigationTarget.UserProfile(result.user.id)
        UserProfileRepositoryResult.Unauthorized,
        is UserProfileRepositoryResult.Error,
        -> MentionNavigationTarget.DiscoverSearch(username.trim().removePrefix("@"))
    }
}
