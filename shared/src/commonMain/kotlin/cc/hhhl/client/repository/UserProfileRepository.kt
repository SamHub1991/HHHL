package cc.hhhl.client.repository

import cc.hhhl.client.api.SharkeyUserProfileApi
import cc.hhhl.client.api.USER_PROFILE_DESCRIPTION_MAX_LENGTH
import cc.hhhl.client.api.USER_PROFILE_NAME_MAX_LENGTH
import cc.hhhl.client.api.UserProfileApi
import cc.hhhl.client.api.UserAvailabilityResult
import cc.hhhl.client.api.UserProfileLoadResult
import cc.hhhl.client.api.UserProfileUpdateDraft
import cc.hhhl.client.api.UserProfileUpdateResult
import cc.hhhl.client.model.User

open class UserProfileRepository(
    private val tokenProvider: () -> String?,
    private val userIdProvider: () -> String?,
    private val api: UserProfileApi = SharkeyUserProfileApi(),
) {
    open suspend fun load(): UserProfileRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserProfileRepositoryResult.Unauthorized
        val userId = userIdProvider()?.takeIf { it.isNotBlank() }
            ?: return UserProfileRepositoryResult.Error("无法读取当前账号")

        return mapLoadResult(api.loadProfile(token, userId))
    }

    open suspend fun resolveMention(username: String): UserProfileRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserProfileRepositoryResult.Unauthorized
        val cleanUsername = username.trim().removePrefix("@").takeIf { it.isNotBlank() }
            ?: return UserProfileRepositoryResult.Error("请输入用户名")

        return mapLoadResult(api.loadProfileByUsername(token, cleanUsername))
    }

    open suspend fun updateProfile(
        name: String,
        description: String,
    ): UserProfileRepositoryResult {
        return updateProfileFields(
            name = name,
            description = description,
        )
    }

    open suspend fun updateBanner(
        name: String,
        description: String,
        bannerId: String,
    ): UserProfileRepositoryResult {
        val cleanBannerId = bannerId.trim()
        if (cleanBannerId.isBlank()) {
            return UserProfileRepositoryResult.Error("请选择横幅图片")
        }
        return updateProfileFields(
            name = name,
            description = description,
            bannerId = cleanBannerId,
        )
    }

    open suspend fun checkUsernameAvailable(username: String): UserProfileAvailabilityRepositoryResult {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return UserProfileAvailabilityRepositoryResult.Error("请输入用户名")
        return mapAvailabilityResult(api.checkUsernameAvailable(cleanUsername))
    }

    open suspend fun checkEmailAddressAvailable(emailAddress: String): UserProfileAvailabilityRepositoryResult {
        val cleanEmailAddress = emailAddress.trim()
        if (cleanEmailAddress.isBlank()) return UserProfileAvailabilityRepositoryResult.Error("请输入邮箱")
        return mapAvailabilityResult(api.checkEmailAddressAvailable(cleanEmailAddress))
    }

    private suspend fun updateProfileFields(
        name: String,
        description: String,
        avatarId: String? = null,
        bannerId: String? = null,
    ): UserProfileRepositoryResult {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return UserProfileRepositoryResult.Unauthorized
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return UserProfileRepositoryResult.Error("请输入名称")
        }
        if (cleanName.length > USER_PROFILE_NAME_MAX_LENGTH) {
            return UserProfileRepositoryResult.Error("名称不能超过 ${USER_PROFILE_NAME_MAX_LENGTH} 字")
        }
        val cleanDescription = description.trim()
        if (cleanDescription.length > USER_PROFILE_DESCRIPTION_MAX_LENGTH) {
            return UserProfileRepositoryResult.Error("简介不能超过 ${USER_PROFILE_DESCRIPTION_MAX_LENGTH} 字")
        }

        return mapUpdateResult(
            api.updateProfile(
                token = token,
                draft = UserProfileUpdateDraft(
                    name = cleanName,
                    description = cleanDescription,
                    avatarId = avatarId?.trim()?.takeIf { it.isNotBlank() },
                    bannerId = bannerId?.trim()?.takeIf { it.isNotBlank() },
                ),
            ),
        )
    }

    private fun mapLoadResult(result: UserProfileLoadResult): UserProfileRepositoryResult {
        return when (result) {
            is UserProfileLoadResult.Success -> UserProfileRepositoryResult.Success(result.user)
            UserProfileLoadResult.Unauthorized -> UserProfileRepositoryResult.Unauthorized
            is UserProfileLoadResult.NetworkError -> {
                UserProfileRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserProfileLoadResult.ServerError -> UserProfileRepositoryResult.Error(result.message)
        }
    }

    private fun mapUpdateResult(result: UserProfileUpdateResult): UserProfileRepositoryResult {
        return when (result) {
            is UserProfileUpdateResult.Success -> UserProfileRepositoryResult.Success(result.user)
            UserProfileUpdateResult.Unauthorized -> UserProfileRepositoryResult.Unauthorized
            is UserProfileUpdateResult.NetworkError -> {
                UserProfileRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserProfileUpdateResult.ServerError -> UserProfileRepositoryResult.Error(result.message)
        }
    }

    private fun mapAvailabilityResult(result: UserAvailabilityResult): UserProfileAvailabilityRepositoryResult {
        return when (result) {
            is UserAvailabilityResult.Success -> UserProfileAvailabilityRepositoryResult.Success(
                available = result.available,
                reason = result.reason,
            )
            is UserAvailabilityResult.NetworkError -> {
                UserProfileAvailabilityRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is UserAvailabilityResult.ServerError -> UserProfileAvailabilityRepositoryResult.Error(result.message)
        }
    }
}

sealed interface UserProfileRepositoryResult {
    data class Success(val user: User) : UserProfileRepositoryResult

    data object Unauthorized : UserProfileRepositoryResult

    data class Error(val message: String) : UserProfileRepositoryResult
}

sealed interface UserProfileAvailabilityRepositoryResult {
    data class Success(
        val available: Boolean,
        val reason: String? = null,
    ) : UserProfileAvailabilityRepositoryResult

    data class Error(val message: String) : UserProfileAvailabilityRepositoryResult
}
