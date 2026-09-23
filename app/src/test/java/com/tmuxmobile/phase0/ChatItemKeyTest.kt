package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the LazyColumn key. Content-only keys collide on a real transcript
 * (a tool-call log repeats identical entries constantly), and duplicate keys make
 * LazyColumn throw IllegalArgumentException at runtime.
 *
 * The fixtures below are not invented: they are the exact content strings that recur
 * most often in the live Hermes session, taken from
 * `SELECT content, COUNT(*) ... GROUP BY content HAVING c > 1` against
 * ~/.hermes/state.db — e.g. `{"output": "", "exit_code": 0, "error": null}` appears 8
 * times, and that session had 274 duplicate groups across 624 rows.
 */
class ChatItemKeyTest {

    // Verbatim duplicates observed in the live session.
    private val repeatedToolResult = "{\"output\": \"\", \"exit_code\": 0, \"error\": null}"
    private val repeatedDone = "Done."

    @Test
    fun `keys are unique when identical content repeats`() {
        val events = listOf(
            ChatEvent.AssistantMessage(repeatedToolResult),
            ChatEvent.ToolCallChip("terminal", repeatedToolResult),
            ChatEvent.AssistantMessage(repeatedToolResult),
            ChatEvent.AssistantMessage(repeatedDone),
            ChatEvent.UserMessage(repeatedDone),
            ChatEvent.AssistantMessage(repeatedDone),
        )

        val keys = events.mapIndexed { i, e -> itemKey(i, e) }

        assertEquals("keys must be unique or LazyColumn throws", keys.size, keys.toSet().size)
    }

    @Test
    fun `keys stay stable for earlier items when the list is appended to`() {
        // The displayed list can be a concatenation ("Claude + Hermes"), so an append must
        // not re-key the prefix -- that would defeat the purpose of using keys at all.
        val base = listOf(
            ChatEvent.AssistantMessage(repeatedDone),
            ChatEvent.UserMessage(repeatedDone),
        )
        val grown = base + listOf(ChatEvent.AssistantMessage("a later message"))

        val before = base.mapIndexed { i, e -> itemKey(i, e) }
        val after = grown.mapIndexed { i, e -> itemKey(i, e) }.take(before.size)

        assertEquals(before, after)
    }

    @Test
    fun `all three event shapes produce distinct keys at the same index`() {
        // Same content, same index, different type -- these must not collide, which is
        // what the type prefix is for.
        val keys = listOf(
            itemKey(0, ChatEvent.UserMessage("x")),
            itemKey(0, ChatEvent.AssistantMessage("x")),
            itemKey(0, ChatEvent.ToolCallChip("x", "x")),
        )
        assertEquals(3, keys.toSet().size)
    }
}
