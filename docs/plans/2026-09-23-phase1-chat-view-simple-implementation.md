# Chat View (Simple) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This project's own handoff note:** on `agent-stack`, execution of this plan goes to Hermes via tmux relay, not to Claude Code's own subagent tooling — Claude Code designs/specs/plans, Hermes builds. If you are Hermes reading this: work task-by-task in order, treat each task's steps as the unit of work, and don't skip the verification step in any task.

**Goal:** Render a real agent session (Claude Code or Hermes) as a readable chat view instead of raw pane text, reading each agent's own structured log over its own SSH channel — independent of terminal rendering (sub-project 1, deferred).

**Architecture:** Two adapters (`ClaudeCodeChatAdapter`, `HermesChatAdapter`) each open their own SSH exec channel via a new `SshSpikeSession.execStream()` method (reusing the already-authenticated connection from Phase 0), stream lines from a source-specific shell command, and map them into a common `ChatEvent` model via pure-logic mappers. A new `ChatScreen` renders `ChatEvent`s as bubbles with a basic compose-bar input, toggled against Phase 0's existing raw pane view.

**Tech Stack:** Same as Phase 0 (Kotlin 2.4.20, Compose BOM 2026.09.00, AGP 9.4.1, Gradle 9.6.0, JDK 17, sshj 0.41.1) plus `org.json` (bundled in the Android SDK — no new dependency) for JSON parsing.

