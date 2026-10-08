package com.derekross.markview.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette: "ink & iris". A calm indigo primary with a warm apricot accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4A55C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF00006E),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF8D4F2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF331100),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1A2594),
    primaryContainer = Color(0xFF323DAB),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF532200),
    tertiaryContainer = Color(0xFF703715),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
)

// Sepia: warm paper tones that reduce glare for long sessions.
private val SepiaColors = lightColorScheme(
    primary = Color(0xFF8A5A2B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2DCC0),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF6E5B48),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADBC4),
    onSecondaryContainer = Color(0xFF261909),
    tertiary = Color(0xFF55633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E8B4),
    onTertiaryContainer = Color(0xFF141F00),
    background = Color(0xFFF6EEDD),
    onBackground = Color(0xFF3A2E22),
    surface = Color(0xFFF6EEDD),
    onSurface = Color(0xFF3A2E22),
    surfaceVariant = Color(0xFFE9DDC6),
    onSurfaceVariant = Color(0xFF5A4C3D),
    surfaceContainerLowest = Color(0xFFFBF5E9),
    surfaceContainerLow = Color(0xFFF1E7D3),
    surfaceContainer = Color(0xFFEDE2CC),
    surfaceContainerHigh = Color(0xFFE7DBC3),
    surfaceContainerHighest = Color(0xFFE1D4BA),
    outline = Color(0xFF8C7B67),
    outlineVariant = Color(0xFFD5C5AB),
)

private fun ColorScheme.toBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF1A1A1E),
    surfaceContainerHighest = Color(0xFF232328),
)

/** Extra colors the Material scheme doesn't model, used by the Markdown renderer. */
@Immutable
data class ExtendedColors(
    val isDark: Boolean,
    val codeBackground: Color,
    val note: Color,
    val tip: Color,
    val important: Color,
    val warning: Color,
    val caution: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(false, Color(0xFFF1F0F5), Color(0xFF2F6FDE), Color(0xFF1F8A4C), Color(0xFF8250DF), Color(0xFFB07A00), Color(0xFFCF222E))
}

private fun extendedColors(theme: AppTheme, dark: Boolean, scheme: ColorScheme) = when {
    theme == AppTheme.Sepia -> ExtendedColors(
        isDark = false,
        codeBackground = Color(0xFFEDE1C9),
        note = Color(0xFF3C6E9E), tip = Color(0xFF4F7A2E), important = Color(0xFF7A4E9C),
        warning = Color(0xFFA66A00), caution = Color(0xFFB0412E),
    )
    dark -> ExtendedColors(
        isDark = true,
        codeBackground = if (theme == AppTheme.Black) Color(0xFF111114) else scheme.surfaceContainerHigh,
        note = Color(0xFF6CA4F8), tip = Color(0xFF56D364), important = Color(0xFFB88CF6),
        warning = Color(0xFFE3B341), caution = Color(0xFFF47067),
    )
    else -> ExtendedColors(
        isDark = false,
        codeBackground = scheme.surfaceContainer,
        note = Color(0xFF2F6FDE), tip = Color(0xFF1F8A4C), important = Color(0xFF8250DF),
        warning = Color(0xFF9A6700), caution = Color(0xFFCF222E),
    )
}

private val BaseTypography = Typography()

/** App chrome typography: Inter throughout, with slightly tighter display tracking. */
val MarkviewTypography: Typography = with(BaseTypography) {
    fun TextStyle.inter(weight: FontWeight? = null) = copy(fontFamily = MarkviewFonts.Inter, fontWeight = weight ?: fontWeight)
    Typography(
        displayLarge = displayLarge.inter(FontWeight.SemiBold).copy(letterSpacing = (-1).sp),
        displayMedium = displayMedium.inter(FontWeight.SemiBold).copy(letterSpacing = (-0.5).sp),
        displaySmall = displaySmall.inter(FontWeight.SemiBold),
        headlineLarge = headlineLarge.inter(FontWeight.SemiBold),
        headlineMedium = headlineMedium.inter(FontWeight.SemiBold),
        headlineSmall = headlineSmall.inter(FontWeight.SemiBold),
        titleLarge = titleLarge.inter(FontWeight.SemiBold),
        titleMedium = titleMedium.inter(FontWeight.SemiBold),
        titleSmall = titleSmall.inter(FontWeight.Medium),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(FontWeight.Medium),
        labelMedium = labelMedium.inter(FontWeight.Medium),
        labelSmall = labelSmall.inter(FontWeight.Medium),
    )
}

@Composable
fun AppTheme.isDark(): Boolean = when (this) {
    AppTheme.System -> isSystemInDarkTheme()
    AppTheme.Light, AppTheme.Sepia -> false
    AppTheme.Dark, AppTheme.Black -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkviewTheme(
    theme: AppTheme = AppTheme.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = theme.isDark()
    val context = LocalContext.current
    val useDynamic = dynamicColor && theme != AppTheme.Sepia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        theme == AppTheme.Sepia -> SepiaColors
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = if (theme == AppTheme.Black) base.toBlack() else base

    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extendedColors(theme, dark, scheme)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = MarkviewTypography,
            content = content,
        )
    }
}
