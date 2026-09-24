# Handoff: tmux-mobile-client — P1–P5 milestones complete

**When:** 2026-09-24 (session ended ~08:40 UTC+02:00)
**Repo:** `/home/agent-stack/projects/tmux-mobile-client` (branch `master`)
**Remote:** `https://github.com/abenitop/tmux-mobile-client.git`
**HEAD at end of work:** `27a1f04` — pushed; `43475ce..27a1f04` on `master`.

---

## 1. The five milestones (P1–P5), commit hashes

All five are done, unit-tested, built, and pushed. One commit per milestone.

| # | Commit | What | Key files |
|---|--------|------|-----------|
| P1 | `67e58cc` | **Trust-on-first-use host-key verification** — replaced `PromiscuousVerifier`. First connect pins the host key; matching fingerprint reconnects clean; a changed key is **rejected** into a mismatch screen. | `TofuHostKeyVerifier.kt`, `TofuHostKeyVerifierTest.kt`, `TofuHostKeyVerifierLiveTest.kt`, `HostKeyMismatchScreen.kt` |
| P2 | `02d2039` | **Dark theme** — `TmuxMobileTheme` forces `darkColorScheme`; removes the white frame around `TerminalHost`. | `Theme.kt`, `MainActivity.kt` |
| P3 | `f6f532a` | **WhatsApp-style chat bubbles** — tailed/rounded/tight/colored, run-grouping (only the last bubble in a run gets the tail). | `ChatBubbleShape.kt`, `ChatBubblePlan.kt`, `ChatBubblePlanTest.kt`, `ChatScreen.kt` |
| P4 | `93edd41` | **Diff cards + permission prompts** — Edit/Write tool blocks render as inline diff cards (red/green/monospace/selectable); permission prompts detected from raw pane output with options parsed out of the pane. | `ChatEvent.kt`, `ClaudeCodeTranscriptMapper.kt`, `PermissionPromptDetector.kt`, `PermissionPromptDetectorTest.kt`, `ClaudeCodeEditMappingTest.kt` |
| P5 | `27a1f04` | **Multi-host manager** — Room (`hosts` table) + separate Android-Keystore-backed password store; navigation graph (hosts list → add/edit form → session). Deleted the old single-connection `ConnectionStore`/`ConnectScreen` flow. | `Host.kt`, `HostDao.kt`, `AppDatabase.kt`, `HostRepository.kt`, `HostRepositoryTest.kt`, `PasswordStore.kt`, `HostsScreen.kt`, `HostFormScreen.kt`, `MainActivity.kt` |

### Deviations from the plan (all documented in-code / in commit messages)

1. **P1** — the plan's `HostKeyVerifier` snippet only implemented `verify()`, but sshj 0.41.1's `HostKeyVerifier` has **two** abstract methods (`verify` + `findExistingAlgorithms`). Implemented both; `findExistingAlgorithms` returns `emptyList()` (same as `PromiscuousVerifier`). Otherwise the code would not compile.
2. **P1** — the TOFU fingerprint was persisted in the existing encrypted `ConnectionStore` (now `Connection.kt` carries the field) rather than waiting on P5's Room schema, so the security fix isn't blocked behind a later milestone.
3. **P5** — `HostRepository.delete()` also deletes the host's password (the plan left the secret orphaned in the keystore store).
4. **P5** — `Host` gained a `sessionName` field (default `"main"`); the plan hardcoded `"main"`, but the existing connect flow already takes a session name from the user and a real server won't have the dev fixture's session name.

### Dependency notes (P5, important to re-read if touching build files)

- **KSP pinned to `2.3.12`.** There is **no KSP build for Kotlin 2.4.x** (verified against Maven Central — newest is 2.3.12). The project is on Kotlin **2.4.20** + AGP **9.4.1**; `2.3.12` is verified working against it (`kspDebugKotlin` runs, Room generates its implementations).
- **Room `2.8.5`** (`room-runtime` + `room-ktx`), **navigation-compose `2.10.2`**.
- BouncyCastle `bcprov-jdk18on:1.84` (Android's provider has no X25519; this is pinned to sshj 0.41.1's own resolution).
- sshj `0.41.1`.

---

## 2. Verification status

