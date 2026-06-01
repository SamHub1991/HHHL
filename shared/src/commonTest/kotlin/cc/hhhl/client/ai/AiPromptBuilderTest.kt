package cc.hhhl.client.ai

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPromptBuilderTest {
    @Test
    fun timelineDigestPromptIncludesStructuredPosts() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.TimelineDigest,
            input = AiTaskInput(
                timelineTitle = "首页",
                timelineNotes = listOf(
                    AiPostContext(
                        id = "note-1",
                        author = "Alice",
                        username = "alice",
                        text = "发布了 Android 版本",
                        createdAtLabel = "刚刚",
                        stats = "回复 1 · 转发 0 · 反应 5",
                    ),
                ),
            ),
        )

        assertTrue(prompt.user.contains("当前时间线"))
        assertTrue(prompt.user.contains("首页"))
        assertTrue(prompt.user.contains("Alice @alice"))
        assertTrue(prompt.user.contains("发布了 Android 版本"))
        assertTrue(prompt.user.contains("反应 5"))
    }

    @Test
    fun automationSemanticPromptAsksForYesOrNo() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.AutomationSemanticCondition,
            input = AiTaskInput(
                prompt = "用户反馈 bug",
                automationEventText = "有人说更新后无法打开设置",
            ),
        )

        assertTrue(prompt.user.contains("RESULT: YES 或 RESULT: NO"))
        assertTrue(prompt.user.contains("不要要求关键词完全一致"))
        assertTrue(prompt.user.contains("用户反馈 bug"))
        assertTrue(prompt.user.contains("无法打开设置"))
        assertEquals(96, prompt.maxOutputTokens)
    }

    @Test
    fun automationSemanticDecisionAcceptsCommonModelReplies() {
        assertTrue("RESULT: YES\n这是用户反馈 bug".aiSemanticYes())
        assertTrue("结论：满足\n对方在描述一个问题".aiSemanticYes())
        assertTrue("可以触发，因为语义相关".aiSemanticYes())
        assertFalse("RESULT: NO\n只是闲聊".aiSemanticYes())
        assertFalse("结论：不满足，证据不足".aiSemanticYes())
        assertFalse("无关：没有提到 bug".aiSemanticYes())
    }

    @Test
    fun threadSummaryPromptIncludesRootContextAndReplies() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.ThreadSummary,
            input = AiTaskInput(
                timelineTitle = "帖子线程 · 2 条回复",
                timelineNotes = listOf(
                    AiPostContext(id = "root", author = "Alice", username = "alice", text = "主帖：准备发布新版本"),
                    AiPostContext(id = "reply-1", author = "Bob", username = "bob", text = "回复：更新后设置打不开"),
                ),
            ),
        )

        assertTrue(prompt.user.contains("总结这个帖子线程"))
        assertTrue(prompt.user.contains("帖子线程 · 2 条回复"))
        assertTrue(prompt.user.contains("Alice @alice"))
        assertTrue(prompt.user.contains("Bob @bob"))
        assertTrue(prompt.user.contains("设置打不开"))
    }

    @Test
    fun profileSummaryPromptIncludesProfileAndRecentPosts() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.ProfileSummary,
            input = AiTaskInput(
                profile = AiProfileContext(
                    id = "user-1",
                    displayName = "Alice",
                    username = "alice",
                    bio = "做客户端和自动化",
                    stats = "关注 10 · 关注者 20 · 帖子 30",
                    relationship = "已关注",
                ),
                timelineNotes = listOf(
                    AiPostContext(id = "note-1", author = "Alice", text = "最近在做 AI 自动化"),
                ),
            ),
        )

        assertTrue(prompt.user.contains("总结这个用户资料"))
        assertTrue(prompt.user.contains("Alice @alice"))
        assertTrue(prompt.user.contains("做客户端和自动化"))
        assertTrue(prompt.user.contains("最近在做 AI 自动化"))
    }

    @Test
    fun chatActionPromptAsksForOwnersAndFollowUps() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.ChatActionItems,
            input = AiTaskInput(
                chatTitle = "项目群",
                chatMessages = listOf(AiChatMessageContext("Bob", "我明天修复登录问题")),
            ),
        )

        assertTrue(prompt.user.contains("待办"))
        assertTrue(prompt.user.contains("负责人"))
        assertTrue(prompt.user.contains("Bob: 我明天修复登录问题"))
    }

    @Test
    fun chatSummaryPromptsNameConcreteRanges() {
        val recent = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.ChatRecentSummary,
            input = AiTaskInput(
                chatTitle = "项目群",
                prompt = "范围：最近 50 条消息。",
                chatMessages = listOf(AiChatMessageContext("Alice", "发布了新版本")),
            ),
        )
        val unread = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.ChatUnreadSummary,
            input = AiTaskInput(
                chatTitle = "项目群",
                prompt = "范围：未读期间的 3 条消息。",
                chatMessages = listOf(AiChatMessageContext("Bob", "需要确认发布")),
            ),
        )

        assertTrue(recent.user.contains("最近 50 条"))
        assertTrue(recent.user.contains("范围：最近 50 条消息"))
        assertTrue(unread.user.contains("未读期间发生了什么"))
        assertTrue(unread.user.contains("范围：未读期间的 3 条消息"))
    }

    @Test
    fun sensitivePostContextIsRedactedUnlessAllowed() {
        val note = Note(
            id = "sensitive-note",
            author = User(id = "u1", displayName = "Alice", username = "alice", avatarInitial = "A"),
            text = "隐藏正文内容",
            createdAtLabel = "刚刚",
            cw = "剧透和敏感话题",
            media = listOf(NoteMedia(id = "m1", description = "敏感图片描述", isSensitive = true)),
        )

        val redacted = note.toAiTaskInput(uploadSensitiveContentAllowed = false)
        val allowed = note.toAiTaskInput(uploadSensitiveContentAllowed = true)

        assertTrue(redacted.noteText.contains("内容警告：剧透和敏感话题"))
        assertTrue(redacted.noteText.contains("正文因敏感内容权限未开启未上传"))
        assertFalse(redacted.noteText.contains("隐藏正文内容"))
        assertFalse(redacted.noteText.contains("敏感图片描述"))
        assertTrue(allowed.noteText.contains("隐藏正文内容"))
        assertTrue(allowed.noteText.contains("敏感图片描述"))
    }

    @Test
    fun automationRuleSuggestionsPromptKeepsActionsConfirmationGated() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.AutomationRuleSuggestions,
            input = AiTaskInput(
                prompt = "减少手动整理回调",
                automationEventText = "现有规则：1 条\n最近执行日志：Webhook 失败",
            ),
        )

        assertTrue(prompt.user.contains("自动化中心"))
        assertTrue(prompt.user.contains("触发器"))
        assertTrue(prompt.user.contains("安全级别"))
        assertTrue(prompt.user.contains("不要建议默认自动发送"))
        assertTrue(prompt.user.contains("Webhook 失败"))
    }

    @Test
    fun automationRuleDraftPromptExplainsWebhookAndAiWebhookFields() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.AutomationRuleDraft,
            input = AiTaskInput(
                prompt = "收到聊天消息后用 AI 提取任务再发到 Webhook",
            ),
        )

        assertTrue(prompt.user.contains("webhookUrl 或 url"))
        assertTrue(prompt.user.contains("AiGenerateWebhook"))
        assertTrue(prompt.user.contains("只输出 JSON"))
        assertTrue(prompt.user.contains("AiForwardToRoom"))
    }

    @Test
    fun workspaceActionPlanPromptRequiresConfirmationForTools() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.WorkspaceActionPlan,
            input = AiTaskInput(
                prompt = "整理下一步",
                automationEventText = "当前通知：Alice 提到了我\n当前聊天：Bob 需要我回复",
            ),
        )

        assertTrue(prompt.user.contains("全局行动规划助手"))
        assertTrue(prompt.user.contains("高优先级"))
        assertTrue(prompt.user.contains("需要用户确认"))
        assertTrue(prompt.user.contains("不要声称已经执行"))
        assertTrue(prompt.user.contains("Bob 需要我回复"))
    }

    @Test
    fun assistantChatPromptStaysLocalAndConfirmationGated() {
        val prompt = AiPromptBuilder.build(
            settings = AiSettings(enabled = true, apiKey = "key"),
            kind = AiTaskKind.AssistantChat,
            input = AiTaskInput(
                prompt = "帮我创建一条自动转发规则",
                text = "我: 之前问过聊天室转发",
                automationEventText = "自动化：3 条规则，启用 2 条",
            ),
        )

        assertTrue(prompt.user.contains("本地 AI 助手"))
        assertTrue(prompt.user.contains("不是远程 Misskey 机器人账号"))
        assertTrue(prompt.user.contains("不能声称你自己已经执行"))
        assertTrue(prompt.user.contains("高风险自动批准已开启"))
        assertTrue(prompt.user.contains("需确认"))
        assertTrue(prompt.user.contains("动作按钮"))
        assertTrue(prompt.user.contains("不要输出伪 JSON 工具调用"))
        assertTrue(prompt.user.contains("帮我创建一条自动转发规则"))
    }
}
