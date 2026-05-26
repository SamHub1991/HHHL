package cc.hhhl.client.ui.screen

import cc.hhhl.client.state.DiscoverSearchMode
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverPresentationTest {
    @Test
    fun quickActionsKeepAllDiscoverEntrypointsReachable() {
        assertEquals(
            listOf(
                "频道",
                "页面",
                "图库",
                "Play",
                "公告",
            ),
            discoverQuickActionLabels(),
        )
    }

    @Test
    fun primaryQuickActionsKeepOnlyMainDiscoverEntryVisible() {
        assertEquals(
            listOf("频道"),
            discoverPrimaryQuickActionLabels(),
        )
    }

    @Test
    fun visibleModesFollowInstanceCapabilities() {
        assertEquals(
            listOf(DiscoverSearchMode.Notes, DiscoverSearchMode.Users),
            discoverVisibleModes(
                canSearchNotes = true,
                canTrend = false,
                canViewFederation = false,
            ),
        )
        assertEquals(
            listOf(DiscoverSearchMode.Users, DiscoverSearchMode.Trends, DiscoverSearchMode.Federation),
            discoverVisibleModes(
                canSearchNotes = false,
                canTrend = true,
                canViewFederation = true,
            ),
        )
    }

    @Test
    fun searchModesKeepPrimaryCompactAndOverflowReachable() {
        val visibleModes = listOf(
            DiscoverSearchMode.Notes,
            DiscoverSearchMode.Users,
            DiscoverSearchMode.Trends,
            DiscoverSearchMode.Federation,
        )
        val primary = discoverPrimarySearchModes(
            visibleModes = visibleModes,
            selectedMode = DiscoverSearchMode.Federation,
        )

        assertEquals(
            listOf(DiscoverSearchMode.Notes, DiscoverSearchMode.Users, DiscoverSearchMode.Federation),
            primary,
        )
        assertEquals(
            listOf(DiscoverSearchMode.Trends),
            discoverOverflowSearchModes(visibleModes, primary),
        )
    }

    @Test
    fun searchCopyMatchesModeAndState() {
        assertEquals("搜索帖子、话题、关键词", discoverSearchPlaceholder(DiscoverSearchMode.Notes))
        assertEquals("搜索用户、@用户名", discoverSearchPlaceholder(DiscoverSearchMode.Users))
        assertEquals("搜索", discoverSearchActionLabel(isSearching = false))
        assertEquals("搜索中", discoverSearchActionLabel(isSearching = true))
    }

    @Test
    fun filterPresentationStaysCompact() {
        assertEquals(listOf("全部来源", "本地", "远程"), discoverFilterOriginLabels())
        assertEquals("高级筛选", discoverAdvancedFilterTriggerLabel(DiscoverAdvancedFilters()))
        assertEquals(
            "筛选 3",
            discoverAdvancedFilterTriggerLabel(
                DiscoverAdvancedFilters(
                    username = "alice",
                    domain = "example.social",
                    sinceDate = "2026-05-01",
                ),
            ),
        )
        assertEquals(
            "远程 / @alice / example.social / 自 2026-05-01 / 至 2026-05-26",
            discoverFilterSummary(
                filters = DiscoverAdvancedFilters(
                    origin = DiscoverSearchOrigin.Remote,
                    username = "@alice",
                    domain = "example.social",
                    sinceDate = "2026-05-01",
                    untilDate = "2026-05-26",
                ),
                selectedMode = DiscoverSearchMode.Users,
            ),
        )
        assertEquals(
            listOf("远程", "@alice", "example.social", "自 2026-05-01", "至 2026-05-26"),
            discoverActiveFilterLabels(
                filters = DiscoverAdvancedFilters(
                    origin = DiscoverSearchOrigin.Remote,
                    username = "@alice",
                    domain = "example.social",
                    sinceDate = "2026-05-01",
                    untilDate = "2026-05-26",
                ),
                selectedMode = DiscoverSearchMode.Users,
            ),
        )
    }

    @Test
    fun endReachedCopyMatchesMode() {
        assertEquals(
            "已显示全部 0 个趋势",
            discoverEndReachedText(
                cc.hhhl.client.state.DiscoverUiState(
                    selectedMode = DiscoverSearchMode.Trends,
                    endReached = true,
                ),
            ),
        )
    }
}
