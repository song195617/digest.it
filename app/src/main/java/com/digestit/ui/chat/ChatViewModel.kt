package com.digestit.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSessionInfo
import com.digestit.domain.repository.IChatRepository
import com.digestit.domain.repository.IEpisodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val sessions: List<ChatSessionInfo> = emptyList(),
    val errorMessage: String? = null,
    val retryMessageText: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: IChatRepository,
    private val episodeRepository: IEpisodeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentEpisodeId: String? = null
    private var sessionsJob: Job? = null
    private var sessionMessagesJob: Job? = null
    private var observedSessionId: String? = null

    fun load(episodeId: String) {
        if (currentEpisodeId == episodeId && sessionsJob != null) return
        currentEpisodeId = episodeId
        observedSessionId = null
        sessionsJob?.cancel()
        sessionMessagesJob?.cancel()
        _state.value = ChatState()

        viewModelScope.launch { episodeRepository.markEpisodeOpened(episodeId) }

        sessionsJob = viewModelScope.launch {
            chatRepository.listSessions(episodeId).collect { sessions ->
                _state.update { current ->
                    val stillExists = sessions.any { it.sessionId == current.sessionId }
                    val nextSessionId = when {
                        current.sessionId == null && sessions.isNotEmpty() -> sessions.first().sessionId
                        !stillExists && sessions.isNotEmpty() -> sessions.first().sessionId
                        else -> current.sessionId
                    }
                    current.copy(sessions = sessions, sessionId = nextSessionId)
                }
                observeSessionIfNeeded(_state.value.sessionId)
            }
        }

        observeSessionIfNeeded(null)
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun onErrorConsumed() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun selectSession(sessionId: String) {
        _state.update { it.copy(sessionId = sessionId, errorMessage = null, retryMessageText = null) }
        observeSessionIfNeeded(sessionId)
    }

    fun startNewSession() {
        val episodeId = currentEpisodeId ?: return
        viewModelScope.launch {
            val sessionId = chatRepository.startNewSession(episodeId)
            _state.update {
                it.copy(
                    sessionId = sessionId,
                    messages = emptyList(),
                    errorMessage = null,
                    retryMessageText = null,
                )
            }
            observeSessionIfNeeded(sessionId)
        }
    }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return
        sendText(text, clearInput = true)
    }

    fun sendPresetMessage(text: String) {
        sendText(text.trim(), clearInput = false)
    }

    fun retryLastMessage() {
        val text = _state.value.retryMessageText ?: return
        sendText(text, clearInput = false)
    }

    private fun sendText(text: String, clearInput: Boolean) {
        val episodeId = currentEpisodeId ?: return
        if (text.isBlank() || _state.value.isLoading) return

        _state.update {
            it.copy(
                inputText = if (clearInput) "" else it.inputText,
                isLoading = true,
                errorMessage = null,
                retryMessageText = null,
            )
        }

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
                _state.update { it.copy(isLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "AI 对话失败，请稍后重试。",
                        retryMessageText = text,
                    )
                }
            }
        }
    }

    private fun observeSessionIfNeeded(sessionId: String?) {
        val episodeId = currentEpisodeId ?: return
        if (sessionId == observedSessionId && sessionMessagesJob != null) return
        observedSessionId = sessionId
        sessionMessagesJob?.cancel()
        sessionMessagesJob = viewModelScope.launch {
            chatRepository.getChatSession(episodeId, sessionId).collect { session ->
                if (session != null) {
                    _state.update {
                        it.copy(messages = session.messages, sessionId = session.id)
                    }
                } else {
                    _state.update {
                        it.copy(messages = emptyList(), sessionId = sessionId)
                    }
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
