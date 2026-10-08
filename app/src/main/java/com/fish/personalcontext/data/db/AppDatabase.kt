package com.fish.personalcontext.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TimelineEventEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "personal_context.db"
    }
}
