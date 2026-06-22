package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.ChannelCategory
import cc.hhhl.client.model.ChannelListKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelPresentationTest {
    @Test
    fun primaryKindsKeepCommonChannelViewsVisible() {
        assertEquals(
            listOf(
                ChannelListKind.Featured,
                ChannelListKind.Followed,
            ),
            channelPrimaryKinds(),
        )
    }

    @Test
    fun overflowKindsKeepSecondaryChannelViewsReachable() {
        assertEquals(
            listOf(
                ChannelListKind.Favorites,
                ChannelListKind.Owned,
            ),
            channelOverflowKinds(),
        )
    }

    @Test
    fun managementActionsKeepArchiveInDestructiveOverflowAction() {
        val actions = channelManagementActions(
            isMutatingChannel = false,
            isArchived = false,
            onEditChannel = {},
            onArchiveChannel = {},
        )

        assertEquals(listOf("编辑", "归档"), actions.map { it.label })
        assertEquals(listOf(false, true), actions.map { it.destructive })
    }

    @Test
    fun categoryLabelUsesUncategorizedFallbackAndCountSuffix() {
        assertEquals(
            "AI与大模型 13",
            channelCategoryLabel(ChannelCategory(name = "AI与大模型", channelsCount = 13)),
        )
        assertEquals(
            "未分类",
            channelCategoryLabel(ChannelCategory(name = "", channelsCount = 0, uncategorized = true)),
        )
    }
}
