# Host Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This project's own handoff note:** on `agent-stack`, execution goes to Hermes via tmux relay, not Claude Code's own subagent tooling — Claude Code designs/specs/plans, Hermes builds. Work task-by-task in order; each task is independently committable and testable per its own steps.

**Goal:** Replace the hardcoded host/port/username/key constants with a real host manager — add a server by hostname/IP, username and password; see saved servers in a list; tap one to open the existing Terminal/Chat screen against it.

**Architecture:** See `docs/specs/2026-09-24-host-manager-design.md`. Room-backed host metadata + Android Keystore-backed password storage, password-only SSH auth (key auth stays in `SshSpikeSession` unchanged, per the spec's documented deviation), trust-on-first-use host-key verification replacing the current `PromiscuousVerifier`, and a two-destination `navigation-compose` graph (Hosts → Session) replacing `MainActivity`'s single Composable.

**Tech Stack:** Same as prior sub-projects (Kotlin 2.4.20, Compose BOM 2026.09.00, AGP 9.4.1, Gradle 9.6.0, JDK 17, sshj 0.41.1), plus: Room (`androidx.room:room-runtime` + `room-ktx` + `room-compiler` via KSP) for host storage, `androidx.security:security-crypto` for encrypted password storage, `androidx.navigation:navigation-compose` for the Hosts→Session flow. None of these are in the project yet — verify exact versions against this project's existing AndroidX/Compose BOM versions at implementation time (see Known Implementation Risks).

## Global Constraints

- Same VPS/session target as prior sub-projects for the *session* screen (`phase0-test` stays the one hardcoded tmux session name) — this plan only replaces how the app gets its **connection** info (host/port/username/password), not session selection. No session list, no multi-session support (see design spec's non-goals).
- `SshSpikeSession`'s existing key-based auth path (`assetKeyName`) must keep working unchanged — Hermes's own device-testing setup depends on it. Password auth is additive, not a replacement.
- Password never touches Room or plaintext storage — only `EncryptedSharedPreferences` (Android Keystore-backed).
- Host-key verification changes from accept-any (`PromiscuousVerifier`) to trust-on-first-use, per host.

---

### Task 1: Room entities — `Host`, `HostDao`, `AppDatabase`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/Host.kt`
- Create: `app/src/main/java/com/tmuxmobile/phase0/HostDao.kt`
- Create: `app/src/main/java/com/tmuxmobile/phase0/AppDatabase.kt`
- Modify: `app/build.gradle.kts`, `settings.gradle.kts` (Room needs the KSP plugin, not just a dependency)

**Interfaces:**
- Produces: `data class Host(id: Long, name: String, hostname: String, port: Int, username: String, hostKeyFingerprint: String?)`, `interface HostDao` (`observeAll(): Flow<List<Host>>`, `getById(id: Long): Host?`, `insert(host: Host): Long`, `update(host: Host)`, `delete(host: Host)`), `AppDatabase.get(context): AppDatabase`.

Room's annotation processor verifies the schema at compile time; this task's own correctness check is a successful build (`./gradlew :app:assembleDebug`), not a unit test — there is no business logic here yet (Task 3 adds and tests that). This matches the project's existing discipline of treating framework wiring as integration-level, not unit-tested for its own sake.

- [ ] **Step 1: Add Room to the build**

In `settings.gradle.kts`'s plugin management (or wherever this project declares plugin versions — check the existing `pluginManagement` block before adding), add the KSP plugin matching this project's Kotlin version (2.4.20). In `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") // verify exact version against Kotlin 2.4.20 at implementation time
}

dependencies {
    // ... existing deps ...
    implementation("androidx.room:room-runtime:2.8.2") // verify latest against this project's AndroidX versions
    implementation("androidx.room:room-ktx:2.8.2")
    ksp("androidx.room:room-compiler:2.8.2")
}
```

- [ ] **Step 2: Write `Host.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * hostKeyFingerprint starts null (no host connected to yet) and is filled in on first
 * successful connect -- trust-on-first-use, see TofuHostKeyVerifier.
 */
@Entity(tableName = "hosts")
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val hostKeyFingerprint: String? = null,
)
```

- [ ] **Step 3: Write `HostDao.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY name")
    fun observeAll(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: Long): Host?

    @Insert
    suspend fun insert(host: Host): Long

    @Update
    suspend fun update(host: Host)

    @Delete
    suspend fun delete(host: Host)
}
```

- [ ] **Step 4: Write `AppDatabase.kt`**

```kotlin
package com.tmuxmobile.phase0

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Host::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "tmux-mobile.db",
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 5: Build to confirm Room's annotation processor accepts the schema**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, with generated `HostDao_Impl` / `AppDatabase_Impl` sources under `build/generated/ksp/`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/Host.kt \
        app/src/main/java/com/tmuxmobile/phase0/HostDao.kt \
        app/src/main/java/com/tmuxmobile/phase0/AppDatabase.kt \
        app/build.gradle.kts settings.gradle.kts
git commit -m "feat: add Room entities for host storage"
```

---

### Task 2: Encrypted password storage

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/PasswordStore.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `interface PasswordStore` (`put(hostId: Long, password: String)`, `get(hostId: Long): String?`, `delete(hostId: Long)`), `class EncryptedPasswordStore(context: Context) : PasswordStore`. The interface exists specifically so Task 3's `HostRepository` can be tested against a fake — Android Keystore-backed encryption cannot run in a plain JVM unit test.

- [ ] **Step 1: Add Jetpack Security to the build**

```kotlin
// app/build.gradle.kts, in dependencies { }
implementation("androidx.security:security-crypto:1.1.0") // verify latest stable at implementation time
```

- [ ] **Step 2: Write `PasswordStore.kt`**

```kotlin
package com.tmuxmobile.phase0

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface PasswordStore {
    fun put(hostId: Long, password: String)
    fun get(hostId: Long): String?
    fun delete(hostId: Long)
}

/**
 * Android Keystore-backed, never plaintext, never in Room. A separate preferences file
 * from anything else the app stores, keyed by host id.
 */
class EncryptedPasswordStore(context: Context) : PasswordStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "host_passwords",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun put(hostId: Long, password: String) {
        prefs.edit().putString(hostId.toString(), password).apply()
    }

    override fun get(hostId: Long): String? = prefs.getString(hostId.toString(), null)

    override fun delete(hostId: Long) {
        prefs.edit().remove(hostId.toString()).apply()
    }
}
```

This is integration-level (Android Keystore doesn't run in a plain JVM unit test) — verified on a real device in Task 8's exit criterion, same discipline as the rest of this project.

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/PasswordStore.kt app/build.gradle.kts
git commit -m "feat: encrypted password storage backed by Android Keystore"
```

---

### Task 3: `HostRepository`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HostRepository.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/HostRepositoryTest.kt`

**Interfaces:**
- Consumes: `HostDao` (Task 1), `PasswordStore` (Task 2).
- Produces: `class HostRepository(dao: HostDao, passwords: PasswordStore)` — `observeHosts(): Flow<List<Host>>`, `suspend fun save(host: Host, password: String): Long`, `suspend fun delete(host: Host)`, `suspend fun passwordFor(hostId: Long): String?`, `suspend fun recordHostKeyFingerprint(hostId: Long, fingerprint: String)`.

Tested against hand-written in-memory fakes of `HostDao` and `PasswordStore` (matching this project's existing plain-JUnit style — `ControlModeParserTest`, `HermesChatAdapterRowTest`, etc. — none of which use Robolectric or a real database).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeHostDao : HostDao {
    private val hosts = MutableStateFlow<List<Host>>(emptyList())
    private var nextId = 1L

    override fun observeAll() = hosts

    override suspend fun getById(id: Long) = hosts.value.find { it.id == id }

    override suspend fun insert(host: Host): Long {
        val id = nextId++
        hosts.value = hosts.value + host.copy(id = id)
        return id
    }

    override suspend fun update(host: Host) {
        hosts.value = hosts.value.map { if (it.id == host.id) host else it }
    }

    override suspend fun delete(host: Host) {
        hosts.value = hosts.value.filter { it.id != host.id }
    }
}

private class FakePasswordStore : PasswordStore {
    val stored = mutableMapOf<Long, String>()
    override fun put(hostId: Long, password: String) { stored[hostId] = password }
    override fun get(hostId: Long) = stored[hostId]
    override fun delete(hostId: Long) { stored.remove(hostId) }
}

class HostRepositoryTest {

    @Test
    fun `save inserts a new host and stores its password separately`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)

        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        assertEquals("hunter2", passwords.get(id))
        assertEquals("box", dao.getById(id)?.name)
    }

    @Test
    fun `save on an existing host updates it, not inserts a duplicate`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        repo.save(Host(id = id, name = "renamed", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        assertEquals(1, dao.observeAll().value.size)
        assertEquals("renamed", dao.getById(id)?.name)
    }

    @Test
    fun `delete removes the host row and its stored password`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        repo.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
        assertNull(passwords.get(id))
    }

    @Test
    fun `recordHostKeyFingerprint updates only that field`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        repo.recordHostKeyFingerprint(id, "AA:BB:CC")

        assertEquals("AA:BB:CC", dao.getById(id)?.hostKeyFingerprint)
        assertEquals("box", dao.getById(id)?.name)
    }
}
```

Note: `runTest` is from `kotlinx-coroutines-test`, not yet a test dependency in this project — add it in this step.

- [ ] **Step 2: Add the coroutines test dependency and run to verify the tests fail**

```kotlin
// app/build.gradle.kts, in dependencies { }
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0") // match the app's coroutines version
```

Run: `./gradlew :app:testDebugUnitTest --tests "*.HostRepositoryTest"`
Expected: FAIL — `HostRepository` unresolved.

- [ ] **Step 3: Write `HostRepository.kt`**

```kotlin
package com.tmuxmobile.phase0

