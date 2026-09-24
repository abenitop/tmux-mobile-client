package com.tmuxmobile.phase0.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// v2 palette — "paper + ink" (docs/design/design-tokens.md, docs/design/design-spec.md).

// Surfaces (warm cream)
val Page = Color(0xFFFAF9F5)
val SurfaceWarm = Color(0xFFEDE8DF)
val SurfaceAlt = Color(0xFFF0EEE6)
val Border = Color(0xFFD8D2C6)
val BorderLight = Color(0xFFEDEDEB)

// Ink (text)
val InkStrong = Color(0xFF0F0E0C)
val Ink = Color(0xFF1A1916)
val InkText = Color(0xFF2C2A26)
val InkMid = Color(0xFF3A3730)
val InkMuted = Color(0xFFA39D92)

// Terminal panel (dark)
val TerminalBg = Color(0xFF0F0E0C)
val TerminalPanel = Color(0xFF1A1916)
val TerminalBorder = Color(0xFF2C2A26)

// Accents
val TerminalGreen = Color(0xFF70E080)
val StatusGreen = Color(0xFF7BC96F)
val Amber = Color(0xFFF2A93B)
val Pink = Color(0xFFE87AA5)
val Blue = Color(0xFF6FB3F2)

// Semantic
val Approve = Color(0xFF9FDC92)
val ApproveOn = Color(0xFF142514)
val Deny = Color(0xFFF29A8F)
val DenyOn = Color(0xFF2A1413)
val Danger = Color(0xFFB00020)

// Light scheme (the app's only scheme — warm cream chrome, dark terminal panels inside it)
val LightColors = lightColorScheme(
    primary = TerminalGreen,
    onPrimary = InkStrong,
    primaryContainer = Approve,
    onPrimaryContainer = ApproveOn,
    secondary = InkMid,
    onSecondary = SurfaceWarm,
    secondaryContainer = SurfaceAlt,
    onSecondaryContainer = InkText,
    tertiary = StatusGreen,
    onTertiary = InkStrong,
    tertiaryContainer = Amber,
    onTertiaryContainer = InkStrong,
    background = Page,
    onBackground = InkText,
    surface = Page,
    onSurface = InkText,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = InkMid,
    surfaceTint = TerminalGreen,
    inverseSurface = TerminalBg,
    inverseOnSurface = SurfaceWarm,
    inversePrimary = StatusGreen,
    error = Danger,
    onError = Page,
    errorContainer = Deny,
    onErrorContainer = DenyOn,
    outline = Border,
    outlineVariant = BorderLight,
    scrim = InkStrong,
    surfaceBright = Page,
    surfaceDim = SurfaceAlt,
    surfaceContainer = SurfaceAlt,
    surfaceContainerHigh = SurfaceWarm,
    surfaceContainerHighest = SurfaceWarm,
    surfaceContainerLow = Page,
    surfaceContainerLowest = Page,
)

// Dark scheme is unused (the app is light-first, terminal panels are dark within it), but
// MaterialTheme needs a reference; kept as a warm-dark mirror of the same tokens.
val DarkColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = InkStrong,
    primaryContainer = InkStrong,
    onPrimaryContainer = TerminalGreen,
    secondary = InkMuted,
    onSecondary = InkStrong,
    secondaryContainer = TerminalPanel,
    onSecondaryContainer = InkText,
    tertiary = StatusGreen,
    onTertiary = InkStrong,
    tertiaryContainer = Amber,
    onTertiaryContainer = InkStrong,
    background = TerminalBg,
    onBackground = SurfaceWarm,
    surface = TerminalBg,
    onSurface = SurfaceWarm,
    surfaceVariant = TerminalPanel,
    onSurfaceVariant = InkMuted,
    surfaceTint = TerminalGreen,
    inverseSurface = SurfaceWarm,
    inverseOnSurface = InkStrong,
    inversePrimary = StatusGreen,
    error = Deny,
    onError = DenyOn,
    errorContainer = Danger,
    onErrorContainer = Page,
    outline = TerminalBorder,
    outlineVariant = TerminalPanel,
    scrim = InkStrong,
    surfaceBright = TerminalPanel,
    surfaceDim = TerminalBg,
    surfaceContainer = TerminalPanel,
    surfaceContainerHigh = TerminalBorder,
    surfaceContainerHighest = InkMid,
    surfaceContainerLow = TerminalBg,
    surfaceContainerLowest = InkStrong,
)
