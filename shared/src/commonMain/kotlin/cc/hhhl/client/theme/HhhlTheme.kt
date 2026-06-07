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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val HhhlTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp),
    )
}

private val HhhlShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

data class HhhlColors(
    val mediaBackground: Color,
    val avatarBackground: Color,
    val badgeBackground: Color,
    val inputBackground: Color,
    val pageBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val panelBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInverse: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerText: Color,
    val border: Color,
    val focusRing: Color,
    val inputBorder: Color,
    val inputFocusedBorder: Color,
    val buttonBackground: Color,
    val buttonSelectedBackground: Color,
    val chipBackground: Color,
    val chipSelectedBackground: Color,
    val topBarBackground: Color,
    val bottomNavBackground: Color,
    val bottomNavSelected: Color,
    val chatBackground: Color,
    val chatIncomingBubble: Color,
    val chatOutgoingBubble: Color,
    val chatIncomingText: Color,
    val chatOutgoingText: Color,
    val chatBubbleBorder: Color,
    val chatComposerBackground: Color,
    val chatMentionHighlight: Color,
    val noteBackground: Color,
    val noteActionBackground: Color,
    val noteReactionBackground: Color,
    val noteTreeLine: Color,
    val quoteBackground: Color,
    val unreadBadge: Color,
    val overlayScrim: Color,
    val shadow: Color,
    val toastBackground: Color,
    val toastText: Color,
    val rankBronze: Color,
    val rankSilver: Color,
    val rankGold: Color,
    val rankPlatinum: Color,
    val richTextRainbowColors: List<Color>,
)

private data class HhhlPalette(
    val scheme: ColorScheme,
    val colors: HhhlColors,
)

private val DefaultRichTextRainbowColors = listOf(
    Color(0xFFFF2D2D),
    Color(0xFFFF8A00),
    Color(0xFFFFD400),
    Color(0xFF3FE000),
    Color(0xFF00A7FF),
    Color(0xFF7A5CFF),
    Color(0xFFFF4FD8),
)

private fun HhhlPalette.withSchemeAccent(): HhhlPalette {
    val accent = scheme.primary
    val previousAccent = colors.accent
    return copy(
        colors = colors.copy(
            accent = accent,
            focusRing = colors.focusRing.reTintIfAccentDerived(previousAccent, accent),
            buttonBackground = colors.buttonBackground.reTintIfAccentDerived(previousAccent, accent),
            buttonSelectedBackground = colors.buttonSelectedBackground.reTintIfAccentDerived(previousAccent, accent),
            chipBackground = colors.chipBackground.reTintIfAccentDerived(previousAccent, accent),
            chipSelectedBackground = colors.chipSelectedBackground.reTintIfAccentDerived(previousAccent, accent),
            chatOutgoingBubble = colors.chatOutgoingBubble.reTintIfAccentDerived(previousAccent, accent),
            noteActionBackground = colors.noteActionBackground.reTintIfAccentDerived(previousAccent, accent),
            noteReactionBackground = colors.noteReactionBackground.reTintIfAccentDerived(previousAccent, accent),
            rankGold = colors.rankGold.reTintIfAccentDerived(previousAccent, accent),
        ),
    )
}

private fun Color.reTintIfAccentDerived(sourceAccent: Color, targetAccent: Color): Color {
    val sameAccentRgb = red == sourceAccent.red && green == sourceAccent.green && blue == sourceAccent.blue
    return if (sameAccentRgb) targetAccent.copy(alpha = alpha) else this
}

private fun modernLightScheme(
    primary: Color,
    background: Color = Color(0xFFF6F8FA),
    surface: Color = Color.White,
    surfaceVariant: Color = Color(0xFFF1F4F7),
    surfaceContainerHighest: Color = Color(0xFFEAF0F5),
    onBackground: Color = Color(0xFF0F1419),
    secondary: Color = Color(0xFF53606D),
    outline: Color = Color(0xFFD9E0E7),
    outlineVariant: Color = Color(0xFFE6ECF1),
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant,
    surfaceContainer = surface,
    surfaceContainerLow = background,
    surfaceContainerHighest = surfaceContainerHighest,
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = secondary,
    secondary = secondary,
    outline = outline,
    outlineVariant = outlineVariant,
)

