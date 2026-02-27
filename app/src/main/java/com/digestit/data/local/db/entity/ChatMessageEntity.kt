package com.digestit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.MessageRole
import java.time.Instant

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val episodeId: String,
    val role: String,
    val content: String,
    val timestamp: Long
) {
    fun toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = Instant.ofEpochMilli(timestamp)
    )

    companion object {
        fun fromDomain(msg: ChatMessage, episodeId: String) = ChatMessageEntity(
            id = msg.id,
            sessionId = msg.sessionId,
            episodeId = episodeId,
            role = msg.role.name,
            content = msg.content,
            timestamp = msg.timestamp.toEpochMilli()
        )
    }
}
