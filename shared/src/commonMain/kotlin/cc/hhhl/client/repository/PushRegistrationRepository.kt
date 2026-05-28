package cc.hhhl.client.repository

import cc.hhhl.client.api.PushRegistrationActionResult
import cc.hhhl.client.api.PushRegistrationApi
import cc.hhhl.client.api.PushRegistrationLookupResult
import cc.hhhl.client.api.PushRegistrationResult
import cc.hhhl.client.api.SharkeyPushRegistrationApi
import cc.hhhl.client.model.PushRegistration
import cc.hhhl.client.model.PushRegistrationInput

open class PushRegistrationRepository(
    private val tokenProvider: () -> String?,
    private val api: PushRegistrationApi = SharkeyPushRegistrationApi(),
) {
    open suspend fun register(input: PushRegistrationInput): PushRegistrationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PushRegistrationRepositoryResult.Unauthorized
        return api.register(token, input).toRepositoryResult()
    }

    open suspend fun showRegistration(endpoint: String): PushRegistrationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PushRegistrationRepositoryResult.Unauthorized
        return when (val result = api.showRegistration(token, endpoint)) {
            is PushRegistrationLookupResult.Success -> PushRegistrationRepositoryResult.LookupSuccess(result.registration)
            PushRegistrationLookupResult.Unauthorized -> PushRegistrationRepositoryResult.Unauthorized
            is PushRegistrationLookupResult.NetworkError -> {
                PushRegistrationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is PushRegistrationLookupResult.ServerError -> PushRegistrationRepositoryResult.Error(result.message)
        }
    }

    open suspend fun updateRegistration(
        endpoint: String,
        sendReadMessage: Boolean,
    ): PushRegistrationRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return PushRegistrationRepositoryResult.Unauthorized
        return api.updateRegistration(token, endpoint, sendReadMessage).toRepositoryResult()
    }

    open suspend fun unregister(endpoint: String): PushRegistrationRepositoryResult {
        return when (val result = api.unregister(endpoint)) {
            PushRegistrationActionResult.Success -> PushRegistrationRepositoryResult.ActionSuccess
            is PushRegistrationActionResult.NetworkError -> {
                PushRegistrationRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is PushRegistrationActionResult.ServerError -> PushRegistrationRepositoryResult.Error(result.message)
        }
    }
}

sealed interface PushRegistrationRepositoryResult {
    data class Success(val registration: PushRegistration) : PushRegistrationRepositoryResult
    data class LookupSuccess(val registration: PushRegistration?) : PushRegistrationRepositoryResult
    data object ActionSuccess : PushRegistrationRepositoryResult
    data object Unauthorized : PushRegistrationRepositoryResult
    data class Error(val message: String) : PushRegistrationRepositoryResult
}

private fun PushRegistrationResult.toRepositoryResult(): PushRegistrationRepositoryResult {
    return when (this) {
        is PushRegistrationResult.Success -> PushRegistrationRepositoryResult.Success(registration)
        PushRegistrationResult.Unauthorized -> PushRegistrationRepositoryResult.Unauthorized
        is PushRegistrationResult.NetworkError -> PushRegistrationRepositoryResult.Error("无法连接服务器：$message")
        is PushRegistrationResult.ServerError -> PushRegistrationRepositoryResult.Error(message)
    }
}
