package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Row of extra special keys above the system keyboard (Ctrl / Esc / Tab / arrows). This is
 * the operator's explicit override of the "never a custom on-screen keyboard" constraint in
 * AGENTS.md (recorded in docs/specs/2026-09-26-app-chrome-navigation-design.md).
 *
 * [mode] is one of "auto" (show when the IME is up), "always", "never". Detecting the IME
 * in Compose is noisy, so "auto" behaves like "always" for now — the bar sits above the
 * compose bar, which only shows in compose mode, so the practical difference is negligible.
 * ponytail: an IME-visibility listener can tighten "auto" later if it matters.
 *
 * Keys are sent as tmux key names via [onKey]; Ctrl sends "C-<key>".
 */
@Composable
fun KeyboardToolbar(
    mode: String,
    onKey: (String) -> Unit,
) {
    if (mode == "never") return

    val keys = listOf(
        "Ctrl" to "C",
        "Esc" to "Escape",
        "Tab" to "Tab",
        "↑" to "Up",
        "↓" to "Down",
        "←" to "Left",
        "→" to "Right",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { (label, keyName) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .clickable { onKey(keyName) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
