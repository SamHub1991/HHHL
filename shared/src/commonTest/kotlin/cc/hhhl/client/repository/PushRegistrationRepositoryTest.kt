package cc.hhhl.client.repository

import cc.hhhl.client.api.PushRegistrationActionResult
import cc.hhhl.client.api.PushRegistrationApi
import cc.hhhl.client.api.PushRegistrationLookupResult
import cc.hhhl.client.api.PushRegistrationResult
import cc.hhhl.client.model.PushRegistration
import cc.hhhl.client.model.PushRegistrationInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PushRegistrationRepositoryTest {
    @Test
    fun registerRequiresToken() = runTest {
        val repository = PushRegistrationRepository(
            tokenProvider = { null },
            api = fakeApi(),
        )

        assertEquals(
            PushRegistrationRepositoryResult.Unauthorized,
            repository.register(sampleInput()),
        )
    }

    @Test
    fun registerMapsSuccess() = runTest {
        val registration = sampleRegistration()
        val repository = PushRegistrationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(registerResult = PushRegistrationResult.Success(registration)),
        )

        assertEquals(
            PushRegistrationRepositoryResult.Success(registration),
            repository.register(sampleInput()),
        )
    }

    @Test
    fun showRegistrationMapsMissingSubscription() = runTest {
        val repository = PushRegistrationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(showResult = PushRegistrationLookupResult.Success(null)),
        )

        assertEquals(
            PushRegistrationRepositoryResult.LookupSuccess(null),
            repository.showRegistration("https://push.example/1"),
        )
    }

    @Test
    fun updateRegistrationMapsServerError() = runTest {
        val repository = PushRegistrationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(registerResult = PushRegistrationResult.ServerError(400, "invalid subscription")),
        )

        assertEquals(
            PushRegistrationRepositoryResult.Error("invalid subscription"),
            repository.updateRegistration("https://push.example/1", sendReadMessage = true),
        )
    }

    @Test
    fun unregisterDoesNotRequireToken() = runTest {
        val repository = PushRegistrationRepository(
            tokenProvider = { null },
            api = fakeApi(unregisterResult = PushRegistrationActionResult.Success),
        )

        assertEquals(
            PushRegistrationRepositoryResult.ActionSuccess,
            repository.unregister("https://push.example/1"),
        )
    }

    private fun fakeApi(
        registerResult: PushRegistrationResult = PushRegistrationResult.Success(sampleRegistration()),
        showResult: PushRegistrationLookupResult = PushRegistrationLookupResult.Success(sampleRegistration()),
        unregisterResult: PushRegistrationActionResult = PushRegistrationActionResult.Success,
    ): PushRegistrationApi {
        return object : PushRegistrationApi {
            override suspend fun register(
                token: String,
                input: PushRegistrationInput,
            ): PushRegistrationResult = registerResult

            override suspend fun showRegistration(
                token: String,
                endpoint: String,
            ): PushRegistrationLookupResult = showResult

            override suspend fun updateRegistration(
                token: String,
                endpoint: String,
                sendReadMessage: Boolean,
            ): PushRegistrationResult = registerResult

            override suspend fun unregister(endpoint: String): PushRegistrationActionResult = unregisterResult
        }
    }

    private fun sampleInput(): PushRegistrationInput {
        return PushRegistrationInput(
            endpoint = "https://push.example/1",
            auth = "auth-key",
            publicKey = "public-key",
            sendReadMessage = true,
        )
    }

    private fun sampleRegistration(): PushRegistration {
        return PushRegistration(
            userId = "user-1",
            endpoint = "https://push.example/1",
            sendReadMessage = true,
            key = "server-key",
        )
    }
}
