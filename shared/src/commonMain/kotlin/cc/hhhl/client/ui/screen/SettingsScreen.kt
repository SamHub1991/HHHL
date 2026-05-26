package cc.hhhl.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.display.DefaultNoteVisibility
import cc.hhhl.client.display.NotificationBadgeMode
import cc.hhhl.client.display.TimelineDensity
import cc.hhhl.client.state.SettingsGroup
import cc.hhhl.client.state.SettingsItem
import cc.hhhl.client.state.SettingsItemKey
import cc.hhhl.client.state.SettingsUiState
import cc.hhhl.client.theme.HhhlThemePreset
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.ui.component.HhhlDropdownMenuMaxHeight
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlTopBar
import cc.hhhl.client.ui.component.ThemePicker
import cc.hhhl.client.ui.component.TimelineDensityPicker

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HhhlTopBar(
            title = "设置",
            navigation = {
                HhhlBackButton(onClick = onBack)
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.groups.forEach { group ->
                item(key = "settings-group-${group.key}") {
                    SettingsGroupBlock(
                        group = group,
                        selectedTheme = state.selectedTheme,
                        selectedTimelineDensity = state.selectedTimelineDensity,
                        selectedDefaultNoteVisibility = state.selectedDefaultNoteVisibility,
                        selectedNotificationBadgeMode = state.selectedNotificationBadgeMode,
                        onThemeSelected = onThemeSelected,
                        onTimelineDensitySelected = onTimelineDensitySelected,
                        onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                        onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupBlock(
    group: SettingsGroup,
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    selectedDefaultNoteVisibility: DefaultNoteVisibility,
    selectedNotificationBadgeMode: NotificationBadgeMode,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = group.label,
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
        group.items.forEach { item ->
            SettingsRow(
                item = item,
                selectedTheme = selectedTheme,
                selectedTimelineDensity = selectedTimelineDensity,
                selectedDefaultNoteVisibility = selectedDefaultNoteVisibility,
                selectedNotificationBadgeMode = selectedNotificationBadgeMode,
                onThemeSelected = onThemeSelected,
                onTimelineDensitySelected = onTimelineDensitySelected,
                onDefaultNoteVisibilitySelected = onDefaultNoteVisibilitySelected,
                onNotificationBadgeModeSelected = onNotificationBadgeModeSelected,
            )
            HhhlDivider()
        }
    }
}

@Composable
private fun SettingsRow(
    item: SettingsItem,
    selectedTheme: HhhlThemePreset,
    selectedTimelineDensity: TimelineDensity,
    selectedDefaultNoteVisibility: DefaultNoteVisibility,
    selectedNotificationBadgeMode: NotificationBadgeMode,
    onThemeSelected: (HhhlThemePreset) -> Unit,
    onTimelineDensitySelected: (TimelineDensity) -> Unit,
    onDefaultNoteVisibilitySelected: (DefaultNoteVisibility) -> Unit,
    onNotificationBadgeModeSelected: (NotificationBadgeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsItemIcon(icon = item.icon, enabled = item.enabled)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.label,
                color = if (item.enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    LocalHhhlColors.current.subtleText
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (item.key) {
                SettingsItemKey.Theme -> ThemePicker(
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.TimelineDensity -> TimelineDensityPicker(
                    selectedDensity = selectedTimelineDensity,
                    onDensitySelected = onTimelineDensitySelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.DefaultNoteVisibility -> SettingsDropdownPicker(
                    selectedLabel = selectedDefaultNoteVisibility.label,
                    options = DefaultNoteVisibility.entries.toList(),
                    optionLabel = { it.label },
                    isSelected = { it == selectedDefaultNoteVisibility },
                    onSelected = onDefaultNoteVisibilitySelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsItemKey.NotificationBadges -> SettingsDropdownPicker(
                    selectedLabel = selectedNotificationBadgeMode.label,
                    options = NotificationBadgeMode.entries.toList(),
                    optionLabel = { it.label },
                    isSelected = { it == selectedNotificationBadgeMode },
                    onSelected = onNotificationBadgeModeSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Text(
                    text = item.value.orEmpty(),
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsItemIcon(
    icon: String,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    LocalHhhlColors.current.inputBackground
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon.take(2),
            color = if (enabled) MaterialTheme.colorScheme.primary else LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun <T> SettingsDropdownPicker(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(LocalHhhlColors.current.inputBackground)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            color = if (isSelected(option)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

fun settingsGroupLabels(groups: List<SettingsGroup>): List<String> {
    return groups.map { it.label }
}

fun settingsItemLabels(groups: List<SettingsGroup>): List<String> {
    return groups.flatMap { group -> group.items.map { it.label } }
}
