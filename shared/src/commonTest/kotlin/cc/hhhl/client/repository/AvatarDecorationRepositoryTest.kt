package cc.hhhl.client.repository

import cc.hhhl.client.api.AvatarDecorationApi
import cc.hhhl.client.api.AvatarDecorationLoadResult
import cc.hhhl.client.model.AvatarDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AvatarDecorationRepositoryTest {
    @Test
    fun loadRequiresToken() = runTest {
        val repository = AvatarDecorationRepository(
            tokenProvider = { null },
            api = fakeApi(),
        )

        assertEquals(AvatarDecorationRepositoryResult.Unauthorized, repository.load())
    }

    @Test
    fun loadMapsSuccess() = runTest {
        val decorations = listOf(sampleDecoration())
        val repository = AvatarDecorationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(AvatarDecorationLoadResult.Success(decorations)),
        )

        assertEquals(
            AvatarDecorationRepositoryResult.Success(decorations),
            repository.load(),
        )
    }

    @Test
    fun loadMapsNetworkError() = runTest {
        val repository = AvatarDecorationRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(AvatarDecorationLoadResult.NetworkError("timeout")),
        )

        assertEquals(
            AvatarDecorationRepositoryResult.Error("无法连接服务器：timeout"),
            repository.load(),
        )
    }

    private fun fakeApi(
        result: AvatarDecorationLoadResult = AvatarDecorationLoadResult.Success(emptyList()),
    ): AvatarDecorationApi {
        return object : AvatarDecorationApi {
            override suspend fun load(token: String): AvatarDecorationLoadResult = result
        }
    }

    private fun sampleDecoration(): AvatarDecoration {
        return AvatarDecoration(
            id = "decoration-1",
            url = "https://dc.hhhl.cc/decorations/1.png",
            angle = 12f,
            flipH = true,
            offsetX = 0.1f,
            offsetY = -0.2f,
        )
    }
}
