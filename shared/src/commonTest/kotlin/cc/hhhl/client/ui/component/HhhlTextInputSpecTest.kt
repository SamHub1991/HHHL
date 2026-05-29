package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HhhlTextInputSpecTest {
    @Test
    fun textInputUsesSoftModernMetrics() {
        assertEquals(42.dp, HhhlTextInputSingleLineMinHeight)
        assertEquals(80.dp, HhhlTextInputMultiLineMinHeight)
        assertEquals(13.dp, HhhlTextInputCornerRadius)
        assertEquals(12.dp, HhhlTextInputHorizontalPadding)
        assertEquals(8.dp, HhhlTextInputVerticalPadding)
    }
}
