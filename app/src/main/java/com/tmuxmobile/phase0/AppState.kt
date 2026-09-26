package com.tmuxmobile.phase0

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide, non-secret UI preferences (font size, dense top bar, keyboard toolbar).
 * Plain [android.content.SharedPreferences] is enough — these are not credentials, so no
 * Keystore. A separate file from [EncryptedPasswordStore] on purpose: UI prefs must not
 * clear (or be cleared by) the secrets store.
 *
 * Values are held in Compose state and written through to disk on set. They MUST be state
 * and not a bare `prefs.get*()` read: the overflow menu writes these while the screen that
 * consumes them is already composed, and a plain getter gives no recomposition trigger —
 * selecting "Keyboard toolbar: Always" appeared to do nothing until the screen was left
 * and re-entered. This also fixes the submenu checkmark, which reads the same getter.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val fontSizeState = mutableStateOf(prefs.getInt("font_size", DEFAULT_FONT_SIZE))
    var fontSize: Int
        get() = fontSizeState.value
        set(v) {
            fontSizeState.value = v
            prefs.edit().putInt("font_size", v).apply()
        }

    private val denseBarState = mutableStateOf(prefs.getString("dense_bar", "auto") ?: "auto")
    var denseBar: String
        get() = denseBarState.value
        set(v) {
            denseBarState.value = v
            prefs.edit().putString("dense_bar", v).apply()
        }

    private val toolbarState = mutableStateOf(prefs.getString("keyboard_toolbar", "auto") ?: "auto")
    var keyboardToolbar: String
        get() = toolbarState.value
        set(v) {
            toolbarState.value = v
            prefs.edit().putString("keyboard_toolbar", v).apply()
        }

    companion object {
        /** Selectable sizes are 8..24 step 2 (the operator's spec). */
        val FONT_SIZES: List<Int> = (8..24 step 2).toList()
        // ponytail: the previous hardcoded text size was 30, outside the spec's 8..24
        // range; default to the top of the range and let the operator raise it if needed.
        const val DEFAULT_FONT_SIZE = 24
    }
}

/**
 * Shared UI state between the app-level chrome (drawer / 3-dot, composed around the whole
 * NavHost) and SpikeScreen (which owns the live SSH/tmux session). Without this the chrome
 * cannot know whether a session is attached, so it cannot grey out Split / Clear / Download
 * / Forget host on the Hosts screen — the exact gap that hid the chrome from the operator.
 */
class AppState(val prefs: AppPrefs) {
    // ---- session presence (chrome reads these for grey-out) ----
    var connected by mutableStateOf(false)
    var hostsEmpty by mutableStateOf(true)
    var attachedSessionName by mutableStateOf<String?>(null)

    // ---- terminal sub-state, single source of truth (moved out of SpikeScreen) ----
    var viewMode by mutableStateOf("terminal") // terminal | chat-claude | chat-hermes
    var inputMode by mutableStateOf("raw") // raw | compose
    var readOnly by mutableStateOf(true)

    // ---- download dialog ----
    var showDownloadDialog by mutableStateOf(false)

    // ---- session actions, injected by SpikeScreen while a session is attached; null
    // otherwise, which is exactly what "grey out" keys off ----
    var splitRight: (() -> Unit)? = null
    var splitDown: (() -> Unit)? = null
    var downloadFile: ((String) -> Unit)? = null
    var clearAll: (() -> Unit)? = null
    var forgetHost: (() -> Unit)? = null
    // Send a single special key (Esc / Tab / arrows / Ctrl-<x>) into the pane. Null when
    // no session is attached.
    var sendSpecialKey: ((String) -> Unit)? = null
}
