# tmux Mobile Client — Product Spec

Sep 22, 2026 · @Someone

## Now: build scope

Only these get built next. Everything under **Later** is parked until real users ask for it.

1. **Core app (Phase 1)**: done, in device testing.
2. **Chat view, simple version**: Claude Code and Hermes only. Read the newest log for the pane's directory (Claude Code JSONL) or newest session (Hermes `state.db`), render bubbles, send through the Android-keyboard compose bar. No hooks, no compression lineage, no daemon.
3. **Play Store beta**: themes, macros, templates, privacy policy, Data Safety form.

The Chat view section below describes the full design; build only the simple version above first.

## Vision and principles

A native Android tmux client for the Play Store: SSH in, land directly on your tmux sessions, work with the phone's own keyboard. AI-agent supervision is an optional, opt-in layer.

**Target users**

- Sysadmins and homelab users managing long-running jobs over SSH.
- Developers who keep work alive in tmux on remote machines.
- AI-agent users running Claude Code, Codex, Gemini CLI, Aider, OpenCode or Hermes-style TUIs (Pro).

**Principles**

1. **Cockpit, not workstation.** The phone is for watching, deciding and short instructions, not writing code.
2. **Sessions first.** Connecting lands on the session list, never a bare shell prompt.
3. **System keyboard only.** No custom on-screen keyboard; Gboard, SwiftKey, voice dictation and autocorrect work fully.
4. **Zero server install for the core.** Only `tmux` and `sshd` are required.
5. **Agents optional and vendor-neutral.** Off by default; no dependency on any AI vendor.
6. **Safe by default.** Attach read-only; typing is an explicit action.

## Architecture

The app talks to tmux through **control mode** (`tmux -CC`) over one SSH channel, so sessions, windows and panes are structured data rather than scraped screen text.

| Layer | Choice | Notes |
| --- | --- | --- |
| Language / UI | Kotlin, Jetpack Compose | Min SDK 26 (Android 8) |
| SSH | sshj or mwiede/jsch | Ed25519/RSA keys, agent forwarding off by default |
| Terminal rendering | Termux `terminal-emulator` + `terminal-view` | Verify license before shipping |
| tmux protocol | Control mode parser (`%output`, `%window-add`, `%session-changed`, `%exit`) | Needs tmux 2.x+; target 3.x |
| Storage | Room (hosts, macros), Android Keystore (keys, secrets) | No cloud sync in MVP |
| Background | Foreground service while attached | Keeps the connection alive when the app is backgrounded |

```mermaid
flowchart LR
  A[Compose UI] --> B[Session VM]
  B --> C[tmux control-mode client]
  C --> D[SSH channel]
  D --> E[Server: sshd + tmux]
  C --> F[Terminal emulator<br/>per pane]
  F --> A
```

Each pane's `%output` stream feeds its own terminal emulator instance; input goes back as `send-keys` commands on the same channel.

## Core features (free, MVP)

Everything here works with only `sshd` and `tmux` on the server.

| Feature | Behavior | Server command |
| --- | --- | --- |
| Host manager | Save hosts, users, ports, keys; generate Ed25519 keys in-app | — |
| Session list | Shown immediately after connect | `list-sessions -F '#{session_name}\|#{session_windows}\|#{session_attached}\|#{session_activity}'` |
| Process labels | Show what runs in each session (claude, hermes, vim, htop) | `list-panes -a -F '#{session_name}\|#{pane_current_command}'` |
| Attach | Tap a session to attach in control mode | `tmux -CC attach -t <name>` |
| Window/pane switcher | Tabs for windows, visual pane map, swipe between | Control-mode events |
| New session | Name + start directory + optional start command | `new-session -A -d -s <name> -c <dir> '<cmd>'` |
| Templates | Saved presets, e.g. repo dir + launch command | Same as above |
| Macros | Saved commands/prompts per host or session | `send-keys -l` |
| Scrollback | Load on demand, searchable | `capture-pane -p -S -<n>` |
| Auto-reconnect | Resume last session after network change or app restart | Re-attach |
| Read-only attach | Default; tap to enable input | Client-side gate |
| Multi-server | Several hosts, one combined session list | Parallel connections |
| Themes | Light/dark + popular terminal palettes | — |

## Input model

All typing goes through the phone's own keyboard. The app never ships an on-screen keyboard.

**Compose mode (default)**

- A standard Compose `TextField` pinned above the IME, with normal input type, so swipe, autocorrect, voice dictation and language switching work.
- Multi-line input; Send button or Enter-to-send (setting).
- On send: `send-keys -t <pane> -l "<text>"` then `send-keys -t <pane> Enter`. The `-l` flag stops tmux from interpreting words like `Enter` or `C-c`.
- History of sent lines per session, recalled with Up swipe.

**Raw mode (toggle)**

- A custom `InputConnection` forwards each keystroke immediately, for vim, less, htop and TUI menus.

**Special keys without an on-screen keyboard**

