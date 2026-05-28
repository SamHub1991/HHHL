package cc.hhhl.client.theme

import cc.hhhl.client.api.DriveFileUpload
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class HhhlThemePreset(
    val storageKey: String,
    val label: String,
    val previewAccentHex: String,
    val previewBackgroundHex: String,
    val previewSurfaceHex: String,
    val groupLabel: String,
) {
    System("system", "跟随系统", "#007AFF", "#F5F5F7", "#FFFFFF", "系统"),
    Light("x-light", "清爽亮色", "#1D9BF0", "#F6F8FA", "#FFFFFF", "基础"),
    Dark("x-dark", "纯净深色", "#1D9BF0", "#000000", "#080A0E", "基础"),
    Dim("x-dim", "暗灰", "#7DB6FF", "#11161D", "#171D26", "基础"),
    XBlue("x-blue", "X 蓝", "#1D9BF0", "#F6F8FA", "#FFFFFF", "X 色彩"),
    XPurple("x-purple", "X 紫", "#7B61FF", "#F7F6FF", "#FFFFFF", "X 色彩"),
    XPink("x-pink", "X 粉", "#F91880", "#FFF7FB", "#FFFFFF", "X 色彩"),
    XOrange("x-orange", "X 橙", "#FF7A00", "#F9F8F5", "#FFFFFF", "X 色彩"),
    XDarkBlue("x-dark-blue", "X 深蓝", "#1D9BF0", "#000000", "#080A0E", "X 色彩"),
    XDarkPurple("x-dark-purple", "X 深紫", "#8B5CF6", "#000000", "#080A0E", "X 色彩"),
    XDarkPink("x-dark-pink", "X 深粉", "#F91880", "#000000", "#080A0E", "X 色彩"),
    XDarkOrange("x-dark-orange", "X 深橙", "#FF8A00", "#000000", "#080A0E", "X 色彩"),
    AppleLight("apple-light", "Apple 亮色", "#007AFF", "#F5F5F7", "#FFFFFF", "Apple"),
    AppleDark("apple-dark", "Apple 深色", "#0A84FF", "#101010", "#1C1C1E", "Apple"),
    AppleMint("apple-mint", "Apple 薄荷", "#00C7BE", "#F5F8F8", "#FFFFFF", "Apple"),
    TgClassic("tg-classic", "TG 经典", "#3390EC", "#F4F8FB", "#FFFFFF", "Telegram"),
    TgIce("tg-ice", "TG 冰蓝", "#2AABEE", "#F1F7FC", "#FFFFFF", "Telegram"),
    TgNight("tg-night", "TG 夜间", "#58A6FF", "#121A24", "#182331", "Telegram"),
    TgAmoled("tg-amoled", "TG AMOLED", "#3390EC", "#000000", "#07090D", "Telegram"),
    Graphite("graphite", "石墨灰", "#5E6A75", "#F5F5F7", "#FFFFFF", "中性"),
    OledBlack("oled-black", "OLED 黑", "#0A84FF", "#000000", "#050505", "中性"),
    HhhlGreen("hhhl-green", "HHHL 青绿", "#00A67E", "#F5F9F8", "#FFFFFF", "HHHL"),
    HhhlDarkGreen("hhhl-dark-green", "HHHL 夜蓝", "#66A8FF", "#000000", "#090B0F", "HHHL");

    companion object {
        fun fromStoredValue(value: String?): HhhlThemePreset {
            return value
                ?.let { stored ->
                    ThemePresetRegistry.presets.firstOrNull { it.storageKey == stored || it.name == stored }
                }
                ?: System
        }
    }
}

object ThemePresetRegistry {
    val presets: List<HhhlThemePreset> = HhhlThemePreset.entries.toList()
}

data class ThemeUiState(
    val selectedPreset: HhhlThemePreset = HhhlThemePreset.System,
    val customTheme: HhhlCustomTheme = HhhlCustomTheme(),
)