**Spec:** `docs/specs/2026-09-23-phase1-chat-view-simple-design.md` (this plan implements that spec only; the full "Chat view (any agent CLI)" design in `docs/reference/product-spec.md` is explicitly NOT built here — see that spec's "Explicit non-goals").

## Global Constraints

- No hooks — detection and streaming use only the fallback paths (hardcoded targets, no live pane detection).
- No compression lineage — Hermes's `parent_session_id` chain is not followed, only the single newest session.
- Plain bubbles only — no diff cards, no interactive permission card, no subagent grouping, no thinking toggle. Tool calls render as one collapsed chip (name + short summary).
- Both adapters target hardcoded sessions on the `agent-stack` VPS (same host Phase 0 already connects to — no new SSH key/infra needed):
  - Claude Code: this session's own project directory, verified as `/home/agent-stack/.claude/projects/-home-agent-stack/` (re-verify with `ls ~/.claude/projects/` if this has changed by execution time — don't assume it's still current).
  - Hermes: `/home/agent-stack/.hermes/state.db` (schema verified 2026-09-23: `sessions` and `messages` tables, columns `id, role, content, tool_calls, tool_name, timestamp` on `messages`).
- All SQL/shell fragments embedded in Kotlin below were composed against the real schema and real sample rows pulled from the live database/transcript on 2026-09-23 — verify they still match if the schema has changed since.

---

### Task 1: `ChatEvent` model + Claude Code transcript mapper (TDD)

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ChatEvent.kt`
- Create: `app/src/main/java/com/tmuxmobile/phase0/ClaudeCodeTranscriptMapper.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/ClaudeCodeTranscriptMapperTest.kt`

**Interfaces:**
- Produces: `sealed class ChatEvent` (`UserMessage(text)`, `AssistantMessage(text)`, `ToolCallChip(name, summary)`) and `object ClaudeCodeTranscriptMapper { fun mapLine(jsonLine: String): List<ChatEvent> }` — Task 4 (`ClaudeCodeChatAdapter`) calls `mapLine` on every line read from the tailed transcript. Task 6 (`ChatScreen`) renders `List<ChatEvent>`.

- [ ] **Step 1: Write `ChatEvent.kt`**

```kotlin
package com.tmuxmobile.phase0

sealed class ChatEvent {
    data class UserMessage(val text: String) : ChatEvent()
    data class AssistantMessage(val text: String) : ChatEvent()
    data class ToolCallChip(val name: String, val summary: String) : ChatEvent()
}
```

- [ ] **Step 2: Write the failing tests**

These use real lines pulled from this session's own transcript on 2026-09-23 (`~/.claude/projects/-home-agent-stack/2b5aeb72-3a3b-4095-b221-11087ff8d863.jsonl`), not synthetic guesses.

```kotlin
package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeCodeTranscriptMapperTest {

    @Test
    fun `maps a plain user string message`() {
        val line = """{"type":"user","message":{"role":"user","content":"continue"}}"""
        assertEquals(listOf(ChatEvent.UserMessage("continue")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps an assistant text block`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Hello there"}]}}"""
        assertEquals(listOf(ChatEvent.AssistantMessage("Hello there")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps an assistant tool_use block to a collapsed chip`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_01","name":"Bash","input":{"command":"ls -la"}}]}}"""
        assertEquals(listOf(ChatEvent.ToolCallChip("Bash", "ls -la")), ClaudeCodeTranscriptMapper.mapLine(line))
    }

    @Test
    fun `maps multiple content blocks in one assistant line to multiple events in order`() {
        val line = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Checking now"},{"type":"tool_use","id":"toolu_02","name":"Read","input":{"file_path":"/tmp/x.txt"}}]}}"""
        assertEquals(
            listOf(ChatEvent.AssistantMessage("Checking now"), ChatEvent.ToolCallChip("Read", "/tmp/x.txt")),
            ClaudeCodeTranscriptMapper.mapLine(line)
        )
    }

    @Test
    fun `ignores tool_result blocks and non-message line types`() {
        val toolResult = """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_01","content":"ok"}]}}"""
        val summaryLine = """{"type":"summary","summary":"Some earlier context"}"""
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(toolResult))
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(summaryLine))
    }

    @Test
    fun `ignores malformed or blank lines rather than crashing`() {
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine("not valid json"))
        assertEquals(emptyList<ChatEvent>(), ClaudeCodeTranscriptMapper.mapLine(""))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.ClaudeCodeTranscriptMapperTest"`
Expected: FAIL — `ClaudeCodeTranscriptMapper` doesn't exist yet.

- [ ] **Step 4: Write the minimal implementation**

```kotlin
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
                "tool_use" -> {
                    val name = block.optString("name")
                    val input = block.optJSONObject("input")
                    val summary = input?.firstValueOrEmpty() ?: ""
                    events += ChatEvent.ToolCallChip(name, summary)
                }
                // tool_result and any other block type: not rendered in the simple version
            }
        }
        return events
    }

    private fun JSONObject.firstValueOrEmpty(): String {
        val key = keys().asSequence().firstOrNull() ?: return ""
        return optString(key)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.ClaudeCodeTranscriptMapperTest"`
Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ChatEvent.kt app/src/main/java/com/tmuxmobile/phase0/ClaudeCodeTranscriptMapper.kt app/src/test/java/com/tmuxmobile/phase0/ClaudeCodeTranscriptMapperTest.kt
git commit -m "chat: ChatEvent model + Claude Code transcript mapper with tests"
```

---

### Task 2: Hermes message-row mapper (TDD)

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HermesMessageMapper.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/HermesMessageMapperTest.kt`

**Interfaces:**
- Consumes: `ChatEvent` (Task 1).
- Produces: `object HermesMessageMapper { fun mapRow(row: JSONObject): List<ChatEvent> }` — Task 5 (`HermesChatAdapter`) calls `mapRow` on every row object from the polled `sqlite3 -json` output.

- [ ] **Step 1: Write the failing tests**

These use real rows pulled from agent-stack's own `~/.hermes/state.db` on 2026-09-23 (`sqlite3 -json ~/.hermes/state.db "SELECT id, role, content, tool_calls, tool_name FROM messages WHERE ..."`).

```kotlin
package com.tmuxmobile.phase0

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesMessageMapperTest {

    @Test
    fun `maps a plain user message`() {
        val row = JSONObject("""{"id":7470,"role":"user","content":"exit","tool_calls":null,"tool_name":null}""")
        assertEquals(listOf(ChatEvent.UserMessage("exit")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `maps a plain assistant message`() {
        val row = JSONObject("""{"id":7471,"role":"assistant","content":"Goodbye.","tool_calls":null,"tool_name":null}""")
        assertEquals(listOf(ChatEvent.AssistantMessage("Goodbye.")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `maps an assistant row with both text and a tool call, in order`() {
        val row = JSONObject(
            """{"id":7553,"role":"assistant","content":"Retrying.","tool_calls":"[{\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"terminal\", \"arguments\": \"{\\\"command\\\": \\\"git push\\\"}\"}}]","tool_name":null}"""
        )
        assertEquals(
            listOf(ChatEvent.AssistantMessage("Retrying."), ChatEvent.ToolCallChip("terminal", "git push")),
            HermesMessageMapper.mapRow(row)
        )
    }

    @Test
    fun `maps a tool-call-only row with blank content to just the chip`() {
        val row = JSONObject(
            """{"id":7555,"role":"assistant","content":"","tool_calls":"[{\"id\": \"call_2\", \"type\": \"function\", \"function\": {\"name\": \"terminal\", \"arguments\": \"{\\\"command\\\": \\\"git log\\\"}\"}}]","tool_name":null}"""
        )
        assertEquals(listOf(ChatEvent.ToolCallChip("terminal", "git log")), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `ignores tool-role rows -- results are not rendered in the simple version`() {
        val row = JSONObject("""{"id":7472,"role":"tool","content":"output text","tool_calls":null,"tool_name":"terminal"}""")
        assertEquals(emptyList<ChatEvent>(), HermesMessageMapper.mapRow(row))
    }

    @Test
    fun `ignores malformed tool_calls JSON rather than crashing`() {
        val row = JSONObject("""{"id":7556,"role":"assistant","content":"","tool_calls":"not valid json","tool_name":null}""")
        assertEquals(emptyList<ChatEvent>(), HermesMessageMapper.mapRow(row))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.HermesMessageMapperTest"`
Expected: FAIL — `HermesMessageMapper` doesn't exist yet.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.tmuxmobile.phase0

import org.json.JSONArray
import org.json.JSONObject

object HermesMessageMapper {
    fun mapRow(row: JSONObject): List<ChatEvent> {
        val role = row.optString("role")
        val content = row.optString("content")
        val events = mutableListOf<ChatEvent>()

        when (role) {
            "user" -> if (content.isNotBlank()) events += ChatEvent.UserMessage(content)
            "assistant" -> {
                if (content.isNotBlank()) events += ChatEvent.AssistantMessage(content)
                val toolCallsRaw = row.optString("tool_calls", "null")
                if (toolCallsRaw != "null") {
                    events += parseToolCalls(toolCallsRaw)
                }
            }
            // "tool" role: these are results, not rendered in the simple version
        }
        return events
    }

    private fun parseToolCalls(toolCallsJson: String): List<ChatEvent> {
        val calls = try {
            JSONArray(toolCallsJson)
        } catch (e: Exception) {
            return emptyList()
        }
        val events = mutableListOf<ChatEvent>()
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val function = call.optJSONObject("function") ?: continue
            val name = function.optString("name")
            val summary = try {
                val args = JSONObject(function.optString("arguments", "{}"))
                val key = args.keys().asSequence().firstOrNull()
                key?.let { args.optString(it) } ?: ""
            } catch (e: Exception) {
                ""
            }
            events += ChatEvent.ToolCallChip(name, summary)
        }
        return events
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.HermesMessageMapperTest"`
Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HermesMessageMapper.kt app/src/test/java/com/tmuxmobile/phase0/HermesMessageMapperTest.kt
git commit -m "chat: Hermes state.db row mapper with tests"
```

---

### Task 3: `SshSpikeSession.execStream()` — generic streamed exec channel

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt`

**Interfaces:**
- Consumes: nothing new — reuses the already-connected `client` field set up by `connect()` (Phase 0).
- Produces: `fun execStream(command: String): Flow<String>` on `SshSpikeSession` — Tasks 4 and 5 each call this once with their own command to get a `Flow<String>` of stdout lines. Must only be called after `connect()` has completed (same precondition as the existing `lines()`/`sendKeys()`).

- [ ] **Step 1: Add `execStream` to `SshSpikeSession.kt`**

Add this method to the `SshSpikeSession` class (anywhere after the constructor, e.g. right after `lines()`):

```kotlin
    fun execStream(command: String): Flow<String> = flow {
        val execSession = withContext(Dispatchers.IO) { client.startSession() }
        val execCommand = withContext(Dispatchers.IO) { execSession.exec(command) }
        val reader = BufferedReader(InputStreamReader(execCommand.inputStream))
        while (true) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            emit(line)
        }
    }
```

This opens a second channel on the same authenticated SSH connection Phase 0's `connect()` already established — sshj supports multiple concurrent sessions per `SSHClient`. No new imports needed; every type here (`flow`, `withContext`, `Dispatchers`, `BufferedReader`, `InputStreamReader`) is already imported in this file for `lines()`.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (No unit test here, same reasoning as Phase 0's Task 4 — this only does anything meaningful against a live SSH target, covered by Tasks 4/5's manual verification and Task 6's end-to-end check.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt
git commit -m "chat: add SshSpikeSession.execStream for generic streamed exec channels"
```

---

### Task 4: `ClaudeCodeChatAdapter`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ClaudeCodeChatAdapter.kt`

**Interfaces:**
- Consumes: `SshSpikeSession.execStream(String): Flow<String>` (Task 3), `ClaudeCodeTranscriptMapper.mapLine(String): List<ChatEvent>` (Task 1).
- Produces: `class ClaudeCodeChatAdapter(session: SshSpikeSession) { fun events(): Flow<ChatEvent> }` — Task 6 (`ChatScreen`) collects this.

- [ ] **Step 1: Verify the tail command directly over SSH before writing Kotlin**

From the VPS, using the same restricted key Phase 0 set up (or any shell with access to this account — this reads a file, no forced-command restriction applies since we're just testing the shell command itself, not going through the app's SSH channel yet):

```bash
ls -t /home/agent-stack/.claude/projects/-home-agent-stack/*.jsonl | head -1
```

Expected: prints one `.jsonl` path — confirms the hardcoded directory still exists and has content. If the directory name has changed (e.g. this session's transcript moved, or `~/.claude/projects/` layout changed), re-verify with `ls ~/.claude/projects/` and update the path used below before continuing — don't guess.

- [ ] **Step 2: Write `ClaudeCodeChatAdapter.kt`**

```kotlin
package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat

private const val CLAUDE_PROJECT_DIR = "/home/agent-stack/.claude/projects/-home-agent-stack"

class ClaudeCodeChatAdapter(private val session: SshSpikeSession) {
    fun events(): Flow<ChatEvent> {
        val findNewest = "ls -t $CLAUDE_PROJECT_DIR/*.jsonl | head -1"
        val tailCommand = "tail -n +1 -F \"\$($findNewest)\""
        return session.execStream(tailCommand)
            .flatMapConcat { line -> ClaudeCodeTranscriptMapper.mapLine(line).asFlow() }
    }
}
```

`findNewest` is interpolated as a literal Kotlin constant into `tailCommand`; the `\$(...)` produces a literal `$(...)` in the final string, which the remote shell evaluates as a subshell picking the newest transcript file each time the command starts. `-n +1` streams from the beginning of the file (this session's transcript, not an incremental resume) — acceptable for this simple version per the parent spec (no persisted read-offset across app restarts).

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ClaudeCodeChatAdapter.kt
git commit -m "chat: Claude Code chat adapter (tail this session's own transcript)"
```

---

### Task 5: `HermesChatAdapter`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HermesChatAdapter.kt`

**Interfaces:**
- Consumes: `SshSpikeSession.execStream(String): Flow<String>` (Task 3), `HermesMessageMapper.mapRow(JSONObject): List<ChatEvent>` (Task 2).
- Produces: `class HermesChatAdapter(session: SshSpikeSession) { fun events(): Flow<ChatEvent> }` — Task 6 (`ChatScreen`) collects this.

- [ ] **Step 1: Verify the polling loop directly over SSH before writing Kotlin**

From the VPS:

```bash
sid=$(sqlite3 /home/agent-stack/.hermes/state.db "SELECT id FROM sessions ORDER BY started_at DESC LIMIT 1;")
echo "newest session: $sid"
sqlite3 -json /home/agent-stack/.hermes/state.db "SELECT id, role, content, tool_calls, tool_name FROM messages WHERE session_id='$sid' ORDER BY id DESC LIMIT 3;"
```

Expected: prints a real session id and a JSON array of up to 3 real message rows. If this box's Hermes schema has changed since 2026-09-23 (column names, table names), the query above will error — re-verify with `sqlite3 /home/agent-stack/.hermes/state.db ".schema messages"` and update Step 2's script accordingly before continuing.

- [ ] **Step 2: Write `HermesChatAdapter.kt`**

```kotlin
package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import org.json.JSONArray

private const val HERMES_DB_PATH = "/home/agent-stack/.hermes/state.db"

class HermesChatAdapter(private val session: SshSpikeSession) {
    fun events(): Flow<ChatEvent> {
        val pollLoop = """
            sid=$(sqlite3 $HERMES_DB_PATH "SELECT id FROM sessions ORDER BY started_at DESC LIMIT 1;")
            offset=0
            while true; do
              rows=$(sqlite3 -json $HERMES_DB_PATH "SELECT id, role, content, tool_calls, tool_name FROM messages WHERE session_id='${'$'}{sid}' AND id > ${'$'}{offset} ORDER BY id;")
              if [ -n "${'$'}{rows}" ] && [ "${'$'}{rows}" != "[]" ]; then
                echo "${'$'}{rows}"
                offset=$(echo "${'$'}{rows}" | python3 -c "import json,sys; print(max(r['id'] for r in json.load(sys.stdin)))")
              fi
              sleep 1.5
            done
        """.trimIndent()
        return session.execStream(pollLoop)
            .flatMapConcat { line -> parseRowsLine(line).asFlow() }
    }

    private fun parseRowsLine(line: String): List<ChatEvent> {
        if (line.isBlank()) return emptyList()
        val rows = try {
            JSONArray(line)
        } catch (e: Exception) {
            return emptyList()
        }
        val events = mutableListOf<ChatEvent>()
        for (i in 0 until rows.length()) {
            rows.optJSONObject(i)?.let { events += HermesMessageMapper.mapRow(it) }
        }
        return events
    }
}
```

`${'$'}` is Kotlin's escape for a literal `$` inside a triple-quoted string (backslash escapes don't work in raw strings) — every bash variable reference (`${'$'}{sid}`, `${'$'}{offset}`, `${'$'}{rows}`) needs it; `$HERMES_DB_PATH` deliberately does *not* use it, since that one really is a Kotlin constant meant to be interpolated at compile time, and the two `$(...)` subshells don't need escaping either, since Kotlin's simple-name interpolation only triggers on `$` followed by an identifier character, not `(`.

This is a genuine continuous loop for the life of the SSH channel — it starts once when `events()` is collected and keeps polling every 1.5s until the channel is closed (when `SshSpikeSession.close()` runs, same lifecycle as Phase 0's control-mode channel), not a one-shot read.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HermesChatAdapter.kt
git commit -m "chat: Hermes chat adapter (continuous poll loop over one SSH channel)"
```

---

### Task 6: `ChatScreen` UI, MainActivity wiring, and end-to-end verification

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ChatScreen.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- Consumes: `ChatEvent` (Task 1), `ClaudeCodeChatAdapter`/`HermesChatAdapter` (Tasks 4/5), existing `SshSpikeSession.sendKeys(paneId: String, text: String)` (Phase 0, unchanged — no signature change needed since Chat view only ever sends literal compose-bar text, same as Phase 0's test button already does).
- Produces: nothing further — this is the leaf that makes Chat view runnable and toggleable against Phase 0's existing Terminal view.

- [ ] **Step 1: Write `ChatScreen.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(events: List<ChatEvent>, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(events) { event -> ChatBubble(event) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                if (draft.isNotBlank()) {
                    onSend(draft)
                    draft = ""
                }
            }) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(event: ChatEvent) {
    when (event) {
        is ChatEvent.UserMessage -> Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            Card { Text(event.text, modifier = Modifier.padding(8.dp)) }
        }
        is ChatEvent.AssistantMessage -> Text(event.text, modifier = Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodyLarge)
        is ChatEvent.ToolCallChip -> Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text("${event.name}: ${event.summary}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 2: Wire the toggle and both adapters into `MainActivity.kt`**

Modify `SpikeScreen` in `MainActivity.kt`: add a view-mode toggle (Terminal/Chat), a selector for which Chat source (Claude Code/Hermes) since both adapters exist side by side in this sub-project, and collect the selected adapter's `events()` into a growing list.

```kotlin
// Add these imports alongside the existing ones in MainActivity.kt:
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateListOf

// Add inside SpikeScreen, alongside the existing `output`/`paneId` state:
    var viewMode by remember { mutableStateOf("terminal") } // "terminal" | "chat-claude" | "chat-hermes"
    val chatEvents = remember { mutableStateListOf<ChatEvent>() }

// Add a second LaunchedEffect (alongside the existing one that calls session.connect):
    LaunchedEffect(viewMode) {
        if (viewMode == "chat-claude") {
            chatEvents.clear()
            runCatching {
                ClaudeCodeChatAdapter(session).events().collect { chatEvents.add(it) }
            }.onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("ERROR: ${e.message}")) }
        } else if (viewMode == "chat-hermes") {
            chatEvents.clear()
            runCatching {
                HermesChatAdapter(session).events().collect { chatEvents.add(it) }
            }.onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("ERROR: ${e.message}")) }
        }
    }

// Replace the Column's content with a mode switcher on top, and branch on viewMode below it:
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { viewMode = "terminal" }) { Text("Terminal") }
            Button(onClick = { viewMode = "chat-claude" }) { Text("Chat: Claude") }
            Button(onClick = { viewMode = "chat-hermes" }) { Text("Chat: Hermes") }
        }
        if (viewMode == "terminal") {
            // existing Text(output) + Send test input button, unchanged
        } else {
            ChatScreen(events = chatEvents, onSend = { text ->
                val target = paneId ?: return@ChatScreen
                scope.launch {
                    runCatching { session.sendKeys(target, text) }
                        .onFailure { e -> chatEvents.add(ChatEvent.AssistantMessage("SEND ERROR: ${e.message}")) }
                }
            })
        }
    }
```

Apply this as an actual edit to the existing `SpikeScreen` composable — keep the current Terminal-mode body (the scrolling `Text(output)` + "Send test input" button) exactly as Phase 0 left it, just nested under the `if (viewMode == "terminal")` branch instead of being the only content.

Note: Chat view's compose-bar `onSend` reuses `paneId`, which is the `phase0-test` pane learned from the *Terminal* channel's control-mode stream — sending through Chat view types into that same disposable test pane, not into the live Hermes/Claude Code session being displayed. That's correct for this sub-project: the exit criterion only requires *reading* real Hermes/Claude Code data and *sending* successfully through the existing verified path, not routing input into someone else's live agent session (which the parent spec's non-goals already exclude via "no live pane detection").

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Install on the operator's phone over wireless ADB**

```bash
adb connect <phone-tailscale-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Reuse the same phone/connection Phase 0 already verified works, unless it's changed.)

- [ ] **Step 5: Run the exit-criterion check from the design spec**

1. Launch the app. Confirm Terminal mode still works exactly as it did at the end of Phase 0 (this proves nothing regressed).
2. Tap "Chat: Hermes". Confirm real messages from agent-stack's live Hermes session appear as bubbles within a couple seconds.
3. While still open, cause a new message to happen in that Hermes session from another angle (e.g. send it something via `tmux send-keys` to its own session, or just wait if it's mid-task) and confirm the new message appears in the phone's Chat view within ~1.5-3s, without reopening the app — this proves it's a live poll, not a one-shot read.
4. Tap "Chat: Claude". Confirm this session's own transcript (the one this plan came from) renders as bubbles, including at least one `ToolCallChip`.
5. From Chat view's compose bar, type something and tap Send. Confirm (via `tmux attach -t phase0-test` on the VPS, same as Phase 0's check) that it was typed into the test session.

All five happening is this sub-project's exit criterion. If step 3 doesn't show a live update, that's a real finding — check whether the poll loop is actually still running (verify the SSH channel didn't get closed early) before assuming it's a rendering bug.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ChatScreen.kt app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "chat: Chat view UI, mode toggle, and end-to-end wiring"
git push
```
