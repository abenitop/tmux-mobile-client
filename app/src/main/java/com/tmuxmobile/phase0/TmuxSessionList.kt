package com.tmuxmobile.phase0

/**
 * A tmux session as shown on the Sessions (home) screen.
 *
 * [windows] and [activity] come straight from `list-sessions`; [attached] is whether any
 * client is currently attached; [process] is the "what runs in here" label derived from
 * `list-panes` (claude/vim/htop/…), null when there is no interesting process.
 */
data class TmuxSession(
    val name: String,
    val windows: Int,
    val attached: Boolean,
    val activity: Long,
    val process: String? = null,
)

/**
 * Pure parsing + command building for the Sessions list. Extracted so the exact remote
 * command strings and the `|`-delimited parsing are unit-testable (the shell quoting
 * around the format string is the kind of thing that silently produces a broken command).
 */
object SessionListParser {

    /**
     * The format string MUST be single-quoted: it goes through the remote user's shell
     * (sshj `exec`), and unquoted `|` would be read as a pipe. Single quotes make the
     * whole `#{}` block literal so tmux substitutes it.
     */
    fun listSessionsCommand(): String =
        "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_activity}'"

    fun listPanesCommand(): String =
        "tmux list-panes -a -F '#{session_name}|#{pane_current_command}'"

    fun parseSession(line: String): TmuxSession? {
        val parts = line.split('|')
        if (parts.size < 4) return null
        val windows = parts[1].toIntOrNull() ?: return null
        return TmuxSession(
            name = parts[0],
            windows = windows,
            attached = parts[2] == "1",
            activity = parts[3].toLongOrNull() ?: 0L,
        )
    }

    /** Returns (sessionName, currentCommand), or null for a malformed line. */
    fun parsePane(line: String): Pair<String, String>? {
        val idx = line.indexOf('|')
        if (idx < 0) return null
        return line.substring(0, idx) to line.substring(idx + 1)
    }

    /** Pick the most informative process from a session's pane commands, skipping shells
     *  (a bare `bash`/`zsh` pane means nothing special is running — no badge, like the
     *  design's "no agent"). */
    fun pickProcess(commands: List<String>): String? {
        val shells = setOf("bash", "zsh", "sh", "fish", "dash", "-zsh", "-bash", "login", "tmux")
        return commands.firstOrNull { it.isNotBlank() && it !in shells }
    }

    /** Relative label for `#{session_activity}` (a unix-seconds timestamp). */
    fun formatActivity(activitySeconds: Long, nowSeconds: Long): String {
        val diff = nowSeconds - activitySeconds
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> "${diff / 86400}d ago"
        }
    }
}
