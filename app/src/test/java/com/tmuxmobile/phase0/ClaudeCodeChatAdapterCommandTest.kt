package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the Kotlin->bash escaping in claudeTailCommand(). The plan's version used
 * `\\$(...)`, which Kotlin turns into `\$(...)` — a backslash that makes bash read the
 * subshell as an escaped literal filename. This test pins the exact bytes so that
 * class of bug can't come back silently.
 */
class ClaudeCodeChatAdapterCommandTest {

    @Test
    fun `tail command keeps a live subshell and no stray backslash`() {
        val expected = "tail -n +1 -F \"${'$'}(ls -t $CLAUDE_PROJECT_DIR/*.jsonl | head -1)\""
        assertEquals(expected, claudeTailCommand())
    }

    @Test
    fun `tail command contains no escaped-dollar backslash sequence`() {
        assertEquals(false, claudeTailCommand().contains("\\${'$'}"))
    }
}