class HostRepository(
    private val dao: HostDao,
    private val passwords: PasswordStore,
) {
    fun observeHosts() = dao.observeAll()

    suspend fun save(host: Host, password: String): Long {
        val id = if (host.id == 0L) dao.insert(host) else {
            dao.update(host)
            host.id
        }
        passwords.put(id, password)
        return id
    }

    suspend fun delete(host: Host) {
        dao.delete(host)
        passwords.delete(host.id)
    }

    suspend fun passwordFor(hostId: Long): String? = passwords.get(hostId)

    suspend fun recordHostKeyFingerprint(hostId: Long, fingerprint: String) {
        val host = dao.getById(hostId) ?: return
        dao.update(host.copy(hostKeyFingerprint = fingerprint))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.HostRepositoryTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HostRepository.kt \
        app/src/test/java/com/tmuxmobile/phase0/HostRepositoryTest.kt \
        app/build.gradle.kts
git commit -m "feat: HostRepository combining Room storage and encrypted passwords"
```

---

### Task 4: Trust-on-first-use host-key verification + password auth in `SshSpikeSession`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/TofuHostKeyVerifier.kt`
- Test: `app/src/test/java/com/tmuxmobile/phase0/TofuHostKeyVerifierTest.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt`

**Interfaces:**
- Produces: `internal fun fingerprintMatches(known: String?, presented: String): Boolean` (pure, unit-tested), `class TofuHostKeyVerifier(knownFingerprint: String?, onFirstConnect: (String) -> Unit) : HostKeyVerifier`. `SshSpikeSession`'s constructor gains `password: String? = null` and `hostKeyVerifier: HostKeyVerifier = PromiscuousVerifier()`, both defaulted so the existing key-based call site in `MainActivity.kt` (used by Chat view / Hermes's dev testing) keeps compiling and behaving identically unchanged.

- [ ] **Step 1: Write the failing test for the pure comparison logic**

```kotlin
package com.tmuxmobile.phase0

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TofuHostKeyVerifierTest {

    @Test
    fun `no known fingerprint means first connect -- always matches`() {
        assertTrue(fingerprintMatches(known = null, presented = "AA:BB"))
    }

    @Test
    fun `matching fingerprint passes`() {
        assertTrue(fingerprintMatches(known = "AA:BB", presented = "AA:BB"))
    }

    @Test
    fun `mismatched fingerprint fails`() {
        assertFalse(fingerprintMatches(known = "AA:BB", presented = "CC:DD"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TofuHostKeyVerifierTest"`
Expected: FAIL — `fingerprintMatches` unresolved.

- [ ] **Step 3: Write `TofuHostKeyVerifier.kt`**

```kotlin
package com.tmuxmobile.phase0

import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/** known == null means "never connected before" -- first connect always succeeds. */
internal fun fingerprintMatches(known: String?, presented: String): Boolean =
    known == null || known == presented

/**
 * Trust-on-first-use, replacing the PromiscuousVerifier used since Phase 0 (which
 * accepted any host key unconditionally). First connect to a host: accept whatever key
 * it presents and report the fingerprint via [onFirstConnect] so the caller can persist
 * it. Every later connect: compare against [knownFingerprint] and reject on mismatch --
 * same threat model as SSH's known_hosts, scoped to per-host state in Room instead of a
 * shared file.
 */
class TofuHostKeyVerifier(
    private val knownFingerprint: String?,
    private val onFirstConnect: (String) -> Unit,
) : HostKeyVerifier {
    var mismatch: Boolean = false
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val presented = SecurityUtils.getFingerprint(key)
        if (knownFingerprint == null) onFirstConnect(presented)
        val ok = fingerprintMatches(knownFingerprint, presented)
        if (!ok) mismatch = true
        return ok
    }
}
```

Verify `SecurityUtils.getFingerprint(PublicKey)` is sshj 0.41.1's actual API surface at implementation time (see Known Implementation Risks) — if the method name or return format differs, adjust here; the pure `fingerprintMatches` logic and its test are unaffected either way.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.TofuHostKeyVerifierTest"`
Expected: PASS.

- [ ] **Step 5: Add password auth + pluggable host-key verifier to `SshSpikeSession`**

```kotlin
// SshSpikeSession.kt -- constructor and connect() changes only, rest of the class unchanged
class SshSpikeSession(
    private val appContext: Context,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val assetKeyName: String? = null,
    private val password: String? = null,
    private val hostKeyVerifier: HostKeyVerifier = PromiscuousVerifier(),
) {
    // ... existing lateinit vars unchanged ...

    suspend fun connect(sessionName: String) = withContext(Dispatchers.IO) {
        installBouncyCastle()

        client = SSHClient()
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connect(host, port)
        if (password != null) {
            client.authPassword(username, password)
        } else {
            val keyFile = copyAssetKeyToInternalStorage()
            client.authPublickey(username, client.loadKeys(keyFile.absolutePath))
        }

        // ... rest of connect() (session/exec/pty/capture-pane) unchanged ...
    }

    // ... rest of class unchanged ...
}
```

`copyAssetKeyToInternalStorage()` now only runs in the key-auth branch, so it's fine that `assetKeyName` is nullable — add a `requireNotNull(assetKeyName)` inside that function if it isn't already null-safe, so a misconfigured call (`password == null && assetKeyName == null`) fails loudly instead of silently.

- [ ] **Step 6: Run the full unit test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all tests, including the existing Chat view and SSH command tests — the new constructor parameters are all defaulted, so `MainActivity.kt`'s existing `SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME)` call keeps compiling and behaving identically).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/TofuHostKeyVerifier.kt \
        app/src/test/java/com/tmuxmobile/phase0/TofuHostKeyVerifierTest.kt \
        app/src/main/java/com/tmuxmobile/phase0/SshSpikeSession.kt
git commit -m "feat: password auth + trust-on-first-use host-key verification"
```

---

### Task 5: Navigation scaffold + `HostsScreen`

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HostsScreen.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: `HostRepository.observeHosts()` (Task 3).
- Produces: `HostsScreen(hosts: List<Host>, onConnect: (Host) -> Unit, onAdd: () -> Unit, onEdit: (Host) -> Unit, onDelete: (Host) -> Unit)` — a `@Composable`. `MainActivity` becomes a `NavHost` with destinations `"hosts"`, `"host_form?hostId={hostId}"` (Task 6), `"session/{hostId}"` (Task 8, wraps the existing Terminal/Chat screen).

This task wires navigation and the list screen only; the session screen still uses the hardcoded `HOST`/`PORT`/etc. constants until Task 8 switches it over — keeps each task buildable and testable on its own.

- [ ] **Step 1: Add `navigation-compose`**

```kotlin
// app/build.gradle.kts, in dependencies { }
implementation("androidx.navigation:navigation-compose:2.9.0") // verify against this project's Compose BOM at implementation time
```

- [ ] **Step 2: Write `HostsScreen.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HostsScreen(
    hosts: List<Host>,
    onConnect: (Host) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Host) -> Unit,
    onDelete: (Host) -> Unit,
) {
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(hosts) { host ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${host.name} (${host.username}@${host.hostname}:${host.port})")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConnect(host) }) { Text("Connect") }
                            Button(onClick = { onEdit(host) }) { Text("Edit") }
                            Button(onClick = { onDelete(host) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire `MainActivity` as a `NavHost`, `"hosts"` as the start destination**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HostRepository(AppDatabase.get(applicationContext).hostDao(), EncryptedPasswordStore(applicationContext))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val hosts by repository.observeHosts().collectAsState(initial = emptyList())
                    NavHost(navController = navController, startDestination = "hosts") {
                        composable("hosts") {
                            HostsScreen(
                                hosts = hosts,
                                onConnect = { host -> navController.navigate("session/${host.id}") },
                                onAdd = { navController.navigate("host_form") },
                                onEdit = { host -> navController.navigate("host_form?hostId=${host.id}") },
                                onDelete = { host -> /* wired in Task 6 alongside the form's own delete button, or here directly via a LaunchedEffect + repository.delete -- implementer's choice */ },
                            )
                        }
                        // "host_form" and "session/{hostId}" destinations added in Tasks 6 and 8.
                    }
                }
            }
        }
    }
}
```

The `SpikeScreen` Composable and its hardcoded constants stay exactly as they are for now, unreferenced by this new `NavHost` — Task 8 removes them and adds the `"session/{hostId}"` destination that calls into a renamed/adapted version of it.

- [ ] **Step 4: Verify on the real device**

Build, install, confirm the app now opens on an empty Hosts screen (no crash, no hosts yet) with a visible "+" button. Existing Terminal/Chat functionality is temporarily unreachable from the UI until Task 8 — that's expected mid-plan, not a regression to fix now.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HostsScreen.kt \
        app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt \
        app/build.gradle.kts
git commit -m "feat: navigation scaffold and Hosts list screen"
```

