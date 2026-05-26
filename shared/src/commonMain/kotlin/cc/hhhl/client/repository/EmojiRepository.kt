package cc.hhhl.client.repository

import cc.hhhl.client.api.EmojiApi
import cc.hhhl.client.api.EmojiLoadResult
import cc.hhhl.client.api.SharkeyEmojiApi
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.ui.component.customEmojiUrlMap

open class EmojiRepository(
    private val api: EmojiApi = SharkeyEmojiApi(),
) {
    open suspend fun loadReactionOptions(limit: Int = DEFAULT_REACTION_OPTION_LIMIT): EmojiRepositoryResult {
        return when (val result = api.loadEmojis()) {
            is EmojiLoadResult.Success -> EmojiRepositoryResult.Success(
                reactionOptions = result.emojis
                    .asSequence()
                    .filter { !it.isSensitive }
                    .map { it.reactionCode }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(limit.coerceAtLeast(0))
                    .toList(),
                emojiUrls = customEmojiUrlMap(result.emojis),
                customEmojis = result.emojis
                    .filter { !it.isSensitive }
                    .distinctBy { it.name }
                    .sortedWith(compareBy<CustomEmoji> { it.category.orEmpty() }.thenBy { it.name }),
            )
            is EmojiLoadResult.NetworkError -> {
                EmojiRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is EmojiLoadResult.ServerError -> EmojiRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_REACTION_OPTION_LIMIT = 40
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
