package com.tmuxmobile.phase0

/**
 * Recognises a Claude Code permission prompt in raw tmux pane text and turns it into an
 * answerable [ChatEvent.PermissionPrompt].
 *
 * Detection is pane-text based, not hook based: the prompt is *written to the pane*, so it
 * arrives on the same %output stream the terminal already renders -- no polling, no hook,
 * no extra channel. (The product spec prefers a `Notification` hook for reliability; the
 * simple version has no hooks, so this is the documented fallback.)
 *
 * VERSION FRAGILITY, read before trusting the answer buttons: both the question wording and
 * the option list differ between Claude Code versions -- observed strings in the installed
 * v2.1.280 bundle include "Do you want to proceed?", "Do you want to allow ", "Do you want
 * to continue?" and "Do you want to use this ", and the options range from a 2-choice
 * Yes/No to the 3-choice "Yes / Yes, and don't ask again for ... / No, and tell Claude what
 * to do differently". This parser therefore reads the OPTIONS OUT of the pane text instead
 * of assuming a fixed Allow/Always/Deny set, and reports the prompt as unanswerable rather
 * than inventing options it cannot see.
 */
object PermissionPromptDetector {

    /** The question lines Claude Code uses to ask for permission. */
    private val QUESTION_MARKERS = listOf(
        "Do you want to proceed?",
        "Do you want to allow ",
        "Do you want to continue?",
        "Do you want to use this ",
    )

    /** Matches a numbered option row, with or without the TUI's cursor glyph. */
    private val OPTION_ROW = Regex("""^\s*(?:[❯>]\s*)?(\d+)\.\s+(.+?)\s*$""")

    /**
     * Returns the prompt if [paneText] currently shows one, else null.
     *
     * Scans from the BOTTOM up so the newest prompt wins when the scrollback holds several
     * (a busy pane can contain earlier answered prompts above the live one).
     */
    fun detect(paneText: String): ChatEvent.PermissionPrompt? {
        val lines = paneText.lines()
        for (i in lines.indices.reversed()) {
            val questionIndex = findQuestion(lines, i) ?: continue
            val options = optionsAfter(lines, questionIndex)
            // No parsed options means either the prompt has scrolled such that its choices
            // are gone, or the wording changed again. Reporting nothing is safer than
            // guessing which key answers it.
            if (options.isEmpty()) continue
            return ChatEvent.PermissionPrompt(
                toolName = toolNameAbove(lines, questionIndex),
                detail = lines[questionIndex].trim(),
                options = options,
            )
        }
        return null
    }

    /**
     * The key sequence that answers option [index] (0-based), as a tmux `send-keys` payload.
     *
     * The prompt is an arrow-navigated select whose cursor starts on the first option, so
     * option N is reached with N Down presses and confirmed with Enter. Arrow keys are used
     * rather than typing the digit because digit-select is not guaranteed across versions,
     * while cursor movement + Enter is how a human answers the same prompt.
     *
     * NOT YET VERIFIED against a live prompt (device access was unavailable while this was
     * built) -- verify before relying on it. This function is the single place that would
     * need to change if the real mapping differs.
     */
    fun optionKeyFor(index: Int): String {
        if (index <= 0) return "Enter"
        return generateSequence { "Down" }.take(index).joinToString(" ") + " Enter"
    }

    private fun findQuestion(lines: List<String>, index: Int): Int? {
        val line = lines[index]
        return if (QUESTION_MARKERS.any { line.contains(it) }) index else null
    }

    /** Option rows belonging to the prompt whose question sits at [questionIndex]. */
    private fun optionsAfter(lines: List<String>, questionIndex: Int): List<PermissionOption> {
        val options = mutableListOf<PermissionOption>()
        // The rows follow the question within a few lines (an intervening blank line or a
        // wrapped question is possible). Stop at the first non-option, non-blank line.
        for (i in questionIndex + 1 until minOf(lines.size, questionIndex + 12)) {
            val match = OPTION_ROW.matchEntire(lines[i])
            if (match == null) {
                if (lines[i].isBlank() && options.isEmpty()) continue
                if (options.isNotEmpty()) break
                continue
            }
            val number = match.groupValues[1].toIntOrNull() ?: continue
            options += PermissionOption(
                label = match.groupValues[2],
                // Numbered from 1 in the UI, 0-based for cursor movement.
                key = optionKeyFor(number - 1),
            )
        }
        return options
    }

    /**
     * Best-effort tool name: Claude Code prints the pending call above the question, e.g.
     * "Bash(rm -rf build)". Falls back to a generic label.
     */
    private fun toolNameAbove(lines: List<String>, questionIndex: Int): String {
        val toolPattern = Regex("""^([A-Z][A-Za-z]+)\s*\(""")
        for (i in (questionIndex - 1) downTo maxOf(0, questionIndex - 8)) {
            val match = toolPattern.find(lines[i].trim())
            if (match != null) return match.groupValues[1]
        }
        return "Tool"
    }
}
