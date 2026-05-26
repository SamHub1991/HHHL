package cc.hhhl.client.state

import cc.hhhl.client.model.InstanceCapabilities
import cc.hhhl.client.model.InstanceMeta
import cc.hhhl.client.repository.InstanceMetaRepository
import cc.hhhl.client.repository.InstanceMetaRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceMetaStateHolderTest {
    @Test
    fun loadStoresInstanceMeta() = runTest {
        val meta = sampleMeta()
        val holder = InstanceMetaStateHolder(
            repository = fakeRepository(InstanceMetaRepositoryResult.Success(meta)),
            scope = TestScope(testScheduler),
        )

        holder.load()
        assertTrue(holder.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals(meta, holder.state.value.meta)
        assertEquals(null, holder.state.value.errorMessage)
    }

    @Test
    fun loadStoresError() = runTest {
        val holder = InstanceMetaStateHolder(
            repository = fakeRepository(InstanceMetaRepositoryResult.Error("meta unavailable")),
            scope = TestScope(testScheduler),
        )

        holder.load()
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoading)
        assertEquals("meta unavailable", holder.state.value.errorMessage)
    }

    private fun fakeRepository(result: InstanceMetaRepositoryResult): InstanceMetaRepository {
        return object : InstanceMetaRepository(
            api = object : cc.hhhl.client.api.InstanceMetaApi {
                override suspend fun loadMeta(): cc.hhhl.client.api.InstanceMetaLoadResult {
                    return cc.hhhl.client.api.InstanceMetaLoadResult.Success(sampleMeta())
                }
            },
        ) {
            override suspend fun load(): InstanceMetaRepositoryResult = result
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
            capabilities = InstanceCapabilities(canSearchNotes = false),
        )
    }
}

