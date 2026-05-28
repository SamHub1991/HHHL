package cc.hhhl.client.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    var lastTriggeredItemCount by remember(listState) { mutableIntStateOf(-1) }

    if (itemCount < lastTriggeredItemCount) {
        lastTriggeredItemCount = -1
    }

    LaunchedEffect(listState, itemCount, isLoadingMore, threshold) {
        if (itemCount <= 0 || isLoadingMore) return@LaunchedEffect

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0
            lastVisibleIndex >= itemCount - threshold
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore && lastTriggeredItemCount != itemCount) {
                    lastTriggeredItemCount = itemCount
                    currentOnLoadMore()
                }
            }
    }
}
