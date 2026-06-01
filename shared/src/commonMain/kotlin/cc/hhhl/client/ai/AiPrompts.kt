package cc.hhhl.client.ai

data class AiPrompt(
    val system: String,
    val user: String,
    val maxOutputTokens: Int,
)

object AiPromptBuilder {
    fun build(settings: AiSettings, kind: AiTaskKind, input: AiTaskInput): AiPrompt {
        val compact = input.compact(settings.maxInputChars.coerceIn(1_000, 40_000))
        val system = settings.systemPrompt.ifBlank { DEFAULT_AI_SYSTEM_PROMPT }
        val tone = settings.tonePreference.ifBlank { "自然、简洁、贴近当前语气" }
        return AiPrompt(
            system = system,
            user = when (kind) {
                AiTaskKind.ComposePolish -> composePrompt(
                    instruction = "润色下面的发帖草稿，保留原意和语言风格，只输出润色后的正文。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeShorten -> composePrompt(
                    instruction = "把下面的发帖草稿压缩得更短，保留关键信息，只输出缩短后的正文。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeExpand -> composePrompt(
                    instruction = "扩写下面的发帖草稿，让表达更完整自然，只输出扩写后的正文。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeTranslateZh -> composePrompt(
                    instruction = "把下面内容翻译成自然中文，只输出译文。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeContentWarning -> composePrompt(
                    instruction = "为下面内容生成一个简短内容警告或摘要标题，最多 28 个中文字符，只输出内容警告。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeHashtags -> composePrompt(
                    instruction = "为下面内容推荐 3 到 6 个话题标签，格式为 #标签，逗号分隔，只输出标签。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeMentionSuggestions -> composePrompt(
                    instruction = "根据上下文推荐 1 到 5 个可能适合 @ 的对象或角色。若上下文没有明确对象，输出“不建议 @”。只输出简短清单。",
                    tone = tone,
                    input = compact,
                )
                AiTaskKind.ComposeFromRecentPosts -> composeFromRecentPostsPrompt(tone, compact)
                AiTaskKind.PostSummary -> postPrompt(
                    instruction = "总结这条帖子，列出核心观点、情绪/立场、是否需要回应。用 3 到 5 行中文输出。",
                    input = compact,
                )
                AiTaskKind.PostReplyDraft -> postPrompt(
                    instruction = "基于这条帖子写一个自然、礼貌、简洁的回复草稿。只输出回复正文，不要自动 @ 人。",
                    input = compact,
                )
                AiTaskKind.ThreadSummary -> timelinePrompt(
                    instruction = "总结这个帖子线程：主帖观点、上下文、回复里的共识/分歧、需要我处理或回应的点。用 5 到 8 条短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ThreadReplyDraft -> timelinePrompt(
                    instruction = "基于这个帖子线程写一个适合参与讨论的回复草稿。参考上下文和回复，避免重复已有人说过的内容，只输出回复正文。",
                    input = compact,
                )
                AiTaskKind.TimelineDigest -> timelinePrompt(
                    instruction = "总结当前时间线：主要话题、重要更新、争议点、值得稍后看的内容。用 5 到 8 条短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.TimelineReplyOpportunities -> timelinePrompt(
                    instruction = "从当前时间线找出适合我互动的机会：可回复的问题、可点赞/收藏的内容、可关注的人。不要自动执行，只输出建议清单。",
                    input = compact,
                )
                AiTaskKind.TimelineFilterSuggestions -> timelinePrompt(
                    instruction = "分析当前时间线里哪些内容可能适合设置静音词、屏蔽实例、稍后查看或建立搜索。不要执行任何过滤动作，只输出建议清单，并说明原因。",
                    input = compact,
                )
                AiTaskKind.ChatSummary -> chatPrompt(
                    instruction = "总结这段聊天：重要结论、待办、谁提到了我或需要我回复。用短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ChatRecentSummary -> chatPrompt(
                    instruction = "总结最近 50 条聊天：主要话题、重要结论、待办、需要我回复或关注的人。用短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ChatTodaySummary -> chatPrompt(
                    instruction = "总结今天的聊天：按时间顺序归纳发生了什么、当前结论、待办和需要我处理的点。只依据给出的今日消息；如果上下文说明没有可解析的今日消息，要明确说明。",
                    input = compact,
                )
                AiTaskKind.ChatUnreadSummary -> chatPrompt(
                    instruction = "总结我未读期间发生了什么：先说最重要的变化，再列出需要我回复、确认或稍后处理的消息。只依据给出的未读消息；如果没有未读消息，要明确说明。",
                    input = compact,
                )
                AiTaskKind.ChatReplyDraft -> chatPrompt(
                    instruction = "根据这段聊天生成一个适合发送的回复草稿。只输出回复正文。",
                    input = compact,
                )
                AiTaskKind.ChatActionItems -> chatPrompt(
                    instruction = "从这段聊天提取待办、负责人、截止时间、阻塞点和需要我跟进的消息。没有明确负责人时写“未指定”。用短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ChatDecisionSummary -> chatPrompt(
                    instruction = "整理这段聊天中的决策、备选方案、尚未决定的问题和下一步。只依据聊天内容，不要编造。用短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ChatImportanceCheck -> chatPrompt(
                    instruction = "判断这批聊天消息是否值得立刻通知我。只有出现明确提到我、需要我处理、强时效任务、重要决策、异常风险或指定关注对象时才算重要。第一行只输出 Important: true 或 Important: false，第二行用一句话说明原因。",
                    input = compact,
                )
                AiTaskKind.NotificationSummary -> notificationPrompt(
                    instruction = "总结这些通知，按重要程度归纳发生了什么，哪些可以忽略，哪些需要处理。",
                    input = compact,
                )
                AiTaskKind.NotificationFollowUp -> notificationPrompt(
                    instruction = "找出这些通知里最可能需要我回复、关注、处理或稍后看的项目，输出简短清单。",
                    input = compact,
                )
                AiTaskKind.NotificationPriority -> notificationPrompt(
                    instruction = "按高/中/低优先级整理这些通知，说明原因和建议动作。不要编造未出现的信息。",
                    input = compact,
                )
                AiTaskKind.ProfileSummary -> profilePrompt(
                    instruction = "总结这个用户资料和最近可见帖子：身份/兴趣、近期话题、互动风格、需要注意的风险。用 5 到 8 条短项目符号输出。",
                    input = compact,
                )
                AiTaskKind.ProfileInteractionSuggestions -> profilePrompt(
                    instruction = "基于这个用户资料和最近可见帖子，给出互动建议：是否适合关注、私聊、特别关心、加入列表、静音或避免互动。不要执行任何动作，只输出理由和建议。",
                    input = compact,
                )
                AiTaskKind.AutomationSemanticCondition -> automationConditionPrompt(compact)
                AiTaskKind.AutomationGeneratedAction -> automationActionPrompt(compact)
                AiTaskKind.AutomationExplain -> automationExplainPrompt(compact)
                AiTaskKind.AutomationRuleSuggestions -> automationRuleSuggestionsPrompt(compact)
                AiTaskKind.AutomationRuleDraft -> automationRuleDraftPrompt(compact)
                AiTaskKind.AssistantChat -> assistantChatPrompt(compact)
                AiTaskKind.WorkspaceActionPlan -> workspaceActionPlanPrompt(compact)
                AiTaskKind.ConnectionTest -> "请只回复 OK。"
            },
            maxOutputTokens = when (kind) {
                AiTaskKind.ComposeContentWarning -> 80
                AiTaskKind.ComposeHashtags -> 120
                AiTaskKind.ComposeMentionSuggestions -> 160
                AiTaskKind.ComposeFromRecentPosts -> settings.maxOutputTokens.coerceIn(160, 1_200)
                AiTaskKind.AutomationSemanticCondition -> 96
                AiTaskKind.ChatImportanceCheck -> 80
                AiTaskKind.AutomationRuleDraft -> settings.maxOutputTokens.coerceIn(320, 1_600)
                AiTaskKind.AssistantChat -> settings.maxOutputTokens.coerceIn(240, 2_000)
                AiTaskKind.WorkspaceActionPlan -> settings.maxOutputTokens.coerceIn(320, 4_000)
                AiTaskKind.ConnectionTest -> 16
                else -> settings.maxOutputTokens.coerceIn(64, 4_000)
            },
        )
    }

