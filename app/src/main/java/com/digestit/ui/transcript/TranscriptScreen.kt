package com.digestit.ui.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.digestit.domain.model.TranscriptSegment
import com.digestit.ui.common.formatTimestamp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    episodeId: String,
    initialTimestampMs: Long?,
    viewModel: TranscriptViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(episodeId, initialTimestampMs) { viewModel.load(episodeId, initialTimestampMs) }

    LaunchedEffect(state.pendingScrollTargetStartMs, state.visibleSegments) {
        val targetStartMs = state.pendingScrollTargetStartMs ?: return@LaunchedEffect
        val itemIndex = state.visibleSegments.indexOfFirst { it.startMs == targetStartMs }
        if (itemIndex >= 0) {
            listState.animateScrollToItem(itemIndex + 1)
        }
        viewModel.onPendingScrollHandled()
    }

    val exoPlayer = remember(state.audioUrl) {
        state.audioUrl?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("转录全文") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索文字") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            Row {
                                IconButton(onClick = viewModel::goToPreviousMatch, enabled = state.searchMatchCount > 1) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                                }
                                IconButton(onClick = viewModel::goToNextMatch, enabled = state.searchMatchCount > 1) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                                }
                            }
                        }
                    },
                    singleLine = true
                )
            }
        },
        bottomBar = {
            if (exoPlayer != null) {
                AudioPlayerBar(player = exoPlayer)
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.transcript == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "转录文本未找到", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(paddingValues)
                ) {
                    item {
                        Text(
                            if (state.searchQuery.isBlank()) {
                                "共 ${state.transcript!!.wordCount} 字 · ${state.visibleSegments.size} 个片段"
                            } else {
                                "命中 ${state.searchMatchCount} 条 · 当前 ${state.currentMatchPosition.coerceAtLeast(1)}/${state.searchMatchCount.coerceAtLeast(1)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(state.visibleSegments, key = { it.startMs }) { segment ->
                        TranscriptSegmentItem(
                            segment = segment,
                            isHighlighted = segment.startMs == state.highlightedSegmentStartMs,
                            onTimestampClick = {
                                viewModel.focusTimestamp(segment.startMs)
                                exoPlayer?.seekTo(segment.startMs)
                                exoPlayer?.play()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerBar(player: ExoPlayer, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPositionMs by remember { mutableLongStateOf(player.currentPosition) }
    val durationMs = player.duration.coerceAtLeast(1L)

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            currentPositionMs = player.currentPosition
            delay(500)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放"
                    )
                }
                Text(
                    "${formatTimestamp(currentPositionMs)} / ${formatTimestamp(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TranscriptSegmentItem(
    segment: TranscriptSegment,
    isHighlighted: Boolean,
    onTimestampClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHighlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        SuggestionChip(
            onClick = onTimestampClick,
            label = { Text(formatTimestamp(segment.startMs), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            segment.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
