package cc.hhhl.client

import cc.hhhl.client.ai.AiPrompt
import cc.hhhl.client.ai.AiRepository
import cc.hhhl.client.ai.AiRepositoryResult
import cc.hhhl.client.ai.AiServiceMode
import cc.hhhl.client.ai.AiSettings
import cc.hhhl.client.ai.AiSnapshot
import cc.hhhl.client.ai.AiStateHolder
import cc.hhhl.client.ai.AiStore
import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.ui.screen.AiAssistantActionProposal
import cc.hhhl.client.ui.screen.AiAssistantMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantReplyGenerationTest {
    @Test
    fun staleAssistantReplyAfterConversationClearDoesNotRestoreMessagesOrApproveActions() = runTest {
        val repository = DelayedAiRepository()
        val holder = AiStateHolder(
            store = MemoryAiStore(
                AiSnapshot(
                    settings = AiSettings(
                        serviceMode = AiServiceMode.LocalOnly,
                        baseUrl = "local-ai",
                        apiKey = "test-key",
                        chatModel = "test-model",
                    ),
                ),
            ),
            accountId = "account-1",
            repository = repository,
            scope = TestScope(testScheduler),
        )
        holder.restore()

        var generation = 0L
        var messages = emptyList<AiAssistantMessage>()
        var pendingPrompt: String? = null
        var draft = "删除当前聊天室"
        var attachments = emptyList<DriveFile>()
        val autoApprovedActions = mutableListOf<AiAssistantActionProposal>()

        startAiAssistantReply(
            prompt = draft,
            attachments = emptyList(),
            currentAttachments = attachments,
            currentDraft = draft,
            messages = messages,
            pendingPrompt = pendingPrompt,
            aiProcessing = false,
            attachmentUploading = false,
            aiStateHolder = holder,
            scope = this,
            contextTextProvider = { "" },
            requestGeneration = generation,
            isRequestCurrent = { requestGeneration ->
                aiAssistantReplyRequestIsCurrent(
                    requestGeneration = requestGeneration,
                    currentGeneration = generation,
                )
            },
            onToast = {},
            onMessagesChanged = { messages = it },
            onPendingPromptChanged = { pendingPrompt = it },
            onDraftChanged = { draft = it },
            onAttachmentsChanged = { attachments = it },
            onAutoApproveActions = { autoApprovedActions += it },
        )

        assertEquals(2, messages.size)
        assertTrue(pendingPrompt.orEmpty().contains("删除当前聊天室"))

        generation += 1
        messages = emptyList()
        pendingPrompt = null
        draft = ""
        attachments = emptyList()

        repository.result.complete(AiRepositoryResult.Success("会删除当前聊天室，需要确认。"))
        advanceUntilIdle()

        assertTrue(messages.isEmpty())
        assertEquals(null, pendingPrompt)
        assertTrue(autoApprovedActions.isEmpty())
    }
}

private class DelayedAiRepository : AiRepository() {
    val result = CompletableDeferred<AiRepositoryResult>()

    override suspend fun complete(
        settings: AiSettings,
        prompt: AiPrompt,
        model: String,
        fileIds: List<String>,
    ): AiRepositoryResult {
        return result.await()
    }
}

private class MemoryAiStore(
    initialSnapshot: AiSnapshot = AiSnapshot(),
) : AiStore {
    private var snapshot: AiSnapshot = initialSnapshot

    override fun read(accountId: String): AiSnapshot = snapshot

    override fun write(accountId: String, snapshot: AiSnapshot) {
        this.snapshot = snapshot
    }

    override fun clearAccount(accountId: String) {
        snapshot = AiSnapshot()
    }
}
