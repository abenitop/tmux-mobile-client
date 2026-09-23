package com.tmuxmobile.phase0

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesMessageMapperTest {

    @Test
    fun `maps a plain user message`() {
        val row = JSONObject("""{"id":7470,"role":"user","content":"exit","tool_calls":null,"tool_name":null}""")
        assertEquals(listOf(ChatEvent.UserMessage("exit")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `maps a plain assistant message`() {
        val row = JSONObject("""{"id":7471,"role":"assistant","content":"Goodbye.","tool_calls":null,"tool_name":null}""")
        assertEquals(listOf(ChatEvent.AssistantMessage("Goodbye.")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `maps an assistant row with both text and a tool call, in order`() {
        val row = JSONObject(
            """{"id":7553,"role":"assistant","content":"Retrying.","tool_calls":"[{\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"terminal\", \"arguments\": \"{\\\"command\\\": \\\"git push\\\"}\"}}]","tool_name":null}"""
        )
        assertEquals(
            listOf(ChatEvent.AssistantMessage("Retrying."), ChatEvent.ToolCallChip("terminal", "git push")),
            HermesMessageMapper.mapRow(row)
        )
    }

    @Test
    fun `maps a tool-call-only row with blank content to just the chip`() {
        val row = JSONObject(
            """{"id":7555,"role":"assistant","content":"","tool_calls":"[{\"id\": \"call_2\", \"type\": \"function\", \"function\": {\"name\": \"terminal\", \"arguments\": \"{\\\"command\\\": \\\"git log\\\"}\"}}]","tool_name":null}"""
        )
        assertEquals(listOf(ChatEvent.ToolCallChip("terminal", "git log")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `ignores tool-role rows -- results are not rendered in the simple version`() {
        val row = JSONObject("""{"id":7472,"role":"tool","content":"output text","tool_calls":null,"tool_name":"terminal"}""")
        assertEquals(emptyList<ChatEvent>(), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `ignores malformed tool_calls JSON rather than crashing`() {
        val row = JSONObject("""{"id":7556,"role":"assistant","content":"","tool_calls":"not valid json","tool_name":null}""")
        assertEquals(emptyList<ChatEvent>(), HermesMessageMapper.mapRow(row))
    }
}
