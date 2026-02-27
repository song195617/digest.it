package com.digestit.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.digestit.data.local.db.dao.ChatDao
import com.digestit.data.local.db.dao.EpisodeDao
import com.digestit.data.local.db.entity.ChatMessageEntity
import com.digestit.data.local.db.entity.EpisodeEntity
import com.digestit.data.local.db.entity.TranscriptEntity

@Database(
    entities = [EpisodeEntity::class, TranscriptEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun chatDao(): ChatDao
}
