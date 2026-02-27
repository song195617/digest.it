package com.digestit.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.Highlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    episodeId: String,
    viewModel: SummaryViewModel = hiltViewModel(),
    onNavigateToTranscript: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.episode?.title ?: "摘要", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToTranscript) {
                        Icon(Icons.Default.Description, contentDescription = "查看全文")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onNavigateToChat,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI 对话")
                }
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val summary = state.summary
        if (summary == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("摘要尚未生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(paddingValues)) {
            // One-liner card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    summary.oneLiner,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Tab row
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == SummaryTab.KEY_POINTS,
                    onClick = { viewModel.selectTab(SummaryTab.KEY_POINTS) },
                    text = { Text("核心要点") }
                )
                Tab(
                    selected = state.selectedTab == SummaryTab.FULL_SUMMARY,
                    onClick = { viewModel.selectTab(SummaryTab.FULL_SUMMARY) },
                    text = { Text("详细摘要") }
                )
                Tab(
                    selected = state.selectedTab == SummaryTab.HIGHLIGHTS,
                    onClick = { viewModel.selectTab(SummaryTab.HIGHLIGHTS) },
                    text = { Text("精彩片段") }
                )
            }

            when (state.selectedTab) {
                SummaryTab.KEY_POINTS -> KeyPointsList(summary.keyPoints)
                SummaryTab.FULL_SUMMARY -> FullSummaryContent(summary.fullSummary)
                SummaryTab.HIGHLIGHTS -> HighlightsList(summary.highlights)
            }
        }
    }
}

@Composable
private fun KeyPointsList(keyPoints: List<String>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(keyPoints) { index, point ->
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(point, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FullSummaryContent(fullSummary: String) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(fullSummary, style = MaterialTheme.typography.bodyMedium, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

@Composable
private fun HighlightsList(highlights: List<Highlight>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(highlights.size) { index ->
            val highlight = highlights[index]
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    val minutes = highlight.timestampMs / 60000
                    val seconds = (highlight.timestampMs % 60000) / 1000
                    SuggestionChip(
                        onClick = {},
                        label = { Text("%02d:%02d".format(minutes, seconds)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "\"${highlight.quote}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (highlight.context.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            highlight.context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
