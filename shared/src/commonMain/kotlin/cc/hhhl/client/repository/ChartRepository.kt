package cc.hhhl.client.repository

import cc.hhhl.client.api.ChartApi
import cc.hhhl.client.api.ChartLoadResult
import cc.hhhl.client.api.SharkeyChartApi
import cc.hhhl.client.model.ChartPayload
import cc.hhhl.client.model.ChartSpan
import cc.hhhl.client.model.InstanceChartKind
import cc.hhhl.client.model.UserChartKind

open class ChartRepository(
    private val api: ChartApi = SharkeyChartApi(),
) {
    open suspend fun loadInstanceChart(
        kind: InstanceChartKind,
        span: ChartSpan = ChartSpan.Day,
        limit: Int = DEFAULT_LIMIT,
        offset: Int? = null,
        host: String? = null,
    ): ChartRepositoryResult {
        return mapResult(api.loadInstanceChart(kind, span, limit, offset, host))
    }

    open suspend fun loadUserChart(
        kind: UserChartKind,
        userId: String,
        span: ChartSpan = ChartSpan.Day,
        limit: Int = DEFAULT_LIMIT,
        offset: Int? = null,
    ): ChartRepositoryResult {
        return mapResult(api.loadUserChart(kind, userId, span, limit, offset))
    }

    private fun mapResult(result: ChartLoadResult): ChartRepositoryResult {
        return when (result) {
            is ChartLoadResult.Success -> ChartRepositoryResult.Success(result.chart)
            is ChartLoadResult.NetworkError -> ChartRepositoryResult.Error("无法连接服务器：${result.message}")
            is ChartLoadResult.ServerError -> ChartRepositoryResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 30
    }
}

sealed interface ChartRepositoryResult {
    data class Success(val chart: ChartPayload) : ChartRepositoryResult
    data class Error(val message: String) : ChartRepositoryResult
}
