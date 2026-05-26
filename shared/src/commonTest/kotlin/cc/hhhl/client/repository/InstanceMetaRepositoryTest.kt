package cc.hhhl.client.repository

import cc.hhhl.client.api.InstanceMetaApi
import cc.hhhl.client.api.InstanceMetaLoadResult
import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.InstanceMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class InstanceMetaRepositoryTest {
    @Test
    fun loadReturnsMetaFromApi() = runTest {
        val meta = sampleMeta()
        val repository = InstanceMetaRepository(
            api = fakeApi(InstanceMetaLoadResult.Success(meta)),
        )

        val result = repository.load()

        assertEquals(InstanceMetaRepositoryResult.Success(meta), result)
    }

    @Test
    fun loadMapsNetworkError() = runTest {
        val repository = InstanceMetaRepository(
            api = fakeApi(InstanceMetaLoadResult.NetworkError("timeout")),
        )

        val result = repository.load()

        assertEquals(InstanceMetaRepositoryResult.Error("无法连接服务器：timeout"), result)
    }

    @Test
    fun loadMapsServerError() = runTest {
        val repository = InstanceMetaRepository(
            api = fakeApi(InstanceMetaLoadResult.ServerError(500, "meta unavailable")),
        )

        val result = repository.load()

        assertEquals(InstanceMetaRepositoryResult.Error("meta unavailable"), result)
    }

    private fun fakeApi(result: InstanceMetaLoadResult): InstanceMetaApi {
        return object : InstanceMetaApi {
            override suspend fun loadMeta(): InstanceMetaLoadResult = result
        }
    }

    private fun sampleMeta(): InstanceMeta {
        return InstanceMeta(
            name = "hhhl",
            description = "期待AGI时代来临",
            version = "2025.5.2-dev",
            iconUrl = "/client-assets/icon.png",
            themeColor = "#86b300",
            maxNoteTextLength = 3000,
            maxCwLength = 500,
            defaultLike = "❤️",
            capabilities = InstanceCapabilities(
                canSearchNotes = false,
                localTimelineAvailable = true,
                globalTimelineAvailable = true,
                bubbleTimelineAvailable = false,
                chatAvailable = true,
            ),
        )
    }
}

