package com.digestit.data.repository

import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.data.local.db.dao.ChatDao
import com.digestit.data.local.db.entity.ChatMessageEntity
import com.digestit.data.remote.websocket.ChatWebSocketManager
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSession
import com.digestit.domain.model.ChatSessionInfo
import com.digestit.domain.model.MessageRole
import com.digestit.domain.repository.IChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val webSocketManager: ChatWebSocketManager,
    private val prefs: UserPreferencesDataStore
) : IChatRepository {

    override fun getChatSession(episodeId: String, sessionId: String?): Flow<ChatSession?> {
        val sessionFlow = if (sessionId.isNullOrBlank()) {
            chatDao.observeLatestSessionId(episodeId)
        } else {
            flowOf(sessionId)
        }
        return sessionFlow.flatMapLatest { resolvedSessionId ->
            if (resolvedSessionId.isNullOrBlank()) {
                flowOf(null)
            } else {
                chatDao.getMessages(episodeId, resolvedSessionId).map { entities ->
                    if (entities.isEmpty()) {
                        null
                    } else {
                        ChatSession(
                            id = resolvedSessionId,
                            episodeId = episodeId,
                            createdAt = Instant.ofEpochMilli(entities.first().timestamp),
                            messages = entities.map { it.toDomain() }
                        )
                    }
                }
            }
        }
    }

    override fun listSessions(episodeId: String): Flow<List<ChatSessionInfo>> =
        chatDao.observeSessions(episodeId).map { rows ->
            rows.map { row ->
                ChatSessionInfo(
                    sessionId = row.sessionId,
                    createdAt = Instant.ofEpochMilli(row.createdAt),
                    messageCount = row.messageCount,
                    previewText = row.preview,
                )
            }
        }

    override suspend fun startNewSession(episodeId: String): String = UUID.randomUUID().toString()

    override suspend fun sendMessage(
        episodeId: String,
        sessionId: String?,
        userMessage: String
    ): Flow<ChatMessage> = flow {
        val baseUrl = prefs.backendUrl.first()
        val resolvedSessionId = sessionId ?: chatDao.getLatestSessionId(episodeId) ?: UUID.randomUUID().toString()

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = resolvedSessionId,
            role = MessageRole.USER,
            content = userMessage,
            timestamp = Instant.now()
        )
        chatDao.insertMessage(ChatMessageEntity.fromDomain(userMsg, episodeId))
        emit(userMsg)

        var lastAssistantMsg: ChatMessage? = null
        try {
            webSocketManager.streamChatResponse(
                baseUrl = baseUrl,
                episodeId = episodeId,
                sessionId = resolvedSessionId,
                userMessage = userMessage
            ).collect { chunk ->
                lastAssistantMsg = chunk
                emit(chunk)
            }
        } catch (error: Throwable) {
            throw IllegalStateException(error.message ?: "AI 对话失败")
        }

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
