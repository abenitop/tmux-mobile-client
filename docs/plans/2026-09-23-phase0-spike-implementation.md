# Phase 0 Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This project's own handoff note:** on `agent-stack`, execution of this plan goes to Hermes via tmux relay, not to Claude Code's own subagent tooling — Claude Code designs/specs/plans, Hermes builds. If you are Hermes reading this: work task-by-task in order, treat each task's steps as the unit of work, and don't skip the verification step in any task.

**Goal:** Prove that an Android app can speak tmux's control-mode protocol correctly over SSH — attach to a real tmux session, receive its output, and send input to it — before investing in real UI or terminal emulation.

**Architecture:** Single-Activity Kotlin/Compose app. `sshj` opens an SSH exec channel running `tmux -CC attach -t phase0-test` against a dedicated disposable session on the agent-stack VPS. A hand-rolled line parser extracts `%output` events and appends their decoded text to a plain scrolling `Text`. One button sends a hardcoded string via `send-keys`.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose (BOM 2026.09.00), Android Gradle Plugin 9.4.1, Gradle 9.6.0, JDK 17, sshj 0.41.1, kotlinx-coroutines-android 1.11.0.

**Spec:** `docs/specs/2026-09-23-phase0-spike-design.md` (this plan implements that spec's Phase 0 scope only; the full product vision is `docs/reference/product-spec.md`).

## Global Constraints

- Min SDK 26 (Android 8); compileSdk/targetSdk 36 (Android 16) — matches Google Play's current target-API floor (verified via developer.android.com, 2026-09-23).
- All library/tool versions below were verified against their primary sources (Google's Maven metadata, GitHub releases, AGP release notes) on 2026-09-23 — do not substitute newer versions without re-verifying; do not use older ones from training memory.
- SSH via `sshj` 0.41.1 only (Apache 2.0 license) — no `jsch`.
- No terminal emulation (no ANSI/VT100, no cursor placement, no colors) — raw decoded text only. Deferred to Phase 1.
- No real input UI, no hosts list, no session list, no navigation — one screen, one hardcoded test string, one button.
- No auto-reconnect or error recovery beyond not crashing.
- Target: dedicated `phase0-test` tmux session (tmux 3.4 confirmed installed) on the agent-stack VPS, Tailscale IP `100.66.191.51`, user `agent-stack` — never a live Claude Code/Hermes session, to avoid disrupting real work.
- Test device: operator's physical Android phone over wireless ADB via Tailscale. No emulator — this VPS has no `/dev/kvm` (confirmed via `/proc/cpuinfo` and `/dev/kvm` absence), so emulation isn't viable here.
- The private SSH key generated for this spike is never committed to git — generated locally, `.gitignore`d, copied into the app's assets only on the machine that builds it.
- Package/application ID `com.tmuxmobile.phase0` is a placeholder for this phase only — the product spec lists the real app name/package ID as an open question for a later phase.

---

### Task 1: Project scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: a buildable, empty Android app module (`:app`) that later tasks add code to. No Kotlin source yet.

- [ ] **Step 1: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "tmux-mobile-client-phase0"
include(":app")
```

- [ ] **Step 2: Write root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Write `.gitignore`**

```
.gradle/
build/
app/build/
local.properties
.idea/
*.iml
.DS_Store
```

- [ ] **Step 5: Write `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tmuxmobile.phase0"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tmuxmobile.phase0"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-phase0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.hierynomus:sshj:0.41.1")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 6: Write `app/src/main/AndroidManifest.xml`**

No `<activity>` yet — Task 5 adds it, once `MainActivity` exists. Declaring it earlier would make this task's build fail on a missing class.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="tmux-phase0"
        android:theme="@android:style/Theme.Material.Light.NoActionBar" />

</manifest>
```

- [ ] **Step 7: Generate the Gradle wrapper pinned to 9.6.0**

Run: `gradle wrapper --gradle-version 9.6.0` (requires a system Gradle install to bootstrap from — any recent version works for this one-time step; the generated wrapper is what everyone uses afterward)

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (the properties file should show `gradle-9.6.0-bin.zip`).

- [ ] **Step 8: Verify the empty project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore app/build.gradle.kts app/src/main/AndroidManifest.xml gradlew gradlew.bat gradle/
git commit -m "phase0: project scaffolding (Kotlin/Compose, AGP 9.4.1, sshj 0.41.1)"
```

---

### Task 2: Control-mode line parser (TDD)

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ControlModeParser.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/ControlModeParserTest.kt`

**Interfaces:**
- Produces: `data class PaneOutput(val paneId: String, val text: String)` and `object ControlModeParser { fun parseLine(line: String): PaneOutput? }` — Task 5 (`MainActivity`) calls `ControlModeParser.parseLine(line)` on every line read from the SSH session and uses `.paneId` / `.text` on non-null results.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.tmuxmobile.phase0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlModeParserTest {

    @Test
    fun `parses a simple output line`() {
        val result = ControlModeParser.parseLine("%output %3 hello world")
        assertEquals(PaneOutput("%3", "hello world"), result)
    }

    @Test
    fun `returns null for non-output control lines`() {
        assertNull(ControlModeParser.parseLine("%begin 123 456 1"))
        assertNull(ControlModeParser.parseLine("%window-add @1"))
        assertNull(ControlModeParser.parseLine(""))
    }

    @Test
    fun `decodes an octal-escaped newline`() {
        val result = ControlModeParser.parseLine("%output %3 line1\\012line2")
        assertEquals("line1\nline2", result?.text)
    }

    @Test
    fun `decodes an escaped backslash`() {
        val result = ControlModeParser.parseLine("%output %3 path\\\\to\\\\file")
        assertEquals("path\\to\\file", result?.text)
    }

    @Test
    fun `handles multiple panes independently`() {
        val a = ControlModeParser.parseLine("%output %1 from pane one")
        val b = ControlModeParser.parseLine("%output %2 from pane two")
        assertEquals("%1", a?.paneId)
        assertEquals("%2", b?.paneId)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.ControlModeParserTest"`
Expected: FAIL — `ControlModeParser` and `PaneOutput` don't exist yet.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.tmuxmobile.phase0

data class PaneOutput(val paneId: String, val text: String)

object ControlModeParser {
    private val outputLineRegex = Regex("^%output (%\\d+) (.*)$")

    fun parseLine(line: String): PaneOutput? {
        val match = outputLineRegex.matchEntire(line) ?: return null
        val (paneId, escaped) = match.destructured
        return PaneOutput(paneId, decodeEscapes(escaped))
    }

    private fun decodeEscapes(escaped: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 3 < escaped.length &&
                escaped[i + 1] in '0'..'7' &&
                escaped[i + 2] in '0'..'7' &&
                escaped[i + 3] in '0'..'7'
            ) {
                val octal = escaped.substring(i + 1, i + 4)
                sb.append(octal.toInt(8).toChar())
                i += 4
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tmuxmobile.phase0.ControlModeParserTest"`
Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ControlModeParser.kt app/src/test/java/com/tmuxmobile/phase0/ControlModeParserTest.kt
git commit -m "phase0: control-mode %output line parser with tests"
```

---

### Task 3: SSH key and dedicated test session (VPS infra)

No Android/Kotlin files touched. This provisions the target the app connects to. Run on the `agent-stack` VPS as the `agent-stack` user — no sudo needed, nothing here touches another service.

**Files:**
- Create (not committed — gitignored): `app/src/main/assets/phase0_id_ed25519`, `app/src/main/assets/phase0_id_ed25519.pub`
- Modify: `.gitignore`

- [ ] **Step 1: Generate a dedicated ed25519 keypair for this spike only**

```bash
mkdir -p app/src/main/assets
ssh-keygen -t ed25519 -N "" -f app/src/main/assets/phase0_id_ed25519 -C "phase0-spike-only"
```

- [ ] **Step 2: Add the key to `.gitignore` before it can ever be staged**

Add this line to `.gitignore`:

```
app/src/main/assets/phase0_id_ed25519
```

(The `.pub` file is fine to commit — it's not secret — but isn't needed by the app, so leave it out of both git and the APK; keep it only on disk for reference.)

- [ ] **Step 3: Restrict the key in `authorized_keys` to only this forced command**

Append to `~/.ssh/authorized_keys` (the `agent-stack` user's own file — no sudo):

```
command="tmux -CC attach -t phase0-test",no-pty,no-agent-forwarding,no-X11-forwarding,no-port-forwarding <paste contents of phase0_id_ed25519.pub here>
```

This means the key can do exactly one thing — attach to `phase0-test` in control mode — regardless of what command the client requests. No pty is allocated, matching sshj's default `exec()` behavior (which doesn't request one).

- [ ] **Step 4: Create the dedicated disposable test session**

```bash
tmux new-session -d -s phase0-test
```

- [ ] **Step 5: Verify the restricted key works before touching the app**

From any machine that can reach `100.66.191.51:22` (e.g. the VPS itself, over loopback):

```bash
ssh -i app/src/main/assets/phase0_id_ed25519 -o StrictHostKeyChecking=accept-new agent-stack@100.66.191.51
```

Expected: drops directly into control-mode output (lines starting with `%begin`, `%output`, etc.) — not a normal shell prompt. Ctrl-C to exit. If you see a shell prompt instead, the forced command isn't applied — check the `authorized_keys` line for typos before moving on.

- [ ] **Step 6: Commit (gitignore change only)**

```bash
git add .gitignore
git commit -m "phase0: gitignore the spike SSH private key"
```

---

### Task 4: SSH session wrapper

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt`

**Interfaces:**
- Consumes: `android.content.Context` (for reading the bundled asset key and internal storage), nothing from Task 2 or 3's code directly (Task 3 only needs to have happened so the *target* exists — the asset file itself must be present at build time, from Task 3 step 1).
- Produces: `class SshSpikeSession(context: Context, host: String, port: Int, username: String, assetKeyName: String)` with `suspend fun connect(sessionName: String)`, `fun lines(): Flow<String>`, `suspend fun sendKeys(paneId: String, text: String)`, `fun close()` — Task 5 (`MainActivity`) constructs one instance and calls all four.

**Implementation notes verified against sshj 0.41.1's source before writing this** (`SSHClient.java`, `KeyProviderUtil.java`, `Channel.java`): `ed25519` keys from `ssh-keygen` are written in the newer "OpenSSH v1" private-key format, not PKCS8. sshj's string-content `loadKeys(String privateKey, String publicKey, PasswordFinder)` overload only supports PKCS8, so it won't parse our key directly. The file-path overload `loadKeys(String location)` auto-detects the format correctly (confirmed in `KeyProviderUtil`, which recognizes the `-----BEGIN OPENSSH PRIVATE KEY-----` header and maps it to `OpenSSHKeyV1KeyFile`). Since Android assets aren't real filesystem paths, the key must be copied to internal storage first, then loaded from that real file path.

- [ ] **Step 1: Write `SshSpikeSession.kt`**

```kotlin
package com.tmuxmobile.phase0

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

class SshSpikeSession(
    private val appContext: Context,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val assetKeyName: String,
) {
    private lateinit var client: SSHClient
    private lateinit var session: Session
    private lateinit var command: Session.Command
    private lateinit var stdin: OutputStream
    private lateinit var stdout: BufferedReader

    suspend fun connect(sessionName: String) = withContext(Dispatchers.IO) {
        val keyFile = copyAssetKeyToInternalStorage()

        client = SSHClient()
        // ponytail: host-key verification disabled for this spike only.
        // Phase 1 must pin the VPS's real host key fingerprint instead of
        // trusting blindly — fine for now since the target is a throwaway
        // test session reachable only over Tailscale.
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        client.authPublickey(username, client.loadKeys(keyFile.absolutePath))

        session = client.startSession()
        command = session.exec("tmux -CC attach -t $sessionName")
        stdin = command.outputStream
        stdout = BufferedReader(InputStreamReader(command.inputStream))
    }

    private fun copyAssetKeyToInternalStorage(): File {
        val keyFile = File(appContext.filesDir, assetKeyName)
        if (!keyFile.exists()) {
            appContext.assets.open(assetKeyName).use { input ->
                keyFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return keyFile
    }

    fun lines(): Flow<String> = flow {
        while (true) {
            val line = withContext(Dispatchers.IO) { stdout.readLine() } ?: break
            emit(line)
        }
    }

    suspend fun sendKeys(paneId: String, text: String) = withContext(Dispatchers.IO) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        stdin.write("send-keys -t $paneId -l \"$escaped\"\n".toByteArray())
        stdin.write("send-keys -t $paneId Enter\n".toByteArray())
        stdin.flush()
    }

    fun close() {
        runCatching { command.close() }
        runCatching { session.close() }
        runCatching { client.disconnect() }
    }
}
```

- [ ] **Step 2: Verify it compiles against the pinned sshj artifact**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (No unit test here — this class only does anything meaningful against a live SSH target, which is what Task 5's manual verification covers, per the design spec's testing section.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt
git commit -m "phase0: sshj-based control-mode session wrapper"
```

---

### Task 5: UI wiring and end-to-end verification

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ControlModeParser.parseLine(String): PaneOutput?` (Task 2), `SshSpikeSession(...)` and its four members (Task 4).
- Produces: nothing further — this is the leaf that makes the app runnable.

- [ ] **Step 1: Add the launcher activity to the manifest**

Replace the `<application ... />` self-closing tag in `app/src/main/AndroidManifest.xml` with:

```xml
    <application
        android:allowBackup="false"
        android:label="tmux-phase0"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
```

- [ ] **Step 2: Write `MainActivity.kt`**

Note: the pane ID is *not* hardcoded — a freshly created session's pane ID depends on how many panes have existed on that tmux server before it, which isn't knowable at plan-writing time. The screen waits for the first `%output` event to learn the real pane ID, then enables the send button.

```kotlin
package com.tmuxmobile.phase0

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val HOST = "100.66.191.51"
private const val PORT = 22
private const val USERNAME = "agent-stack"
private const val ASSET_KEY_NAME = "phase0_id_ed25519"
private const val SESSION_NAME = "phase0-test"
private const val TEST_INPUT = "echo phase0-spike-ok"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpikeScreen(appContext = applicationContext)
                }
            }
        }
    }
}

@Composable
fun SpikeScreen(appContext: Context) {
    var output by remember { mutableStateOf("connecting...\n") }
    var paneId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val session = remember { SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME) }

    LaunchedEffect(Unit) {
        runCatching {
            session.connect(SESSION_NAME)
            session.lines().collect { line ->
                val parsed = ControlModeParser.parseLine(line)
                if (parsed != null) {
                    if (paneId == null) paneId = parsed.paneId
                    output += parsed.text + "\n"
                }
            }
        }.onFailure { e -> output += "ERROR: ${e.message}\n" }
    }

    DisposableEffect(Unit) {
        onDispose { session.close() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = output,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        )
        Button(
            onClick = {
                val target = paneId ?: return@Button
                scope.launch {
                    runCatching { session.sendKeys(target, TEST_INPUT) }
                        .onFailure { e -> output += "SEND ERROR: ${e.message}\n" }
                }
            },
            enabled = paneId != null
        ) {
            Text(if (paneId != null) "Send test input" else "Waiting for pane...")
        }
    }
}
```

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Install on the operator's phone over wireless ADB**

On the phone: enable Developer Options → Wireless debugging, join the Tailscale tailnet, note its Tailscale IP.

```bash
adb connect <phone-tailscale-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 5: Run the exit-criterion check from the design spec**

1. Launch the app on the phone. It should show `connecting...` then real pane content within a couple seconds, and the button should change from "Waiting for pane..." to "Send test input".
2. In a separate terminal on the VPS, watch the real session directly: `tmux attach -t phase0-test`.
3. Tap "Send test input" on the phone.
4. Confirm in the VPS terminal that `echo phase0-spike-ok` was typed and executed in the real session.
5. Confirm on the phone screen that the echoed output (`phase0-spike-ok`) appears, appended to the scrolling text — sourced from the control-mode `%output` stream, not from watching the terminal directly.

Both 4 and 5 happening is the Phase 0 exit criterion. If either fails, that's the actual finding this phase exists to produce — report what broke rather than pushing forward into Phase 1.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "phase0: single-screen UI wiring, completes the spike"
```
