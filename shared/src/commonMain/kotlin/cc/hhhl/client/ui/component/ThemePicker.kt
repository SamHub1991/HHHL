package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.2f
    val pickerBackground = if (isDarkSurface) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
    }
    val pickerBorder = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkSurface) 0.14f else 0.08f)

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
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "更换",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = HhhlDropdownMenuMaxHeight),
        ) {
            ThemePresetRegistry.presets.groupBy { it.groupLabel }.forEach { (groupLabel, presets) ->
                Text(
                    text = groupLabel,
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (selectedTheme == preset) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
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
