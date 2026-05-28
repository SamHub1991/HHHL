package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class TrendingHashtag(
    val tag: String,
    val chart: List<Int>,
    val usersCount: Int,
    val mentionedUsersCount: Int = 0,
    val mentionedLocalUsersCount: Int = 0,
    val mentionedRemoteUsersCount: Int = 0,
)
