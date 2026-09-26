package com.tmuxmobile.phase0

import android.view.KeyEvent

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Pure key/gesture -> tmux key-name translation (the design spec's one unit-testable
 * piece of this sub-project). Printable characters are NOT handled here -- they arrive
 * via TerminalViewClient.onCodePoint (the IME path) and are sent as literal text.
 *
 * Every returned name is a single whitespace-free token, because callers interpolate it
 * directly into a control-mode command line (`send-keys -t <target> <name>`).
 */
object TmuxKeyMapper {

    fun swipeKeyName(direction: SwipeDirection): String = when (direction) {
        SwipeDirection.UP -> "Up"
        SwipeDirection.DOWN -> "Down"
        SwipeDirection.LEFT -> "Left"
        SwipeDirection.RIGHT -> "Right"
    }

    fun specialKeyName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        // tmux's name for Backspace is "BSpace"; "Backspace" is not a valid key name
        // and tmux would reject it.
        KeyEvent.KEYCODE_DEL -> "BSpace"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ESCAPE -> "Escape"
        else -> null
    }

    /**
     * Combines a base key name with the sticky Ctrl modifier into a tmux control key name.
     *
     * Ctrl on its own is only a modifier and sends nothing. tmux's bare "C" is NOT a
     * control key -- it types a literal "C" (verified over a live control-mode channel:
     * `send-keys -t <s> C` echoed `%output %21 C`, whereas `C-c` delivered a real
     * interrupt and killed the foreground process). So a Ctrl press arms the next key,
     * which lands here as "C-<key>".
     */
    fun controlKeyName(baseKeyName: String): String = when (baseKeyName) {
        // Ctrl-[ IS the ESC byte, so Ctrl+Esc collapses to the escape sequence.
        "Escape" -> "C-["
        "Tab" -> "C-I"
        else -> "C-$baseKeyName"
    }
}
