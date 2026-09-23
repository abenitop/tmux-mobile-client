package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The shared "type a line and send it" row. Used by Chat view and by Terminal mode's
 * compose sub-mode -- extracted from ChatScreen so the two can't drift apart.
 *
 * Blank input is ignored and the draft is only cleared once it has been accepted, so
 * pressing Send on an empty field doesn't wipe anything.
 */
@Composable
fun ComposeInputBar(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            if (draft.isNotBlank()) {
                onSend(draft)
                draft = ""
            }
        }) {
            Text("Send")
        }
    }
}
