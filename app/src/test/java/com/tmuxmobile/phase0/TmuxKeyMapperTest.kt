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
