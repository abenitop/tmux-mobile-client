package com.tmuxmobile.phase0

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private const val HOST = "100.66.191.51"
private const val PORT = 22
private const val USERNAME = "agent-stack"
private const val ASSET_KEY_NAME = "phase0_id_ed25519"
private const val SESSION_NAME = "phase0-test"

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
    var paneId by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("terminal") } // terminal | chat-claude | chat-hermes
    // Terminal sub-mode: which surface owns input. Toggling this changes both what is
    // shown AND whether RawInputBridge forwards anything (see `enabled` below); showing
    // the compose bar while raw key routing stayed live would send every typed character
    // twice -- once as a pane keystroke, once when Send was pressed.
    var inputMode by remember { mutableStateOf("raw") } // raw | compose
    val chatEvents = remember { mutableStateListOf<ChatEvent>() }
    val scope = rememberCoroutineScope()
    val session = remember { SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME) }

    // Terminal pane output. A SharedFlow (not state) because TerminalHost consumes it
    // as a stream and appends to the emulator; extraBufferCapacity keeps the collector
    // from ever suspending on backpressure.
    val terminalFeed = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }

    LaunchedEffect(Unit) {
        runCatching {
            session.connect(SESSION_NAME)
            connected = true
            session.lines().collect { line ->
                val parsed = ControlModeParser.parseLine(line)
                if (parsed != null) {
                    if (paneId == null) paneId = parsed.paneId
                    terminalFeed.emit(parsed.text)
                }
            }
        }.onFailure { e ->
            connected = false
            // No raw-text accumulator to append to any more; surface the failure into
            // the terminal itself so it stays visible where output used to appear.
            terminalFeed.emit("\r\nERROR: ${e.message}\r\n")
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

    // Raw-mode input. Keyed on `paneId` so a newly-learned pane id reaches the bridge;
    // the lambda falls back to the session name, since Chat's Send proved the target
    // does not actually need a pane id and an idle session may never emit one.
    // `enabled` follows inputMode so compose mode owns input exclusively.
    val rawInputBridge = remember(paneId, session) {
        RawInputBridge(
            scope,
            session,
            target = { paneId ?: SESSION_NAME },
            enabled = { inputMode == "raw" },
        )
    }

    // Flush any buffered raw-mode text when raw mode gives up input (leaving Terminal
    // mode, or switching to compose), so a keystroke typed just before the switch isn't
    // lost or delivered after the user moved on.
    DisposableEffect(viewMode, inputMode) {
        onDispose { if (viewMode == "terminal" && inputMode == "raw") rawInputBridge.flush() }
    }

    DisposableEffect(Unit) {
        onDispose { session.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // targetSdk 36 forces edge-to-edge on Android 15+, so without this the
            // mode-toggle row is drawn *under* the system status bar and its touches are
            // consumed by SystemUI -- "Chat: Hermes" was literally untappable (the status
            // bar's insets frame is the full width and 113px tall, and the row sat inside
            // it). safeDrawing keeps content clear of the status bar and nav bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Row {
            Button(onClick = { viewMode = "terminal" }) { Text("Terminal") }
            Button(onClick = { viewMode = "chat-claude" }) { Text("Chat: Claude") }
            Button(onClick = { viewMode = "chat-hermes" }) { Text("Chat: Hermes") }
        }
        if (viewMode == "terminal") {
            Row {
                Button(onClick = { inputMode = "raw" }) { Text("Raw") }
                Button(onClick = { inputMode = "compose" }) { Text("Compose") }
            }
            TerminalHost(
                modifier = Modifier.weight(1f),
                feed = terminalFeed,
                viewClient = rawInputBridge,
                onFling = { direction -> rawInputBridge.onFling(direction) },
                // Compose mode owns focus (its text field must take the IME); raw mode
                // hands it to the terminal so key events and long-press reach it.
                focusable = inputMode == "raw",
            )
            // Only in compose mode: the same shared bar Chat view uses. Raw mode is
            // keyboard/gesture driven, so a text field there would be redundant.
            if (inputMode == "compose") {
                ComposeInputBar(onSend = { text ->
                    scope.launch {
                        runCatching { session.sendKeys(paneId ?: SESSION_NAME, text, literal = true) }
                            .onFailure { e ->
                                terminalFeed.emit("\r\nSEND ERROR: ${e.message}\r\n")
                            }
                    }
                })
            }
        } else {
            ChatScreen(events = chatEvents, onSend = { text ->
                // Target the SESSION, not paneId. paneId is only ever learned from a
                // %output line, and an idle session emits none -- so gating send on
                // paneId made Chat's Send silently do nothing (the draft cleared, but
                // onSend had already returned). Verified: `send-keys -t phase0-test -l`
                // + Enter works on an idle session with no prior %output.
                scope.launch {
                    runCatching { session.sendKeys(SESSION_NAME, text) }
                        .onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("SEND ERROR: ${e.message}")) }
                }
            })
        }
    }
}
