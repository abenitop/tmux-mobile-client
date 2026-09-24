package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt strings below are the real ones found in the installed Claude Code bundle
 * (v2.1.280) -- "Do you want to proceed?", "Do you want to allow ", "Do you want to
 * continue?" -- plus the option wording ("Yes, and don't ask again for ...", "No, and tell
 * Claude what to do differently"), not invented text.
 */
class PermissionPromptDetectorTest {

    private val threeOptionPrompt = """
        Bash(rm -rf build)
        Do you want to proceed?
        ❯ 1. Yes
          2. Yes, and don't tell me again
          3. No, and tell Claude what to do differently
    """.trimIndent()

    @Test
    fun `detects a prompt and reads its options out of the pane`() {
        val prompt = PermissionPromptDetector.detect(threeOptionPrompt)

        assertEquals("Bash", prompt?.toolName)
        assertEquals(3, prompt?.options?.size)
        assertEquals(listOf("Yes", "Yes, and don't tell me again", "No, and tell Claude what to do differently"), prompt?.options?.map { it.label })
    }

    @Test
    fun `option keys move the cursor from the first option then confirm`() {
        val prompt = PermissionPromptDetector.detect(threeOptionPrompt)!!

        assertEquals("the cursor already sits on option 1", "Enter", prompt.options[0].key)
        assertEquals("Down Enter", prompt.options[1].key)
        assertEquals("Down Down Enter", prompt.options[2].key)
    }

    @Test
    fun `detects the two-option form with a different question wording`() {
        val pane = """
            Read(~/secrets.env)
            Do you want to allow reading from ~/secrets.env?
            1. Yes
            2. No
        """.trimIndent()

        val prompt = PermissionPromptDetector.detect(pane)
        assertEquals("Read", prompt?.toolName)
        assertEquals(2, prompt?.options?.size)
    }

    @Test
    fun `an option row without the cursor glyph is still parsed`() {
        val pane = """
            Do you want to continue?
            1. Yes
            2. No
        """.trimIndent()

        assertEquals(2, PermissionPromptDetector.detect(pane)?.options?.size)
    }

    @Test
    fun `no prompt in ordinary pane output`() {
        assertNull(PermissionPromptDetector.detect("$ ls -la\ntotal 8\ndrwxr-xr-x 2 user user 4096 file"))
        assertNull(PermissionPromptDetector.detect(""))
    }

    @Test
    fun `a question with no readable options is not reported`() {
        // The wording can be recognised while the choices have scrolled away or changed
        // shape. Reporting a prompt with no options would render buttons that send nothing.
        assertNull(PermissionPromptDetector.detect("Do you want to proceed?"))
    }

    @Test
    fun `a tool name that cannot be found falls back to a generic label`() {
        val pane = """
            Do you want to proceed?
            1. Yes
            2. No
        """.trimIndent()

        assertEquals("Tool", PermissionPromptDetector.detect(pane)?.toolName)
    }

    @Test
    fun `the newest prompt wins when older answered ones are still on screen`() {
        val pane = """
            Bash(ls)
            Do you want to proceed?
            1. Yes
            2. No
            Bash(rm -rf build)
            Do you want to proceed?
            1. Yes
            2. No
            3. No, and tell Claude what to do differently
        """.trimIndent()

        assertEquals(3, PermissionPromptDetector.detect(pane)?.options?.size)
    }

    @Test
    fun `question wording seen in the bundle is recognised`() {
        // Every marker taken from the installed v2.1.280 strings.
        val banners = listOf(
            "Do you want to proceed?",
            "Do you want to continue?",
            "Do you want to allow this connection?",
            "Do you want to use this tool?",
        )
        for (banner in banners) {
            val pane = "$banner\n1. Yes\n2. No"
            assertTrue("not recognised: $banner", PermissionPromptDetector.detect(pane) != null)
        }
    }
}
