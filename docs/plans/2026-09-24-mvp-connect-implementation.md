# MVP Connect Screen — Implementation Plan (24-hour path to real usage)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Why this plan exists instead of `2026-09-24-host-manager-implementation.md`:** the operator needs to actually use this app against their own real server within ~24 hours, replacing a third-party terminal app they're using as a stopgap right now. The full host-manager plan (Room-backed multi-host list, add/edit/delete, trust-on-first-use host-key hardening) is real, correct, and still the right eventual design — but it's more work than fits the deadline. This plan is the smallest correct slice that gets someone actually using their own server today: one remembered connection, encrypted password storage, no list/CRUD UI, host-key verification unchanged (`PromiscuousVerifier`, same as every prior sub-project) rather than newly hardened. The full plan is not discarded, just deferred — see "Fast-follow" at the end.

**Goal:** Replace the hardcoded `HOST`/`PORT`/`USERNAME`/`ASSET_KEY_NAME` constants with a single real connection the operator enters once (hostname, port, username, password, tmux session name), stored encrypted on-device. Fix the session-attach command so it works against a server that has no pre-existing tmux session (every prior sub-project only ever tested against `phase0-test`, which already existed).

**Architecture:** One encrypted-prefs-backed `Connection` (not Room — no list, so no database is needed). `MainActivity` branches on whether a connection is saved: none → `ConnectScreen`; saved → the existing Terminal/Chat screen, now parameterized by the saved connection instead of constants. `SshSpikeSession` gains password auth (host-key verification unchanged for this plan). `tmux -CC attach -t <name>` becomes `tmux -CC new-session -A -s <name>` (create-or-attach) so a brand-new server with no tmux session works on first connect.

**Tech Stack:** Same as prior sub-projects (Kotlin 2.4.20, Compose BOM 2026.09.00, sshj 0.41.1), plus `androidx.security:security-crypto` for encrypted storage — the only new dependency. No Room, no `navigation-compose`.

## Global Constraints

- `SshSpikeSession`'s existing key-based auth path (`assetKeyName`) must keep working unchanged — Hermes's own device-testing setup depends on it. Password auth is additive.
- Password never touches plaintext storage — `EncryptedSharedPreferences` (Android Keystore-backed) only.
- Host-key verification is explicitly unchanged in this plan (`PromiscuousVerifier`) — flagged to the operator as a deferred hardening step, not silently dropped.
- No multi-connection list, no edit/delete UI beyond a single "forget this connection" action (Task 3) — one remembered connection only, matching the deadline-driven scope cut above.

---

### Task 1: Encrypted connection storage

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ConnectionStore.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `data class Connection(hostname: String, port: Int, username: String, password: String, sessionName: String)`, `class ConnectionStore(context: Context)` — `save(connection: Connection)`, `load(): Connection?`, `clear()`.

- [ ] **Step 1: Add Jetpack Security to the build**

```kotlin
// app/build.gradle.kts, in dependencies { }
implementation("androidx.security:security-crypto:1.1.0") // verify latest stable at implementation time
```

- [ ] **Step 2: Write `ConnectionStore.kt`**

```kotlin
package com.tmuxmobile.phase0

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Connection(
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String,
    val sessionName: String,
)

/**
 * One remembered connection, Android Keystore-backed. No list, no per-host rows -- the
 * MVP scope is a single connection the operator enters once. Multi-host storage (Room)
 * is the fast-follow plan, not this one.
 */
class ConnectionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "connection",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(connection: Connection) {
        prefs.edit()
            .putString("hostname", connection.hostname)
            .putInt("port", connection.port)
            .putString("username", connection.username)
            .putString("password", connection.password)
            .putString("sessionName", connection.sessionName)
            .apply()
    }

    fun load(): Connection? {
        val hostname = prefs.getString("hostname", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val sessionName = prefs.getString("sessionName", null) ?: return null
        return Connection(hostname, prefs.getInt("port", 22), username, password, sessionName)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
```

