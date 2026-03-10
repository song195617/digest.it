package com.digestit.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AudioPlayerState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val currentUrl: String? = null,
    val episodeTitle: String = "",
    val author: String = ""
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollingJob: Job? = null

    private data class PendingSource(val url: String, val title: String, val author: String)
    @Volatile private var pendingSource: PendingSource? = null

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlayerService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val ctrl = controllerFuture?.get() ?: return@addListener
            controller = ctrl
            ctrl.addListener(playerListener)
            _state.update { it.copy(isConnected = true) }
            startPolling()
            pendingSource?.let { pending ->
                pendingSource = null
                applySource(ctrl, pending.url, pending.title, pending.author)
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val dur = controller?.duration ?: 0L
                if (dur > 0L) {
                    _state.update { it.copy(durationMs = dur) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                val ctrl = controller
                if (ctrl != null) {
                    _state.update { it.copy(positionMs = ctrl.currentPosition) }
                }
                delay(200)
            }
        }
    }

    suspend fun setAudioSource(url: String, title: String, author: String) {
        withContext(Dispatchers.Main) {
            if (_state.value.currentUrl == url) return@withContext
            val ctrl = controller
            if (ctrl == null) {
                pendingSource = PendingSource(url, title, author)
                _state.update { it.copy(episodeTitle = title, author = author, currentUrl = url) }
                return@withContext
            }
            applySource(ctrl, url, title, author)
        }
    }

    private fun applySource(ctrl: MediaController, url: String, title: String, author: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(author)
                    .build()
            )
            .build()
        ctrl.setMediaItem(mediaItem)
        ctrl.prepare()
        _state.update {
            it.copy(
                currentUrl = url,
                episodeTitle = title,
                author = author,
                durationMs = 0L,
                positionMs = 0L
            )
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun skipForward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition + 15_000L).coerceAtMost(ctrl.duration.coerceAtLeast(0L)))
    }

    fun skipBackward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition - 15_000L).coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }
}
