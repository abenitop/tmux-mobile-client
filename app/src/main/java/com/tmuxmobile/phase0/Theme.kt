package com.tmuxmobile.phase0

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tmuxmobile.phase0.R
import com.tmuxmobile.phase0.ui.theme.DarkColors

/**
 * v2 type: Bricolage Grotesque for UI, JetBrains Mono for terminal / host / path strings.
 *
 * Both families are bundled as STATIC per-weight files (no variable fonts, no
 * FontVariation.Settings): variable fonts + FontVariation broke rendering on-device
 * ("Invalid resource ID 0x00000000", blank screen), so every weight is a real TTF.
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
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
 * Dark theme (user-requested): dark warm surfaces, ink/cream text, terminal green accent.
 * Built from the same v2 tokens in Color.kt (DarkColors). The terminal panel is near-black,
 * so a dark MaterialTheme lets it read as continuous chrome rather than a white frame.
 */
@Composable
fun TmuxMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