data class HhhlCustomTheme(
    val accentColorHex: String = "",
    val accentSoftColorHex: String = "",
    val backgroundColorHex: String = "",
    val surfaceColorHex: String = "",
    val elevatedSurfaceColorHex: String = "",
    val panelBackgroundColorHex: String = "",
    val chatBackgroundColorHex: String = "",
    val inputBackgroundColorHex: String = "",
    val cardBackgroundColorHex: String = "",
    val noteBackgroundColorHex: String = "",
    val primaryTextColorHex: String = "",
    val secondaryTextColorHex: String = "",
    val mutedTextColorHex: String = "",
    val dividerColorHex: String = "",
    val borderColorHex: String = "",
    val mediaBackgroundColorHex: String = "",
    val avatarBackgroundColorHex: String = "",
    val badgeBackgroundColorHex: String = "",
    val unreadBadgeColorHex: String = "",
    val successColorHex: String = "",
    val warningColorHex: String = "",
    val dangerColorHex: String = "",
    val dangerTextColorHex: String = "",
    val textInverseColorHex: String = "",
    val focusRingColorHex: String = "",
    val inputBorderColorHex: String = "",
    val inputFocusedBorderColorHex: String = "",
    val toastBackgroundColorHex: String = "",
    val toastTextColorHex: String = "",
    val rankBronzeColorHex: String = "",
    val rankSilverColorHex: String = "",
    val rankGoldColorHex: String = "",
    val rankPlatinumColorHex: String = "",
    val buttonBackgroundColorHex: String = "",
    val buttonSelectedBackgroundColorHex: String = "",
    val chipBackgroundColorHex: String = "",
    val chipSelectedBackgroundColorHex: String = "",
    val topBarBackgroundColorHex: String = "",
    val bottomNavBackgroundColorHex: String = "",
    val bottomNavSelectedColorHex: String = "",
    val incomingBubbleColorHex: String = "",
    val outgoingBubbleColorHex: String = "",
    val incomingBubbleTextColorHex: String = "",
    val outgoingBubbleTextColorHex: String = "",
    val chatBubbleBorderColorHex: String = "",
    val chatComposerBackgroundColorHex: String = "",
    val chatMentionHighlightColorHex: String = "",
    val noteActionBackgroundColorHex: String = "",
    val noteReactionBackgroundColorHex: String = "",
    val noteTreeLineColorHex: String = "",
    val quoteBackgroundColorHex: String = "",
    val overlayScrimColorHex: String = "",
    val shadowColorHex: String = "",
    val globalBackgroundImageDataUri: String = "",
    val chatBackgroundImageDataUri: String = "",
) {
    val enabled: Boolean
        get() = listOf(
            accentColorHex,
            accentSoftColorHex,
            backgroundColorHex,
            surfaceColorHex,
            elevatedSurfaceColorHex,
            panelBackgroundColorHex,
            chatBackgroundColorHex,
            inputBackgroundColorHex,
            cardBackgroundColorHex,
            noteBackgroundColorHex,
            primaryTextColorHex,
            secondaryTextColorHex,
            mutedTextColorHex,
            dividerColorHex,
            borderColorHex,
            mediaBackgroundColorHex,
            avatarBackgroundColorHex,
            badgeBackgroundColorHex,
            unreadBadgeColorHex,
            successColorHex,
            warningColorHex,
            dangerColorHex,
            dangerTextColorHex,
            textInverseColorHex,
            focusRingColorHex,
            inputBorderColorHex,
            inputFocusedBorderColorHex,
            toastBackgroundColorHex,
            toastTextColorHex,
            rankBronzeColorHex,
            rankSilverColorHex,
            rankGoldColorHex,
            rankPlatinumColorHex,
            buttonBackgroundColorHex,
            buttonSelectedBackgroundColorHex,
            chipBackgroundColorHex,
            chipSelectedBackgroundColorHex,
            topBarBackgroundColorHex,
            bottomNavBackgroundColorHex,
            bottomNavSelectedColorHex,
            incomingBubbleColorHex,
            outgoingBubbleColorHex,
            incomingBubbleTextColorHex,
            outgoingBubbleTextColorHex,
            chatBubbleBorderColorHex,
            chatComposerBackgroundColorHex,
            chatMentionHighlightColorHex,
            noteActionBackgroundColorHex,
            noteReactionBackgroundColorHex,
            noteTreeLineColorHex,
            quoteBackgroundColorHex,
            overlayScrimColorHex,
            shadowColorHex,
            globalBackgroundImageDataUri,
            chatBackgroundImageDataUri,
        ).any { it.isNotBlank() }
}

