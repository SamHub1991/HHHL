package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

enum class UserSocialKind(val label: String) {
    Following("关注"),
    Followers("关注者"),
}

@Immutable
data class UserSocialItem(
    val id: String,
    val user: User,
)
