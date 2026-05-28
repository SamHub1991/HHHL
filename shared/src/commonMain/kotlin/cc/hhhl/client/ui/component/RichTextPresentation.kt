package cc.hhhl.client.ui.component

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NotificationItem

fun notificationLineText(notification: NotificationItem): String {
    return cc.hhhl.client.presentation.notificationLineText(notification)
}

fun chatMessageBodyText(message: ChatMessage): String {
    return cc.hhhl.client.presentation.chatMessageBodyText(message)
}

fun notePreviewText(
    note: Note,
    fallback: String = "无文本内容",
): String {
    return cc.hhhl.client.presentation.notePreviewText(note, fallback)
}

fun notePreviewText(
    text: String,
    cw: String?,
    fallback: String = "无文本内容",
): String {
    return cc.hhhl.client.presentation.notePreviewText(text = text, cw = cw, fallback = fallback)
}

fun richTextPlainPreviewText(text: String?): String {
    return cc.hhhl.client.presentation.richTextPlainPreviewText(text)
}
