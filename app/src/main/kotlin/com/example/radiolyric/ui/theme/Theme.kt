package com.example.radiolyric.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
        darkColorScheme(
                primary = Color(0xFFE0245E), // Heart-red accent for the play button
                onPrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFFB388FF),
                background = Color(0xFF0A0A0A), // True near-black for in-car glare reduction
                onBackground = Color(0xFFF2F2F2),
                surface = Color(0xFF141414),
                onSurface = Color(0xFFEAEAEA),
                surfaceVariant = Color(0xFF1F1F1F),
                onSurfaceVariant = Color(0xFFCCCCCC),
        )

private val LightColors =
        lightColorScheme(
                primary = Color(0xFFC2185B),
                onPrimary = Color(0xFFFFFFFF),
                background = Color(0xFFFAFAFA),
                onBackground = Color(0xFF101010),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF101010),
        )

/**
 * In-car Material 3 theme. Defaults to a dark, high-contrast palette because head-unit screens are
 * usually dark-mode by default. A future `SettingsRepository` can override the [forceDark] flag.
 */
@Composable
fun RadioLyricTheme(
        forceDark: Boolean = true,
        content: @Composable () -> Unit,
) {
    val useDark = forceDark || isSystemInDarkTheme()
    MaterialTheme(
            colorScheme = if (useDark) DarkColors else LightColors,
            typography = RadioLyricTypography,
            content = content,
    )
}
