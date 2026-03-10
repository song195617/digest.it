package com.digestit.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.digestit.media.AudioPlayerState
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.formatTimestamp

@Composable
fun GlobalAudioPlayerBar(
    modifier: Modifier = Modifier,
    viewModel: AudioPlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    if (!playerState.hasMediaItem && playerState.currentUrl == null) return

    AudioPlayerBar(
        playerState = playerState,
        onPlayPause = { if (playerState.isPlaying) viewModel.pause() else viewModel.play() },
        onSeekTo = viewModel::seekTo,
        onSkipForward = viewModel::skipForward,
        onSkipBackward = viewModel::skipBackward,
        onSpeedChange = viewModel::setPlaybackSpeed,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerBar(
    playerState: AudioPlayerState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speedSteps = listOf(1.0f, 1.5f, 2.0f, 0.75f)
    val controlsEnabled = playerState.canPlay
    val seekEnabled = playerState.canSeek && playerState.durationMs > 0L

    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playerState.positionMs) {
        if (!isDragging) sliderValue = playerState.positionMs.toFloat()
    }

    EditorialCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = playerState.episodeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playerState.author.isNotBlank()) {
                            Text(
                                text = playerState.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val playerMessage = when {
                            !playerState.errorMessage.isNullOrBlank() -> playerState.errorMessage
                            playerState.playbackState == Player.STATE_BUFFERING -> "音频加载中"
                            !playerState.isConnected -> "连接播放器中"
                            !controlsEnabled -> "播放器准备中"
                            else -> null
                        }
                        playerMessage?.let {
                            LabelPill(
                                text = it,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                val currentSpeedIndex = speedSteps.indexOf(playerState.playbackSpeed).takeIf { it >= 0 } ?: 0
                val nextSpeedIndex = (currentSpeedIndex + 1) % speedSteps.size
                AssistChip(
                    onClick = { onSpeedChange(speedSteps[nextSpeedIndex]) },
                    enabled = controlsEnabled,
                    label = {
                        val speed = playerState.playbackSpeed
                        val speedLabel = if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"
                        Text(text = speedLabel, style = MaterialTheme.typography.labelMedium)
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTimestamp(playerState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        isDragging = true
                    },
                    onValueChangeFinished = {
                        onSeekTo(sliderValue.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..playerState.durationMs.toFloat().coerceAtLeast(1f),
                    enabled = seekEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    formatTimestamp(playerState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipBackward, enabled = seekEnabled) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "后退15秒")
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = controlsEnabled,
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onSkipForward, enabled = seekEnabled) {
                    Icon(Icons.Default.SkipNext, contentDescription = "前进15秒")
                }
            }
        }
    }
}
