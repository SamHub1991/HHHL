package cc.hhhl.client.ui.screen

import cc.hhhl.client.aiAssistantTargetsCurrentChatRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAssistantPresentationTest {
    @Test
    fun suggestedActionsRequireConfirmationForAutomationDrafts() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我创建一条自动转发规则，把 A 聊天室消息转发到 B",
            reply = "可以生成规则草稿，需确认后创建。",
            idPrefix = "message-1",
        )

        val automation = actions.single { it.kind == AiAssistantActionKind.CreateAutomationDraft }
        assertEquals("message-1-CreateAutomationDraft", automation.id)
        assertEquals(AiAssistantActionRisk.Draft, automation.risk)
        assertEquals(AiAssistantActionStatus.Pending, automation.status)
        assertTrue(automation.description.contains("仍需确认"))
    }

    @Test
    fun suggestedActionsCoverDebugAndSearchIntents() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我 debug 规则为什么没触发，并网络搜索一下 Misskey webhook 限制",
            reply = "先看日志，再搜索相关限制。",
            idPrefix = "message-2",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.OpenAutomationLogs })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.OpenWebSearch })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.OpenDiscoverSearch })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.OpenWebhookManagement })
    }

    @Test
    fun casualAssistantReplyDoesNotCreateCapabilityActions() {
        val actions = aiAssistantSuggestedActions(
            prompt = "你好",
            reply = "你好！我可以帮你写消息、整理规则、检查自动化和复制清单。",
            idPrefix = "message-casual",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.OpenAutomation })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.CreateAutomationDraft })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.OpenAutomationLogs })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.CopyChecklist })
    }

    @Test
    fun multiLineAssistantReplyDoesNotAutoCreateChecklistAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "解释一下这个功能",
            reply = "第一点：它用于整理消息。\n第二点：它可以辅助写草稿。\n第三点：涉及操作时需要确认。",
            idPrefix = "message-multiline",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.CopyChecklist })
    }

    @Test
    fun capabilityDiscussionDoesNotTriggerMentionedNavigationActions() {
        val actions = aiAssistantSuggestedActions(
            prompt = "你能做什么？比如打开通知、创建草稿、搜索这些能力可以介绍下吗",
            reply = "我可以介绍这些能力，但普通交流不会替你打开页面。",
            idPrefix = "message-capability",
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun discussionAboutDangerousActionDoesNotTriggerDangerousAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "请问删除当前聊天室有什么风险？",
            reply = "删除聊天室会移除当前聊天室数据，执行前应先确认备份和权限。",
            idPrefix = "message-danger-discussion",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages })
    }

    @Test
    fun bareDangerousVerbDiscussionDoesNotTriggerDangerousAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "删除当前聊天室有什么风险？",
            reply = "这只是风险说明，不会执行删除。",
            idPrefix = "message-bare-danger-discussion",
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun explicitPoliteDangerousActionStillTriggersDangerousAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "请帮我删除当前聊天室",
            reply = "会删除当前聊天室，需要确认。",
            idPrefix = "message-danger-explicit",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom })
    }

    @Test
    fun explicitQuestionCanStillTriggerNavigationAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "能不能帮我打开通知页",
            reply = "可以打开通知页。",
            idPrefix = "message-question-action",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.OpenNotifications })
    }

    @Test
    fun negatedAssistantReplyDoesNotTriggerHighRiskActions() {
        val actions = aiAssistantSuggestedActions(
            prompt = "继续聊刚才的问题",
            reply = "我只是说明风险，不会删除当前聊天室，也不要清空消息或发送草稿。",
            idPrefix = "message-negated-risk",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.SendChatDraft })
    }

    @Test
    fun structuredBodyTextDoesNotTriggerHighRiskActions() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我写一条帖子草稿",
            reply = """
                可以作为草稿。
                ```hhhl-assistant-payload
                {"body":"不要删除当前聊天室，先确认备份。"}
                ```
            """.trimIndent(),
            idPrefix = "message-body-risk-text",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.FillComposeDraft })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom })
    }

    @Test
    fun suggestedActionsCoverCommonNavigationAndRefreshTools() {
        val actions = aiAssistantSuggestedActions(
            prompt = "打开通知页并刷新当前页面，然后检查更新",
            reply = "可以打开通知、刷新页面并检查更新。",
            idPrefix = "message-nav",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.OpenNotifications })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.RefreshCurrentView })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.CheckForUpdates })
        assertTrue(actions.all { it.status == AiAssistantActionStatus.Pending })
    }

    @Test
    fun suggestedActionsCoverSecondaryNavigationTools() {
        val cases = listOf(
            "打开 AI 设置" to AiAssistantActionKind.OpenAiSettings,
            "打开主题自定义" to AiAssistantActionKind.OpenThemeCustomization,
            "打开我的帖子" to AiAssistantActionKind.OpenProfileNotes,
            "打开发帖页" to AiAssistantActionKind.OpenCompose,
            "打开成就" to AiAssistantActionKind.OpenAchievements,
            "打开用户列表" to AiAssistantActionKind.OpenUserLists,
            "打开关注请求" to AiAssistantActionKind.OpenFollowRequests,
            "打开关系管理" to AiAssistantActionKind.OpenRelationshipManagement,
            "打开天线" to AiAssistantActionKind.OpenAntennas,
            "打开剪辑" to AiAssistantActionKind.OpenClips,
            "打开频道" to AiAssistantActionKind.OpenChannels,
            "打开页面" to AiAssistantActionKind.OpenPages,
            "打开相册" to AiAssistantActionKind.OpenGallery,
            "打开 Flash" to AiAssistantActionKind.OpenFlash,
            "打开公告" to AiAssistantActionKind.OpenAnnouncements,
        )

        cases.forEachIndexed { index, (prompt, kind) ->
            val action = aiAssistantSuggestedActions(
                prompt = prompt,
                reply = "可以打开对应页面。",
                idPrefix = "message-secondary-nav-$index",
            ).single { it.kind == kind }
            val expectedRisk = when (kind) {
                AiAssistantActionKind.OpenCompose -> AiAssistantActionRisk.Draft
                else -> AiAssistantActionRisk.ReadOnly
            }
            assertEquals(expectedRisk, action.risk)
        }
    }

    @Test
    fun suggestedActionsCoverAdminNavigationAsReadOnly() {
        val action = aiAssistantSuggestedActions(
            prompt = "打开管理后台帮我看看站点配置",
            reply = "后台操作需要人工确认。",
            idPrefix = "message-admin",
        ).single { it.kind == AiAssistantActionKind.OpenAdminDashboard }

        assertEquals(AiAssistantActionRisk.ReadOnly, action.risk)
    }

    @Test
    fun suggestedActionsCoverSearchAndReadWriteConfirmationTools() {
        val actions = aiAssistantSuggestedActions(
            prompt = "站内搜索聊天室转发，把当前聊天草稿发送，并把通知全部已读",
            reply = "这些动作需要你批准。",
            idPrefix = "message-tools",
        )

        val search = actions.single { it.kind == AiAssistantActionKind.OpenDiscoverSearch }
        assertEquals("聊天室转发", search.payload)
        assertEquals(AiAssistantActionRisk.ReadOnly, search.risk)
        assertTrue(actions.any { it.kind == AiAssistantActionKind.SendChatDraft && it.risk == AiAssistantActionRisk.RequiresConfirmation })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.MarkNotificationsRead && it.risk == AiAssistantActionRisk.RequiresConfirmation })
    }

    @Test
    fun publishComposeDraftDoesNotOverwriteCurrentDraft() {
        val actions = aiAssistantSuggestedActions(
            prompt = "发布当前发帖草稿",
            reply = "发布当前草稿需要批准。",
            idPrefix = "message-publish",
        )

        val publish = actions.single { it.kind == AiAssistantActionKind.PublishComposeDraft }
        assertEquals("", publish.payload)
        assertTrue(actions.none { it.kind == AiAssistantActionKind.FillComposeDraft })
    }

    @Test
    fun publishComposeDraftUsesStructuredBodyWhenProvided() {
        val actions = aiAssistantSuggestedActions(
            prompt = "直接发布这条帖子",
            reply = """
                可以发布。
                ```hhhl-assistant-payload
                {"body":"今天把后台聊天实时触发修好了。"}
                ```
            """.trimIndent(),
            idPrefix = "message-publish-body",
        )

        val publish = actions.single { it.kind == AiAssistantActionKind.PublishComposeDraft }
        assertEquals("今天把后台聊天实时触发修好了。", publish.payload)
    }

    @Test
    fun assistantReplyAloneDoesNotTriggerSendChatDraftAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "执行",
            reply = "我会发送当前聊天草稿。",
            idPrefix = "message-send-continuation",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.SendChatDraft })
    }

    @Test
    fun assistantReplyAloneDoesNotTriggerPublishComposeDraftAction() {
        val actions = aiAssistantSuggestedActions(
            prompt = "继续",
            reply = "我会发布当前发帖草稿。",
            idPrefix = "message-publish-continuation",
        )

        assertTrue(actions.none { it.kind == AiAssistantActionKind.PublishComposeDraft })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.FillComposeDraft })
    }

    @Test
    fun explicitPromptStillTriggersSendAndPublishActions() {
        val send = aiAssistantSuggestedActions(
            prompt = "发送当前聊天草稿",
            reply = "需要批准后发送。",
            idPrefix = "message-explicit-send",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }
        val publish = aiAssistantSuggestedActions(
            prompt = "发布当前发帖草稿",
            reply = "需要批准后发布。",
            idPrefix = "message-explicit-publish",
        ).single { it.kind == AiAssistantActionKind.PublishComposeDraft }

        assertEquals(AiAssistantActionRisk.RequiresConfirmation, send.risk)
        assertEquals(AiAssistantActionRisk.RequiresConfirmation, publish.risk)
    }

    @Test
    fun suggestedActionsCoverChatAssistantTools() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我做聊天室摘要，再给一个转发模板，并把回复消息改写短一点",
            reply = "可以先摘要，再生成转发模板。改写后的回复：收到，我稍后确认。",
            idPrefix = "message-3",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.RunChatSummary })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.CreateForwardTemplateDraft })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.FillChatDraft })
    }

    @Test
    fun suggestedActionsKeepAutomationWebhookAndBulkNavigationReadOnly() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我启用自动转发规则，调用 webhook，并批量清空通知",
            reply = "这些都需要人工确认。",
            idPrefix = "message-risk",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.ReviewAutomationRisk && it.risk == AiAssistantActionRisk.ReadOnly })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.OpenWebhookManagement && it.risk == AiAssistantActionRisk.ReadOnly })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.ReviewBulkOperation && it.risk == AiAssistantActionRisk.ReadOnly })
    }

    @Test
    fun suggestedActionsCoverDirectCurrentChatRoomManagement() {
        val clear = aiAssistantSuggestedActions(
            prompt = "清空当前聊天室消息",
            reply = "会直接清空当前聊天室消息。",
            idPrefix = "message-room-clear",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val delete = aiAssistantSuggestedActions(
            prompt = "删除当前聊天室",
            reply = "会删除当前聊天室。",
            idPrefix = "message-room-delete",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }
        val leave = aiAssistantSuggestedActions(
            prompt = "退出当前聊天室",
            reply = "会退出当前聊天室。",
            idPrefix = "message-room-leave",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }
        val mute = aiAssistantSuggestedActions(
            prompt = "静音当前聊天室",
            reply = "会静音当前聊天室。",
            idPrefix = "message-room-mute",
        ).single { it.kind == AiAssistantActionKind.MuteCurrentChatRoom }
        val unmute = aiAssistantSuggestedActions(
            prompt = "取消静音当前聊天室",
            reply = "会取消静音当前聊天室。",
            idPrefix = "message-room-unmute",
        ).single { it.kind == AiAssistantActionKind.UnmuteCurrentChatRoom }

        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, delete.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
        assertEquals(AiAssistantActionRisk.RequiresConfirmation, mute.risk)
        assertEquals(AiAssistantActionRisk.RequiresConfirmation, unmute.risk)
        assertTrue(clear.description.contains("直接调用"))
    }

    @Test
    fun directCurrentChatRoomManagementDoesNotAddGenericCurrentPageReview() {
        val actions = aiAssistantSuggestedActions(
            prompt = "删除当前聊天室",
            reply = "会删除当前聊天室。",
            idPrefix = "message-room-direct-only",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.ReviewCurrentPageAction })
    }

    @Test
    fun suggestedRoomManagementActionsPreserveNamedRoomPayload() {
        val action = aiAssistantSuggestedActions(
            prompt = "直接删除 AGI 讨论 聊天室",
            reply = "会删除 AGI 讨论 聊天室。",
            idPrefix = "message-room-named-delete",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }

        assertTrue(action.payload.contains("AGI 讨论"))
    }

    @Test
    fun suggestedActionsRequireConfirmationForCurrentPageWriteOperations() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我关注这个用户并上传附件",
            reply = "这些会修改远程状态，需要你确认。",
            idPrefix = "message-current-write",
        )

        val review = actions.single { it.kind == AiAssistantActionKind.ReviewCurrentPageAction }
        assertEquals(AiAssistantActionRisk.RequiresConfirmation, review.risk)
        assertTrue(review.description.contains("不会静默"))
    }

    @Test
    fun suggestedActionsCreateDirectCurrentNoteOperations() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我点赞收藏当前帖子，再转发当前帖子",
            reply = "可以直接处理当前帖子。",
            idPrefix = "message-current-note",
        )

        assertTrue(actions.any { it.kind == AiAssistantActionKind.ReactCurrentNote })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.FavoriteCurrentNote })
        assertTrue(actions.any { it.kind == AiAssistantActionKind.RenoteCurrentNote })
        assertTrue(actions.none { it.kind == AiAssistantActionKind.ReviewCurrentPageAction })
        assertTrue(
            actions
                .filter {
                    it.kind == AiAssistantActionKind.ReactCurrentNote ||
                        it.kind == AiAssistantActionKind.FavoriteCurrentNote ||
                        it.kind == AiAssistantActionKind.RenoteCurrentNote
                }
                .all { it.risk == AiAssistantActionRisk.RequiresConfirmation },
        )
    }

    @Test
    fun suggestedActionsCreateHighRiskCurrentNoteDelete() {
        val action = aiAssistantSuggestedActions(
            prompt = "直接删除当前帖子",
            reply = "会删除当前帖子。",
            idPrefix = "message-current-note-delete",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentNote }

        assertEquals(AiAssistantActionRisk.HighRisk, action.risk)
        assertTrue(action.description.contains("直接调用删除接口"))
    }

    @Test
    fun draftCandidateExtractsFencedBody() {
        val reply = """
            可以填入这个帖子草稿：
            ```
            今天整理了聊天室自动化的防循环和调试日志。
            ```
            需确认后再发布。
        """.trimIndent()

        assertEquals(
            "今天整理了聊天室自动化的防循环和调试日志。",
            aiAssistantDraftCandidate(reply),
        )
    }

    @Test
    fun structuredReplyExtractsPayloadAndHidesProtocolBlock() {
        val reply = """
            可以填入这个帖子草稿，发布前请确认。
            ```hhhl-assistant-payload
            {"body":"今天把聊天室自动化调试流程整理完了。","targetRoom":"AGI 讨论","targetUser":"张三","mentions":["李四","@alice"],"channel":"更新","visibility":"home","cw":"进展","localOnly":"true","searchQuery":"Misskey webhook 限制","automationGoal":"收到 A 聊天室消息后转发到 B 聊天室","mutedWord":"抽奖广告","memory":"发帖草稿要简短","checklist":"- 检查规则\n- 模拟触发"}
            ```
        """.trimIndent()

        val parsed = aiAssistantStructuredReply(reply)

        assertEquals("可以填入这个帖子草稿，发布前请确认。", parsed.visibleText)
        assertEquals("今天把聊天室自动化调试流程整理完了。", parsed.payload.body)
        assertEquals("AGI 讨论", parsed.payload.targetRoom)
        assertEquals("张三", parsed.payload.targetUser)
        assertEquals(listOf("李四", "@alice"), parsed.payload.mentions)
        assertEquals("更新", parsed.payload.channel)
        assertEquals("home", parsed.payload.visibility)
        assertEquals("进展", parsed.payload.contentWarning)
        assertEquals("true", parsed.payload.localOnly)
        assertEquals("Misskey webhook 限制", parsed.payload.searchQuery)
        assertEquals("收到 A 聊天室消息后转发到 B 聊天室", parsed.payload.automationGoal)
        assertEquals("抽奖广告", parsed.payload.mutedWord)
        assertEquals("发帖草稿要简短", parsed.payload.memory)
        assertEquals("- 检查规则\n- 模拟触发", parsed.payload.checklist)
    }

    @Test
    fun structuredReplyExtractsWholeJsonWrapper() {
        val parsed = aiAssistantStructuredReply(
            """{"reply":"可以这样回复。","payload":{"body":"收到，我稍后确认。","targetUser":"张三"}}""",
        )

        assertEquals("可以这样回复。", parsed.visibleText)
        assertEquals("收到，我稍后确认。", parsed.payload.body)
        assertEquals("张三", parsed.payload.targetUser)
    }

    @Test
    fun wrapperDisplayReplyIsNotUsedAsActionBody() {
        val payload = aiAssistantActionPayload(
            """{"reply":"可以发送这条消息。","payload":{"targetUser":"张三"}}""",
        )

        assertEquals("", payload.body)
        assertEquals("张三", payload.targetUser)
    }

    @Test
    fun bareTextPayloadStillWorksAsBodyAlias() {
        val payload = aiAssistantActionPayload(
            """{"text":"收到，我稍后确认。","targetUser":"张三"}""",
        )

        assertEquals("收到，我稍后确认。", payload.body)
        assertEquals("张三", payload.targetUser)
    }

    @Test
    fun structuredReplyExtractsFencedJsonWrapper() {
        val parsed = aiAssistantStructuredReply(
            """
                ```json
                {"message":"整理好了。","data":{"checklist":["检查规则","模拟触发"]}}
                ```
            """.trimIndent(),
        )

        assertEquals("整理好了。", parsed.visibleText)
        assertEquals("检查规则\n模拟触发", parsed.payload.checklist)
    }

    @Test
    fun assistantDisplayBubblesSplitParagraphsAndKeepCodeFence() {
        val bubbles = aiAssistantDisplayBubbles(
            """
                先看这个结论。

                ```json
                {"ok":true}
                ```

                然后再确认动作。
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "先看这个结论。",
                "```json\n{\"ok\":true}\n```",
                "然后再确认动作。",
            ),
            bubbles,
        )
    }

    @Test
    fun assistantDisplayBubblesRenderJsonReplyArrayAsSeparateBubbles() {
        val bubbles = aiAssistantDisplayBubbles(
            """{"reply":["第一段结论。","第二段建议。"],"payload":{"body":"只作为草稿内容"}}""",
        )

        assertEquals(listOf("第一段结论。", "第二段建议。"), bubbles)
    }

    @Test
    fun assistantDisplayBubblesPreserveRemoteJsonMessageAndAdditionalSections() {
        val bubbles = aiAssistantDisplayBubbles(
            """
                {
                  "message": "下面是完整示例。",
                  "code": "```kotlin\nfun main() {\n    println(10 * 3)\n}\n```",
                  "example": "示例输入：\n10\n*\n3\n\n输出：\n30"
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "下面是完整示例。",
                "```kotlin\nfun main() {\n    println(10 * 3)\n}\n```",
                "示例输入：\n10\n*\n3\n\n输出：\n30",
            ),
            bubbles,
        )
    }

    @Test
    fun assistantDisplayBubblesExtractEmbeddedJsonFenceAndHideProtocol() {
        val bubbles = aiAssistantDisplayBubbles(
            """
                结果如下：
                ```json
                {"reply":"第一段。\n\n第二段。","payload":{"body":"草稿正文"}}
                ```
            """.trimIndent(),
        )

        assertEquals(listOf("结果如下：", "第一段。", "第二段。"), bubbles)
    }

    @Test
    fun structuredReplyHidesUnclosedPayloadFence() {
        val parsed = aiAssistantStructuredReply(
            """
                可以先作为草稿。
                ```hhhl-assistant-payload
                {"body":"今天继续修小助手渲染。","targetRoom":"AGI 讨论"}
            """.trimIndent(),
        )

        assertEquals("可以先作为草稿。", parsed.visibleText)
        assertEquals("今天继续修小助手渲染。", parsed.payload.body)
        assertEquals("AGI 讨论", parsed.payload.targetRoom)
    }

    @Test
    fun bugDiscussionDoesNotTriggerMentionedFeatureActions() {
        val actions = aiAssistantSuggestedActions(
            prompt = "通知未读显示 99+ 有问题，公告完整内容格式也不对",
            reply = "这是界面问题描述，不应该打开通知或公告页面。",
            idPrefix = "message-ui-bug-discussion",
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun outgoingDraftPrefersAssistantPayloadBodyOverCurrentDraft() {
        assertEquals("AI正文", aiAssistantOutgoingDraftText("旧草稿", " AI正文 "))
        assertEquals("旧草稿", aiAssistantOutgoingDraftText(" 旧草稿 ", " "))
    }

    @Test
    fun structuredBodyIsUsedForComposeWithoutAssistantPreamble() {
        val actions = aiAssistantSuggestedActions(
            prompt = "帮我写一条帖子草稿",
            reply = """
                好的，我会先作为草稿填入。
                ```hhhl-assistant-payload
                {"body":"今天把聊天室自动化调试流程整理完了。","searchQuery":"","automationGoal":"","mutedWord":"","memory":"","checklist":""}
                ```
            """.trimIndent(),
            idPrefix = "message-structured-compose",
        )

        val fill = actions.single { it.kind == AiAssistantActionKind.FillComposeDraft }
        assertEquals("今天把聊天室自动化调试流程整理完了。", fill.payload)
    }

    @Test
    fun structuredChatTargetsArePackedIntoActionPayload() {
        val action = aiAssistantSuggestedActions(
            prompt = "在 AGI 讨论聊天室发消息并 @张三",
            reply = """
                我会发送这条消息，需确认。
                ```hhhl-assistant-payload
                {"body":"明天 10 点开会。","targetRoom":"AGI 讨论","mentions":["张三"]}
                ```
            """.trimIndent(),
            idPrefix = "message-targeted-chat",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("明天 10 点开会。", payload.body)
        assertEquals("AGI 讨论", payload.targetRoom)
        assertEquals(listOf("张三"), payload.mentions)
    }

    @Test
    fun mentionNamesInPromptBecomeRoutingPayload() {
        val action = aiAssistantSuggestedActions(
            prompt = "发送消息 @张三 说我稍后确认",
            reply = """
                会发送这条消息。
                ```hhhl-assistant-payload
                {"body":"我稍后确认。"}
                ```
            """.trimIndent(),
            idPrefix = "message-mention-fallback",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("我稍后确认。", payload.body)
        assertEquals(listOf("张三"), payload.mentions)
    }

    @Test
    fun directChatNameInPromptBecomesTargetUserPayload() {
        val action = aiAssistantSuggestedActions(
            prompt = "私聊张三说我稍后确认",
            reply = """
                会发送这条消息。
                ```hhhl-assistant-payload
                {"body":"我稍后确认。"}
                ```
            """.trimIndent(),
            idPrefix = "message-direct-target",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("我稍后确认。", payload.body)
        assertEquals("张三", payload.targetUser)
    }

    @Test
    fun naturalDirectChatPromptBodyIsUsedWithoutStructuredReply() {
        val action = aiAssistantSuggestedActions(
            prompt = "私聊张三说我稍后确认",
            reply = "可以，我会发送这条消息。",
            idPrefix = "message-direct-inline-body",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("我稍后确认", payload.body)
        assertEquals("张三", payload.targetUser)
    }

    @Test
    fun naturalDirectPrivateMessagePromptIsParsed() {
        val action = aiAssistantSuggestedActions(
            prompt = "给张三私信：我稍后确认",
            reply = "可以，我会发送这条消息。",
            idPrefix = "message-direct-private-letter",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("我稍后确认", payload.body)
        assertEquals("张三", payload.targetUser)
    }

    @Test
    fun roomNameInPromptBecomesTargetRoomPayload() {
        val action = aiAssistantSuggestedActions(
            prompt = "在 AGI 讨论聊天室发消息并 @李四",
            reply = """
                会发送这条消息。
                ```hhhl-assistant-payload
                {"body":"明天 10 点同步。"}
                ```
            """.trimIndent(),
            idPrefix = "message-room-target",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("明天 10 点同步。", payload.body)
        assertEquals("AGI 讨论", payload.targetRoom)
        assertEquals(listOf("李四"), payload.mentions)
    }

    @Test
    fun naturalRoomChatPromptBodyAfterColonIsUsedWithoutStructuredReply() {
        val action = aiAssistantSuggestedActions(
            prompt = "在 AGI 讨论聊天室发消息：明天 10 点同步",
            reply = "可以，我会发送这条消息。",
            idPrefix = "message-room-inline-body",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("明天 10 点同步", payload.body)
        assertEquals("AGI 讨论", payload.targetRoom)
    }

    @Test
    fun naturalRoomChatPromptBodyWithoutColonIsUsedWithoutStructuredReply() {
        val action = aiAssistantSuggestedActions(
            prompt = "在 AGI 讨论聊天室发消息明天 10 点同步",
            reply = "可以，我会发送这条消息。",
            idPrefix = "message-room-inline-body-no-colon",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("明天 10 点同步", payload.body)
        assertEquals("AGI 讨论", payload.targetRoom)
    }

    @Test
    fun naturalRoomGroupPromptBodyIsParsed() {
        val action = aiAssistantSuggestedActions(
            prompt = "在 AGI 讨论群里说今晚 8 点同步",
            reply = "可以，我会发送这条消息。",
            idPrefix = "message-room-group-inline-body",
        ).single { it.kind == AiAssistantActionKind.SendChatDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("今晚 8 点同步", payload.body)
        assertEquals("AGI 讨论", payload.targetRoom)
    }

    @Test
    fun targetedComposePayloadKeepsChannelVisibilityAndMentions() {
        val action = aiAssistantSuggestedActions(
            prompt = "直接发布到更新频道并 @李四",
            reply = """
                我会发布，需确认。
                ```hhhl-assistant-payload
                {"body":"今天完成了 AI 助手动作升级。","channel":"更新","mentions":["李四"],"visibility":"home","cw":"进展"}
                ```
            """.trimIndent(),
            idPrefix = "message-targeted-compose",
        ).single { it.kind == AiAssistantActionKind.PublishComposeDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("今天完成了 AI 助手动作升级。", payload.body)
        assertEquals("更新", payload.channel)
        assertEquals(listOf("李四"), payload.mentions)
        assertEquals("home", payload.visibility)
        assertEquals("进展", payload.contentWarning)
    }

    @Test
    fun naturalComposePromptChannelAndBodyAreUsedWithoutStructuredReply() {
        val action = aiAssistantSuggestedActions(
            prompt = "发布到更新频道：今天完成了未读和 AI 助手动作修复",
            reply = "可以，我会发布这条帖子。",
            idPrefix = "message-compose-inline-channel",
        ).single { it.kind == AiAssistantActionKind.PublishComposeDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("今天完成了未读和 AI 助手动作修复", payload.body)
        assertEquals("更新", payload.channel)
    }

    @Test
    fun naturalComposePromptChannelBeforeVerbIsUsedWithoutStructuredReply() {
        val action = aiAssistantSuggestedActions(
            prompt = "更新频道发帖今天完成了后台自动化去重修复",
            reply = "可以，我会发布这条帖子。",
            idPrefix = "message-compose-inline-channel-before-verb",
        ).single { it.kind == AiAssistantActionKind.PublishComposeDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("今天完成了后台自动化去重修复", payload.body)
        assertEquals("更新", payload.channel)
    }

    @Test
    fun naturalComposePromptChannelWithLocationSuffixIsParsed() {
        val action = aiAssistantSuggestedActions(
            prompt = "更新频道里发帖今天完成了语音助手解析优化",
            reply = "可以，我会发布这条帖子。",
            idPrefix = "message-compose-inline-channel-location-suffix",
        ).single { it.kind == AiAssistantActionKind.PublishComposeDraft }

        val payload = aiAssistantActionPayload(action.payload)
        assertEquals("今天完成了语音助手解析优化", payload.body)
        assertEquals("更新", payload.channel)
    }

    @Test
    fun structuredFieldsDriveSearchMutedWordMemoryAndAutomationPayloads() {
        val reply = """
            我会按字段给出客户端需要的内容。
            ```hhhl-assistant-payload
            {"body":"收到，我稍后确认。","searchQuery":"Misskey webhook 限制","automationGoal":"收到 A 聊天室消息后转发到 B 聊天室","mutedWord":"抽奖广告","memory":"偏好短回复","checklist":"- 先看日志\n- 再模拟触发"}
            ```
        """.trimIndent()

        assertEquals("Misskey webhook 限制", aiAssistantSearchQueryCandidate("网络搜索一下乱七八糟", reply))
        assertEquals("抽奖广告", aiAssistantMutedWordPayload("添加静音词", reply))
        assertEquals("偏好短回复", aiAssistantMemoryCandidate("记住我喜欢短回复", reply))
        assertEquals("收到 A 聊天室消息后转发到 B 聊天室", aiAssistantAutomationGoalCandidate("帮我创建自动化", reply))
        assertEquals("- 先看日志\n- 再模拟触发", aiAssistantChecklistCandidate("清单", reply))
    }

    @Test
    fun autoApprovalPolicySeparatesLowAndHighRiskActions() {
        val lowRisk = AiAssistantActionProposal(
            id = "open-notifications",
            kind = AiAssistantActionKind.OpenNotifications,
            title = "打开通知",
            description = "打开通知页",
        )
        val draft = AiAssistantActionProposal(
            id = "fill-compose",
            kind = AiAssistantActionKind.FillComposeDraft,
            title = "填入草稿",
            description = "填入发帖草稿",
        )
        val write = AiAssistantActionProposal(
            id = "publish",
            kind = AiAssistantActionKind.PublishComposeDraft,
            title = "发布",
            description = "发布草稿",
        )
        val highRisk = AiAssistantActionProposal(
            id = "delete-room",
            kind = AiAssistantActionKind.DeleteCurrentChatRoom,
            title = "删除聊天室",
            description = "删除当前聊天室",
        )

        val defaultSettings = AiAssistantAutoApprovalSettings()
        assertTrue(lowRisk.canAutoApprove(defaultSettings))
        assertTrue(draft.canAutoApprove(defaultSettings))
        assertFalse(write.canAutoApprove(defaultSettings))
        assertFalse(highRisk.canAutoApprove(defaultSettings))

        val highRiskSettings = AiAssistantAutoApprovalSettings(highRiskEnabled = true)
        assertTrue(write.canAutoApprove(highRiskSettings))
        assertTrue(highRisk.canAutoApprove(highRiskSettings))
        assertFalse(highRisk.copy(status = AiAssistantActionStatus.Failed).canAutoApprove(highRiskSettings))
        assertFalse(highRisk.copy(status = AiAssistantActionStatus.Running).canAutoApprove(highRiskSettings))
        assertFalse(write.copy(status = AiAssistantActionStatus.Approved).canAutoApprove(highRiskSettings))

        val highRiskOnlySettings = AiAssistantAutoApprovalSettings(
            lowRiskEnabled = false,
            highRiskEnabled = true,
        )
        assertFalse(lowRisk.canAutoApprove(highRiskOnlySettings))
        assertTrue(highRisk.canAutoApprove(highRiskOnlySettings))
    }

    @Test
    fun highRiskRoomActionsKeepPromptAsPayload() {
        val prompt = "删除当前聊天室"
        val reply = "这是高风险操作，需要确认。"

        val action = aiAssistantSuggestedActions(
            prompt = prompt,
            reply = reply,
            idPrefix = "room-action",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }

        assertEquals(prompt, action.payload)
        assertEquals(AiAssistantActionRisk.HighRisk, action.risk)
    }

    @Test
    fun highRiskRoomActionPayloadKeepsAssistantTargetWhenPromptIsContinuation() {
        val action = aiAssistantSuggestedActions(
            prompt = "执行",
            reply = "我将清空当前聊天室消息，高风险自动批准开启时会由客户端执行。",
            idPrefix = "room-action-continuation",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }

        assertTrue(action.payload.contains("清空当前聊天室"))
        assertEquals(AiAssistantActionRisk.HighRisk, action.risk)
    }

    @Test
    fun highRiskNoteActionPayloadKeepsAssistantTargetWhenPromptIsContinuation() {
        val action = aiAssistantSuggestedActions(
            prompt = "继续",
            reply = "我将删除当前帖子，高风险自动批准开启时会由客户端执行。",
            idPrefix = "note-action-continuation",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentNote }

        assertTrue(action.payload.contains("删除当前帖子"))
        assertEquals(AiAssistantActionRisk.HighRisk, action.risk)
    }

    @Test
    fun highRiskRoomActionsMatchRoomSynonyms() {
        val delete = aiAssistantSuggestedActions(
            prompt = "删除这个房间",
            reply = "将由客户端执行。",
            idPrefix = "delete-room",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }
        val clear = aiAssistantSuggestedActions(
            prompt = "清空本房间聊天记录",
            reply = "将由客户端执行。",
            idPrefix = "clear-room",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val leave = aiAssistantSuggestedActions(
            prompt = "退出这个群聊",
            reply = "将由客户端执行。",
            idPrefix = "leave-room",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }

        assertEquals(AiAssistantActionRisk.HighRisk, delete.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
    }

    @Test
    fun highRiskRoomActionsMatchCasualChineseCommands() {
        val delete = aiAssistantSuggestedActions(
            prompt = "删了这个群",
            reply = "将由客户端执行。",
            idPrefix = "delete-casual-room",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }
        val deleteShort = aiAssistantSuggestedActions(
            prompt = "删群",
            reply = "将由客户端执行。",
            idPrefix = "delete-short-room",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }
        val clear = aiAssistantSuggestedActions(
            prompt = "清掉当前聊天记录",
            reply = "将由客户端执行。",
            idPrefix = "clear-casual-room",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val clearRecord = aiAssistantSuggestedActions(
            prompt = "清理当前消息记录",
            reply = "将由客户端执行。",
            idPrefix = "clear-record-room",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val leave = aiAssistantSuggestedActions(
            prompt = "退群",
            reply = "将由客户端执行。",
            idPrefix = "leave-casual-room",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }
        val deleteNote = aiAssistantSuggestedActions(
            prompt = "删了当前帖子",
            reply = "将由客户端执行。",
            idPrefix = "delete-casual-note",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentNote }

        assertEquals(AiAssistantActionRisk.HighRisk, delete.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, deleteShort.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, clearRecord.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, deleteNote.risk)
    }

    @Test
    fun highRiskRoomActionsMatchShortCurrentCommands() {
        val delete = aiAssistantSuggestedActions(
            prompt = "删了吧",
            reply = "将由客户端执行。",
            idPrefix = "delete-short-current",
        ).single { it.kind == AiAssistantActionKind.DeleteCurrentChatRoom }
        val clear = aiAssistantSuggestedActions(
            prompt = "清一下",
            reply = "将由客户端执行。",
            idPrefix = "clear-short-current",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val leave = aiAssistantSuggestedActions(
            prompt = "退了吧",
            reply = "将由客户端执行。",
            idPrefix = "leave-short-current",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }

        assertEquals(AiAssistantActionRisk.HighRisk, delete.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
    }

    @Test
    fun highRiskRoomActionsMatchCurrentChatConversationPhrases() {
        val clear = aiAssistantSuggestedActions(
            prompt = "清空当前聊天记录",
            reply = "高风险自动批准开启时由客户端执行。",
            idPrefix = "clear-current-chat",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val leave = aiAssistantSuggestedActions(
            prompt = "退出当前会话",
            reply = "高风险自动批准开启时由客户端执行。",
            idPrefix = "leave-current-conversation",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }
        val mute = aiAssistantSuggestedActions(
            prompt = "静音这个对话",
            reply = "需要确认。",
            idPrefix = "mute-current-conversation",
        ).single { it.kind == AiAssistantActionKind.MuteCurrentChatRoom }

        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
        assertEquals(AiAssistantActionRisk.RequiresConfirmation, mute.risk)
    }

    @Test
    fun highRiskRoomActionsMatchHereConversationPhrases() {
        val clear = aiAssistantSuggestedActions(
            prompt = "清空这里聊天记录",
            reply = "高风险自动批准开启时由客户端执行。",
            idPrefix = "clear-here-chat",
        ).single { it.kind == AiAssistantActionKind.ClearCurrentChatRoomMessages }
        val leave = aiAssistantSuggestedActions(
            prompt = "退出这儿的会话",
            reply = "高风险自动批准开启时由客户端执行。",
            idPrefix = "leave-here-chat",
        ).single { it.kind == AiAssistantActionKind.LeaveCurrentChatRoom }

        assertEquals(AiAssistantActionRisk.HighRisk, clear.risk)
        assertEquals(AiAssistantActionRisk.HighRisk, leave.risk)
    }

    @Test
    fun roomActionTargetTreatsShortRoomCommandsAsCurrentChat() {
        assertTrue("删群".aiAssistantTargetsCurrentChatRoom())
        assertTrue("删了吧".aiAssistantTargetsCurrentChatRoom())
        assertTrue("清空聊天记录".aiAssistantTargetsCurrentChatRoom())
        assertTrue("清一下".aiAssistantTargetsCurrentChatRoom())
        assertTrue("退出这儿的会话".aiAssistantTargetsCurrentChatRoom())
        assertTrue("退了吧".aiAssistantTargetsCurrentChatRoom())
        assertFalse("删除 设计讨论 room-123".aiAssistantTargetsCurrentChatRoom())
    }

    @Test
    fun streamingTextCyclesThroughVisiblePhases() {
        assertTrue(aiAssistantStreamingText(0).contains("读取当前上下文"))
        assertTrue(aiAssistantStreamingText(1).contains("匹配可用工具"))
        assertTrue(aiAssistantStreamingText(4).contains("读取当前上下文"))
    }

    @Test
    fun searchQueryCandidateRemovesCommandWords() {
        assertEquals("Misskey webhook 限制", aiAssistantSearchQueryCandidate("网络搜索一下 Misskey webhook 限制"))
        assertEquals("聊天室转发", aiAssistantSearchQueryCandidate("站内搜索聊天室转发"))
    }
}
