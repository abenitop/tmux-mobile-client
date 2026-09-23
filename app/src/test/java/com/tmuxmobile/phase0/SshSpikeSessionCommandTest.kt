package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

class SshSpikeSessionCommandTest {

    @Test
    fun `literal mode sends escaped text then a separate Enter command`() {
        assertEquals(
            listOf("send-keys -t %3 -l \"hello\"\n", "send-keys -t %3 Enter\n"),
            buildSendKeysCommands("%3", "hello", literal = true)
        )
    }

    @Test
    fun `literal mode escapes backslashes and quotes`() {
        val commands = buildSendKeysCommands("%3", "say \"hi\\\"", literal = true)
        // Both escapes matter and they interact in order: backslash-doubling runs
        // FIRST, so the backslash it introduces before a quote is not itself doubled.
        // Input chars  : say "hi\"
        // After  \\->\\\\: say "hi\\"
        // After  "->\"  : say \"hi\\\"
        assertEquals("send-keys -t %3 -l \"say \\\"hi\\\\\\\"\"\n", commands[0])
    }

    @Test
    fun `non-literal mode sends the key name directly, no auto Enter`() {
        assertEquals(
            listOf("send-keys -t %3 Up\n"),
            buildSendKeysCommands("%3", "Up", literal = false)
        )
    }

    @Test
    fun `literal mode always emits exactly two commands`() {
        // The Enter is what submits the line; Task 3's raw mode relies on literal=false
        // specifically to NOT get it, so both shapes are pinned here.
        assertEquals(2, buildSendKeysCommands("%3", "x", literal = true).size)
        assertEquals(1, buildSendKeysCommands("%3", "x", literal = false).size)
    }
}
