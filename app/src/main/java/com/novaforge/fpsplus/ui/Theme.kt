package com.novaforge.fpsplus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FPSColors {
    val Background = Color(0xFF080C14)
    val Surface = Color(0xFF101927)
    val SurfaceRaised = Color(0xFF162235)
    val Border = Color(0xFF26374B)
    val Accent = Color(0xFF61E6F2)
    val AccentBlue = Color(0xFF6598FF)
    val Muted = Color(0xFF8B9BAE)
    val Text = Color(0xFFEAF5FA)
    val Good = Color(0xFF55D6A0)
    val Warning = Color(0xFFF5C66A)
    val Bad = Color(0xFFFF617D)
}

private val DarkScheme = darkColorScheme(
    primary = FPSColors.Accent,
    onPrimary = FPSColors.Background,
    secondary = FPSColors.AccentBlue,
    background = FPSColors.Background,
    onBackground = FPSColors.Text,
    surface = FPSColors.Surface,
    onSurface = FPSColors.Text,
    surfaceVariant = FPSColors.SurfaceRaised,
    onSurfaceVariant = FPSColors.Muted,
    outline = FPSColors.Border
)

@Composable
fun FPSPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = MaterialTheme.typography, content = content)
}
