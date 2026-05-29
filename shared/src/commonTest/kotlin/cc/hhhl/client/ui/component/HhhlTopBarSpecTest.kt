package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HhhlTopBarSpecTest {
    @Test
    fun topBarUsesPolishedCompactHeight() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(44, metrics.containerHeight)
        assertEquals(10, metrics.horizontalPadding)
        assertEquals(5, metrics.verticalPadding)
        assertEquals(18, metrics.panelCornerRadius)
        assertEquals(3, metrics.panelElevation)
        assertTrue(metrics.containerHeight <= 48)
    }

    @Test
    fun topBarKeepsActionTargetsReadableWhileCompact() {
        val metrics = hhhlTopBarMetrics()

        assertEquals(32, metrics.slotMinSize)
        assertEquals(32, metrics.backButtonSize)
        assertTrue(metrics.backButtonSize < metrics.containerHeight)
        assertTrue(metrics.backIconSize <= 18)
        assertEquals(13.dp, HhhlIconActionCornerRadius)
        assertEquals(0.dp, HhhlIconActionIdleElevation)
    }
}
