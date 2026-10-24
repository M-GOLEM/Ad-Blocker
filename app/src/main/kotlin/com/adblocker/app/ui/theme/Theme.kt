package com.adblocker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val bg: List<Color>,
    val card: List<Color>,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
    val isDark: Boolean
)

val DarkPalette = Palette(
    bg = listOf(Color(0xFF07071A), Color(0xFF120E2E), Color(0xFF07071A)),
    card = listOf(Color(0xFF1C1943), Color(0xFF120F2C)),
    text = Color.White,
    muted = Color(0xFF9E9AC9),
    accent = Color(0xFF00FF9C),
    accent2 = Color(0xFF00C8FF),
    danger = Color(0xFFFF4D6D),
    isDark = true
)

val LightPalette = Palette(
    bg = listOf(Color(0xFFF7F4FF), Color(0xFFEAF6FF), Color(0xFFFFF5F9)),
    card = listOf(Color(0xFFFFFFFF), Color(0xFFF2EEFF)),
    text = Color(0xFF1A1733),
    muted = Color(0xFF6B6790),
    accent = Color(0xFF00A67E),
    accent2 = Color(0xFF7C3AED),
    danger = Color(0xFFE11D48),
    isDark = false
)

/** Heart color for the dedication */
val HeartPink = Color(0xFFFF5C8A)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun AdBlockerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent,
            secondary = p.accent2,
            background = p.bg[0],
            surface = p.card[0],
            onPrimary = Color.Black,
            onBackground = p.text,
            onSurface = p.text
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            secondary = p.accent2,
            background = p.bg[0],
            surface = p.card[0],
            onPrimary = Color.White,
            onBackground = p.text,
            onSurface = p.text
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}