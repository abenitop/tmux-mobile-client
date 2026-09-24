package com.tmuxmobile.phase0

import kotlinx.coroutines.flow.MutableStateFlow
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
        assertNull("no fingerprint until first connect", dao.getById(id)?.hostKeyFingerprint)
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
    fun `delete removes the host row AND its stored password`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        repo.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
        assertNull("orphaned secrets are still secrets", passwords.get(id))
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
        assertEquals("port must survive the update", 22, dao.getById(id)?.port)
    }

    @Test
    fun `editing a host with a blank password keeps the stored one`() = runTest {
        val dao = FakeHostDao()
        val passwords = FakePasswordStore()
        val repo = HostRepository(dao, passwords)
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "hunter2")

        // Saving an edit whose password field was left empty must not wipe the secret.
        repo.save(Host(id = id, name = "box", hostname = "1.2.3.4", port = 2222, username = "root"), "")

        assertEquals("hunter2", passwords.get(id))
        assertEquals(2222, dao.getById(id)?.port)
    }

    @Test
    fun `recordHostKeyFingerprint on a missing host is a no-op, not a crash`() = runTest {
        val repo = HostRepository(FakeHostDao(), FakePasswordStore())

        repo.recordHostKeyFingerprint(999L, "AA:BB")
    }

    @Test
    fun `host exposes the fingerprint it was given`() = runTest {
        val dao = FakeHostDao()
        val repo = HostRepository(dao, FakePasswordStore())
        val id = repo.save(Host(name = "box", hostname = "1.2.3.4", port = 22, username = "root"), "pw")

        repo.recordHostKeyFingerprint(id, "11:22")

        assertEquals("11:22", repo.host(id)?.hostKeyFingerprint)
    }
}