- **78 unit tests green, 0 failures, 0 skipped.** Includes:
  - `TofuHostKeyVerifierLiveTest` (3 tests) — **real SSH handshakes** against a disposable sshd, proving first-connect pins `f5:fb:10:ce:55:c2:9a:34:1d:2f:fc:74:b0:40:10:69`, a pinned fingerprint reconnects, and a **changed host key is rejected** with mismatch recorded. This test is guarded and skips cleanly if the throwaway server is down.
  - `HostRepositoryTest` (7 tests) — CRUD, password-orphan fix, blank-password-keeps-stored, fingerprint no-op on missing host.
  - `PermissionPromptDetectorTest` (9), `ClaudeCodeEditMappingTest` (7), `ChatBubblePlanTest` (6), plus the pre-existing mapper/parser/key-mapper suites.
- **APK installs, launches, and opens Room cleanly on device** — verified via adb `install -r` + `am start` + logcat (no `FATAL EXCEPTION`, no `Room`/`SQLiteException`, process alive).
- **CAVEAT — full UI device-testing was NOT possible.** The test phone (Pixel 8 Pro) is behind a **secure lockscreen (PIN bouncer / `AlternateBouncerView`)**, which blocks adb UI driving. No credential was guessed. The P1 mismatch-screen path and the P3/P4/P5 visual flows were therefore verified by unit test + live-handshake test + install/launch/logcat, **not** by interactive on-device clicking.

### Build/verify commands (exact, re-runnable)

```bash
cd /home/agent-stack/projects/tmux-mobile-client
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk

# Full build + test:
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Live TOFU handshake test only (needs the disposable sshd up, else it skips):
./gradlew :app:testDebugUnitTest --tests "*.TofuHostKeyVerifierLiveTest"

# Device install/launch (wireless debugging):
ADB=$HOME/android-sdk/platform-tools/adb
D=100.88.177.101:45707   # Pixel 8 Pro Tailscale addr + wireless-debug port (may have rotated)
$ADB -s $D install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s $D shell am start -n com.tmuxmobile.phase0/.MainActivity
```

Gradle wrapper is **not** used for the main build — the session used Gradle `9.6.0` at `~/tools/gradle-9.6.0` via `./gradlew` (wrapper configured to that version). If `./gradlew` resolves elsewhere, check the wrapper config.

---

## 3. Remaining open items (in priority order)

1. **Real end-user server run.** Everything so far was tested against a throwaway sshd (`/tmp/tofu-server/`, ed25519 host key regenerable to trigger mismatch). The app has **never** been connected to the operator's actual server — that needs **the operator's real server credentials** (host, user, key/password, and the tmux session name it actually runs). Do not fabricate or reuse dev-spike credentials for this.
2. **Android 16 KB page alignment.** `libtermux.so` misalignment + `libandroidx.graphics.path.so` are **Play Store blockers** — deferred explicitly, not for this round. (The "Don't Show Again" compat dialog appears at `[604,1510][919,1632]` on the test device if it surfaces again.)
3. **Termux licensing ambiguity.** GPLv3 / Termux licensing is a Play-readiness question — deferred, not for this round.
4. **Permission-prompt per-option key mapping is UNVERIFIED against a live prompt.** `PermissionPromptDetector.optionKeyFor()` currently sends arrow-navigation + Enter, because the key that answers each option could not be confirmed without interactive device access. This is isolated in **one function** (`PermissionPromptDetector.kt`), has its own tests, and is the **single change point** if the real prompt turns out to need a different key sequence. Do not trust it blindly until a live Claude Code permission prompt has been answered on-device.

---

## 4. Hard project constraints (never violate)

- **System keyboard only.** Never build a custom on-screen keyboard. Special keys are sent as **gestures + text tokens** (see `TmuxKeyMapper.kt` / `RawInputBridge.kt`) — never a synthetic on-screen key grid.
- **Do not touch shared infra without explicit approval:**
  - `~/.ssh/phase0-chat-dispatcher` allowlist — permits **both** `tmux -CC attach -t phase0-test` and `tmux -CC new-session -A -s phase0-test` (the `new-session -A` entry was added 2026-09-24 by OpenCode). Still a shared-infra security boundary; don't edit without approval.
  - Shared LiteLLM config (`/opt/agent-stack/config/litellm/`, `routing_callback.py`, `/opt/agent-stack/.env`).
  - Other users' credentials (`avidya`, `darior`, `karmal`).
