package com.digestit.data.remote.websocket

import com.digestit.data.remote.dto.ChatStreamChunk
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.MessageRole
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    fun streamChatResponse(
        baseUrl: String,
        episodeId: String,
        sessionId: String?,
        userMessage: String
    ): Flow<ChatMessage> = callbackFlow {
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/v1/ws/chat/${sessionId ?: "new"}"

        // AI provider headers are added by the OkHttp interceptor in NetworkModule
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        var accumulatedContent = ""
        var resolvedSessionId = sessionId ?: UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = gson.toJson(
                    mapOf(
                        "episode_id" to episodeId,
                        "message" to userMessage
                    )
                )
                webSocket.send(payload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val chunk = gson.fromJson(text, ChatStreamChunk::class.java)
                if (chunk.sessionId != null) resolvedSessionId = chunk.sessionId
                accumulatedContent += chunk.delta
                trySend(
                    ChatMessage(
                        id = messageId,
                        sessionId = resolvedSessionId,
                        role = MessageRole.ASSISTANT,
                        content = accumulatedContent,
                        timestamp = Instant.now(),
                        isStreaming = !chunk.done,
                        referencedTimestamps = chunk.referencedTimestamps ?: emptyList()
                    )
                )
                if (chunk.done) {
                    close()
                    webSocket.close(1000, "Done")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        awaitClose { ws.cancel() }
    }
}
