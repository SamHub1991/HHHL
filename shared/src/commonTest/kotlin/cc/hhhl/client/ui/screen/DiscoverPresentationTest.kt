package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.FederationInstance
import cc.hhhl.client.model.TrendingHashtag
import cc.hhhl.client.state.DiscoverSearchMode
import cc.hhhl.client.state.DiscoverAdvancedFilters
import cc.hhhl.client.state.DiscoverSearchOperator
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
    fun primaryQuickActionsKeepCommonDiscoverEntrypointsVisible() {
        assertEquals(
            listOf("频道", "页面", "图库", "Play"),
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
        assertEquals("按实例域名筛选", discoverFederationFilterPlaceholder())
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
            "任一词 / @alice / 用户 user-1 / example.social / 频道 channel-1 / 自 2026-05-01 / 至 2026-05-26 / 排除 draft / 带附件 / 不含回复",
            discoverFilterSummary(
                filters = DiscoverAdvancedFilters(
                    operator = DiscoverSearchOperator.AnyWord,
                    username = "@alice",
                    userId = "user-1",
                    domain = "example.social",
                    channelId = "channel-1",
                    sinceDate = "2026-05-01",
                    untilDate = "2026-05-26",
                    excludeWords = "draft",
                    withFiles = true,
                    includeReplies = false,
                ),
                selectedMode = DiscoverSearchMode.Notes,
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

    @Test
    fun trendChartSummaryDoesNotExposeRawBuckets() {
        assertEquals(
            "最近 +3",
            trendChartSummary(TrendingHashtag(tag = "签到", usersCount = 3, chart = listOf(0, 0, 3))),
        )
        assertEquals(
            "热度 4",
            trendChartSummary(TrendingHashtag(tag = "签到", usersCount = 3, chart = listOf(1, 3, 0))),
        )
        assertEquals(
            "活跃中",
            trendChartSummary(TrendingHashtag(tag = "签到", usersCount = 3, chart = emptyList())),
        )
    }

    @Test
    fun federationDetailMetaIncludesLocalizedTimestampsWhenAvailable() {
        val instance = FederationInstance(
            id = "instance-1",
            host = "example.social",
            name = "Example",
            softwareName = "sharkey",
            softwareVersion = "2026.5",
            usersCount = 1,
            notesCount = 2,
            followingCount = 3,
            followersCount = 4,
            isNotResponding = false,
            isSuspended = false,
            isBlocked = false,
            isSilenced = false,
            maintainerName = "Admin",
            infoUpdatedAtLabel = "2026-05-25 08:00",
            latestRequestReceivedAtLabel = "2026-05-25 09:00",
        )

        assertEquals(
            "sharkey 2026.5 · 维护者 Admin · 信息更新 2026-05-25 08:00 · 最近请求 2026-05-25 09:00",
            federationDetailMeta(instance),
        )
        assertEquals(
            listOf(
                FederationDetailField("域名", "example.social"),
                FederationDetailField("软件", "sharkey 2026.5"),
                FederationDetailField("维护者", "Admin"),
                FederationDetailField("状态", "联邦中"),
            ),
            federationDetailInfoRows(instance),
        )
        assertEquals(
            listOf(
                FederationDetailField("信息更新", "2026-05-25 08:00"),
                FederationDetailField("最近请求", "2026-05-25 09:00"),
            ),
            federationDetailTimeRows(instance),
        )
    }

    @Test
    fun federationDetailRowsFormatStatusAndCounts() {
        val instance = FederationInstance(
            id = "instance-1",
            host = "example.social",
            name = "Example",
            softwareName = "sharkey",
            softwareVersion = "2026.5",
            usersCount = 1200,
            notesCount = 987654,
            followingCount = 30,
            followersCount = 4000,
            isNotResponding = false,
            isSuspended = false,
            isBlocked = false,
            isSilenced = true,
        )

        assertEquals("已静音", federationInstanceStatusLabel(instance))
        assertEquals(
            "1,200 用户 · 987,654 帖子 · 30 关注 · 4,000 粉丝",
            federationDetailMetrics(instance),
        )
        assertEquals(
            listOf(FederationDetailField("时间", "暂无本地时间信息")),
            federationDetailTimeRows(instance),
        )
    }
}
