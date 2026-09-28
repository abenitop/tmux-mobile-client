package com.tmuxmobile.phase0

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Raw-mode input: hardware/injected key events and system-keyboard codepoints go
 * straight to the SSH session instead of the dummy local TerminalSession (Task 1
 * verified both paths are interceptable and do not leak to the dummy process).
 *
 * [enabled] gates the whole bridge. Terminal mode shows a compose bar whose text field
 * also takes IME input, so without this gate typing a compose message would ALSO be fed
 * through [onCodePoint] into the pane -- sending every character twice, and to the
 * wrong place. While compose is active the bridge must stay silent.
 *
 * Rapid keystrokes are coalesced per [FLUSH_MS] window: each control-mode command needs
 * its own SSH write+flush (tmux reads only the first command per read), so one write per
 * character means one round-trip per character. Batching a burst into a single
 * `send-keys -l` write keeps typing responsive. Order is preserved because the pending
 * text is appended in arrival order and the send itself is serialized on [scope].
 */
class RawInputBridge(
    private val scope: CoroutineScope,
    private val session: SshSpikeSession,
    private val target: () -> String?,
    private val enabled: () -> Boolean = { true },
    /**
     * Invoked (at most once per stall) when input arrives while [enabled] is false, so the
     * user is told WHY their keystrokes vanish instead of seeing nothing happen. Reported
     * through the terminal feed by the caller.
     */
    private val onBlocked: () -> Unit = {},
) : TerminalViewClient {

    private val handler = Handler(Looper.getMainLooper())
    private val pendingText = StringBuilder()
    private var flushScheduled = false
    private var blockedNoticeShown = false

    private val flushRunnable = Runnable { flushPending() }

    fun onFling(direction: SwipeDirection) = send(TmuxKeyMapper.swipeKeyName(direction))

    /**
     * Single choke point for the "input disabled" state. Every input callback funnels
     * through here so a blocked keystroke is announced once rather than silently dropped
     * (which is what made this look like a broken keyboard).
     */
    private fun blocked(): Boolean {
        if (blockedNoticeShown) return true
        blockedNoticeShown = true
        onBlocked()
        return true
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        // While composing OR read-only, swallow key events rather than returning false.
        // TerminalView falls through to `mTermSession.write(...)` / inputCodePoint() when
        // the client returns false, which would push the keystrokes into the DUMMY local
        // session and echo them into the shared emulator on top of the real pane content.
        if (!enabled()) return blocked()
        blockedNoticeShown = false
        val name = TmuxKeyMapper.specialKeyName(keyCode) ?: return false
        // Flush any buffered text FIRST: a special key must not jump ahead of the
        // characters typed before it (e.g. "ls" + Enter).
        flushPending()
        send(name)
        return true
    }

    // Consumed unconditionally: returning false here would let the event fall through to
    // the dummy local session, which would then echo it into the emulator.
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = true

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // Same reasoning as onKeyDown: consume, but do not forward while blocked.
        if (!enabled()) return blocked()
        blockedNoticeShown = false
        queueLiteral(String(Character.toChars(codePoint)))
        return true
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        if (!enabled()) return blocked()
        flushPending()
        send("Escape")
        return true
    }

    private fun queueLiteral(text: String) {
        pendingText.append(text)
        if (!flushScheduled) {
            flushScheduled = true
            handler.postDelayed(flushRunnable, FLUSH_MS)
        }
    }

    private fun flushPending() {
        if (flushScheduled) {
            handler.removeCallbacks(flushRunnable)
            flushScheduled = false
        }
        if (pendingText.isEmpty()) return
        val text = pendingText.toString()
        pendingText.setLength(0)
        // submit = false: this is per-keystroke text, NOT a line to submit. Appending
        // Enter would submit a line on every character typed.
        send(text, literal = true, submit = false)
    }

    /** Flushes buffered text and cancels any pending timer. Call when leaving Terminal mode. */
    fun flush() = flushPending()

    private fun send(keys: String, literal: Boolean = false, submit: Boolean = true) {
        val t = target() ?: return
        scope.launch {
            runCatching { session.sendKeys(t, keys, literal, submit) }
                .onFailure { Log.w(TAG, "sendKeys failed for '$keys'", it) }
        }
    }

    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    /**
     * Must be TRUE, despite TYPE_NULL being nominally the "correct" input type.
     *
     * TerminalView.onCreateInputConnection picks EditorInfo.inputType from this:
     * true -> TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS,
     * false -> TYPE_NULL. Returning false therefore declares "not a text editor", and
     * the library's own comment on that branch is the bug report we hit:
     *
     *   "Some keyboards seems do not reset the internal state on TYPE_NULL.
     *    Affects mostly Samsung stock keyboards." (termux-app#686)
     *
     * Observed on the S23: the soft keyboard renders and keys visibly press, but no
     * character ever reaches onCodePoint, so nothing is typed. With a char-based input
     * type the IME routes through commitText -> sendTextToTerminal -> inputCodePoint ->
     * onCodePoint (which forwards to the pane) instead of going stale.
     *
     * Verified at the source level in TerminalView.java: inputCodePoint returns early
     * when onCodePoint is handled, and otherwise falls through to
     * mTermSession.writeCodePoint() -- the DUMMY local "cat" session. So any code point
     * this bridge fails to consume lands in the local session, never in the SSH pane.
     */
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "stack", e) }

    private companion object {
        const val TAG = "RawInputBridge"

        /**
         * Coalescing window for burst typing. Small enough that a lone keystroke still
         * feels immediate (it is flushed at the next event or after this delay), large
         * enough to collapse a fast burst into a handful of SSH round-trips.
         */
        const val FLUSH_MS = 30L
    }
}
