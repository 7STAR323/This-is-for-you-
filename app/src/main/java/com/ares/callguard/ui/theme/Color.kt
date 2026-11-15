package com.ares.callguard.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Forest Prestige
val ForestGreen = Color(0xFF183B2B)
val DeepOlive = Color(0xFF34483B)
val SageGreen = Color(0xFF78947A)
val SoftGreen = Color(0xFFB5C5AE)
val WarmIvory = Color(0xFFF1EFE5)
val MutedChampagne = Color(0xFFD5C7A3)

private val PaleIvory = Color(0xFFFAF8F1)
private val NightForest = Color(0xFF0E2118)
private val Ember = Color(0xFF8C3B2A)
private val EmberLight = Color(0xFFE8A995)

// Sage is too light for text on ivory (about 3:1), so body and secondary text use the two
// dark greens and sage is kept for icons and decoration.
val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = WarmIvory,
    primaryContainer = SoftGreen,
    onPrimaryContainer = ForestGreen,
    secondary = DeepOlive,
    onSecondary = WarmIvory,
    secondaryContainer = Color(0xFFDDE4D6),
    onSecondaryContainer = ForestGreen,
    tertiary = MutedChampagne,
    onTertiary = ForestGreen,
    tertiaryContainer = Color(0xFFEBE2C8),
    onTertiaryContainer = ForestGreen,
    background = WarmIvory,
    onBackground = ForestGreen,
    surface = PaleIvory,
    onSurface = ForestGreen,
    surfaceVariant = Color(0xFFE4E6D9),
    onSurfaceVariant = DeepOlive,
    surfaceContainer = PaleIvory,
    surfaceContainerHigh = PaleIvory,
    surfaceContainerLow = WarmIvory,
    outline = SageGreen,
    outlineVariant = SoftGreen,
    error = Ember,
    onError = WarmIvory,
)

// At night the palette turns inside out: the greens become the surfaces and ivory the ink.
val DarkColors = darkColorScheme(
    primary = SoftGreen,
    onPrimary = ForestGreen,
    primaryContainer = DeepOlive,
    onPrimaryContainer = WarmIvory,
    secondary = SageGreen,
    onSecondary = NightForest,
    secondaryContainer = DeepOlive,
    onSecondaryContainer = WarmIvory,
    tertiary = MutedChampagne,
    onTertiary = ForestGreen,
    tertiaryContainer = Color(0xFF4A452F),
    onTertiaryContainer = MutedChampagne,
    background = NightForest,
    onBackground = WarmIvory,
    surface = ForestGreen,
    onSurface = WarmIvory,
    surfaceVariant = DeepOlive,
    onSurfaceVariant = SoftGreen,
    surfaceContainer = ForestGreen,
    surfaceContainerHigh = DeepOlive,
    surfaceContainerLow = NightForest,
    outline = SageGreen,
    outlineVariant = DeepOlive,
    error = EmberLight,
    onError = NightForest,
)
