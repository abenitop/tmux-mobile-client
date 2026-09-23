# Real Terminal Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This project's own handoff note:** on `agent-stack`, execution goes to Hermes via tmux relay, not Claude Code's own subagent tooling — Claude Code designs/specs/plans, Hermes builds. If you are Hermes reading this: work task-by-task in order. **Update:** Task 1's spike is complete (findings in Task 1's Step 6) and Tasks 2-6 are now fully written below, grounded in those findings — proceed task-by-task through all of them.

**Goal:** Replace Phase 0's raw-text pane dump with real terminal rendering (Termux's `terminal-view`/`terminal-emulator`) and real input (raw + compose modes), still against the one hardcoded `phase0-test` session.

**Architecture:** See `docs/specs/2026-09-23-phase1-terminal-rendering-design.md`. One load-bearing risk the design didn't fully resolve: Termux's `TerminalView` cannot render without a real `TerminalSession`, and `TerminalSession` unconditionally spawns a **native local PTY subprocess** (real NDK C code) on first use — even though this app has no local process to run; all content comes from tmux control-mode over SSH, not a local shell. Task 1 below is a pass/fail spike verifying the only known workaround (spawn a harmless no-op local process just to satisfy `TerminalSession`, then bypass its normal local-pty output path by feeding SSH-sourced bytes directly into its emulator, and intercept all key events to redirect input over SSH instead of to the dummy local process) before any of the real UI work is planned or built.

**Tech Stack:** Same as Phase 0/Chat view (Kotlin 2.4.20, Compose BOM 2026.09.00, AGP 9.4.1, Gradle 9.6.0, JDK 17, sshj 0.41.1), plus Termux's `terminal-view`/`terminal-emulator` via JitPack: `com.github.termux.termux-app:terminal-view:v0.118.3` (verified: not on Maven Central; JitPack coordinate confirmed against `termux-app`'s own `build.gradle`/`jitpack.yml`; v0.118.3 is the latest non-beta tag as of 2026-09-23 — re-check if this has changed by execution time).

## Global Constraints

