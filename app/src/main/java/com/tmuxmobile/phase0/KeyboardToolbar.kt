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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmuxmobile.phase0.ui.theme.Amber

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

    // Ctrl is a MODIFIER: on its own it sends nothing. Bare "C" is not a control key at
    // all -- tmux echoes it as a literal "C" character (verified over a live control-mode
    // channel: `send-keys -t <s> C` came back as `%output %21 C`, while `C-c` produced a
    // real interrupt and killed the foreground process). So Ctrl stays sticky: pressing it
    // arms the next key, which is then sent as C-<key> ("C-c", "C-d", ...). Ctrl twice
    // disarms, and a modifier is never sent as a standalone key.
    var ctrlArmed by remember { mutableStateOf(false) }
    val keys = listOf(
        if (ctrlArmed) "Ctrl*" to "__CTRL__" else "Ctrl" to "__CTRL__",
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
                    .background(
                        // Armed Ctrl is tinted so the modifier state is visible.
                        if (ctrlArmed && keyName == "__CTRL__") Amber
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable {
                        when {
                            keyName == "__CTRL__" -> ctrlArmed = !ctrlArmed
                            ctrlArmed -> {
                                ctrlArmed = false
                                onKey(TmuxKeyMapper.controlKeyName(keyName))
                            }
                            else -> onKey(keyName)
                        }
                    }
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
