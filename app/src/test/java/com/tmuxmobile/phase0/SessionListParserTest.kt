package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListParserTest {

    @Test
    fun `list-sessions command single-quotes the format so the pipe is not a shell pipe`() {
        val cmd = SessionListParser.listSessionsCommand()
        assertTrue("format must be single-quoted", cmd.contains("'#{session_name}|#{session_windows}|#{session_attached}|#{session_activity}'"))
        assertTrue(cmd.startsWith("tmux list-sessions -F "))
    }

    @Test
    fun `parses a session line`() {
        val s = SessionListParser.parseSession("api-refactor|3|1|1790000000")
        assertEquals("api-refactor", s?.name)
        assertEquals(3, s?.windows)
        assertTrue(s?.attached == true)
        assertEquals(1790000000L, s?.activity)
    }

    @Test
    fun `attached is false when the flag is 0`() {
        val s = SessionListParser.parseSession("logs|1|0|1790000000")
        assertFalse(s?.attached == true)
    }

    @Test
    fun `returns null for a malformed line`() {
        assertNull(SessionListParser.parseSession("not-a-session-line"))
        assertNull(SessionListParser.parseSession("name|not-a-number|1|0"))
    }

    @Test
    fun `parses a pane line into session and command`() {
        assertEquals("api-refactor" to "claude", SessionListParser.parsePane("api-refactor|claude"))
    }

    @Test
    fun `pickProcess skips shells and prefers the interesting command`() {
        assertEquals("claude", SessionListParser.pickProcess(listOf("bash", "claude")))
        assertEquals("vim", SessionListParser.pickProcess(listOf("-zsh", "vim")))
        assertNull(SessionListParser.pickProcess(listOf("bash")))
        assertNull(SessionListParser.pickProcess(emptyList()))
    }

    @Test
    fun `formatActivity buckets by magnitude`() {
        val now = 1_000_000L
        assertEquals("just now", SessionListParser.formatActivity(now - 30, now))
        assertEquals("5m ago", SessionListParser.formatActivity(now - 300, now))
        assertEquals("3h ago", SessionListParser.formatActivity(now - 10_800, now))
        assertEquals("2d ago", SessionListParser.formatActivity(now - 172_800, now))
    }
}
