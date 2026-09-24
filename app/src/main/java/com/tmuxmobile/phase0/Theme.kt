package com.tmuxmobile.phase0

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tmuxmobile.phase0.R
import com.tmuxmobile.phase0.ui.theme.LightColors

/**
 * v2 type: Bricolage Grotesque for UI, JetBrains Mono for terminal / host / path strings.
 *
 * Bricolage Grotesque is a variable font (opsz/wdth/wght). The only file Google publishes
 * is the variable TTF, so bold is a FontVariation on the same resource rather than a second
 * file; Compose maps FontWeight.Bold to the wght axis (falls back to synthetic bold on
 * platform builds that ignore the axis — acceptable, never wrong).
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(
        R.font.bricolage_grotesque,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Terminal output, host/path strings, ports, process labels. Never UI copy. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Bricolage, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Bricolage, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Bricolage, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
)

/**
 * Chat palette. The chat view is intentionally NOT part of this port's restyle (the task
 * scopes the visual pass to Hosts / Host form / Sessions list / Terminal), so these keep
 * their existing dark-WhatsApp values and the chat renders exactly as before on its own
 * explicitly-dark background.
 */
internal val ChatBackground = androidx.compose.ui.graphics.Color(0xFF0B141A)
internal val BubbleMine = androidx.compose.ui.graphics.Color(0xFF005C4B)
internal val BubbleTheirs = androidx.compose.ui.graphics.Color(0xFF202C33)
internal val BubbleToolChip = androidx.compose.ui.graphics.Color(0xFF1F2C34)
internal val BubbleText = androidx.compose.ui.graphics.Color(0xFFE9EDEF)
internal val DiffAdded = androidx.compose.ui.graphics.Color(0xFF7EE787)
internal val DiffRemoved = androidx.compose.ui.graphics.Color(0xFFFF7B72)
internal val PermissionCardColor = androidx.compose.ui.graphics.Color(0xFF2A3942)

/**
 * Light-first warm cream ("paper + ink") chrome. The dark terminal panel is painted by
 * TerminalHost itself, so the light MaterialTheme no longer shows as a white frame around
 * it — the terminal reads as an intentional dark panel inside cream chrome, per the v2
 * direction. Replaces the old always-dark theme.
 */
@Composable
fun TmuxMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
