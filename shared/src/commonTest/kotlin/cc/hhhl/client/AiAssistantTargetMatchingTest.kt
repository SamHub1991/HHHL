package cc.hhhl.client

import cc.hhhl.client.model.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAssistantTargetMatchingTest {
    @Test
    fun fuzzyTargetMatchingRejectsUnsafeShortFragments() {
        assertFalse(aiAssistantFuzzyTargetMatches(name = "Alice", target = "a"))
        assertFalse(aiAssistantFuzzyTargetMatches(name = "lsbot", target = "ls"))
        assertFalse(aiAssistantFuzzyTargetMatches(name = "相亲相爱一家人", target = "相"))
    }

    @Test
    fun fuzzyTargetMatchingAllowsUsefulChineseAndAsciiFragments() {
        assertTrue(aiAssistantFuzzyTargetMatches(name = "Alice", target = "ali"))
        assertTrue(aiAssistantFuzzyTargetMatches(name = "AGI 讨论", target = "agi"))
        assertTrue(aiAssistantFuzzyTargetMatches(name = "相亲相爱一家人", target = "相亲"))
    }

    @Test
    fun cleanTargetKeepsExactShortNamesAvailableToExactMatching() {
        assertTrue(" @ls ".aiAssistantCleanTarget() == "ls")
        assertFalse(aiAssistantFuzzyTargetMatches(name = "ls", target = "ls"))
    }

    @Test
    fun userSearchCandidatesMustStillMatchTargetSafely() {
        val alice = User(id = "user-alice", displayName = "Alice", username = "alice", avatarInitial = "A")
        val ls = User(id = "user-ls", displayName = "LS", username = "ls", avatarInitial = "L")

        assertTrue(listOf(alice).aiAssistantMatchingUsers("a", fuzzy = true).isEmpty())
        assertTrue(listOf(alice).aiAssistantMatchingUsers("ali", fuzzy = true).single() == alice)
        assertTrue(listOf(ls).aiAssistantMatchingUsers("ls", fuzzy = false).single() == ls)
    }
}
