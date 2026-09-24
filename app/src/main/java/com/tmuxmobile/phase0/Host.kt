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
    /**
     * Which tmux session to create-or-attach. The plan hardcoded this; it is per-host here
     * because the existing connect flow already takes a session name from the user and a
     * real server will not have the dev fixture's session name.
     */
    val sessionName: String = "main",
    val hostKeyFingerprint: String? = null,
)
