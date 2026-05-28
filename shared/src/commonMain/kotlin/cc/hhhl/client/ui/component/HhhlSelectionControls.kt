package cc.hhhl.client.ui.component

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun HhhlCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val checkedContainer = colors.buttonSelectedBackground.copy(alpha = if (isDarkSurface) 0.62f else 0.46f)
    val checkedContent = hhhlReadableOnControlColor(checkedContainer, colors.accent)
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = checkedContainer,
            uncheckedColor = colors.border.copy(alpha = if (isDarkSurface) 0.72f else 0.82f),
            checkmarkColor = checkedContent,
            disabledCheckedColor = checkedContainer.copy(alpha = 0.32f),
            disabledUncheckedColor = colors.border.copy(alpha = 0.42f),
            disabledIndeterminateColor = colors.border.copy(alpha = 0.42f),
        ),
    )
}

@Composable
fun HhhlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val checkedTrack = colors.buttonSelectedBackground.copy(alpha = if (isDarkSurface) 0.58f else 0.44f)
    val checkedThumb = hhhlReadableOnControlColor(checkedTrack, colors.accent)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = checkedThumb,
            checkedTrackColor = checkedTrack,
            checkedBorderColor = colors.focusRing.copy(alpha = if (isDarkSurface) 0.36f else 0.18f),
            uncheckedThumbColor = if (isDarkSurface) colors.textMuted.copy(alpha = 0.82f) else colors.surfaceElevated,
            uncheckedTrackColor = colors.inputBackground.copy(alpha = if (isDarkSurface) 0.52f else 0.66f),
            uncheckedBorderColor = colors.border.copy(alpha = if (isDarkSurface) 0.46f else 0.58f),
            disabledCheckedThumbColor = colors.textMuted.copy(alpha = 0.58f),
            disabledCheckedTrackColor = checkedTrack.copy(alpha = 0.32f),
            disabledCheckedBorderColor = colors.border.copy(alpha = 0.34f),
            disabledUncheckedThumbColor = colors.textMuted.copy(alpha = 0.42f),
            disabledUncheckedTrackColor = colors.inputBackground.copy(alpha = 0.42f),
            disabledUncheckedBorderColor = colors.border.copy(alpha = 0.28f),
        ),
    )
}

@Composable
fun HhhlProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    CircularProgressIndicator(
        modifier = modifier,
        color = colors.accent,
        trackColor = colors.border.copy(alpha = if (isDarkSurface) 0.24f else 0.16f),
        strokeWidth = strokeWidth,
    )
}

@Composable
fun HhhlDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHhhlColors.current
    val contentColor = if (destructive) colors.danger else colors.textPrimary
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = if (destructive) colors.danger else colors.textSecondary,
            trailingIconColor = if (destructive) colors.danger else colors.textSecondary,
            disabledTextColor = colors.textMuted.copy(alpha = 0.46f),
            disabledLeadingIconColor = colors.textMuted.copy(alpha = 0.36f),
            disabledTrailingIconColor = colors.textMuted.copy(alpha = 0.36f),
        ),
    )
}
