package cc.hhhl.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import cc.hhhl.client.model.EmojiCategory
import cc.hhhl.client.model.commonEmojiCategories
import cc.hhhl.client.model.commonEmojiOptions
import cc.hhhl.client.theme.LocalHhhlColors
import coil3.compose.AsyncImage

data class UnicodeEmojiPickerSection(
    val key: String,
    val label: String,
    val emojis: List<String>,
)

data class CustomEmojiPickerSection(
    val label: String,
    val emojis: List<CustomEmoji>,
)

private data class EmojiPickerTab(
    val key: String,
    val label: String,
)

private const val CustomEmojiTabKey = "custom"
private const val SearchEmojiTabKey = "search"
private const val SearchUnicodeEmojiMaxPerCategory = 96

private data class CustomEmojiPickerIndex(
    val emojis: List<CustomEmoji>,
    val emojisByName: Map<String, CustomEmoji>,
    val categories: Map<String, List<CustomEmoji>>,
)

fun customEmojiPickerSections(
    customEmojis: List<CustomEmoji>,
    query: String = "",
    recentEmojiCodes: List<String> = emptyList(),
    maxPerCategory: Int = 48,
): List<CustomEmojiPickerSection> {
    return customEmojiPickerIndex(customEmojis).sections(
        query = query,
        recentEmojiCodes = recentEmojiCodes,
        maxPerCategory = maxPerCategory,
    )
}

private fun customEmojiPickerIndex(customEmojis: List<CustomEmoji>): CustomEmojiPickerIndex {
    val distinctEmojisByName = LinkedHashMap<String, CustomEmoji>()
    customEmojis.forEach { emoji ->
        if (
            !emoji.isSensitive &&
            emoji.name.isNotBlank() &&
            emoji.url.isNotBlank() &&
            !distinctEmojisByName.containsKey(emoji.name)
        ) {
            distinctEmojisByName[emoji.name] = emoji
        }
    }

    val categories = mutableMapOf<String, MutableList<CustomEmoji>>()
    distinctEmojisByName.values.forEach { emoji ->
        val category = emoji.category?.takeIf(String::isNotBlank) ?: "未分类"
        categories.getOrPut(category) { mutableListOf() }.add(emoji)
    }
    val sortedCategories = categories.mapValues { (_, emojis) -> emojis.sortedBy { it.name } }
    return CustomEmojiPickerIndex(
        emojis = distinctEmojisByName.values.toList(),
        emojisByName = distinctEmojisByName,
        categories = sortedCategories,
    )
}

private fun CustomEmojiPickerIndex.sections(
    query: String,
    recentEmojiCodes: List<String>,
    maxPerCategory: Int,
): List<CustomEmojiPickerSection> {
    val cleanQuery = query.trim()
    val filtered = ArrayList<CustomEmoji>(emojis.size)
    emojis.forEach { emoji ->
        if (cleanQuery.isBlank() || emoji.matchesCustomEmojiQuery(cleanQuery)) {
            filtered += emoji
        }
    }

    val filteredNameSet = filtered.mapTo(HashSet(filtered.size)) { it.name }
    val recentNames = LinkedHashSet<String>()
    recentEmojiCodes.forEach { code ->
        code.trim().customEmojiNameFromCode()?.let { recentNames.add(it) }
    }
    val recent = recentNames.mapNotNull { name ->
        emojisByName[name]?.takeIf { it.name in filteredNameSet }
    }
    val recentNameSet = recent.mapTo(HashSet(recent.size)) { it.name }
    val maxItems = maxPerCategory.coerceAtLeast(1)

    return buildList {
        if (recent.isNotEmpty()) add(CustomEmojiPickerSection("最近", recent))
        categories.keys.sorted().forEach { category ->
            val emojis = categories[category].orEmpty()
                .asSequence()
                .filter { it.name in filteredNameSet && it.name !in recentNameSet }
                .take(maxItems)
                .toList()
            if (emojis.isNotEmpty()) {
                add(CustomEmojiPickerSection(label = category, emojis = emojis))
            }
        }
    }
}

