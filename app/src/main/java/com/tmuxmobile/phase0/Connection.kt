package com.tmuxmobile.phase0

/** A concrete SSH endpoint ready to connect to. Plain data, no storage logic -- Room's
 *  [Host] is the persistent record; this is the in-memory shape the session layer takes. */
data class Connection(
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String,
    val sessionName: String,
    /**
     * Trust-on-first-use host key. Null means "never connected to this host yet"; filled
     * in on the first successful handshake, then enforced on every later connect.
     */
    val hostKeyFingerprint: String? = null,
)
