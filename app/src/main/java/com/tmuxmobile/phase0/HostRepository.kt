package com.tmuxmobile.phase0

/**
 * Hosts (Room) + their passwords (Android Keystore). Kept separate so Host rows are
 * readable/listable without touching secrets.
 */
class HostRepository(
    private val dao: HostDao,
    private val passwords: PasswordStore,
) {
    fun observeHosts() = dao.observeAll()

    suspend fun host(id: Long): Host? = dao.getById(id)

    /** Inserts a new host (id == 0) or updates the existing one. Stores the password
     *  against the resulting id, so a new host's password is keyed correctly. */
    suspend fun save(host: Host, password: String): Long {
        val id = if (host.id == 0L) {
            dao.insert(host)
        } else {
            dao.update(host)
            host.id
        }
        // An edited host whose password field was left blank keeps its stored password.
        if (password.isNotEmpty()) passwords.put(id, password)
        return id
    }

    /** Deletes the host AND its password -- leaving the secret behind would orphan it. */
    suspend fun delete(host: Host) {
        dao.delete(host)
        passwords.delete(host.id)
    }

    suspend fun passwordFor(hostId: Long): String? = passwords.get(hostId)

    /** Trust-on-first-use: records the host key seen on first connect. */
    suspend fun recordHostKeyFingerprint(hostId: Long, fingerprint: String) {
        val host = dao.getById(hostId) ?: return
        dao.update(host.copy(hostKeyFingerprint = fingerprint))
    }
}
