package cc.hhhl.client.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationStateHolderTest {
    @Test
    fun emitExecutesMatchingRuleAndPersistsLog() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Log matches",
                        trigger = AutomationTrigger.ChatMessage,
                        conditions = listOf(
                            AutomationCondition(
                                id = "condition-1",
                                type = AutomationConditionType.MessageContains,
                                value = "hello",
                            ),
                        ),
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{sender.name}}: {{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                senderName = "Alice",
                messageText = "hello world",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, holder.state.value.logs.size)
        assertEquals("Alice: hello world", holder.state.value.logs.single().message)
        assertTrue(holder.state.value.logs.single().success)
        assertEquals("Alice: hello world", store.lastSnapshot.logs.single().message)
    }

    @Test
    fun emitIgnoresOwnMessageWhenRuleRequestsIt() = runTest {
        val store = MemoryAutomationStore(
            AutomationSnapshot(
                rules = listOf(
                    AutomationRule(
                        id = "rule-1",
                        name = "Ignore self",
                        trigger = AutomationTrigger.ChatMessage,
                        ignoreOwnMessages = true,
                        actions = listOf(
                            AutomationAction(
                                id = "action-1",
                                type = AutomationActionType.AddLog,
                                bodyTemplate = "{{message.text}}",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val holder = AutomationStateHolder(
            store = store,
            accountId = "account-1",
            scope = TestScope(testScheduler),
        )
        holder.restore()

        holder.emit(
            AutomationEvent(
                id = "event-1",
                trigger = AutomationTrigger.ChatMessage,
                messageText = "self message",
                isFromCurrentUser = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.logs)
    }
}

private class MemoryAutomationStore(
    initialSnapshot: AutomationSnapshot = AutomationSnapshot(),
) : AutomationStore {
    var lastSnapshot: AutomationSnapshot = initialSnapshot
        private set

    override fun read(accountId: String): AutomationSnapshot = lastSnapshot

    override fun write(accountId: String, snapshot: AutomationSnapshot) {
        lastSnapshot = snapshot
    }

    override fun clearAccount(accountId: String) {
        lastSnapshot = AutomationSnapshot()
    }
}
