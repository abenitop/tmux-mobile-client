package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixtures use the field names observed in a real Claude Code transcript
 * (`Edit`: file_path/old_string/new_string/replace_all; `Write`: file_path/content),
 * not invented ones.
 */
class ClaudeCodeEditMappingTest {

    @Test
    fun `maps an Edit tool_use block to a diff card`() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_01","name":"Edit","input":{"file_path":"/app/Main.kt","old_string":"val a = 1","new_string":"val a = 2","replace_all":false}}]}}
        """.trimIndent()

        assertEquals(
            listOf(
                ChatEvent.DiffCard(
                    name = "Edit",
                    filePath = "/app/Main.kt",
                    oldText = "val a = 1",
                    newText = "val a = 2",
                    isNewFile = false,
                ),
            ),
            ClaudeCodeTranscriptMapper.mapLine(line),
        )
    }

    @Test
    fun `maps a Write tool_use block to a new-file diff card`() {
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_02","name":"Write","input":{"file_path":"/app/New.kt","content":"package app\n"}}]}}
        """.trimIndent()

        assertEquals(
            listOf(
                ChatEvent.DiffCard(
                    name = "Write",
                    filePath = "/app/New.kt",
                    oldText = "",
                    newText = "package app\n",
                    isNewFile = true,
                ),
            ),
            ClaudeCodeTranscriptMapper.mapLine(line),
        )
    }

    @Test
    fun `a Write to an existing path is still reported as a new-file card`() {
        // The transcript gives no "existed before" flag for Write, so isNewFile means
        // "no old content to show" -- the UI labels it accordingly rather than claiming
        // the file was newly created.
        val line = """
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_03","name":"Write","input":{"file_path":"/app/Existing.kt","content":"overwritten\n"}}]}}
        """.trimIndent()

        val event = ClaudeCodeTranscriptMapper.mapLine(line).single() as ChatEvent.DiffCard
        assertEquals("", event.oldText)
        assertEquals("overwritten\n", event.newText)
    }

    @Test
    fun `Read and Bash still map to one-line chips, not diff cards`() {
        val read = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t1","name":"Read","input":{"file_path":"/tmp/x.txt"}}]}}"""
        val bash = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t2","name":"Bash","input":{"command":"ls -la"}}]}}"""

        assertEquals(listOf(ChatEvent.ToolCallChip("Read", "/tmp/x.txt")), ClaudeCodeTranscriptMapper.mapLine(read))
        assertEquals(listOf(ChatEvent.ToolCallChip("Bash", "ls -la")), ClaudeCodeTranscriptMapper.mapLine(bash))
    }

    @Test
    fun `an Edit with a missing field degrades to a chip instead of throwing`() {
        // The transcript format is internal and undocumented (per the product spec): parse
        // tolerantly. A malformed Edit must not take down the chat view.
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t3","name":"Edit","input":{"file_path":"/app/X.kt"}}]}}"""

        val events = ClaudeCodeTranscriptMapper.mapLine(line)
        assertEquals(1, events.size)
        assertEquals(ChatEvent.ToolCallChip("Edit", "/app/X.kt"), events.single())
    }

    @Test
    fun `an Edit with no input at all degrades to a chip`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t4","name":"Edit"}]}}"""
        assertEquals(1, ClaudeCodeTranscriptMapper.mapLine(line).size)
    }

    @Test
    fun `an empty-string new_string still produces a diff card`() {
        // Deleting everything is a legitimate edit; treating "" as "missing" would
        // mis-map it to a chip.
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t5","name":"Edit","input":{"file_path":"/app/Y.kt","old_string":"gone","new_string":""}}]}}"""

        val event = ClaudeCodeTranscriptMapper.mapLine(line).single() as ChatEvent.DiffCard
        assertEquals("gone", event.oldText)
        assertEquals("", event.newText)
    }
}
