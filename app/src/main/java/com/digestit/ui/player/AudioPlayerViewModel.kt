package com.digestit.ui.player

import androidx.lifecycle.ViewModel
import com.digestit.media.AudioPlayerManager
import com.digestit.media.AudioPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val audioPlayerManager: AudioPlayerManager
) : ViewModel() {
    val playerState: StateFlow<AudioPlayerState> = audioPlayerManager.state

    fun play() = audioPlayerManager.play()
    fun pause() = audioPlayerManager.pause()
    fun seekTo(positionMs: Long) = audioPlayerManager.seekTo(positionMs)
    fun skipForward() = audioPlayerManager.skipForward()
    fun skipBackward() = audioPlayerManager.skipBackward()
    fun setPlaybackSpeed(speed: Float) = audioPlayerManager.setPlaybackSpeed(speed)
}
