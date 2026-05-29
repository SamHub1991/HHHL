package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.ChatMessageQuote
import cc.hhhl.client.model.ChatMessageReference
import cc.hhhl.client.model.ChatRoomMember
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import cc.hhhl.client.state.ChatUiState
import cc.hhhl.client.ui.component.containsValidMfmSyntax
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
            listOf(
                "刷新消息",
                "搜索消息",
                "AI 总结聊天",
                "AI 回复草稿",
                "AI 待办提取",
                "AI 决策摘要",
                "过滤设置",
                "编辑聊天室",
                "邀请成员",
                "静音聊天室",
                "退出聊天室",
                "删除聊天室",
                "添加附件",
            ),
            actions.map { it.label },
        )
        assertEquals(
            listOf(true, true, false, false, false, false, true, true, true, true, true, true, true),
            actions.map { it.enabled },
        )
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
    fun chatMessagePresentationKeepsRichTextBodyForInlineRendering() {
        val richText = "$[x2 $[jelly 正文]] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}"
        val message = chatMessage(
            id = "message-rich-body",
            authorId = "user-1",
            text = richText,
        )

        val presentation = chatMessagePresentation(message)

        assertEquals(null, presentation.quote)
        assertEquals(richText, presentation.body)
    }

    @Test
    fun quotedChatMessageNormalizesOnlyQuotePreviewAndKeepsBodyRichText() {
        val richBody = "$[fg.color=ff0000 新回复]"
        val message = chatMessage(
            id = "message-rich-quoted",
            authorId = "user-1",
            text = "> Alice: $[x2 原消息] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}\n" +
                "<!-- hhhl-chat-quote:message-source -->\n\n" +
                richBody,
        )

        val presentation = chatMessagePresentation(message)

        assertEquals("Alice", presentation.quote?.author)
        assertEquals("原消息 docs 👍", presentation.quote?.preview)
        assertEquals(richBody, presentation.body)
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
    fun strayQuoteMarkerLineIsNotRenderedAsMessageBodyText() {
        val message = chatMessage(
            id = "message-stray-marker",
            authorId = "user-1",
            text = "普通正文\n<!-- hhhl-chat-quote:message-source -->\n$[x2 尾巴]",
        )

        val presentation = chatMessagePresentation(message)

        assertEquals(null, presentation.quote)
        assertEquals("普通正文\n$[x2 尾巴]", presentation.body)
    }

    @Test
    fun messageReferenceConvertsToJumpableQuote() {
        val user = sampleMentionUser()
        val reference = ChatMessageReference(
            id = "message-source",
            fromUser = user,
            text = "原消息正文",
        )

        val rendered = reference.toRenderedQuote()

        assertEquals("message-source", rendered.messageId)
        assertEquals(user.displayName, rendered.author)
        assertEquals("原消息正文", rendered.preview)
    }

    @Test
    fun messageReferenceQuoteNormalizesRichTextPreview() {
        val user = sampleMentionUser()
        val reference = ChatMessageReference(
            id = "message-source-rich",
            fromUser = user,
            text = "$[x2 原消息] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}",
        )

        val rendered = reference.toRenderedQuote()

        assertEquals("原消息 docs 👍", rendered.preview)
    }

    @Test
    fun referencedQuoteIndexUsesProvidedVisibleMessageList() {
        val hidden = chatMessage("hidden", authorId = "user-2", text = "原消息正文")
        val visible = chatMessage("visible", authorId = "user-1", text = "原消息正文")
        val quote = ChatRenderedQuote(
            messageId = null,
            author = "Alice",
            preview = "原消息正文",
        )

        assertEquals(1, listOf(hidden, visible).indexOfReferencedQuote(quote))
        assertEquals(0, listOf(visible).indexOfReferencedQuote(quote))
    }

    @Test
    fun chatMessageUiFilterOnlyResetsForLoadedHiddenJumpTargets() {
        val filter = ChatMessageUiFilterState(hideMfmSyntaxMessages = true)
        val loadedIds = setOf("visible", "hidden")
        val visibleIndex = mapOf("visible" to 0)

        assertEquals(false, filter.shouldResetForLoadedHiddenMessage("visible", loadedIds, visibleIndex))
        assertEquals(true, filter.shouldResetForLoadedHiddenMessage("hidden", loadedIds, visibleIndex))
        assertEquals(false, filter.shouldResetForLoadedHiddenMessage("not-loaded", loadedIds, visibleIndex))
        assertEquals(false, filter.reset().shouldResetForLoadedHiddenMessage("hidden", loadedIds, visibleIndex))
    }

    @Test
    fun chatMessageUiFilterResetsForLoadedHiddenQuoteFallbackTargets() {
        val hidden = chatMessage("hidden", authorId = "user-2", text = "原消息正文")
        val visible = chatMessage("visible", authorId = "user-1", text = "别的正文")
        val quote = ChatRenderedQuote(messageId = null, author = "Alice", preview = "原消息正文")
        val filter = ChatMessageUiFilterState(hiddenUserIds = setOf("user-2"))

        assertEquals(
            true,
            filter.shouldResetForLoadedHiddenQuote(
                quote = quote,
                loadedMessages = listOf(hidden, visible),
                visibleMessages = listOf(visible),
                loadedMessageIds = setOf("hidden", "visible"),
                visibleMessageIndexById = mapOf("visible" to 0),
            ),
        )
    }

    @Test
    fun chatMessageUiFilterDoesNotResetForVisibleQuoteFallbackTargets() {
        val hidden = chatMessage("hidden", authorId = "user-2", text = "别的正文")
        val visible = chatMessage("visible", authorId = "user-1", text = "原消息正文")
        val quote = ChatRenderedQuote(messageId = null, author = "Alice", preview = "原消息正文")
        val filter = ChatMessageUiFilterState(hiddenUserIds = setOf("user-2"))

        assertEquals(
            false,
            filter.shouldResetForLoadedHiddenQuote(
                quote = quote,
                loadedMessages = listOf(hidden, visible),
                visibleMessages = listOf(visible),
                loadedMessageIds = setOf("hidden", "visible"),
                visibleMessageIndexById = mapOf("visible" to 0),
            ),
        )
    }

    @Test
    fun appendedChatMentionUsesDisplayNameAndKeepsDraftText() {
        val user = sampleMentionUser()

        assertEquals("@${user.displayName} ", "".withAppendedChatMention(user))
        assertEquals(
            "你好 @${user.displayName} ",
            "你好 ".withAppendedChatMention(user),
        )
    }

    @Test
    fun chatMessageUiFilterHidesMfmSyntaxUserAndRegexMatchesOnly() {
        val messages = listOf(
            chatMessage("plain", authorId = "user-1", text = "hello"),
            chatMessage("mfm", authorId = "user-1", text = "$[x2 hello]"),
            chatMessage("hidden-user", authorId = "user-2", text = "visible text"),
            chatMessage("regex", authorId = "user-3", text = "BLOCKED keyword"),
        )
        val filter = ChatMessageUiFilterState(
            hideMfmSyntaxMessages = true,
            hiddenUserIds = setOf("user-2"),
            regexPatterns = listOf("blocked\\s+keyword", "["),
        )
        val regexes = compileChatMessageUiFilterRegexes(filter.regexPatterns)

        assertEquals(
            listOf("plain"),
            messages.filterByChatMessageUiFilter(filter, regexes).map { it.id },
        )
        assertEquals(messages, messages.filterByChatMessageUiFilter(filter.reset(), emptyList()))
    }

    @Test
    fun chatMessageUiFilterDraftsDoNotBecomeActiveRules() {
        val messages = listOf(
            chatMessage("keep-regex-draft", authorId = "user-1", text = "draft blocked token"),
            chatMessage("keep-user-draft", authorId = "draft-user", text = "hello"),
            chatMessage("hide-active", authorId = "hidden-user", text = "hello"),
        )
        val filter = ChatMessageUiFilterState(
            hiddenUserIds = setOf("hidden-user"),
            hiddenUserDraft = "draft-user",
            regexDraft = "blocked",
        )
        val activeFilter = filter.activeRulesOnly()

        assertEquals(
            listOf("keep-regex-draft", "keep-user-draft"),
            messages.filterByChatMessageUiFilter(activeFilter, compileChatMessageUiFilterRegexes(activeFilter.regexPatterns))
                .map { it.id },
        )
        assertEquals("", activeFilter.hiddenUserDraft)
        assertEquals("", activeFilter.regexDraft)
        assertEquals(1, activeFilter.activeCount)
    }

    @Test
    fun userConversationPreviewUsesNormalizedRichTextPreviewText() {
        val message = chatMessage("mfm-preview", authorId = "user-1", text = "$[fg.color=ff0000 hi] :blobcat:")

        assertEquals("我：hi :blobcat:", chatUserConversationPreview(message, sentByMe = true))
    }

    @Test
    fun chatMessageUiFilterCanHideByUsernameOrRemoteAcct() {
        val local = chatMessage("local", authorId = "local-user", text = "hello", username = "alice")
        val remote = chatMessage(
            id = "remote",
            authorId = "remote-user",
            text = "hello",
            username = "bob",
            host = "example.social",
        )

        assertEquals(
            emptyList(),
            listOf(local).filterByChatMessageUiFilter(
                ChatMessageUiFilterState(hiddenUserIds = setOf("alice")),
                emptyList(),
            ),
        )
        assertEquals(
            emptyList(),
            listOf(remote).filterByChatMessageUiFilter(
                ChatMessageUiFilterState(hiddenUserIds = setOf("@bob@example.social")),
                emptyList(),
            ),
        )
        assertEquals(
            emptyList(),
            listOf(remote).filterByChatMessageUiFilter(
                ChatMessageUiFilterState(hiddenUserIds = setOf("@Bob@EXAMPLE.SOCIAL")),
                emptyList(),
            ),
        )
        assertEquals("@bob@example.social", " @bob@example.social ".cleanChatMessageUiHiddenUserRule())
        assertEquals(null, "bob example".cleanChatMessageUiHiddenUserRule())
        assertEquals(null, "u".repeat(129).cleanChatMessageUiHiddenUserRule())
    }

    @Test
    fun chatMessageUiFilterUserToggleRemovesAllRulesForThatUser() {
        val remoteUser = User(
            id = "remote-user",
            displayName = "Bob",
            username = "bob",
            avatarInitial = "B",
            host = "example.social",
        )
        val filter = ChatMessageUiFilterState(
            hiddenUserIds = setOf("remote-user", "Bob", "@Bob@EXAMPLE.SOCIAL", "other-user"),
        )

        assertEquals(
            setOf("other-user"),
            filter.withToggledHiddenUser(user = remoteUser, hidden = true).hiddenUserIds,
        )
        assertEquals(
            setOf("other-user", "remote-user"),
            ChatMessageUiFilterState(hiddenUserIds = setOf("other-user"))
                .withToggledHiddenUser(user = remoteUser, hidden = false)
                .hiddenUserIds,
        )
    }

    @Test
    fun chatSearchAuthorFiltersIncludeMembersWithoutMessagesAndSearchByIdentity() {
        val memberOnly = chatUser(
            id = "member-only",
            displayName = "林间",
            username = "lin",
            host = "example.social",
        )
        val messageAuthor = chatUser(id = "message-user", displayName = "Alice", username = "alice")
        val searchAuthor = chatUser(id = "search-user", displayName = "Bob", username = "bob")
        val filters = buildChatSearchAuthorFilters(
            members = listOf(chatRoomMember(memberOnly)),
            messages = listOf(chatMessage("message", authorId = messageAuthor.id, username = messageAuthor.username)),
            searchResults = listOf(chatMessage("search", authorId = searchAuthor.id, username = searchAuthor.username)),
        )

        assertEquals(listOf("member-only", "search-user", "message-user"), filters.map { it.userId })
        assertEquals(listOf("member-only"), filters.filterByChatSearchAuthorQuery("@lin@example").map { it.userId })
        assertEquals(listOf("member-only"), filters.filterByChatSearchAuthorQuery("林").map { it.userId })
    }

    @Test
    fun chatSearchAuthorFiltersAreBoundedForLargeRooms() {
        val members = List(260) { index ->
            chatRoomMember(chatUser(id = "member-$index", displayName = "Member $index", username = "member$index"))
        }

        val filters = buildChatSearchAuthorFilters(members = members, messages = emptyList(), searchResults = emptyList())

        assertEquals(240, filters.size)
        assertEquals("member-0", filters.first().userId)
        assertEquals("member-239", filters.last().userId)
    }

    @Test
    fun chatMessageRegexSearchMatchesBodyAuthorReferenceAndFile() {
        val bodyMatch = chatMessage("body", authorId = "user-1", text = "hello target body")
        val authorMatch = chatMessage("author", authorId = "user-2", text = "hello", username = "alice")
        val quoteMatch = chatMessage("quote", authorId = "user-3", text = "hello").copy(
            quote = ChatMessageReference(
                id = "quote-source",
                fromUser = chatUser(id = "quoted-user", displayName = "Quoted", username = "quoted"),
                text = "quoted target preview",
            ),
        )
        val fileMatch = chatMessage("file", authorId = "user-4", text = "").copy(
            file = DriveFile(
                id = "file-1",
                name = "target-file.webp",
                type = "image/webp",
                url = null,
                thumbnailUrl = null,
                comment = null,
                size = 1,
                isSensitive = false,
            ),
        )
        val hide = chatMessage("hide", authorId = "user-5", text = "hello")
        val regex = Regex("target|@?alice", RegexOption.IGNORE_CASE)

        assertEquals(
            listOf("body", "author", "quote", "file"),
            listOf(bodyMatch, authorMatch, quoteMatch, fileMatch, hide)
                .filterByChatMessageSearchRegex(regex, "")
                .map { it.id },
        )
    }

    @Test
    fun chatMessageRegexSearchRejectsUnsafePatterns() {
        assertEquals(true, "target\\s+body".isSafeChatMessageSearchRegex())
        assertEquals(false, "[".isSafeChatMessageSearchRegex())
        assertEquals(false, "(a+)+$".isSafeChatMessageSearchRegex())
        assertEquals(false, "(ab|a)+$".isSafeChatMessageSearchRegex())
        assertEquals(false, "(?=.*token).*".isSafeChatMessageSearchRegex())
        assertEquals(false, "x".repeat(200).isSafeChatMessageSearchRegex())
    }

    @Test
    fun loadedChatMessageIdSetIndexesCurrentMessagesForJumpChecks() {
        val messages = listOf(
            chatMessage("m1", authorId = "user-1"),
            chatMessage("m2", authorId = "user-2"),
            chatMessage("m2", authorId = "user-3"),
        )

        assertEquals(setOf("m1", "m2"), messages.loadedChatMessageIdSet())
        assertEquals(true, "m2" in messages.loadedChatMessageIdSet())
        assertEquals(false, "missing" in messages.loadedChatMessageIdSet())
    }

    @Test
    fun chatMessageIdFingerprintChangesWhenSameSizedMessageWindowChanges() {
        val first = listOf(
            chatMessage("m1", authorId = "user-1"),
            chatMessage("m2", authorId = "user-1"),
            chatMessage("m3", authorId = "user-1"),
        )
        val sameSizeDifferentMiddle = listOf(
            chatMessage("m1", authorId = "user-1"),
            chatMessage("m2b", authorId = "user-1"),
            chatMessage("m3", authorId = "user-1"),
        )
        val sameSizeDifferentTail = listOf(
            chatMessage("m1", authorId = "user-1"),
            chatMessage("m2", authorId = "user-1"),
            chatMessage("m4", authorId = "user-1"),
        )

        assertEquals("0", emptyList<ChatMessage>().chatMessageIdFingerprint())
        assertEquals(false, first.chatMessageIdFingerprint() == sameSizeDifferentMiddle.chatMessageIdFingerprint())
        assertEquals(false, first.chatMessageIdFingerprint() == sameSizeDifferentTail.chatMessageIdFingerprint())
    }

    @Test
    fun mfmSyntaxDetectorHandlesDollarBracketAndBraceForms() {
        assertEquals(true, "$[x2 hello]".containsValidMfmSyntax())
        assertEquals(true, "${'$'}{fg.color=ff0000 hello}".containsValidMfmSyntax())
        assertEquals(true, "${'$'}{username}".containsValidMfmSyntax())
        assertEquals(false, "plain $ text".containsValidMfmSyntax())
        assertEquals(false, "literal $[ code".containsValidMfmSyntax())
        assertEquals(false, "${'$'}{ broken value".containsValidMfmSyntax())
        assertEquals(false, "${'$'}{two words}".containsValidMfmSyntax())
        assertEquals(false, "${'$'}{unknown text}".containsValidMfmSyntax())
    }

    @Test
    fun chatMessageUiMfmFilterIgnoresBrokenOrLiteralDollarSyntax() {
        val literal = chatMessage("literal", authorId = "user-1", text = "here is code: $[not closed")
        val broken = chatMessage("broken", authorId = "user-1", text = "${'$'}{not closed")
        val valid = chatMessage("valid", authorId = "user-1", text = "$[x2 hello]")
        val interpolation = chatMessage("interpolation", authorId = "user-1", text = "${'$'}{username}")
        val plainBrace = chatMessage("plain-brace", authorId = "user-1", text = "${'$'}{two words}")
        val filter = ChatMessageUiFilterState(hideMfmSyntaxMessages = true)

        assertEquals(
            listOf("literal", "broken", "plain-brace"),
            listOf(literal, broken, valid, interpolation, plainBrace)
                .filterByChatMessageUiFilter(filter, emptyList())
                .map { it.id },
        )
    }

    @Test
    fun mfmSyntaxDetectorRespectsBoundedScanWindow() {
        val longPrefix = "x".repeat(4_200)
        assertEquals(false, (longPrefix + "$[x2 hello]").containsValidMfmSyntax())
        assertEquals(false, (longPrefix + "${'$'}{username}").containsValidMfmSyntax())
        assertEquals(true, ("x".repeat(4_000) + "$[x2 hello]").containsValidMfmSyntax())
    }

    @Test
    fun chatMessageUiRegexFilterUsesBoundedMessageHeadAndTailText() {
        val prefixMatch = chatMessage(
            "prefix",
            authorId = "user-1",
            text = "blocked" + "x".repeat(5_000),
        )
        val suffixMatch = chatMessage(
            "suffix",
            authorId = "user-1",
            text = "x".repeat(4_200) + "blocked",
        )
        val middleOnlyMatch = chatMessage(
            "middle",
            authorId = "user-1",
            text = "x".repeat(2_300) + "blocked" + "x".repeat(2_300),
        )
        val filter = ChatMessageUiFilterState(regexPatterns = listOf("blocked"))
        val regexes = compileChatMessageUiFilterRegexes(filter.regexPatterns)

        assertEquals(
            listOf("middle"),
            listOf(prefixMatch, suffixMatch, middleOnlyMatch).filterByChatMessageUiFilter(filter, regexes).map { it.id },
        )
    }

    @Test
    fun chatMessageUiRegexFilterKeepsFullMatchTextUnderLimit() {
        val message = chatMessage(
            "under-limit",
            authorId = "user-1",
            text = "x".repeat(2_200) + "blocked",
        )
        val filter = ChatMessageUiFilterState(regexPatterns = listOf("blocked"))
        val regexes = compileChatMessageUiFilterRegexes(filter.regexPatterns)

        assertEquals(emptyList(), listOf(message).filterByChatMessageUiFilter(filter, regexes))
    }

    @Test
    fun chatMessageUiRegexFilterStillChecksReferenceAndFileText() {
        val replyMatch = chatMessage("reply", authorId = "user-1", text = "hello").copy(
            reply = ChatMessageReference(
                id = "reply-source",
                fromUser = FakeData.me,
                text = "reply blocked token",
            ),
        )
        val quoteMatch = chatMessage("quote", authorId = "user-1", text = "hello").copy(
            quote = ChatMessageReference(
                id = "quote-source",
                fromUser = FakeData.me,
                text = "quote blocked token",
            ),
        )
        val fileMatch = chatMessage("file", authorId = "user-1", text = "").copy(
            file = DriveFile(
                id = "file-1",
                name = "blocked-file.webp",
                type = "image/webp",
                url = null,
                thumbnailUrl = null,
                comment = null,
                size = 1,
                isSensitive = false,
            ),
        )
        val keep = chatMessage("keep", authorId = "user-1", text = "hello")
        val filter = ChatMessageUiFilterState(regexPatterns = listOf("blocked"))
        val regexes = compileChatMessageUiFilterRegexes(filter.regexPatterns)

        assertEquals(
            listOf("keep"),
            listOf(replyMatch, quoteMatch, fileMatch, keep).filterByChatMessageUiFilter(filter, regexes).map { it.id },
        )
    }

    @Test
    fun chatMessageUiFilterAppliesToSearchSourceBeforeSearchFilters() {
        val source = listOf(
            chatMessage("keep", authorId = "user-1", text = "hello target"),
            chatMessage("hide-mfm", authorId = "user-1", text = "$[x2 target]"),
            chatMessage("hide-user", authorId = "user-2", text = "target"),
            chatMessage("hide-regex", authorId = "user-3", text = "blocked target"),
        )
        val uiFilter = ChatMessageUiFilterState(
            hideMfmSyntaxMessages = true,
            hiddenUserIds = setOf("user-2"),
            regexPatterns = listOf("blocked"),
        )
        val uiFiltered = source.filterByChatMessageUiFilter(
            uiFilter,
            compileChatMessageUiFilterRegexes(uiFilter.regexPatterns),
        )

        assertEquals(listOf("keep"), uiFiltered.filterByChatMessageSearch("target", "").map { it.id })
    }

    @Test
    fun chatMessageUiRegexCompilerBoundsRulesIndependentlyFromUi() {
        val patterns = buildList {
            add("blocked")
            add("blocked")
            add("x".repeat(200))
            add("[")
            add("(a+)+$")
            add("(ab|a)+$")
            repeat(40) { index -> add("rule$index") }
        }

        val regexes = compileChatMessageUiFilterRegexes(patterns)

        assertEquals(true, regexes.size <= 24)
        assertEquals(true, regexes.first().containsMatchIn("blocked text"))
        assertEquals(false, regexes.any { it.pattern.length > 160 })
        assertEquals(false, regexes.any { it.pattern == "[" })
        assertEquals(false, regexes.any { it.pattern == "(a+)+$" })
        assertEquals(false, regexes.any { it.pattern == "(ab|a)+$" })
        assertEquals(true, "hello|world".isSafeChatMessageUiFilterRegex())
        assertEquals(false, "(a+)+$".isSafeChatMessageUiFilterRegex())
        assertEquals(false, "(a|aa)+$".isSafeChatMessageUiFilterRegex())
        assertEquals(false, "(word)\\1".isSafeChatMessageUiFilterRegex())
        assertEquals(false, "(?=.*token).*".isSafeChatMessageUiFilterRegex())
        assertEquals(false, "(?<=prefix)token".isSafeChatMessageUiFilterRegex())
    }

    private fun chatMessage(
        id: String,
        authorId: String,
        text: String = "hello",
        username: String = "me",
        host: String? = null,
    ): ChatMessage {
        val user = FakeData.me.copy(id = authorId, username = username, host = host)
        return ChatMessage(
            id = id,
            roomId = "room-1",
            fromUser = user,
            text = text,
            createdAtLabel = "now",
        )
    }

    private fun chatUser(
        id: String,
        displayName: String,
        username: String,
        host: String? = null,
    ): User {
        return FakeData.me.copy(
            id = id,
            displayName = displayName,
            username = username,
            avatarInitial = displayName.take(1).ifBlank { username.take(1) },
            host = host,
        )
    }

    private fun chatRoomMember(user: User): ChatRoomMember {
        return ChatRoomMember(
            membershipId = "membership-${user.id}",
            roomId = "room-1",
            user = user,
            joinedAtLabel = "now",
        )
    }

    private fun sampleMentionUser(): User {
        return User(
            id = "u1",
            displayName = "林间",
            username = "lin",
            avatarInitial = "林",
        )
    }
}