    private fun composePrompt(instruction: String, tone: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine("语气偏好：$tone")
            if (input.noteText.isNotBlank()) {
                appendLine()
                appendLine("回复/引用上下文：")
                appendLine(input.noteText)
            }
            appendLine()
            appendLine("草稿：")
            appendLine(input.text.ifBlank { input.noteText })
        }.trim()
    }

    private fun postPrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine()
            appendLine("作者：${input.noteAuthor.ifBlank { "未知" }}")
            appendLine("帖子：")
            appendLine(input.noteText.ifBlank { input.text })
            if (input.quotedNoteText.isNotBlank()) {
                appendLine()
                appendLine("引用内容：")
                appendLine(input.quotedNoteText)
            }
        }.trim()
    }

    private fun timelinePrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine("时间线：${input.timelineTitle.ifBlank { input.title.ifBlank { "当前时间线" } }}")
            appendLine()
            input.timelineNotes.forEachIndexed { index, note ->
                val time = note.createdAtLabel.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                val user = note.username.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
                val noteId = note.id.takeIf { it.isNotBlank() }?.let { " [ID: $it]" }.orEmpty()
                appendLine("${index + 1}. ${time}${note.author} $user$noteId：${note.text}")
                if (note.stats.isNotBlank()) appendLine("   ${note.stats}")
            }
        }.trim()
    }

    private fun composeFromRecentPostsPrompt(tone: String, input: AiTaskInput): String {
        return buildString {
            appendLine("结合我最近看到/发布的帖子上下文，生成一条适合现在发布的新帖子草稿。")
            appendLine("要求：贴近当前账号语气，不要编造未出现的信息，不要复述原文；如果有草稿，优先保留草稿意图并补充最近帖子里的相关上下文。")
            appendLine("语气偏好：$tone")
            appendLine("只输出可直接放进发帖框的正文。")
            if (input.text.isNotBlank()) {
                appendLine()
                appendLine("当前草稿：")
                appendLine(input.text)
            }
            if (input.noteText.isNotBlank()) {
                appendLine()
                appendLine("回复/引用上下文：")
                appendLine(input.noteText)
            }
            if (input.timelineNotes.isNotEmpty()) {
                appendLine()
                appendLine("最近帖子：")
                input.timelineNotes.forEachIndexed { index, note ->
                    val time = note.createdAtLabel.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                    val user = note.username.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
                    val noteId = note.id.takeIf { it.isNotBlank() }?.let { " [ID: $it]" }.orEmpty()
                    appendLine("${index + 1}. ${time}${note.author} $user$noteId：${note.text}")
                }
            }
        }.trim()
    }

    private fun chatPrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine("聊天：${input.chatTitle.ifBlank { "当前会话" }}")
            if (input.prompt.isNotBlank()) appendLine(input.prompt)
            appendLine()
            input.chatMessages.forEach { message ->
                val time = message.createdAtLabel.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                appendLine("$time${message.sender}: ${message.text}")
            }
        }.trim()
    }

    private fun notificationPrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine()
            input.notifications.forEach { item ->
                val time = item.createdAtLabel.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                val noteId = item.noteId.takeIf { it.isNotBlank() }?.let { " [帖子ID: $it]" }.orEmpty()
                appendLine("$time${item.type} · ${item.actor}$noteId: ${item.text}")
                if (item.notePreviewText.isNotBlank()) appendLine("帖子预览：${item.notePreviewText}")
            }
        }.trim()
    }

    private fun profilePrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine()
            input.profile?.let { profile ->
                appendLine("用户：${profile.displayName} @${profile.username}")
                if (profile.host.isNotBlank()) appendLine("实例：${profile.host}")
                if (profile.stats.isNotBlank()) appendLine("统计：${profile.stats}")
                if (profile.relationship.isNotBlank()) appendLine("关系：${profile.relationship}")
                if (profile.bio.isNotBlank()) {
                    appendLine("简介：")
                    appendLine(profile.bio)
                }
            }
            if (input.timelineNotes.isNotEmpty()) {
                appendLine()
                appendLine("最近可见帖子：")
                input.timelineNotes.forEachIndexed { index, note ->
                    val time = note.createdAtLabel.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
                    val noteId = note.id.takeIf { it.isNotBlank() }?.let { " [ID: $it]" }.orEmpty()
                    appendLine("${index + 1}. ${time}${note.text}$noteId")
                    if (note.stats.isNotBlank()) appendLine("   ${note.stats}")
                }
            }
        }.trim()
    }


    private fun automationConditionPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("判断自动化事件是否满足用户定义的自动化语义条件。")
            appendLine("第一行必须固定输出 RESULT: YES 或 RESULT: NO，第二行用一句话说明理由。")
            appendLine("只要事件语义、意图或同义表达能合理满足条件，就输出 RESULT: YES；只有明显无关、相反或证据不足时才输出 RESULT: NO。")
            appendLine("不要要求关键词完全一致，不要因为措辞不同就判失败。")
            appendLine("条件：${input.prompt.ifBlank { input.text }}")
            appendLine()
            appendLine("事件：")
            appendLine(input.automationEventText.ifBlank { input.text })
        }.trim()
    }

    private fun automationActionPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("根据自动化事件生成一段可直接执行的简短文本，可能会被用于日志、通知、Webhook、聊天回复或帖子回复。只输出正文。")
            appendLine("如果上下文不应该自动回复、自动引用或自动执行，输出 SKIP。")
            if (input.prompt.isNotBlank()) appendLine("要求：${input.prompt}")
            appendLine()
            appendLine("事件：")
            appendLine(input.automationEventText.ifBlank { input.text })
        }.trim()
    }

    private fun automationExplainPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("解释这个自动化事件可能匹配哪些规则、适合做什么低风险动作、哪些动作需要人工确认。用短项目符号输出。")
            if (input.prompt.isNotBlank()) appendLine("规则/要求：${input.prompt}")
            appendLine()
            appendLine("事件：")
            appendLine(input.automationEventText.ifBlank { input.text })
        }.trim()
    }

    private fun automationRuleSuggestionsPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("你在 HHHL 自动化中心里帮助用户设计规则。根据现有规则、执行日志和用户目标，提出更智能但安全的自动化方案。")
            appendLine("每条建议必须包含：触发器、条件、动作、安全级别、为什么有用、需要用户确认的部分。")
            appendLine("优先建议低风险动作：本地日志、系统通知、填草稿、生成 Webhook 正文。不要建议默认自动发送、删除、屏蔽、举报或批量操作。")
            appendLine("输出 3 到 6 条，使用短项目符号，便于用户手动创建规则。")
            if (input.prompt.isNotBlank()) appendLine("目标：${input.prompt}")
            appendLine()
            appendLine("自动化上下文：")
            appendLine(input.automationEventText.ifBlank { input.text })
        }.trim()
    }

    private fun automationRuleDraftPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("把用户的一句话目标转换为 HHHL 自动化规则草案。只输出一个 JSON 对象，不要 Markdown，不要解释。")
            appendLine("JSON 字段：name, enabled, trigger, conditionMode, conditions, actionMode, actions, ignoreOwnMessages, cooldownSeconds, safetyNote。enabled 默认输出 true，除非用户明确说只生成禁用草稿。")
            appendLine("trigger 只能是 ChatMessage、ChatAttention、TimelineNote、Notification、SpecialCare。")
            appendLine("conditionMode/actionMode 只能是 All、Any / Sequential、Parallel。")
            appendLine("conditions 每项字段：type, value, enabled。type 只能是 SenderUserId、SenderUserIds、SenderUsername、SenderNameContains、MessageContains、RoomId、RoomNameContains、DirectUserId、SourceKind、AttentionKind、NotificationType、ChannelId、ChannelNameContains、MessageType、TimelineKind、NoteVisibility、AiSemantic。")
            appendLine("如果你要表达名字，也可以在条件项里输出 roomName、userName、userNames、username、channelName、messageType、timelineKind、notificationType、attentionKind，解析器会转换成对应条件。")
            appendLine("actions 每项字段：type, targetId, titleTemplate, bodyTemplate, mentionSender, replyToEvent, quoteEvent, enabled, failurePolicy。Webhook 目标也可以用 webhookUrl 或 url，AI 要求也可以用 prompt/instruction，解析器会归一成 targetId/bodyTemplate。")
            appendLine("action.type 只能是 AddLog、SystemNotification、ForwardToRoom、AiForwardToRoom、ForwardToUser、ReplyToChat、AiReplyToChat、ReplyToNote、AiReplyToNote、QuoteNote、AiQuoteNote、RenoteNote、PostToChannel、CopyChannelLink、Webhook、AiGenerateLog、AiGenerateNotification、AiGenerateWebhook。")
            appendLine("不要编造 ID。只有当前上下文列出了名称 -> ID 时才使用 RoomId、SenderUserId/SenderUserIds、ChannelId；否则用 RoomNameContains、SenderNameContains/SenderUsername、ChannelNameContains，系统会再查服务器解析。")
            appendLine("聊天室消息：trigger=ChatMessage，聊天室来源必须加 {type: SourceKind, value: room}；指定聊天室优先 RoomId，不知道 ID 用 RoomNameContains。私聊来源用 SourceKind=direct，指定私聊用户用 DirectUserId 或 SenderUserId。")
            appendLine("某聊天室中某个/多个用户发消息：使用 ChatMessage + SourceKind=room + RoomId/RoomNameContains + SenderUserId/SenderUserIds 或 SenderNameContains。用户说全部/所有/任意用户时，不要添加发送者条件。")
            appendLine("聊天消息类型：用 MessageType，值可为 text、file、image、video、audio、reply、quote，多个值用逗号分隔。")
            appendLine("如果用户目标是聊天里“被 @ / 有人 @ 我 / @我时”触发，必须使用 trigger=ChatAttention，并添加条件 {type: AttentionKind, value: Mention, enabled: true}。如果要自动回复聊天，action.type 使用 AiReplyToChat，targetId 留空，replyToEvent 通常设为 true。")
            appendLine("聊天回复/引用提醒分别使用 ChatAttention + AttentionKind=Reply/Quote；聊天特别关心消息使用 ChatAttention + AttentionKind=SpecialCare，或用户明确说特别关心时使用 SpecialCare。")
            appendLine("帖子流/某人发帖/某频道有帖子：使用 trigger=TimelineNote；指定发帖用户用 SenderUserId 或 SenderNameContains；指定频道优先 ChannelId，不知道 ID 用 ChannelNameContains，并加 TimelineKind=Channel；帖子消息类型也用 MessageType，值可含 text、image、file、poll、reply、quote。")
            appendLine("关注、帖子回复、帖子提及、帖子引用等来自通知的事件：使用 trigger=Notification，并添加 NotificationType=Follow/Reply/Mention/Quote/Renote/Reaction 等。不要把聊天 @ 误生成为 Notification。")
            appendLine("failurePolicy 只能是 Continue 或 Stop。")
            appendLine("用户说“发送到 Webhook/回调/HTTP”时，action.type=Webhook，targetId 填用户给出的 Webhook URL，bodyTemplate 放要发送的模板。用户说“用 AI 提取/总结后发送到 Webhook”时，action.type=AiGenerateWebhook，targetId 填 Webhook URL，bodyTemplate 放 AI 提取要求，例如只输出 JSON。")
            appendLine("优先生成低风险草案：AddLog、SystemNotification、AiGenerateLog、AiGenerateNotification。涉及自动发送、转发、引用、Webhook、剪贴板、频道发帖时，必须写清条件、加冷却，并在 safetyNote 说明需要用户确认。")
            appendLine("不要输出删除、屏蔽、举报、关注、批量操作。动作目标 targetId 可以用 room:聊天室名、user:用户名、channel:频道名，系统会解析；若无法确定则留空并在 safetyNote 说明。")
            appendLine("模板变量可用：{{sender.name}}、{{sender.username}}、{{sender.mention}}、{{message.text}}、{{message.id}}、{{message.type}}、{{room.id}}、{{room.name}}、{{direct.user.id}}、{{notification.text}}、{{note.id}}、{{note.link}}、{{channel.id}}、{{channel.name}}、{{channel.link}}、{{timeline.kind}}、{{event.body}}。")
            appendLine()
            appendLine("用户目标：")
            appendLine(input.prompt.ifBlank { input.text })
            if (input.automationEventText.isNotBlank()) {
                appendLine()
                appendLine("当前自动化上下文：")
                appendLine(input.automationEventText)
            }
        }.trim()
    }

    private fun assistantChatPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("你是 HHHL 客户端里的本地 AI 助手，运行在聊天页内，不是远程 Misskey 机器人账号。")
            appendLine("你可以帮助用户理解当前界面、整理聊天和通知、解释自动化规则、设计规则草稿、生成回复或发帖草稿。")
            appendLine("所有会修改状态或发送到外部的动作都只能建议给客户端执行，不能声称你自己已经执行。")
            appendLine("当用户要求执行动作时，按风险说明需要确认：只读、草稿、需确认、高风险。")
            appendLine("如果当前应用上下文显示高风险自动批准已开启，你可以直接给出删除、清空、退出、发送、发布等已支持动作建议；客户端会自动批准并执行，你只说明将由客户端执行。")
            appendLine("客户端会在你的回复下方显示可批准动作按钮，例如打开时间线/通知/聊天/设置/更新日志、刷新当前页、检查更新、创建自动化草稿、打开日志、生成聊天室摘要、填入或发送聊天草稿、按聊天室/私聊用户/姓名 @ 人发送消息、填入或发布发帖草稿、按频道/可见性/@ 人发布帖子、标记通知已读、添加静音词、复制清单、打开站内/网络搜索或保存记忆；你需要在正文里说明动作意图和风险。")
            appendLine("不要输出伪 JSON 工具调用，不要声称已经点击按钮；涉及发送消息、发布帖子、转发、删除、关注、屏蔽、举报、Webhook 或跨聊天室操作时，如果高风险自动批准未开启，必须提示用户批准。")
            appendLine("如果你的回复会让客户端填入草稿、创建自动化草稿、打开搜索、添加静音词、保存记忆或复制清单，必须在回复最后追加一个隐藏给客户端解析的结构化载荷块。")
            appendLine("载荷块格式必须严格如下，字段不需要时填空字符串，不要把解释写进字段值：")
            appendLine("```hhhl-assistant-payload")
            appendLine("{\"body\":\"\",\"targetRoom\":\"\",\"targetUser\":\"\",\"mentions\":[],\"channel\":\"\",\"visibility\":\"\",\"cw\":\"\",\"localOnly\":\"\",\"searchQuery\":\"\",\"automationGoal\":\"\",\"mutedWord\":\"\",\"memory\":\"\",\"checklist\":\"\"}")
            appendLine("```")
            appendLine("字段含义：body 只放要填进发帖/聊天框的正文；targetRoom 放目标聊天室名称或 ID；targetUser 放私聊收件人名称、用户名、@acct 或 ID；mentions 放正文需要 @ 的人名、显示名、用户名或 @acct 数组；channel 放目标频道名称或 ID；visibility 放 public/home/followers/specified 或中文公开/首页/关注者/指定；cw 放内容警告；localOnly 放 true/false；searchQuery 只放搜索关键词；automationGoal 只放要创建规则的目标描述；mutedWord 只放一个静音词；memory 只放要保存的偏好；checklist 只放可复制清单。")
            appendLine("当用户用名字说“@某人”时，把原始名字写进 mentions，不要要求用户提供 ID；如果上下文有名称 -> ID，可优先用 ID 或 @acct。目标聊天室、目标用户、频道同理，名字不确定时保留用户说的名字，客户端会继续本地匹配或搜索。")
            appendLine("回答要简洁、直接，并尽量给出下一步可点击或可复制的内容。")
            if (input.automationEventText.isNotBlank()) {
                appendLine()
                appendLine("当前应用上下文：")
                appendLine(input.automationEventText)
            }
            if (input.prompt.isNotBlank()) {
                appendLine()
                appendLine("本轮用户消息：")
                appendLine(input.prompt)
            }
            if (input.text.isNotBlank()) {
                appendLine()
                appendLine("最近对话记忆：")
                appendLine(input.text)
            }
        }.trim()
    }

    private fun workspaceActionPlanPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("你是 HHHL 客户端的全局行动规划助手。根据用户允许上传的当前上下文，整理接下来最值得处理的事项。")
            appendLine("输出必须分组：高优先级、可稍后处理、可自动化、可忽略、需要用户确认。")
            appendLine("每条建议包含：来源、原因、建议动作、是否需要打开帖子/聊天/通知/自动化中心。")
            appendLine("可以建议填草稿、@ 某人、回复某条、建立自动化规则或调整过滤词，但不要声称已经执行。")
            appendLine("发送、删除、屏蔽、举报、静音、关注、批量操作和外部 Webhook 都必须写明需要用户确认。")
            if (input.prompt.isNotBlank()) appendLine("目标：${input.prompt}")
            appendLine()
            appendLine("全局上下文：")
            appendLine(input.automationEventText.ifBlank { input.text })
        }.trim()
    }
}

