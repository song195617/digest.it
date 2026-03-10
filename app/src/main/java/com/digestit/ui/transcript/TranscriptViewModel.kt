package com.digestit.ui.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.data.remote.normalizeAudioPath
import com.digestit.domain.model.Transcript
import com.digestit.domain.model.TranscriptSegment
import com.digestit.domain.repository.IEpisodeRepository
import com.digestit.media.AudioPlayerManager
import com.digestit.media.AudioPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptState(
    val isLoading: Boolean = true,
    val transcript: Transcript? = null,
    val paragraphs: List<TranscriptParagraphUiModel> = emptyList(),
    val searchQuery: String = "",
    val searchMatchCount: Int = 0,
    val currentMatchPosition: Int = 0,
    val selectedSentenceStartMs: Long? = null,
    val playbackSentenceStartMs: Long? = null,
    val pendingScrollTargetParagraphStartMs: Long? = null,
    val error: String? = null,
) {
    val activeSentenceStartMs: Long?
        get() = playbackSentenceStartMs ?: selectedSentenceStartMs
}

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val repository: IEpisodeRepository,
    private val prefs: UserPreferencesDataStore,
    private val audioPlayerManager: AudioPlayerManager,
) : ViewModel() {

    private data class LoadedAudioSource(
        val episodeId: String,
        val url: String,
        val title: String,
        val author: String
    )

    private val _state = MutableStateFlow(TranscriptState())
    val state: StateFlow<TranscriptState> = _state.asStateFlow()

    val playerState: StateFlow<AudioPlayerState> = audioPlayerManager.state
    private var loadedAudioSource: LoadedAudioSource? = null
    private var currentEpisodeId: String? = null
    private var transcriptSegments: List<TranscriptSegment> = emptyList()
    private var transcriptParagraphs: List<TranscriptParagraphUiModel> = emptyList()
    private var paragraphStartBySentenceStartMs: Map<Long, Long> = emptyMap()
    private var searchMatchSentenceStartMs: List<Long> = emptyList()
    private var lastPlaybackParagraphStartMs: Long? = null

    init {
        viewModelScope.launch {
            playerState.collectLatest(::syncPlaybackHighlight)
        }
    }

    fun load(episodeId: String, initialTimestampMs: Long?) {
        viewModelScope.launch {
            currentEpisodeId = episodeId
            loadedAudioSource = null
            transcriptSegments = emptyList()
            transcriptParagraphs = emptyList()
            paragraphStartBySentenceStartMs = emptyMap()
            searchMatchSentenceStartMs = emptyList()
            lastPlaybackParagraphStartMs = null
            _state.update {
                it.copy(
                    isLoading = true,
                    transcript = null,
                    paragraphs = emptyList(),
                    searchQuery = "",
                    searchMatchCount = 0,
                    currentMatchPosition = 0,
                    selectedSentenceStartMs = null,
                    playbackSentenceStartMs = null,
                    pendingScrollTargetParagraphStartMs = null,
                    error = null,
                )
            }
            repository.markEpisodeOpened(episodeId)
            val transcript = repository.getTranscript(episodeId)
            val episode = repository.getEpisode(episodeId)
            val audioUrl = normalizeAudioPath(episode?.audioUrl)?.let { path ->
                val base = prefs.backendUrl.first().trimEnd('/')
                "$base$path"
            }
            val episodeTitle = episode?.title.orEmpty()
            val episodeAuthor = episode?.author.orEmpty()
            transcriptSegments = transcript?.segments ?: emptyList()
            transcriptParagraphs = TranscriptParagraphFormatter.buildParagraphs(transcriptSegments)
            paragraphStartBySentenceStartMs = TranscriptParagraphFormatter.buildSentenceParagraphMap(transcriptParagraphs)
            _state.update {
                it.copy(
                    isLoading = false,
                    transcript = transcript,
                    paragraphs = transcriptParagraphs,
                    error = if (transcript == null) "转录文本未找到" else null,
                )
            }
            if (audioUrl != null) {
                loadedAudioSource = LoadedAudioSource(episodeId, audioUrl, episodeTitle, episodeAuthor)
                if (initialTimestampMs != null) {
                    audioPlayerManager.playFrom(
                        episodeId,
                        audioUrl,
                        episodeTitle,
                        episodeAuthor,
                        initialTimestampMs
                    )
                } else {
                    audioPlayerManager.setAudioSource(episodeId, audioUrl, episodeTitle, episodeAuthor)
                }
            } else {
                loadedAudioSource = null
            }
            initialTimestampMs?.let { focusSentenceAt(it) }
        }
    }

    fun onSearchQueryChange(query: String) {
        if (_state.value.transcript == null) return
        searchMatchSentenceStartMs = if (query.isBlank()) {
            emptyList()
        } else {
            transcriptSegments
                .filter { it.text.contains(query, ignoreCase = true) }
                .map { it.startMs }
        }
        val firstMatchStartMs = searchMatchSentenceStartMs.firstOrNull()
        _state.update {
            it.copy(
                searchQuery = query,
                searchMatchCount = searchMatchSentenceStartMs.size,
                currentMatchPosition = if (query.isBlank() || firstMatchStartMs == null) 0 else 1,
                selectedSentenceStartMs = if (query.isBlank()) it.selectedSentenceStartMs else firstMatchStartMs,
                pendingScrollTargetParagraphStartMs = if (query.isBlank()) {
                    null
                } else {
                    firstMatchStartMs?.let(paragraphStartBySentenceStartMs::get)
                },
            )
        }
    }

    fun focusTimestamp(timestampMs: Long) {
        focusSentenceAt(timestampMs)
        val source = loadedAudioSource
        if (source != null) {
            viewModelScope.launch {
                audioPlayerManager.playFrom(
                    source.episodeId,
                    source.url,
                    source.title,
                    source.author,
                    timestampMs
                )
            }
        } else if (playerState.value.currentEpisodeId == currentEpisodeId) {
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
        if (state.searchQuery.isBlank() || searchMatchSentenceStartMs.isEmpty()) return
        val nextIndex = state.currentMatchPosition % searchMatchSentenceStartMs.size
        val targetStartMs = searchMatchSentenceStartMs[nextIndex]
        _state.update {
            it.copy(
                currentMatchPosition = nextIndex + 1,
                selectedSentenceStartMs = targetStartMs,
                pendingScrollTargetParagraphStartMs = paragraphStartBySentenceStartMs[targetStartMs],
            )
        }
    }

    fun goToPreviousMatch() {
        val state = _state.value
        if (state.searchQuery.isBlank() || searchMatchSentenceStartMs.isEmpty()) return
        val currentIndex = (state.currentMatchPosition - 1).coerceAtLeast(0)
        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else searchMatchSentenceStartMs.lastIndex
        val targetStartMs = searchMatchSentenceStartMs[prevIndex]
        _state.update {
            it.copy(
                currentMatchPosition = prevIndex + 1,
                selectedSentenceStartMs = targetStartMs,
                pendingScrollTargetParagraphStartMs = paragraphStartBySentenceStartMs[targetStartMs],
            )
        }
    }

    fun onPendingScrollHandled() {
        _state.update { it.copy(pendingScrollTargetParagraphStartMs = null) }
    }

    private fun focusSentenceAt(timestampMs: Long) {
        val target = TranscriptParagraphFormatter.findSentenceForTimestamp(transcriptSegments, timestampMs) ?: return
        _state.update {
            it.copy(
                selectedSentenceStartMs = target.startMs,
                pendingScrollTargetParagraphStartMs = paragraphStartBySentenceStartMs[target.startMs],
            )
        }
    }

    private fun syncPlaybackHighlight(playerState: AudioPlayerState) {
        val episodeId = currentEpisodeId
        if (
            episodeId == null ||
            playerState.currentEpisodeId != episodeId ||
            transcriptSegments.isEmpty()
        ) {
            if (_state.value.playbackSentenceStartMs != null) {
                lastPlaybackParagraphStartMs = null
                _state.update { it.copy(playbackSentenceStartMs = null) }
            }
            return
        }

        val targetSentence = TranscriptParagraphFormatter.findSentenceForTimestamp(
            transcriptSegments,
            playerState.positionMs
        )
        val targetStartMs = targetSentence?.startMs
        if (targetStartMs == _state.value.playbackSentenceStartMs) return

        val paragraphStartMs = targetStartMs?.let(paragraphStartBySentenceStartMs::get)
        val shouldScroll = paragraphStartMs != null && paragraphStartMs != lastPlaybackParagraphStartMs
        lastPlaybackParagraphStartMs = paragraphStartMs
        _state.update {
            it.copy(
                playbackSentenceStartMs = targetStartMs,
                pendingScrollTargetParagraphStartMs = if (shouldScroll) {
                    paragraphStartMs
                } else {
                    it.pendingScrollTargetParagraphStartMs
                },
            )
        }
    }
}
