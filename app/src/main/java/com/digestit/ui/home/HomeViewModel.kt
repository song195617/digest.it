package com.digestit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.model.SearchResult
import com.digestit.domain.model.SearchSourceType
import com.digestit.domain.repository.IEpisodeRepository
import com.digestit.domain.usecase.GetEpisodesUseCase
import com.digestit.domain.usecase.SubmitUrlUseCase
import com.digestit.worker.JobPollingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeFilter {
    ALL,
    FAVORITES,
    RECENT
}

data class HomeState(
    val episodes: List<Episode> = emptyList(),
    val processingEpisodes: List<Episode> = emptyList(),
    val completedEpisodes: List<Episode> = emptyList(),
    val failedEpisodes: List<Episode> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val selectedFilter: HomeFilter = HomeFilter.ALL,
    val isSubmitting: Boolean = false,
    val urlInput: String = "",
    val error: String? = null
)

sealed class HomeEffect {
    data class NavigateToProcessing(val jobId: String) : HomeEffect()
    data class NavigateToSummary(val episodeId: String) : HomeEffect()
    data class NavigateToTranscript(val episodeId: String, val timestampMs: Long?) : HomeEffect()
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

    private var latestEpisodes: List<Episode> = emptyList()
    private var latestSearchResults: List<SearchResult> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            getEpisodes().collect { episodes ->
                latestEpisodes = episodes
                syncContent()
            }
        }
        viewModelScope.launch { repository.refreshEpisodes() }
    }

    fun onUrlInputChange(url: String) {
        _state.update { it.copy(urlInput = url, error = null) }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            latestSearchResults = emptyList()
            syncContent()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            latestSearchResults = repository.searchContent(query)
            syncContent()
        }
    }

    fun onFilterChange(filter: HomeFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        syncContent()
        if (_state.value.searchQuery.isNotBlank()) {
            onSearchQueryChange(_state.value.searchQuery)
        }
    }

    fun onSubmitUrl() {
        val url = _state.value.urlInput.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = submitUrl(url)) {
                is SubmitUrlUseCase.Result.Success -> {
                    val job = result.job
                    val episodeId = job.episodeId
                    _state.update { it.copy(isSubmitting = false, urlInput = "") }
                    when (job.status) {
                        ProcessingStatus.COMPLETED,
                        ProcessingStatus.FAILED -> {
                            if (episodeId != null) {
                                _effects.value = HomeEffect.NavigateToSummary(episodeId)
                            } else {
                                _effects.value = HomeEffect.ShowError(job.errorMessage ?: "任务状态异常")
                            }
                        }
                        else -> {
                            enqueuePollingWorker(job.jobId, episodeId.orEmpty())
                            _effects.value = HomeEffect.NavigateToProcessing(job.jobId)
                        }
                    }
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
            episode.processingStatus == ProcessingStatus.FAILED
        ) {
            _effects.value = HomeEffect.NavigateToSummary(episode.id)
            return
        }
        viewModelScope.launch {
            val works = workManager.getWorkInfosByTagFlow("episode:${episode.id}").first()
            val active = works.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
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

    fun onSearchResultClick(result: SearchResult) {
        _effects.value = when (result.sourceType) {
            SearchSourceType.TRANSCRIPT -> HomeEffect.NavigateToTranscript(result.episodeId, result.timestampMs)
            else -> HomeEffect.NavigateToSummary(result.episodeId)
        }
    }

    fun toggleFavorite(episode: Episode) {
        viewModelScope.launch {
            repository.setFavorite(episode.id, !episode.isFavorite)
        }
    }

    fun retryEpisode(episodeId: String) {
        viewModelScope.launch {
            runCatching { repository.retryEpisode(episodeId) }
                .onSuccess { job ->
                    enqueuePollingWorker(job.jobId, episodeId)
                    _effects.value = HomeEffect.NavigateToProcessing(job.jobId)
                }
                .onFailure { error ->
                    _effects.value = HomeEffect.ShowError(error.message ?: "重试失败")
                }
        }
    }

    fun onEffectConsumed() {
        _effects.value = null
    }

    fun deleteEpisode(episodeId: String) {
        viewModelScope.launch {
            try {
                repository.deleteEpisode(episodeId)
            } catch (e: Exception) {
                _effects.value = HomeEffect.ShowError(e.message ?: "删除失败")
            }
        }
    }

    private fun enqueuePollingWorker(jobId: String, episodeId: String) {
        val request = OneTimeWorkRequestBuilder<JobPollingWorker>()
            .setInputData(workDataOf(JobPollingWorker.KEY_JOB_ID to jobId))
            .addTag(jobId)
            .addTag("episode:$episodeId")
            .build()
        workManager.enqueue(request)
    }

    private fun syncContent() {
        val filteredEpisodes = applyFilter(latestEpisodes, _state.value.selectedFilter)
        val processing = filteredEpisodes.filter {
            it.processingStatus !in setOf(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED)
        }
        val completed = filteredEpisodes.filter { it.processingStatus == ProcessingStatus.COMPLETED }
        val failed = filteredEpisodes.filter { it.processingStatus == ProcessingStatus.FAILED }
        val filteredSearch = latestSearchResults.filter { result ->
            filteredEpisodes.any { it.id == result.episodeId }
        }
        _state.update {
            it.copy(
                episodes = filteredEpisodes,
                processingEpisodes = processing,
                completedEpisodes = completed,
                failedEpisodes = failed,
                searchResults = filteredSearch,
            )
        }
    }

    private fun applyFilter(episodes: List<Episode>, filter: HomeFilter): List<Episode> {
        val base = when (filter) {
            HomeFilter.ALL -> episodes
            HomeFilter.FAVORITES -> episodes.filter { it.isFavorite }
            HomeFilter.RECENT -> episodes.sortedByDescending { it.lastOpenedAt ?: it.createdAt }
        }
        return when (filter) {
            HomeFilter.RECENT -> base
            else -> base.sortedByDescending { it.createdAt }
        }
    }
}
