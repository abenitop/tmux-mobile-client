package com.tmuxmobile.phase0

import org.json.JSONArray
import org.json.JSONObject

object HermesMessageMapper {
    fun mapRow(row: JSONObject): List<ChatEvent> {
        val role = row.optString("role")
        val content = row.optString("content")
        val events = mutableListOf<ChatEvent>()

        when (role) {
            "user" -> if (content.isNotBlank()) events += ChatEvent.UserMessage(content)
            "assistant" -> {
                if (content.isNotBlank()) events += ChatEvent.AssistantMessage(content)
                val toolCallsRaw = row.optString("tool_calls", "null")
                if (toolCallsRaw != "null") {
                    events += parseToolCalls(toolCallsRaw)
                }
            }
            // ponytail: "tool" role rows are results, not rendered in the simple version.
        }
        return events
    }

    private fun parseToolCalls(toolCallsJson: String): List<ChatEvent> {
        val calls = try {
            JSONArray(toolCallsJson)
        } catch (e: Exception) {
            return emptyList()
        }
        val events = mutableListOf<ChatEvent>()
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val function = call.optJSONObject("function") ?: continue
            val name = function.optString("name")
            val summary = try {
                val args = JSONObject(function.optString("arguments", "{}"))
                val key = args.keys().asSequence().firstOrNull()
                key?.let { args.optString(it) } ?: ""
            } catch (e: Exception) {
                ""
            }
            events += ChatEvent.ToolCallChip(name, summary)
        }
        return events
    }
}
