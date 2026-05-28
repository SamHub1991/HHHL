package cc.hhhl.client.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color? = null,
    iconContentColor: Color? = null,
    titleContentColor: Color? = null,
    textContentColor: Color? = null,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = LocalHhhlColors.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor ?: colors.surfaceElevated.copy(alpha = 0.98f),
        iconContentColor = iconContentColor ?: colors.textSecondary,
        titleContentColor = titleContentColor ?: colors.textPrimary,
        textContentColor = textContentColor ?: colors.textSecondary,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
