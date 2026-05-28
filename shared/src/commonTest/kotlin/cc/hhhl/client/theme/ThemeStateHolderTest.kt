package cc.hhhl.client.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeStateHolderTest {
    @Test
    fun startsWithSystemTheme() {
        val holder = ThemeStateHolder()

        assertEquals(HhhlThemePreset.System, holder.state.value.selectedPreset)
    }

    @Test
    fun selectsExplicitThemePreset() {
        val holder = ThemeStateHolder()

        holder.select(HhhlThemePreset.Dim)

        assertEquals(HhhlThemePreset.Dim, holder.state.value.selectedPreset)
    }

    @Test
    fun restoresStoredThemePreset() {
        val store = InMemoryThemeStore(HhhlThemePreset.Dark.storageKey)
        val holder = ThemeStateHolder(themeStore = store)

        holder.restoreStoredTheme()

        assertEquals(HhhlThemePreset.Dark, holder.state.value.selectedPreset)
    }

    @Test
    fun restoresLegacyEnumNameThemePreset() {
        val store = InMemoryThemeStore(HhhlThemePreset.Dark.name)
        val holder = ThemeStateHolder(themeStore = store)

        holder.restoreStoredTheme()

        assertEquals(HhhlThemePreset.Dark, holder.state.value.selectedPreset)
    }

    @Test
    fun selectingThemePersistsPreset() {
        val store = InMemoryThemeStore()
        val holder = ThemeStateHolder(themeStore = store)

        holder.select(HhhlThemePreset.Dim)

        assertEquals(HhhlThemePreset.Dim.storageKey, store.savedPresetName)
    }

    @Test
    fun restoreFallsBackToSystemWhenThemeStoreFails() {
        val holder = ThemeStateHolder(themeStore = ThrowingThemeStore(loadFails = true))

        holder.restoreStoredTheme()

        assertEquals(HhhlThemePreset.System, holder.state.value.selectedPreset)
    }

    @Test
    fun selectingThemeStillUpdatesStateWhenPersistingFails() {
        val holder = ThemeStateHolder(themeStore = ThrowingThemeStore(saveFails = true))

        holder.select(HhhlThemePreset.XDarkPink)

        assertEquals(HhhlThemePreset.XDarkPink, holder.state.value.selectedPreset)
    }

    @Test
    fun presetsExposeStableUniqueStorageKeys() {
        val keys = ThemePresetRegistry.presets.map { it.storageKey }

        assertEquals(keys.distinct(), keys)
        assertTrue(keys.all { it.isNotBlank() })
    }

    @Test
    fun registryIsTheThemePresetSourceForUiPickers() {
        assertEquals(HhhlThemePreset.entries.toList(), ThemePresetRegistry.presets)
    }

    @Test
    fun includesHhhlSharkeyThemePresets() {
        val labels = HhhlThemePreset.entries.map { it.label }

        assertTrue("HHHL 青绿" in labels)
        assertTrue("HHHL 夜蓝" in labels)
    }

    @Test
    fun includesXStyleAccentThemePresets() {
        val labels = HhhlThemePreset.entries.map { it.label }

        assertTrue("X 蓝" in labels)
        assertTrue("X 紫" in labels)
        assertTrue("X 粉" in labels)
        assertTrue("X 橙" in labels)
        assertTrue("X 深蓝" in labels)
        assertTrue("X 深紫" in labels)
        assertTrue("X 深粉" in labels)
        assertTrue("X 深橙" in labels)
    }

    @Test
    fun includesModernAppleAndNeutralThemePresets() {
        val labels = HhhlThemePreset.entries.map { it.label }

        assertTrue("Apple 亮色" in labels)
        assertTrue("Apple 深色" in labels)
        assertTrue("Apple 薄荷" in labels)
        assertTrue("石墨灰" in labels)
        assertTrue("OLED 黑" in labels)
    }

    @Test
    fun presetsExposePreviewColors() {
        HhhlThemePreset.entries.forEach { preset ->
            assertTrue(preset.previewAccentHex.startsWith("#"))
            assertTrue(preset.previewBackgroundHex.startsWith("#"))
            assertTrue(preset.previewSurfaceHex.startsWith("#"))
            assertEquals(7, preset.previewAccentHex.length)
            assertEquals(7, preset.previewBackgroundHex.length)
            assertEquals(7, preset.previewSurfaceHex.length)
        }
    }

    @Test
    fun presetsExposePickerGroups() {
        val groups = HhhlThemePreset.entries.map { it.groupLabel }

        assertTrue(groups.all { it.isNotBlank() })
        assertTrue("Apple" in groups)
        assertTrue("X 色彩" in groups)
        assertTrue("中性" in groups)
    }

    @Test
    fun darkAccentThemesUseDarkPreviewBackgrounds() {
        val darkAccentThemes = listOf(
            HhhlThemePreset.Dark,
            HhhlThemePreset.XDarkBlue,
            HhhlThemePreset.XDarkPurple,
            HhhlThemePreset.XDarkPink,
            HhhlThemePreset.XDarkOrange,
            HhhlThemePreset.AppleDark,
            HhhlThemePreset.OledBlack,
            HhhlThemePreset.HhhlDarkGreen,
        )

        darkAccentThemes.forEach { preset ->
            assertTrue(preset.previewBackgroundHex != "#FFFFFF")
            assertTrue(preset.previewSurfaceHex != "#FFFFFF")
        }
    }

    @Test
    fun invalidStoredThemeFallsBackToSystem() {
        val store = InMemoryThemeStore("unknown")
        val holder = ThemeStateHolder(themeStore = store)

        holder.restoreStoredTheme()

        assertEquals(HhhlThemePreset.System, holder.state.value.selectedPreset)
    }

    private class InMemoryThemeStore(
        private var storedPresetName: String? = null,
    ) : ThemeStore {
        var savedPresetName: String? = null

        override fun loadThemePresetName(): String? {
            return storedPresetName
        }

        override fun saveThemePresetName(presetName: String) {
            savedPresetName = presetName
            storedPresetName = presetName
        }
    }

    private class ThrowingThemeStore(
        private val loadFails: Boolean = false,
        private val saveFails: Boolean = false,
    ) : ThemeStore {
        override fun loadThemePresetName(): String? {
            if (loadFails) error("theme store load failed")
            return null
        }

        override fun saveThemePresetName(presetName: String) {
            if (saveFails) error("theme store save failed")
        }
    }
}
