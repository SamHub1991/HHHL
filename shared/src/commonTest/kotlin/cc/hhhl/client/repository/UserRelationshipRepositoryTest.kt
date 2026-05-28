package cc.hhhl.client.repository

import cc.hhhl.client.api.UserRelationshipApi
import cc.hhhl.client.api.UserRelationshipLoadResult
import cc.hhhl.client.api.UserRelationshipListResult
import cc.hhhl.client.api.UserRelationshipResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.UserRelationship
import cc.hhhl.client.model.UserRelationshipListEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserRelationshipRepositoryTest {
    @Test
    fun loadRelationUsesTokenAndUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val relationship = UserRelationship(userId = "user-1", isMuted = true)
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                relationResult = UserRelationshipLoadResult.Success(relationship),
                result = UserRelationshipResult.Success,
            ),
        )

        val result = repository.loadRelation("user-1")

        assertEquals(UserRelationshipRepositoryResult.RelationLoaded(relationship), result)
        assertEquals(listOf(ApiCall("relation", "token-123", "user-1")), calls)
    }

    @Test
    fun followUsesTokenAndUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = UserRelationshipResult.Success),
        )

        val result = repository.follow("user-1")

        assertEquals(UserRelationshipRepositoryResult.Success, result)
        assertEquals(listOf(ApiCall("follow", "token-123", "user-1")), calls)
    }

    @Test
    fun muteUnmuteBlockAndUnblockUseTokenAndUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = UserRelationshipResult.Success),
        )

        assertEquals(UserRelationshipRepositoryResult.Success, repository.mute("user-1"))
        assertEquals(UserRelationshipRepositoryResult.Success, repository.unmute("user-1"))
        assertEquals(UserRelationshipRepositoryResult.Success, repository.block("user-1"))
        assertEquals(UserRelationshipRepositoryResult.Success, repository.unblock("user-1"))

        assertEquals(
            listOf(
                ApiCall("mute", "token-123", "user-1"),
                ApiCall("unmute", "token-123", "user-1"),
                ApiCall("block", "token-123", "user-1"),
                ApiCall("unblock", "token-123", "user-1"),
            ),
            calls,
        )
    }

    @Test
    fun loadsMutedAndBlockedUsersWithPaginationCursor() = runTest {
        val calls = mutableListOf<ApiCall>()
        val existing = UserRelationshipListEntry(
            id = "mute-old",
            user = FakeData.me.copy(id = "old-user"),
        )
        val next = UserRelationshipListEntry(
            id = "mute-new",
            user = FakeData.me.copy(id = "new-user"),
        )
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                mutedListResult = UserRelationshipListResult.Success(listOf(next)),
                blockedListResult = UserRelationshipListResult.Success(emptyList()),
                result = UserRelationshipResult.Success,
            ),
        )

        val muted = repository.loadMutedUsers(listOf(existing))
        val blocked = repository.loadBlockedUsers()

        assertEquals(
            UserRelationshipListRepositoryResult.Success(
                entries = listOf(existing, next),
                endReached = false,
            ),
            muted,
        )
        assertEquals(
            UserRelationshipListRepositoryResult.Success(
                entries = emptyList(),
                endReached = true,
            ),
            blocked,
        )
        assertEquals(
            listOf(
                ApiCall("mutedList", "token-123", "mute-old"),
                ApiCall("blockedList", "token-123", ""),
            ),
            calls,
        )
    }

    @Test
    fun unfollowUsesTokenAndUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = UserRelationshipResult.Success),
        )

        val result = repository.unfollow("user-1")

        assertEquals(UserRelationshipRepositoryResult.Success, result)
        assertEquals(listOf(ApiCall("unfollow", "token-123", "user-1")), calls)
    }

    @Test
    fun updateAndInvalidateFollowingUseTokenAndUserId() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(calls = calls, result = UserRelationshipResult.Success),
        )

        assertEquals(
            UserRelationshipRepositoryResult.Success,
            repository.updateFollowing("user-1", notify = "normal", withReplies = true),
        )
        assertEquals(UserRelationshipRepositoryResult.Success, repository.invalidateFollowing("user-1"))
        assertEquals(
            UserRelationshipRepositoryResult.Success,
            repository.updateAllFollowing(notify = "none", withReplies = false),
        )

        assertEquals(
            listOf(
                ApiCall("updateFollowing:normal:true", "token-123", "user-1"),
                ApiCall("invalidateFollowing", "token-123", "user-1"),
                ApiCall("updateAllFollowing:none:false", "token-123", ""),
            ),
            calls,
        )
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserRelationshipRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserRelationshipResult.Success,
            ),
        )

        assertIs<UserRelationshipRepositoryResult.Unauthorized>(repository.follow("user-1"))
        assertEquals(0, calls)
    }

    @Test
    fun blankUserIdReturnsErrorWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserRelationshipRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserRelationshipResult.Success,
            ),
        )

        assertEquals(
            UserRelationshipRepositoryResult.Error("无法操作用户"),
            repository.follow(" "),
        )
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        relationResult: UserRelationshipLoadResult = UserRelationshipLoadResult.Success(UserRelationship("user-1")),
        mutedListResult: UserRelationshipListResult = UserRelationshipListResult.Success(emptyList()),
        blockedListResult: UserRelationshipListResult = UserRelationshipListResult.Success(emptyList()),
        result: UserRelationshipResult,
        onCall: () -> Unit = {},
    ): UserRelationshipApi {
        return object : UserRelationshipApi {
            override suspend fun loadRelation(
                token: String,
                userId: String,
            ): UserRelationshipLoadResult {
                onCall()
                calls.add(ApiCall("relation", token, userId))
                return relationResult
            }

            override suspend fun loadMutedUsers(
                token: String,
                limit: Int,
                untilId: String?,
            ): UserRelationshipListResult {
                onCall()
                calls.add(ApiCall("mutedList", token, untilId.orEmpty()))
                return mutedListResult
            }

            override suspend fun loadBlockedUsers(
                token: String,
                limit: Int,
                untilId: String?,
            ): UserRelationshipListResult {
                onCall()
                calls.add(ApiCall("blockedList", token, untilId.orEmpty()))
                return blockedListResult
            }

            override suspend fun follow(
                token: String,
                userId: String,
                withReplies: Boolean?,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("follow", token, userId))
                return result
            }

            override suspend fun unfollow(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("unfollow", token, userId))
                return result
            }

            override suspend fun updateFollowing(
                token: String,
                userId: String,
                notify: String?,
                withReplies: Boolean?,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("updateFollowing:$notify:$withReplies", token, userId))
                return result
            }

            override suspend fun updateAllFollowing(
                token: String,
                notify: String?,
                withReplies: Boolean?,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("updateAllFollowing:$notify:$withReplies", token, ""))
                return result
            }

            override suspend fun invalidateFollowing(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("invalidateFollowing", token, userId))
                return result
            }

            override suspend fun mute(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("mute", token, userId))
                return result
            }

            override suspend fun unmute(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("unmute", token, userId))
                return result
            }

            override suspend fun block(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("block", token, userId))
                return result
            }

            override suspend fun unblock(
                token: String,
                userId: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("unblock", token, userId))
                return result
            }

            override suspend fun reportUser(
                token: String,
                userId: String,
                comment: String,
            ): UserRelationshipResult {
                onCall()
                calls.add(ApiCall("report", token, userId))
                return result
            }
        }
    }

    private data class ApiCall(
        val action: String,
        val token: String,
        val userId: String,
    )
}
