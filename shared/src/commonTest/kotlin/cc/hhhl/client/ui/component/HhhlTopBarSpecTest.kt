package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HhhlTopBarSpecTest {
    @Test
    fun topBarUsesPolishedCompactHeight() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(56, metrics.containerHeight)
        assertEquals(16, metrics.horizontalPadding)
        assertEquals(9, metrics.verticalPadding)
        assertEquals(23, metrics.panelCornerRadius)
        assertEquals(6, metrics.panelElevation)
        assertTrue(metrics.containerHeight <= 56)
    }

    @Test
    fun topBarKeepsActionTargetsReadableWhileCompact() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(42, metrics.slotMinSize)
        assertEquals(42, metrics.backButtonSize)
        assertTrue(metrics.backButtonSize < metrics.containerHeight)
        assertTrue(metrics.backIconSize <= 19)
        assertEquals(999.dp, HhhlIconActionCornerRadius)
        assertEquals(0.dp, HhhlIconActionIdleElevation)
    }
}
