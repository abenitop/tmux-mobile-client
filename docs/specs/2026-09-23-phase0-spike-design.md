# Phase 0 — tmux control-mode spike (design)

**Date:** 2026-09-23
**Status:** approved for planning
**Parent doc:** `docs/reference/product-spec.md` (full 5-phase product spec), `docs/reference/ui-mockups.pdf`

## Purpose

Full product vision is in `docs/reference/product-spec.md`. This spec covers
only Phase 0 from that doc's roadmap table: prove the one genuinely unproven
piece of the whole project — that an Android client can speak tmux's control
mode (`tmux -CC`) correctly over SSH and get clean, bidirectional pane
traffic. Everything else in the product spec (terminal emulation, real UI,
the agent layer) is either a solved problem (Termux's libraries) or builds on
this working.

**Exit criterion (unchanged from the product spec):** type something on the
phone, watch it execute in a real tmux session on the target VPS, and see the
resulting output land back on the phone as text — sourced from tmux control
mode, not scraped screen output.

## Explicit non-goals for this phase

To keep the risk isolated to the protocol layer:

- No terminal emulation (no ANSI/VT100 interpretation, no cursor placement,
  no colors). Raw decoded text only.
- No real input UI. One hardcoded test string, fired by one button.
- No hosts list, session list, navigation, or any other screen. One Activity,
  one screen.
- No auto-reconnect, no error recovery beyond not crashing.
- No Termux `terminal-emulator`/`terminal-view` dependency yet — deferred to
  Phase 1, where it's an integration task, not a research question.

These are deliberate scope cuts for this phase only, not the final
architecture — the product spec is the source of truth for what Phase 1+
builds on top of this.

## Architecture

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, single Jetpack Compose screen | Matches product spec's eventual stack; min SDK 26 |
| SSH | `sshj` (Apache 2.0) | Pure JVM, actively maintained, no need to carry a second SSH library (`jsch`) into a spike |
| Target | `agent-stack` VPS itself, a dedicated disposable test session (e.g. `tmux new-session -d -s phase0-test`, running a plain shell) | Confirmed reachable, real protocol behavior, no synthetic test server needed — and isolated from any live Claude Code/Hermes session so injected test keystrokes can't disrupt real work |
| tmux invocation | SSH exec channel running `tmux -CC attach -t <session>` | Standard way to enter control mode non-interactively |
| Protocol parsing | Minimal hand-rolled parser for `%output %<pane-id> <text>` lines only | Everything else in the control-mode stream (`%window-add`, `%session-changed`, etc.) is ignored this phase |
| Rendering | Plain scrolling Compose `Text`, decoded pane text appended as it arrives | No emulator dependency |
| Input | One hardcoded string → `send-keys -t <pane> -l "<text>"` then `send-keys -t <pane> Enter`, sent over the same SSH session on button tap | Proves the write path without building the real compose bar |
| Auth | SSH key pair generated for this spike, added to `agent-stack`'s `authorized_keys` scoped to this use | No password auth; matches product spec's eventual key-based model |
| Test device | Operator's physical Android phone, wireless ADB over Tailscale (100.66.191.51) | No KVM/hardware virtualization on this VPS — emulator isn't viable here, confirmed via `/proc/cpuinfo` and absence of `/dev/kvm` |

```
Compose UI (1 screen)
  -> SSH session (sshj, exec "tmux -CC attach -t <session>")
       -> stdout: control-mode stream, parsed for %output lines only -> appended to Text
       -> stdin: on button tap, write send-keys command for hardcoded string
```

## Data flow

1. App opens directly into the single screen (no host picker — target host,
   session name (`phase0-test`), and SSH key path are build-time constants
   for this phase).
2. On screen load, app opens an SSH connection via `sshj`, execs
   `tmux -CC attach -t <session>`.
3. App reads stdout line by line. Lines matching `%output %<pane-id> <text>`
   are decoded (control mode escapes some bytes; decode just enough to get
   readable text) and appended to the scrolling `Text`. All other control-mode
   lines (`%begin`, `%end`, `%window-add`, etc.) are logged but not acted on.
4. On button tap, app writes `send-keys -t <pane-id> -l "<hardcoded text>"`
   followed by `send-keys -t <pane-id> Enter` to the SSH session's stdin.
5. Operator watches the real tmux session directly (e.g. via their own
   terminal) to confirm the keystrokes landed, and watches the phone screen
   to confirm the resulting output round-tripped back.

## Error handling

Minimal, matching the phase's scope: catch connection/auth failures and show
the exception message in the `Text` area instead of crashing. No retry, no
reconnect logic — those are Phase 1 concerns (`Auto-reconnect` in the product
spec's feature table).

## Testing

Given this phase's job is to answer a yes/no protocol question, the "test" is
the manual exit criterion itself: operator confirms on both ends (their own
terminal view of the session, and the phone) that a round trip worked. No
automated test suite for this phase — nothing here has stable enough
behavior yet to be worth asserting on. Phase 1 (real parsing of more
control-mode message types, reconnect logic, multiple panes) is where unit
tests on the parser start to earn their keep.

## Handoff

Per this project's division of labor: this spec + the implementation plan
(written next, via the writing-plans skill) go to Hermes to actually build —
SDK install, writing the Kotlin/sshj code, wiring the SSH key, running the
build, and working through the wireless-ADB test loop with the operator.
