package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatMessageReference
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
            isManagingRoom = false,
            isMuted = false,
            onRefresh = {},
            onAddMedia = {},
            onEditRoom = {},
            onInviteMember = {},
            onLeaveRoom = {},
            onDeleteRoom = {},
            onToggleMute = {},
        )

        assertEquals(
            listOf("刷新消息", "搜索消息", "编辑聊天室", "邀请成员", "静音聊天室", "退出聊天室", "删除聊天室", "添加附件"),
            actions.map { it.label },
        )
        assertEquals(listOf(true, true, true, true, true, true, true, true), actions.map { it.enabled })
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
    fun quoteComposerTitleShowsActionAndAuthor() {
        val quote = ChatMessageQuote(
            messageId = "message-1",
            authorName = "Alice",
            previewText = "原消息",
        )

        assertEquals("引用 Alice", chatQuoteComposerTitle("引用", quote))
        assertEquals("回复 Alice", chatQuoteComposerTitle("回复", quote))
    }

    @Test
    fun messageOverflowKeepsRowActionsUnderMoreMenu() {
        val actions = chatMessageOverflowActions(
            messageId = "message-1",
            reactionOptions = listOf("❤️", "👍", "🎉", "😆", "😮", "😢", "🔥"),
            isReactionPending = false,
            isOutgoing = false,
            onReply = {},
            onQuote = {},
            onOpenReactionPicker = {},
            onDelete = {},
            onCopy = {},
            onReport = {},
        )
        val pendingActions = chatMessageOverflowActions(
            messageId = "message-1",
            reactionOptions = listOf("❤️", "👍"),
            isReactionPending = true,
            isOutgoing = true,
            onReply = {},
            onQuote = {},
            onOpenReactionPicker = {},
            onDelete = {},
            onCopy = {},
            onReport = {},
        )

        assertEquals(
            listOf("回复", "引用", "回应", "复制", "举报"),
            actions.map { it.label },
        )
        assertEquals(List(5) { true }, actions.map { it.enabled })
        assertEquals(listOf("回复", "引用", "回应处理中", "复制", "删除"), pendingActions.map { it.label })
        assertEquals(listOf(true, true, false, true, true), pendingActions.map { it.enabled })
    }

    @Test
    fun quotedChatMessageRendersQuoteSeparatelyFromBody() {
        val message = chatMessage(
            id = "message-quoted",
            authorId = "user-1",
            text = "> Alice: 原消息\n<!-- hhhl-chat-quote:message-source -->\n\n新的回复",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals("Alice", presentation.quote?.author)
        assertEquals("message-source", presentation.quote?.messageId)
        assertEquals("原消息", presentation.quote?.preview)
        assertEquals("新的回复", presentation.body)
    }

    @Test
    fun quoteOnlyChatMessageStillHasReadableBody() {
        val message = chatMessage(
            id = "message-quote-only",
            authorId = "user-1",
            text = "> Alice: 原消息\n<!-- hhhl-chat-quote:message-source -->",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals("Alice", presentation.quote?.author)
        assertEquals("原消息", presentation.body)
    }

    @Test
    fun markdownBlockquoteWithoutMarkerStaysInMessageBody() {
        val message = chatMessage(
            id = "message-markdown-quote",
            authorId = "user-1",
            text = "> 普通 Markdown 引用\n\n正文",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals(null, presentation.quote)
        assertEquals("> 普通 Markdown 引用\n\n正文", presentation.body)
    }

    @Test
    fun messageReferenceConvertsToJumpableQuote() {
        val reference = ChatMessageReference(
            id = "message-source",
            fromUser = FakeData.lin,
            text = "原消息正文",
        )

        val rendered = reference.toRenderedQuote()

        assertEquals("message-source", rendered.messageId)
        assertEquals(FakeData.lin.displayName, rendered.author)
        assertEquals("原消息正文", rendered.preview)
    }

    @Test
    fun appendedChatMentionUsesDisplayNameAndKeepsDraftText() {
        assertEquals("@${FakeData.lin.displayName} ", "".withAppendedChatMention(FakeData.lin))
        assertEquals(
            "你好 @${FakeData.lin.displayName} ",
            "你好 ".withAppendedChatMention(FakeData.lin),
        )
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
