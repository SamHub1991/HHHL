package cc.hhhl.client.repository

import cc.hhhl.client.api.ChatStreamingApi
import cc.hhhl.client.api.ChatStreamingEvent
import cc.hhhl.client.api.SharkeyChatStreamingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class ChatStreamingRepository(
    private val tokenProvider: () -> String?,
    private val api: ChatStreamingApi = SharkeyChatStreamingApi(),
) {
    open fun streamRoomMessages(roomId: String): Flow<ChatStreamingEvent> {
        val cleanRoomId = roomId.trim()
        if (cleanRoomId.isEmpty()) {
            return flowOf(
                ChatStreamingEvent.Error("请选择聊天室"),
                ChatStreamingEvent.Closed,
            )
        }
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return flowOf(
                ChatStreamingEvent.Unauthorized,
                ChatStreamingEvent.Closed,
            )
        return api.streamRoomMessages(token, cleanRoomId)
    }
}
