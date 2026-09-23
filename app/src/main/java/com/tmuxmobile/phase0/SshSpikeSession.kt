package com.tmuxmobile.phase0

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.security.Security

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

        installBouncyCastle()

        client = SSHClient()
        // ponytail: host-key verification disabled for this spike only.
        // Phase 1 must pin the VPS's real host key fingerprint instead of
        // trusting blindly — fine for now since the target is a throwaway
        // test session reachable only over Tailscale.
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(host, port)
        client.authPublickey(username, client.loadKeys(keyFile.absolutePath))

        session = client.startSession()
        // tmux -CC requires a pty on stdin: without one it exits immediately
        // ("tcgetattr failed: Inappropriate ioctl for device") and emits no
        // control-mode protocol. Verified empirically against tmux 3.4 and
        // sshj 0.41.1 — this deviates from the plan, which assumed sshj's
        // bare exec() (no pty) was sufficient.
        session.allocateDefaultPTY()
        command = session.exec("tmux -CC attach -t $sessionName")
        stdin = command.outputStream
        stdout = BufferedReader(InputStreamReader(command.inputStream))
    }

    private fun installBouncyCastle() {
        // Android ships its own stripped-down provider named "BC" (see Google's
        // "Cryptography Changes in Android P"). sshj's SecurityUtils only adds the
        // bundled BouncyCastle if Security.getProvider("BC") is null, so without this
        // the app gets Android's BC, which has no X25519 -> "no such algorithm:
        // X25519 for provider BC". Replacing the provider is the fix recommended by
        // both sshj's issue tracker and Apache MINA SSHD's Android docs.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        } else {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
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

    /**
     * Opens a SECOND channel on the same authenticated connection and streams the
     * command's stdout line by line (sshj supports concurrent sessions per SSHClient).
     * The channel stays open until the remote command exits — callers use it for the
     * long-lived `tail -F` / poll-loop commands behind Chat view.
     */
    fun execStream(command: String): Flow<String> = flow {
        val execSession = withContext(Dispatchers.IO) { client.startSession() }
        val execCommand = withContext(Dispatchers.IO) { execSession.exec(command) }
        val reader = BufferedReader(InputStreamReader(execCommand.inputStream))
        while (true) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            emit(line)
        }
    }

    suspend fun sendKeys(target: String, keys: String, literal: Boolean = true) = withContext(Dispatchers.IO) {
        // Each control-mode command MUST be flushed on its own. If both commands
        // arrive in one write, tmux executes only the first and discards the rest,
        // so "send-keys Enter" was silently dropped and the text was typed but never
        // submitted. Verified against tmux 3.4 over a live control-mode channel:
        // single-flush => only the literal text lands in the pane; one flush per
        // command => the line is typed and submitted.
        for (command in buildSendKeysCommands(target, keys, literal)) {
            stdin.write(command.toByteArray())
            stdin.flush()
        }
    }

    fun close() {
        runCatching { command.close() }
        runCatching { session.close() }
        runCatching { client.disconnect() }
    }
}

/**
 * Builds the control-mode command lines for one `sendKeys` call, in the order they must
 * be flushed (see sendKeys for why each needs its own flush).
 *
 * `literal = true` types [keys] verbatim into the pane and then submits with Enter;
 * `literal = false` passes [keys] through as tmux key names ("Up", "Escape", "C-c") and
 * appends NO Enter, so navigation keys act without submitting a line.
 *
 * Order of escapes matters: backslash-doubling runs first, so the backslash introduced
 * before an embedded quote is not itself doubled.
 */
internal fun buildSendKeysCommands(target: String, keys: String, literal: Boolean): List<String> =
    if (literal) {
        val escaped = keys.replace("\\", "\\\\").replace("\"", "\\\"")
        listOf("send-keys -t $target -l \"$escaped\"\n", "send-keys -t $target Enter\n")
    } else {
        listOf("send-keys -t $target $keys\n")
    }
