package cc.hhhl.client.update

data class AppReleaseNotes(
    val versionName: String,
    val title: String,
    val summary: String,
    val highlights: List<String>,
)

interface ReleaseNotesStore {
    fun loadLastShownVersion(): String?

    fun saveLastShownVersion(versionName: String)
}

object NoopReleaseNotesStore : ReleaseNotesStore {
    override fun loadLastShownVersion(): String? = null

    override fun saveLastShownVersion(versionName: String) = Unit
}

fun releaseNotesFor(versionName: String): AppReleaseNotes {
    val cleanVersion = versionName.trim().removePrefix("v").ifBlank { "当前版本" }
    return when (cleanVersion) {
        "0.4.1" -> AppReleaseNotes(
            versionName = cleanVersion,
            title = "HHHL $cleanVersion 更新内容",
            summary = "这版重点完善 AI 自动化规则生成和后台触发链路，并继续打磨更新安装体验。",
            highlights = listOf(
                "AI 自动化草稿现在能按真实聊天室名、用户名和频道名解析到对应 ID，生成后默认启用。",
                "自动化新增帖子和频道帖子触发，支持用户发帖、频道新帖、消息类型、可见性和时间线来源条件。",
                "聊天室消息自动化支持按聊天室、发送者、全部用户和图片/回复/引用等消息类型精细匹配。",
                "后台同步现在会按启用规则扫描指定用户、频道和时间线，减少漏触发并避免首次扫描刷历史消息。",
                "优化 GitHub Release 更新检查、下载校验和安装后的重开流程。",
            ),
        )
        "0.4.0" -> AppReleaseNotes(
            versionName = cleanVersion,
            title = "HHHL $cleanVersion 更新内容",
            summary = "这版重点补齐自动化、更新安装和聊天稳定性，第一次打开时展示一次。",
            highlights = listOf(
                "聊天里被 @、回复、引用或特别关心消息，现在可以触发 ChatAttention 自动化规则。",
                "自动化支持 AI 回复聊天、帖子回复、引用、Webhook、频道发帖和复制频道链接，并增加工具权限保护。",
                "应用内更新支持自动下载，下载完成后打开安装确认，安装完成后可自动重新打开。",
                "修复通知全部已读、聊天室未读较多时崩溃、MFM 大字体气泡溢出等问题。",
                "优化发现页卡片一致性、帖子稍后看按钮位置、聊天用户筛选和多处界面细节。",
            ),
        )
        else -> AppReleaseNotes(
            versionName = cleanVersion,
            title = "HHHL $cleanVersion 更新内容",
            summary = "HHHL 已更新到 $cleanVersion。",
            highlights = listOf(
                "包含稳定性修复、性能优化和交互体验改进。",
                "建议确认设置里的软件更新、通知和 AI 自动化权限是否符合你的使用方式。",
            ),
        )
    }
}
