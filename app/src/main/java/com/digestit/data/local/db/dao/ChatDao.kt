package com.digestit.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digestit.data.local.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT sessionId FROM chat_messages WHERE episodeId = :episodeId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSessionId(episodeId: String): Flow<String?>

    @Query("SELECT * FROM chat_messages WHERE episodeId = :episodeId AND sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(episodeId: String, sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE episodeId = :episodeId")
    suspend fun clearHistory(episodeId: String)

    @Query("SELECT sessionId FROM chat_messages WHERE episodeId = :episodeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSessionId(episodeId: String): String?
}
