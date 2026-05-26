package cc.hhhl.client.model

enum class UserSocialKind(val label: String) {
    Following("关注"),
    Followers("关注者"),
}

data class UserSocialItem(
    val id: String,
    val user: User,
)
