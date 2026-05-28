package cc.hhhl.client.cache

import cc.hhhl.client.model.ChatMessage
import cc.hhhl.client.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageCacheTest {
    @Test
    fun inMemoryCacheTrimsMessagesPerConversation() = runTest {
        val cache = InMemoryChatMessageCache()
        val key = cacheKey("room-1")

        cache.write(key, (0 until 520).map { index -> sampleMessage("m$index") })

        val restored = cache.read(key)
        assertEquals(500, restored.size)
        assertEquals("m20", restored.first().id)
        assertEquals("m519", restored.last().id)
    }

    @Test
    fun inMemoryCacheTrimsOldestConversationsAndCompleteMarkers() = runTest {
        val cache = InMemoryChatMessageCache()
        val oldestKey = cacheKey("room-0")

        repeat(65) { index ->
            val key = cacheKey("room-$index")
            cache.write(key, listOf(sampleMessage("m$index")))
            cache.markComplete(key)
        }

        assertTrue(cache.read(oldestKey).isEmpty())
        assertFalse(cache.isComplete(oldestKey))
        assertEquals(64, cache.readAccount("account-1").size)
        assertTrue(cache.isComplete(cacheKey("room-64")))
    }

    @Test
    fun codecTrimsMessagesPerConversation() {
        val key = cacheKey("room-1")
        val payload = ChatMessageCacheCodec.encode(
            mapOf(key to (0 until 520).map { index -> sampleMessage("m$index") }),
        )

        val restored = ChatMessageCacheCodec.decode(payload).getValue(key)
        assertEquals(500, restored.size)
        assertEquals("m20", restored.first().id)
        assertEquals("m519", restored.last().id)
    }

    private fun cacheKey(conversationId: String): ChatMessageCacheKey {
        return ChatMessageCacheKey(
            accountId = "account-1",
            type = ChatMessageCacheConversationType.Room,
            conversationId = conversationId,
        )
    }

    private fun sampleMessage(id: String): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = "room-1",
            fromUser = User("user-1", "Alice", "alice", "A"),
            text = "message $id",
            createdAtLabel = "2026-05-25 01:23",
        )
    }
}