- **Never use live `~/.claude/projects/**` transcripts as test targets** (they are real session records).
- **Dev key** lives in `app/src/main/assets/`:
  - `phase0_id_ed25519` (private key — **already gitignored**, do not commit).
  - `phase0_id_ed25519.pub` (public key — currently **untracked**; the `.gitignore` only lists the private key).
  - This is a **spike-only** key; not for production use.
- Untracked-but-present (do not sweep into commits): `.kotlin/` (build logs), `AGENTS.md`, `app/src/main/assets/`.

---

## 5. File pointers (read these first on resume)

- `AGENTS.md` — project conventions.
- `docs/reference/product-spec.md` — full product spec.
- `docs/plans/2026-09-24-host-manager-implementation.md` — the plan P1 (its Task 4) and P5 (its Tasks 1–3 + 5–8) were drawn from; note the **priority reshuffle** (TOFU was promoted ahead of host manager).
- `docs/specs/2026-09-23-phase1-chat-view-simple-design.md` — P4 source.
- `app/src/main/java/com/tmuxmobile/phase0/` — all source; the most recently touched are `MainActivity.kt`, `ChatScreen.kt`, `PermissionPromptDetector.kt`, `HostRepository.kt`, `PasswordStore.kt`.

### Source-file map (for quick orientation)

- `MainActivity.kt` — navigation graph (hosts → host_form → session), TOFU wiring, permission-prompt detection on the pane-output path, `Connection.toConnection()`.
- `SshSpikeSession.kt` — SSH session layer; pluggable `hostKeyVerifier` (default `PromiscuousVerifier`, overridden to TOFU at the connect call site).
- `Connection.kt` — plain data class (was formerly nested in the now-deleted `ConnectionStore.kt`).
- `Host.kt` / `HostDao.kt` / `AppDatabase.kt` / `HostRepository.kt` / `PasswordStore.kt` — P5 data layer (Room + Keystore).
- `HostsScreen.kt` / `HostFormScreen.kt` — P5 UI.
- `ChatScreen.kt` / `ChatBubbleShape.kt` / `ChatBubblePlan.kt` — P3 bubbles + P4 diff/permission rendering.
- `ChatEvent.kt` — sealed class (grew `ToolCallChip`, `DiffCard`, `PermissionPrompt` in P4).
- `ClaudeCodeTranscriptMapper.kt` / `PermissionPromptDetector.kt` — P4 mapping + detection.
- `TofuHostKeyVerifier.kt` / `HostKeyMismatchScreen.kt` — P1.
- `Theme.kt` — P2 dark palette + bubble/diff colors.

### Test-map (for orientation)

- `TofuHostKeyVerifierTest.kt` (7), `TofuHostKeyVerifierLiveTest.kt` (3, live sshd), `HostRepositoryTest.kt` (7), `PermissionPromptDetectorTest.kt` (9), `ClaudeCodeEditMappingTest.kt` (7), `ChatBubblePlanTest.kt` (6), plus the pre-existing `ClaudeCodeTranscriptMapperTest`, `ControlModeParserTest`, `HermesChatAdapterRowTest`, `HermesMessageMapperTest`, `SshSpikeSessionCommandTest`, `TmuxKeyMapperTest`, `ChatItemKeyTest`, `ClaudeCodeChatAdapterCommandTest`.

### Known gotchas (learned this session)

- **JVM unit tests cannot use `android.util.Base64` or `org.json`** — they're Android stubs. For the TOFU test this was solved by generating a real throwaway RSA-2048 key with `openssl` in `/tmp/tofu_key/` and inlining its sshj-format fingerprint. `org.json` in the mapper is handled because those tests run with the Android Gradle plugin's JSON stub available — if a new JSON test fails on a stub, mirror that pattern.
- **`GenericShape` is `androidx.compose.foundation.shape.GenericShape`**, not `ui.graphics`. The round-rect factory uses `topLeftCornerRadius`/`topRightCornerRadius`/`bottomLeftCornerRadius`/`bottomRightCornerRadius`, **not** `topLeft`/`topRight`/etc.
- **LazyColumn keys must be unique AND stable** — keyed by `index + content-identity` (`itemKey()`); a real session had 274 duplicate groups that would throw on content-only keys.
