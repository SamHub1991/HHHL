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
            versionName = "0.8.7",
            title = "HHHL 0.8.7 更新内容",
            summary = "这版修复聊天消息删除没有通过实时频道同步的问题。",
            highlights = listOf(
                "聊天实时流新增消息删除事件解析，兼容 chatMessageDeleted、messageDeleted、deleted 等事件名。",
                "聊天室和私聊收到删除事件后会同步移除当前消息、搜索结果和本地消息缓存。",
                "主实时流和 Android 后台实时通知服务也接入删除事件，避免删除被当作新消息通知。",
                "补充实时删除事件解析和聊天状态层回归测试。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.6",
            title = "HHHL 0.8.6 更新内容",
            summary = "这版修复聊天室渲染带颜色富文本时可能崩溃的问题。",
            highlights = listOf(
                "修复聊天室消息里 MFM/富文本颜色样式触发的 ArrayIndexOutOfBoundsException。",
                "颜色解析改为按 RRGGBB/RRGGBBAA 通道构造 Compose Color，避免生成无效颜色空间。",
                "补充富文本颜色回归测试，覆盖后续透明度 copy 的崩溃路径。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.5",
            title = "HHHL 0.8.5 更新内容",
            summary = "这版修复聊天里图片和文件附件发送不完整的问题。",
            highlights = listOf(
                "私聊发送图片/文件时会把全部已上传附件 fileIds 一起提交，不再只发送第一张图或第一个文件。",
                "聊天室和私聊发送附件统一兼容单附件 fileId 与多附件 fileIds，请求体更贴合 Sharkey 聊天接口。",
                "连续选择多张图片或多个文件后，发送成功会正常清空聊天输入框附件状态。",
                "补充聊天 API、仓库和状态层回归测试，覆盖私聊多附件和文件消息发送路径。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.4",
            title = "HHHL 0.8.4 更新内容",
            summary = "这版补齐回复编辑页里的 AI 回复入口，并让回复草稿生成更贴合原帖内容。",
            highlights = listOf(
                "在回复某条已加载帖子时，编辑页的更多菜单 AI 子菜单会显示“AI 回复”。",
                "AI 回复草稿会结合对方原帖、作者、引用内容和当前输入框里的回复意图生成，不再只处理当前草稿。",
                "统一帖子操作里的 AI 回复文案和图标映射，减少“回复草稿”和“AI 回复”混用。",
                "补充编辑页操作和 AI prompt 回归测试，避免后续改动丢失原帖上下文。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.3",
            title = "HHHL 0.8.3 更新内容",
            summary = "这版重点修复聊天加载消息时可能导致界面无响应、ANR 或崩溃的问题。",
            highlights = listOf(
                "聊天消息加载、缓存读写和消息合并改为后台 worker 执行，避免重活阻塞主界面线程。",
                "聊天后台任务增加串行保护，减少多路加载同时改状态带来的 ANR 和竞态风险。",
                "Android 聊天消息缓存改为按会话拆分存储，并移除旧的大体积单字段缓存，降低 SharedPreferences 锁等待和 GC 压力。",
                "修复进入聊天详情时初始滚动位置可能误触发连续加载更早消息的问题。",
                "发布前已通过 shared 全量单测、Android Debug 编译、Debug 模拟器安装和 Release 签名校验。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.2",
            title = "HHHL 0.8.2 更新内容",
            summary = "这版优化时间线新内容提示的位置，减少顶部标签栏拥挤。",
            highlights = listOf(
                "时间线顶部的新内容入口从 segmented 标签行移出，避免“新 N”按钮挤占首页、社交、本地等标签文本。",
                "新内容提示改为右上方半透明悬浮胶囊按钮，视觉更轻，也不会增加顶部行高。",
                "点击新内容悬浮按钮仍会跳转到第一条新内容，并保持原有的新内容标记消费逻辑。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.1",
            title = "HHHL 0.8.1 更新内容",
            summary = "这版修复自动化规则里“过滤自己发的消息”开关在部分动作下无法关闭的问题。",
            highlights = listOf(
                "修复带 AI 回复、转发、发帖、@ 等动作的自动化规则中，“过滤自己发的消息”关闭后又被保存逻辑改回开启的问题。",
                "保留高风险动作的默认冷却时间和频率限制，关闭自己消息过滤时仍然会保留基础防循环保护。",
                "补充自动化规则回归测试，避免后续风险控制清洗再次覆盖用户手动开关选择。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.8.0",
            title = "HHHL 0.8.0 更新内容",
            summary = "这版重做时间线、聊天、发现、通知和设置等核心界面，并把 AI 自动化扩展到生图、图生图和图片参数识别。",
            highlights = listOf(
                "时间线、聊天首页、通知页和发现页调整为更接近微信的紧凑布局，统一背景、标题栏、按钮组件和卡片密度。",
                "时间线帖子支持长内容折叠/展开，帖子操作按钮改为更小、更透明的一致规格，右下角发帖悬浮按钮进一步缩小并使用主题强调色。",
                "聊天页合并聊天室/用户入口，成员入口收进右上角菜单，创建房间入口收进更多菜单，减少顶部多余行。",
                "设置页压缩输入框、开关和附加按钮尺寸，修复局部色块、异常背景和过大的附件按钮等界面问题。",
                "AI 设置新增独立生图配置，可单独填写生图 Base URL、API Key 和模型，默认支持 gpt-image-2。",
                "自动化规则支持识别生成图片、编辑图片、图生图语义，并可从消息语义里提取尺寸、质量、背景、格式、压缩、张数和透明图等参数。",
                "自动化图片回复会继续遵循 @ 对方、回复触发消息、引用触发消息等动作配置，避免只发图片时漏掉原有回复要求。",
                "修复 shared 单测中底栏规格常量编译问题，并补充 AI 图片生成、编辑、规则草稿和界面呈现相关回归测试。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.7.5",
            title = "HHHL 0.7.5 更新内容",
            summary = "这版继续修复高风险异步回写和 AI 小助手展示问题，让旧请求结果更难污染当前界面。",
            highlights = listOf(
                "修复 AI 小助手显示远程 AI 返回 JSON 时只显示部分内容的问题，message、code、example、output 等字段会完整拆成多个气泡展示。",
                "AI 小助手代码块支持横向滚动，长代码和复杂格式不会再被气泡宽度截断。",
                "修复聊天室管理设置保存较慢时，用户已经切到其他聊天室后旧保存结果又把当前聊天室切回去的问题。",
                "继续补齐公告、天线、频道、聊天、剪辑和用户列表等页面的旧请求回写保护，降低页面切换后的竞态污染。",
                "补充对应回归测试，并通过 shared 全量单测和 Android Debug/Release 构建验证。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.7.4",
            title = "HHHL 0.7.4 更新内容",
            summary = "这版继续修复页面切换后旧请求回写当前界面的竞态问题，减少旧操作结果污染新打开内容。",
            highlights = listOf(
                "修复闪念编辑保存、网盘新建/删除文件夹、图库更新等后台结果返回较慢时，可能覆盖当前打开内容的问题。",
                "修复闪念、页面和图库详情点赞失败后，旧详情页错误提示可能显示到新详情页的问题。",
                "修复删除当前正在查看的网盘文件夹后，路径已回退但列表仍保留被删除文件夹内容的问题。",
                "补充对应竞态回归测试，并已先安装 Debug 包到模拟器验证。",
            ),
        ),
        AppReleaseNotes(
            versionName = "0.7.3",
            title = "HHHL 0.7.3 更新内容",
            summary = "这版作为 0.7 系列 patch 包，继续同步当前稳定修复和发布包。当前 debug 已先安装到模拟器验证。",
            highlights = listOf(
                "同步 0.7.2 已完成的帖子详情、私聊消息和频道刷新竞态修复到新的 patch 版本。",
                "保持 AI 配置、自动化、未读状态和更新日志入口等近期修复在当前发布包中可用。",
                "发布前已先构建并安装 Debug 版本到模拟器，再继续生成 Release 包。",
            ),
        ),
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
