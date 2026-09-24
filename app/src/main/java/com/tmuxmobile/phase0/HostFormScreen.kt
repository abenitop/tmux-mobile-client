package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tmuxmobile.phase0.ui.theme.InkMuted
import com.tmuxmobile.phase0.ui.theme.InkText

/**
 * v2 Add-server form. Same fields and same onSave/onCancel contract as before; the mono
 * host/port inputs, uppercase labels, and warm input surfaces are the design's restyle.
 */
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
        Text(
            if (existing == null) "New host" else "Edit host",
            color = InkText,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        FieldLabel("NAME")
        MonoField(value = name, onValueChange = { name = it }, label = "My server")
        FieldLabel("HOST")
        MonoField(value = hostname, onValueChange = { hostname = it }, label = "Hostname or IP")
        FieldLabel("PORT")
        MonoField(value = port, onValueChange = { port = it }, label = "22")
        FieldLabel("USER")
        MonoField(value = username, onValueChange = { username = it }, label = "Username")
        FieldLabel("PASSWORD")
        MonoField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)
        FieldLabel("TMUX SESSION")
        MonoField(value = sessionName, onValueChange = { sessionName = it }, label = "main")

        Spacer(Modifier.height(20.dp))
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = InkMuted,
        fontFamily = JetBrainsMono,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** v2 input: mono text on a warm surface, rounded, per design s4. */
@Composable
private fun MonoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMono, color = InkText),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
