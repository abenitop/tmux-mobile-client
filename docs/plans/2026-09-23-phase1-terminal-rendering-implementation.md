# Real Terminal Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This project's own handoff note:** on `agent-stack`, execution goes to Hermes via tmux relay, not Claude Code's own subagent tooling — Claude Code designs/specs/plans, Hermes builds. If you are Hermes reading this: work task-by-task in order, and **do not skip Task 1's decision gate** — everything after it depends on what Task 1 actually finds, which is why only Task 1 is fully specified right now.

**Goal:** Replace Phase 0's raw-text pane dump with real terminal rendering (Termux's `terminal-view`/`terminal-emulator`) and real input (raw + compose modes), still against the one hardcoded `phase0-test` session.

**Architecture:** See `docs/specs/2026-09-23-phase1-terminal-rendering-design.md`. One load-bearing risk the design didn't fully resolve: Termux's `TerminalView` cannot render without a real `TerminalSession`, and `TerminalSession` unconditionally spawns a **native local PTY subprocess** (real NDK C code) on first use — even though this app has no local process to run; all content comes from tmux control-mode over SSH, not a local shell. Task 1 below is a pass/fail spike verifying the only known workaround (spawn a harmless no-op local process just to satisfy `TerminalSession`, then bypass its normal local-pty output path by feeding SSH-sourced bytes directly into its emulator, and intercept all key events to redirect input over SSH instead of to the dummy local process) before any of the real UI work is planned or built.

**Tech Stack:** Same as Phase 0/Chat view (Kotlin 2.4.20, Compose BOM 2026.09.00, AGP 9.4.1, Gradle 9.6.0, JDK 17, sshj 0.41.1), plus Termux's `terminal-view`/`terminal-emulator` via JitPack: `com.github.termux.termux-app:terminal-view:v0.118.3` (verified: not on Maven Central; JitPack coordinate confirmed against `termux-app`'s own `build.gradle`/`jitpack.yml`; v0.118.3 is the latest non-beta tag as of 2026-09-23 — re-check if this has changed by execution time).

## Global Constraints

