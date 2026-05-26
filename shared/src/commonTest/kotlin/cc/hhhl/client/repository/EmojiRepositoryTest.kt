package cc.hhhl.client.repository

import cc.hhhl.client.api.EmojiApi
import cc.hhhl.client.api.EmojiLoadResult
import cc.hhhl.client.model.CustomEmoji
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class EmojiRepositoryTest {
    @Test
    fun loadReactionOptionsFiltersSensitiveAndLimitsResults() = runTest {
        val repository = EmojiRepository(
            api = fakeApi(
                EmojiLoadResult.Success(
                    listOf(
                        sampleEmoji("blobcat"),
                        sampleEmoji("sensitive", isSensitive = true),
                        sampleEmoji("party"),
                    ),
                ),
            ),
        )

        val result = repository.loadReactionOptions(limit = 1)

        assertIs<EmojiRepositoryResult.Success>(result)
        assertEquals(listOf(":blobcat:"), result.reactionOptions)
        assertEquals("https://dc.hhhl.cc/emoji/blobcat.webp", result.emojiUrls[":blobcat@.:"])
        assertEquals(listOf("blobcat", "party"), result.customEmojis.map { it.name })
    }

    @Test
    fun serverErrorMapsToRepositoryError() = runTest {
        val repository = EmojiRepository(
            api = fakeApi(EmojiLoadResult.ServerError(503, "temporarily unavailable")),
        )

        val result = repository.loadReactionOptions()

        assertIs<EmojiRepositoryResult.Error>(result)
        assertEquals("temporarily unavailable", result.message)
    }

    private fun fakeApi(result: EmojiLoadResult): EmojiApi {
        return object : EmojiApi {
            override suspend fun loadEmojis(): EmojiLoadResult {
                return result
            }
        }
    }

    private fun sampleEmoji(
        name: String,
        isSensitive: Boolean = false,
    ): CustomEmoji {
        return CustomEmoji(
            name = name,
            category = "cat",
            url = "https://dc.hhhl.cc/emoji/$name.webp",
            aliases = emptyList(),
            localOnly = true,
            isSensitive = isSensitive,
        )
    }
}