private fun modernDarkScheme(
    primary: Color,
    background: Color = Color.Black,
    surface: Color = Color(0xFF090B0F),
    surfaceVariant: Color = Color(0xFF10141B),
    surfaceContainerHighest: Color = Color(0xFF171C24),
    onBackground: Color = Color(0xFFE7E9EA),
    secondary: Color = Color(0xFF94A1AF),
    outline: Color = Color(0xFF29313A),
    outlineVariant: Color = Color(0xFF1C232B),
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant,
    surfaceContainer = surface,
    surfaceContainerLow = background,
    surfaceContainerHighest = surfaceContainerHighest,
    onBackground = onBackground,
    onSurface = onBackground,
    onSurfaceVariant = secondary,
    secondary = secondary,
    outline = outline,
    outlineVariant = outlineVariant,
)

private fun modernLightColors(
    accent: Color = Color(0xFF1D9BF0),
    accentTint: Color = Color(0xFFE7F3FF),
    pageBackground: Color = Color(0xFFF3F6FA),
    surface: Color = Color.White,
    surfaceElevated: Color = Color.White,
    panelBackground: Color = surface.copy(alpha = 0.94f),
    textPrimary: Color = Color(0xFF0F1419),
    textSecondary: Color = Color(0xFF53606D),
    lineColor: Color = Color(0xFFE4EAF0),
    mutedText: Color = Color(0xFF66727F),
    mediaBackground: Color = Color(0xFFF0F4F8),
    inputBackground: Color = Color(0xFFF1F4F7),
    noteSurface: Color = Color.White,
): HhhlColors = buildHhhlColors(
    pageBackground = pageBackground,
    surface = surface,
    surfaceElevated = surfaceElevated,
    panelBackground = panelBackground,
    noteSurface = noteSurface,
    textPrimary = textPrimary,
    textSecondary = textSecondary,
    textMuted = mutedText,
    textInverse = Color.White,
    accent = accent,
    accentSoft = accentTint,
    border = lineColor,
    mediaBackground = mediaBackground,
    inputBackground = inputBackground,
)

private fun modernDarkColors(
    accent: Color = Color(0xFF1D9BF0),
    accentTint: Color = Color(0xFF0D2132),
    pageBackground: Color = Color(0xFF05070A),
    surface: Color = Color(0xFF0B0F14),
    surfaceElevated: Color = Color(0xFF151B24),
    panelBackground: Color = surfaceElevated.copy(alpha = 0.92f),
    textPrimary: Color = Color(0xFFE7E9EA),
    textSecondary: Color = Color(0xFF94A1AF),
    lineColor: Color = Color(0xFF232B34),
    mutedText: Color = Color(0xFF7F8B99),
    mediaBackground: Color = Color(0xFF0E131A),
    inputBackground: Color = Color(0xFF111720),
    noteSurface: Color = surface,
): HhhlColors = buildHhhlColors(
    pageBackground = pageBackground,
    surface = surface,
    surfaceElevated = surfaceElevated,
    panelBackground = panelBackground,
    noteSurface = noteSurface,
    textPrimary = textPrimary,
    textSecondary = textSecondary,
    textMuted = mutedText,
    textInverse = Color.White,
    accent = accent,
    accentSoft = accentTint,
    border = lineColor,
    mediaBackground = mediaBackground,
    inputBackground = inputBackground,
)

