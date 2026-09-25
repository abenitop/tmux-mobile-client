package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmuxmobile.phase0.ui.theme.StatusGreen
import com.tmuxmobile.phase0.ui.theme.TerminalGreen

/**
 * v2 Hosts screen: dark page, server cards on a raised surface with a
 * connection-status dot and mono `user@host` meta, and a "+ Add server" pill.
 * Same onConnect/onAdd/onEdit/onDelete callbacks as before — restyle only.
 * Colors come from MaterialTheme.colorScheme (dark theme), not hardcoded tokens.
 */
@Composable
fun HostsScreen(
    hosts: List<Host>,
    onConnect: (Host) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "servers",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Hosts",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        if (hosts.isEmpty()) {
            // weight(1f), NOT fillMaxSize(): fillMaxSize() would consume all remaining
            // vertical space and push the "+ Add server" pill off the bottom of the screen.
            // weight(1f) lets the empty-state fill the space the list would, and the pill
            // still lays out below it.
            Column(
                modifier = Modifier.weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No servers yet.", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                Text("Tap “Add server” to save one.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(hosts, key = { it.id }) { host ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusGreen, CircleShape),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                host.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${host.username}@${host.hostname}:${host.port}",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConnect(host) }) { Text("Connect") }
                            Button(onClick = { onEdit(host) }) { Text("Edit") }
                            Button(onClick = { onDelete(host) }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        // "+ Add server" pill, full-width, per design s1. Inverse surface (light on dark).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(26.dp))
                .clickable(onClick = onAdd)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", color = TerminalGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Text("Add server", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
