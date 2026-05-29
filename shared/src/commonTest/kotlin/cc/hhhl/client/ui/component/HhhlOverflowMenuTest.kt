package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertNotNull

class HhhlOverflowMenuTest {
    @Test
    fun defaultOverflowMenuActionsExposeIconsForCommonChatActions() {
        assertNotNull(HhhlOverflowMenuAction("回复", onClick = {}).icon)
        assertNotNull(HhhlOverflowMenuAction("引用", onClick = {}).icon)
        assertNotNull(HhhlOverflowMenuAction("复制", onClick = {}).icon)
        assertNotNull(HhhlOverflowMenuAction("举报", destructive = true, onClick = {}).icon)
    }
}
