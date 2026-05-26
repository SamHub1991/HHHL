package cc.hhhl.client.model

data class TrendingHashtag(
    val tag: String,
    val chart: List<Int>,
    val usersCount: Int,
)
