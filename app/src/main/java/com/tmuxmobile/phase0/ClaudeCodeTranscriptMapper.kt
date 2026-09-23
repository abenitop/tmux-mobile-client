package com.tmuxmobile.phase0

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ClaudeCodeTranscriptMapper {
    fun mapLine(jsonLine: String): List<ChatEvent> {
        if (jsonLine.isBlank()) return emptyList()
        val root = try {
            JSONObject(jsonLine)
        } catch (e: JSONException) {
            return emptyList()
        }
        val type = root.optString("type")
        if (type != "user" && type != "assistant") return emptyList()

        val message = root.optJSONObject("message") ?: return emptyList()
        val role = message.optString("role")
        val content = message.opt("content")

        return when {
            role == "user" && content is String -> listOf(ChatEvent.UserMessage(content))
            role == "assistant" && content is JSONArray -> content.toChatEvents()
            else -> emptyList()
        }
    }

    private fun JSONArray.toChatEvents(): List<ChatEvent> {
        val events = mutableListOf<ChatEvent>()
        for (i in 0 until length()) {
            val block = optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> events += ChatEvent.AssistantMessage(block.optString("text"))
                "tool_use" -> {
                    val name = block.optString("name")
                    val input = block.optJSONObject("input")
                    val summary = input?.firstValueOrEmpty() ?: ""
                    events += ChatEvent.ToolCallChip(name, summary)
                }
                // ponytail: tool_result (and thinking) blocks are not rendered in the
                // simple version, per the design's explicit non-goals.
            }
        }
        return events
    }

    private fun JSONObject.firstValueOrEmpty(): String {
        val key = keys().asSequence().firstOrNull() ?: return ""
        return optString(key)
    }
}