- Same VPS/session target as Phase 0 and Chat view (`100.66.191.51`, `agent-stack`, `phase0-test`) — no new SSH infrastructure needed for this sub-project (raw-mode input reuses the existing control-mode channel via `sendKeys`, not a new `execStream` channel).
- Licensing remains unresolved and is explicitly out of scope to resolve here (see the design spec's "Licensing note") — using Termux's library for development only, must be revisited before any Play Store submission.
- No multi-pane support, no host manager, no auto-reconnect — same non-goals as the design spec.
- **Do not write Tasks 2+ of this plan yourself.** Task 1 is a spike; its outcome determines what Task 2 even is (continue with `TerminalView`, or fall back to a custom renderer). Complete Task 1, report the exact findings back, and wait for the plan to be extended before continuing.

---

### Task 1: Spike — verify SSH-fed content can render in `TerminalView` via a no-op local session

**Files:**
- Modify: `app/build.gradle.kts` (add JitPack repo + `terminal-view` dependency)
- Modify: `settings.gradle.kts` (add JitPack to `dependencyResolutionManagement.repositories`)
- Create: a throwaway spike file, e.g. `app/src/main/java/com/tmuxmobile/phase0/TerminalSpike.kt` — **not committed if the spike fails**; if it succeeds, its real findings (exact constructor calls, threading behavior) become the basis for Task 2, written up in a findings note (see Step 6).

**This is a spike, not a normal task** — per `superpowers:brainstorming`'s spike definition, the output is an answer (does this approach work, and exactly how), not code you necessarily keep as-is.

- [ ] **Step 1: Add the dependency**

In `settings.gradle.kts`, add JitPack to `dependencyResolutionManagement.repositories` (alongside the existing `google()`/`mavenCentral()`):

```kotlin
maven { url = uri("https://jitpack.io") }
```

In `app/build.gradle.kts`, add:

```kotlin
implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
```

Run `./gradlew :app:assembleDebug` to confirm it resolves and pulls in `terminal-emulator` transitively (it's a submodule dependency of `terminal-view` within the same JitPack build). If resolution fails or the artifact/tag has changed, re-verify against `https://github.com/termux/termux-app/tags` and JitPack's own build log for this repo before assuming the coordinate is wrong — don't guess a different one.

- [ ] **Step 2: Read the real `TerminalSession` constructor and threading model from source before writing any code**

Termux's `TerminalSession` (`terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java` in the `termux-app` repo, same tag as the dependency above) was not fully read in this plan's own research pass — specifically, its exact constructor signature and what its background reader thread does when the spawned process produces no output (e.g. `sleep infinity`) are unverified. Before writing the spike code:

1. Fetch that file at tag `v0.118.3` and read the constructor and `initializeEmulator()`/reader-thread implementation directly.
2. Determine the minimal command/args that make it spawn a harmless no-op process (e.g. `/system/bin/sleep` with `infinity`, or equivalent — Android's shell environment may differ from desktop Linux, verify what's actually available).
3. Confirm (from source, not assumption) whether that background reader thread blocking on the no-op process's stdout will cause any problem for directly calling `session.getEmulator().append(bytes, len)` from elsewhere — e.g. race conditions, the reader thread's own idle-EOF handling closing the session unexpectedly, thread-safety of `append()` itself.

Do not write Step 3's code until this is answered from the real source.

- [ ] **Step 3: Build the throwaway spike**

Using what Step 2 found, write `TerminalSpike.kt`: a minimal Composable (wired into `MainActivity` behind a temporary "Spike" button, or a completely separate throwaway entry point — implementer's choice, whichever is faster to verify and easier to remove cleanly) that:

1. Creates a `TerminalView(context, null)`.
2. Creates a `TerminalSession` using the no-op process command found in Step 2.
3. Implements a minimal `TerminalViewClient` (all ~20 methods — verified no default/empty base class exists, per this plan's own research; a trivial implementation returning safe defaults, e.g. `false`/no-ops, for everything except the specific method(s) needed for the test below).
4. Calls `terminalView.attachSession(session)` and `terminalView.setTerminalViewClient(client)`.
5. After attaching, calls `session.getEmulator().append(...)` with a fixed, recognizable test string (e.g. `"SPIKE_RENDER_TEST_OK\r\n"`, using `\r\n` since that's what a real terminal stream would send, not just `\n`).

- [ ] **Step 4: Verify on the real device — does the test string actually render?**

Build, install on the operator's phone (same wireless-adb flow as Phase 0/Chat view), open the spike screen, and check visually (or via `uiautomator dump`) whether `SPIKE_RENDER_TEST_OK` is actually drawn on screen by `TerminalView`. This is the crux of the whole spike — don't infer success from "it compiled and didn't crash."

- [ ] **Step 5: Verify key input can be intercepted and does not reach the dummy local process**

Type something into the terminal view (system keyboard) and confirm, via your `TerminalViewClient` implementation, that keystrokes reach your code (e.g. log or display what was received) rather than silently going to the no-op local process. Confirm the no-op process itself never receives real input and doesn't need to (i.e. the architecture of "TerminalSession exists only to satisfy the library, all real I/O is manual" actually holds under real key events, not just synthetic `append()` calls).

- [ ] **Step 6: Decision gate — report findings, then stop**

Write up, in plain terms (a comment block at the top of `TerminalSpike.kt` is fine, or just in your final report to the operator):

- Did Step 4 render correctly? Exact `TerminalSession` constructor call that worked, exact no-op command used.
- Did Step 5 confirm key events are interceptable and don't leak to the dummy process?
- Any race conditions, exceptions, or unexpected behavior from the background reader thread (Step 2's concern) observed in practice?
- Your recommendation: proceed with `TerminalView` (and what the real integration shape should look like, now that it's verified), or fall back to a custom-built minimal ANSI renderer instead (and why).

**Do not commit `TerminalSpike.kt` or proceed to build the real UI** — report back with the findings above and wait for the rest of this plan to be written based on them, per this plan's Global Constraints. If Step 4 or Step 5 fails outright (can't get it working after reasonable effort), that's the answer this spike exists to produce — report exactly what failed and where, rather than pushing forward on a guess.