---

### Task 6: Add/Edit Host screen

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HostFormScreen.kt`
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- Consumes: `HostRepository.save(host, password)` (Task 3).
- Produces: `HostFormScreen(existing: Host?, existingPassword: String?, onSave: (Host, String) -> Unit, onCancel: () -> Unit)` — a `@Composable`.

- [ ] **Step 1: Write `HostFormScreen.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun HostFormScreen(
    existing: Host?,
    existingPassword: String?,
    onSave: (Host, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var hostname by remember { mutableStateOf(existing?.hostname ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existingPassword ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = hostname, onValueChange = { hostname = it }, label = { Text("Hostname or IP") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            val portNumber = port.toIntOrNull() ?: 22
            onSave(
                Host(
                    id = existing?.id ?: 0,
                    name = name,
                    hostname = hostname,
                    port = portNumber,
                    username = username,
                    hostKeyFingerprint = existing?.hostKeyFingerprint,
                ),
                password,
            )
        }) { Text("Save") }
        Button(onClick = onCancel) { Text("Cancel") }
    }
}
```

- [ ] **Step 2: Add the `"host_form"` destination to `MainActivity`'s `NavHost`**

```kotlin
composable(
    "host_form?hostId={hostId}",
    arguments = listOf(navArgument("hostId") { type = NavType.LongType; defaultValue = 0L }),
) { backStackEntry ->
    val hostId = backStackEntry.arguments?.getLong("hostId") ?: 0L
    var existing by remember { mutableStateOf<Host?>(null) }
    var existingPassword by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hostId) {
        if (hostId != 0L) {
            existing = repository.observeHosts().first().find { it.id == hostId }
            existingPassword = repository.passwordFor(hostId)
        }
    }
    HostFormScreen(
        existing = existing,
        existingPassword = existingPassword,
        onSave = { host, password ->
            scope.launch {
                repository.save(host, password)
                navController.popBackStack()
            }
        },
        onCancel = { navController.popBackStack() },
    )
}
```

(`scope` here is a `rememberCoroutineScope()` added alongside `navController`/`hosts` in `onCreate`'s `setContent` block; `first()` needs `kotlinx.coroutines.flow.first`.) Also wire `HostsScreen`'s `onDelete` (left as a placeholder in Task 5) to `scope.launch { repository.delete(host) }` now that `scope` exists.

- [ ] **Step 3: Verify on the real device**

Build, install, tap "+", fill in a host (any values — no real server needed yet, this only tests storage), Save, confirm it appears in the Hosts list. Tap Edit on it, confirm the form pre-fills correctly including the password field. Tap Delete, confirm it disappears.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HostFormScreen.kt \
        app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "feat: add/edit host form, wired to HostRepository"
```

