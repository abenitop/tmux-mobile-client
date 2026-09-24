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
