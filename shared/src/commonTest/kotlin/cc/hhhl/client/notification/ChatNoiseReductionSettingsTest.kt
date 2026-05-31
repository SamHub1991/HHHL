package cc.hhhl.client.notification

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatNoiseReductionSettingsTest {
    @Test
    fun importantOnlyKeepsAttentionKeywordAndUserMatches() {
        val settings = ChatNoiseReductionSettings(
            importantOnly = true,
            keywordRules = listOf("部署"),
            userRules = listOf("@alice@example.social"),
        )

        assertEquals(
            true,
            settings.evaluate(ChatNoiseReductionCandidate(attentionKindName = "提到我")).shouldNotify,
        )
        assertEquals(
            true,
            settings.evaluate(ChatNoiseReductionCandidate(text = "今晚部署完成了吗")).shouldNotify,
        )
        assertEquals(
            true,
            settings.evaluate(
                ChatNoiseReductionCandidate(
                    senderUsername = "Alice",
                    senderHost = "example.social",
                    text = "hello",
                ),
            ).shouldNotify,
        )
        assertEquals(
            false,
            settings.evaluate(ChatNoiseReductionCandidate(text = "普通聊天")).shouldNotify,
        )
    }

    @Test
    fun aiImportanceOnlyRequestsAiWhenRulesDoNotMatch() {
        val settings = ChatNoiseReductionSettings(
            importantOnly = true,
            aiImportanceEnabled = true,
        )
        val candidate = ChatNoiseReductionCandidate(text = "看起来普通")

        val pending = settings.evaluate(candidate)
        assertEquals(false, pending.shouldNotify)
        assertEquals(true, pending.requiresAi)
        assertEquals(true, settings.evaluate(candidate, aiImportant = true).shouldNotify)
        assertEquals(false, settings.evaluate(candidate, aiImportant = false).shouldNotify)
    }

    @Test
    fun rulesAndAiDecisionParsingAreNormalized() {
        assertEquals(listOf("部署", "alice"), " 部署，alice\n部署 ".toChatNoiseRules())
        assertEquals(true, "Important: true\n需要我处理".aiChatImportanceDecision())
        assertEquals(false, "Important: false\n闲聊".aiChatImportanceDecision())
    }
}
