package cc.hhhl.client.ui.component

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.NotificationItem

fun notificationLineText(notification: NotificationItem): String {
    return "${notification.actor.displayName} ${notification.text}"
}

fun chatMessageBodyText(message: ChatMessage): String {
    return message.text.ifBlank { message.file?.name ?: "[附件消息]" }
}
