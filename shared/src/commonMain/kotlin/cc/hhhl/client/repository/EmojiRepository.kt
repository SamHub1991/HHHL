package cc.hhhl.client.repository

import cc.hhhl.client.api.EmojiApi
import cc.hhhl.client.api.EmojiLoadResult
import cc.hhhl.client.api.SharkeyEmojiApi
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.ui.component.customEmojiUrlMap

open class EmojiRepository(
    private val api: EmojiApi = SharkeyEmojiApi(),
) {
    private val successfulResultsByLimit = mutableMapOf<Int, EmojiRepositoryResult.Success>()

    open suspend fun loadReactionOptions(limit: Int = DEFAULT_REACTION_OPTION_LIMIT): EmojiRepositoryResult {
        val safeLimit = limit.coerceAtLeast(0)
        successfulResultsByLimit[safeLimit]?.let { return it }

        return when (val result = api.loadEmojis()) {
            is EmojiLoadResult.Success -> buildSuccessResult(result.emojis, safeLimit)
            is EmojiLoadResult.NetworkError -> {
                EmojiRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is EmojiLoadResult.ServerError -> EmojiRepositoryResult.Error(result.message)
        }
    }

    private fun buildSuccessResult(
        emojis: List<CustomEmoji>,
        limit: Int,
    ): EmojiRepositoryResult.Success {
        val success = EmojiRepositoryResult.Success(
            reactionOptions = emojis
                .asSequence()
                .filter { !it.isSensitive }
                .map { it.reactionCode }
                .filter { it.isNotBlank() }
                .distinct()
                .take(limit)
                .toList(),
            emojiUrls = customEmojiUrlMap(emojis),
            customEmojis = emojis
                .filter { !it.isSensitive }
                .distinctBy { it.name }
                .sortedWith(compareBy<CustomEmoji> { it.category.orEmpty() }.thenBy { it.name }),
        )
        successfulResultsByLimit[limit] = success
        return success
    }

    private companion object {
        const val DEFAULT_REACTION_OPTION_LIMIT = 240
    }
}

sealed interface EmojiRepositoryResult {
    data class Success(
        val reactionOptions: List<String>,
        val emojiUrls: Map<String, String> = emptyMap(),
        val customEmojis: List<CustomEmoji> = emptyList(),
    ) : EmojiRepositoryResult

    data class Error(val message: String) : EmojiRepositoryResult
}
