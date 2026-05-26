package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HhhlTextInputSpecTest {
    @Test
    fun textInputUsesSoftModernMetrics() {
        assertEquals(44.dp, HhhlTextInputSingleLineMinHeight)
        assertEquals(88.dp, HhhlTextInputMultiLineMinHeight)
        assertEquals(10.dp, HhhlTextInputCornerRadius)
        assertEquals(12.dp, HhhlTextInputHorizontalPadding)
        assertEquals(9.dp, HhhlTextInputVerticalPadding)
    }
}
