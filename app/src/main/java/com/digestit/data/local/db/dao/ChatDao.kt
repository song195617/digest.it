package com.digestit.data.local.db.dao

import androidx.room.*
import com.digestit.data.local.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE episodeId = :episodeId ORDER BY timestamp ASC")
    fun getMessages(episodeId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE episodeId = :episodeId")
    suspend fun clearHistory(episodeId: String)

    @Query("SELECT sessionId FROM chat_messages WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getSessionId(episodeId: String): String?
}
