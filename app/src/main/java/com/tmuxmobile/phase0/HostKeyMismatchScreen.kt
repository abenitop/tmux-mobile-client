package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxmobile.phase0.ui.theme.Danger
import com.tmuxmobile.phase0.ui.theme.InkMuted
import com.tmuxmobile.phase0.ui.theme.InkText

/**
 * Shown INSTEAD of connecting when TofuHostKeyVerifier reports a mismatch -- never a
 * silent bypass. Same threat model as SSH's "REMOTE HOST IDENTIFICATION HAS CHANGED"
 * warning.
 */
@Composable
fun HostKeyMismatchScreen(connection: Connection, onGoBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Host key changed", color = InkText, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "${connection.hostname}:${connection.port}",
            color = InkMuted,
            fontFamily = JetBrainsMono,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "The server presented a different identity than it did before. This could mean " +
                "the server was reinstalled, or that something is intercepting your connection. " +
                "Not connecting.",
            color = InkText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGoBack) { Text("Go back") }
    }
}
