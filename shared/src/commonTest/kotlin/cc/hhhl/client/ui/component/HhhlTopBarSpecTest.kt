package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HhhlTopBarSpecTest {
    @Test
    fun topBarUsesCompactModernHeight() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(44, metrics.containerHeight)
        assertTrue(metrics.containerHeight <= 44)
    }

    @Test
    fun topBarKeepsActionTargetsReadableWhileCompact() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(34, metrics.slotMinSize)
        assertEquals(30, metrics.backButtonSize)
        assertTrue(metrics.backButtonSize < metrics.containerHeight)
        assertTrue(metrics.backIconSize <= 18)
    }
}
