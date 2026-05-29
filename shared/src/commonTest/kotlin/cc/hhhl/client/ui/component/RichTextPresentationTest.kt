package cc.hhhl.client.ui.component

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.presentation.chatMessageBodyText
import cc.hhhl.client.presentation.notePreviewText
import cc.hhhl.client.presentation.notificationLineText
import cc.hhhl.client.presentation.richTextPlainPreviewText
import cc.hhhl.client.presentation.truncateRichTextPreviewText
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextPresentationTest {
    @Test
    fun notificationLineKeepsActorNameAndMessageTextTogether() {
        val notification = NotificationItem(
            id = "notification-1",
            type = NotificationType.Mention,
            actor = FakeData.me,
            text = "提到了你 :blobcat:",
            createdAtLabel = "刚刚",
            noteId = "note-1",
        )

        assertEquals("${notification.actor.displayName} 提到了你 :blobcat:", notificationLineText(notification))
    }

    @Test
    fun notificationLineNormalizesRichTextSyntaxForPreviewText() {
        val notification = NotificationItem(
            id = "notification-2",
            type = NotificationType.Mention,
            actor = FakeData.me,
            text = "$[fg.color=ff0000 提到了你] [文档](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}",
            createdAtLabel = "刚刚",
        )

        assertEquals("${notification.actor.displayName} 提到了你 文档 👍", notificationLineText(notification))
    }

    @Test
    fun chatMessageBodyUsesTextBeforeAttachmentName() {
        val message = ChatMessage(
            id = "message-1",
            roomId = "room-1",
            fromUser = FakeData.me,
            text = "hello #Sharkey",
            createdAtLabel = "刚刚",
            file = sampleDriveFile(),
        )

        assertEquals("hello #Sharkey", chatMessageBodyText(message))
    }

    @Test
    fun chatMessageBodyNormalizesMfmAndMarkdownForPreview() {
        val message = ChatMessage(
            id = "message-rich",
            roomId = "room-1",
            fromUser = FakeData.me,
            text = "$[x2 hello] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}",
            createdAtLabel = "刚刚",
        )

        assertEquals("hello docs 👍", chatMessageBodyText(message))
    }

    @Test
    fun chatMessageBodyFallsBackToAttachmentNameThenPlaceholder() {
        val fileMessage = ChatMessage(
            id = "message-1",
            roomId = "room-1",
            fromUser = FakeData.me,
            text = "",
            createdAtLabel = "刚刚",
            file = sampleDriveFile(),
        )
        val emptyMessage = fileMessage.copy(file = null)

        assertEquals("photo.webp", chatMessageBodyText(fileMessage))
        assertEquals("[附件消息]", chatMessageBodyText(emptyMessage))
    }

    @Test
    fun notePreviewTextPrefersCwThenBodyThenFallback() {
        val base = Note(
            id = "note-1",
            author = FakeData.me,
            text = "正文",
            createdAtLabel = "刚刚",
        )

        assertEquals("CW 警告", notePreviewText(base.copy(cw = "CW 警告"), fallback = "fallback"))
        assertEquals("正文", notePreviewText(base.copy(cw = "   "), fallback = "fallback"))
        assertEquals("fallback", notePreviewText(base.copy(text = "   ", cw = null), fallback = "fallback"))
    }

    @Test
    fun notePreviewTextNormalizesCwAndBodyRichTextSyntax() {
        val base = Note(
            id = "note-rich",
            author = FakeData.me,
            text = "$[x2 hello] [docs](https://dc.hhhl.cc)",
            createdAtLabel = "刚刚",
        )

        assertEquals("CW 文档", notePreviewText(base.copy(cw = "$[fg.color=ff0000 CW] [文档](https://dc.hhhl.cc)"), fallback = "fallback"))
        assertEquals("hello docs", notePreviewText(base.copy(cw = "   "), fallback = "fallback"))
    }

    @Test
    fun richTextPlainPreviewTextSharesNormalizationRules() {
        assertEquals("hello docs 👍", richTextPlainPreviewText("$[x2 hello] [docs](https://dc.hhhl.cc) ${'$'}{unicode 1f44d}"))
        assertEquals("", richTextPlainPreviewText("   "))
        assertEquals("", richTextPlainPreviewText(null))
    }

    @Test
    fun truncateRichTextPreviewTextAvoidsSplittingRichTokens() {
        assertEquals("hello...", "hello $[fg.color=ff0000 red text] tail".truncateRichTextPreviewText(18))
        assertEquals("hello...", "hello ${'$'}{fg.color=ff0000 red text} tail".truncateRichTextPreviewText(18))
        assertEquals("hello...", "hello :blobcat: tail".truncateRichTextPreviewText(11))
        assertEquals("see...", "see [docs](https://dc.hhhl.cc/path) tail".truncateRichTextPreviewText(14))
        assertEquals(
            "see...",
            "see [docs [v2]](https://dc.hhhl.cc/wiki/a_(b)) tail".truncateRichTextPreviewText(18),
        )
        assertEquals(
            "see [docs [v2]](https://dc.hhhl.cc/wiki/a_(b))...",
            "see [docs [v2]](https://dc.hhhl.cc/wiki/a_(b)) tail".truncateRichTextPreviewText(47),
        )
    }

    @Test
    fun truncateRichTextPreviewTextAvoidsSplittingLongMfmTokens() {
        val longMfmBody = "a".repeat(180)
        val mfm = "$[fg.color=ff0000 $longMfmBody] tail"
        val braceMfm = "${'$'}{fg.color=ff0000 $longMfmBody} tail"

        assertEquals("...", mfm.truncateRichTextPreviewText(140))
        assertEquals("...", braceMfm.truncateRichTextPreviewText(140))
    }

    @Test
    fun truncateRichTextPreviewTextAvoidsSplittingNestedMfmTokens() {
        val nested = "lead $[x2 before $[fg.color=ff0000 inner] after outer tail] done"
        val mixed = "lead ${'$'}{fg.color=00ff00 before $[jelly inner] after outer tail} done"

        assertEquals("lead...", nested.truncateRichTextPreviewText(48))
        assertEquals("lead...", mixed.truncateRichTextPreviewText(50))
    }

    @Test
    fun truncateRichTextPreviewTextAllowsCutAfterClosedNestedMfmTokens() {
        val nested = "lead $[x2 before $[fg.color=ff0000 inner] after] done"

        assertEquals("lead $[x2 before $[fg.color=ff0000 inner] after] done", nested.truncateRichTextPreviewText(54))
    }

    private fun sampleDriveFile(): DriveFile {
        return DriveFile(
            id = "file-1",
            name = "photo.webp",
            type = "image/webp",
            url = "https://dc.hhhl.cc/files/photo.webp",
            thumbnailUrl = null,
            comment = null,
            size = 1024,
            isSensitive = false,
        )
    }
}
