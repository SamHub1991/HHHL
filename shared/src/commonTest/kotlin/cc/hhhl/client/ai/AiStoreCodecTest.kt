package cc.hhhl.client.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiStoreCodecTest {
    @Test
    fun snapshotRoundTripsSettingsAndTasks() {
        val snapshot = AiSnapshot(
            settings = AiSettings(
                enabled = true,
                provider = AiProviderPreset.DeepSeek,
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "secret",
                chatModel = "deepseek-chat",
                backgroundAllowed = true,
                wifiOnlyBackground = true,
                assistantLowRiskAutoApproval = true,
                assistantHighRiskAutoApproval = true,
                floatingAssistantEnabled = false,
                automationRuleDraftModel = AiAutomationModelConfig(
                    enabled = true,
                    provider = AiProviderPreset.Qwen,
                    baseUrl = "https://automation.example.com/v1",
                    apiKey = "automation-secret",
                    model = "automation-model",
                ),
            ),
            tasks = listOf(
                AiTask(
                    id = "task-1",
                    accountId = "account-1",
                    kind = AiTaskKind.TimelineDigest,
                    input = AiTaskInput(
                        timelineTitle = "首页",
                        timelineNotes = listOf(
                            AiPostContext(
                                id = "note-1",
                                author = "Alice",
                                username = "alice",
                                text = "今天发布了新版本",
                                stats = "回复 1 · 转发 2 · 反应 3",
                            ),
                        ),
                    ),
                    status = AiTaskStatus.Completed,
                    resultText = "版本更新摘要",
                ),
            ),
        )

        val decoded = AiStoreCodec.decode(AiStoreCodec.encode(snapshot))

        assertEquals(snapshot.settings.provider, decoded.settings.provider)
        assertEquals("deepseek-chat", decoded.settings.chatModel)
        assertTrue(decoded.settings.wifiOnlyBackground)
        assertTrue(decoded.settings.assistantLowRiskAutoApproval)
        assertTrue(decoded.settings.assistantHighRiskAutoApproval)
        assertEquals(false, decoded.settings.floatingAssistantEnabled)
        assertTrue(decoded.settings.automationRuleDraftModel.enabled)
        assertEquals(AiProviderPreset.Qwen, decoded.settings.automationRuleDraftModel.provider)
        assertEquals("automation-model", decoded.settings.automationRuleDraftModel.model)
        assertEquals(AiTaskKind.TimelineDigest, decoded.tasks.single().kind)
        assertEquals("版本更新摘要", decoded.tasks.single().resultText)
        assertEquals("Alice", decoded.tasks.single().input.timelineNotes.single().author)
    }

    @Test
    fun invalidPayloadFallsBackToEmptySnapshot() {
        val decoded = AiStoreCodec.decode("not json")

        assertEquals(AiSettings(), decoded.settings)
        assertEquals(emptyList(), decoded.tasks)
    }
}
