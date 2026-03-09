package com.digestit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.MessageRole
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val episodeId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val referencedTimestampsJson: String = "[]",
) {
    fun toDomain(): ChatMessage {
        val type = object : TypeToken<List<Long>>() {}.type
        val referencedTimestamps: List<Long> = Gson().fromJson(referencedTimestampsJson, type) ?: emptyList()
        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = MessageRole.valueOf(role),
            content = content,
            timestamp = Instant.ofEpochMilli(timestamp),
            referencedTimestamps = referencedTimestamps,
        )
    }

    companion object {
        fun fromDomain(msg: ChatMessage, episodeId: String) = ChatMessageEntity(
            id = msg.id,
            sessionId = msg.sessionId,
            episodeId = episodeId,
            role = msg.role.name,
            content = msg.content,
            timestamp = msg.timestamp.toEpochMilli(),
            referencedTimestampsJson = Gson().toJson(msg.referencedTimestamps),
        )
    }
}
