package cc.hhhl.client.model

import androidx.compose.runtime.Immutable

@Immutable
data class FollowRequest(
    val id: String,
    val user: User,
)
