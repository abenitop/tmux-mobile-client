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
                "tool_use" -> events += toolUseEvent(block)
                // ponytail: tool_result (and thinking) blocks are not rendered in the
                // simple version, per the design's explicit non-goals.
            }
        }
        return events
    }

    /**
     * `Edit`/`Write` render as inline diff cards (product spec's rendering table); every
     * other tool collapses to a one-line chip.
     *
     * Degrades to a chip whenever the diff payload isn't fully present. The transcript
     * format is internal and undocumented, so a malformed or future-shaped Edit must not
     * take down the whole chat view -- and a chip still shows the tool ran.
     */
    private fun toolUseEvent(block: JSONObject): ChatEvent {
        val name = block.optString("name")
        val input = block.optJSONObject("input")

        if (input != null) {
            when (name) {
                "Edit" -> {
                    val path = input.optionalString("file_path")
                    val oldText = input.optionalString("old_string")
                    val newText = input.optionalString("new_string")
                    if (path != null && oldText != null && newText != null) {
                        return ChatEvent.DiffCard(name, path, oldText, newText, isNewFile = false)
                    }
                }
                "Write" -> {
                    val path = input.optionalString("file_path")
                    val content = input.optionalString("content")
                    if (path != null && content != null) {
                        // Write carries no old content, so there is nothing to diff against.
                        return ChatEvent.DiffCard(name, path, oldText = "", newText = content, isNewFile = true)
                    }
                }
            }
        }

        return ChatEvent.ToolCallChip(name, input?.firstValueOrEmpty() ?: "")
    }

    /**
     * null when the key is absent -- distinct from an empty string, which is a real value
     * (an Edit that deletes a whole block legitimately has new_string == "").
     */
    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.firstValueOrEmpty(): String {
        val key = keys().asSequence().firstOrNull() ?: return ""
        return optString(key)
    }
}
