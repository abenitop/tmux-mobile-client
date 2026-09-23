# Task: build Chat view (simple version) for the tmux Mobile Client Android app

**From:** Claude Code (this box's `agent-stack:0.0` session)
**Date:** 2026-09-23

## Scope — in

- This repo only: `/home/agent-stack/projects/tmux-mobile-client` (private GitHub repo
  `abenitop/tmux-mobile-client`, remote `origin` already configured).
- Read, in order: `docs/reference/product-spec.md` — specifically the "Now: build scope" section
  and the "Chat view (any agent CLI)" section (full design; you're building only the "simple
  version" the build-scope note describes), `docs/specs/2026-09-23-phase1-chat-view-simple-design.md`
  (what and why — including why this doesn't need sub-project 1's terminal rendering, which stays
  deferred), then `docs/plans/2026-09-23-phase1-chat-view-simple-implementation.md` (the actual
  build plan).
- Execute the plan's 6 tasks **in order**, exactly as written — each task has its own files,
  code, and a verification step. Don't skip a verification step, especially Tasks 4 and 5's
  "verify directly over SSH before writing Kotlin" steps — the shell commands embedded in Kotlin
  string literals were verified against this box's real data on 2026-09-23, but re-verify before
  trusting them since state (session ids, schema) can drift.
- Commit after each task as the plan instructs, and `git push` after each commit so progress is
  visible on GitHub as you go.
- Phase 0's existing code (`SshSpikeSession`, `ControlModeParser`, `MainActivity`) is a known-good,
  phone-verified foundation — don't rewrite it beyond what Task 3 and Task 6 explicitly ask for.

## Scope — out

- Nothing in `/opt/agent-stack` changes for this task. Fully separate project.
- Sub-project 1 (real terminal rendering via Termux's terminal-emulator/terminal-view,
  `docs/specs/2026-09-23-phase1-terminal-rendering-design.md`) is **not** part of this task —
  it's deferred, not abandoned. Don't pull any of its work forward; Chat view is deliberately
  independent of it (see this task's design spec, "Why this doesn't need sub-project 1").
- No hooks, no compression-lineage following, no diff cards, no interactive permission card, no
  subagent grouping, no thinking toggle, no live pane/session detection — all explicit non-goals
  in the design spec. If you find yourself building toward any of these, stop and re-read the
  non-goals section; the full "Chat view (any agent CLI)" design describes them, but they're
  intentionally out of scope for this pass.
- Don't touch any other service on this box (litellm-router, WhatsApp bridge, open-webui,
  wg-easy, upload-inbox, qdrant, calibre-web, rag-api, Caddy, or anyone else's tmux session).
- The Hermes adapter reads `agent-stack`'s own live `~/.hermes/state.db` **read-only** — the
  design spec is explicit about this and the plan's SQL is all `SELECT`. Never write to it.

## Judgment you have

- The plan's embedded shell/SQL commands and Kotlin escaping (`${'$'}` for literal bash `$` inside
  triple-quoted strings) were verified carefully at plan-writing time, but if something disagrees
  once you actually run it, verify-by-running beats verify-by-reading — fix it and note why, same
  standard as Phase 0.
- If agent-stack's Hermes `state.db` schema or session data has changed since 2026-09-23 (new
  columns, different session for "newest"), re-verify against the live schema before trusting the
  plan's hardcoded column list.
- Don't deviate from the design's core scope cuts (plain bubbles only, hardcoded targets, no
  hooks) without flagging it back to me first — those were deliberate, not oversights.

## Where you'll need to stop and wait

Task 6's step 4 (install over wireless ADB) and step 5 (phone-in-hand exit-criterion check) need
the operator's physical Android phone reachable the same way Phase 0's did. If it's not currently
reachable, get through step 3 (built APK, `BUILD SUCCESSFUL`) and stop and report — don't guess at
a phone IP or wait indefinitely. I'm watching this session.

## Everything else

Work through it. If you get well and truly stuck (not just "this needs a docs lookup"), say so
clearly rather than pushing forward on a guess.
