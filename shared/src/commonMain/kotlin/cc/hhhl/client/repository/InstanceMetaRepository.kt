package cc.hhhl.client.repository

import cc.hhhl.client.api.InstanceMetaApi
import cc.hhhl.client.api.InstanceAuxiliaryLoadResult
import cc.hhhl.client.api.InstanceMetaLoadResult
import cc.hhhl.client.api.SharkeyInstanceMetaApi
import cc.hhhl.client.model.InstanceMeta

open class InstanceMetaRepository(
    private val api: InstanceMetaApi = SharkeyInstanceMetaApi(),
) {
    open suspend fun load(): InstanceMetaRepositoryResult {
        return when (val result = api.loadMeta()) {
            is InstanceMetaLoadResult.Success -> InstanceMetaRepositoryResult.Success(
                result.meta.copy(
                    stats = (api.loadStats() as? InstanceAuxiliaryLoadResult.Success)?.value,
                    onlineUsers = (api.loadOnlineUsers() as? InstanceAuxiliaryLoadResult.Success)?.value,
                    serverInfo = (api.loadServerInfo() as? InstanceAuxiliaryLoadResult.Success)?.value,
                ),
            )
            is InstanceMetaLoadResult.NetworkError -> {
                InstanceMetaRepositoryResult.Error("无法连接服务器：${result.message}")
            }
            is InstanceMetaLoadResult.ServerError -> InstanceMetaRepositoryResult.Error(result.message)
        }
    }
}

sealed interface InstanceMetaRepositoryResult {
    data class Success(val meta: InstanceMeta) : InstanceMetaRepositoryResult

    data class Error(val message: String) : InstanceMetaRepositoryResult
}
