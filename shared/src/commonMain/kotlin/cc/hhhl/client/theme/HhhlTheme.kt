package cc.hhhl.client.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HhhlTypography = Typography()

private val HhhlShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
)

data class HhhlColors(
    val divider: Color,
    val subtleText: Color,
    val avatarBackground: Color,
    val mediaBackground: Color,
    val inputBackground: Color,
    val cardBackground: Color,
    val badgeBackground: Color,
)

private data class HhhlPalette(
    val scheme: ColorScheme,
    val colors: HhhlColors,
)

private fun modernLightScheme(
    primary: Color,
    background: Color = Color(0xFFF7F9FA),
    surface: Color = Color.White,
    onBackground: Color = Color(0xFF0F1419),
    secondary: Color = Color(0xFF536471),
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = background,
    surface = surface,
    surfaceVariant = Color(0xFFF2F4F5),
    surfaceContainer = surface,
    surfaceContainerLow = background,
    surfaceContainerHighest = Color(0xFFEFF3F4),
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = secondary,
    secondary = secondary,
    outline = Color(0xFFD8DEE3),
    outlineVariant = Color(0xFFE7ECEF),
)

private fun modernDarkScheme(
    primary: Color,
    background: Color = Color.Black,
    surface: Color = Color(0xFF080808),
    onBackground: Color = Color(0xFFE7E9EA),
    secondary: Color = Color(0xFF8B98A5),
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = background,
    surface = surface,
    surfaceVariant = Color(0xFF16181C),
    surfaceContainer = surface,
    surfaceContainerLow = background,
    surfaceContainerHighest = Color(0xFF202327),
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = secondary,
    secondary = secondary,
    outline = Color(0xFF2F3336),
    outlineVariant = Color(0xFF1F2328),
)

private fun modernLightColors(
    accentTint: Color = Color(0xFFE8F4FE),
    divider: Color = Color(0xFFE7ECEF),
    subtleText: Color = Color(0xFF536471),
    mediaBackground: Color = Color(0xFFF1F4F6),
    inputBackground: Color = Color(0xFFF3F5F7),
    cardBackground: Color = Color.White,
): HhhlColors = HhhlColors(
    divider = divider,
    subtleText = subtleText,
    avatarBackground = accentTint,
    mediaBackground = mediaBackground,
    inputBackground = inputBackground,
    cardBackground = cardBackground,
    badgeBackground = accentTint,
)

private fun modernDarkColors(
    accentTint: Color = Color(0xFF0B1822),
    divider: Color = Color(0xFF2A2F35),
    subtleText: Color = Color(0xFF8B98A5),
    mediaBackground: Color = Color(0xFF111316),
    inputBackground: Color = Color(0xFF16181C),
    cardBackground: Color = Color(0xFF080808),
): HhhlColors = HhhlColors(
    divider = divider,
    subtleText = subtleText,
    avatarBackground = accentTint,
    mediaBackground = mediaBackground,
    inputBackground = inputBackground,
    cardBackground = cardBackground,
    badgeBackground = accentTint,
)

private val LightPalette = HhhlPalette(
    scheme = modernLightScheme(primary = Color(0xFF1D9BF0)),
    colors = modernLightColors(),
)

private val DarkPalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFF1D9BF0)),
    colors = modernDarkColors(),
)

private val DimPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF86B7FF),
        background = Color(0xFF15181D),
        surface = Color(0xFF1D222B),
        onBackground = Color(0xFFE7EAF0),
        secondary = Color(0xFFAAB4C0),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF223044),
        divider = Color(0xFF303844),
        subtleText = Color(0xFFAAB4C0),
        mediaBackground = Color(0xFF202631),
        inputBackground = Color(0xFF242B35),
        cardBackground = Color(0xFF1D222B),
    ),
)

private val XBluePalette = LightPalette

private val XPurplePalette = HhhlPalette(
    scheme = modernLightScheme(primary = Color(0xFF7856FF), background = Color(0xFFF8F7FB)),
    colors = modernLightColors(
        accentTint = Color(0xFFF0EBFF),
        mediaBackground = Color(0xFFF3F1F8),
        inputBackground = Color(0xFFF5F3F8),
    ),
)

private val XPinkPalette = HhhlPalette(
    scheme = modernLightScheme(primary = Color(0xFFF91880), background = Color(0xFFFBF7FA)),
    colors = modernLightColors(
        accentTint = Color(0xFFFFE8F3),
        mediaBackground = Color(0xFFF8F1F5),
        inputBackground = Color(0xFFF9F3F6),
    ),
)

private val XOrangePalette = HhhlPalette(
    scheme = modernLightScheme(primary = Color(0xFFFF7A00), background = Color(0xFFFAF8F4)),
    colors = modernLightColors(
        accentTint = Color(0xFFFFF0DE),
        mediaBackground = Color(0xFFF7F3EC),
        inputBackground = Color(0xFFF8F4EF),
    ),
)

private val XDarkBluePalette = DarkPalette

private val XDarkPurplePalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFF7856FF)),
    colors = modernDarkColors(accentTint = Color(0xFF171126)),
)

private val XDarkPinkPalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFF91880)),
    colors = modernDarkColors(accentTint = Color(0xFF220A16)),
)

private val XDarkOrangePalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFFF7A00)),
    colors = modernDarkColors(accentTint = Color(0xFF241507)),
)

