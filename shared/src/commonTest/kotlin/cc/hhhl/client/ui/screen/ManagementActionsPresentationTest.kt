package cc.hhhl.client.ui.screen

import cc.hhhl.client.api.DriveFileSort
import cc.hhhl.client.model.DriveFileTypeFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagementActionsPresentationTest {
    @Test
    fun collectionSummaryActionsKeepRefreshesInOverflow() {
        assertEquals(
            listOf("刷新列表", "刷新动态"),
            userListSummaryActions(
                hasSelectedList = true,
                isLoadingLists = false,
                isLoadingTimeline = false,
                onRefreshLists = {},
                onRefreshTimeline = {},
            ).map { it.label },
        )
        assertEquals(
            listOf("刷新剪辑", "刷新动态"),
            clipSummaryActions(
                hasSelectedClip = true,
                isLoadingClips = false,
                isLoadingNotes = false,
                onRefreshClips = {},
                onRefreshNotes = {},
            ).map { it.label },
        )
        assertEquals(
            listOf("刷新天线", "刷新动态"),
            antennaSummaryActions(
                hasSelectedAntenna = true,
                isLoadingAntennas = false,
                isLoadingNotes = false,
                onRefreshAntennas = {},
                onRefreshNotes = {},
            ).map { it.label },
        )
    }

    @Test
    fun collectionTimelineRefreshRequiresSelection() {
        val userListActions = userListSummaryActions(
            hasSelectedList = false,
            isLoadingLists = false,
            isLoadingTimeline = false,
            onRefreshLists = {},
            onRefreshTimeline = {},
        )
        val clipActions = clipSummaryActions(
            hasSelectedClip = false,
            isLoadingClips = false,
            isLoadingNotes = false,
            onRefreshClips = {},
            onRefreshNotes = {},
        )
        val antennaActions = antennaSummaryActions(
            hasSelectedAntenna = false,
            isLoadingAntennas = false,
            isLoadingNotes = false,
            onRefreshAntennas = {},
            onRefreshNotes = {},
        )

        assertEquals(listOf(true, false), userListActions.map { it.enabled })
        assertEquals(listOf(true, false), clipActions.map { it.enabled })
        assertEquals(listOf(true, false), antennaActions.map { it.enabled })
    }

    @Test
    fun driveSummaryKeepsSecondaryActionsInOverflow() {
        val rootActions = driveSummaryActions(
            inFolder = false,
            isStreamMode = false,
            isLoading = false,
            actionsEnabled = true,
            onRefresh = {},
            onNavigateUp = {},
            onStreamModeChanged = {},
        )
        val folderActions = driveSummaryActions(
            inFolder = true,
            isStreamMode = false,
            isLoading = false,
            actionsEnabled = true,
            onRefresh = {},
            onNavigateUp = {},
            onStreamModeChanged = {},
        )

        assertEquals(listOf("刷新", "最近文件流"), rootActions.map { it.label })
        assertEquals(listOf("刷新", "最近文件流", "返回上级"), folderActions.map { it.label })
    }

    @Test
    fun followRequestRejectIsDestructiveOverflowAction() {
        val actions = followRequestActions(
            isPending = false,
            userId = "user-1",
            onReject = {},
        )

        assertEquals(listOf("拒绝"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun userListManagementKeepsDeleteInDestructiveOverflowAction() {
        val actions = userListHeaderActions(
            isMutating = false,
            onDelete = {},
        )

        assertEquals(listOf("删除"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun userListMemberRemovalUsesDestructiveOverflowAction() {
        val actions = userListMemberActions(
            onRemoveMember = {},
        )

        assertEquals(listOf("移除成员"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun antennaManagementKeepsDeleteInDestructiveOverflowAction() {
        val actions = antennaHeaderActions(
            isMutating = false,
            onDelete = {},
        )

        assertEquals(listOf("删除"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun announcementManagementKeepsDeleteInDestructiveOverflowAction() {
        val actions = announcementManagementActions(
            isMutating = false,
            onDelete = {},
        )

        assertEquals(listOf("删除"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun clipManagementKeepsDeleteInDestructiveOverflowAction() {
        val actions = clipHeaderActions(
            isManagingClip = false,
            onDeleteClip = {},
        )

        assertEquals(listOf("删除"), actions.map { it.label })
        assertEquals(listOf(true), actions.map { it.destructive })
    }

    @Test
    fun driveFolderActionsKeepDeleteInDestructiveOverflowAction() {
        val actions = driveFolderRowActions(
            actionsEnabled = true,
            confirmingDelete = false,
            onStartEdit = {},
            onDelete = {},
        )

        assertEquals(listOf("改名", "删除"), actions.map { it.label })
        assertEquals(listOf(false, true), actions.map { it.destructive })
    }

    @Test
    fun driveFileActionsKeepDeleteInDestructiveOverflowAction() {
        val actions = driveFileRowActions(
            isSensitive = false,
            actionsEnabled = true,
            confirmingDelete = true,
            onDetails = {},
            onStartEdit = {},
            onToggleSensitive = {},
            onDelete = {},
        )

        assertEquals(listOf("详情", "改名", "敏感", "确认删除"), actions.map { it.label })
        assertEquals(listOf(false, false, false, true), actions.map { it.destructive })
    }

    @Test
    fun driveFileActionsExposeMoveToRootWhenFileIsInFolder() {
        val actions = driveFileRowActions(
            isSensitive = false,
            canMoveToRoot = true,
            actionsEnabled = true,
            confirmingDelete = false,
            onDetails = {},
            onStartEdit = {},
            onToggleSensitive = {},
            onMoveToRoot = {},
            onDelete = {},
        )

        assertEquals(listOf("详情", "改名", "敏感", "移到根目录", "删除"), actions.map { it.label })
        assertEquals(listOf(false, false, false, false, true), actions.map { it.destructive })
    }

    @Test
    fun driveTypeFilterOptionsExposeCompactFileCategories() {
        assertEquals(
            listOf("全部", "图片", "视频", "音频", "文档", "其他"),
            driveTypeFilterOptions().map { it.label },
        )
    }

    @Test
    fun driveControlActionsCombineSortAndTypeFiltersInOverflow() {
        val actions = driveControlActions(
            selectedSort = DriveFileSort.CreatedDescending,
            selectedTypeFilter = DriveFileTypeFilter.All,
            onSortSelected = {},
            onTypeFilterSelected = {},
        )

        assertEquals(
            listOf(
                "排序：最新",
                "排序改为 最早",
                "排序改为 名称 A-Z",
                "排序改为 名称 Z-A",
                "排序改为 大小 ↓",
                "排序改为 大小 ↑",
                "类型：全部",
                "只看图片",
                "只看视频",
                "只看音频",
                "只看文档",
                "只看其他",
            ),
            actions.map { it.label },
        )
        assertEquals(false, actions.first().enabled)
        assertEquals(false, actions[6].enabled)
    }
}
