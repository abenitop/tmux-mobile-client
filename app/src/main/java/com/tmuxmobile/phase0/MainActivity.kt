package com.tmuxmobile.phase0

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateListOf
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
    var connected by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("terminal") } // terminal | chat-claude | chat-hermes
    val chatEvents = remember { mutableStateListOf<ChatEvent>() }
    val scope = rememberCoroutineScope()
    val session = remember { SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME) }

    LaunchedEffect(Unit) {
        runCatching {
            session.connect(SESSION_NAME)
            connected = true
            session.lines().collect { line ->
                val parsed = ControlModeParser.parseLine(line)
                if (parsed != null) {
                    if (paneId == null) paneId = parsed.paneId
                    output += parsed.text + "\n"
                }
            }
        }.onFailure { e ->
            connected = false
            output += "ERROR: ${e.message}\n"
        }
    }

    // Keyed on `connected` as well as viewMode: the chat adapters open a channel on the
    // already-authenticated client, so starting them before connect() returns would fail
    // with UninitializedPropertyAccessException. Tapping Chat during connect now simply
    // starts streaming once the connection is up.
    LaunchedEffect(viewMode, connected) {
        if (!connected) return@LaunchedEffect
        when (viewMode) {
            "chat-claude" -> {
                chatEvents.clear()
                runCatching {
                    ClaudeCodeChatAdapter(session).events().collect { chatEvents.add(it) }
                }.onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("ERROR: ${e.message}")) }
            }
            "chat-hermes" -> {
                chatEvents.clear()
                runCatching {
                    HermesChatAdapter(session).events().collect { chatEvents.add(it) }
                }.onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("ERROR: ${e.message}")) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { session.close() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { viewMode = "terminal" }) { Text("Terminal") }
            Button(onClick = { viewMode = "chat-claude" }) { Text("Chat: Claude") }
            Button(onClick = { viewMode = "chat-hermes" }) { Text("Chat: Hermes") }
        }
        if (viewMode == "terminal") {
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
        } else {
            ChatScreen(events = chatEvents, onSend = { text ->
                val target = paneId ?: return@ChatScreen
                scope.launch {
                    runCatching { session.sendKeys(target, text) }
                        .onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("SEND ERROR: ${e.message}")) }
                }
            })
        }
    }
}
