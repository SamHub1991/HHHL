package cc.hhhl.client.ui.component

data class AvatarImageSpec(
    val remoteUrl: String?,
    val fallbackUrl: String,
)

@Suppress("UNUSED_PARAMETER")
fun avatarImageSpec(
    initial: String,
    avatarUrl: String?,
): AvatarImageSpec {
    return AvatarImageSpec(
        remoteUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() },
        fallbackUrl = HHHL_BRAND_AVATAR_URL,
    )
}

const val HHHL_BRAND_AVATAR_URL = "https://dc.hhhl.cc/client-assets/icon.png"
