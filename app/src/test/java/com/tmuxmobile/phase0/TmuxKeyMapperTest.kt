package com.tmuxmobile.phase0

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmuxKeyMapperTest {

    @Test
    fun `maps swipe directions to tmux arrow key names`() {
        assertEquals("Up", TmuxKeyMapper.swipeKeyName(SwipeDirection.UP))
        assertEquals("Down", TmuxKeyMapper.swipeKeyName(SwipeDirection.DOWN))
        assertEquals("Left", TmuxKeyMapper.swipeKeyName(SwipeDirection.LEFT))
        assertEquals("Right", TmuxKeyMapper.swipeKeyName(SwipeDirection.RIGHT))
    }

    @Test
    fun `maps known special key codes to tmux key names`() {
        assertEquals("Up", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals("Down", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals("Left", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals("Right", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals("Enter", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_ENTER))
        assertEquals("BSpace", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DEL))
        assertEquals("Tab", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_TAB))
        assertEquals("Escape", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_ESCAPE))
    }

    /**
     * The toolbar's Ctrl is sticky: it arms the next key, which is sent as a control key.
     * A bare "C" must never be emitted -- tmux treats it as a literal "C" character, so
     * the old always-send-"C" behaviour typed a letter instead of acting as a modifier.
     */
    @Test
    fun `controlKeyName combines a base key with Ctrl and never yields bare C`() {
        assertEquals("C-c", TmuxKeyMapper.controlKeyName("c"))
        assertEquals("C-d", TmuxKeyMapper.controlKeyName("d"))
        assertEquals("C-Up", TmuxKeyMapper.controlKeyName("Up"))
        // Ctrl-[ and Ctrl-I are the canonical encodings of ESC and Tab.
        assertEquals("C-[", TmuxKeyMapper.controlKeyName("Escape"))
        assertEquals("C-I", TmuxKeyMapper.controlKeyName("Tab"))
        // A modifier on its own is not a key: the toolbar must never send plain "C".
        assertEquals("C-C", TmuxKeyMapper.controlKeyName("C"))
    }

    @Test
    fun `returns null for printable keys -- those arrive via onCodePoint, not here`() {
        assertNull(TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_A))
        assertNull(TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun `every mapped name is a single safe token -- none may contain whitespace or quotes`() {
        // These strings are interpolated straight into a control-mode command line
        // (send-keys -t <target> <name>), so a name containing a space, quote,
        // backslash or newline would be re-parsed as extra arguments by tmux.
        val names = SwipeDirection.entries.map { TmuxKeyMapper.swipeKeyName(it) } +
            listOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
            ).mapNotNull { TmuxKeyMapper.specialKeyName(it) }
        for (name in names) {
            assertEquals("unsafe key name: $name", -1, name.indexOfFirst { it.isWhitespace() || it == '"' || it == '\\' })
        }
    }
}
