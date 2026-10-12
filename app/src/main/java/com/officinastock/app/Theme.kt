package com.officinastock.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// 4.10: every colour follows UiPrefs.light (dark = original Ubicora look, light = bright workshop).
private fun pick(dark: Long, light: Long) = Color(if (UiPrefs.light) light else dark)
val Ink: Color get() = pick(0xFF07121F, 0xFFF2F6F5)
val PanelBg: Color get() = pick(0xFF101E2D, 0xFFFFFFFF)
val Surface2: Color get() = pick(0xFF17293B, 0xFFE3ECEA)
val Mint: Color get() = pick(0xFF2DDEB3, 0xFF0B8A6D)
val Sky: Color get() = pick(0xFF6DC8F1, 0xFF1774A8)
val TextPrimary: Color get() = pick(0xFFF0F7FF, 0xFF0C1B27)
val TextSecondary: Color get() = pick(0xFF99ADBE, 0xFF4C6170)
val Amber: Color get() = pick(0xFFFFC978, 0xFFA45F0A)
val Danger: Color get() = pick(0xFFFF8387, 0xFFC4353C)
/** Thin borders and tracks: light on the dark theme, dark on the light one. */
val Hairline: Color get() = if (UiPrefs.light) Color.Black else Color.White
/** Text and icons drawn on a Mint background. */
val OnMint: Color get() = if (UiPrefs.light) Color.White else Color(0xFF07121F)
val HeroBrush: Brush get() = if (UiPrefs.light)
    Brush.linearGradient(listOf(Color(0xFFDDF3EC), Color(0xFFE7F0F6), Color(0xFFF1F4FA)))
    else Brush.linearGradient(listOf(Color(0xFF173F4C), Color(0xFF0B2539), Color(0xFF101D36)))

@Composable fun StockTheme(content: @Composable () -> Unit) {
    val scheme = if (UiPrefs.light) lightColorScheme(
        primary = Mint, onPrimary = OnMint, secondary = Sky, onSecondary = Color.White,
        background = Ink, onBackground = TextPrimary, surface = PanelBg, onSurface = TextPrimary,
        surfaceVariant = Surface2, onSurfaceVariant = TextSecondary,
        secondaryContainer = Color(0xFFCDEBE2), onSecondaryContainer = TextPrimary,
        outline = Color(0xFF9FB2B8), error = Danger
    ) else darkColorScheme(
        primary = Mint, onPrimary = OnMint, secondary = Sky, onSecondary = Ink,
        background = Ink, onBackground = TextPrimary, surface = PanelBg, onSurface = TextPrimary,
        surfaceVariant = Surface2, onSurfaceVariant = TextSecondary,
        outline = Color(0xFF3B5061), error = Danger
    )
    // Glove mode: everything a bit larger, so buttons are easier to hit with gloves or dirty hands.
    val base = LocalDensity.current
    val density = if (UiPrefs.glove) Density(base.density * 1.15f, (base.fontScale * 1.1f).coerceAtMost(2f)) else base
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
