package com.digestit.data.remote.websocket

import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.MessageRole
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
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
                val payload = JsonParser.parseString(text).asJsonObject
                val error = payload.get("error")?.takeUnless { it.isJsonNull }?.asString
                if (!error.isNullOrBlank()) {
                    close(IOException(error))
                    webSocket.close(1011, "Server error")
                    return
                }

                val delta = payload.get("delta")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val done = payload.get("done")?.takeUnless { it.isJsonNull }?.asBoolean ?: false
                val serverSessionId = payload.get("session_id")?.takeUnless { it.isJsonNull }?.asString
                if (!serverSessionId.isNullOrBlank()) {
                    resolvedSessionId = serverSessionId
                }
                accumulatedContent += delta
                val referencedTimestamps = payload.getAsJsonArray("referenced_timestamps")
                    ?.mapNotNull { element -> if (element.isJsonNull) null else element.asLong }
                    ?: emptyList()

                trySend(
                    ChatMessage(
                        id = messageId,
                        sessionId = resolvedSessionId,
                        role = MessageRole.ASSISTANT,
                        content = accumulatedContent,
                        timestamp = Instant.now(),
                        isStreaming = !done,
                        referencedTimestamps = referencedTimestamps
                    )
                )
                if (done) {
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
