# Task: build real terminal rendering + input for the tmux Mobile Client Android app

**From:** Claude Code (this box's `agent-stack:0.0` session)
**Date:** 2026-09-23

## Scope — in

- This repo only: `/home/agent-stack/projects/tmux-mobile-client`.
- Your Task 1 spike is done and its findings are folded into the plan. Read
  `docs/plans/2026-09-23-phase1-terminal-rendering-implementation.md` Tasks 2 through 6 and
  execute them in order, TDD steps and commits as written.
- Task 4 (generalizing `sendKeys`) and Task 3 (`RawInputBridge`) are cross-dependent — Task 3's
  code assumes Task 4's signature. Do Task 4 before Task 3 if you want a clean build at every
  intermediate commit; the plan notes this.
- Task 5 extracts a composable that already exists inline in `ChatScreen.kt` — read that file's
  current input row before writing anything new, don't duplicate it.
- Full exit-criterion verification (Task 6, Step 2) needs a **real Claude Code tmux session**,
  not just `phase0-test`'s echo test — use whatever's available for that on your side.

## Scope — out

- Nothing in `/opt/agent-stack`.
- Chat view is done and merged — Task 5 only extracts one composable from it; don't otherwise
  touch or re-verify it.
- No host manager, multi-pane, or auto-reconnect — same non-goals as the design spec, unchanged.
- Don't resolve the Termux licensing question or the 16 KB `libtermux.so` alignment issue — both
  stay on the Phase 2 list per the design spec.

## Judgment you have

- Task 6, Step 1's input-mode gating (stopping raw-mode keys from also firing while
  `ComposeInputBar` has focus) is described at the "what has to be true" level, not handed to you
  as exact code — pick whichever mechanism is less code and verify it doesn't double-fire on a
  real device.
- If anything in Tasks 2-6 assumes something about `TerminalView`/`TerminalSession` that your own
  Task 1 research found to be wrong, trust your own verified findings over the plan text and
  proceed on what's actually true — flag the discrepancy when you report back.

## Everything else

Six tasks, each independently committable and testable per its own steps. Report back when all
six are done, or if you get stuck on any one of them.
