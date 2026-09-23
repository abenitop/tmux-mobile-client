package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.Flow

/**
 * Renders SSH-sourced tmux pane output via Termux's TerminalView. A no-op local
 * TerminalSession ("/system/bin/cat", no args) exists only to satisfy the library --
 * all real content comes from [feed], fed directly into the session's emulator. See
 * this plan's Task 1 findings for why each setup step below is ordered this way.
 */
@Composable
fun TerminalHost(
    modifier: Modifier = Modifier,
    feed: Flow<String>,
    viewClient: TerminalViewClient,
) {
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var view by remember { mutableStateOf<TerminalView?>(null) }
    // Holds the live session for the dispose callback. A separate holder rather than
    // reading `session` inside onDispose, which would capture the initial null.
    val sessionRef = remember { SessionRef() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TerminalView(context, null).apply {
                // Order matters (Task 1 findings): client and text size must be set
                // before attachSession(), which lazily creates the renderer and reads
                // the client during updateSize().
                setTerminalViewClient(viewClient)
                setTextSize(30)
                // TerminalView paints no background once an emulator is attached, so
                // default-color text is nearly invisible against the app's window --
                // must set this explicitly (Task 1 finding).
                setBackgroundColor(0xFF000000.toInt())
                // Hardware/injected key events never arrive without this; the
                // soft-keyboard/IME path is unaffected either way (Task 1 finding).
                isFocusable = true
                isFocusableInTouchMode = true
                doOnLayout {
                    val s = TerminalSession(
                        "/system/bin/cat",
                        "/",
                        arrayOf("cat"),
                        arrayOf("TERM=xterm-256color"),
                        2000,
                        NoOpTerminalSessionClient,
                    )
                    attachSession(s)
                    sessionRef.session = s
                    session = s
                }
                view = this
            }
        },
        // Re-applied on every client change. Without this the factory runs once and a
        // later viewClient (e.g. RawInputBridge remembered against a newly-learned
        // paneId) would never reach the TerminalView.
        update = { terminalView -> terminalView.setTerminalViewClient(viewClient) },
    )

    LaunchedEffect(session) {
        val activeSession = session ?: return@LaunchedEffect
        feed.collect { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            activeSession.emulator.append(bytes, bytes.size)
            view?.onScreenUpdated()
        }
    }

    // Not in the plan's snippet, but without it every Terminal-mode entry leaks the
    // no-op "cat" process: leaving the composition never calls finishIfRunning().
    DisposableEffect(Unit) {
        onDispose {
            sessionRef.session?.finishIfRunning()
            sessionRef.session = null
        }
    }
}

private class SessionRef {
    var session: TerminalSession? = null
}

/**
 * Placeholder client for Task 2 (rendering only, no input yet). Task 3 replaces this
 * with RawInputBridge; until then the Terminal view accepts no key input at all, which
 * is exactly the behaviour Task 2 is meant to verify.
 */
object NoOpTerminalViewClient : TerminalViewClient {
    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "stack", e) }
}

/**
 * The no-op local session must never exit (Task 1 finding: exit closes the emulator's
 * queues and appends "[Process completed]"), so onSessionFinished here would only ever
 * fire on an unexpected crash of "/system/bin/cat" -- logging is enough, there's
 * nothing to recover.
 */
private object NoOpTerminalSessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {}
    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {
        android.util.Log.w("TerminalHost", "no-op local session exited unexpectedly")
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
    override fun onPasteTextFromClipboard(session: TerminalSession) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "stack", e) }
}
