package cc.hhhl.client.repository

import cc.hhhl.client.api.AccountRecoveryApi
import cc.hhhl.client.api.AccountRecoveryResult
import cc.hhhl.client.api.SharkeyAccountRecoveryApi

open class AccountRecoveryRepository(
    private val api: AccountRecoveryApi = SharkeyAccountRecoveryApi(),
) {
    open suspend fun requestPasswordReset(username: String, email: String): AccountRecoveryRepositoryResult {
        return mapResult(api.requestPasswordReset(username, email))
    }

    open suspend fun resetPassword(token: String, password: String): AccountRecoveryRepositoryResult {
        return mapResult(api.resetPassword(token, password))
    }

    private fun mapResult(result: AccountRecoveryResult): AccountRecoveryRepositoryResult {
        return when (result) {
            AccountRecoveryResult.Success -> AccountRecoveryRepositoryResult.Success
            is AccountRecoveryResult.NetworkError -> AccountRecoveryRepositoryResult.Error("无法连接服务器：${result.message}")
            is AccountRecoveryResult.ServerError -> AccountRecoveryRepositoryResult.Error(result.message)
        }
    }
}

sealed interface AccountRecoveryRepositoryResult {
    data object Success : AccountRecoveryRepositoryResult
    data class Error(val message: String) : AccountRecoveryRepositoryResult
}
