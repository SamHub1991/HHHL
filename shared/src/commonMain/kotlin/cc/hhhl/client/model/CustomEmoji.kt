package cc.hhhl.client.model

data class CustomEmoji(
    val name: String,
    val category: String?,
    val url: String,
    val aliases: List<String>,
    val localOnly: Boolean,
    val isSensitive: Boolean,
) {
    val reactionCode: String
        get() = ":$name:"
}
