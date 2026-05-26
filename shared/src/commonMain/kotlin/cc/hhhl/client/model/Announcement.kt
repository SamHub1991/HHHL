package cc.hhhl.client.model

data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val imageUrl: String?,
    val icon: String,
    val display: String,
    val needConfirmationToRead: Boolean,
    val silence: Boolean,
    val confetti: Boolean,
    val forYou: Boolean,
    val isRead: Boolean,
    val createdAtLabel: String = "",
    val updatedAtLabel: String = "",
) {
    val iconLabel: String
        get() = when (icon) {
            "warning" -> "警告"
            "error" -> "错误"
            "success" -> "成功"
            else -> "信息"
        }

    val displayLabel: String
        get() = when (display) {
            "dialog" -> "弹窗"
            "banner" -> "横幅"
            "normal" -> "普通"
            else -> display.ifBlank { "公告" }
        }
}
