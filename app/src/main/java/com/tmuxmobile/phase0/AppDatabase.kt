package com.tmuxmobile.phase0

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Host::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "tmux-mobile.db",
                ).build().also { instance = it }
            }
    }
}