private fun buildHhhlColors(
    pageBackground: Color,
    surface: Color,
    surfaceElevated: Color,
    panelBackground: Color,
    noteSurface: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    textInverse: Color,
    accent: Color,
    accentSoft: Color,
    border: Color,
    mediaBackground: Color,
    inputBackground: Color,
    avatarBackground: Color = accentSoft,
    badgeBackground: Color = accentSoft,
    success: Color = Color(0xFF34C759),
    warning: Color = Color(0xFFFFB020),
    danger: Color = Color(0xFFFF453A),
    unreadBadge: Color = Color(0xFFFF3B30),
): HhhlColors = HhhlColors(
    mediaBackground = mediaBackground,
    avatarBackground = avatarBackground,
    badgeBackground = badgeBackground,
    inputBackground = inputBackground,
    pageBackground = pageBackground,
    surface = surface,
    surfaceElevated = surfaceElevated,
    panelBackground = panelBackground,
    textPrimary = textPrimary,
    textSecondary = textSecondary,
    textMuted = textMuted,
    textInverse = textInverse,
    accent = accent,
    accentSoft = accentSoft,
    success = success,
    warning = warning,
    danger = danger,
    dangerText = Color.White,
    border = border,
    focusRing = accent.copy(alpha = 0.32f),
    inputBorder = border.copy(alpha = 0.64f),
    inputFocusedBorder = accent.copy(alpha = 0.30f),
    buttonBackground = surfaceElevated.blendWith(accent, if (pageBackground.luminance() < 0.45f) 0.10f else 0.05f),
    buttonSelectedBackground = accent.copy(alpha = if (pageBackground.luminance() < 0.45f) 0.24f else 0.16f),
    chipBackground = surfaceElevated.blendWith(accent, if (pageBackground.luminance() < 0.45f) 0.08f else 0.04f),
    chipSelectedBackground = accent.copy(alpha = if (pageBackground.luminance() < 0.45f) 0.22f else 0.15f),
    topBarBackground = panelBackground,
    bottomNavBackground = panelBackground,
    bottomNavSelected = accentSoft,
    chatBackground = Color.Transparent,
    chatIncomingBubble = surfaceElevated,
    chatOutgoingBubble = accent,
    chatIncomingText = textPrimary,
    chatOutgoingText = textInverse,
    chatBubbleBorder = border.copy(alpha = if (pageBackground.luminance() < 0.45f) 0.24f else 0.36f),
    chatComposerBackground = inputBackground,
    chatMentionHighlight = accentSoft,
    noteBackground = noteSurface,
    noteActionBackground = accent.copy(alpha = if (pageBackground.luminance() < 0.45f) 0.09f else 0.045f),
    noteReactionBackground = accent.copy(alpha = if (pageBackground.luminance() < 0.45f) 0.14f else 0.08f),
    noteTreeLine = border.copy(alpha = 0.86f),
    quoteBackground = inputBackground.copy(alpha = 0.72f),
    unreadBadge = unreadBadge,
    overlayScrim = Color.Black.copy(alpha = 0.56f),
    shadow = if (pageBackground.luminance() < 0.45f) {
        Color.Black.copy(alpha = 0.56f)
    } else {
        Color.Black.copy(alpha = 0.18f)
    },
    toastBackground = if (pageBackground.luminance() < 0.45f) {
        Color.White.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.86f)
    },
    toastText = if (pageBackground.luminance() < 0.45f) {
        Color.Black
    } else {
        Color.White
    },
    rankBronze = Color(0xFFB87333),
    rankSilver = textSecondary,
    rankGold = accent,
    rankPlatinum = danger,
    richTextRainbowColors = DefaultRichTextRainbowColors,
)

private val LightPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF1D9BF0),
        background = Color(0xFFF6F8FA),
        surface = Color.White,
        onBackground = Color(0xFF0F1419),
        secondary = Color(0xFF53606D),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE7F3FF),
        lineColor = Color(0xFFE2E8EF),
        mutedText = Color(0xFF66727F),
        mediaBackground = Color(0xFFEEF3F8),
        inputBackground = Color(0xFFF1F4F7),
    ),
)

private val DarkPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF1D9BF0),
        background = Color.Black,
        surface = Color(0xFF080A0E),
        surfaceVariant = Color(0xFF101720),
        surfaceContainerHighest = Color(0xFF151C26),
        onBackground = Color(0xFFE7E9EA),
        secondary = Color(0xFF94A1AF),
        outline = Color(0xFF25303A),
        outlineVariant = Color(0xFF1A222C),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF0B2134),
        lineColor = Color(0xFF232C36),
        mutedText = Color(0xFF7F8B99),
        mediaBackground = Color(0xFF0D1219),
        inputBackground = Color(0xFF111720),
        noteSurface = Color(0xFF080A0E),
    ),
)

private val DimPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF7DB6FF),
        background = Color(0xFF11161D),
        surface = Color(0xFF171D26),
        surfaceVariant = Color(0xFF1B2330),
        surfaceContainerHighest = Color(0xFF202A36),
        onBackground = Color(0xFFE7ECF2),
        secondary = Color(0xFFA0ADBA),
        outline = Color(0xFF2B3744),
        outlineVariant = Color(0xFF222C37),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF172B43),
        pageBackground = Color(0xFF11161D),
        surface = Color(0xFF171D26),
        surfaceElevated = Color(0xFF202A36),
        lineColor = Color(0xFF2B3744),
        mutedText = Color(0xFFA0ADBA),
        mediaBackground = Color(0xFF171F2A),
        inputBackground = Color(0xFF1B2430),
        noteSurface = Color(0xFF171D26),
    ),
)

private val XBluePalette = LightPalette

private val XPurplePalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF7B61FF),
        background = Color(0xFFF7F6FF),
        onBackground = Color(0xFF141221),
        secondary = Color(0xFF615B73),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFEFEBFF),
        pageBackground = Color(0xFFF7F6FF),
        textPrimary = Color(0xFF141221),
        textSecondary = Color(0xFF615B73),
        lineColor = Color(0xFFE5E1F2),
        mutedText = Color(0xFF746D86),
        mediaBackground = Color(0xFFF0EEFA),
        inputBackground = Color(0xFFF2F0FA),
    ),
)

