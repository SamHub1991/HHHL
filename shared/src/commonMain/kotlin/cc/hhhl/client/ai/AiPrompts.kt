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
                AiTaskKind.WorkspaceActionPlan -> workspaceActionPlanPrompt(compact)
                AiTaskKind.ConnectionTest -> "请只回复 OK。"
            },
            maxOutputTokens = when (kind) {
                AiTaskKind.ComposeContentWarning -> 80
                AiTaskKind.ComposeHashtags -> 120
                AiTaskKind.ComposeMentionSuggestions -> 160
                AiTaskKind.ComposeFromRecentPosts -> settings.maxOutputTokens.coerceIn(160, 1_200)
                AiTaskKind.AutomationSemanticCondition -> 40
                AiTaskKind.AutomationRuleDraft -> settings.maxOutputTokens.coerceIn(320, 1_600)
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
                appendLine("${index + 1}. ${time}${note.author} $user：${note.text}")
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
                    appendLine("${index + 1}. ${time}${note.author} $user：${note.text}")
                }
            }
        }.trim()
    }

    private fun chatPrompt(instruction: String, input: AiTaskInput): String {
        return buildString {
            appendLine(instruction)
            appendLine("聊天：${input.chatTitle.ifBlank { "当前会话" }}")
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
                appendLine("$time${item.type} · ${item.actor}: ${item.text}")
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
                    appendLine("${index + 1}. ${time}${note.text}")
                    if (note.stats.isNotBlank()) appendLine("   ${note.stats}")
                }
            }
        }.trim()
    }


    private fun automationConditionPrompt(input: AiTaskInput): String {
        return buildString {
            appendLine("判断自动化事件是否满足语义条件。必须先输出 YES 或 NO，第二行可以用一句话说明。")
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
            appendLine("actions 每项字段：type, targetId, titleTemplate, bodyTemplate, mentionSender, replyToEvent, quoteEvent, enabled, failurePolicy。")
            appendLine("action.type 只能是 AddLog、SystemNotification、ForwardToRoom、ForwardToUser、ReplyToChat、AiReplyToChat、ReplyToNote、AiReplyToNote、QuoteNote、AiQuoteNote、RenoteNote、PostToChannel、CopyChannelLink、Webhook、AiGenerateLog、AiGenerateNotification、AiGenerateWebhook。")
            appendLine("不要编造 ID。只有当前上下文列出了名称 -> ID 时才使用 RoomId、SenderUserId/SenderUserIds、ChannelId；否则用 RoomNameContains、SenderNameContains/SenderUsername、ChannelNameContains，系统会再查服务器解析。")
            appendLine("聊天室消息：trigger=ChatMessage，聊天室来源必须加 {type: SourceKind, value: room}；指定聊天室优先 RoomId，不知道 ID 用 RoomNameContains。私聊来源用 SourceKind=direct，指定私聊用户用 DirectUserId 或 SenderUserId。")
            appendLine("某聊天室中某个/多个用户发消息：使用 ChatMessage + SourceKind=room + RoomId/RoomNameContains + SenderUserId/SenderUserIds 或 SenderNameContains。用户说全部/所有/任意用户时，不要添加发送者条件。")
            appendLine("聊天消息类型：用 MessageType，值可为 text、file、image、video、audio、reply、quote，多个值用逗号分隔。")
            appendLine("如果用户目标是聊天里“被 @ / 有人 @ 我 / @我时”触发，必须使用 trigger=ChatAttention，并添加条件 {type: AttentionKind, value: Mention, enabled: true}。如果要自动回复聊天，action.type 使用 AiReplyToChat，targetId 留空，replyToEvent 通常设为 true。")
            appendLine("聊天回复/引用提醒分别使用 ChatAttention + AttentionKind=Reply/Quote；聊天特别关心消息使用 ChatAttention + AttentionKind=SpecialCare，或用户明确说特别关心时使用 SpecialCare。")
            appendLine("帖子流/某人发帖/某频道有帖子：使用 trigger=TimelineNote；指定发帖用户用 SenderUserId 或 SenderNameContains；指定频道优先 ChannelId，不知道 ID 用 ChannelNameContains，并加 TimelineKind=Channel；帖子消息类型也用 MessageType，值可含 text、image、file、poll、reply、quote。")
            appendLine("关注、帖子回复、帖子提及、帖子引用等来自通知的事件：使用 trigger=Notification，并添加 NotificationType=Follow/Reply/Mention/Quote/Renote/Reaction 等。不要把聊天 @ 误生成为 Notification。")
            appendLine("failurePolicy 只能是 Continue 或 Stop。")
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
    val clean = trim().lowercase()
    return clean.startsWith("yes") || clean.startsWith("是") || clean.startsWith("符合")
}