- Same VPS/session target as Phase 0 and Chat view (`100.66.191.51`, `agent-stack`, `phase0-test`) — no new SSH infrastructure needed for this sub-project (raw-mode input reuses the existing control-mode channel via `sendKeys`, not a new `execStream` channel).
- Licensing remains unresolved and is explicitly out of scope to resolve here (see the design spec's "Licensing note") — using Termux's library for development only, must be revisited before any Play Store submission.
- No multi-pane support, no host manager, no auto-reconnect — same non-goals as the design spec.
- Task 1's spike is complete; Tasks 2-6 below are the real implementation, grounded in its findings (Task 1, Step 6).

---

### Task 1: Spike — verify SSH-fed content can render in `TerminalView` via a no-op local session

**Files:**
- Modify: `app/build.gradle.kts` (add JitPack repo + `terminal-view` dependency)
- Modify: `settings.gradle.kts` (add JitPack to `dependencyResolutionManagement.repositories`)
- Create: a throwaway spike file, e.g. `app/src/main/java/com/tmuxmobile/phase0/TerminalSpike.kt` — **not committed if the spike fails**; if it succeeds, its real findings (exact constructor calls, threading behavior) become the basis for Task 2, written up in a findings note (see Step 6).

**This is a spike, not a normal task** — per `superpowers:brainstorming`'s spike definition, the output is an answer (does this approach work, and exactly how), not code you necessarily keep as-is.

- [x] **Step 1: Add the dependency**

In `settings.gradle.kts`, add JitPack to `dependencyResolutionManagement.repositories` (alongside the existing `google()`/`mavenCentral()`):

```kotlin
maven { url = uri("https://jitpack.io") }
```

In `app/build.gradle.kts`, add:

```kotlin
implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
```

Run `./gradlew :app:assembleDebug` to confirm it resolves and pulls in `terminal-emulator` transitively (it's a submodule dependency of `terminal-view` within the same JitPack build). If resolution fails or the artifact/tag has changed, re-verify against `https://github.com/termux/termux-app/tags` and JitPack's own build log for this repo before assuming the coordinate is wrong — don't guess a different one.

- [x] **Step 2: Read the real `TerminalSession` constructor and threading model from source before writing any code**

Termux's `TerminalSession` (`terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java` in the `termux-app` repo, same tag as the dependency above) was not fully read in this plan's own research pass — specifically, its exact constructor signature and what its background reader thread does when the spawned process produces no output (e.g. `sleep infinity`) are unverified. Before writing the spike code:

1. Fetch that file at tag `v0.118.3` and read the constructor and `initializeEmulator()`/reader-thread implementation directly.
2. Determine the minimal command/args that make it spawn a harmless no-op process (e.g. `/system/bin/sleep` with `infinity`, or equivalent — Android's shell environment may differ from desktop Linux, verify what's actually available).
3. Confirm (from source, not assumption) whether that background reader thread blocking on the no-op process's stdout will cause any problem for directly calling `session.getEmulator().append(bytes, len)` from elsewhere — e.g. race conditions, the reader thread's own idle-EOF handling closing the session unexpectedly, thread-safety of `append()` itself.

Do not write Step 3's code until this is answered from the real source.

- [x] **Step 3: Build the throwaway spike**

Using what Step 2 found, write `TerminalSpike.kt`: a minimal Composable (wired into `MainActivity` behind a temporary "Spike" button, or a completely separate throwaway entry point — implementer's choice, whichever is faster to verify and easier to remove cleanly) that:

1. Creates a `TerminalView(context, null)`.
2. Creates a `TerminalSession` using the no-op process command found in Step 2.
3. Implements a minimal `TerminalViewClient` (all ~20 methods — verified no default/empty base class exists, per this plan's own research; a trivial implementation returning safe defaults, e.g. `false`/no-ops, for everything except the specific method(s) needed for the test below).
4. Calls `terminalView.attachSession(session)` and `terminalView.setTerminalViewClient(client)`.
5. After attaching, calls `session.getEmulator().append(...)` with a fixed, recognizable test string (e.g. `"SPIKE_RENDER_TEST_OK\r\n"`, using `\r\n` since that's what a real terminal stream would send, not just `\n`).

- [x] **Step 4: Verify on the real device — does the test string actually render?**

Build, install on the operator's phone (same wireless-adb flow as Phase 0/Chat view), open the spike screen, and check visually (or via `uiautomator dump`) whether `SPIKE_RENDER_TEST_OK` is actually drawn on screen by `TerminalView`. This is the crux of the whole spike — don't infer success from "it compiled and didn't crash."

- [x] **Step 5: Verify key input can be intercepted and does not reach the dummy local process**

Type something into the terminal view (system keyboard) and confirm, via your `TerminalViewClient` implementation, that keystrokes reach your code (e.g. log or display what was received) rather than silently going to the no-op local process. Confirm the no-op process itself never receives real input and doesn't need to (i.e. the architecture of "TerminalSession exists only to satisfy the library, all real I/O is manual" actually holds under real key events, not just synthetic `append()` calls).

- [x] **Step 6: Decision gate — report findings, then stop**

**Spike findings (2026-09-23, Hermes) — Tasks 2+ below are built on these:**

- Render works. `TerminalSession("/system/bin/cat", "/", arrayOf("cat"), arrayOf("TERM=xterm-256color"), 2000, sessionClient)` as the no-op local session; feeding `session.emulator.append(bytes, bytes.size)` then `terminalView.onScreenUpdated()` renders correctly, including SGR colors. A plain-color line was nearly invisible against the unset window background — `TerminalView` paints no background once an emulator is attached, so the app must set one explicitly (dark background, not left to the library).
- Key interception works cleanly (both paths): hardware/injected `KeyEvent`s reach `TerminalViewClient.onKeyDown/onKeyUp`, and system-keyboard IME input reaches `onCodePoint` (via `commitText → sendTextToTerminal → inputCodePoint`, checked before any fallthrough to the local process) — confirmed neither path leaks to the dummy `cat` process.
- Corrected plan assumptions: the `TerminalSession` constructor itself spawns nothing (plain field assignment); the native PTY subprocess spawns in `initializeEmulator()`, called from `updateSize()`, called from `TerminalView.attachSession()`. `cleanupResources()` runs only on process exit, so the no-op process must never exit (`/system/bin/cat` with no file args blocks on stdin — correct). `TerminalSessionClient` is 16 methods, `TerminalViewClient` is 23 (corrected after further review — the spike header's "20" was an undercount); neither has a default/base implementation.
- Ordering rules (not tied to specific source line numbers, which drift by tag): `setTerminalViewClient()` must be called before `attachSession()` (attach's `updateSize()` dereferences the client). `setTextSize()` (or `setTypeface()`) must be called before `attachSession()` — the renderer is lazily created there and nothing else creates it. The view needs `isFocusable = true` and `isFocusableInTouchMode = true` or hardware-key input silently never arrives (soft-keyboard/IME input is unaffected). `argv[0]` must be non-null/non-empty or the native exec fails and the dummy process exits immediately.
- No race conditions or unexpected reader-thread behavior observed against a genuinely-never-exiting no-op process.
- Test-environment-only gotchas (not production concerns, but worth knowing when verifying Tasks 3/6 on-device — all four looked like app bugs before being traced to the test setup): a system "16 KB page size" compatibility dialog can steal window focus during key-injection testing; the soft keyboard appearing shifts layout mid-test; the status bar can swallow taps near the top of the screen (Phase 0 hit this too, fixed with `safeDrawing` insets); and `adb shell input text` does NOT exercise `onKeyDown` (it delivers `ACTION_MULTIPLE`/`KEYCODE_UNKNOWN`, not real per-key events) — use `adb shell input keyevent <code>` or real on-screen typing to test the raw-key path specifically.
- Separately confirmed (not part of Step 4/5, but relevant to Phase 2 planning): Termux's `libtermux.so` is not 16 KB page-size aligned (Play Store requirement on Android 15+) — tracked alongside the licensing question on the Phase 2 list, not addressed here.
- **Recommendation: proceed with `TerminalView`.** No need for a custom ANSI renderer. Tasks 2+ below build the real integration on this verified shape.

Nothing from the spike is committed. `TerminalSpike.kt` and its throwaway `SpikeActivity` manifest entry are deleted in Task 2 once the same logic exists for real. The two build-file changes (JitPack repo, `terminal-view` dependency) are kept as-is — Task 2 needs them.

---

### Task 2: `TerminalHost` — real rendering, replacing Phase 0's raw-text dump

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/TerminalHost.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`
- Delete: `app/src/main/java/com/tmuxmobile/phase0/TerminalSpike.kt`
- Modify: `app/src/main/AndroidManifest.xml` (remove the throwaway `SpikeActivity` entry)

**Interfaces:**
- Consumes: `ControlModeParser.parseLine(String): PaneOutput?` (Phase 0, unchanged), `PaneOutput.text: String`.
- Produces: `TerminalHost(modifier: Modifier, feed: Flow<String>, viewClient: TerminalViewClient)` — a `@Composable`. Task 3's `RawInputBridge` is the `viewClient` argument callers pass in; until Task 3 exists, Task 2's own tests/manual verification use a minimal no-op `TerminalViewClient` (all methods returning `false`/no-ops), the same shape the spike used.

This is integration-level wiring, not meaningfully unit-testable (per the design spec's own Testing section) — verify Steps 2-4 below on the real device, same discipline as Task 1.

- [ ] **Step 1: Write `TerminalHost.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.Flow

/**
 * Renders SSH-sourced tmux pane output via Termux's TerminalView. A no-op local
 * TerminalSession ("/system/bin/cat", no args) exists only to satisfy the library --
 * all real content comes from [feed], fed directly into the session's emulator. See
 * this plan's Task 1 findings for why each setup step below is ordered this way.
 */
@Composable
fun TerminalHost(
    modifier: Modifier = Modifier,
    feed: Flow<String>,
    viewClient: TerminalViewClient,
) {
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var view by remember { mutableStateOf<TerminalView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TerminalView(context, null).apply {
                // Order matters (Task 1 findings): client and text size must be set
                // before attachSession(), which lazily creates the renderer and reads
                // the client during updateSize().
                setTerminalViewClient(viewClient)
                setTextSize(30)
                // TerminalView paints no background once an emulator is attached, so
                // default-color text is nearly invisible against the app's window --
                // must set this explicitly (Task 1 finding).
                setBackgroundColor(0xFF000000.toInt())
                // Hardware/injected key events never arrive without this; the
                // soft-keyboard/IME path is unaffected either way (Task 1 finding).
                isFocusable = true
                isFocusableInTouchMode = true
                doOnLayout {
                    val s = TerminalSession(
                        "/system/bin/cat",
                        "/",
                        arrayOf("cat"),
                        arrayOf("TERM=xterm-256color"),
                        2000,
                        NoOpTerminalSessionClient,
                    )
                    attachSession(s)
                    session = s
                }
                view = this
            }
        },
    )

    LaunchedEffect(session) {
        val activeSession = session ?: return@LaunchedEffect
        feed.collect { text ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            activeSession.emulator.append(bytes, bytes.size)
            view?.onScreenUpdated()
        }
    }
}

/**
 * The no-op local session must never exit (Task 1 finding: exit closes the emulator's
 * queues and appends "[Process completed]"), so onSessionFinished here would only ever
 * fire on an unexpected crash of "/system/bin/cat" -- logging is enough, there's
 * nothing to recover.
 */
private object NoOpTerminalSessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {}
    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {
        android.util.Log.w("TerminalHost", "no-op local session exited unexpectedly")
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
    override fun onPasteTextFromClipboard(session: TerminalSession) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "stack", e) }
}
```

- [ ] **Step 2: Wire it into `MainActivity`, replacing the raw-text `Text(output)` dump**

In `SpikeScreen` (`MainActivity.kt`):
1. Replace the `output` string accumulator with a `terminalFeed = remember { MutableSharedFlow<String>(extraBufferCapacity = 64) }` (import `kotlinx.coroutines.flow.MutableSharedFlow`). `extraBufferCapacity` avoids the emitting coroutine ever suspending on backpressure — dropping/blocking behavior isn't a concern here since the flow is consumed as fast as it's produced.
2. In the existing `LaunchedEffect(Unit)` connect block, keep parsing `paneId` as before, but replace `output += parsed.text + "\n"` with `terminalFeed.emit(parsed.text)`.
3. In the `"terminal"` branch of the `when (viewMode)`, replace the `Text(output, ...)` + "Send test input" button with:
   ```kotlin
   TerminalHost(
       modifier = Modifier.weight(1f),
       feed = terminalFeed,
       viewClient = NoOpTerminalViewClient, // replaced by RawInputBridge in Task 3
   )
   ```
   Add a small private `NoOpTerminalViewClient` object in `MainActivity.kt` for now (all 20 `TerminalViewClient` methods returning `false`/no-ops, same shape as the spike's — Task 3 replaces this call site's argument, not `TerminalHost` itself).
4. Delete the now-unused `output`/`paneId`-driven `Text` composable, the "Send test input" `Button`, and the `TEST_INPUT` constant (Task 3/5 replace this with real input).

- [ ] **Step 3: Delete the spike**

Delete `TerminalSpike.kt` and remove the `SpikeActivity` entry from `AndroidManifest.xml`. Its findings now live in this plan (Step 6 above) and Task 2's own code comments.

- [ ] **Step 4: Verify on the real device**

Build, install, open the app, select Terminal mode against the real `phase0-test` tmux session. Confirm: real pane content (not a synthetic test string) renders with correct colors and formatting, not raw escape codes. This is Step 4 of Task 1's exit criterion, now against live data instead of a fixed string.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/TerminalHost.kt \
        app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/build.gradle.kts settings.gradle.kts
git rm app/src/main/java/com/tmuxmobile/phase0/TerminalSpike.kt
git commit -m "feat: render real tmux output via Termux TerminalView"
```

---

### Task 3: `TmuxKeyMapper` + `RawInputBridge` — raw-mode input

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/TmuxKeyMapper.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/TmuxKeyMapperTest.kt`
- Create: `app/src/main/java/com/tmuxmobile/phase0/RawInputBridge.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt` (pass a real `RawInputBridge` into `TerminalHost`, add swipe detection)

**Interfaces:**
- Consumes: `SshSpikeSession.sendKeys(target: String, keys: String, literal: Boolean = true)` (Task 4 — write Task 4 first, or stub it now and let Task 4 fill it in; either order works since only the signature matters here).
- Produces: `TmuxKeyMapper.specialKeyName(keyCode: Int): String?`, `TmuxKeyMapper.swipeKeyName(direction: SwipeDirection): String`, `enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }`, `class RawInputBridge(scope: CoroutineScope, session: SshSpikeSession, paneId: () -> String?) : TerminalViewClient`.

This task depends on Task 4's `sendKeys` signature — do Task 4 first if working strictly in order.

- [ ] **Step 1: Write the failing test for `TmuxKeyMapper`**

```kotlin
package com.tmuxmobile.phase0

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmuxKeyMapperTest {

    @Test
    fun `maps swipe directions to tmux arrow key names`() {
        assertEquals("Up", TmuxKeyMapper.swipeKeyName(SwipeDirection.UP))
        assertEquals("Down", TmuxKeyMapper.swipeKeyName(SwipeDirection.DOWN))
        assertEquals("Left", TmuxKeyMapper.swipeKeyName(SwipeDirection.LEFT))
        assertEquals("Right", TmuxKeyMapper.swipeKeyName(SwipeDirection.RIGHT))
    }

    @Test
    fun `maps known special key codes to tmux key names`() {
        assertEquals("Up", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals("Down", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals("Left", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals("Right", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals("Enter", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_ENTER))
        assertEquals("BSpace", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_DEL))
        assertEquals("Tab", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_TAB))
        assertEquals("Escape", TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `returns null for printable keys -- those arrive via onCodePoint, not here`() {
        assertNull(TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_A))
        assertNull(TmuxKeyMapper.specialKeyName(KeyEvent.KEYCODE_SPACE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TmuxKeyMapperTest"`
Expected: FAIL — `TmuxKeyMapper`/`SwipeDirection` unresolved.

- [ ] **Step 3: Write `TmuxKeyMapper.kt`**

```kotlin
package com.tmuxmobile.phase0

import android.view.KeyEvent

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Pure key/gesture -> tmux key-name translation (the design spec's one unit-testable
 * piece of this sub-project). Printable characters are NOT handled here -- they arrive
 * via TerminalViewClient.onCodePoint (the IME path) and are sent as literal text.
 */
object TmuxKeyMapper {

    fun swipeKeyName(direction: SwipeDirection): String = when (direction) {
        SwipeDirection.UP -> "Up"
        SwipeDirection.DOWN -> "Down"
        SwipeDirection.LEFT -> "Left"
        SwipeDirection.RIGHT -> "Right"
    }

    fun specialKeyName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_DEL -> "BSpace"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ESCAPE -> "Escape"
        else -> null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TmuxKeyMapperTest"`
Expected: PASS

- [ ] **Step 5: Write `RawInputBridge.kt`**

Long-press is exposed directly by `TerminalViewClient.onLongPress`. Swipe/fling is not part of that interface (verified against the v0.118.3 source in Task 1) — it needs a separate `GestureDetector` wired onto the `TerminalView` itself, so `RawInputBridge` exposes an `onFling` method that `MainActivity` calls from that detector, rather than implementing gesture detection itself.

```kotlin
package com.tmuxmobile.phase0

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Raw-mode input: hardware/injected key events and system-keyboard codepoints go
 * straight to the SSH session instead of the dummy local TerminalSession (Task 1
 * verified both paths are interceptable and don't leak to the dummy process).
 */
class RawInputBridge(
    private val scope: CoroutineScope,
    private val session: SshSpikeSession,
    private val target: () -> String?,
) : TerminalViewClient {

    fun onFling(direction: SwipeDirection) = send(TmuxKeyMapper.swipeKeyName(direction))

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        val name = TmuxKeyMapper.specialKeyName(keyCode) ?: return false
        send(name)
        return true
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = true

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        send(String(Character.toChars(codePoint)), literal = true)
        return true
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        send("Escape")
        return true
    }

    private fun send(keys: String, literal: Boolean = false) {
        val t = target() ?: return
        scope.launch { runCatching { session.sendKeys(t, keys, literal) } }
    }

    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) {}
    override fun logVerbose(tag: String, message: String) {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "stack", e) }
}
```

Note: `onCodePoint`'s `send(..., literal = true)` sends one character at a time (each keystroke commits separately through the IME path per Task 1's findings), so this hits `sendKeys`'s literal branch once per character — no trailing Enter is sent (Task 4's `literal = true` branch only auto-appends Enter when `keys` is a full string with an explicit send; verify this matches Task 4's actual implementation and adjust if it appends Enter per-character, which would be wrong here).

- [ ] **Step 6: Wire `RawInputBridge` into `MainActivity`, replacing `NoOpTerminalViewClient`**

In `SpikeScreen`, replace the Task 2 placeholder:
```kotlin
val rawInputBridge = remember(paneId) { RawInputBridge(scope, session) { paneId ?: SESSION_NAME } }
...
TerminalHost(
    modifier = Modifier.weight(1f),
    feed = terminalFeed,
    viewClient = rawInputBridge,
)
```
Delete `NoOpTerminalViewClient` from `MainActivity.kt` (no longer referenced).

For swipe: wrap the `TerminalHost`'s underlying view with a `GestureDetector` in the `AndroidView`'s `update` block, or add an `onFling`-capable `Modifier.pointerInput` on the Compose side calling `rawInputBridge.onFling(...)` — implementer's choice, whichever is less code given the rest of Task 2's `AndroidView` factory.

- [ ] **Step 7: Verify on the real device**

Attach to a raw-mode TUI (`vim` or `htop`) on the target session. Confirm: typing via the system keyboard sends literal characters, long-press sends Escape, swipe up/down/left/right navigates, and none of it leaks to the dummy local process (same check Task 1's Step 5 used: transcript shouldn't show typed text echoed back from a local shell).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/TmuxKeyMapper.kt \
        app/src/test/java/com/tmuxmobile/phase0/TmuxKeyMapperTest.kt \
        app/src/main/java/com/tmuxmobile/phase0/RawInputBridge.kt \
        app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "feat: raw-mode input via RawInputBridge (keys, IME text, long-press, swipe)"
```

---

### Task 4: Generalize `SshSpikeSession.sendKeys` with a `literal` flag

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/SshSpikeSessionCommandTest.kt`

**Interfaces:**
- Produces: `suspend fun SshSpikeSession.sendKeys(target: String, keys: String, literal: Boolean = true)` — the `literal = true` default preserves the exact existing behavior at both current call sites (Chat view's `onSend`, Phase 0's old test button), so neither needs to change. `literal = false` is new, used by Task 3's `RawInputBridge` for named keys (`"Up"`, `"Escape"`, etc.) with no auto-appended Enter.
- Also produces a pure, testable helper: `internal fun buildSendKeysCommands(target: String, keys: String, literal: Boolean): List<String>` (each string is one full control-mode command line, newline-terminated, in the order they must be flushed).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Test

class SshSpikeSessionCommandTest {

    @Test
    fun `literal mode sends escaped text then a separate Enter command`() {
        assertEquals(
            listOf("send-keys -t %3 -l \"hello\"\n", "send-keys -t %3 Enter\n"),
            buildSendKeysCommands("%3", "hello", literal = true)
        )
    }

    @Test
    fun `literal mode escapes backslashes and quotes`() {
        val commands = buildSendKeysCommands("%3", "say \"hi\\\"", literal = true)
        assertEquals("send-keys -t %3 -l \"say \\\"hi\\\\\\\"\"\n", commands[0])
    }

    @Test
    fun `non-literal mode sends the key name directly, no auto Enter`() {
        assertEquals(
            listOf("send-keys -t %3 Up\n"),
            buildSendKeysCommands("%3", "Up", literal = false)
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SshSpikeSessionCommandTest"`
Expected: FAIL — `buildSendKeysCommands` unresolved.

- [ ] **Step 3: Extract `buildSendKeysCommands` and generalize `sendKeys`**

In `SshSpikeSession.kt`, replace the existing `sendKeys` with:

```kotlin
internal fun buildSendKeysCommands(target: String, keys: String, literal: Boolean): List<String> =
    if (literal) {
        val escaped = keys.replace("\\", "\\\\").replace("\"", "\\\"")
        listOf("send-keys -t $target -l \"$escaped\"\n", "send-keys -t $target Enter\n")
    } else {
        listOf("send-keys -t $target $keys\n")
    }

suspend fun sendKeys(target: String, keys: String, literal: Boolean = true) = withContext(Dispatchers.IO) {
    // Each control-mode command MUST be flushed on its own -- tmux only reads the
    // first command per flush (Phase 0 finding; still true here, unchanged).
    for (command in buildSendKeysCommands(target, keys, literal)) {
        stdin.write(command.toByteArray())
        stdin.flush()
    }
}
```

Remove the old inline escaping logic from the class body (now lives in `buildSendKeysCommands`). The existing `paneId: String` parameter name becomes `target: String` — rename it at the two existing call sites too (`MainActivity.kt`'s Chat `onSend`, and the old Terminal test button, if it still exists at this point in the task order — Task 2 deletes that button, so if Task 2 ran first this only touches Chat view's call site).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SshSpikeSessionCommandTest"`
Expected: PASS

- [ ] **Step 5: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all existing tests, including Chat view's, unaffected by the signature's new default parameter).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt \
        app/src/test/java/com/tmuxmobile/phase0/SshSpikeSessionCommandTest.kt \
        app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "refactor: generalize sendKeys with a literal flag for raw-mode keys"
```

---

### Task 5: `ComposeInputBar` — compose-mode text input

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ComposeInputBar.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- Consumes: `SshSpikeSession.sendKeys(target, keys, literal = true)` (Task 4).
- Produces: `ComposeInputBar(onSend: (String) -> Unit)` — a `@Composable`, structurally identical to `ChatScreen.kt`'s existing input row (`OutlinedTextField` + `Button`), extracted so Chat view and Terminal's compose mode share it instead of duplicating the row.

Per the ladder (reuse before rewrite): `ChatScreen.kt` already has this exact `OutlinedTextField` + `Button` + draft-clearing pattern inline. Extract it into a shared composable instead of writing a second copy.

- [ ] **Step 1: Extract `ComposeInputBar.kt` from `ChatScreen.kt`'s existing input row**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
fun ComposeInputBar(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
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
```

- [ ] **Step 2: Use it from `ChatScreen.kt`**

Replace `ChatScreen`'s inline `Row { OutlinedTextField(...); Button(...) { Text("Send") } }` block with `ComposeInputBar(onSend = onSend)`. Remove the now-duplicate `draft` state and now-unused imports from `ChatScreen.kt`.

- [ ] **Step 3: Run the existing Chat view manual check**

Chat view has no automated UI test (per its own plan) — rebuild, reinstall, open Chat: Claude or Chat: Hermes, confirm sending still works exactly as before extraction. This is a refactor with no behavior change; if anything differs, the extraction was wrong.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ComposeInputBar.kt \
        app/src/main/java/com/tmuxmobile/phase0/ChatScreen.kt
git commit -m "refactor: extract ComposeInputBar, shared by Chat view and Terminal compose mode"
```

---

### Task 6: Raw/compose mode toggle + exit-criterion verification

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- Consumes: `TerminalHost` (Task 2), `RawInputBridge` (Task 3), `ComposeInputBar` (Task 5), `SshSpikeSession.sendKeys(target, keys, literal = true)` (Task 4).

This is the sub-project's final integration task — no new files, just composing what Tasks 2-5 built into the Terminal mode screen, plus the design spec's exit-criterion verification.

- [ ] **Step 1: Add a raw/compose sub-mode toggle inside Terminal mode**

In `SpikeScreen`'s `"terminal"` branch, add a second small state var (e.g. `var inputMode by remember { mutableStateOf("raw") }`, distinct from the existing top-level `viewMode`) and a toggle row above `TerminalHost`:
```kotlin
Row {
    Button(onClick = { inputMode = "raw" }) { Text("Raw") }
    Button(onClick = { inputMode = "compose" }) { Text("Compose") }
}
TerminalHost(modifier = Modifier.weight(1f), feed = terminalFeed, viewClient = rawInputBridge)
if (inputMode == "compose") {
    ComposeInputBar(onSend = { text ->
        scope.launch {
            runCatching { session.sendKeys(paneId ?: SESSION_NAME, text, literal = true) }
                .onFailure { /* surface as needed */ }
        }
    })
}
```
`TerminalHost` stays mounted in both sub-modes (rendering is always live); only whether `ComposeInputBar` is shown, and which surface holds input focus, changes. In raw mode, hardware/IME input already routes through `RawInputBridge` regardless of `ComposeInputBar`'s visibility, since that's wired into `TerminalHost`'s `viewClient`, not gated on `inputMode` — gate it explicitly (e.g. `RawInputBridge` checks an `enabled: () -> Boolean` lambda from `inputMode`, or simplest: don't call `requestFocus()` on the terminal view / don't show the system keyboard for it while `inputMode == "compose"`) so typing in `ComposeInputBar`'s text field doesn't also leak into raw mode's key path. Verify this on-device in Step 3 below — if both modes fire on the same keystroke, that's a bug to fix here, not defer.

- [ ] **Step 2: Verify the full exit criterion on the real device**

Against a real Claude Code tmux session (not just `phase0-test`'s echo test), confirm all three from the design spec:
1. Output renders properly — colors, cursor, formatting, not raw escape codes.
2. Compose mode drives it via typed text (send a real prompt, see it typed and submitted).
3. Raw mode navigates a TUI (`vim` or `htop`) using Esc (long-press) and arrow gestures (swipe).

If a key or gesture test looks unresponsive on-device, check for a stray system dialog stealing window focus first (Task 1's spike hit exactly this and it wasted debugging time before the actual cause was found).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "feat: raw/compose mode toggle for Terminal view, completing Phase 1 sub-project 1"
```
