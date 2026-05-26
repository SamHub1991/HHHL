package cc.hhhl.client.ui.component

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
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
