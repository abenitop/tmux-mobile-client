package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HostsScreen(
    hosts: List<Host>,
    onConnect: (Host) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
) {
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } },
    ) { padding ->
        if (hosts.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No servers yet. Tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(hosts, key = { it.id }) { host ->
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(host.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text("${host.username}@${host.hostname}:${host.port}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConnect(host) }) { Text("Connect") }
                                Button(onClick = { onEdit(host) }) { Text("Edit") }
                                Button(onClick = { onDelete(host) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
