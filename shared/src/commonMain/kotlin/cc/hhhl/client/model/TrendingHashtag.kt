package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class TrendingHashtag(
    val tag: String,
    val chart: List<Int>,
    val usersCount: Int,
)
