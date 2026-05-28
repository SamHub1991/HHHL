package cc.hhhl.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hhhl.client.theme.HhhlCustomTheme
import cc.hhhl.client.theme.LocalHhhlColors
import cc.hhhl.client.theme.ThemeCustomizationCatalog
import cc.hhhl.client.theme.ThemeCustomizationColorSwatch
import cc.hhhl.client.theme.ThemeCustomizationTemplate
import cc.hhhl.client.theme.toColorOrNull
import cc.hhhl.client.ui.component.HhhlActionChip
import cc.hhhl.client.ui.component.HhhlBackButton
import cc.hhhl.client.ui.component.HhhlDivider
import cc.hhhl.client.ui.component.HhhlIconActionButton
import cc.hhhl.client.ui.component.HhhlTextInput
import cc.hhhl.client.ui.component.HhhlTopBar
import kotlin.math.roundToInt

@Composable
fun ThemeCustomizationScreen(
    customTheme: HhhlCustomTheme,
    onCustomThemeChanged: (HhhlCustomTheme) -> Unit,
    onReset: () -> Unit,
    onPickGlobalBackgroundImage: () -> Unit,
    onClearGlobalBackgroundImage: () -> Unit,
    onPickChatBackgroundImage: () -> Unit,
    onClearChatBackgroundImage: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        HhhlTopBar(
            title = "主题自定义",
            navigation = { HhhlBackButton(onClick = onBack) },
            action = {
                HhhlIconActionButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "恢复默认",
                    enabled = customTheme.enabled,
                    onClick = onReset,
                )
            },
        )
        HhhlDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item(key = "preview") {
                ThemePreviewPanel(customTheme = customTheme)
            }
            item(key = "templates") {
                ThemeTemplateSection(
                    customTheme = customTheme,
                    onTemplateSelected = { template ->
                        onCustomThemeChanged(
                            template.customTheme.copy(
                                globalBackgroundImageDataUri = customTheme.globalBackgroundImageDataUri,
                                chatBackgroundImageDataUri = customTheme.chatBackgroundImageDataUri,
                            ),
                        )
                    },
                )
            }
            item(key = "colors") {
                ThemeEditorSection(title = "颜色") {
                    ThemeHexInput(
                        label = "强调色",
                        value = customTheme.accentColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.accentColorHex,
                        swatches = accentThemeSwatches,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(accentColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "柔和强调色",
                        value = customTheme.accentSoftColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.accentSoftColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(accentSoftColorHex = it)) },
                    )
                    ThemeHexInput(
                        label = "全局背景",
                        value = customTheme.backgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.backgroundColorHex,
                        swatches = backgroundThemeSwatches,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(backgroundColorHex = it)) },
                    )
                    ThemeHexInput(
                        label = "聊天背景",
                        value = customTheme.chatBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chatBackgroundColorHex,
                        swatches = chatBackgroundThemeSwatches,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chatBackgroundColorHex = it)) },
                    )
                }
            }
            item(key = "surfaces") {
                ThemeEditorSection(title = "界面层级") {
                    ThemeHexInput(
                        label = "输入框底色",
                        value = customTheme.inputBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.inputBackgroundColorHex,
                        swatches = inputBackgroundThemeSwatches,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(inputBackgroundColorHex = it)) },
                    )
                    ThemeHexInput(
                        label = "卡片底色",
                        value = customTheme.cardBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.cardBackgroundColorHex,
                        swatches = cardBackgroundThemeSwatches,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(cardBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "帖子背景",
                        value = customTheme.noteBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.noteBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(noteBackgroundColorHex = it)) },
                    )
                }
            }
            item(key = "text-and-lines") {
                ThemeEditorSection(title = "文字与线条") {
                    ThemeTokenInput(
                        label = "主文字",
                        value = customTheme.primaryTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.primaryTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(primaryTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "次级文字",
                        value = customTheme.secondaryTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.secondaryTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(secondaryTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "弱提示文字",
                        value = customTheme.mutedTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.mutedTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(mutedTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "反色文字",
                        value = customTheme.textInverseColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.textInverseColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(textInverseColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "分割线",
                        value = customTheme.dividerColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.dividerColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(dividerColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "边框",
                        value = customTheme.borderColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.borderColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(borderColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "焦点环",
                        value = customTheme.focusRingColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.focusRingColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(focusRingColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "输入框边框",
                        value = customTheme.inputBorderColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.inputBorderColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(inputBorderColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "输入框焦点边框",
                        value = customTheme.inputFocusedBorderColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.inputFocusedBorderColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(inputFocusedBorderColorHex = it)) },
                    )
                }
            }
            item(key = "controls-and-nav") {
                ThemeEditorSection(title = "控件与导航") {
                    ThemeTokenInput(
                        label = "页面表面",
                        value = customTheme.surfaceColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.surfaceColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(surfaceColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "浮层表面",
                        value = customTheme.elevatedSurfaceColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.elevatedSurfaceColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(elevatedSurfaceColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "面板背景",
                        value = customTheme.panelBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.panelBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(panelBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "按钮背景",
                        value = customTheme.buttonBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.buttonBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(buttonBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "按钮选中背景",
                        value = customTheme.buttonSelectedBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.buttonSelectedBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(buttonSelectedBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "标签背景",
                        value = customTheme.chipBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chipBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chipBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "标签选中背景",
                        value = customTheme.chipSelectedBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chipSelectedBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chipSelectedBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "顶部栏背景",
                        value = customTheme.topBarBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.topBarBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(topBarBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "底部导航背景",
                        value = customTheme.bottomNavBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.bottomNavBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(bottomNavBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "底部导航选中",
                        value = customTheme.bottomNavSelectedColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.bottomNavSelectedColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(bottomNavSelectedColorHex = it)) },
                    )
                }
            }
            item(key = "chat") {
                ThemeEditorSection(title = "聊天") {
                    ThemeTokenInput(
                        label = "收到的气泡",
                        value = customTheme.incomingBubbleColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.incomingBubbleColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(incomingBubbleColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "发出的气泡",
                        value = customTheme.outgoingBubbleColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.outgoingBubbleColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(outgoingBubbleColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "收到气泡文字",
                        value = customTheme.incomingBubbleTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.incomingBubbleTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(incomingBubbleTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "发出气泡文字",
                        value = customTheme.outgoingBubbleTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.outgoingBubbleTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(outgoingBubbleTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "气泡边框",
                        value = customTheme.chatBubbleBorderColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chatBubbleBorderColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chatBubbleBorderColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "聊天输入栏",
                        value = customTheme.chatComposerBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chatComposerBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chatComposerBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "提及高亮",
                        value = customTheme.chatMentionHighlightColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.chatMentionHighlightColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(chatMentionHighlightColorHex = it)) },
                    )
                }
            }
            item(key = "notes-and-media") {
                ThemeEditorSection(title = "帖子与媒体") {
                    ThemeTokenInput(
                        label = "帖子操作按钮",
                        value = customTheme.noteActionBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.noteActionBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(noteActionBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "回应背景",
                        value = customTheme.noteReactionBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.noteReactionBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(noteReactionBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "回复树线",
                        value = customTheme.noteTreeLineColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.noteTreeLineColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(noteTreeLineColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "引用块背景",
                        value = customTheme.quoteBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.quoteBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(quoteBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "媒体底色",
                        value = customTheme.mediaBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.mediaBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(mediaBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "头像底色",
                        value = customTheme.avatarBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.avatarBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(avatarBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "角标背景",
                        value = customTheme.badgeBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.badgeBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(badgeBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "未读红点",
                        value = customTheme.unreadBadgeColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.unreadBadgeColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(unreadBadgeColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "成功状态",
                        value = customTheme.successColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.successColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(successColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "警告状态",
                        value = customTheme.warningColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.warningColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(warningColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "危险状态",
                        value = customTheme.dangerColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.dangerColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(dangerColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "危险文字",
                        value = customTheme.dangerTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.dangerTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(dangerTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "Toast 背景",
                        value = customTheme.toastBackgroundColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.toastBackgroundColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(toastBackgroundColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "Toast 文字",
                        value = customTheme.toastTextColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.toastTextColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(toastTextColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "遮罩",
                        value = customTheme.overlayScrimColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.overlayScrimColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(overlayScrimColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "阴影",
                        value = customTheme.shadowColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.shadowColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(shadowColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "铜牌色",
                        value = customTheme.rankBronzeColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.rankBronzeColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(rankBronzeColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "银牌色",
                        value = customTheme.rankSilverColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.rankSilverColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(rankSilverColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "金牌色",
                        value = customTheme.rankGoldColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.rankGoldColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(rankGoldColorHex = it)) },
                    )
                    ThemeTokenInput(
                        label = "白金色",
                        value = customTheme.rankPlatinumColorHex,
                        placeholder = ThemeCustomizationCatalog.placeholders.rankPlatinumColorHex,
                        onValueChange = { onCustomThemeChanged(customTheme.copy(rankPlatinumColorHex = it)) },
                    )
                }
            }
            item(key = "images") {
                ThemeEditorSection(title = "背景图") {
                    ThemeImageRow(
                        label = "全局背景图",
                        active = customTheme.globalBackgroundImageDataUri.isNotBlank(),
                        onPick = onPickGlobalBackgroundImage,
                        onClear = onClearGlobalBackgroundImage,
                    )
                    ThemeImageRow(
                        label = "聊天背景图",
                        active = customTheme.chatBackgroundImageDataUri.isNotBlank(),
                        onPick = onPickChatBackgroundImage,
                        onClear = onClearChatBackgroundImage,
                    )
                }
            }
            item(key = "reset") {
                HhhlActionChip(
                    label = "恢复默认",
                    enabled = customTheme.enabled,
                    onClick = onReset,
                )
            }
        }
    }
}

@Composable
private fun ThemeTokenInput(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    swatches: List<ThemeCustomizationColorSwatch> = emptyList(),
) {
    val colors = LocalHhhlColors.current
    val parsedColor = value.toColorOrNull()
    val fallbackColor = themeTokenFallbackColor(label, colors) ?: placeholder.toColorOrNull()
    val visibleFallbackColor = fallbackColor?.takeUnless { it.alpha <= 0.01f } ?: colors.pageBackground
    val effectivePlaceholder = if (label == "聊天背景" && value.isBlank() && fallbackColor?.alpha == 0f) {
        "跟随背景"
    } else {
        fallbackColor?.toHexRgb() ?: placeholder
    }
    val displayedColor = parsedColor ?: visibleFallbackColor
    val invalid = value.isNotBlank() && parsedColor == null
    var pickerExpanded by remember(label) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeCurrentColorSwatch(
                color = displayedColor,
                selected = pickerExpanded,
                invalid = invalid,
                onClick = { pickerExpanded = !pickerExpanded },
            )
            HhhlTextInput(
                value = value,
                onValueChange = onValueChange,
                placeholder = effectivePlaceholder,
                label = label,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            HhhlIconActionButton(
                icon = Icons.Filled.Palette,
                contentDescription = "$label 颜色选择",
                emphasized = pickerExpanded,
                onClick = { pickerExpanded = !pickerExpanded },
            )
        }
        if (pickerExpanded) {
            ThemeInlineColorPicker(
                value = value,
                placeholder = effectivePlaceholder,
                currentColor = displayedColor,
                swatches = swatches.ifEmpty { defaultThemeTokenSwatches },
                onColorSelected = onValueChange,
                onClear = { onValueChange("") },
            )
        }
    }
}

@Composable
private fun ThemeCurrentColorSwatch(
    color: Color,
    selected: Boolean,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(30.dp)
            .clip(shape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    invalid -> colors.danger.copy(alpha = 0.76f)
                    selected -> colors.focusRing.copy(alpha = 0.72f)
                    else -> colors.border.copy(alpha = 0.46f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ThemeInlineColorPicker(
    value: String,
    placeholder: String,
    currentColor: Color,
    swatches: List<ThemeCustomizationColorSwatch>,
    onColorSelected: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val red = currentColor.red.toColorChannelInt()
    val green = currentColor.green.toColorChannelInt()
    val blue = currentColor.blue.toColorChannelInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(themeEditorNestedPanelColor())
            .border(1.dp, colors.border.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(currentColor)
                    .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentColor.toHexRgb(),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = value.takeIf { it.isNotBlank() } ?: placeholder,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HhhlActionChip(
                label = "清除",
                enabled = value.isNotBlank(),
                onClick = onClear,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeColorSwatchButton(
                swatch = ThemeCustomizationColorSwatch(currentColor.toHexRgb()),
                selected = value.isBlank(),
                onClick = { onColorSelected(currentColor.toHexRgb()) },
            )
            swatches.forEach { swatch ->
                ThemeColorSwatchButton(
                    swatch = swatch,
                    selected = value.equals(swatch.hex, ignoreCase = true),
                    onClick = { onColorSelected(swatch.hex) },
                )
            }
        }
        ThemeColorSpectrumGrid(
            currentColor = currentColor,
            onColorSelected = onColorSelected,
        )
        ThemeColorChannelSlider("R", red, Color(0xFFFF453A)) { next ->
            onColorSelected(rgbToHex(next, green, blue))
        }
        ThemeColorChannelSlider("G", green, Color(0xFF34C759)) { next ->
            onColorSelected(rgbToHex(red, next, blue))
        }
        ThemeColorChannelSlider("B", blue, Color(0xFF0A84FF)) { next ->
            onColorSelected(rgbToHex(red, green, next))
        }
    }
}

@Composable
private fun ThemeColorSpectrumGrid(
    currentColor: Color,
    onColorSelected: (String) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        colorSpectrumRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { color ->
                    val hex = color.toHexRgb()
                    val selected = currentColor.toHexRgb().equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    colors.focusRing.copy(alpha = 0.70f)
                                } else {
                                    colors.border.copy(alpha = 0.34f)
                                },
                                shape = RoundedCornerShape(9.dp),
                            )
                            .clickable { onColorSelected(hex) },
                    )
                }
            }
        }
    }
}

private fun themeTokenFallbackColor(label: String, colors: cc.hhhl.client.theme.HhhlColors): Color? {
    return when (label) {
        "强调色" -> colors.accent
        "柔和强调色" -> colors.accentSoft
        "全局背景" -> colors.pageBackground
        "聊天背景" -> colors.chatBackground
        "输入框底色" -> colors.inputBackground
        "卡片底色" -> colors.surface
        "帖子背景" -> colors.noteBackground
        "主文字" -> colors.textPrimary
        "次级文字" -> colors.textSecondary
        "弱提示文字" -> colors.textMuted
        "反色文字" -> colors.textInverse
        "分割线", "边框" -> colors.border
        "焦点环" -> colors.focusRing
        "输入框边框" -> colors.inputBorder
        "输入框焦点边框" -> colors.inputFocusedBorder
        "页面表面" -> colors.surface
        "浮层表面" -> colors.surfaceElevated
        "面板背景" -> colors.panelBackground
        "按钮背景" -> colors.buttonBackground
        "按钮选中背景" -> colors.buttonSelectedBackground
        "标签背景" -> colors.chipBackground
        "标签选中背景" -> colors.chipSelectedBackground
        "顶部栏背景" -> colors.topBarBackground
        "底部导航背景" -> colors.bottomNavBackground
        "底部导航选中" -> colors.bottomNavSelected
        "收到的气泡" -> colors.chatIncomingBubble
        "发出的气泡" -> colors.chatOutgoingBubble
        "收到气泡文字" -> colors.chatIncomingText
        "发出气泡文字" -> colors.chatOutgoingText
        "气泡边框" -> colors.chatBubbleBorder
        "聊天输入栏" -> colors.chatComposerBackground
        "提及高亮" -> colors.chatMentionHighlight
        "帖子操作按钮" -> colors.noteActionBackground
        "回应背景" -> colors.noteReactionBackground
        "回复树线" -> colors.noteTreeLine
        "引用块背景" -> colors.quoteBackground
        "媒体底色" -> colors.mediaBackground
        "头像底色" -> colors.avatarBackground
        "角标背景" -> colors.badgeBackground
        "未读红点" -> colors.unreadBadge
        "成功状态" -> colors.success
        "警告状态" -> colors.warning
        "危险状态" -> colors.danger
        "危险文字" -> colors.dangerText
        "Toast 背景" -> colors.toastBackground
        "Toast 文字" -> colors.toastText
        "遮罩" -> colors.overlayScrim
        "阴影" -> colors.shadow
        "铜牌色" -> colors.rankBronze
        "银牌色" -> colors.rankSilver
        "金牌色" -> colors.rankGold
        "白金色" -> colors.rankPlatinum
        else -> null
    }
}

@Composable
private fun ThemeColorChannelSlider(
    label: String,
    value: Int,
    channelColor: Color,
    onValueChange: (Int) -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(18.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = channelColor,
                activeTrackColor = channelColor.copy(alpha = 0.82f),
                inactiveTrackColor = colors.border.copy(alpha = 0.30f),
            ),
        )
        Text(
            text = value.toString(),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
private fun ThemePreviewPanel(customTheme: HhhlCustomTheme) {
    val colors = LocalHhhlColors.current
    val background = colors.pageBackground
    val card = colors.noteBackground
    val input = colors.inputBackground
    val accent = colors.accent
    val textColor = colors.textPrimary
    val inputTextColor = colors.textSecondary
    val mutedText = colors.textMuted
    val chatBackground = colors.chatBackground.takeUnless { it.alpha <= 0.01f } ?: colors.pageBackground
    val activeLabel = if (customTheme.enabled) "自定义已启用" else "基于当前主题"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, colors.border.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HHHL",
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = activeLabel,
                    color = mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ThemeMiniStatusDot(colors.success)
                ThemeMiniStatusDot(colors.warning)
                ThemeMiniStatusDot(colors.danger)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
                .border(1.dp, colors.border.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "帖子卡片与信息面板",
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "@hhhl · 刚刚",
                color = mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(input)
                    .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "输入框、搜索框、聊天编辑区",
                    color = inputTextColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePreviewPill("回复", colors.noteActionBackground, accent, Modifier.weight(1f))
                ThemePreviewPill("回应", colors.noteReactionBackground, accent, Modifier.weight(1f))
                ThemePreviewPill("引用", colors.quoteBackground, colors.textSecondary, Modifier.weight(1f))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(chatBackground)
                .border(1.dp, colors.chatBubbleBorder.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemePreviewBubble(
                text = "收到的消息气泡",
                container = colors.chatIncomingBubble,
                content = colors.chatIncomingText,
                alignEnd = false,
            )
            ThemePreviewBubble(
                text = "发出的消息气泡",
                container = colors.chatOutgoingBubble,
                content = colors.chatOutgoingText,
                alignEnd = true,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.chatComposerBackground)
                    .border(1.dp, colors.inputBorder.copy(alpha = 0.40f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "聊天输入栏",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.bottomNavBackground)
                .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemePreviewPill("首页", colors.bottomNavSelected, accent, Modifier.weight(1f))
            ThemePreviewPill("通知", Color.Transparent, mutedText, Modifier.weight(1f))
            ThemePreviewPill("设置", Color.Transparent, mutedText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemeMiniStatusDot(color: Color) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .border(1.dp, colors.border.copy(alpha = 0.24f), RoundedCornerShape(5.dp)),
    )
}

@Composable
private fun ThemePreviewPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(container)
            .border(1.dp, colors.border.copy(alpha = 0.20f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun ThemePreviewBubble(
    text: String,
    container: Color,
    content: Color,
    alignEnd: Boolean,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(14.dp))
                .background(container)
                .border(1.dp, colors.chatBubbleBorder.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThemeTemplateSection(
    customTheme: HhhlCustomTheme,
    onTemplateSelected: (ThemeCustomizationTemplate) -> Unit,
) {
    ThemeEditorSection(title = "设计模板") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeCustomizationCatalog.templates.forEach { template ->
                val selected = customTheme.matchesTemplate(template.customTheme)
                ThemeTemplateCard(
                    template = template,
                    selected = selected,
                    onClick = { onTemplateSelected(template) },
                )
            }
        }
    }
}

@Composable
private fun ThemeTemplateCard(
    template: ThemeCustomizationTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val accent = template.accentHex.toColorOrNull() ?: colors.accent
    val background = template.backgroundHex.toColorOrNull() ?: colors.pageBackground
    val surface = template.surfaceHex.toColorOrNull() ?: colors.surface
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(shape)
            .background(themeEditorNestedPanelColor())
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.focusRing.copy(alpha = 0.70f) else colors.border.copy(alpha = 0.28f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeTemplateDot(background, Modifier.weight(1f))
            ThemeTemplateDot(surface, Modifier.weight(1f))
            ThemeTemplateDot(accent, Modifier.weight(1f))
        }
        Text(
            text = template.label,
            color = if (selected) colors.accent else colors.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemeTemplateDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHhhlColors.current
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(9.dp)),
    )
}

private fun HhhlCustomTheme.matchesTemplate(template: HhhlCustomTheme): Boolean {
    return accentColorHex.equals(template.accentColorHex, ignoreCase = true) &&
        backgroundColorHex.equals(template.backgroundColorHex, ignoreCase = true) &&
        surfaceColorHex.equals(template.surfaceColorHex, ignoreCase = true) &&
        inputBackgroundColorHex.equals(template.inputBackgroundColorHex, ignoreCase = true) &&
        outgoingBubbleColorHex.equals(template.outgoingBubbleColorHex, ignoreCase = true)
}

@Composable
private fun ThemeEditorSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = LocalHhhlColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(themeEditorPanelColor())
                .border(1.dp, colors.border.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ThemeHexInput(
    label: String,
    value: String,
    placeholder: String,
    swatches: List<ThemeCustomizationColorSwatch> = emptyList(),
    onValueChange: (String) -> Unit,
) {
    ThemeTokenInput(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        swatches = swatches,
    )
}

@Composable
private fun ThemeColorSwatchButton(
    swatch: ThemeCustomizationColorSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    val color = swatch.hex.toColorOrNull() ?: Color.Transparent
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    colors.focusRing.copy(alpha = 0.42f)
                } else {
                    colors.border.copy(alpha = 0.52f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (color.luminance() < 0.45f) Color.White else Color(0xFF0F1419),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun themeEditorPanelColor(): Color {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    return if (isDarkSurface) {
        Color.White.copy(alpha = 0.050f)
    } else {
        colors.surface.copy(alpha = 0.78f)
    }
}

@Composable
private fun themeEditorNestedPanelColor(): Color {
    val colors = LocalHhhlColors.current
    val isDarkSurface = colors.pageBackground.luminance() < 0.18f
    return if (isDarkSurface) {
        Color.White.copy(alpha = 0.055f)
    } else {
        colors.inputBackground.copy(alpha = 0.54f)
    }
}

private fun Float.toColorChannelInt(): Int {
    return (this * 255f).roundToInt().coerceIn(0, 255)
}

private fun rgbToHex(red: Int, green: Int, blue: Int): String {
    return "#${red.toHexByte()}${green.toHexByte()}${blue.toHexByte()}"
}

private fun Int.toHexByte(): String {
    return coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
}

private fun Color.toHexRgb(): String {
    return rgbToHex(red.toColorChannelInt(), green.toColorChannelInt(), blue.toColorChannelInt())
}

private val colorSpectrumRows: List<List<Color>> = listOf(
    listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF5F5F7),
        Color(0xFFE7E9EA),
        Color(0xFF8B98A5),
        Color(0xFF2F3336),
        Color(0xFF111114),
        Color(0xFF050506),
        Color(0xFF000000),
    ),
    listOf(
        Color(0xFFEAF4FF),
        Color(0xFFB9DCFF),
        Color(0xFF72B7F6),
        Color(0xFF3390EC),
        Color(0xFF1D9BF0),
        Color(0xFF0A84FF),
        Color(0xFF1F4E79),
        Color(0xFF071B31),
    ),
    listOf(
        Color(0xFFE4F8F6),
        Color(0xFFB4EEE8),
        Color(0xFF69DCD2),
        Color(0xFF00C7BE),
        Color(0xFF34C759),
        Color(0xFF2EA44F),
        Color(0xFF183A2A),
        Color(0xFF0B2017),
    ),
    listOf(
        Color(0xFFFFE8F3),
        Color(0xFFFFB8D8),
        Color(0xFFFF7AB8),
        Color(0xFFF91880),
        Color(0xFFFF453A),
        Color(0xFFFF8A80),
        Color(0xFF5A1B22),
        Color(0xFF201216),
    ),
    listOf(
        Color(0xFFFFF0DE),
        Color(0xFFFFD7A3),
        Color(0xFFFFB454),
        Color(0xFFFFB020),
        Color(0xFFFF7A00),
        Color(0xFFB87333),
        Color(0xFF4A2A13),
        Color(0xFF1F1710),
    ),
    listOf(
        Color(0xFFF0EBFF),
        Color(0xFFD7CBFF),
        Color(0xFFB49CFF),
        Color(0xFF7856FF),
        Color(0xFFBF5AF2),
        Color(0xFFFF4FD8),
        Color(0xFF3A2356),
        Color(0xFF171620),
    ),
)

@Composable
private fun ThemeImageRow(
    label: String,
    active: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalHhhlColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = if (active) colors.accent else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (active) "已设置" else "未设置",
                color = colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        HhhlIconActionButton(
            icon = Icons.Filled.Palette,
            contentDescription = "选择$label",
            emphasized = true,
            onClick = onPick,
        )
        HhhlIconActionButton(
            icon = Icons.Filled.DeleteOutline,
            contentDescription = "清除$label",
            enabled = active,
            onClick = onClear,
        )
    }
}

private val accentThemeSwatches = ThemeCustomizationCatalog.accentSwatches
private val backgroundThemeSwatches = ThemeCustomizationCatalog.backgroundSwatches
private val chatBackgroundThemeSwatches = ThemeCustomizationCatalog.chatBackgroundSwatches
private val inputBackgroundThemeSwatches = ThemeCustomizationCatalog.inputBackgroundSwatches
private val cardBackgroundThemeSwatches = ThemeCustomizationCatalog.cardBackgroundSwatches
private val defaultThemeTokenSwatches = listOf(
    "#000000",
    "#050506",
    "#0B0D10",
    "#111114",
    "#1C1C1E",
    "#202B36",
    "#2F3336",
    "#71767B",
    "#8B98A5",
    "#E7E9EA",
    "#F5F5F7",
    "#FFFFFF",
    "#1D9BF0",
    "#0A84FF",
    "#3390EC",
    "#34C759",
    "#FFB020",
    "#FF453A",
).map(::ThemeCustomizationColorSwatch)