interface ThemeStore {
    fun loadThemePresetName(): String?

    fun saveThemePresetName(presetName: String)

    fun loadCustomTheme(): HhhlCustomTheme = HhhlCustomTheme()

    fun saveCustomTheme(customTheme: HhhlCustomTheme) = Unit
}

object NoopThemeStore : ThemeStore {
    override fun loadThemePresetName(): String? = null

    override fun saveThemePresetName(presetName: String) = Unit
}

class ThemeStateHolder(
    private val themeStore: ThemeStore = NoopThemeStore,
) {
    private val mutableState = MutableStateFlow(ThemeUiState())
    val state: StateFlow<ThemeUiState> = mutableState

    fun restoreStoredTheme() {
        val restoredPreset = HhhlThemePreset.fromStoredValue(
            runCatching { themeStore.loadThemePresetName() }.getOrNull(),
        )
        val restoredCustomTheme = runCatching { themeStore.loadCustomTheme() }.getOrDefault(HhhlCustomTheme())

        mutableState.update { it.copy(selectedPreset = restoredPreset, customTheme = restoredCustomTheme) }
    }

    fun select(preset: HhhlThemePreset) {
        runCatching { themeStore.saveThemePresetName(preset.storageKey) }
        mutableState.update { it.copy(selectedPreset = preset) }
    }

    fun updateCustomTheme(customTheme: HhhlCustomTheme) {
        val normalized = customTheme.normalized()
        runCatching { themeStore.saveCustomTheme(normalized) }
        mutableState.update { it.copy(customTheme = normalized) }
    }

    fun resetCustomTheme() {
        updateCustomTheme(HhhlCustomTheme())
    }

    fun setGlobalBackgroundImage(upload: DriveFileUpload) {
        updateCustomTheme(
            state.value.customTheme.copy(globalBackgroundImageDataUri = upload.toImageDataUri()),
        )
    }

    fun clearGlobalBackgroundImage() {
        updateCustomTheme(state.value.customTheme.copy(globalBackgroundImageDataUri = ""))
    }

    fun setChatBackgroundImage(upload: DriveFileUpload) {
        updateCustomTheme(
            state.value.customTheme.copy(chatBackgroundImageDataUri = upload.toImageDataUri()),
        )
    }

    fun clearChatBackgroundImage() {
        updateCustomTheme(state.value.customTheme.copy(chatBackgroundImageDataUri = ""))
    }
}

