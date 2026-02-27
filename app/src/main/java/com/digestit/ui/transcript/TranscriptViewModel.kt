package com.digestit.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.domain.model.Transcript
import com.digestit.domain.model.TranscriptSegment
import com.digestit.domain.repository.IEpisodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptState(
    val isLoading: Boolean = true,
    val transcript: Transcript? = null,
    val filteredSegments: List<TranscriptSegment> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
)

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val repository: IEpisodeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TranscriptState())
    val state: StateFlow<TranscriptState> = _state.asStateFlow()

    fun load(episodeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val transcript = repository.getTranscript(episodeId)
            _state.update {
                it.copy(
                    isLoading = false,
                    transcript = transcript,
                    filteredSegments = transcript?.segments ?: emptyList()
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { state ->
            val filtered = if (query.isBlank()) {
                state.transcript?.segments ?: emptyList()
            } else {
                state.transcript?.segments?.filter { it.text.contains(query, ignoreCase = true) }
                    ?: emptyList()
            }
            state.copy(searchQuery = query, filteredSegments = filtered)
        }
    }
}
