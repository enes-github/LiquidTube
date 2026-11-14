@file:OptIn(ExperimentalTextApi::class)

package com.enes.liquidtube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enes.liquidtube.R
import com.enes.liquidtube.ui.glass.GlassStyle
import androidx.compose.ui.unit.dp

// ----------------------------------------------------------------- colors

val AppleRed = Color(0xFFFA2D48)

/** Accent choices (Settings → Accent Color). */
val AccentPalette = listOf(
    "Red" to AppleRed,
    "Pink" to Color(0xFFFF375F),
    "Orange" to Color(0xFFFF9F0A),
    "Green" to Color(0xFF30D158),
    "Teal" to Color(0xFF40C8E0),
    "Blue" to Color(0xFF0A84FF),
    "Purple" to Color(0xFFBF5AF2),
)

/** Settings → Accent Color: the extra choice after the palette, taken from the playing cover. */
val ACCENT_ARTWORK = AccentPalette.size

/** Whether Liquid Glass bends light at its edges (Settings → Glass Refraction). */
val LocalGlassRefraction = androidx.compose.runtime.compositionLocalOf { true }

/** iOS system colors (semantic), so light and dark both read correctly. */
@Immutable
data class AppColors(
    val background: Color,
    val elevated: Color,
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val chrome: Color,
    val accent: Color,
    val isDark: Boolean,
)

private val DarkColors = AppColors(
    background = Color(0xFF000000),
    elevated = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    label = Color(0xEBFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    chrome = Color(0xB3161618),
    accent = AppleRed,
    isDark = true,
)

private val LightColors = AppColors(
    background = Color(0xFFFFFFFF),
    elevated = Color(0xFFF2F2F7),
    fill = Color(0x1F767680),
    label = Color(0xE6000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    chrome = Color(0xBFF9F9F9),
    accent = AppleRed,
    isDark = false,
)

private val LocalAppColors = staticCompositionLocalOf { DarkColors }

// ------------------------------------------------------------- typography

// Inter (variable) — the closest freely-licensed match for SF Pro. The opsz axis
// gives tighter "Display" letterforms at large-title sizes, like SF Pro Display.
private fun inter(weight: Int, opticalSize: Float) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

val InterText = FontFamily(inter(400, 14f), inter(500, 14f), inter(600, 14f), inter(700, 14f))
val InterDisplay = FontFamily(inter(600, 32f), inter(700, 32f), inter(800, 32f))

/** The iOS Dynamic Type scale (default size), tracking tuned for Inter. */
object AppType {
    val largeTitle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.8).sp)
    val title1 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp)
    val title2 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.45).sp)
    val title3 = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.45).sp)
    val headline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp)
    val body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp)
    val callout = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp)
    val subhead = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.25).sp)
    val footnote = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp)
    val caption = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
    val caption2 = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.1.sp)
}

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
    val type = AppType
}

@Composable
fun LiquidTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = AppleRed,
    glassRefraction: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = (if (darkTheme) DarkColors else LightColors).copy(accent = accent)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, background = c.background, surface = c.background,
            onBackground = c.label, onSurface = c.label, surfaceVariant = c.elevated,
            onSurfaceVariant = c.secondaryLabel, outline = c.separator,
        )
    } else {
        lightColorScheme(
            primary = c.accent, background = c.background, surface = c.background,
            onBackground = c.label, onSurface = c.label, surfaceVariant = c.elevated,
            onSurfaceVariant = c.secondaryLabel, outline = c.separator,
        )
    }
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = InterText),
        bodyMedium = base.bodyMedium.copy(fontFamily = InterText),
        bodySmall = base.bodySmall.copy(fontFamily = InterText),
        labelLarge = base.labelLarge.copy(fontFamily = InterText),
        labelMedium = base.labelMedium.copy(fontFamily = InterText),
        titleMedium = base.titleMedium.copy(fontFamily = InterText),
    )
    CompositionLocalProvider(LocalAppColors provides c, LocalGlassRefraction provides glassRefraction) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Liquid Glass material for floating chrome (tab bar, mini-player, search button). */
fun chromeGlass(dark: Boolean) = if (dark) {
    // iOS 26 dark glass is a lifted, see-through grey, not a black slab with an outline.
    GlassStyle(
        tint = Color(0x8A2A2A2E), blur = 16.dp, refraction = 18.dp, band = 22.dp,
        sheen = 0.05f, rim = 0.42f, edge = Color.White.copy(alpha = 0.045f),
    )
} else {
    GlassStyle(
        tint = Color(0xC2FBFBFD), blur = 14.dp, refraction = 18.dp, band = 22.dp,
        sheen = 0.25f, rim = 1f, edge = Color.Black.copy(alpha = 0.10f),
    )
}

/** Frosted material for context menus and sheets: heavy blur, light lens, soft rim. */
fun menuGlass(dark: Boolean) = if (dark) {
    GlassStyle(
        tint = Color(0x94262629), blur = 34.dp, refraction = 10.dp, band = 18.dp, chroma = 0.06f,
        sheen = 0.04f, rim = 0.32f, edge = Color.White.copy(alpha = 0.06f),
    )
} else {
    GlassStyle(
        tint = Color(0xC7F6F6F8), blur = 34.dp, refraction = 10.dp, band = 18.dp, chroma = 0.06f,
        sheen = 0.2f, rim = 0.9f, edge = Color.Black.copy(alpha = 0.06f),
    )
}

/** Glass for small buttons that float over page content (back, •••, download). */
fun buttonGlass(dark: Boolean) = if (dark) {
    GlassStyle(
        tint = Color(0x732C2C30), blur = 10.dp, refraction = 10.dp, band = 14.dp,
        sheen = 0.05f, rim = 0.45f, edge = Color.White.copy(alpha = 0.05f),
    )
} else {
    GlassStyle(
        tint = Color(0xB3FFFFFF), blur = 10.dp, refraction = 10.dp, band = 14.dp,
        sheen = 0.25f, rim = 1f, edge = Color.Black.copy(alpha = 0.08f),
    )
}
