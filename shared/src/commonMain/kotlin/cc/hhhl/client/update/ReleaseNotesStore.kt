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
    return knownReleaseNotes()
        .firstOrNull { notes -> notes.versionName == cleanVersion }
        ?: AppReleaseNotes(
            versionName = cleanVersion,
            title = "HHHL $cleanVersion 更新内容",
            summary = "HHHL 已更新到 $cleanVersion。",
            highlights = listOf(
                "包含稳定性修复、性能优化和交互体验改进。",
                "建议确认设置里的软件更新、通知和 AI 自动化权限是否符合你的使用方式。",
            ),
        )
}

fun releaseNotesTimeline(): List<AppReleaseNotes> = knownReleaseNotes()

private fun knownReleaseNotes(): List<AppReleaseNotes> {
    return listOf(
        AppReleaseNotes(
            versionName = "0.7.2",
            title = "HHHL 0.7.2 更新内容",
            summary = "这版集中修复多个页面切换和后台结果回写的稳定性问题，减少旧请求覆盖当前界面的情况。",
            highlights = listOf(
                "修复帖子详情页在切走又切回同一帖子后，旧翻译、回复、反应用户、编辑记录等异步结果可能覆盖新状态的问题。",
                "修复私聊会话切换后，旧消息加载请求返回时可能把当前私聊消息列表覆盖成旧内容的问题。",
                "修复频道列表刷新过程中手动切换频道后，刷新结果回来又把选中频道切回旧频道的问题。",
                "优化多处登录、未读、特别关心、AI 配置和自动化状态恢复逻辑，降低重新打开应用后的状态回退概率。",
                "补充相关竞态回归测试，并通过共享模块单测和 Android Debug 构建验证。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.7.1",
            title = "HHHL 0.7.1 更新内容",
            summary = "这版修复远程 AI 调用和助手误触发问题，并补齐聊天室完整公告入口。",
            highlights = listOf(
                "修复远程 AI 小助手调用 chat-stream 接口不可用的问题，现在会走正确的远程流式接口。",
                "优化 AI 助手提示词和动作解析，降低普通对话误触发打开页面、执行操作等动作的概率。",
                "聊天室右上角菜单新增“查看公告”，可打开独立公告页查看完整聊天室公告内容。",
                "聊天室公告只在房间公告非空时显示入口，完整公告页会保留原始长文本，不再使用列表里的省略预览。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.7.0",
            title = "HHHL 0.7.0 更新内容",
            summary = "这版继续把 AI 助手、自动化和后台消息链路做成更完整的工作流，并修复多处聊天体验问题。",
            highlights = listOf(
                "AI 助手支持图片、文件、表情和附件上下文，可直接把附件一起交给远程模型处理。",
                "AI 配置优先接入远程 gpt-5.5 流式模型，同时保留本地供应商配置作为高级模式。",
                "自动化 Webhook 增强消息原文、变量和附件传递，方便把聊天里的图片、文件和提取结果送到外部服务。",
                "优化后台消息同步和自动化补扫逻辑，降低不打开聊天室时漏触发特别关心、@、回复、引用和规则动作的概率。",
                "修复聊天室未读数在刷新后可能重新变未读、偶尔显示 99+ 的问题。",
                "聊天室列表的长简介和长分组名会自动压缩省略，不再把列表项撑得过高。",
                "自动化调试信息补充 AI 真实判断回复，便于排查规则为什么命中或没有执行。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.6.1",
            title = "HHHL 0.6.1 更新内容",
            summary = "这版修复聊天搜索选择和 AI 结果卡片恢复问题，减少重新打开界面后的误显示。",
            highlights = listOf(
                "修复聊天消息搜索里服务器下拉用户偶尔点了没反应的问题，选中的远程用户现在会稳定保留并开始筛选消息。",
                "聊天搜索按远程用户过滤时，如果当前页没有匹配但服务器还有更多结果，会继续自动加载后续消息。",
                "关闭过的 AI 结果卡片会被持久记录，重新打开聊天等界面时不会再把旧的已关闭卡片显示出来。",
                "新的 AI 生成结果仍会正常弹出，不受之前关闭旧卡片影响。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.6.0",
            title = "HHHL 0.6.0 更新内容",
            summary = "这版完善 AI 模型配置和自动化草稿生成链路，并修复聊天未读状态恢复问题。",
            highlights = listOf(
                "AI 配置页新增默认配置和自动化配置 Tab，可单独管理自动化草稿生成使用的 URL、Key 和模型名。",
                "自动化配置未启用时继续使用默认模型；启用后只影响“根据草稿生成规则”的模型调用。",
                "AI 自动化草稿解析和规则生成链路更稳，补齐模型配置持久化、草稿生成和执行相关测试。",
                "修复重新打开应用后，已读聊天室可能又恢复成 99+ 未读的问题。",
                "继续优化后台通知、实时同步和自动化触发恢复逻辑，降低后台漏触发概率。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.5.2",
            title = "HHHL 0.5.2 更新内容",
            summary = "这版继续修复后台实时收消息和自动化触发链路，并补充后台保活入口。",
            highlights = listOf(
                "后台收到聊天室和私聊新消息时，不再因为通知去重误挡自动化、特别关心、@、回复和引用触发。",
                "实时同步服务增加后台保活处理，降低应用退到后台后消息和自动化明显延迟的概率。",
                "设置页新增“后台实时保活”入口，可直接打开系统电池优化设置，方便把 HHHL 加入不受限制。",
                "修复后台同步拿到新消息后，只有重新打开聊天室才更容易触发自动化的问题。",
                "拆分主界面设置路由，避免设置功能继续增加后触发组合函数过大导致编译失败。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.5.1",
            title = "HHHL 0.5.1 更新内容",
            summary = "这版重点修复自动化在多入口收消息时可能重复触发的问题。",
            highlights = listOf(
                "自动化会持久记录已处理的消息、帖子和通知，同一条远程事件重启后也不会被同一规则重复执行。",
                "同一条聊天室或私聊消息从实时通道、后台轮询、打开聊天室懒加载等入口重复到达时，会按真实消息 ID 去重。",
                "修复手机后台同步拿到聊天室消息后，回到 App 仍可能看不到本地缓存新消息的问题。",
                "帖子按 noteId 去重，通知和其他事件按稳定事件 ID 去重，减少特别关心、@、回复、引用通知重复触发。",
                "手动模拟和手动重试不受持久去重影响，方便继续调试规则和重跑失败动作。",
                "补充自动化缓存编解码和跨恢复去重测试，提升后台实时链路稳定性。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.5.0",
            title = "HHHL 0.5.0 更新内容",
            summary = "这版把 AI 助手升级为全局入口和自动化中枢，并重点修复后台实时收消息链路。",
            highlights = listOf(
                "新增默认开启的 AI 小光球，可拖动贴边，点击语音输入，长按调整高风险自动批准等快捷设置。",
                "设置页新增 AI 助手入口和独立 AI 配置页，补充 Claude、小米等供应商和对应默认模型。",
                "AI 助手现在能理解发帖、指定聊天室发消息、私聊、按名字 @ 人、回复和引用等更复杂操作。",
                "不打开聊天室也会持续拉取远程消息，并触发特别关心、自动化、回复、引用和 @ 通知。",
                "修复高风险自动批准已开启时，AI 助手仍不能直接执行高风险动作的问题。",
                "聊天搜索作者和过滤隐藏用户支持服务器用户搜索，不再只显示已加载的少量候选。",
                "收藏页从稍后看升级为帖子/信息分段，支持收藏聊天室和私聊信息，并保留渲染格式和附件。",
                "完善 notes 相关接口、发现页和 Markdown 渲染操作，AI 总结卡片显示更稳定。",
                "聊天顶部状态提示支持关闭，移除聊天室创建提示，并补齐自建聊天室删除和管理入口。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.4.2",
            title = "HHHL 0.4.2 更新内容",
            summary = "这版修复聊天输入框和自建聊天室管理问题，并完善更新日志展示。",
            highlights = listOf(
                "修复发送聊天室消息成功后，输入框在保持焦点时可能没有自动清空的问题。",
                "自己创建但只有自己的聊天室，现在会稳定显示在主聊天室列表中。",
                "自建聊天室详情页会按拥有列表开放编辑、邀请和删除入口，避免 owner 信息兜底导致无法管理。",
                "删除聊天室成功后，会同步从主列表、我管理的聊天室和相关邀请状态中移除。",
                "设置页获取更新区域会显示当前版本，并可直接打开完整更新日志时间线。",
                "更新后首次打开会弹出本版本更新提示，并支持跳转到更新日志时间线。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.4.1",
            title = "HHHL 0.4.1 更新内容",
            summary = "这版重点完善 AI 自动化规则生成和后台触发链路，并继续打磨更新安装体验。",
            highlights = listOf(
                "AI 自动化草稿现在能按真实聊天室名、用户名和频道名解析到对应 ID，生成后默认启用。",
                "自动化新增帖子和频道帖子触发，支持用户发帖、频道新帖、消息类型、可见性和时间线来源条件。",
                "聊天室消息自动化支持按聊天室、发送者、全部用户和图片/回复/引用等消息类型精细匹配。",
                "后台同步现在会按启用规则扫描指定用户、频道和时间线，减少漏触发并避免首次扫描刷历史消息。",
                "优化 GitHub Release 更新检查、下载校验和安装后的重开流程。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.4.0",
            title = "HHHL 0.4.0 更新内容",
            summary = "这版重点补齐自动化、更新安装和聊天稳定性，第一次打开时展示一次。",
            highlights = listOf(
                "聊天里被 @、回复、引用或特别关心消息，现在可以触发 ChatAttention 自动化规则。",
                "自动化支持 AI 回复聊天、帖子回复、引用、Webhook、频道发帖和复制频道链接，并增加工具权限保护。",
                "应用内更新支持自动下载，下载完成后打开安装确认，安装完成后可自动重新打开。",
                "修复通知全部已读、聊天室未读较多时崩溃、MFM 大字体气泡溢出等问题。",
                "优化发现页卡片一致性、帖子稍后看按钮位置、聊天用户筛选和多处界面细节。",
            ),
        ),
    )
}