private val AppleLightPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF007AFF),
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        secondary = Color(0xFF6E6E73),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFEAF4FF),
        divider = Color(0xFFE5E5EA),
        subtleText = Color(0xFF6E6E73),
        mediaBackground = Color(0xFFF1F1F4),
        inputBackground = Color(0xFFF2F2F7),
    ),
)

private val AppleDarkPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF0A84FF),
        background = Color(0xFF101010),
        surface = Color(0xFF1C1C1E),
        onBackground = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF10243A),
        divider = Color(0xFF38383A),
        subtleText = Color(0xFF98989D),
        mediaBackground = Color(0xFF1C1C1E),
        inputBackground = Color(0xFF2C2C2E),
        cardBackground = Color(0xFF1C1C1E),
    ),
)

private val AppleMintPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF00AFA8),
        background = Color(0xFFF5F7F6),
        onBackground = Color(0xFF1D1D1F),
        secondary = Color(0xFF66706F),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE4F8F6),
        divider = Color(0xFFE2E8E6),
        subtleText = Color(0xFF66706F),
        mediaBackground = Color(0xFFEFF4F3),
        inputBackground = Color(0xFFF1F5F4),
    ),
)

private val GraphitePalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF6E6E73),
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        secondary = Color(0xFF6E6E73),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE8E8ED),
        divider = Color(0xFFE5E5EA),
        subtleText = Color(0xFF6E6E73),
        mediaBackground = Color(0xFFF1F1F4),
        inputBackground = Color(0xFFF2F2F7),
    ),
)

private val OledBlackPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF0A84FF),
        background = Color.Black,
        surface = Color(0xFF050505),
        onBackground = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF0C1A28),
        divider = Color(0xFF242426),
        subtleText = Color(0xFF98989D),
        mediaBackground = Color(0xFF0E0E10),
        inputBackground = Color(0xFF111113),
        cardBackground = Color(0xFF050505),
    ),
)

private val HhhlGreenPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF78A000),
        background = Color(0xFFFAFCF7),
        onBackground = Color(0xFF10140A),
        secondary = Color(0xFF536238),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFEAF4D4),
        divider = Color(0xFFDDE7CB),
        subtleText = Color(0xFF536238),
        mediaBackground = Color(0xFFF1F6E8),
        inputBackground = Color(0xFFF3F7EC),
    ),
)

private val HhhlDarkGreenPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFFA7D23F),
        background = Color(0xFF0F130B),
        surface = Color(0xFF151B10),
        onBackground = Color(0xFFE9EFD9),
        secondary = Color(0xFFB6C39A),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF202B13),
        divider = Color(0xFF2E3A1E),
        subtleText = Color(0xFFB6C39A),
        mediaBackground = Color(0xFF18200F),
        inputBackground = Color(0xFF1B2412),
        cardBackground = Color(0xFF151B10),
    ),
)

val LocalHhhlColors = staticCompositionLocalOf {
    HhhlColors(
        divider = Color(0xFFE6ECF0),
        subtleText = Color(0xFF536471),
        avatarBackground = Color(0xFFE8F5FE),
        mediaBackground = Color(0xFFF2F6F8),
        inputBackground = Color(0xFFF2F6F8),
        cardBackground = Color.White,
        badgeBackground = Color(0xFFE8F5FE),
    )
}

@Composable
fun HhhlTheme(
    preset: HhhlThemePreset = HhhlThemePreset.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val effectivePreset = when (preset) {
        HhhlThemePreset.System -> if (systemDark) HhhlThemePreset.Dark else HhhlThemePreset.Light
        else -> preset
    }
    val palette = paletteFor(effectivePreset)
    val scheme: ColorScheme = palette.scheme
    val extra = palette.colors

    androidx.compose.runtime.CompositionLocalProvider(LocalHhhlColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HhhlTypography,
            shapes = HhhlShapes,
            content = content,
        )
    }
}

private fun paletteFor(preset: HhhlThemePreset): HhhlPalette {
    return when (preset) {
        HhhlThemePreset.Light -> LightPalette
        HhhlThemePreset.Dark -> DarkPalette
        HhhlThemePreset.Dim -> DimPalette
        HhhlThemePreset.XBlue -> XBluePalette
        HhhlThemePreset.XPurple -> XPurplePalette
        HhhlThemePreset.XPink -> XPinkPalette
        HhhlThemePreset.XOrange -> XOrangePalette
        HhhlThemePreset.XDarkBlue -> XDarkBluePalette
        HhhlThemePreset.XDarkPurple -> XDarkPurplePalette
        HhhlThemePreset.XDarkPink -> XDarkPinkPalette
        HhhlThemePreset.XDarkOrange -> XDarkOrangePalette
        HhhlThemePreset.AppleLight -> AppleLightPalette
        HhhlThemePreset.AppleDark -> AppleDarkPalette
        HhhlThemePreset.AppleMint -> AppleMintPalette
        HhhlThemePreset.Graphite -> GraphitePalette
        HhhlThemePreset.OledBlack -> OledBlackPalette
        HhhlThemePreset.HhhlGreen -> HhhlGreenPalette
        HhhlThemePreset.HhhlDarkGreen -> HhhlDarkGreenPalette
        HhhlThemePreset.System -> LightPalette
    }
}
