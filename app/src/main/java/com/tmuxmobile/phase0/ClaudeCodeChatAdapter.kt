package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat

internal const val CLAUDE_PROJECT_DIR = "/home/agent-stack/.claude/projects/-home-agent-stack"

/**
 * Built as its own function so the exact shell string (including the `$(...)` subshell
 * that picks the newest transcript) is unit-testable — Kotlin string escaping here is
 * the kind of thing that silently produces a broken command.
 *
 * `\$(` is the escape for a literal `$` followed by `(` — NOT `\\$(`, which would emit
 * a backslash and make bash treat the subshell as an escaped literal filename.
 */
internal fun claudeTailCommand(): String =
    "tail -n +1 -F \"\$(ls -t $CLAUDE_PROJECT_DIR/*.jsonl | head -1)\""

class ClaudeCodeChatAdapter(private val session: SshSpikeSession) {
    fun events(): Flow<ChatEvent> {
        return session.execStream(claudeTailCommand())
            .flatMapConcat { line -> ClaudeCodeTranscriptMapper.mapLine(line).asFlow() }
    }
}
