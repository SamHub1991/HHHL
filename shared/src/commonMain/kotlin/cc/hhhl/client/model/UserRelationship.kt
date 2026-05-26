package cc.hhhl.client.model

data class UserRelationship(
    val userId: String,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val hasPendingFollowRequestFromYou: Boolean = false,
    val hasPendingFollowRequestToYou: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocking: Boolean = false,
    val isBlocked: Boolean = false,
)
