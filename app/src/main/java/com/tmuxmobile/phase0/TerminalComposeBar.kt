package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tmuxmobile.phase0.ui.theme.InkMuted
import com.tmuxmobile.phase0.ui.theme.InkStrong
import com.tmuxmobile.phase0.ui.theme.InkText
import com.tmuxmobile.phase0.ui.theme.SurfaceWarm
import com.tmuxmobile.phase0.ui.theme.TerminalGreen

/**
 * Terminal compose bar (v2, screen s2): the phone keyboard types into a rounded
 * "Message to pane…" bubble and Send fires send-keys into the live tmux pane. A raw-mode
 * chip swaps to keyboard/gesture input. Behaviour mirrors the shared ComposeInputBar:
 * blank input is ignored, the draft is cleared only after it is accepted.
 *
 * [readOnly] disables the field and the send button (input is off; safe-by-default attach).
 */
@Composable
fun TerminalComposeBar(
    onSend: (String) -> Unit,
    readOnly: Boolean,
    onRawMode: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rounded input bubble. BasicTextField (not OutlinedTextField) so there is no
        // border -- the bubble itself is the field, per the design.
        Box(
            modifier = Modifier
                .weight(1f)
                .background(SurfaceWarm, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty() && !readOnly) {
                Text("Message to pane…", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = !readOnly,
                textStyle = TextStyle(color = InkText, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Green ➤ send button. Disabled in read-only so input is genuinely off.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (readOnly) InkMuted else TerminalGreen)
                .clickable(enabled = !readOnly) {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("➤", color = InkStrong, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        // Raw-mode chip (swaps the compose bar for keyboard/gesture input).
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceWarm)
                .clickable(onClick = onRawMode)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("RAW", color = InkText, fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodySmall)
        }
    }
}
