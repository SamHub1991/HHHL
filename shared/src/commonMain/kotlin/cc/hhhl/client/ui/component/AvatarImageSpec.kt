package cc.hhhl.client.ui.component

data class AvatarImageSpec(
    val remoteUrl: String?,
    val fallbackInitial: String,
)

@Suppress("UNUSED_PARAMETER")
fun avatarImageSpec(
    initial: String,
    avatarUrl: String?,
): AvatarImageSpec {
    return AvatarImageSpec(
        remoteUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() },
        fallbackInitial = "H",
    )
}
