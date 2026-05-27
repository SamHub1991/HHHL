package cc.hhhl.client.repository

import cc.hhhl.client.api.MainStreamingApi
import cc.hhhl.client.api.MainStreamingEvent
import cc.hhhl.client.api.SharkeyMainStreamingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class MainStreamingRepository(
    private val tokenProvider: () -> String?,
    private val api: MainStreamingApi = SharkeyMainStreamingApi(),
) {
    open fun streamMain(): Flow<MainStreamingEvent> {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return flowOf(
                MainStreamingEvent.Unauthorized,
                MainStreamingEvent.Closed,
            )
        return api.streamMain(token)
    }
}
