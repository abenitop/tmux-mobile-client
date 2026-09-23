# Phase 1, sub-project — Chat view (simple version) (design)

**Date:** 2026-09-23
**Status:** approved for planning
**Parent doc:** `docs/reference/product-spec.md` — "Now: build scope" (reprioritized this ahead
of host manager/session list) and the full "Chat view (any agent CLI)" section (this sub-project
implements only the "simple version" it explicitly scopes down to).
**Builds on:** `docs/specs/2026-09-23-phase0-spike-design.md` (Phase 0 spike, complete).
Independent of `docs/specs/2026-09-23-phase1-terminal-rendering-design.md` (sub-project 1,
deferred) — see "Why this doesn't need sub-project 1" below.

## Purpose

Give a readable, chat-style view of a real agent session (Claude Code or Hermes) running in a
tmux pane, instead of raw pane text — the higher-value, more differentiating feature for this
app's AI-agent-user segment, per the operator's explicit reprioritization.

**Exit criterion:** from the phone, open Chat view against agent-stack's live Hermes session and
see real messages rendered as bubbles, live-updating as the conversation continues; open it
against this Claude Code session's own transcript and see the same; send a message from the
phone's compose bar and confirm the target agent receives it as a new turn.

## Why this doesn't need sub-project 1

Chat view does not render pane output through a terminal emulator at all. It reads a *separate*
data source — Claude Code's JSONL transcript or Hermes's `state.db` — over its own SSH exec
channel, parses structured events, and renders them as Compose bubbles. The only thing it shares
with Phase 0 is the `send-keys` input path. So Phase 0's raw-escape-codes pane view being
unpolished doesn't affect Chat view's rendering quality at all, and sub-project 1 (real terminal
rendering) can stay deferred without blocking this.

## Explicit non-goals for this sub-project

- **No hooks.** Detection and streaming use only the fallback paths the parent spec already
  defines without a `SessionStart`/`Notification` hook.
- **No compression lineage.** Hermes's `parent_session_id` chain (continuing a conversation
  across a context-compression boundary) is not followed — only the single newest session.
- **No rendering richness beyond plain bubbles**: no diff cards (`Edit`/`Write`), no interactive
  permission card (Allow/Always/Deny — the piece most dependent on hooks for reliable detection
  without pane-output pattern-matching), no subagent (`Task`) grouping, no thinking toggle (just
  omit thinking blocks, matching the parent spec's own default-hidden behavior). Tool calls render
  as a single collapsed one-line chip (name + short summary), nothing more.
- **No live pane/session detection.** Both adapters target a hardcoded session (matching Phase
  0's "one hardcoded target" pattern) — agent-stack's own Hermes (`~/.hermes/state.db`) and this
  Claude Code session's own project transcript. Dynamic detection (matching
  `pane_current_command` across a real session list) only matters once there's more than one
  session to disambiguate between, which is host manager/session-list territory (a separate,
  still-unscheduled sub-project).
- **No daemon**, obviously already out of scope in the parent spec too.

## Architecture

Two adapters, each streaming over its own long-lived SSH exec channel (separate from Phase 0's
tmux control-mode channel), mapping into one common event model:

```kotlin
sealed class ChatEvent {
    data class UserMessage(val text: String, val ts: Double) : ChatEvent()
    data class AssistantMessage(val text: String, val ts: Double) : ChatEvent()
    data class ToolCallChip(val name: String, val summary: String, val ts: Double) : ChatEvent()
}
```

No `diff`/`permission`/`subagent` event types — those belong to the fuller design this sub-project
deliberately doesn't implement.

**`ClaudeCodeChatAdapter`**
- Hardcoded to this session's own project path under `~/.claude/projects/<encoded-cwd>/` — no
  live detection (see non-goals).
- Streams via `tail -n +<offset> -F <newest-jsonl-in-that-dir>` over one SSH exec channel.
- Parses each line as JSON; maps `user`/`assistant`/`tool_use`/`tool_result` content blocks to
  `ChatEvent`s. Format is internal/undocumented (per the parent spec) — parse tolerantly, unknown
  event shapes collapse into a generic `ToolCallChip`.

**`HermesChatAdapter`**
- Hardcoded to agent-stack's `~/.hermes/state.db`. Schema verified directly against the real
  file: `sessions` (has `parent_session_id`, unused here) and `messages` (`role`, `content`,
  `tool_calls`, `tool_name`, `timestamp`, `token_count`) match the parent spec's description.
- Rather than reconnect every poll, one exec channel runs a small server-side loop for the life of
  the Chat view session:
  ```bash
  while true; do
    sqlite3 -json ~/.hermes/state.db \
      "SELECT id, role, content, tool_calls, tool_name, timestamp FROM messages
       WHERE session_id = '<newest-session-id>' AND id > $offset ORDER BY id"
    sleep 1.5
  done
  ```
  The client tails this channel's stdout, parses each JSON batch, updates `offset` to the max `id`
  seen. This is a genuine continuous loop for the lifetime of the view, not a one-shot read —
  starts when Chat view opens, stops when the view is backgrounded/closed, matching the parent
  spec's "poll every 1–2s while Chat is open; stop when backgrounded" exactly.
- Newest session lookup (`ORDER BY started_at DESC LIMIT 1`) happens once when the adapter starts,
  not re-queried per poll.

## UI

Adds a Chat/Terminal toggle to Phase 0's existing single hardcoded-session screen. Chat view is a
`LazyColumn` of bubble composables (user right-aligned, assistant full-width prose, tool calls as
a collapsed one-line chip). This sub-project also builds a basic Compose text-field + Send button
— a minimal version of what sub-project 1 calls the compose input bar — since Chat view needs
real input now regardless of sub-project 1's (deferred) status. Sending reuses Phase 0's
`send-keys -l` + `Enter` path, now generalized the same way sub-project 1 already planned
(`SshSpikeSession.sendKeys(paneId, keys, literal)`), targeted at whichever pane the active adapter
is reading from.

## Error handling

Same as established pattern: a dropped exec channel is shown visibly in the UI, no auto-reconnect
(unchanged scope cut from Phase 0, confirmed still acceptable during today's phone verification).

## Testing

- JSONL-line → `ChatEvent` and SQLite-row → `ChatEvent` mapping is pure logic, unit-testable —
  TDD, matching `ControlModeParserTest`'s style. Test with real captured samples from both
  adapters' actual targets, not synthetic guesses.
- The polling-loop shell command and SSH streaming wiring are integration-level — verified on a
  real phone against both live targets per the exit criterion above.
