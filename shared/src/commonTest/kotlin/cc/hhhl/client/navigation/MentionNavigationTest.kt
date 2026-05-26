package cc.hhhl.client.navigation

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.repository.UserProfileRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals

class MentionNavigationTest {
    @Test
    fun successNavigatesToResolvedUserProfile() {
        val target = mentionNavigationTarget(
            username = "alice",
            result = UserProfileRepositoryResult.Success(FakeData.me.copy(id = "user-1")),
        )

        assertEquals(MentionNavigationTarget.UserProfile("user-1"), target)
    }

    @Test
    fun errorFallsBackToDiscoverUserSearch() {
        val target = mentionNavigationTarget(
            username = " @alice ",
            result = UserProfileRepositoryResult.Error("not found"),
        )

        assertEquals(MentionNavigationTarget.DiscoverSearch("alice"), target)
    }
}
