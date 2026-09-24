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
    /**
     * Trust-on-first-use host key. Null means "never connected to this host yet"; filled
     * in on the first successful handshake, then enforced on every later connect.
     */
    val hostKeyFingerprint: String? = null,
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
            .putString("hostKeyFingerprint", connection.hostKeyFingerprint)
            .apply()
    }

    fun load(): Connection? {
        val hostname = prefs.getString("hostname", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val sessionName = prefs.getString("sessionName", null) ?: return null
        return Connection(
            hostname = hostname,
            port = prefs.getInt("port", 22),
            username = username,
            password = password,
            sessionName = sessionName,
            hostKeyFingerprint = prefs.getString("hostKeyFingerprint", null),
        )
    }

    /** Records the host key seen on first connect. Keeps every other field as stored. */
    fun saveHostKeyFingerprint(fingerprint: String) {
        prefs.edit().putString("hostKeyFingerprint", fingerprint).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
