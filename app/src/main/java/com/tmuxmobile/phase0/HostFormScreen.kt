package com.tmuxmobile.phase0

import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * v2 Add-server form. Same fields and same onSave/onCancel contract as before; the mono
 * host/port inputs and uppercase labels are the design's restyle. Colors come from
 * MaterialTheme.colorScheme (dark theme), not hardcoded tokens.
 */
@Composable
fun HostFormScreen(
    existing: Host?,
    existingPassword: String?,
    onSave: (Host, String) -> Unit,
    onCancel: () -> Unit,
) {
    // Keyed on existing?.id so the async-loaded host populates the fields: the form is
    // first composed with existing == null (empty), then again once the edit target
    // loads. Without the key, remember keeps the empty initial values forever and the
    // edit form looks like the saved host was lost.
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var hostname by remember(existing?.id) { mutableStateOf(existing?.hostname ?: "") }
    var port by remember(existing?.id) { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember(existing?.id) { mutableStateOf(existing?.username ?: "") }
    var password by remember(existing?.id, existingPassword) { mutableStateOf(existingPassword ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            if (existing == null) "New host" else "Edit host",
            color = MaterialTheme.colorScheme.onSurface,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = JetBrainsMono,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** v2 input: mono text on a surface, rounded, per design s4. */
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
        // PasswordVisualTransformation only changes what is DRAWN -- it does not tell the
        // IME anything, so without these the field is still treated as ordinary prose and
        // the keyboard's auto-correct/auto-capitalize stay enabled. On a Samsung IME that
        // silently rewrites credentials ("password" -> capitalised, or autocorrected), and
        // the mangled value is then what gets persisted and sent, giving "Failed password"
        // on auth. Password + no autocorrect + no capitalization is the correct contract.
        keyboardOptions = if (isPassword) {
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            )
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