private fun HhhlCustomTheme.normalized(): HhhlCustomTheme {
    return copy(
        accentColorHex = accentColorHex.normalizedColorHex(),
        accentSoftColorHex = accentSoftColorHex.normalizedColorHex(),
        backgroundColorHex = backgroundColorHex.normalizedColorHex(),
        surfaceColorHex = surfaceColorHex.normalizedColorHex(),
        elevatedSurfaceColorHex = elevatedSurfaceColorHex.normalizedColorHex(),
        panelBackgroundColorHex = panelBackgroundColorHex.normalizedColorHex(),
        chatBackgroundColorHex = chatBackgroundColorHex.normalizedColorHex(),
        inputBackgroundColorHex = inputBackgroundColorHex.normalizedColorHex(),
        cardBackgroundColorHex = cardBackgroundColorHex.normalizedColorHex(),
        noteBackgroundColorHex = noteBackgroundColorHex.normalizedColorHex(),
        primaryTextColorHex = primaryTextColorHex.normalizedColorHex(),
        secondaryTextColorHex = secondaryTextColorHex.normalizedColorHex(),
        mutedTextColorHex = mutedTextColorHex.normalizedColorHex(),
        dividerColorHex = dividerColorHex.normalizedColorHex(),
        borderColorHex = borderColorHex.normalizedColorHex(),
        mediaBackgroundColorHex = mediaBackgroundColorHex.normalizedColorHex(),
        avatarBackgroundColorHex = avatarBackgroundColorHex.normalizedColorHex(),
        badgeBackgroundColorHex = badgeBackgroundColorHex.normalizedColorHex(),
        unreadBadgeColorHex = unreadBadgeColorHex.normalizedColorHex(),
        successColorHex = successColorHex.normalizedColorHex(),
        warningColorHex = warningColorHex.normalizedColorHex(),
        dangerColorHex = dangerColorHex.normalizedColorHex(),
        dangerTextColorHex = dangerTextColorHex.normalizedColorHex(),
        textInverseColorHex = textInverseColorHex.normalizedColorHex(),
        focusRingColorHex = focusRingColorHex.normalizedColorHex(),
        inputBorderColorHex = inputBorderColorHex.normalizedColorHex(),
        inputFocusedBorderColorHex = inputFocusedBorderColorHex.normalizedColorHex(),
        toastBackgroundColorHex = toastBackgroundColorHex.normalizedColorHex(),
        toastTextColorHex = toastTextColorHex.normalizedColorHex(),
        rankBronzeColorHex = rankBronzeColorHex.normalizedColorHex(),
        rankSilverColorHex = rankSilverColorHex.normalizedColorHex(),
        rankGoldColorHex = rankGoldColorHex.normalizedColorHex(),
        rankPlatinumColorHex = rankPlatinumColorHex.normalizedColorHex(),
        buttonBackgroundColorHex = buttonBackgroundColorHex.normalizedColorHex(),
        buttonSelectedBackgroundColorHex = buttonSelectedBackgroundColorHex.normalizedColorHex(),
        chipBackgroundColorHex = chipBackgroundColorHex.normalizedColorHex(),
        chipSelectedBackgroundColorHex = chipSelectedBackgroundColorHex.normalizedColorHex(),
        topBarBackgroundColorHex = topBarBackgroundColorHex.normalizedColorHex(),
        bottomNavBackgroundColorHex = bottomNavBackgroundColorHex.normalizedColorHex(),
        bottomNavSelectedColorHex = bottomNavSelectedColorHex.normalizedColorHex(),
        incomingBubbleColorHex = incomingBubbleColorHex.normalizedColorHex(),
        outgoingBubbleColorHex = outgoingBubbleColorHex.normalizedColorHex(),
        incomingBubbleTextColorHex = incomingBubbleTextColorHex.normalizedColorHex(),
        outgoingBubbleTextColorHex = outgoingBubbleTextColorHex.normalizedColorHex(),
        chatBubbleBorderColorHex = chatBubbleBorderColorHex.normalizedColorHex(),
        chatComposerBackgroundColorHex = chatComposerBackgroundColorHex.normalizedColorHex(),
        chatMentionHighlightColorHex = chatMentionHighlightColorHex.normalizedColorHex(),
        noteActionBackgroundColorHex = noteActionBackgroundColorHex.normalizedColorHex(),
        noteReactionBackgroundColorHex = noteReactionBackgroundColorHex.normalizedColorHex(),
        noteTreeLineColorHex = noteTreeLineColorHex.normalizedColorHex(),
        quoteBackgroundColorHex = quoteBackgroundColorHex.normalizedColorHex(),
        overlayScrimColorHex = overlayScrimColorHex.normalizedColorHex(),
        shadowColorHex = shadowColorHex.normalizedColorHex(),
        globalBackgroundImageDataUri = globalBackgroundImageDataUri.trim(),
        chatBackgroundImageDataUri = chatBackgroundImageDataUri.trim(),
    )
}

private fun String.normalizedColorHex(): String {
    val clean = trim()
    if (clean.isBlank()) return ""
    return if (clean.startsWith("#")) clean else "#$clean"
}

@OptIn(ExperimentalEncodingApi::class)
private fun DriveFileUpload.toImageDataUri(): String {
    val safeContentType = contentType.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    return "data:$safeContentType;base64,${Base64.encode(bytes)}"
}
