package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.model.CustomEmoji
import cc.hhhl.client.theme.LocalHhhlColors
import coil3.compose.AsyncImage

data class CustomEmojiPickerSection(
    val label: String,
    val emojis: List<CustomEmoji>,
)

fun customEmojiPickerSections(
    customEmojis: List<CustomEmoji>,
    query: String = "",
    recentEmojiCodes: List<String> = emptyList(),
    maxPerCategory: Int = 48,
): List<CustomEmojiPickerSection> {
    val cleanQuery = query.trim()
    val distinctEmojis = customEmojis
        .filter { !it.isSensitive && it.name.isNotBlank() && it.url.isNotBlank() }
        .distinctBy { it.name }
    val filtered = if (cleanQuery.isBlank()) {
        distinctEmojis
    } else {
        distinctEmojis.filter { it.matchesCustomEmojiQuery(cleanQuery) }
    }
    val recentNames = recentEmojiCodes.mapNotNull { it.trim().customEmojiNameFromCode() }
    val recent = recentNames.mapNotNull { name -> filtered.firstOrNull { it.name == name } }.distinctBy { it.name }
    val remaining = filtered.filterNot { emoji -> recent.any { it.name == emoji.name } }
    val grouped = remaining
        .groupBy { it.category?.takeIf(String::isNotBlank) ?: "未分类" }
        .toSortedMap()
        .map { (category, emojis) ->
            CustomEmojiPickerSection(
                label = category,
                emojis = emojis.sortedBy { it.name }.take(maxPerCategory.coerceAtLeast(1)),
            )
        }

    return buildList {
        if (recent.isNotEmpty()) add(CustomEmojiPickerSection("最近", recent))
        addAll(grouped.filter { it.emojis.isNotEmpty() })
    }
}

fun customEmojiPickerResultText(emoji: CustomEmoji): String = emoji.reactionCode

@Composable
fun CustomEmojiPicker(
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String> = emptyList(),
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val sections = remember(customEmojis, query, recentEmojiCodes) {
        customEmojiPickerSections(
            customEmojis = customEmojis,
            query = query,
            recentEmojiCodes = recentEmojiCodes,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索自定义表情、别名或分类",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            minHeight = 38.dp,
            verticalPadding = 7.dp,
        )
        if (sections.isEmpty()) {
            Text(
                text = if (customEmojis.isEmpty()) "实例表情加载后会显示在这里" else "没有匹配的表情",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${section.label} · ${section.emojis.size}",
                    color = LocalHhhlColors.current.subtleText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section.emojis.forEach { emoji ->
                        CustomEmojiPickerItem(
                            emoji = emoji,
                            onClick = { onEmojiSelected(customEmojiPickerResultText(emoji)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomEmojiPickerItem(
    emoji: CustomEmoji,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = emoji.url,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = emoji.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun CustomEmoji.matchesCustomEmojiQuery(query: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        reactionCode.contains(query, ignoreCase = true) ||
        category.orEmpty().contains(query, ignoreCase = true) ||
        aliases.any { it.contains(query, ignoreCase = true) }
}

private fun String.customEmojiNameFromCode(): String? {
    if (!startsWith(":") || !endsWith(":") || length <= 2) return null
    return removePrefix(":")
        .removeSuffix(":")
        .substringBefore("@.")
        .takeIf { it.isNotBlank() }
}