fun unicodeEmojiPickerSections(
    query: String = "",
    categories: List<EmojiCategory> = commonEmojiCategories,
    maxPerCategory: Int = Int.MAX_VALUE,
): List<UnicodeEmojiPickerSection> {
    val cleanQuery = query.trim()
    val maxItems = maxPerCategory.coerceAtLeast(1)
    val seen = LinkedHashSet<String>()
    return categories.mapNotNull { category ->
        val categoryMatches = cleanQuery.isNotBlank() && (
            category.label.contains(cleanQuery, ignoreCase = true) ||
                category.key.contains(cleanQuery, ignoreCase = true)
            )
        val emojis = category.emojis
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it) }
            .filter { cleanQuery.isBlank() || categoryMatches || it.contains(cleanQuery) }
            .take(maxItems)
            .toList()
        emojis.takeIf { it.isNotEmpty() }?.let {
            UnicodeEmojiPickerSection(
                key = category.key,
                label = category.label,
                emojis = it,
            )
        }
    }
}

fun customEmojiPickerResultText(emoji: CustomEmoji): String = emoji.reactionCode

@Composable
fun CustomEmojiPicker(
    customEmojis: List<CustomEmoji>,
    recentEmojiCodes: List<String> = emptyList(),
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxPerCategory: Int = 48,
    unicodeEmojis: List<String> = commonEmojiOptions,
    unicodeCategories: List<EmojiCategory> = commonEmojiCategories,
    compact: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var selectedTabKey by remember(unicodeCategories) { mutableStateOf(unicodeCategories.firstOrNull()?.key.orEmpty()) }
    val emojiIndex = remember(customEmojis) {
        customEmojiPickerIndex(customEmojis)
    }
    val sections = remember(emojiIndex, query, recentEmojiCodes, maxPerCategory) {
        emojiIndex.sections(
            query = query,
            recentEmojiCodes = recentEmojiCodes,
            maxPerCategory = maxPerCategory,
        )
    }
    val unicodeSections = remember(query, unicodeEmojis, unicodeCategories) {
        val categories = if (unicodeEmojis === commonEmojiOptions && unicodeCategories === commonEmojiCategories) {
            unicodeCategories
        } else {
            listOf(EmojiCategory(key = "unicode", label = "全部", emojis = unicodeEmojis))
        }
        unicodeEmojiPickerSections(
            query = query,
            categories = categories,
            maxPerCategory = if (query.isBlank()) Int.MAX_VALUE else SearchUnicodeEmojiMaxPerCategory,
        )
    }
    val tabs = remember(unicodeSections, sections, query) {
        if (query.isBlank()) {
            buildList {
                unicodeSections.forEach { add(EmojiPickerTab(it.key, it.label)) }
                if (sections.isNotEmpty()) add(EmojiPickerTab(CustomEmojiTabKey, "实例"))
            }
        } else {
            listOf(EmojiPickerTab(SearchEmojiTabKey, "搜索"))
        }
    }
    val selectedTab = tabs.firstOrNull { it.key == selectedTabKey } ?: tabs.firstOrNull()
    val selectedUnicodeSection = unicodeSections.firstOrNull { it.key == selectedTab?.key }
    val outerHorizontalPadding = if (compact) 8.dp else 12.dp
    val outerVerticalPadding = if (compact) 6.dp else 8.dp
    val outerSpacing = if (compact) 7.dp else 10.dp
    val searchMinHeight = if (compact) 34.dp else 38.dp
    val searchVerticalPadding = if (compact) 5.dp else 7.dp
    val sectionSpacing = if (compact) 7.dp else 10.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(outerSpacing),
    ) {
        HhhlTextInput(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索表情、别名或分类",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            minHeight = searchMinHeight,
            verticalPadding = searchVerticalPadding,
        )
        if (tabs.isNotEmpty()) {
            EmojiPickerTabRow(
                tabs = tabs,
                selectedKey = selectedTab?.key.orEmpty(),
                onSelected = { selectedTabKey = it },
                compact = compact,
            )
        }
        if (sections.isEmpty() && unicodeSections.isEmpty()) {
            Text(
                text = if (customEmojis.isEmpty()) "实例表情加载后会显示在这里" else "没有匹配的表情",
                color = LocalHhhlColors.current.subtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (query.isBlank() && selectedUnicodeSection != null) {
            CustomUnicodeEmojiGridSection(
                section = selectedUnicodeSection,
                onEmojiSelected = onEmojiSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                compact = compact,
            )
        } else if (query.isNotBlank() && (unicodeSections.isNotEmpty() || sections.isNotEmpty())) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                items(
                    items = unicodeSections,
                    key = { "unicode-${it.key}" },
                    contentType = { "unicode-emoji-section" },
                ) { section ->
                    CustomUnicodeEmojiSection(
                        section,
                        onEmojiSelected,
                        compact = compact,
                    )
                }
                items(
                    items = sections,
                    key = { "custom-${it.label}" },
                    contentType = { "custom-emoji-section" },
                ) { section ->
                    CustomEmojiPickerSectionView(
                        section,
                        onEmojiSelected,
                        compact = compact,
                    )
                }
            }
        } else if (selectedTab?.key == CustomEmojiTabKey && sections.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                items(
                    items = sections,
                    key = { it.label },
                    contentType = { "custom-emoji-section" },
                ) { section ->
                    CustomEmojiPickerSectionView(
                        section,
                        onEmojiSelected,
                        compact = compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomUnicodeEmojiGridSection(
    section: UnicodeEmojiPickerSection,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val minCellSize = if (compact) 42.dp else 48.dp
    val gridSpacing = if (compact) 6.dp else 8.dp
    val itemShape = RoundedCornerShape(if (compact) 7.dp else 8.dp)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
    ) {
        Text(
            text = "${section.label} · ${section.emojis.size}",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minCellSize),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(
                items = section.emojis,
                key = { it },
                contentType = { "unicode-emoji-grid-item" },
            ) { emoji ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(itemShape)
                        .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.78f))
                        .border(
                            width = 1.dp,
                            color = LocalHhhlColors.current.divider.copy(alpha = 0.44f),
                            shape = itemShape,
                        )
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerTabRow(
    tabs: List<EmojiPickerTab>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            EmojiPickerTabItem(
                label = tab.label,
                selected = tab.key == selectedKey,
                onClick = { onSelected(tab.key) },
                compact = compact,
            )
        }
    }
}

@Composable
private fun EmojiPickerTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    colors.inputBackground.copy(alpha = 0.58f)
                },
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    colors.divider.copy(alpha = 0.36f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 5.dp else 7.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CustomUnicodeEmojiSection(
    section: UnicodeEmojiPickerSection,
    onEmojiSelected: (String) -> Unit,
    compact: Boolean = false,
) {
    val gridSpacing = if (compact) 6.dp else 8.dp
    val itemShape = RoundedCornerShape(if (compact) 7.dp else 8.dp)
    val minCellSize = if (compact) 32.dp else 36.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
        Text(
            text = "${section.label} · ${section.emojis.size}",
            color = LocalHhhlColors.current.subtleText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = ((maxWidth.value / minCellSize.value).toInt()).coerceAtLeast(1)
            val rows = section.emojis.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                    ) {
                        row.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(itemShape)
                                    .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.78f))
                                    .border(
                                        width = 1.dp,
                                        color = LocalHhhlColors.current.divider.copy(alpha = 0.44f),
                                        shape = itemShape,
                                    )
                                    .clickable { onEmojiSelected(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        repeat(columns - row.size) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomEmojiPickerSectionView(
    section: CustomEmojiPickerSection,
    onEmojiSelected: (String) -> Unit,
    compact: Boolean = false,
) {
    val gridSpacing = if (compact) 6.dp else 8.dp
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
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
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
        ) {
            section.emojis.forEach { emoji ->
                CustomEmojiPickerItem(
                    emoji = emoji,
                    onClick = { onEmojiSelected(customEmojiPickerResultText(emoji)) },
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun CustomEmojiPickerItem(
    emoji: CustomEmoji,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val iconSize = if (compact) 22.dp else 24.dp
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LocalHhhlColors.current.inputBackground.copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = LocalHhhlColors.current.divider.copy(alpha = 0.44f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 7.dp else 8.dp,
                vertical = if (compact) 5.dp else 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = emoji.url,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
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
