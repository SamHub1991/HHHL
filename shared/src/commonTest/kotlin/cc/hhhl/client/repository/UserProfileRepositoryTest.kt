package cc.hhhl.client.repository

import cc.hhhl.client.api.UserProfileApi
import cc.hhhl.client.api.UserAvailabilityResult
import cc.hhhl.client.api.UserProfileLoadResult
import cc.hhhl.client.api.UserProfileUpdateDraft
import cc.hhhl.client.api.UserProfileUpdateResult
import cc.hhhl.client.api.USER_PROFILE_DESCRIPTION_MAX_LENGTH
import cc.hhhl.client.api.USER_PROFILE_NAME_MAX_LENGTH
import cc.hhhl.client.fake.FakeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserProfileRepositoryTest {
    @Test
    fun loadUsesTokenAndUserIdProviders() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                calls = calls,
                result = UserProfileLoadResult.Success(FakeData.me),
            ),
        )

        val result = repository.load()

        assertEquals(listOf(ApiCall("token-123", "user-1")), calls)
        assertEquals(UserProfileRepositoryResult.Success(FakeData.me), result)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserProfileRepository(
            tokenProvider = { null },
            userIdProvider = { "user-1" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserProfileLoadResult.Success(FakeData.me),
            ),
        )

        assertIs<UserProfileRepositoryResult.Unauthorized>(repository.load())
        assertEquals(0, calls)
    }

    @Test
    fun resolveMentionUsesTokenAndCleanUsername() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                calls = calls,
                result = UserProfileLoadResult.Success(FakeData.me),
            ),
        )

        val result = repository.resolveMention(" @alice ")

        assertEquals(listOf(ApiCall("token-123", "alice")), calls)
        assertEquals(UserProfileRepositoryResult.Success(FakeData.me), result)
    }

    @Test
    fun resolveMentionMissingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserProfileRepository(
            tokenProvider = { null },
            userIdProvider = { "user-1" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = UserProfileLoadResult.Success(FakeData.me),
            ),
        )

        assertIs<UserProfileRepositoryResult.Unauthorized>(repository.resolveMention("alice"))
        assertEquals(0, calls)
    }

    @Test
    fun updateProfileUsesTokenAndTrimsEditableFields() = runTest {
        val updates = mutableListOf<UpdateCall>()
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserProfileLoadResult.Success(FakeData.me),
                updateResult = UserProfileUpdateResult.Success(FakeData.me.copy(displayName = "Alice New")),
                updates = updates,
            ),
        )

        val result = repository.updateProfile(" Alice New ", " new bio ")

        assertEquals(listOf(UpdateCall("token-123", "Alice New", "new bio")), updates)
        assertEquals(
            UserProfileRepositoryResult.Success(FakeData.me.copy(displayName = "Alice New")),
            result,
        )
    }

    @Test
    fun updateProfileRequiresNameBeforeCallingApi() = runTest {
        var calls = 0
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserProfileLoadResult.Success(FakeData.me),
                onCall = { calls += 1 },
            ),
        )

        val result = repository.updateProfile(" ", "bio")

        assertEquals(UserProfileRepositoryResult.Error("请输入名称"), result)
        assertEquals(0, calls)
    }

    @Test
    fun updateProfileValidatesEditableFieldLengthsBeforeCallingApi() = runTest {
        var calls = 0
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserProfileLoadResult.Success(FakeData.me),
                onCall = { calls += 1 },
            ),
        )

        val longName = "a".repeat(USER_PROFILE_NAME_MAX_LENGTH + 1)
        val longDescription = "a".repeat(USER_PROFILE_DESCRIPTION_MAX_LENGTH + 1)

        assertEquals(
            UserProfileRepositoryResult.Error("名称不能超过 ${USER_PROFILE_NAME_MAX_LENGTH} 字"),
            repository.updateProfile(longName, "bio"),
        )
        assertEquals(
            UserProfileRepositoryResult.Error("简介不能超过 ${USER_PROFILE_DESCRIPTION_MAX_LENGTH} 字"),
            repository.updateProfile("Alice", longDescription),
        )
        assertEquals(0, calls)
    }

    @Test
    fun checkUsernameAvailabilityTrimsInput() = runTest {
        val availabilityCalls = mutableListOf<AvailabilityCall>()
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserProfileLoadResult.Success(FakeData.me),
                availabilityCalls = availabilityCalls,
                availabilityResult = UserAvailabilityResult.Success(available = true),
            ),
        )

        val result = repository.checkUsernameAvailable(" @alice ")

        assertEquals(UserProfileAvailabilityRepositoryResult.Success(available = true), result)
        assertEquals(listOf(AvailabilityCall("username", "alice")), availabilityCalls)
    }

    @Test
    fun checkEmailAvailabilityMapsReason() = runTest {
        val availabilityCalls = mutableListOf<AvailabilityCall>()
        val repository = UserProfileRepository(
            tokenProvider = { "token-123" },
            userIdProvider = { "user-1" },
            api = fakeApi(
                result = UserProfileLoadResult.Success(FakeData.me),
                availabilityCalls = availabilityCalls,
                availabilityResult = UserAvailabilityResult.Success(available = false, reason = "used"),
            ),
        )

        val result = repository.checkEmailAddressAvailable(" alice@example.com ")

        assertEquals(UserProfileAvailabilityRepositoryResult.Success(available = false, reason = "used"), result)
        assertEquals(listOf(AvailabilityCall("email", "alice@example.com")), availabilityCalls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: UserProfileLoadResult,
        updateResult: UserProfileUpdateResult = UserProfileUpdateResult.Success(FakeData.me),
        updates: MutableList<UpdateCall> = mutableListOf(),
        availabilityCalls: MutableList<AvailabilityCall> = mutableListOf(),
        availabilityResult: UserAvailabilityResult = UserAvailabilityResult.Success(available = true),
        onCall: () -> Unit = {},
    ): UserProfileApi {
        return object : UserProfileApi {
            override suspend fun loadProfile(
                token: String,
                userId: String,
            ): UserProfileLoadResult {
                onCall()
                calls.add(ApiCall(token, userId))
                return result
            }

            override suspend fun loadProfileByUsername(
                token: String,
                username: String,
            ): UserProfileLoadResult {
                onCall()
                calls.add(ApiCall(token, username))
                return result
            }

            override suspend fun updateProfile(
                token: String,
                draft: UserProfileUpdateDraft,
            ): UserProfileUpdateResult {
                onCall()
                updates.add(UpdateCall(token, draft.name.orEmpty(), draft.description))
                return updateResult
            }

            override suspend fun checkUsernameAvailable(username: String): UserAvailabilityResult {
                onCall()
                availabilityCalls.add(AvailabilityCall("username", username))
                return availabilityResult
            }

            override suspend fun checkEmailAddressAvailable(emailAddress: String): UserAvailabilityResult {
                onCall()
                availabilityCalls.add(AvailabilityCall("email", emailAddress))
                return availabilityResult
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val userId: String,
    )

    private data class UpdateCall(
        val token: String,
        val name: String,
        val description: String,
    )

    private data class AvailabilityCall(
        val type: String,
        val value: String,
    )
}
