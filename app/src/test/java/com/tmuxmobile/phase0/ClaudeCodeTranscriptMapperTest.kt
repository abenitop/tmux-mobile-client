package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeCodeTranscriptMapperTest {

    @Test
    fun `maps a plain user string message`() {
        val line = """{"type":"user","message":{"role":"user","content":"continue"}}"""
        assertEquals(listOf(ChatEvent.UserMessage("continue")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps an assistant text block`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Hello there"}]}}"""
        assertEquals(listOf(ChatEvent.AssistantMessage("Hello there")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps an assistant tool_use block to a collapsed chip`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_01","name":"Bash","input":{"command":"ls -la"}}]}}"""
        assertEquals(listOf(ChatEvent.ToolCallChip("Bash", "ls -la")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps multiple content blocks in one assistant line to multiple events in order`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Checking now"},{"type":"tool_use","id":"toolu_02","name":"Read","input":{"file_path":"/tmp/x.txt"}}]}}"""
        assertEquals(
            listOf(ChatEvent.AssistantMessage("Checking now"), ChatEvent.ToolCallChip("Read", "/tmp/x.txt")),
            ClaudeCodeTranscriptMapper.mapLine(line)
        )
    }

    @Test
    fun `ignores tool_result blocks and non-message line types`() {
        val toolResult = """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_01","content":"ok"}]}}"""
        val summaryLine = """{"type":"summary","summary":"Some earlier context"}"""
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(toolResult))
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(summaryLine))
    }

    @Test
    fun `ignores malformed or blank lines rather than crashing`() {
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine("not valid json"))
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(""))
    }
}
