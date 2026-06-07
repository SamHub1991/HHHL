package cc.hhhl.client.ui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HhhlTextInputSpecTest {
    @Test
    fun textInputUsesSoftModernMetrics() {
        assertEquals(52.dp, HhhlTextInputSingleLineMinHeight)
        assertEquals(112.dp, HhhlTextInputMultiLineMinHeight)
        assertEquals(17.dp, HhhlTextInputCornerRadius)
        assertEquals(17.dp, HhhlTextInputHorizontalPadding)
        assertEquals(13.dp, HhhlTextInputVerticalPadding)
    }

    @Test
    fun focusedInputSyncsExternalClearAfterSend() {
        assertEquals(
            true,
            shouldSyncHhhlTextInputExternalValue(
                focused = true,
                externalValue = "",
                latestLocalText = "hello",
                lastExternalValue = "hello",
            ),
        )
    }

    @Test
    fun focusedInputKeepsPendingLocalEditWhenExternalValueIsStale() {
        assertEquals(
            false,
            shouldSyncHhhlTextInputExternalValue(
                focused = true,
                externalValue = "he",
                latestLocalText = "hello",
                lastExternalValue = "he",
            ),
        )
    }
}