private val XPinkPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFFF91880),
        background = Color(0xFFFFF7FB),
        onBackground = Color(0xFF171217),
        secondary = Color(0xFF695B63),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFFFE7F2),
        pageBackground = Color(0xFFFFF7FB),
        textPrimary = Color(0xFF171217),
        textSecondary = Color(0xFF695B63),
        lineColor = Color(0xFFF1E2E9),
        mutedText = Color(0xFF7C6B75),
        mediaBackground = Color(0xFFF9EEF4),
        inputBackground = Color(0xFFFAF1F6),
    ),
)

private val XOrangePalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFFFF7A00),
        background = Color(0xFFF9F8F5),
        onBackground = Color(0xFF17130E),
        secondary = Color(0xFF665F56),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFFFF0DE),
        pageBackground = Color(0xFFF9F8F5),
        textPrimary = Color(0xFF17130E),
        textSecondary = Color(0xFF665F56),
        lineColor = Color(0xFFECE4D8),
        mutedText = Color(0xFF776F65),
        mediaBackground = Color(0xFFF3F0EA),
        inputBackground = Color(0xFFF5F1EB),
    ),
)

private val XDarkBluePalette = DarkPalette

private val XDarkPurplePalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFF8B5CF6)),
    colors = modernDarkColors(
        accentTint = Color(0xFF1A1730),
        lineColor = Color(0xFF272C3A),
        mediaBackground = Color(0xFF0E121A),
        inputBackground = Color(0xFF121722),
        noteSurface = Color(0xFF080A0E),
    ),
)

private val XDarkPinkPalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFF91880)),
    colors = modernDarkColors(
        accentTint = Color(0xFF29111E),
        lineColor = Color(0xFF2B2630),
        mediaBackground = Color(0xFF0F1118),
        inputBackground = Color(0xFF141720),
        noteSurface = Color(0xFF080A0E),
    ),
)

private val XDarkOrangePalette = HhhlPalette(
    scheme = modernDarkScheme(primary = Color(0xFFFF8A00)),
    colors = modernDarkColors(
        accentTint = Color(0xFF2A1A0C),
        lineColor = Color(0xFF302B24),
        mediaBackground = Color(0xFF0F1218),
        inputBackground = Color(0xFF141820),
        noteSurface = Color(0xFF080A0E),
    ),
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
        lineColor = Color(0xFFE5E5EA),
        mutedText = Color(0xFF6E6E73),
        mediaBackground = Color(0xFFF1F1F4),
        inputBackground = Color(0xFFF2F2F7),
    ),
)

private val AppleDarkPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF0A84FF),
        background = Color(0xFF050506),
        surface = Color(0xFF111114),
        surfaceVariant = Color(0xFF1C1C1E),
        surfaceContainerHighest = Color(0xFF242428),
        onBackground = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
        outline = Color(0xFF2C2C30),
        outlineVariant = Color(0xFF202024),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF071B31),
        pageBackground = Color(0xFF050506),
        surface = Color(0xFF111114),
        surfaceElevated = Color(0xFF1C1C1E),
        panelBackground = Color(0xFF111114).copy(alpha = 0.90f),
        lineColor = Color(0xFF2B2B2F),
        mutedText = Color(0xFF98989D),
        mediaBackground = Color(0xFF111114),
        inputBackground = Color(0xFF1A1A1E),
        noteSurface = Color(0xFF111114),
    ),
)

private val AppleMintPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF00C7BE),
        background = Color(0xFFF5F8F8),
        onBackground = Color(0xFF1D1D1F),
        secondary = Color(0xFF5D6C70),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE1F7F6),
        pageBackground = Color(0xFFF5F8F8),
        lineColor = Color(0xFFE0E8EA),
        mutedText = Color(0xFF5D6C70),
        mediaBackground = Color(0xFFEEF4F5),
        inputBackground = Color(0xFFF0F5F6),
    ),
)

private val TgClassicPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF3390EC),
        background = Color(0xFFF4F8FB),
        surface = Color.White,
        onBackground = Color(0xFF17212B),
        secondary = Color(0xFF6B7F8F),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE7F2FD),
        lineColor = Color(0xFFDCE8F1),
        mutedText = Color(0xFF6B7F8F),
        mediaBackground = Color(0xFFEFF5F9),
        inputBackground = Color(0xFFEFF5F9),
        noteSurface = Color.White,
    ).copy(
        chatIncomingBubble = Color.White,
        chatOutgoingBubble = Color(0xFF3390EC),
        chatOutgoingText = Color.White,
        chatBubbleBorder = Color(0xFFD9E5EF).copy(alpha = 0.42f),
        chatComposerBackground = Color.White,
        bottomNavBackground = Color.White.copy(alpha = 0.88f),
        topBarBackground = Color.White.copy(alpha = 0.88f),
    ),
)

