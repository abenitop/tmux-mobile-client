package com.tmuxmobile.phase0

sealed class ChatEvent {
    data class UserMessage(val text: String) : ChatEvent()
    data class AssistantMessage(val text: String) : ChatEvent()

    /**
     * A tool call with no meaningful diff payload -- Read, Glob, Grep, Bash, etc. Renders
     * as a one-line collapsed chip.
     */
    data class ToolCallChip(val name: String, val summary: String) : ChatEvent()

    /**
     * An edit, rendered as an inline diff card instead of a chip (per the product spec's
     * rendering table: `Edit`/`Write` -> inline diff card).
     *
     * @param oldText what the tool replaced; empty for a Write/new file.
     * @param newText what the tool wrote.
     * @param isNewFile distinguishes "created this file" from "edited it", which the UI
     *   labels differently -- and is the only way to tell a Write of a new file from a
     *   Write that overwrote one, since both arrive with empty oldText.
     */
    data class DiffCard(
        val name: String,
        val filePath: String,
        val oldText: String,
        val newText: String,
        val isNewFile: Boolean,
    ) : ChatEvent()

    /**
     * A permission prompt the agent is waiting on, detected from the pane. The options are
     * whatever the pane actually offered (see PermissionPromptDetector) rather than a fixed
     * Allow/Deny pair, because the wording and the number of options vary by version.
     */
    data class PermissionPrompt(
        val toolName: String,
        val detail: String,
        val options: List<PermissionOption>,
    ) : ChatEvent()
}

/**
 * One answerable choice on a permission prompt.
 *
 * [key] is the literal tmux/send-keys payload that answers it -- a version-dependent
 * detail, which is why it is derived in one place (PermissionPromptDetector.optionKeyFor)
 * and unit-tested rather than inlined at the call site.
 */
data class PermissionOption(val label: String, val key: String)
