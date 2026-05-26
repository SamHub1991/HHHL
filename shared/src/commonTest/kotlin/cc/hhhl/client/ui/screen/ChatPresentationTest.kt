package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.state.ChatUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPresentationTest {
    @Test
    fun ownMessagesAlignOutgoingAndOthersAlignIncoming() {
        val own = chatMessage("message-own", authorId = "user-1")
        val other = chatMessage("message-other", authorId = "user-2")

        assertEquals(ChatMessageAlignment.Outgoing, chatMessageAlignment(own, currentUserId = "user-1"))
        assertEquals(ChatMessageAlignment.Incoming, chatMessageAlignment(other, currentUserId = "user-1"))
        assertEquals(ChatMessageAlignment.Incoming, chatMessageAlignment(own, currentUserId = null))
    }

    @Test
    fun chatDetailOverflowKeepsSecondaryActionsReachable() {
        val actions = chatDetailSummaryActions(
            showingMembers = false,
            isUploadingMedia = false,
            hasAttachment = false,
            canRefreshCurrent = true,
            canAddMedia = true,
            onRefresh = {},
            onShowMessages = {},
            onShowMembers = {},
            onAddMedia = {},
        )

        assertEquals(listOf("刷新消息", "查看消息", "查看成员", "添加附件"), actions.map { it.label })
        assertEquals(listOf(true, false, true, true), actions.map { it.enabled })
    }

    @Test
    fun chatDetailStatusNamesCurrentPane() {
        assertEquals("0 条消息", chatDetailStatusText(ChatUiState()))
        assertEquals(
            "0 位成员",
            chatDetailStatusText(ChatUiState(showingMembers = true)),
        )
    }

    @Test
    fun chatDetailModeLabelShowsCountOnlyWhenUseful() {
        assertEquals("消息", chatDetailModeLabel("消息", 0))
        assertEquals("成员 3", chatDetailModeLabel("成员", 3))
    }

    @Test
    fun composerAttachmentActionNamesCurrentState() {
        assertEquals(
            "更多",
            chatComposerAttachmentActionLabel(isUploadingMedia = false, hasAttachment = false),
        )
        assertEquals(
            "更换附件",
            chatComposerAttachmentActionLabel(isUploadingMedia = false, hasAttachment = true),
        )
        assertEquals(
            "上传中",
            chatComposerAttachmentActionLabel(isUploadingMedia = true, hasAttachment = true),
        )
    }

    @Test
    fun composerEmojiOptionsIncludeCommonSendableChoices() {
        val options = chatComposerEmojiOptions()

        assertEquals(true, "😀" in options)
        assertEquals(true, "👍" in options)
        assertEquals(true, "❤️" in options)
    }

    @Test
    fun specialCareToastNamesToggleResult() {
        assertEquals(
            "已将 Alice 设为特别关心",
            chatSpecialCareToastMessage(displayName = "Alice", isSpecialCare = true),
        )
        assertEquals(
            "已取消对 Alice 的特别关心",
            chatSpecialCareToastMessage(displayName = "Alice", isSpecialCare = false),
        )
        assertEquals(
            "已将 该用户 设为特别关心",
            chatSpecialCareToastMessage(displayName = "", isSpecialCare = true),
        )
    }

    @Test
    fun quoteComposerTitleLooksLikeMarkdownQuote() {
        val quote = ChatMessageQuote(
            messageId = "message-1",
            authorName = "Alice",
            previewText = "原消息",
        )

        assertEquals("> Alice", chatQuoteComposerTitle(quote))
    }

    @Test
    fun messageOverflowKeepsRowActionsUnderMoreMenu() {
        val actions = chatMessageOverflowActions(
            messageId = "message-1",
            defaultReaction = "❤️",
            isReactionPending = false,
            onQuote = {},
            onReact = { _, _ -> },
        )
        val pendingActions = chatMessageOverflowActions(
            messageId = "message-1",
            defaultReaction = "❤️",
            isReactionPending = true,
            onQuote = {},
            onReact = { _, _ -> },
        )

        assertEquals(listOf("引用", "回应 ❤️"), actions.map { it.label })
        assertEquals(listOf(true, true), actions.map { it.enabled })
        assertEquals(listOf("引用", "回应处理中"), pendingActions.map { it.label })
        assertEquals(listOf(true, false), pendingActions.map { it.enabled })
    }

    @Test
    fun quotedChatMessageRendersQuoteSeparatelyFromBody() {
        val message = chatMessage(
            id = "message-quoted",
            authorId = "user-1",
            text = "> Alice: 原消息\n\n新的回复",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals("Alice", presentation.quote?.author)
        assertEquals("原消息", presentation.quote?.preview)
        assertEquals("新的回复", presentation.body)
    }

    @Test
    fun quoteOnlyChatMessageStillHasReadableBody() {
        val message = chatMessage(
            id = "message-quote-only",
            authorId = "user-1",
            text = "> Alice: 原消息",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals("Alice", presentation.quote?.author)
        assertEquals("原消息", presentation.body)
    }

    private fun chatMessage(
        id: String,
        authorId: String,
        text: String = "hello",
    ): ChatMessage {
        val user = FakeData.me.copy(id = authorId)
        return ChatMessage(
            id = id,
            roomId = "room-1",
            fromUser = user,
            text = text,
            createdAtLabel = "now",
        )
    }
}
