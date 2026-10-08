package com.officinastock.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF07121F)
val PanelBg = Color(0xFF101E2D)
val Surface2 = Color(0xFF17293B)
val Mint = Color(0xFF2DDEB3)
val Sky = Color(0xFF6DC8F1)
val TextPrimary = Color(0xFFF0F7FF)
val TextSecondary = Color(0xFF99ADBE)
val Amber = Color(0xFFFFC978)
val Danger = Color(0xFFFF8387)
val HeroBrush = Brush.linearGradient(listOf(Color(0xFF173F4C),Color(0xFF0B2539),Color(0xFF101D36)))
private val DarkScheme = darkColorScheme(
    primary=Mint,onPrimary=Ink,secondary=Sky,onSecondary=Ink,
    background=Ink,onBackground=TextPrimary,surface=PanelBg,onSurface=TextPrimary,
    surfaceVariant=Surface2,onSurfaceVariant=TextSecondary,
    outline=Color(0xFF3B5061),error=Danger
)
@Composable fun StockTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=DarkScheme,typography=Typography(),content=content)
}
