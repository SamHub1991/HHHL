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
                AiTaskKind.WorkspaceActionPlan -> workspaceActionPlanPrompt(compact)
                AiTaskKind.ConnectionTest -> "请只回复 OK。"
            },
            maxOutputTokens = when (kind) {
                AiTaskKind.ComposeContentWarning -> 80
                AiTaskKind.ComposeHashtags -> 120
                AiTaskKind.ComposeMentionSuggestions -> 160
                AiTaskKind.AutomationSemanticCondition -> 40
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
            appendLine("根据自动化事件生成一段可用于本地日志、系统通知或 Webhook 的简短文本。只输出生成文本。")
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
