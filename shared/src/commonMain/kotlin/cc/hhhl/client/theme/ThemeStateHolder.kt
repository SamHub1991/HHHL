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
    Light("x-light", "清爽亮色", "#1D9BF0", "#F7F9FA", "#FFFFFF", "基础"),
    Dark("x-dark", "纯净深色", "#1D9BF0", "#000000", "#080808", "基础"),
    Dim("x-dim", "暗灰", "#86B7FF", "#15181D", "#1D222B", "基础"),
    XBlue("x-blue", "X 蓝", "#1D9BF0", "#F7F9FA", "#FFFFFF", "X 色彩"),
    XPurple("x-purple", "X 紫", "#7856FF", "#F8F7FB", "#FFFFFF", "X 色彩"),
    XPink("x-pink", "X 粉", "#F91880", "#FBF7FA", "#FFFFFF", "X 色彩"),
    XOrange("x-orange", "X 橙", "#FF7A00", "#FAF8F4", "#FFFFFF", "X 色彩"),
    XDarkBlue("x-dark-blue", "X 深蓝", "#1D9BF0", "#000000", "#080808", "X 色彩"),
    XDarkPurple("x-dark-purple", "X 深紫", "#7856FF", "#000000", "#0B0D10", "X 色彩"),
    XDarkPink("x-dark-pink", "X 深粉", "#F91880", "#000000", "#0B0D10", "X 色彩"),
    XDarkOrange("x-dark-orange", "X 深橙", "#FF7A00", "#000000", "#0B0D10", "X 色彩"),
    AppleLight("apple-light", "Apple 亮色", "#007AFF", "#F5F5F7", "#FFFFFF", "Apple"),
    AppleDark("apple-dark", "Apple 深色", "#0A84FF", "#101010", "#1C1C1E", "Apple"),
    AppleMint("apple-mint", "Apple 薄荷", "#00C7BE", "#F5F7F6", "#FFFFFF", "Apple"),
    Graphite("graphite", "石墨灰", "#8E8E93", "#F5F5F7", "#FFFFFF", "中性"),
    OledBlack("oled-black", "OLED 黑", "#0A84FF", "#000000", "#050505", "中性"),
    HhhlGreen("hhhl-green", "HHHL 绿", "#86B300", "#FAFCF7", "#FFFFFF", "HHHL"),
    HhhlDarkGreen("hhhl-dark-green", "HHHL 深绿", "#A7D23F", "#000000", "#0B0D10", "HHHL");

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
    val backgroundColorHex: String = "",
    val chatBackgroundColorHex: String = "",
    val inputBackgroundColorHex: String = "",
    val cardBackgroundColorHex: String = "",
    val globalBackgroundImageDataUri: String = "",
    val chatBackgroundImageDataUri: String = "",
) {
    val enabled: Boolean
        get() = listOf(
            accentColorHex,
            backgroundColorHex,
            chatBackgroundColorHex,
            inputBackgroundColorHex,
            cardBackgroundColorHex,
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
        backgroundColorHex = backgroundColorHex.normalizedColorHex(),
        chatBackgroundColorHex = chatBackgroundColorHex.normalizedColorHex(),
        inputBackgroundColorHex = inputBackgroundColorHex.normalizedColorHex(),
        cardBackgroundColorHex = cardBackgroundColorHex.normalizedColorHex(),
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
