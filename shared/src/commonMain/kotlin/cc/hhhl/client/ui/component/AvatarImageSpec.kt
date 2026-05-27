package cc.hhhl.client.ui.component

data class AvatarImageSpec(
    val remoteUrl: String?,
    val fallbackUrl: String?,
)

@Suppress("UNUSED_PARAMETER")
fun avatarImageSpec(
    initial: String,
    avatarUrl: String?,
): AvatarImageSpec {
    val remoteUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() }
    return AvatarImageSpec(
        remoteUrl = remoteUrl,
        fallbackUrl = if (remoteUrl == null) HHHL_BRAND_AVATAR_URL else null,
    )
}

const val HHHL_BRAND_AVATAR_URL = "https://dc.hhhl.cc/client-assets/icon.png"