private val TgIcePalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF2AABEE),
        background = Color(0xFFF1F7FC),
        surface = Color(0xFFFEFFFF),
        onBackground = Color(0xFF122231),
        secondary = Color(0xFF66839A),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE1F3FD),
        lineColor = Color(0xFFD8EAF5),
        mutedText = Color(0xFF66839A),
        mediaBackground = Color(0xFFEAF4FA),
        inputBackground = Color(0xFFEAF4FA),
        noteSurface = Color(0xFFFEFFFF),
    ).copy(
        chatIncomingBubble = Color(0xFFFEFFFF),
        chatOutgoingBubble = Color(0xFF2AABEE),
        chatOutgoingText = Color.White,
        chatBubbleBorder = Color(0xFFD3E7F3).copy(alpha = 0.42f),
        chatComposerBackground = Color.White,
    ),
)

private val TgNightPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF58A6FF),
        background = Color(0xFF121A24),
        surface = Color(0xFF182331),
        surfaceVariant = Color(0xFF1D2A39),
        surfaceContainerHighest = Color(0xFF223246),
        onBackground = Color(0xFFE6EEF5),
        secondary = Color(0xFF94A8B8),
        outline = Color(0xFF2B3B4B),
        outlineVariant = Color(0xFF233243),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF16324B),
        pageBackground = Color(0xFF121A24),
        surface = Color(0xFF182331),
        surfaceElevated = Color(0xFF223246),
        lineColor = Color(0xFF2B3B4B),
        mutedText = Color(0xFF94A8B8),
        mediaBackground = Color(0xFF162130),
        inputBackground = Color(0xFF1D2A39),
        noteSurface = Color(0xFF182331),
    ).copy(
        chatIncomingBubble = Color(0xFF172536),
        chatOutgoingBubble = Color(0xFF2B6EA6),
        chatIncomingText = Color(0xFFE6EEF5),
        chatOutgoingText = Color(0xFFEAF4FF),
        chatBubbleBorder = Color(0xFF2A3B4D).copy(alpha = 0.34f),
        chatComposerBackground = Color(0xFF1B2A3B),
        topBarBackground = Color(0xFF121A24).copy(alpha = 0.90f),
        bottomNavBackground = Color(0xFF121A24).copy(alpha = 0.90f),
    ),
)

private val TgAmoledPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF3390EC),
        background = Color.Black,
        surface = Color(0xFF07090D),
        surfaceVariant = Color(0xFF0E141C),
        surfaceContainerHighest = Color(0xFF141B25),
        onBackground = Color(0xFFE8EEF3),
        secondary = Color(0xFF91A1AF),
        outline = Color(0xFF202A34),
        outlineVariant = Color(0xFF171F29),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF071A2B),
        lineColor = Color(0xFF202A33),
        mutedText = Color(0xFF91A1AF),
        mediaBackground = Color(0xFF0B1118),
        inputBackground = Color(0xFF0E151E),
        noteSurface = Color(0xFF07090D),
    ).copy(
        chatIncomingBubble = Color(0xFF111A22),
        chatOutgoingBubble = Color(0xFF1F4E79),
        chatIncomingText = Color(0xFFE8EEF3),
        chatOutgoingText = Color(0xFFEAF4FF),
        chatBubbleBorder = Color(0xFF1F2D38).copy(alpha = 0.34f),
        chatComposerBackground = Color(0xFF0E151C),
    ),
)

private val GraphitePalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF5E6A75),
        background = Color(0xFFF5F5F7),
        onBackground = Color(0xFF1D1D1F),
        secondary = Color(0xFF6E6E73),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE7EAEE),
        lineColor = Color(0xFFE5E5EA),
        mutedText = Color(0xFF6E6E73),
        mediaBackground = Color(0xFFF1F1F4),
        inputBackground = Color(0xFFF2F2F7),
    ),
)

private val OledBlackPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF0A84FF),
        background = Color.Black,
        surface = Color(0xFF050505),
        surfaceVariant = Color(0xFF111113),
        surfaceContainerHighest = Color(0xFF17171A),
        onBackground = Color(0xFFF5F5F7),
        secondary = Color(0xFF98989D),
        outline = Color(0xFF252529),
        outlineVariant = Color(0xFF1A1A1E),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF0C1A28),
        surface = Color(0xFF050505),
        surfaceElevated = Color(0xFF111113),
        panelBackground = Color(0xFF050505).copy(alpha = 0.90f),
        lineColor = Color(0xFF242426),
        mutedText = Color(0xFF98989D),
        mediaBackground = Color(0xFF0E0E10),
        inputBackground = Color(0xFF111113),
        noteSurface = Color(0xFF050505),
    ),
)

