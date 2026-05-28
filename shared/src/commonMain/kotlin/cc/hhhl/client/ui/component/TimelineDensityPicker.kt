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
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.theme.LocalHhhlColors

@Composable
fun TimelineDensityPicker(
    selectedDensity: TimelineDensity,
    onDensitySelected: (TimelineDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
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
            Text(
                text = selectedDensity.label,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
            TimelineDensity.entries.forEach { density ->
                HhhlDropdownMenuItem(
                    text = {
                        Text(
                            text = density.label,
                            color = if (selectedDensity == density) {
                                colors.accent
                            } else {
                                colors.textPrimary
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onDensitySelected(density)
                    },
                )
            }
        }
    }
}