Integration-level (Android Keystore doesn't run in a plain JVM unit test) — verified on a real device in Task 4's exit criterion, same discipline as the rest of this project.

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ConnectionStore.kt app/build.gradle.kts
git commit -m "feat: encrypted single-connection storage"
```

---

### Task 2: Password auth in `SshSpikeSession` + create-or-attach session command

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt`

**Interfaces:**
- `SshSpikeSession`'s constructor gains `password: String? = null`, defaulted so the existing key-based call site keeps compiling unchanged. `connect()`'s tmux command changes from attach-only to create-or-attach.

- [ ] **Step 1: Add password auth, defaulted so existing callers are unaffected**

```kotlin
// SshSpikeSession.kt -- constructor and connect() changes only, rest of the class unchanged
class SshSpikeSession(
    private val appContext: Context,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val assetKeyName: String? = null,
    private val password: String? = null,
) {
    // ... existing lateinit vars unchanged ...

    suspend fun connect(sessionName: String) = withContext(Dispatchers.IO) {
        installBouncyCastle()

        client = SSHClient()
        // ponytail: host-key verification still PromiscuousVerifier, unchanged from
        // every prior sub-project -- trust-on-first-use hardening is fast-follow work
        // (docs/plans/2026-09-24-host-manager-implementation.md Task 4), not this plan.
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        if (password != null) {
            client.authPassword(username, password)
        } else {
            val keyFile = copyAssetKeyToInternalStorage()
            client.authPublickey(username, client.loadKeys(keyFile.absolutePath))
        }

        session = client.startSession()
        session.allocateDefaultPTY()
        // Changed from "tmux -CC attach -t $sessionName": every prior sub-project only
        // ever ran against phase0-test, a session that already existed on the dev VPS.
        // A real operator's own server has no tmux session at all on first connect --
        // "new-session -A" creates it if missing, or attaches if it already exists,
        // either way entering control mode. Verify this behaves identically to plain
        // attach against an EXISTING session before relying on it (Step 3).
        command = session.exec("tmux -CC new-session -A -s $sessionName")
        stdin = command.outputStream
        stdout = BufferedReader(InputStreamReader(command.inputStream))

        // ... capture-pane snapshot request unchanged ...
    }

    // ... rest of class unchanged ...
}
```

`copyAssetKeyToInternalStorage()` now only runs in the key-auth branch — add a `requireNotNull(assetKeyName)` inside it if it isn't already null-safe, so a misconfigured call (`password == null && assetKeyName == null`) fails loudly instead of silently.

- [ ] **Step 2: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — the new constructor parameter is defaulted, so every existing call site (`MainActivity.kt`'s Chat view / Hermes's dev testing) keeps compiling and behaving identically.

- [ ] **Step 3: Verify `new-session -A` on the real device against BOTH cases**

Against the existing `phase0-test` session (already exists): confirm `tmux -CC new-session -A -s phase0-test` attaches to it exactly as `tmux -CC attach -t phase0-test` did — same control-mode output, same ability to send input, nothing regresses for Hermes's own dev/test flow.

Against a session name that does NOT exist yet (e.g. `tmux kill-session -t mvp-fresh-test 2>/dev/null` first, then connect with `sessionName = "mvp-fresh-test"`): confirm it creates the session and attaches cleanly, with a normal shell prompt visible in Terminal mode — this is the actual case a new operator hits on their first-ever connection.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt
git commit -m "feat: password auth + create-or-attach session (works against a server with no existing tmux session)"
```

---

### Task 3: `ConnectScreen`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/ConnectScreen.kt`

**Interfaces:**
- Produces: `ConnectScreen(onConnect: (Connection) -> Unit)` — a `@Composable`. Field defaults: port `"22"`, session name `"main"` (arbitrary but stable default, since a brand-new server has nothing named yet).

- [ ] **Step 1: Write `ConnectScreen.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ConnectScreen(onConnect: (Connection) -> Unit) {
    var hostname by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sessionName by remember { mutableStateOf("main") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Connect to your server")
        OutlinedTextField(value = hostname, onValueChange = { hostname = it }, label = { Text("Hostname or IP") }, modifier = Modifier.fillMaxSize())
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxSize())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxSize())
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxSize(),
        )
        OutlinedTextField(value = sessionName, onValueChange = { sessionName = it }, label = { Text("Session name") }, modifier = Modifier.fillMaxSize())
        Button(onClick = {
            onConnect(Connection(hostname, port.toIntOrNull() ?: 22, username, password, sessionName))
        }) { Text("Connect") }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/ConnectScreen.kt
git commit -m "feat: ConnectScreen for entering the operator's own server details"
```

---

### Task 4: Wire it together in `MainActivity`, remove hardcoded constants, exit-criterion verification

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- `SpikeScreen`'s signature changes from reading top-level `HOST`/`PORT`/`USERNAME`/`ASSET_KEY_NAME` constants to taking a `Connection` directly.

- [ ] **Step 1: Branch `MainActivity` on whether a connection is saved**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ConnectionStore(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var connection by remember { mutableStateOf(store.load()) }
                    val current = connection
                    if (current == null) {
                        ConnectScreen(onConnect = { c ->
                            store.save(c)
                            connection = c
                        })
                    } else {
                        SpikeScreen(appContext = applicationContext, connection = current)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Change `SpikeScreen` to take a `Connection` instead of reading constants**

```kotlin
@Composable
fun SpikeScreen(appContext: Context, connection: Connection) {
    // ... existing body unchanged, except:
    // - `val session = remember { SshSpikeSession(appContext, connection.hostname, connection.port,
    //       connection.username, password = connection.password) }`
    //   replaces the old `SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME)` line.
    // - `session.connect(SESSION_NAME)` becomes `session.connect(connection.sessionName)`.
}
```

Delete the top-level `private const val HOST`, `PORT`, `USERNAME`, `ASSET_KEY_NAME`, `SESSION_NAME` — all five are now per-connection instead of hardcoded.

Add a minimal "forget this connection" action (a single button, e.g. in the existing top row alongside Terminal/Chat mode buttons) that calls `store.clear()` and resets `connection` to `null`, so a typo doesn't permanently lock the operator out without reinstalling.

- [ ] **Step 3: Verify the full exit criterion on the real device**

Fresh install (or clear app data): app opens on `ConnectScreen`, not the old hardcoded flow. Enter a real server's hostname/port/username/password and a session name (new or existing). Confirm it connects, renders the real shell, and both Terminal and Chat modes work exactly as they did against the dev VPS. Force-close and reopen the app: confirm it reconnects automatically using the saved connection (no need to re-enter details). Use "forget this connection" and confirm it returns to `ConnectScreen`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "feat: wire app to a real operator-entered connection, completing the MVP connect flow"
```

## Fast-follow (not in this plan's scope)

Once the operator is actually using the app against their own server, `docs/plans/2026-09-24-host-manager-implementation.md` is the next real increment: multiple saved hosts (Room), a proper list/add/edit/delete UI, and — the security-relevant one — trust-on-first-use host-key verification replacing `PromiscuousVerifier`. Worth prioritizing that hardening step specifically once the deadline pressure is off, since real password-based access to a real server is exactly the case host-key verification protects.
