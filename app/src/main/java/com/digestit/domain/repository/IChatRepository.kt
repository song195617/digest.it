package com.digestit.domain.repository

import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSession
import com.digestit.domain.model.ChatSessionInfo
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    fun getChatSession(episodeId: String, sessionId: String? = null): Flow<ChatSession?>
    fun listSessions(episodeId: String): Flow<List<ChatSessionInfo>>
    suspend fun startNewSession(episodeId: String): String
    suspend fun sendMessage(episodeId: String, sessionId: String?, userMessage: String): Flow<ChatMessage>
    suspend fun clearHistory(episodeId: String)
}
