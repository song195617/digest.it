package com.digestit.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digestit.data.local.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

data class ChatSessionSummaryRow(
    val sessionId: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val messageCount: Int,
    val preview: String,
)

@Dao
interface ChatDao {
    @Query("SELECT sessionId FROM chat_messages WHERE episodeId = :episodeId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSessionId(episodeId: String): Flow<String?>

    @Query("SELECT * FROM chat_messages WHERE episodeId = :episodeId AND sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(episodeId: String, sessionId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT
            outer_cm.sessionId AS sessionId,
            MIN(outer_cm.timestamp) AS createdAt,
            MAX(outer_cm.timestamp) AS lastMessageAt,
            COUNT(*) AS messageCount,
            COALESCE((
                SELECT inner_cm.content
                FROM chat_messages AS inner_cm
                WHERE inner_cm.episodeId = outer_cm.episodeId
                  AND inner_cm.sessionId = outer_cm.sessionId
                ORDER BY inner_cm.timestamp DESC
                LIMIT 1
            ), '') AS preview
        FROM chat_messages AS outer_cm
        WHERE outer_cm.episodeId = :episodeId
        GROUP BY outer_cm.sessionId
        ORDER BY lastMessageAt DESC
        """
    )
    fun observeSessions(episodeId: String): Flow<List<ChatSessionSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE episodeId = :episodeId")
    suspend fun clearHistory(episodeId: String)

    @Query("SELECT sessionId FROM chat_messages WHERE episodeId = :episodeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSessionId(episodeId: String): String?
}
