package cc.hhhl.client.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun AutoLoadMoreEffect(
    listState: LazyListState,
    itemCount: Int,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    threshold: Int = 4,
) {
    LaunchedEffect(listState, itemCount, isLoadingMore, threshold) {
        if (itemCount <= 0 || isLoadingMore) return@LaunchedEffect

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            visibleItems.lastOrNull()?.index ?: 0
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= itemCount - threshold) {
                    onLoadMore()
                }
            }
    }
}
