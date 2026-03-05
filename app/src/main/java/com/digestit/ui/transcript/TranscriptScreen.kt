package com.digestit.ui.transcript

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.TranscriptSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    episodeId: String,
    viewModel: TranscriptViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

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
                    Text("转录文本未找到", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(paddingValues)
                ) {
                    item {
                        Text(
                            "共 ${state.transcript!!.wordCount} 字 · ${state.filteredSegments.size} 个片段",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(state.filteredSegments) { segment ->
                        TranscriptSegmentItem(segment)
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptSegmentItem(segment: TranscriptSegment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        val minutes = segment.startMs / 60000
        val seconds = (segment.startMs % 60000) / 1000
        SuggestionChip(
            onClick = {},
            label = { Text("%02d:%02d".format(minutes, seconds), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            segment.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
