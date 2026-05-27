package cc.hhhl.client.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val HhhlTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp),
        bodySmall = bodySmall.copy(lineHeight = 17.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp),
    )
}

private val HhhlShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
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
    surface: Color = Color(0xFF0B0D10),
    onBackground: Color = Color(0xFFE7E9EA),
    secondary: Color = Color(0xFF8B98A5),
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = background,
    surface = surface,
    surfaceVariant = Color(0xFF101418),
    surfaceContainer = surface,
    surfaceContainerLow = background,
    surfaceContainerHighest = Color(0xFF171B20),
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
    accentTint: Color = Color(0xFF111820),
    divider: Color = Color(0xFF242A30),
    subtleText: Color = Color(0xFF8B98A5),
    mediaBackground: Color = Color(0xFF0D1115),
    inputBackground: Color = Color(0xFF101419),
    cardBackground: Color = Color(0xFF0B0D10),
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
        background = Color(0xFF0F141A),
        surface = Color(0xFF151B23),
        onBackground = Color(0xFFE7EAF0),
        secondary = Color(0xFFAAB4C0),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF162234),
        divider = Color(0xFF26303A),
        subtleText = Color(0xFFAAB4C0),
        mediaBackground = Color(0xFF151B23),
        inputBackground = Color(0xFF19212A),
        cardBackground = Color(0xFF151B23),
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
    colors = modernDarkColors(accentTint = Color(0xFF171620)),
)

private val XDarkPinkPalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFF91880)),
    colors = modernDarkColors(accentTint = Color(0xFF1D151A)),
)

private val XDarkOrangePalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFFF7A00)),
    colors = modernDarkColors(accentTint = Color(0xFF1F1710)),
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
        background = Color(0xFF050506),
        surface = Color(0xFF111114),
        onBackground = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF071B31),
        divider = Color(0xFF2B2B2F),
        subtleText = Color(0xFF98989D),
        mediaBackground = Color(0xFF111114),
        inputBackground = Color(0xFF1A1A1E),
        cardBackground = Color(0xFF111114),
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
        background = Color.Black,
        surface = Color(0xFF0B0D10),
        onBackground = Color(0xFFE7E9EA),
        secondary = Color(0xFF8B98A5),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF182011),
        divider = Color(0xFF242A30),
        subtleText = Color(0xFF8B98A5),
        mediaBackground = Color(0xFF0D1115),
        inputBackground = Color(0xFF101419),
        cardBackground = Color(0xFF0B0D10),
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
    customTheme: HhhlCustomTheme = HhhlCustomTheme(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val effectivePreset = when (preset) {
        HhhlThemePreset.System -> if (systemDark) HhhlThemePreset.Dark else HhhlThemePreset.Light
        else -> preset
    }
    val palette = paletteFor(effectivePreset).withCustomTheme(customTheme)
    val scheme: ColorScheme = palette.scheme
    val extra = palette.colors

    androidx.compose.runtime.CompositionLocalProvider(LocalHhhlColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HhhlTypography,
            shapes = HhhlShapes,
        ) {
            ConfigurePlatformSystemBars(scheme)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeRootBackgroundBrush(scheme, effectivePreset.isDarkSurfaceTheme())),
            ) {
                if (customTheme.globalBackgroundImageDataUri.isNotBlank()) {
                    AsyncImage(
                        model = customTheme.globalBackgroundImageDataUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scheme.background.copy(alpha = 0.68f)),
                    )
                }
                content()
            }
        }
    }
}

private fun HhhlPalette.withCustomTheme(customTheme: HhhlCustomTheme): HhhlPalette {
    if (!customTheme.enabled) return this
    val accent = customTheme.accentColorHex.toColorOrNull()
    val background = customTheme.backgroundColorHex.toColorOrNull()
    val inputBackground = customTheme.inputBackgroundColorHex.toColorOrNull()
    val cardBackground = customTheme.cardBackgroundColorHex.toColorOrNull()
    val scheme = this.scheme.copy(
        primary = accent ?: this.scheme.primary,
        background = background ?: this.scheme.background,
        surfaceContainerLow = background ?: this.scheme.surfaceContainerLow,
    )
    val colors = this.colors.copy(
        avatarBackground = accent?.copy(alpha = 0.14f) ?: this.colors.avatarBackground,
        badgeBackground = accent?.copy(alpha = 0.14f) ?: this.colors.badgeBackground,
        inputBackground = inputBackground ?: this.colors.inputBackground,
        cardBackground = cardBackground ?: this.colors.cardBackground,
        mediaBackground = background?.copy(alpha = 0.70f) ?: this.colors.mediaBackground,
    )
    return HhhlPalette(scheme = scheme, colors = colors)
}

internal fun String.toColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) {
        Color(0xFF000000 or value)
    } else {
        Color(value)
    }
}

@Composable
internal expect fun ConfigurePlatformSystemBars(colorScheme: ColorScheme)

private fun themeRootBackgroundBrush(
    scheme: ColorScheme,
    isDarkSurfaceTheme: Boolean,
): Brush {
    return if (isDarkSurfaceTheme) {
        SolidColor(scheme.background)
    } else {
        Brush.verticalGradient(
            colors = listOf(
                scheme.background,
                scheme.surfaceVariant.copy(alpha = 0.08f),
                scheme.background,
            ),
        )
    }
}

private fun HhhlThemePreset.isDarkSurfaceTheme(): Boolean {
    return when (this) {
        HhhlThemePreset.Dark,
        HhhlThemePreset.Dim,
        HhhlThemePreset.XDarkBlue,
        HhhlThemePreset.XDarkPurple,
        HhhlThemePreset.XDarkPink,
        HhhlThemePreset.XDarkOrange,
        HhhlThemePreset.AppleDark,
        HhhlThemePreset.OledBlack,
        HhhlThemePreset.HhhlDarkGreen,
        -> true
        else -> false
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
