package com.digestit.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.domain.model.Episode
import com.digestit.domain.model.Summary
import com.digestit.domain.repository.IEpisodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SummaryTab { KEY_POINTS, FULL_SUMMARY, HIGHLIGHTS }

data class SummaryState(
    val isLoading: Boolean = true,
    val episode: Episode? = null,
    val summary: Summary? = null,
    val selectedTab: SummaryTab = SummaryTab.KEY_POINTS,
    val error: String? = null
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: IEpisodeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryState())
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    fun load(episodeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val episode = repository.getEpisode(episodeId)
            val summary = repository.getSummary(episodeId)
            _state.update { it.copy(isLoading = false, episode = episode, summary = summary) }
        }
    }

    fun selectTab(tab: SummaryTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
}
