package com.tmuxmobile.phase0

import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/**
 * known == null means "never connected before" -- first connect always succeeds.
 *
 * Anything else (including an empty string from a corrupt store) must match exactly.
 * An empty string is a real stored value, not "unknown": treating it as unknown would
 * accept any host key, which is the PromiscuousVerifier behaviour this replaces.
 */
internal fun fingerprintMatches(known: String?, presented: String): Boolean =
    known == null || known == presented

/**
 * Trust-on-first-use, replacing the PromiscuousVerifier used since Phase 0 (which
 * accepted any host key unconditionally). First connect to a host: accept whatever key
 * it presents and report the fingerprint via [onFirstConnect] so the caller can persist
 * it. Every later connect: compare against [knownFingerprint] and reject on mismatch --
 * same threat model as SSH's known_hosts, scoped to per-host state instead of a shared
 * file.
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

    /**
     * sshj asks for the host-key algorithms the verifier wants proposed for a host:port
     * before the handshake. This verifier has no per-host algorithm policy -- it judges
     * whatever key is presented -- and empty means "let sshj use its defaults", which is
     * exactly what PromiscuousVerifier returned.
     */
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}
