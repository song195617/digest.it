package com.digestit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.digestit.domain.model.Episode
import com.digestit.domain.usecase.GetEpisodesUseCase
import com.digestit.domain.usecase.SubmitUrlUseCase
import com.digestit.worker.JobPollingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val episodes: List<Episode> = emptyList(),
    val isSubmitting: Boolean = false,
    val urlInput: String = "",
    val error: String? = null
)

sealed class HomeEffect {
    data class NavigateToProcessing(val jobId: String) : HomeEffect()
    data class NavigateToSummary(val episodeId: String) : HomeEffect()
    data class ShowError(val message: String) : HomeEffect()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getEpisodes: GetEpisodesUseCase,
    private val submitUrl: SubmitUrlUseCase,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _effects = MutableStateFlow<HomeEffect?>(null)
    val effects: StateFlow<HomeEffect?> = _effects.asStateFlow()

    init {
        viewModelScope.launch {
            getEpisodes().collect { episodes ->
                _state.update { it.copy(episodes = episodes) }
            }
        }
    }

    fun onUrlInputChange(url: String) {
        _state.update { it.copy(urlInput = url, error = null) }
    }

    fun onSubmitUrl() {
        val url = _state.value.urlInput.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = submitUrl(url)) {
                is SubmitUrlUseCase.Result.Success -> {
                    val jobId = result.job.jobId
                    enqueuePollingWorker(jobId)
                    _state.update { it.copy(isSubmitting = false, urlInput = "") }
                    _effects.value = HomeEffect.NavigateToProcessing(jobId)
                }
                is SubmitUrlUseCase.Result.UnsupportedPlatform -> {
                    _state.update {
                        it.copy(isSubmitting = false, error = "不支持该链接，仅支持小宇宙和哔哩哔哩")
                    }
                }
                is SubmitUrlUseCase.Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
                }
            }
        }
    }

    fun onEpisodeClick(episode: Episode) {
        _effects.value = HomeEffect.NavigateToSummary(episode.id)
    }

    fun onEffectConsumed() {
        _effects.value = null
    }

    private fun enqueuePollingWorker(jobId: String) {
        val request = OneTimeWorkRequestBuilder<JobPollingWorker>()
            .setInputData(workDataOf(JobPollingWorker.KEY_JOB_ID to jobId))
            .build()
        workManager.enqueue(request)
    }
}