---

### Task 7: Host-key mismatch screen

**Files:**
- Create: `app/src/main/java/com/tmuxmobile/phase0/HostKeyMismatchScreen.kt`

**Interfaces:**
- Produces: `HostKeyMismatchScreen(host: Host, onGoBack: () -> Unit)` — a `@Composable`. Wired into the session screen in Task 8, where `TofuHostKeyVerifier.mismatch` is checked after a failed `connect()`.

- [ ] **Step 1: Write `HostKeyMismatchScreen.kt`**

```kotlin
package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown INSTEAD of connecting when TofuHostKeyVerifier reports a mismatch -- never a
 * silent bypass. Same threat model as SSH's "REMOTE HOST IDENTIFICATION HAS CHANGED"
 * warning.
 */
@Composable
fun HostKeyMismatchScreen(host: Host, onGoBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Host key changed", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The server at ${host.hostname}:${host.port} presented a different identity " +
                "than it did before. This could mean the server was reinstalled, or " +
                "that something is intercepting your connection. Not connecting.",
        )
        Button(onClick = onGoBack) { Text("Go back") }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (No device verification yet — nothing calls this screen until Task 8 wires the real connect flow. Task 8's exit criterion covers triggering it for real.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/HostKeyMismatchScreen.kt
git commit -m "feat: host-key mismatch warning screen"
```

