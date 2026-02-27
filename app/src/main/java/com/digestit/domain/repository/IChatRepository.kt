package com.digestit.domain.repository

import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getChatSession(episodeId: String): Flow<ChatSession?>
    suspend fun sendMessage(episodeId: String, sessionId: String?, userMessage: String): Flow<ChatMessage>
    suspend fun clearHistory(episodeId: String)
}
