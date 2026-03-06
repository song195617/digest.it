package com.digestit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.repository.IEpisodeRepository
import com.digestit.domain.usecase.GetEpisodesUseCase
import com.digestit.domain.usecase.SubmitUrlUseCase
import com.digestit.worker.JobPollingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val workManager: WorkManager,
    private val repository: IEpisodeRepository
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
        viewModelScope.launch { repository.refreshEpisodes() }
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
                    val episodeId = result.job.episodeId ?: ""
                    enqueuePollingWorker(jobId, episodeId)
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
        if (episode.processingStatus == ProcessingStatus.COMPLETED ||
            episode.processingStatus == ProcessingStatus.FAILED) {
            _effects.value = HomeEffect.NavigateToSummary(episode.id)
            return
        }
        // For in-progress episodes, find the active WorkManager job and re-attach
        viewModelScope.launch {
            val works = workManager.getWorkInfosByTagFlow("episode:${episode.id}").first()
            val active = works.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            // WorkInfo doesn't expose inputData; read jobId from the tag we added during enqueue.
            // The backend jobId is a UUID (36 chars, 4 dashes) — distinct from "episode:..." tags.
            val jobId = active?.tags?.firstOrNull { tag ->
                tag.length == 36 && tag.count { it == '-' } == 4
            }
            _effects.value = if (jobId != null) {
                HomeEffect.NavigateToProcessing(jobId)
            } else {
                HomeEffect.NavigateToSummary(episode.id)
            }
        }
    }

    fun onEffectConsumed() {
        _effects.value = null
    }

    private fun enqueuePollingWorker(jobId: String, episodeId: String) {
        val request = OneTimeWorkRequestBuilder<JobPollingWorker>()
            .setInputData(workDataOf(JobPollingWorker.KEY_JOB_ID to jobId))
            .addTag(jobId)                    // ProcessingViewModel observes by this tag
            .addTag("episode:$episodeId")     // HomeViewModel looks up by episode
            .build()
        workManager.enqueue(request)
    }
}