| Key | Method |
| --- | --- |
| Ctrl+C, Ctrl+D, Ctrl+Z | Tokens in the compose field (`^c`, `^d`, `^z`) parsed before send |
| Esc | Long-press on terminal, or `:esc` token |
| Arrows | Swipe on terminal (hold and drag repeats) |
| Up/Down | Volume keys (setting) |
| Tab | `:tab` token or two-finger tap |
| All keys | Hardware/Bluetooth keyboard through `onKeyEvent` |

An optional one-row key strip (Ctrl, Esc, Tab, arrows) above the IME is off by default.

## Screens and flows

Five screens cover the MVP; the session list is the home screen.

| Screen | Contents |
| --- | --- |
| Hosts | Saved servers, connection status, add/edit, key management |
| Sessions (home) | All sessions across connected hosts: name, process label, windows, last activity, attached flag |
| Terminal | Pane view, window tabs, pane map, compose bar, read-only/input toggle |
| Macros & templates | Saved commands and session presets, per host |
| Settings | Themes, gestures, volume keys, Enter behavior, security |

```mermaid
flowchart TD
  A[App open] --> B{Last session?}
  B -- yes --> C[Reconnect + attach]
  B -- no --> D[Sessions list]
  D --> E[Tap session]
  D --> F[New session / template]
  E --> G[Terminal, read-only]
  F --> G
  G --> H[Enable input]
  H --> I[Compose or raw mode]
```

Back from Terminal detaches the client view only; the tmux session keeps running.

## Chat view (any agent CLI)

A readable, chat-style rendering of any agentic CLI session, toggled per session with Terminal. Terminal stays the source of truth. Each agent plugs in through an adapter that turns its own logs into a common message model; Claude Code is the reference adapter.

**Adapter interface**