---

### Task 8: Wire the session screen to a real `Host`, remove hardcoded constants, full exit-criterion verification

**Files:**
- Modify: `app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-7.
- Produces: the `"session/{hostId}"` `NavHost` destination; `SpikeScreen` (or a renamed equivalent) now takes a `Host` and a `password: String` instead of reading the top-level `HOST`/`PORT`/`USERNAME`/`ASSET_KEY_NAME` constants.

This is the sub-project's final integration task — no new files, composing everything built so far and deleting what it replaces.

- [ ] **Step 1: Change `SpikeScreen`'s signature to take a `Host` and password**

```kotlin
@Composable
fun SpikeScreen(appContext: Context, host: Host, password: String, onHostKeyMismatch: () -> Unit) {
    // ... existing body, but:
    // - `session = remember { SshSpikeSession(appContext, host.hostname, host.port, host.username, password = password,
    //       hostKeyVerifier = TofuHostKeyVerifier(host.hostKeyFingerprint) { fp -> /* persist via repository, see Step 2 */ }) }`
    //   replaces the old `SshSpikeSession(appContext, HOST, PORT, USERNAME, ASSET_KEY_NAME)` line.
    // - SESSION_NAME stays a hardcoded top-level constant (session list is out of scope for this
    //   sub-project, per the design spec's non-goals) -- only HOST/PORT/USERNAME/ASSET_KEY_NAME
    //   go away, since those are what the host manager now supplies.
    // - The existing runCatching around connect() should, on failure, check whether the session's
    //   TofuHostKeyVerifier reported a mismatch (expose it, e.g. via a small wrapper class holding
    //   both the SshSpikeSession and its verifier) and call onHostKeyMismatch() instead of just
    //   emitting an error string into the terminal feed.
}
```

Delete the top-level `private const val HOST`, `PORT`, `USERNAME`, `ASSET_KEY_NAME` — `SESSION_NAME` stays.

- [ ] **Step 2: Add the `"session/{hostId}"` destination**

```kotlin
composable(
    "session/{hostId}",
    arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
) { backStackEntry ->
    val hostId = backStackEntry.arguments?.getLong("hostId") ?: return@composable
    var host by remember { mutableStateOf<Host?>(null) }
    var password by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    LaunchedEffect(hostId) {
        host = repository.observeHosts().first().find { it.id == hostId }
        password = repository.passwordFor(hostId)
    }
    val currentHost = host
    val currentPassword = password
    when {
        mismatch && currentHost != null -> HostKeyMismatchScreen(currentHost, onGoBack = { navController.popBackStack() })
        currentHost != null && currentPassword != null -> SpikeScreen(
            appContext = applicationContext,
            host = currentHost,
            password = currentPassword,
            onHostKeyMismatch = { mismatch = true },
        )
        // else: still loading host/password from the repository -- a brief blank frame is
        // acceptable here, this sub-project doesn't add a loading spinner.
    }
}
```

On a successful first connect (no prior `hostKeyFingerprint`), persist the fingerprint reported by `TofuHostKeyVerifier`'s `onFirstConnect` callback via `scope.launch { repository.recordHostKeyFingerprint(hostId, fingerprint) }`.

- [ ] **Step 3: Verify the full exit criterion on the real device**

Against a real server (or the existing `agent-stack`/`phase0-test` target, using password auth this time instead of the bundled key — verify the test account accepts password auth, or use a separate throwaway account/VM that does): add a host by IP + username + password, see it in the list, tap it, land on the Terminal screen actually connected. Edit and delete a saved host. Reconnect to a host whose server host key has changed (e.g., regenerate the test server's SSH host key) and confirm the mismatch warning shows instead of silently connecting.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tmuxmobile/phase0/MainActivity.kt
git commit -m "feat: wire session screen to real hosts, completing the host manager sub-project"
```

## Known Implementation Risks

- **Exact dependency versions** for Room, `androidx.security:security-crypto`, `androidx.navigation:navigation-compose`, and the KSP plugin version matching Kotlin 2.4.20 are not verified against this project's specific AndroidX/Compose BOM combination — verify each at implementation time the same way the terminal-view JitPack coordinate was verified in Phase 1 sub-project 1, rather than assuming the versions written here are current.
- **`SecurityUtils.getFingerprint(PublicKey)`** is assumed to be sshj 0.41.1's real API for computing a host-key fingerprint — verify against sshj's actual source/docs before writing `TofuHostKeyVerifier`; if the method differs, only that one call site changes, not the pure `fingerprintMatches` logic or its test.
- **Testing password auth end-to-end** (Task 8) needs a real server account that accepts password authentication — the existing `agent-stack` test account may be key-only (verify, and if so, use a separate account or VM for this specific test rather than changing the shared account's auth config).
