package cc.hhhl.client.state

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteMessageStoreTest {
    @Test
    fun codecPreservesMessageTextUserReactionAndFile() {
        val favorite = FavoriteMessage(
            id = favoriteMessageId("account-1", FavoriteMessageConversationType.User, "user-2", "message-1"),
            accountId = "account-1",
            conversationType = FavoriteMessageConversationType.User,
            conversationId = "user-2",
            conversationTitle = "Alice",
            message = ChatMessage(
                id = "message-1",
                roomId = "",
                fromUser = User("user-2", "Alice", "alice", "A"),
                text = "$[x2 正文] :blobcat:",
                createdAtLabel = "刚刚",
                file = DriveFile(
                    id = "file-1",
                    name = "image.webp",
                    type = "image/webp",
                    url = "https://example.com/image.webp",
                    thumbnailUrl = "https://example.com/thumb.webp",
                    comment = "说明",
                    size = 12,
                    isSensitive = false,
                ),
            ),
            savedAtEpochMillis = 123,
            savedAtLabel = "刚刚",
        )

        val restored = FavoriteMessageStoreCodec.decode(FavoriteMessageStoreCodec.encode(listOf(favorite))).single()

        assertEquals(favorite.id, restored.id)
        assertEquals(FavoriteMessageConversationType.User, restored.conversationType)
        assertEquals("Alice", restored.message.fromUser.displayName)
        assertEquals("$[x2 正文] :blobcat:", restored.message.text)
        assertEquals("image.webp", restored.message.file?.name)
    }

    @Test
    fun trimmedFavoriteMessagesKeepsNewestDistinctMessages() {
        val older = sampleFavorite("same", savedAt = 1)
        val newer = sampleFavorite("same", savedAt = 2)
        val other = sampleFavorite("other", savedAt = 3)

        assertEquals(
            listOf("other", "same"),
            listOf(older, newer, other).trimmedFavoriteMessages().map { it.message.id },
        )
    }

    private fun sampleFavorite(messageId: String, savedAt: Long): FavoriteMessage {
        return FavoriteMessage(
            id = favoriteMessageId("account", FavoriteMessageConversationType.Room, "room", messageId),
            accountId = "account",
            conversationType = FavoriteMessageConversationType.Room,
            conversationId = "room",
            conversationTitle = "Room",
            message = ChatMessage(
                id = messageId,
                roomId = "room",
                fromUser = User("user", "User", "user", "U"),
                text = "message",
                createdAtLabel = "now",
            ),
            savedAtEpochMillis = savedAt,
        )
    }
}
