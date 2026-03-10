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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.TranscriptSegment
import com.digestit.ui.common.formatTimestamp

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

    LaunchedEffect(episodeId, initialTimestampMs) { viewModel.load(episodeId, initialTimestampMs) }

    LaunchedEffect(state.pendingScrollTargetStartMs, state.visibleSegments) {
        val targetStartMs = state.pendingScrollTargetStartMs ?: return@LaunchedEffect
        val itemIndex = state.visibleSegments.indexOfFirst { it.startMs == targetStartMs }
        if (itemIndex >= 0) {
            listState.animateScrollToItem(itemIndex + 1)
        }
        viewModel.onPendingScrollHandled()
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
                            onTimestampClick = { viewModel.focusTimestamp(segment.startMs) }
                        )
                    }
                }
            }
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
