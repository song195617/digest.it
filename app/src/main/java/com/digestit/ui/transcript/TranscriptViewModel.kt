package com.digestit.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.domain.model.Transcript
import com.digestit.domain.model.TranscriptSegment
import com.digestit.domain.repository.IEpisodeRepository
import com.digestit.media.AudioPlayerManager
import com.digestit.media.AudioPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptState(
    val isLoading: Boolean = true,
    val transcript: Transcript? = null,
    val visibleSegments: List<TranscriptSegment> = emptyList(),
    val searchQuery: String = "",
    val searchMatchCount: Int = 0,
    val currentMatchPosition: Int = 0,
    val highlightedSegmentStartMs: Long? = null,
    val pendingScrollTargetStartMs: Long? = null,
    val error: String? = null
)

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val repository: IEpisodeRepository,
    private val prefs: UserPreferencesDataStore,
    private val audioPlayerManager: AudioPlayerManager,
) : ViewModel() {

    private data class LoadedAudioSource(
        val url: String,
        val title: String,
        val author: String
    )

    private val _state = MutableStateFlow(TranscriptState())
    val state: StateFlow<TranscriptState> = _state.asStateFlow()

    val playerState: StateFlow<AudioPlayerState> = audioPlayerManager.state
    private var loadedAudioSource: LoadedAudioSource? = null

    fun load(episodeId: String, initialTimestampMs: Long?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.markEpisodeOpened(episodeId)
            val transcript = repository.getTranscript(episodeId)
            val episode = repository.getEpisode(episodeId)
            val audioUrl = episode?.audioUrl?.let { path ->
                val base = prefs.backendUrl.first().trimEnd('/')
                "$base$path"
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    transcript = transcript,
                    visibleSegments = transcript?.segments ?: emptyList(),
                    error = if (transcript == null) "转录文本未找到" else null,
                )
            }
            if (audioUrl != null) {
                loadedAudioSource = LoadedAudioSource(audioUrl, episode.title, episode.author)
                if (initialTimestampMs != null) {
                    audioPlayerManager.playFrom(audioUrl, episode.title, episode.author, initialTimestampMs)
                } else {
                    audioPlayerManager.setAudioSource(audioUrl, episode.title, episode.author)
                }
            } else {
                loadedAudioSource = null
            }
            initialTimestampMs?.let { highlightTimestamp(it) }
        }
    }

    fun onSearchQueryChange(query: String) {
        val transcript = _state.value.transcript ?: return
        val visibleSegments = if (query.isBlank()) {
            transcript.segments
        } else {
            transcript.segments.filter { it.text.contains(query, ignoreCase = true) }
        }
        val firstMatchStartMs = visibleSegments.firstOrNull()?.startMs
        _state.update {
            it.copy(
                searchQuery = query,
                visibleSegments = visibleSegments,
                searchMatchCount = if (query.isBlank()) 0 else visibleSegments.size,
                currentMatchPosition = if (query.isBlank() || visibleSegments.isEmpty()) 0 else 1,
                highlightedSegmentStartMs = if (query.isBlank()) it.highlightedSegmentStartMs else firstMatchStartMs,
                pendingScrollTargetStartMs = if (query.isBlank()) null else firstMatchStartMs,
            )
        }
    }

    fun focusTimestamp(timestampMs: Long) {
        highlightTimestamp(timestampMs)
        val source = loadedAudioSource
        if (source != null) {
            viewModelScope.launch {
                audioPlayerManager.playFrom(source.url, source.title, source.author, timestampMs)
            }
        } else {
            audioPlayerManager.seekTo(timestampMs)
            audioPlayerManager.play()
        }
    }

    fun play() = audioPlayerManager.play()
    fun pause() = audioPlayerManager.pause()
    fun seekTo(positionMs: Long) = audioPlayerManager.seekTo(positionMs)
    fun skipForward() = audioPlayerManager.skipForward()
    fun skipBackward() = audioPlayerManager.skipBackward()
    fun setPlaybackSpeed(speed: Float) = audioPlayerManager.setPlaybackSpeed(speed)

    fun goToNextMatch() {
        val state = _state.value
        if (state.searchQuery.isBlank() || state.visibleSegments.isEmpty()) return
        val nextIndex = state.currentMatchPosition % state.visibleSegments.size
        val target = state.visibleSegments[nextIndex]
        _state.update {
            it.copy(
                currentMatchPosition = nextIndex + 1,
                highlightedSegmentStartMs = target.startMs,
                pendingScrollTargetStartMs = target.startMs,
            )
        }
    }

    fun goToPreviousMatch() {
        val state = _state.value
        if (state.searchQuery.isBlank() || state.visibleSegments.isEmpty()) return
        val currentIndex = (state.currentMatchPosition - 1).coerceAtLeast(0)
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else state.visibleSegments.lastIndex
        val target = state.visibleSegments[prevIndex]
        _state.update {
            it.copy(
                currentMatchPosition = prevIndex + 1,
                highlightedSegmentStartMs = target.startMs,
                pendingScrollTargetStartMs = target.startMs,
            )
        }
    }

    fun onPendingScrollHandled() {
        _state.update { it.copy(pendingScrollTargetStartMs = null) }
    }

    private fun highlightTimestamp(timestampMs: Long) {
        val transcript = _state.value.transcript ?: return
        val target = transcript.segments.firstOrNull { segment ->
            timestampMs in segment.startMs..segment.endMs
        } ?: transcript.segments.minByOrNull { kotlin.math.abs(it.startMs - timestampMs) }
        _state.update {
            it.copy(
                highlightedSegmentStartMs = target?.startMs,
                pendingScrollTargetStartMs = target?.startMs,
            )
        }
    }
}
