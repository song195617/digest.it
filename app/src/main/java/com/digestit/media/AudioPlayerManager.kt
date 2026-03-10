package com.digestit.media

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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
    val playbackState: Int = Player.STATE_IDLE,
    val hasMediaItem: Boolean = false,
    val canPlay: Boolean = false,
    val canSeek: Boolean = false,
    val currentEpisodeId: String? = null,
    val currentUrl: String? = null,
    val episodeTitle: String = "",
    val author: String = "",
    val errorMessage: String? = null,
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioPlayerManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollingJob: Job? = null

    private data class PendingSource(
        val episodeId: String,
        val url: String,
        val title: String,
        val author: String
    )

    @Volatile
    private var pendingSource: PendingSource? = null

    @Volatile
    private var pendingSeekMs: Long? = null

    @Volatile
    private var pendingPlay: Boolean = false

    fun connect() {
        if (controller != null || controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioPlayerService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val future = controllerFuture
            val ctrl = runCatching { future?.get() }.getOrNull()
            controllerFuture = null
            if (ctrl == null) {
                Log.e(TAG, "Failed to connect media controller")
                _state.update {
                    it.copy(
                        isConnected = false,
                        canPlay = false,
                        canSeek = false,
                        errorMessage = "音频播放器连接失败"
                    )
                }
                return@addListener
            }

            Log.d(TAG, "Media controller connected")
            controller = ctrl
            ctrl.addListener(playerListener)
            _state.update { it.copy(isConnected = true, errorMessage = null) }
            startPolling()

            val rememberedSource = pendingSource ?: _state.value.currentUrl?.let { url ->
                val episodeId = _state.value.currentEpisodeId ?: return@let null
                PendingSource(episodeId, url, _state.value.episodeTitle, _state.value.author)
            }
            pendingSource = null

            if (ctrl.currentMediaItem == null && rememberedSource != null) {
                applySource(
                    ctrl,
                    rememberedSource.episodeId,
                    rememberedSource.url,
                    rememberedSource.title,
                    rememberedSource.author
                )
            } else {
                syncState(ctrl)
            }
            flushPendingActions(ctrl)
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed: $playbackState")
            val ctrl = controller ?: return
            syncState(ctrl)
            if (playbackState == Player.STATE_READY) {
                flushPendingActions(ctrl)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "isPlaying=$isPlaying")
            controller?.let(::syncState)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "Media item transition: reason=$reason uri=${mediaItem?.localConfiguration?.uri}")
            controller?.let(::syncState)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            controller?.let(::syncState)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            controller?.let(::syncState)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            Log.d(TAG, "Position discontinuity: reason=$reason new=${newPosition.positionMs}")
            controller?.let(::syncState)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error", error)
            _state.update {
                it.copy(
                    isPlaying = false,
                    canPlay = false,
                    canSeek = false,
                    errorMessage = error.errorCodeName.ifBlank { error.message ?: "音频播放失败" }
                )
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                controller?.let(::syncState)
                delay(200)
            }
        }
    }

    suspend fun setAudioSource(episodeId: String, url: String, title: String, author: String) {
        withContext(Dispatchers.Main) {
            val ctrl = controller
            if (ctrl == null) {
                pendingSource = PendingSource(episodeId, url, title, author)
                rememberSource(episodeId, url, title, author)
                return@withContext
            }

            if (controllerHasSource(ctrl, episodeId, url)) {
                _state.update {
                    it.copy(
                        currentEpisodeId = episodeId,
                        currentUrl = url,
                        episodeTitle = title,
                        author = author,
                        hasMediaItem = true,
                        errorMessage = null
                    )
                }
                syncState(ctrl)
                return@withContext
            }

            applySource(ctrl, episodeId, url, title, author)
        }
    }

    suspend fun playFrom(
        episodeId: String,
        url: String,
        title: String,
        author: String,
        positionMs: Long
    ) {
        withContext(Dispatchers.Main) {
            pendingSeekMs = positionMs
            pendingPlay = true

            val ctrl = controller
            if (ctrl == null) {
                pendingSource = PendingSource(episodeId, url, title, author)
                rememberSource(episodeId, url, title, author, positionMs)
                return@withContext
            }

            if (!controllerHasSource(ctrl, episodeId, url) || ctrl.currentMediaItem == null) {
                applySource(ctrl, episodeId, url, title, author, positionMs)
                return@withContext
            }

            _state.update {
                it.copy(
                    currentEpisodeId = episodeId,
                    currentUrl = url,
                    episodeTitle = title,
                    author = author,
                    positionMs = positionMs,
                    errorMessage = null
                )
            }
            flushPendingActions(ctrl)
        }
    }

    fun play() {
        val ctrl = controller
        if (ctrl == null) {
            pendingPlay = true
            return
        }
        if (ctrl.currentMediaItem == null) {
            val source = _state.value.currentUrl?.let { url ->
                val episodeId = _state.value.currentEpisodeId ?: return@let null
                PendingSource(episodeId, url, _state.value.episodeTitle, _state.value.author)
            }
            if (source != null) {
                pendingPlay = true
                applySource(
                    ctrl,
                    source.episodeId,
                    source.url,
                    source.title,
                    source.author,
                    _state.value.positionMs
                )
            }
            return
        }
        if (ctrl.playbackState == Player.STATE_IDLE) {
            pendingPlay = true
            ctrl.prepare()
            syncState(ctrl)
            return
        }
        if (!ctrl.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            pendingPlay = true
            syncState(ctrl)
            return
        }
        ctrl.play()
        syncState(ctrl)
    }

    fun pause() {
        pendingPlay = false
        controller?.pause()
        controller?.let(::syncState)
    }

    fun seekTo(positionMs: Long) {
        val ctrl = controller
        if (ctrl == null) {
            pendingSeekMs = positionMs
            _state.update { it.copy(positionMs = positionMs) }
            return
        }
        if (ctrl.currentMediaItem == null) {
            val source = _state.value.currentUrl?.let { url ->
                val episodeId = _state.value.currentEpisodeId ?: return@let null
                PendingSource(episodeId, url, _state.value.episodeTitle, _state.value.author)
            }
            if (source != null) {
                pendingSeekMs = positionMs
                applySource(
                    ctrl,
                    source.episodeId,
                    source.url,
                    source.title,
                    source.author,
                    positionMs
                )
                return
            }
        }
        if (ctrl.currentMediaItem == null || !ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            pendingSeekMs = positionMs
            _state.update { it.copy(positionMs = positionMs) }
            return
        }
        if (ctrl.playbackState !in setOf(Player.STATE_READY, Player.STATE_ENDED)) {
            pendingSeekMs = positionMs
            if (ctrl.playbackState == Player.STATE_IDLE) {
                ctrl.prepare()
            }
            _state.update { it.copy(positionMs = positionMs) }
            syncState(ctrl)
            return
        }
        ctrl.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
        syncState(ctrl)
    }

    fun skipForward() {
        val ctrl = controller ?: return
        if (!ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        ctrl.seekTo((ctrl.currentPosition + 15_000L).coerceAtMost(ctrl.duration.coerceAtLeast(0L)))
        syncState(ctrl)
    }

    fun skipBackward() {
        val ctrl = controller ?: return
        if (!ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        ctrl.seekTo((ctrl.currentPosition - 15_000L).coerceAtLeast(0L))
        syncState(ctrl)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        controller?.let(::syncState) ?: _state.update { it.copy(playbackSpeed = speed) }
    }

    private fun rememberSource(
        episodeId: String,
        url: String,
        title: String,
        author: String,
        positionMs: Long = 0L
    ) {
        _state.update {
            it.copy(
                currentEpisodeId = episodeId,
                currentUrl = url,
                episodeTitle = title,
                author = author,
                positionMs = positionMs,
                durationMs = 0L,
                playbackState = Player.STATE_BUFFERING,
                hasMediaItem = true,
                canPlay = false,
                canSeek = false,
                errorMessage = null
            )
        }
    }

    private fun applySource(
        ctrl: MediaController,
        episodeId: String,
        url: String,
        title: String,
        author: String,
        initialPositionMs: Long = 0L
    ) {
        Log.d(TAG, "Applying media source: episodeId=$episodeId url=$url")
        rememberSource(episodeId, url, title, author, initialPositionMs)
        val mediaItem = MediaItem.Builder()
            .setMediaId(episodeId)
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
        syncState(ctrl)
    }

    private fun controllerHasSource(ctrl: MediaController, episodeId: String, url: String): Boolean {
        val currentEpisodeId = ctrl.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: _state.value.currentEpisodeId
        val currentUrl = ctrl.currentMediaItem?.localConfiguration?.uri?.toString() ?: _state.value.currentUrl
        return currentEpisodeId == episodeId && currentUrl == url
    }

    private fun flushPendingActions(ctrl: MediaController) {
        if (ctrl.currentMediaItem == null) return

        val canPlayNow = ctrl.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) &&
            ctrl.playbackState in setOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_ENDED)
        val canSeekNow = ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
            ctrl.playbackState in setOf(Player.STATE_READY, Player.STATE_ENDED)

        if ((pendingSeekMs != null || pendingPlay) && ctrl.playbackState == Player.STATE_IDLE) {
            ctrl.prepare()
        }

        if (canSeekNow) {
            pendingSeekMs?.let { target ->
                Log.d(TAG, "Executing pending seek to $target")
                pendingSeekMs = null
                ctrl.seekTo(target)
            }
        }

        if (canPlayNow && pendingPlay) {
            Log.d(TAG, "Executing pending play")
            pendingPlay = false
            ctrl.play()
        }

        syncState(ctrl)
    }

    private fun syncState(ctrl: MediaController) {
        val hasMediaItem = ctrl.currentMediaItem != null || _state.value.currentUrl != null
        val duration = ctrl.duration.takeIf { it > 0L } ?: 0L
        val position = ctrl.currentPosition.takeIf { it >= 0L } ?: _state.value.positionMs
        _state.update {
            it.copy(
                isConnected = true,
                isPlaying = ctrl.isPlaying,
                positionMs = position,
                durationMs = duration,
                playbackSpeed = ctrl.playbackParameters.speed,
                playbackState = ctrl.playbackState,
                hasMediaItem = hasMediaItem,
                canPlay = hasMediaItem && ctrl.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
                canSeek = hasMediaItem && ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                currentEpisodeId = ctrl.currentMediaItem?.mediaId?.takeIf { mediaId -> mediaId.isNotBlank() }
                    ?: it.currentEpisodeId,
                currentUrl = ctrl.currentMediaItem?.localConfiguration?.uri?.toString() ?: it.currentUrl,
                episodeTitle = ctrl.mediaMetadata.title?.toString() ?: it.episodeTitle,
                author = ctrl.mediaMetadata.artist?.toString() ?: it.author,
                errorMessage = null
            )
        }
    }
}
