package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun HostFormScreen(
    existing: Host?,
    existingPassword: String?,
    onSave: (Host, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var hostname by remember { mutableStateOf(existing?.hostname ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existingPassword ?: "") }
    var sessionName by remember { mutableStateOf(existing?.sessionName ?: "main") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = hostname, onValueChange = { hostname = it }, label = { Text("Hostname or IP") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = sessionName,
            onValueChange = { sessionName = it },
            label = { Text("tmux session") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = name.isNotBlank() && hostname.isNotBlank() && username.isNotBlank(),
            onClick = {
                onSave(
                    Host(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        hostname = hostname.trim(),
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        sessionName = sessionName.trim().ifBlank { "main" },
                        // Preserved so an edit doesn't silently un-pin a verified host key.
                        hostKeyFingerprint = existing?.hostKeyFingerprint,
                    ),
                    password,
                )
            },
        ) { Text("Save") }
        Button(onClick = onCancel) { Text("Cancel") }
    }
}
