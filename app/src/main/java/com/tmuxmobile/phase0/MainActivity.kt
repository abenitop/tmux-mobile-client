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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ConnectionStore(applicationContext)
        setContent {
            TmuxMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var connection by remember { mutableStateOf(store.load()) }
                    var mismatch by remember { mutableStateOf(false) }
                    val current = connection
                    when {
                        current != null && mismatch -> HostKeyMismatchScreen(
                            connection = current,
                            onGoBack = {
                                mismatch = false
                                store.clear()
                                connection = null
                            },
                        )
                        current != null -> SpikeScreen(
                            appContext = applicationContext,
                            connection = current,
                            onForget = {
                                store.clear()
                                connection = null
                            },
                            onHostKeyMismatch = { mismatch = true },
                            onHostKeyFingerprintLearned = { fingerprint ->
                                store.saveHostKeyFingerprint(fingerprint)
                            },
                        )
                        else -> ConnectScreen(onConnect = { c ->
                            store.save(c)
                            connection = c
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun SpikeScreen(
    appContext: Context,
    connection: Connection,
    onForget: () -> Unit,
    onHostKeyMismatch: () -> Unit,
    onHostKeyFingerprintLearned: (String) -> Unit,
) {
    var paneId by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("terminal") } // terminal | chat-claude | chat-hermes
    // Terminal sub-mode: which surface owns input. Toggling this changes both what is
    // shown AND whether RawInputBridge forwards anything (see `enabled` below); showing
    // the compose bar while raw key routing stayed live would send every typed character
    // twice -- once as a pane keystroke, once when Send was pressed.
    var inputMode by remember { mutableStateOf("raw") } // raw | compose
    // One transcript per source, plus a "has this source ever been opened" latch.
    //
    // Previously a single list was CLEARED and re-collected on every toggle, so each
    // visit to a Chat tab re-opened the stream and replayed it from the very first
    // message -- a 1700+ message session spent the replay parked at the top of the
    // transcript, which reads as a frozen/blank window. Keeping the lists separate and
    // never clearing them means a toggle is just a view switch, and a source that has
    // already been opened keeps streaming in the background.
    val chatEventsClaude = remember { mutableStateListOf<ChatEvent>() }
    val chatEventsHermes = remember { mutableStateListOf<ChatEvent>() }
    var claudeStarted by remember { mutableStateOf(false) }
    var hermesStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Trust-on-first-use. The verifier is created with the fingerprint already stored for
    // this host (null on the very first connect) and reports the fingerprint it saw on
    // first connect so it can be pinned for every later connect. `mismatch` is read after
    // a failed connect() to tell "wrong host key" apart from "unreachable".
    val hostKeyVerifier = remember {
        TofuHostKeyVerifier(connection.hostKeyFingerprint) { fingerprint ->
            onHostKeyFingerprintLearned(fingerprint)
        }
    }
    val session = remember {
        SshSpikeSession(
            appContext,
            connection.hostname,
            connection.port,
            connection.username,
            password = connection.password,
            hostKeyVerifier = hostKeyVerifier,
        )
    }

    // Terminal pane output. A SharedFlow (not state) because TerminalHost consumes it
    // as a stream and appends to the emulator; extraBufferCapacity keeps the collector
    // from ever suspending on backpressure.
    val terminalFeed = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }

    LaunchedEffect(Unit) {
        runCatching {
            session.connect(connection.sessionName)
            connected = true
            // The connect()-time capture-pane request replies as a %begin/<data>/%end
            // block interleaved with ordinary %output notifications on this same
            // channel -- buffer between %begin and %end and emit it as one snapshot,
            // otherwise fall through to normal %output handling.
            var capturingSnapshot = false
            val snapshotBuf = StringBuilder()
            session.lines().collect { line ->
                when {
                    line.startsWith("%begin") -> {
                        capturingSnapshot = true
                        snapshotBuf.clear()
                    }
                    capturingSnapshot && (line.startsWith("%end") || line.startsWith("%error")) -> {
                        capturingSnapshot = false
                        if (snapshotBuf.isNotEmpty()) terminalFeed.emit(snapshotBuf.toString())
                    }
                    capturingSnapshot -> snapshotBuf.append(line).append("\r\n")
                    else -> {
                        val parsed = ControlModeParser.parseLine(line)
                        if (parsed != null) {
                            if (paneId == null) paneId = parsed.paneId
                            terminalFeed.emit(parsed.text)
                        }
                    }
                }
            }
        }.onFailure { e ->
            connected = false
            // A host key that differs from the pinned one is not a connection error to
            // print into the terminal -- hand it to the screen, which shows the mismatch
            // warning instead of a terminal that never connected.
            if (hostKeyVerifier.mismatch) {
                onHostKeyMismatch()
            } else {
                // No raw-text accumulator to append to any more; surface the failure into
                // the terminal itself so it stays visible where output used to appear.
                terminalFeed.emit("\r\nERROR: ${e.message}\r\n")
            }
        }
    }

    // Keyed on `connected` ALONE. Each source's collect loop runs once, on first connect,
    // and keeps running for the life of the app -- independent of which tab is showing.
    //
    // This MUST NOT be keyed on viewMode: LaunchedEffect cancels + relaunches its whole
    // block whenever a key changes, so keying on viewMode would cancel a source's
    // collect() the moment you leave its tab, and the claudeStarted/hermesStarted latch
    // would then prevent it from ever being re-entered -- permanently stopping that
    // stream. viewMode only chooses which list to RENDER below; it has no bearing on
    // which streams are being collected.
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        // Each source is started exactly once (the latch guards against the effect
        // restarting on reconnect). Both collect() calls run as siblings, so leaving one
        // tab does not cancel the other's stream.
        coroutineScope {
            if (!claudeStarted) {
                claudeStarted = true
                launch {
                    runCatching {
                        ClaudeCodeChatAdapter(session).events().collect {
                            if (isActive) chatEventsClaude.add(it)
                        }
                    }.onFailure { e ->
                        if (isActive) chatEventsClaude.add(ChatEvent.AssistantMessage("ERROR: ${e.message}"))
                    }
                }
            }
            if (!hermesStarted) {
                hermesStarted = true
                launch {
                    runCatching {
                        HermesChatAdapter(session).events().collect {
                            if (isActive) chatEventsHermes.add(it)
                        }
                    }.onFailure { e ->
                        if (isActive) chatEventsHermes.add(ChatEvent.AssistantMessage("ERROR: ${e.message}"))
                    }
                }
            }
        }
    }

    // The transcript the UI shows: the active source's list, or both when neither Chat
    // tab is open. Built with `key` so each ChatEvent keeps a stable identity across
    // recomposition -- that is what stops the list from scroll-jumping on every new item.
    val chatEvents: List<ChatEvent> = when (viewMode) {
        "chat-claude" -> chatEventsClaude
        "chat-hermes" -> chatEventsHermes
        else -> chatEventsClaude + chatEventsHermes
    }

    // Raw-mode input. Keyed on `paneId` so a newly-learned pane id reaches the bridge;
    // the lambda falls back to the session name, since Chat's Send proved the target
    // does not actually need a pane id and an idle session may never emit one.
    // `enabled` follows inputMode so compose mode owns input exclusively.
    val rawInputBridge = remember(paneId, session) {
        RawInputBridge(
            scope,
            session,
            target = { paneId ?: connection.sessionName },
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
            Button(onClick = onForget) { Text("Forget") }
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
                        runCatching { session.sendKeys(paneId ?: connection.sessionName, text, literal = true) }
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
                    runCatching { session.sendKeys(connection.sessionName, text) }
                        .onFailure { e ->
                            // Into the active source's list: `chatEvents` is now a derived
                            // read-only view and cannot be appended to.
                            val target = if (viewMode == "chat-claude") chatEventsClaude else chatEventsHermes
                            target.add(ChatEvent.AssistantMessage("SEND ERROR: ${e.message}"))
                        }
                }
            })
        }
    }
}
