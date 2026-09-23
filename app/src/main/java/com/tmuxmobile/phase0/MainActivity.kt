package com.tmuxmobile.phase0

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val HOST = "100.66.191.51"
private const val PORT = 22
private const val USERNAME = "agent-stack"
private const val ASSET_KEY_NAME = "phase0_id_ed25519"
private const val SESSION_NAME = "phase0-test"
private const val TEST_INPUT = "echo phase0-spike-ok"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpikeScreen(appContext = applicationContext)
                }
            }
        }
    }
}

@Composable
fun SpikeScreen(appContext: Context) {
    var output by remember { mutableStateOf("connecting...\n") }
    var paneId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val session = remember { SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME) }

    LaunchedEffect(Unit) {
        runCatching {
            session.connect(SESSION_NAME)
            session.lines().collect { line ->
                val parsed = ControlModeParser.parseLine(line)
                if (parsed != null) {
                    if (paneId == null) paneId = parsed.paneId
                    output += parsed.text + "\n"
                }
            }
        }.onFailure { e -> output += "ERROR: ${e.message}\n" }
    }

    DisposableEffect(Unit) {
        onDispose { session.close() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = output,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        )
        Button(
            onClick = {
                val target = paneId ?: return@Button
                scope.launch {
                    runCatching { session.sendKeys(target, TEST_INPUT) }
                        .onFailure { e -> output += "SEND ERROR: ${e.message}\n" }
                }
            },
            enabled = paneId != null
        ) {
            Text(if (paneId != null) "Send test input" else "Waiting for pane...")
        }
    }
}