private val HhhlGreenPalette = HhhlPalette(
    scheme = modernLightScheme(
        primary = Color(0xFF00A67E),
        background = Color(0xFFF5F9F8),
        onBackground = Color(0xFF0F1718),
        secondary = Color(0xFF566B70),
    ),
    colors = modernLightColors(
        accentTint = Color(0xFFE3F6F1),
        pageBackground = Color(0xFFF5F9F8),
        textPrimary = Color(0xFF0F1718),
        textSecondary = Color(0xFF566B70),
        lineColor = Color(0xFFDDE9EA),
        mutedText = Color(0xFF65777C),
        mediaBackground = Color(0xFFEEF5F5),
        inputBackground = Color(0xFFF0F6F6),
    ),
)

private val HhhlDarkGreenPalette = HhhlPalette(
    scheme = modernDarkScheme(
        primary = Color(0xFF66A8FF),
        background = Color.Black,
        surface = Color(0xFF090B0F),
        surfaceVariant = Color(0xFF111823),
        surfaceContainerHighest = Color(0xFF172033),
        onBackground = Color(0xFFE7E9EA),
        secondary = Color(0xFF95A2B1),
        outline = Color(0xFF26313D),
        outlineVariant = Color(0xFF1B2430),
    ),
    colors = modernDarkColors(
        accentTint = Color(0xFF10243A),
        surface = Color(0xFF090B0F),
        surfaceElevated = Color(0xFF172033),
        lineColor = Color(0xFF26313D),
        mutedText = Color(0xFF95A2B1),
        mediaBackground = Color(0xFF0D1219),
        inputBackground = Color(0xFF111823),
        noteSurface = Color(0xFF090B0F),
    ),
)

