package com.digestit.data.repository

import com.digestit.data.local.db.dao.ChatDao
import com.digestit.data.local.db.entity.ChatMessageEntity
import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.data.remote.websocket.ChatWebSocketManager
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSession
import com.digestit.domain.model.MessageRole
import com.digestit.domain.repository.IChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val webSocketManager: ChatWebSocketManager,
    private val prefs: UserPreferencesDataStore
) : IChatRepository {

    override fun getChatSession(episodeId: String): Flow<ChatSession?> =
        chatDao.getMessages(episodeId).map { entities ->
            if (entities.isEmpty()) null
            else {
                val sessionId = entities.first().sessionId
                ChatSession(
                    id = sessionId,
                    episodeId = episodeId,
                    createdAt = Instant.ofEpochMilli(entities.first().timestamp),
                    messages = entities.map { it.toDomain() }
                )
            }
        }

    override suspend fun sendMessage(
        episodeId: String,
        sessionId: String?,
        userMessage: String
    ): Flow<ChatMessage> = flow {
        val baseUrl = prefs.backendUrl.first()
        val claudeKey = prefs.claudeApiKey.first()
        val resolvedSessionId = sessionId ?: chatDao.getSessionId(episodeId)

        // Save user message to local DB
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = resolvedSessionId ?: UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = userMessage,
            timestamp = Instant.now()
        )
        chatDao.insertMessage(ChatMessageEntity.fromDomain(userMsg, episodeId))
        emit(userMsg)

        // Stream assistant response via WebSocket
        var lastAssistantMsg: ChatMessage? = null
        webSocketManager.streamChatResponse(
            baseUrl = baseUrl,
            apiKey = claudeKey,
            episodeId = episodeId,
            sessionId = resolvedSessionId,
            userMessage = userMessage
        ).collect { chunk ->
            lastAssistantMsg = chunk
            emit(chunk)
        }

        // Persist final assistant message (non-streaming)
        lastAssistantMsg?.let { final ->
            if (!final.isStreaming) {
                chatDao.insertMessage(ChatMessageEntity.fromDomain(final, episodeId))
            }
        }
    }

    override suspend fun clearHistory(episodeId: String) {
        chatDao.clearHistory(episodeId)
    }
}
