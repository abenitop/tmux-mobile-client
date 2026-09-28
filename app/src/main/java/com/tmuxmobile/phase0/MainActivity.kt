package com.tmuxmobile.phase0

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HostRepository(
            AppDatabase.get(applicationContext).hostDao(),
            EncryptedPasswordStore(applicationContext),
        )
        val prefs = AppPrefs(applicationContext)
        val appState = AppState(prefs)
        setContent {
            TmuxMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()
                    val hosts by repository.observeHosts().collectAsState(initial = emptyList())

                    // Keep the chrome's grey-out decision in sync with the host list.
                    appState.hostsEmpty = hosts.isEmpty()

                    AppChrome(appState = appState, navController = navController) {
                        NavHost(navController = navController, startDestination = "hosts") {
                            composable("hosts") {
                                HostsScreen(
                                    hosts = hosts,
                                    onConnect = { host -> navController.navigate("session/${host.id}") },
                                    onAdd = { navController.navigate("host_form") },
                                    onEdit = { host -> navController.navigate("host_form?hostId=${host.id}") },
                                    onDelete = { host -> scope.delete(host, repository) },
                                )
                            }

                            composable("host_picker") {
                                HostPickerScreen(
                                    hosts = hosts,
                                    onPick = { host -> navController.navigate("session/${host.id}") },
                                )
                            }

                            composable(
                                "host_form?hostId={hostId}",
                                arguments = listOf(navArgument("hostId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }),
                            ) { backStackEntry ->
                                val hostId = backStackEntry.arguments?.getLong("hostId") ?: 0L
                                var existing by remember { mutableStateOf<Host?>(null) }
                                var existingPassword by remember { mutableStateOf<String?>(null) }
                                LaunchedEffect(hostId) {
                                    if (hostId != 0L) {
                                        existing = repository.host(hostId)
                                        existingPassword = repository.passwordFor(hostId)
                                    }
                                }
                                HostFormScreen(
                                    existing = existing,
                                    existingPassword = existingPassword,
                                    onSave = { host, password ->
                                        scope.launch {
                                            repository.save(host, password)
                                            navController.popBackStack()
                                        }
                                    },
                                    onCancel = { navController.popBackStack() },
                                )
                            }

                            composable(
                                "session/{hostId}",
                                arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
                            ) { backStackEntry ->
                                val hostId = backStackEntry.arguments?.getLong("hostId") ?: return@composable
                                var host by remember { mutableStateOf<Host?>(null) }
                                var password by remember { mutableStateOf<String?>(null) }
                                var mismatch by remember { mutableStateOf(false) }
                                LaunchedEffect(hostId) {
                                    host = repository.host(hostId)
                                    password = repository.passwordFor(hostId)
                                }
                                val currentHost = host
                                val currentPassword = password
                                when {
                                    currentHost != null && mismatch -> HostKeyMismatchScreen(
                                        connection = currentHost.toConnection(currentPassword.orEmpty()),
                                        onGoBack = { navController.popBackStack() },
                                    )
                                    currentHost != null && currentPassword != null -> SpikeScreen(
                                        appContext = applicationContext,
                                        appState = appState,
                                        connection = currentHost.toConnection(currentPassword),
                                        onForget = { navController.popBackStack() },
                                        onHostKeyMismatch = { mismatch = true },
                                        onHostKeyFingerprintLearned = { fingerprint ->
                                            scope.launch {
                                                repository.recordHostKeyFingerprint(hostId, fingerprint)
                                            }
                                        },
                                    )
                                    // else: host/password still loading -- a brief blank frame.
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Host.toConnection(password: String) = Connection(
    hostname = hostname,
    port = port,
    username = username,
    password = password,
    sessionName = sessionName,
    hostKeyFingerprint = hostKeyFingerprint,
)

private fun CoroutineScope.delete(host: Host, repository: HostRepository) {
    launch { repository.delete(host) }
}

@Composable
fun SpikeScreen(
    appContext: Context,
    appState: AppState,
    connection: Connection,
    onForget: () -> Unit,
    onHostKeyMismatch: () -> Unit,
    onHostKeyFingerprintLearned: (String) -> Unit,
) {
    var paneId by remember { mutableStateOf<String?>(null) }
    // Sessions-list state: the landing screen between Hosts and Terminal. The transport
    // is connected up front, `list-sessions`/`list-panes` fill this list, and tapping a
    // row attaches to it. attachedSession is the picked name (null until tapped).
    var sessions by remember { mutableStateOf<List<TmuxSession>>(emptyList()) }
    var sessionsError by remember { mutableStateOf<String?>(null) }
    var sessionsLoading by remember { mutableStateOf(true) }
    // One transcript per source, plus a "has this source ever been opened" latch.
    val chatEventsClaude = remember { mutableStateListOf<ChatEvent>() }
    val chatEventsHermes = remember { mutableStateListOf<ChatEvent>() }
    var claudeStarted by remember { mutableStateOf(false) }
    var hermesStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Trust-on-first-use.
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

    val terminalFeed = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }
    val paneTail = remember { StringBuilder() }
    var lastPrompt by remember { mutableStateOf<ChatEvent.PermissionPrompt?>(null) }

    fun appendPaneOutput(text: String) {
        paneTail.append(text)
        if (paneTail.length > 4000) paneTail.delete(0, paneTail.length - 4000)
        val detected = PermissionPromptDetector.detect(paneTail.toString())
        if (detected != null && detected != lastPrompt) {
            lastPrompt = detected
            chatEventsClaude.add(detected)
        }
        if (detected == null) lastPrompt = null
    }

    // Phase 1: connect the transport (no attach) and list the server's sessions.
    LaunchedEffect(Unit) {
        runCatching {
            session.connectTransport()
            val names = mutableListOf<TmuxSession>()
            session.execStream(SessionListParser.listSessionsCommand()).collect { line ->
                SessionListParser.parseSession(line)?.let { names.add(it) }
            }
            val commandsBySession = mutableMapOf<String, MutableList<String>>()
            session.execStream(SessionListParser.listPanesCommand()).collect { line ->
                SessionListParser.parsePane(line)?.let { (name, cmd) ->
                    commandsBySession.getOrPut(name) { mutableListOf() }.add(cmd)
                }
            }
            val processes = commandsBySession.mapValues { (_, cmds) -> SessionListParser.pickProcess(cmds) }
            names.map { it.copy(process = processes[it.name]) }
        }.onSuccess {
            sessions = it
            sessionsLoading = false
        }.onFailure { e ->
            sessionsLoading = false
            if (hostKeyVerifier.mismatch) {
                onHostKeyMismatch()
            } else {
                // sshj logs through slf4j and the APK ships no binding, so its own
                // diagnostics go nowhere; keeping only e.message also hides the cause.
                // Log the full stack and show the class + cause chain, otherwise a
                // failure is undiagnosable from the device.
                Log.e(TAG, "connectTransport / session listing failed", e)
                sessionsError = describeThrowable(e)
            }
        }
    }

    // Phase 2: on tap, attach to the picked session and start the control-mode reader.
    LaunchedEffect(appState.attachedSessionName) {
        val name = appState.attachedSessionName ?: return@LaunchedEffect
        runCatching {
            session.attachToSession(name)
            appState.connected = true
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
                            appendPaneOutput(parsed.text)
                        }
                    }
                }
            }
        }.onFailure { e ->
            appState.connected = false
            Log.e(TAG, "attach to '$name' failed", e)
            if (hostKeyVerifier.mismatch) {
                onHostKeyMismatch()
            } else {
                terminalFeed.emit("\r\nERROR: ${describeThrowable(e)}\r\n")
            }
        }
    }

    LaunchedEffect(appState.connected) {
        if (!appState.connected) return@LaunchedEffect
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

    val chatEvents: List<ChatEvent> = when (appState.viewMode) {
        "chat-claude" -> chatEventsClaude
        "chat-hermes" -> chatEventsHermes
        else -> chatEventsClaude + chatEventsHermes
    }

    val rawInputBridge = remember(paneId, session) {
        RawInputBridge(
            scope,
            session,
            target = { paneId ?: (appState.attachedSessionName ?: connection.sessionName) },
            enabled = { appState.inputMode == "raw" && !appState.readOnly },
            onBlocked = {
                // Distinguish the two reasons input is off, so the fix is obvious: the
                // read-only gate (top-bar lock icon) vs. compose mode owning the text field.
                val why = if (appState.readOnly) {
                    "read-only -- tap the lock icon in the top bar to type"
                } else {
                    "compose mode owns the keyboard -- switch Input mode to Raw"
                }
                terminalFeed.tryEmit("\r\nINPUT DISABLED ($why)\r\n")
            },
        )
    }

    DisposableEffect(appState.viewMode, appState.inputMode) {
        onDispose {
            if (appState.viewMode == "terminal" && appState.inputMode == "raw") rawInputBridge.flush()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session.close()
            // Clear app-level session state so the chrome greys out once we leave this
            // screen, and drop the injected callbacks.
            appState.connected = false
            appState.attachedSessionName = null
            appState.splitRight = null
            appState.splitDown = null
            appState.downloadFile = null
            appState.clearAll = null
            appState.forgetHost = null
            appState.sendSpecialKey = null
            appState.viewMode = "terminal"
            appState.inputMode = "raw"
            appState.readOnly = true
        }
    }

    // Register the session actions the app-level chrome invokes.
    DisposableEffect(session) {
        appState.splitRight = {
            scope.launch { runCatching { session.runRemote("tmux split-window -h") } }
        }
        appState.splitDown = {
            scope.launch { runCatching { session.runRemote("tmux split-window -v") } }
        }
        appState.downloadFile = { path ->
            scope.launch {
                runCatching {
                    val name = path.substringAfterLast('/').ifBlank { "download" }
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dest = File(downloads, name)
                    // ponytail: no auto-rename/overwrite -- the spec says collision shows a
                    // path error, so bail on an existing file rather than inventing suffixes.
                    if (dest.exists()) throw IOException("$name already exists in Downloads")
                    session.downloadFile(path, dest)
                }.onFailure { e ->
                    Toast.makeText(appContext, "Path error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        appState.clearAll = {
            // Terminal: clear the local emulator view (not the remote pane).
            terminalFeed.tryEmit("\u001b[2J\u001b[H")
            // Chat: drop both transcripts.
            chatEventsClaude.clear()
            chatEventsHermes.clear()
        }
        appState.forgetHost = { onForget() }
        appState.sendSpecialKey = { key ->
            scope.launch {
                runCatching {
                    session.sendKeys(
                        target = paneId ?: (appState.attachedSessionName ?: connection.sessionName),
                        keys = key,
                        literal = false,
                        submit = false,
                    )
                }.onFailure { e ->
                    // The toolbar path used to swallow failures entirely (bare runCatching),
                    // so a rejected command looked identical to a working but invisible one
                    // -- "no result after press keys". Surface it the same way the compose
                    // path does so a failure is never silent again.
                    Log.w("TmuxMobile", "sendSpecialKey '$key' failed", e)
                    terminalFeed.tryEmit("\r\nKEY ERROR ($key): ${e.message}\r\n")
                }
            }
        }
        onDispose {
            appState.splitRight = null
            appState.splitDown = null
            appState.downloadFile = null
            appState.clearAll = null
            appState.forgetHost = null
            appState.sendSpecialKey = null
        }
    }

    // Until a session is picked, show the Sessions list. Once attached, the terminal/chat
    // UI takes over. The app-level chrome supplies the top bar / drawer / overflow; this
    // screen only renders the content underneath it.
    if (appState.attachedSessionName == null) {
        SessionsScreen(
            sessions = sessions,
            loading = sessionsLoading,
            error = sessionsError,
            hostLabel = "${connection.username}@${connection.hostname}:${connection.port}",
            onAttach = { name -> appState.attachedSessionName = name },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp),
    ) {
        if (appState.viewMode == "terminal") {
            TerminalHost(
                modifier = Modifier.weight(1f),
                feed = terminalFeed,
                viewClient = rawInputBridge,
                fontSize = appState.prefs.fontSize,
                onFling = { direction -> rawInputBridge.onFling(direction) },
                focusable = appState.inputMode == "raw",
            )
            // Keyboard toolbar (operator override of the "no on-screen keyboard" rule).
            KeyboardToolbar(
                mode = appState.prefs.keyboardToolbar,
                onKey = { key -> appState.sendSpecialKey?.invoke(key) },
            )
            if (appState.inputMode == "compose") {
                TerminalComposeBar(
                    onSend = { text ->
                        scope.launch {
                            runCatching { session.sendKeys(paneId ?: (appState.attachedSessionName ?: connection.sessionName), text, literal = true) }
                                .onFailure { e ->
                                    terminalFeed.emit("\r\nSEND ERROR: ${e.message}\r\n")
                                }
                        }
                    },
                    readOnly = appState.readOnly,
                )
            }
        } else {
            ChatScreen(
                events = chatEvents,
                onSend = { text ->
                    scope.launch {
                        runCatching { session.sendKeys(appState.attachedSessionName ?: connection.sessionName, text) }
                            .onFailure { e ->
                                val target = if (appState.viewMode == "chat-claude") chatEventsClaude else chatEventsHermes
                                target.add(ChatEvent.AssistantMessage("SEND ERROR: ${e.message}"))
                            }
                    }
                },
                onAnswer = { keys ->
                    scope.launch {
                        runCatching {
                            session.sendKeys(
                                target = appState.attachedSessionName ?: connection.sessionName,
                                keys = keys,
                                literal = false,
                                submit = false,
                            )
                        }.onFailure { e ->
                            chatEventsClaude.add(ChatEvent.AssistantMessage("ANSWER ERROR: ${e.message}"))
                        }
                    }
                },
            )
        }
    }
}

/**
 * Renders a throwable as "ClassName: message" plus its cause chain. sshj wraps transport
 * failures, so the root cause ("Broken transport; encountered EOF") is usually one or two
 * levels down; showing only [Throwable.getMessage] hides which layer actually failed.
 */
private const val TAG = "TmuxMobile"

private fun describeThrowable(t: Throwable): String {
    val sb = StringBuilder()
    var current: Throwable? = t
    var depth = 0
    while (current != null && depth < 4) {
        if (depth > 0) sb.append("\n  caused by: ")
        sb.append(current.javaClass.simpleName)
        current.message?.takeIf { it.isNotBlank() }?.let { sb.append(": ").append(it) }
        current = current.cause
        depth++
    }
    return sb.toString()
}
