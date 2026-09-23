package com.tmuxmobile.phase0

sealed class ChatEvent {
    data class UserMessage(val text: String) : ChatEvent()
    data class AssistantMessage(val text: String) : ChatEvent()
    data class ToolCallChip(val name: String, val summary: String) : ChatEvent()
}
