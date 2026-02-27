package com.digestit.domain.usecase

import com.digestit.domain.model.ChatMessage
import com.digestit.domain.repository.IChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SendChatMessageUseCase @Inject constructor(
    private val repository: IChatRepository
) {
    operator fun invoke(
        episodeId: String,
        sessionId: String?,
        message: String
    ): Flow<ChatMessage> = kotlinx.coroutines.flow.flow {
        repository.sendMessage(episodeId, sessionId, message).collect { emit(it) }
    }
}
