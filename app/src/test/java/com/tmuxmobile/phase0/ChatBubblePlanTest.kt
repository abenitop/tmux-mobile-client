package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WhatsApp-style grouping: consecutive messages from the same side form a run with tight
 * spacing and only the run's LAST bubble carries the tail.
 */
class ChatBubblePlanTest {

    private fun plan(vararg events: ChatEvent) = chatBubblePlan(events.toList())

    @Test
    fun `user messages are mine, assistant and tool calls are theirs`() {
        val rows = plan(
            ChatEvent.UserMessage("hi"),
            ChatEvent.AssistantMessage("hello"),
            ChatEvent.ToolCallChip("Read", "foo.kt"),
        )

        assertEquals(BubbleSide.Mine, rows[0].side)
        assertEquals(BubbleSide.Theirs, rows[1].side)
        assertEquals(BubbleSide.Theirs, rows[2].side)
    }

    @Test
    fun `only the last bubble of a run carries the tail`() {
        val rows = plan(
            ChatEvent.AssistantMessage("one"),
            ChatEvent.AssistantMessage("two"),
            ChatEvent.AssistantMessage("three"),
        )

        assertFalse(rows[0].showTail)
        assertFalse(rows[1].showTail)
        assertTrue("last of the run gets the tail", rows[2].showTail)
    }

    @Test
    fun `a sender change ends the run and moves the tail to the new run's end`() {
        val rows = plan(
            ChatEvent.AssistantMessage("one"),
            ChatEvent.UserMessage("two"),
            ChatEvent.UserMessage("three"),
        )

        // Assistant's lone bubble is the end of its own run...
        assertTrue(rows[0].showTail)
        // ...the user's second message carries the user run's tail, not the first.
        assertFalse(rows[1].showTail)
        assertTrue(rows[2].showTail)
    }

    @Test
    fun `spacing is tight inside a run and loose between runs`() {
        val rows = plan(
            ChatEvent.UserMessage("u1"),
            ChatEvent.UserMessage("u2"),
            ChatEvent.AssistantMessage("a1"),
            ChatEvent.UserMessage("u3"),
        )

        assertEquals("first bubble has nothing above it", 0, rows[0].gapAboveDp)
        assertEquals("tight within a run", 2, rows[1].gapAboveDp)
        assertEquals("loose across a sender change", 8, rows[2].gapAboveDp)
        assertEquals("loose across a sender change", 8, rows[3].gapAboveDp)
    }

    @Test
    fun `an empty transcript produces no rows`() {
        assertTrue(plan().isEmpty())
    }

    @Test
    fun `every row keeps its event so rendering never drops a message`() {
        val rows = plan(
            ChatEvent.UserMessage("u"),
            ChatEvent.ToolCallChip("Bash", "ls"),
        )
        assertEquals(2, rows.size)
        assertEquals(ChatEvent.UserMessage("u"), rows[0].event)
        assertEquals(ChatEvent.ToolCallChip("Bash", "ls"), rows[1].event)
    }
}
