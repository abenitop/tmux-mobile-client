package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import org.json.JSONObject

internal const val HERMES_DB_PATH = "/home/agent-stack/.hermes/state.db"

/**
 * One JSON object per line, so each line is independently parseable.
 *
 * Deviation from the plan: the plan used `sqlite3 -json`, which pretty-prints its array
 * across MULTIPLE lines (`[{...},` / `{...},` / `{...}]`). Its `parseRowsLine` parsed each
 * line as a whole JSON array, so any batch of 2+ new rows yielded nothing at all — the
 * bug was invisible to the plan's unit tests because they only exercised `mapRow`.
 * `json_object(...)` is still one JSON row per line and is valid for every SQLite here.
 *
 * The session lookup also deviates: `ORDER BY started_at DESC LIMIT 1` picked a session
 * that had already ended (2 messages, end_reason=tui_shutdown) instead of the live
 * conversation. Prefer a session with `ended_at IS NULL`, falling back to newest.
 */
internal fun hermesPollLoop(): String = """
    sid=${'$'}(sqlite3 $HERMES_DB_PATH "SELECT id FROM sessions ORDER BY (ended_at IS NULL) DESC, started_at DESC LIMIT 1;")
    offset=0
    while true; do
      rows=${'$'}(sqlite3 $HERMES_DB_PATH "SELECT json_object('id',id,'role',role,'content',content,'tool_calls',tool_calls,'tool_name',tool_name) FROM messages WHERE session_id='${'$'}{sid}' AND id > ${'$'}{offset} ORDER BY id;")
      if [ -n "${'$'}{rows}" ]; then
        printf '%s\n' "${'$'}{rows}"
        offset=${'$'}(printf '%s\n' "${'$'}{rows}" | tail -1 | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
      fi
      sleep 1.5
    done
""".trimIndent()

/** Parses a single `json_object(...)` row line. Malformed/blank lines are ignored. */
internal fun hermesRowToEvents(line: String): List<ChatEvent> {
    if (line.isBlank()) return emptyList()
    val row = try {
        JSONObject(line)
    } catch (e: Exception) {
        return emptyList()
    }
    return HermesMessageMapper.mapRow(row)
}

class HermesChatAdapter(private val session: SshSpikeSession) {
    fun events(): Flow<ChatEvent> {
        return session.execStream(hermesPollLoop())
            .flatMapConcat { line -> hermesRowToEvents(line).asFlow() }
    }
}