val LocalHhhlColors = staticCompositionLocalOf {
    modernLightColors()
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
    val palette = paletteFor(effectivePreset).withSchemeAccent().withCustomTheme(customTheme)
    val scheme: ColorScheme = palette.scheme
    val extra = palette.colors

    androidx.compose.runtime.CompositionLocalProvider(LocalHhhlColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HhhlTypography,
            shapes = HhhlShapes,
        ) {
            ConfigurePlatformSystemBars(extra)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(extra.pageBackground),
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
                            .background(extra.pageBackground.copy(alpha = 0.68f)),
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
    val accentSoft = customTheme.accentSoftColorHex.toColorOrNull()
    val background = customTheme.backgroundColorHex.toColorOrNull()
    val surface = customTheme.surfaceColorHex.toColorOrNull()
    val surfaceElevated = customTheme.elevatedSurfaceColorHex.toColorOrNull()
    val panelBackground = customTheme.panelBackgroundColorHex.toColorOrNull()
    val inputBackground = customTheme.inputBackgroundColorHex.toColorOrNull()
    val cardSurface = customTheme.cardBackgroundColorHex.toColorOrNull()
    val noteSurface = customTheme.noteBackgroundColorHex.toColorOrNull() ?: cardSurface
    val primaryText = customTheme.primaryTextColorHex.toColorOrNull()
    val secondaryText = customTheme.secondaryTextColorHex.toColorOrNull()
    val mutedText = customTheme.mutedTextColorHex.toColorOrNull()
    val lineColor = customTheme.dividerColorHex.toColorOrNull()
    val border = customTheme.borderColorHex.toColorOrNull()
    val mediaBackground = customTheme.mediaBackgroundColorHex.toColorOrNull()
    val avatarSurface = customTheme.avatarBackgroundColorHex.toColorOrNull()
    val badgeSurface = customTheme.badgeBackgroundColorHex.toColorOrNull()
    val unreadBadge = customTheme.unreadBadgeColorHex.toColorOrNull()
    val success = customTheme.successColorHex.toColorOrNull()
    val warning = customTheme.warningColorHex.toColorOrNull()
    val danger = customTheme.dangerColorHex.toColorOrNull()
    val dangerText = customTheme.dangerTextColorHex.toColorOrNull()
    val textInverse = customTheme.textInverseColorHex.toColorOrNull()
    val focusRing = customTheme.focusRingColorHex.toColorOrNull()
    val inputBorder = customTheme.inputBorderColorHex.toColorOrNull()
    val inputFocusedBorder = customTheme.inputFocusedBorderColorHex.toColorOrNull()
    val toastBackground = customTheme.toastBackgroundColorHex.toColorOrNull()
    val toastText = customTheme.toastTextColorHex.toColorOrNull()
    val rankBronze = customTheme.rankBronzeColorHex.toColorOrNull()
    val rankSilver = customTheme.rankSilverColorHex.toColorOrNull()
    val rankGold = customTheme.rankGoldColorHex.toColorOrNull()
    val rankPlatinum = customTheme.rankPlatinumColorHex.toColorOrNull()
    val buttonBackground = customTheme.buttonBackgroundColorHex.toColorOrNull()
    val buttonSelectedBackground = customTheme.buttonSelectedBackgroundColorHex.toColorOrNull()
    val chipBackground = customTheme.chipBackgroundColorHex.toColorOrNull()
    val chipSelectedBackground = customTheme.chipSelectedBackgroundColorHex.toColorOrNull()
    val topBarBackground = customTheme.topBarBackgroundColorHex.toColorOrNull()
    val bottomNavBackground = customTheme.bottomNavBackgroundColorHex.toColorOrNull()
    val bottomNavSelected = customTheme.bottomNavSelectedColorHex.toColorOrNull()
    val incomingBubble = customTheme.incomingBubbleColorHex.toColorOrNull()
    val outgoingBubble = customTheme.outgoingBubbleColorHex.toColorOrNull()
    val incomingBubbleText = customTheme.incomingBubbleTextColorHex.toColorOrNull()
    val outgoingBubbleText = customTheme.outgoingBubbleTextColorHex.toColorOrNull()
    val chatBubbleBorder = customTheme.chatBubbleBorderColorHex.toColorOrNull()
    val chatComposerBackground = customTheme.chatComposerBackgroundColorHex.toColorOrNull()
    val chatMentionHighlight = customTheme.chatMentionHighlightColorHex.toColorOrNull()
    val noteActionBackground = customTheme.noteActionBackgroundColorHex.toColorOrNull()
    val noteReactionBackground = customTheme.noteReactionBackgroundColorHex.toColorOrNull()
    val noteTreeLine = customTheme.noteTreeLineColorHex.toColorOrNull()
    val quoteBackground = customTheme.quoteBackgroundColorHex.toColorOrNull()
    val overlayScrim = customTheme.overlayScrimColorHex.toColorOrNull()
    val shadow = customTheme.shadowColorHex.toColorOrNull()
    val scheme = this.scheme.copy(
        primary = accent ?: this.scheme.primary,
        background = background ?: this.scheme.background,
        surface = surface ?: cardSurface ?: noteSurface ?: this.scheme.surface,
        surfaceContainer = surface ?: cardSurface ?: noteSurface ?: this.scheme.surfaceContainer,
        surfaceContainerLow = background ?: this.scheme.surfaceContainerLow,
        surfaceContainerHighest = surfaceElevated ?: panelBackground ?: this.scheme.surfaceContainerHighest,
        onBackground = primaryText ?: this.scheme.onBackground,
        onSurface = primaryText ?: this.scheme.onSurface,
        onSurfaceVariant = secondaryText ?: mutedText ?: this.scheme.onSurfaceVariant,
        secondary = secondaryText ?: this.scheme.secondary,
        outline = border ?: lineColor ?: this.scheme.outline,
        outlineVariant = lineColor ?: border ?: this.scheme.outlineVariant,
    )
    val colors = this.colors.copy(
        inputBackground = inputBackground ?: this.colors.inputBackground,
        mediaBackground = mediaBackground ?: background?.copy(alpha = 0.70f) ?: this.colors.mediaBackground,
        pageBackground = background ?: this.colors.pageBackground,
        surface = surface ?: cardSurface ?: this.colors.surface,
        surfaceElevated = surfaceElevated ?: this.colors.surfaceElevated,
        panelBackground = panelBackground ?: this.colors.panelBackground,
        textPrimary = primaryText ?: this.colors.textPrimary,
        textSecondary = secondaryText ?: this.colors.textSecondary,
        textMuted = mutedText ?: secondaryText ?: this.colors.textMuted,
        textInverse = textInverse ?: this.colors.textInverse,
        accent = accent ?: this.colors.accent,
        accentSoft = accentSoft ?: badgeSurface ?: avatarSurface ?: accent?.copy(alpha = 0.14f) ?: this.colors.accentSoft,
        avatarBackground = avatarSurface ?: this.colors.avatarBackground,
        badgeBackground = badgeSurface ?: avatarSurface ?: this.colors.badgeBackground,
        success = success ?: this.colors.success,
        warning = warning ?: this.colors.warning,
        border = border ?: this.colors.border,
        focusRing = focusRing ?: accent?.copy(alpha = 0.32f) ?: this.colors.focusRing,
        inputBorder = inputBorder ?: border?.copy(alpha = 0.64f) ?: lineColor?.copy(alpha = 0.64f) ?: this.colors.inputBorder,
        inputFocusedBorder = inputFocusedBorder ?: accent?.copy(alpha = 0.30f) ?: this.colors.inputFocusedBorder,
        buttonBackground = buttonBackground ?: this.colors.buttonBackground,
        buttonSelectedBackground = buttonSelectedBackground ?: this.colors.buttonSelectedBackground,
        chipBackground = chipBackground ?: this.colors.chipBackground,
        chipSelectedBackground = chipSelectedBackground ?: this.colors.chipSelectedBackground,
        topBarBackground = topBarBackground ?: panelBackground ?: this.colors.topBarBackground,
        bottomNavBackground = bottomNavBackground ?: panelBackground ?: this.colors.bottomNavBackground,
        bottomNavSelected = bottomNavSelected ?: accentSoft ?: badgeSurface ?: this.colors.bottomNavSelected,
        chatBackground = customTheme.chatBackgroundColorHex.toColorOrNull() ?: this.colors.chatBackground,
        chatIncomingBubble = incomingBubble ?: surfaceElevated ?: cardSurface ?: noteSurface ?: this.colors.chatIncomingBubble,
        chatOutgoingBubble = outgoingBubble ?: accent ?: this.colors.chatOutgoingBubble,
        chatIncomingText = incomingBubbleText ?: primaryText ?: this.colors.chatIncomingText,
        chatOutgoingText = outgoingBubbleText ?: textInverse ?: this.colors.chatOutgoingText,
        chatBubbleBorder = chatBubbleBorder ?: border?.copy(alpha = 0.55f) ?: this.colors.chatBubbleBorder,
        chatComposerBackground = chatComposerBackground ?: inputBackground ?: this.colors.chatComposerBackground,
        chatMentionHighlight = chatMentionHighlight ?: accentSoft ?: badgeSurface ?: accent?.copy(alpha = 0.14f) ?: this.colors.chatMentionHighlight,
        noteBackground = noteSurface ?: cardSurface ?: this.colors.noteBackground,
        noteActionBackground = noteActionBackground ?: this.colors.noteActionBackground,
        noteReactionBackground = noteReactionBackground ?: this.colors.noteReactionBackground,
        noteTreeLine = noteTreeLine ?: border ?: lineColor ?: this.colors.noteTreeLine,
        quoteBackground = quoteBackground ?: inputBackground?.copy(alpha = 0.72f) ?: this.colors.quoteBackground,
        unreadBadge = unreadBadge ?: this.colors.unreadBadge,
        overlayScrim = overlayScrim ?: this.colors.overlayScrim,
        shadow = shadow ?: this.colors.shadow,
        danger = danger ?: this.colors.danger,
        dangerText = dangerText ?: this.colors.dangerText,
        toastBackground = toastBackground ?: this.colors.toastBackground,
        toastText = toastText ?: this.colors.toastText,
        rankBronze = rankBronze ?: this.colors.rankBronze,
        rankSilver = rankSilver ?: secondaryText ?: this.colors.rankSilver,
        rankGold = rankGold ?: accent ?: this.colors.rankGold,
        rankPlatinum = rankPlatinum ?: danger ?: this.colors.rankPlatinum,
    )
    return HhhlPalette(scheme = scheme, colors = colors)
}

internal fun String.toColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    val alpha = if (clean.length == 8) ((value shr 24) and 0xFF) / 255f else 1f
    val red = ((value shr 16) and 0xFF) / 255f
    val green = ((value shr 8) and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

@Composable
internal expect fun ConfigurePlatformSystemBars(colors: HhhlColors)

private fun Color.blendWith(other: Color, otherRatio: Float): Color {
    val ratio = otherRatio.coerceIn(0f, 1f)
    val selfRatio = 1f - ratio
    return Color(
        red = red * selfRatio + other.red * ratio,
        green = green * selfRatio + other.green * ratio,
        blue = blue * selfRatio + other.blue * ratio,
        alpha = alpha * selfRatio + other.alpha * ratio,
    )
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
        HhhlThemePreset.TgClassic -> TgClassicPalette
        HhhlThemePreset.TgIce -> TgIcePalette
        HhhlThemePreset.TgNight -> TgNightPalette
        HhhlThemePreset.TgAmoled -> TgAmoledPalette
        HhhlThemePreset.Graphite -> GraphitePalette
        HhhlThemePreset.OledBlack -> OledBlackPalette
        HhhlThemePreset.HhhlGreen -> HhhlGreenPalette
        HhhlThemePreset.HhhlDarkGreen -> HhhlDarkGreenPalette
        HhhlThemePreset.System -> LightPalette
    }
}
