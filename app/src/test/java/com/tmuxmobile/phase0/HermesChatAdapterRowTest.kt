package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the two things the plan's Hermes adapter got wrong (both found by running it
 * against the live db, not by reading):
 *
 *  1. `sqlite3 -json` pretty-prints an ARRAY ACROSS MULTIPLE LINES, so parsing each
 *     line as a JSON array silently yielded nothing for any batch of 2+ rows. The poll
 *     loop now emits one `json_object(...)` per line instead.
 *  2. Fetching rows must be a real one-object-per-line stream, not just the first row.
 */
class HermesChatAdapterRowTest {

    @Test
    fun `parses one json_object row per line`() {
        val line = """{"id":7775,"role":"assistant","content":"Task 1 done.","tool_calls":null,"tool_name":null}"""
        assertEquals(listOf(ChatEvent.AssistantMessage("Task 1 done.")), hermesRowToEvents(line))
    }

    @Test
    fun `parses a row carrying a tool call`() {
        val line =
            """{"id":7779,"role":"assistant","content":"","tool_calls":"[{\"function\": {\"name\": \"read_file\", \"arguments\": \"{\\\"path\\\": \\\"/tmp/x\\\"}\"}}]","tool_name":null}"""
        assertEquals(listOf(ChatEvent.ToolCallChip("read_file", "/tmp/x")), hermesRowToEvents(line))
    }

    @Test
    fun `ignores a line from pretty-printed array output rather than half-parsing it`() {
        // Exactly what the plan's parseRowsLine received: line 1 of an array. It must
        // not be mistaken for a message.
        assertEquals(emptyList<ChatEvent>(), hermesRowToEvents("""[{"id":7790,"role":"tool","content":"x"},"""))
    }

    @Test
    fun `ignores blank and malformed lines`() {
        assertEquals(emptyList<ChatEvent>(), hermesRowToEvents(""))
        assertEquals(emptyList<ChatEvent>(), hermesRowToEvents("   "))
        assertEquals(emptyList<ChatEvent>(), hermesRowToEvents("not json at all"))
    }

    @Test
    fun `poll loop selects a live session before a newer dead one`() {
        // The plan's ORDER BY started_at DESC picked a 2-message session that had already
        // ended (end_reason=tui_shutdown); the live conversation had ended_at IS NULL.
        assertEquals(true, hermesPollLoop().contains("(ended_at IS NULL) DESC"))
    }

    @Test
    fun `poll loop emits one json_object row per line and advances the offset`() {
        val loop = hermesPollLoop()
        assertEquals(true, loop.contains("json_object("))
        assertEquals(false, loop.contains("-json"))
        assertEquals(true, loop.contains("id > "))
    }
}