- `detect(pane)`: match on `pane_current_command` and cwd.
- `locate(pane)`: find the session log (hook-provided path or newest file in the agent's log folder).
- `stream(offset)`: tail the log over SSH and emit normalized events: `user`, `assistant`, `tool_call`, `tool_result`, `diff`, `permission`, `error`.
- `answer(permission, choice)`: the keys to send for Allow / Deny on that agent's prompt.

| Agent | Session source (verify per version) | Adapter |
| --- | --- | --- |
| Claude Code | `~/.claude/projects/<cwd>/<id>.jsonl` | Reference, v1 |
| Codex CLI | `~/.codex/sessions/` rollout JSONL | v1.1 |
| Gemini CLI | `~/.gemini/` session files | v1.1 |
| OpenCode | `~/.local/share/opencode/` storage | Later |
| Aider | `.aider.chat.history.md` in the repo | Later |
| Hermes Agent (TUI) | \~/.hermes/state.db (SQLite); profiles under \~/.hermes/profiles/\<name>/ | v1, first-class |
| Any other TUI | Generic fallback: your sends become bubbles, new pane output between sends becomes the reply | Built in |

Adapters are declarative where possible (a JSON mapping of log fields to events), so new agents can be added without an app release.

**Hermes Agent adapter (v1)**

- Source: Hermes keeps conversation history in a SQLite store at `~/.hermes/state.db` (sessions, messages, titles, token counts); each profile has its own under `~/.hermes/profiles/<name>/`.
- Streaming: poll new rows (`messages` where id > last seen) over SSH with `sqlite3`, or `python3` since Hermes already requires Python. Poll every 1–2 s while Chat is open; stop when backgrounded.
- Lineage: after context compression Hermes continues in a new session with `parent_session_id`; the adapter follows the chain so the chat reads as one continuous thread.
- Detection: pane command `hermes`; map to the active session by most recently updated session for that profile, or by an optional hook if Hermes exposes one.
- Input: compose bar sends to the TUI with `send-keys -l` + Enter; slash commands (`/model`, etc.) as chips.
- Verify schema per Hermes release; read-only access, never write to `state.db`.

**Reference adapter: Claude Code**

- Claude Code stores each session as JSONL under `~/.claude/projects/<encoded-cwd>/<session-id>.jsonl`; each line is a user, assistant or tool event with content blocks (`text`, `tool_use`, `tool_result`).
- Pane-to-transcript mapping: an optional `SessionStart` hook writes `$TMUX_PANE`, `session_id` and `transcript_path` (both already in the hook input) to a small file the app reads.
- Fallback without the hook: the pane's `#{pane_current_command}` is `claude` and `#{pane_current_path}` names the project, so take the newest JSONL in that project folder.
- Streaming: `tail -n +<offset> -F <transcript>` over an SSH exec channel, parsed line by line. No daemon needed, so Chat can ship in the core app.
- The transcript format is internal and undocumented: parse tolerantly (unknown events render as a collapsed raw block) and test against pinned Claude Code versions.

**Rendering**

| Transcript element | Chat rendering |
| --- | --- |
| User text | Right-aligned bubble |
| Assistant text | Full-width prose, rendered markdown, large type |
| `Read`, `Glob`, `Grep` | One-line collapsed chip |
| `Edit`, `Write` | Inline diff card |
| `Bash` | Command chip with collapsible monospace output |
| Tool error | Red chip, expandable |
| Permission prompt | Inline Allow / Always / Deny card |
| Thinking | Hidden; optional toggle |
| Subagent (`Task`) | Collapsed group with its own turns |

**Input**

- The Android-keyboard compose bar sends with `send-keys -l` then `Enter` to the pane, exactly as typing in Terminal, so Claude Code sees normal input.
- Permission cards answer by sending the key Claude Code's prompt expects; the prompt is detected from the `Notification` hook or pane output. Verify key mapping per Claude Code version.
- Slash commands (`/compact`, `/clear`, `/model`) as chips above the compose bar.

**Readability**

- Body text follows Android font scale plus an in-app slider (15–24sp); text reflows to screen width.
- Monospace only in code and output blocks, which scroll horizontally; outputs over \~20 lines collapse.

**Out of scope for v1**: rewinding or editing past turns, images in the transcript (placeholder only), agents without readable logs beyond the generic fallback.

**Tier**: Chat view is part of the free core app. The pane-mapping hook stays optional.

## Security, privacy and Play Store

No user data leaves the device except over the user's own SSH connections.

- Private keys and API keys stored in Android Keystore; never exported, never synced.
- Biometric app lock (optional) and biometric confirmation before enabling input (optional).
- Host key verification with a clear warning on changed fingerprints.
- No telemetry by default; crash reporting opt-in only.
- Play Data Safety form: no data collected or shared (core); summaries send pane text only to the provider the user configures.
- Privacy policy page required before listing.
- Foreground service type `connectedDevice` or `dataSync` must be declared and justified for Android 14+.

**Licensing to verify before shipping**

- Termux `terminal-emulator` / `terminal-view` license terms.
- SSH library license (sshj: Apache 2.0).
- Any code taken from MuxPod or other open-source clients.

## Monetization and roadmap

Free core builds the user base; Pro (subscription or one-time unlock) covers the agent layer.

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| 0 — Spike | SSH + control-mode parser + one pane rendered | Attach to a live Claude Code session from the phone |
| 1 — Core MVP | Hosts, session list, attach, compose/raw input, gestures, auto-reconnect | Daily use replaces Attach for you |
| 2 — Play Store beta | Themes, macros, templates, read-only default, privacy policy, Data Safety | Closed testing track with 20+ testers |
| 3 — Agent layer | `muxd`, protocol v1, Claude Code adapter, state badges, approvals inbox | Approve a Claude Code edit from a notification |
| 4 — Pro expansion | Diff viewer, summaries, more adapters, multi-server dashboard | Paid tier live |

## Open questions

- [ ] App name and package id (check Play Store for conflicts with "Attach").
- [ ] Pro pricing: subscription vs one-time unlock.
- [ ] Companion language: Go or Rust.
- [ ] Push path: ntfy only, or also an FCM relay you host.
- [ ] Minimum tmux version to support (control mode quirks before 3.x).
- [ ] iOS later, or Android only (Kotlin Multiplatform vs native).

# Later (parked)

Not in current scope. Revisit once the core app and Chat view are in daily use and on the Play Store.

## Agent layer (optional, Pro)

A separate server companion, installed only by users who want it, reports agent state to the app. Without it, no agent UI appears.

**Companion daemon (`muxd`, working name)**

- Single static binary (Go or Rust), runs as a user service.
- Listens on a Unix socket; the app reaches it through the existing SSH connection (`direct-streamlocal`), so no open ports.
- Push for when the app is closed: ntfy (self-hostable) or FCM through a relay the user opts into.

**Vendor-neutral event protocol (JSON lines)**

```json
{"v":1,"session":"api","pane":"%3","agent":"claude-code","state":"needs_approval","title":"Edit src/auth.ts","detail":"...","id":"evt_123","ts":1790000000}
```

| State | Meaning |
| --- | --- |
| `working` | Agent is running |
| `needs_approval` | Waiting on a permission decision |
| `needs_input` | Waiting for a prompt or answer |
| `done` | Task finished |
| `error` | Agent failed or crashed |

**Adapters**

| Agent | Source |
| --- | --- |
| Claude Code | Hooks: `Notification`, `Stop`, `PreToolUse` call `muxd emit` |
| Codex, Gemini CLI, OpenCode, Aider | Their hook/notify config where available |
| Any TUI (e.g. Hermes) | Generic: `muxd emit` from scripts, or idle/pattern detection on pane output |

**Pro features built on it**

- State badges on the session list.
- Approvals inbox with Approve/Deny from notifications; the app answers by `send-keys` to the right pane.
- Diff viewer (`git diff` rendered) before approving edits.
- "While you were away" summary of new scrollback, using the user's own API key or a local model.
- Multi-server agent dashboard.
