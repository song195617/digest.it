package com.digestit.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.repository.IChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val sessionId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: IChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentEpisodeId: String? = null

    fun load(episodeId: String) {
        currentEpisodeId = episodeId
        viewModelScope.launch {
            chatRepository.getChatSession(episodeId).collect { session ->
                if (session != null) {
                    _state.update {
                        it.copy(messages = session.messages, sessionId = session.id)
                    }
                } else {
                    _state.update {
                        it.copy(messages = emptyList(), sessionId = null)
                    }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun onErrorConsumed() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun sendMessage() {
        val episodeId = currentEpisodeId ?: return
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        _state.update { it.copy(inputText = "", isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    episodeId = episodeId,
                    sessionId = _state.value.sessionId,
                    userMessage = text
                ).collect { message ->
                    _state.update { state ->
                        val existingIndex = state.messages.indexOfFirst { it.id == message.id }
                        val newMessages = if (existingIndex >= 0) {
                            state.messages.toMutableList().apply { set(existingIndex, message) }
                        } else {
                            state.messages + message
                        }
                        state.copy(
                            messages = newMessages,
                            isLoading = message.isStreaming,
                            sessionId = message.sessionId,
                            errorMessage = null,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "AI 对话失败，请稍后重试。",
                    )
                }
            }
        }
    }

    val suggestedQuestions = listOf(
        "这期内容的核心观点是什么？",
        "用3句话帮我总结",
        "主讲人对主要话题的看法是什么？",
        "有哪些值得关注的数据或案例？"
    )
}
