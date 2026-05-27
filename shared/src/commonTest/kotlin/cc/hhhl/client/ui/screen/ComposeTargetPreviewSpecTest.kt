package cc.hhhl.client.ui.screen

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.api.ComposeDraft
import cc.hhhl.client.api.ComposePollDraft
import cc.hhhl.client.model.NoteVisibility
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeTargetPreviewSpecTest {
    @Test
    fun replyPreviewNamesTheTargetAuthorAndUsesNoteText() {
        val note = FakeData.timeline.first()

        val preview = composeTargetPreview(note, ComposeTargetKind.Reply)

        assertEquals("回复 @${note.author.username}", preview?.title)
        assertEquals(note.text, preview?.body)
    }

    @Test
    fun quotePreviewUsesContentWarningBeforeBodyText() {
        val note = FakeData.timeline.first().copy(cw = "剧透")

        val preview = composeTargetPreview(note, ComposeTargetKind.Quote)

        assertEquals("引用 @${note.author.username}", preview?.title)
        assertEquals("剧透", preview?.body)
    }

    @Test
    fun emptyTargetPreviewFallsBackToAttachmentSummary() {
        val note = FakeData.timeline[1].copy(text = "", cw = null)

        val preview = composeTargetPreview(note, ComposeTargetKind.Quote)

        assertEquals("${note.media.size} 个附件", preview?.body)
    }

    @Test
    fun visibilityOptionsKeepSharkeyOrderReachableFromDropdown() {
        assertEquals(
            listOf(
                NoteVisibility.Public,
                NoteVisibility.Home,
                NoteVisibility.Followers,
                NoteVisibility.Specified,
            ),
            composeVisibilityOptions(),
        )
    }

    @Test
    fun visibilityOptionsHidePublicWhenInstanceDisablesIt() {
        assertEquals(
            listOf(
                NoteVisibility.Home,
                NoteVisibility.Followers,
                NoteVisibility.Specified,
            ),
            composeVisibilityOptions(canPublicNote = false),
        )
    }

    @Test
    fun secondaryActionsKeepCwAndPollInOverflowMenu() {
        val actions = composeSecondaryActions(
            cwEnabled = false,
            pollEnabled = true,
            onToggleCw = {},
            onTogglePoll = {},
        )

        assertEquals(listOf("内容警告", "移除投票"), actions.map { it.label })
        assertEquals(listOf(false, true), actions.map { it.destructive })
    }

    @Test
    fun composeDangerousInlineActionsMoveToOverflow() {
        assertEquals(
            listOf("移除附件"),
            composeAttachmentActions(onRemoveFile = {}).map { it.label },
        )
        assertEquals(
            listOf(true),
            composeAttachmentActions(onRemoveFile = {}).map { it.destructive },
        )
        assertEquals(
            listOf("删除选项"),
            composePollChoiceActions(onRemoveChoice = {}).map { it.label },
        )
        assertEquals(
            listOf(true),
            composePollChoiceActions(onRemoveChoice = {}).map { it.destructive },
        )
        assertEquals(
            listOf("移除投票"),
            composePollSectionActions(onRemovePoll = {}).map { it.label },
        )
        assertEquals(
            listOf(true),
            composePollSectionActions(onRemovePoll = {}).map { it.destructive },
        )
    }

    @Test
    fun editorStatusSummarizesModernComposeSurfaceState() {
        val draft = ComposeDraft(
            visibility = NoteVisibility.Specified,
            cw = "剧透",
            fileIds = listOf("file-1", "file-2"),
            poll = ComposePollDraft(),
            renoteId = "note-quote",
            channelId = "channel-1",
        )

        assertEquals(
            listOf("指定", "频道", "引用", "内容警告", "2 个文件", "投票已开启"),
            composeEditorStatusParts(draft),
        )
    }

    @Test
    fun editorStatusKeepsReplyEntryVisible() {
        val draft = ComposeDraft(
            visibility = NoteVisibility.Followers,
            replyId = "note-parent",
        )

        assertEquals(listOf("关注者", "回复"), composeEditorStatusParts(draft))
    }

    @Test
    fun editorSurfaceUsesSoftMarkdownPanelMetrics() {
        assertEquals(
            ComposeEditorSurfaceSpec(
                cornerRadius = 20,
                contentPadding = 14,
                bodyMinHeight = 184,
            ),
            composeEditorSurfaceSpec(),
        )
        assertEquals(true, composeTargetPreviewUsesMarkdownQuoteRail())
    }

    @Test
    fun editorModeLabelsKeepPreviewEntryExplicit() {
        assertEquals("编辑", composeEditorModeLabel(ComposeEditorMode.Edit))
        assertEquals("预览", composeEditorModeLabel(ComposeEditorMode.Preview))
    }

    @Test
    fun visibleUserResolutionTextExplainsPendingAndReadyRecipients() {
        assertEquals(
            "指定用户",
            composeVisibleUserResolutionText(emptyList(), isResolving = false),
        )
        assertEquals(
            "已指定 1 · 待解析 2",
            composeVisibleUserResolutionText(
                listOf("user-1", "@alice", "＠bob@example.com"),
                isResolving = false,
            ),
        )
        assertEquals(
            "已指定 2 个用户。",
            composeVisibleUserResolutionText(listOf("user-1", "user-2"), isResolving = false),
        )
        assertEquals(
            "解析中",
            composeVisibleUserResolutionText(listOf("@alice"), isResolving = true),
        )
    }
}
