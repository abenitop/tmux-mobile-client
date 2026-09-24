package com.tmuxmobile.phase0

import net.schmizz.sshj.SSHClient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.Security
import java.net.InetSocketAddress
import java.net.Socket

/**
 * End-to-end proof that trust-on-first-use actually runs during a real SSH handshake --
 * the unit tests only pin the pure comparison logic, which would still pass if the
 * verifier were never installed on the client.
 *
 * Runs against a DISPOSABLE sshd on 100.66.191.51:22998 (dedicated throwaway host key, a
 * `tofutest` user, nothing shared with the real SSH config). The tests are skipped, not
 * failed, when that server isn't running, so the suite stays green on a machine without it.
 *
 * Only the handshake is exercised -- sshj verifies the host key during key exchange, so an
 * authentication failure (which we don't care about here) happens strictly after the
 * verification under test.
 */
class TofuHostKeyVerifierLiveTest {

    private val host = "100.66.191.51"
    private val port = 22998

    /** Fingerprint of the throwaway server's host key, computed independently from its
     *  public key blob (MD5 of the SSH wire encoding -- sshj's format). */
    private val serverFingerprint = "f5:fb:10:ce:55:c2:9a:34:1d:2f:fc:74:b0:40:10:69"

    @Test
    fun `first connect to an unknown host succeeds and reports the server fingerprint`() {
        assumeServerRunning()
        val seen = mutableListOf<String>()

        connect(TofuHostKeyVerifier(knownFingerprint = null, onFirstConnect = { seen.add(it) }))

        assertEquals("the presented key must be reported so it can be pinned", 1, seen.size)
        assertEquals(serverFingerprint, seen.single())
    }

    @Test
    fun `reconnecting to a host whose fingerprint was pinned succeeds`() {
        assumeServerRunning()

        val verifier = TofuHostKeyVerifier(knownFingerprint = serverFingerprint, onFirstConnect = {})

        connect(verifier)

        assertFalse("a matching key must not be flagged as a mismatch", verifier.mismatch)
    }

    @Test
    fun `a changed host key is rejected, not silently accepted`() {
        assumeServerRunning()
        // What a reinstalled server (or a man-in-the-middle) would present instead.
        val verifier = TofuHostKeyVerifier(
            knownFingerprint = "00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00",
            onFirstConnect = {},
        )

        val failure = runCatching { connect(verifier) }.exceptionOrNull()

        assertNotNull("connect() must fail rather than proceed", failure)
        assertTrue("the verifier must record the mismatch so the UI can explain it", verifier.mismatch)
    }

    /** Handshake only: asserts the verifier's verdict by whether the handshake completed. */
    private fun connect(verifier: TofuHostKeyVerifier) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        } else {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
        val client = SSHClient()
        try {
            client.addHostKeyVerifier(verifier)
            client.connect(host, port)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun assumeServerRunning() {
        val reachable = runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
        }.isSuccess
        assumeTrue("disposable TOFU test sshd not running on $host:$port", reachable)
    }
}
