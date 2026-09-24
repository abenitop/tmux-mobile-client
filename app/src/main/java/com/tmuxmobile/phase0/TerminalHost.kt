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
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

/** Minimum drag distance before a gesture counts as a direction, not a tap. */
private const val MIN_SWIPE_PX = 80f

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
    onFling: (SwipeDirection) -> Unit = {},
    focusable: Boolean = true,
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
                // must set this explicitly (Task 1 finding). #0F0E0C is the v2 terminal
                // bg token (warm near-black, not pure #000).
                setBackgroundColor(0xFF0F0E0C.toInt())
                // Hardware/injected key events never arrive without focus. When the
                // compose bar owns input, focus is handed to its text field instead --
                // see the focus block below.
                isFocusable = true
                isFocusableInTouchMode = true
                // Swipe/fling is NOT part of TerminalViewClient (Task 1 verified), so it
                // needs a detector on the view itself. The listener ALWAYS returns false:
                // it only observes. Consuming the event here would stop TerminalView's own
                // onTouchEvent from ever seeing it, which would break tap-to-focus,
                // long-press (mClient.onLongPress) and mouse reporting. A deliberate drag
                // is forwarded as an arrow key; a small minimum distance keeps taps and
                // text-selection drags from registering as directions.
                val swipeDetector = GestureDetector(context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onFling(
                            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float,
                        ): Boolean {
                            val start = e1 ?: return false
                            val dx = e2.x - start.x
                            val dy = e2.y - start.y
                            if (abs(dx) < MIN_SWIPE_PX && abs(dy) < MIN_SWIPE_PX) return false
                            onFling(
                                if (abs(dx) > abs(dy)) {
                                    if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                } else {
                                    if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
                                }
                            )
                            return true
                        }
                    })
                setOnTouchListener { _, event ->
                    swipeDetector.onTouchEvent(event)
                    false
                }
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
                // Take focus as soon as the view is attached. requestFocus() in this
                // factory is too early (the view isn't in the hierarchy yet, and
                // Compose's focus system wins), but a plain tap works -- which made raw
                // mode look dead until the terminal was tapped. Verified on device: the
                // focused node at launch was the "Chat: Hermes" button and injected
                // arrow keys were consumed as focus navigation; after focusing the
                // terminal, the same keys moved the target TUI's cursor.
                doOnAttach { requestFocus() }
                view = this
            }
        },
        // Re-applied on every client change. Without this the factory runs once and a
        // later viewClient (e.g. RawInputBridge remembered against a newly-learned
        // paneId) would never reach the TerminalView.
        update = { terminalView -> terminalView.setTerminalViewClient(viewClient) },
    )

    // Hand focus to (or take it from) the terminal as input ownership changes. Without
    // the release path, compose mode opens with focus still on the terminal -- which
    // never shows the soft keyboard, so the user taps the text field, the IME covers the
    // terminal, and the terminal's own text-selection path never gets a long-press.
    DisposableEffect(focusable, view) {
        val v = view ?: return@DisposableEffect onDispose {}
        if (focusable) {
            v.requestFocus()
            onDispose {}
        } else {
            v.clearFocus()
            onDispose {}
        }
    }

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
