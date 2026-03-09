package com.digestit.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.domain.model.Episode
import com.digestit.domain.model.Summary
import com.digestit.domain.repository.IEpisodeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
            _state.update { it.copy(isLoading = true, error = null) }
            repository.markEpisodeOpened(episodeId)
            val episodeDeferred = async { repository.getEpisode(episodeId) }
            val summaryDeferred = async { repository.getSummary(episodeId) }
            val episode = episodeDeferred.await()
            val summary = summaryDeferred.await()
            _state.update {
                it.copy(
                    isLoading = false,
                    episode = episode,
                    summary = summary,
                    error = if (summary == null) "摘要尚未生成" else null,
                )
            }
        }
    }

    fun selectTab(tab: SummaryTab) {
        _state.update { it.copy(selectedTab = tab) }
    }
}
