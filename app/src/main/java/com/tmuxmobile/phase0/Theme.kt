package com.tmuxmobile.phase0

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark theme, always. The terminal surface is explicitly black (TerminalHost sets it),
 * so a light MaterialTheme produced a stark white frame around it. The app is a terminal
 * client -- dark is the right default rather than following the system setting.
 *
 * `background`/`surface` are set to the same black the terminal paints so the chrome and
 * the terminal read as one continuous surface.
 */
private val TmuxMobileDarkColors = darkColorScheme(
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF000000),
)

/**
 * Chat palette, WhatsApp-dark. Bubbles are only readable if their own text colour is
 * set alongside them -- the theme's onSurface is tuned for the black terminal surface,
 * not for a mid-dark bubble.
 */
internal val ChatBackground = androidx.compose.ui.graphics.Color(0xFF0B141A)
internal val BubbleMine = androidx.compose.ui.graphics.Color(0xFF005C4B)
internal val BubbleTheirs = androidx.compose.ui.graphics.Color(0xFF202C33)
internal val BubbleToolChip = androidx.compose.ui.graphics.Color(0xFF1F2C34)
internal val BubbleText = androidx.compose.ui.graphics.Color(0xFFE9EDEF)

@Composable
fun TmuxMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TmuxMobileDarkColors, content = content)
}
