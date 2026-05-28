package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.ThemePresetRegistry

@Composable
fun ThemePicker(
    selectedTheme: HhhlThemePreset,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedTheme.label
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.surface.luminance() < 0.2f
    val pickerBackground = if (isDarkSurface) {
        colors.surface.copy(alpha = 0.70f)
    } else {
        colors.surface.copy(alpha = 0.68f)
    }
    val pickerBorder = colors.focusRing.copy(alpha = if (isDarkSurface) 0.42f else 0.22f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(pickerBackground)
                .border(
                    width = 1.dp,
                    color = pickerBorder,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = selectedLabel,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "更换",
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        HhhlDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ThemePresetRegistry.presets.groupBy { it.groupLabel }.forEach { (groupLabel, presets) ->
                Text(
                    text = groupLabel,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                presets.forEach { preset ->
                    HhhlDropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (selectedTheme == preset) {
                                        colors.accent
                                    } else {
                                        colors.textPrimary
                                    },
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onThemeSelected(preset)
                        },
                    )
                }
            }
        }
    }
}
