package com.tmuxmobile.phase0

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Per-host passwords, Android Keystore-backed. Never plaintext, never in Room.
 *
 * An interface only because Android Keystore cannot run in a plain JVM unit test, so
 * HostRepository is tested against a fake.
 */
interface PasswordStore {
    fun put(hostId: Long, password: String)
    fun get(hostId: Long): String?
    fun delete(hostId: Long)
}

/** A separate preferences file from the app's other encrypted stores: hosts and the
 *  single saved connection are different lifetimes and must not clear each other. */
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