fun String.aiSemanticYes(): Boolean {
    val clean = trim()
    if (clean.isBlank()) return false
    val decisionText = clean
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .ifBlank { clean }
        .take(160)
        .lowercase()
    val compact = decisionText
        .replace(" ", "")
        .replace("\t", "")
        .replace("_", "")
        .replace("-", "")
    if (aiSemanticNoPattern.containsMatchIn(compact)) return false
    if (aiSemanticYesPattern.containsMatchIn(compact)) return true
    return clean.take(240).lowercase().let { text ->
        !aiSemanticNoPattern.containsMatchIn(text.replace(" ", "")) &&
            aiSemanticYesPattern.containsMatchIn(text.replace(" ", ""))
    }
}

private val aiSemanticYesPattern = Regex(
    """^(?:result[:：]?|结果[:：]?|结论[:：]?|判断[:：]?|answer[:：]?)?(?:yes|y|true|是|是的|符合|满足|已满足|命中|已命中|可以触发|应触发|应该触发|触发|相关|有关|算是|属于)""",
)

private val aiSemanticNoPattern = Regex(
    """^(?:result[:：]?|结果[:：]?|结论[:：]?|判断[:：]?|answer[:：]?)?(?:no|n|false|否|不是|不符合|未符合|不满足|未满足|不命中|未命中|无关|不相关|无需触发|不触发|不能触发|不该触发|证据不足)""",
)
