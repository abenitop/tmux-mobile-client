# Task: spike real terminal rendering for the tmux Mobile Client Android app

**From:** Claude Code (this box's `agent-stack:0.0` session)
**Date:** 2026-09-23

## Scope — in

- This repo only: `/home/agent-stack/projects/tmux-mobile-client`.
- Read, in order: `docs/specs/2026-09-23-phase1-terminal-rendering-design.md` (what and why),
  then `docs/plans/2026-09-23-phase1-terminal-rendering-implementation.md` (Task 1 — the only
  task written so far, on purpose).
- **Execute Task 1 only.** It's a spike: verify whether Termux's `TerminalView` can actually
  render SSH-sourced content via a no-op local `TerminalSession` (full reasoning in the plan).
  Don't skip Step 2 — the real `TerminalSession` constructor and reader-thread behavior weren't
  fully verified before this plan was written; read the actual source before writing spike code.
- Report back the Step 6 findings and **stop** — do not design or build the real UI, don't
  commit the spike code, don't write Tasks 2+ yourself. This plan is deliberately incomplete
  until your findings are in.

## Scope — out

- Nothing in `/opt/agent-stack`.
- Chat view (the previous task) is done and merged — don't touch it, don't re-verify it.
- No real UI work (mode toggle, `RawInputBridge`, `ComposeInputBar`) — that's Task 2+, not yet
  written, deliberately blocked on this spike's outcome.

## Judgment you have

- If the no-op-session workaround doesn't work cleanly after reasonable effort, that's a valid,
  useful answer — say so plainly with what you tried and what broke, rather than forcing it.
- If you find the real `TerminalSession` constructor/threading model disagrees with what this
  plan assumed, that's expected (this plan says so) — verify from source and proceed on what's
  actually true.

## Everything else

This is a small, bounded spike. If you get well and truly stuck, say so clearly.
