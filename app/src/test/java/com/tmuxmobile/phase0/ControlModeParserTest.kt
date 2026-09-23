package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlModeParserTest {

    @Test
    fun `parses a simple output line`() {
        val result = ControlModeParser.parseLine("%output %3 hello world")
        assertEquals(PaneOutput("%3", "hello world"), result)
    }

    @Test
    fun `returns null for non-output control lines`() {
        assertNull(ControlModeParser.parseLine("%begin 123 456 1"))
        assertNull(ControlModeParser.parseLine("%window-add @1"))
        assertNull(ControlModeParser.parseLine(""))
    }

    @Test
    fun `decodes an octal-escaped newline`() {
        val result = ControlModeParser.parseLine("%output %3 line1\\012line2")
        assertEquals("line1\nline2", result?.text)
    }

    @Test
    fun `decodes an escaped backslash`() {
        val result = ControlModeParser.parseLine("%output %3 path\\134to\\134file")
        assertEquals("path\\to\\file", result?.text)
    }

    @Test
    fun `handles multiple panes independently`() {
        val a = ControlModeParser.parseLine("%output %1 from pane one")
        val b = ControlModeParser.parseLine("%output %2 from pane two")
        assertEquals("%1", a?.paneId)
        assertEquals("%2", b?.paneId)
    }
}
