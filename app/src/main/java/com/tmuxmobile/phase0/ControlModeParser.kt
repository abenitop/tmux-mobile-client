package com.tmuxmobile.phase0

data class PaneOutput(val paneId: String, val text: String)

object ControlModeParser {
    private val outputLineRegex = Regex("^%output (%\\d+) (.*)$")

    fun parseLine(line: String): PaneOutput? {
        val match = outputLineRegex.matchEntire(line) ?: return null
        val (paneId, escaped) = match.destructured
        return PaneOutput(paneId, decodeEscapes(escaped))
    }

    private fun decodeEscapes(escaped: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 3 < escaped.length &&
                escaped[i + 1] in '0'..'7' &&
                escaped[i + 2] in '0'..'7' &&
                escaped[i + 3] in '0'..'7'
            ) {
                val octal = escaped.substring(i + 1, i + 4)
                sb.append(octal.toInt(8).toChar())
                i += 4
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }
}
