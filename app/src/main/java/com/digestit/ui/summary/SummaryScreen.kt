package com.digestit.ui.summary

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.Highlight
import com.digestit.ui.common.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    episodeId: String,
    viewModel: SummaryViewModel = hiltViewModel(),
    onNavigateToTranscript: (Long?) -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showCopyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享摘要"))
    }

    fun buildPlainText(summaryText: com.digestit.domain.model.Summary): String {
        return buildString {
            appendLine(state.episode?.title ?: "digest.it 摘要")
            appendLine(state.episode?.author.orEmpty())
            appendLine()
            appendLine(summaryText.oneLiner)
            appendLine()
            appendLine(summaryText.fullSummary)
            if (summaryText.highlights.isNotEmpty()) {
                appendLine()
                appendLine("精彩片段")
                summaryText.highlights.forEach { highlight ->
                    appendLine("- ${formatTimestamp(highlight.timestampMs)} ${highlight.quote}")
                }
            }
        }.trim()
    }

    fun buildMarkdown(summaryText: com.digestit.domain.model.Summary): String {
        return buildString {
            appendLine("# ${state.episode?.title ?: "digest.it 摘要"}")
            if (!state.episode?.author.isNullOrBlank()) {
                appendLine("- 作者: ${state.episode?.author}")
            }
            appendLine()
            appendLine("> ${summaryText.oneLiner}")
            appendLine()
            appendLine("## 详细摘要")
            appendLine(summaryText.fullSummary)
            if (summaryText.highlights.isNotEmpty()) {
                appendLine()
                appendLine("## 精彩片段")
                summaryText.highlights.forEach { highlight ->
                    appendLine("- **${formatTimestamp(highlight.timestampMs)}** ${highlight.quote}")
                }
            }
        }.trim()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.episode?.title ?: "摘要", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { state.summary?.let { shareText(buildPlainText(it)) } }) {
                        Icon(Icons.Default.Share, contentDescription = "分享摘要")
                    }
                    Box {
                        IconButton(onClick = { showCopyMenu = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                        DropdownMenu(expanded = showCopyMenu, onDismissRequest = { showCopyMenu = false }) {
                            DropdownMenuItem(text = { Text("复制一句话") }, onClick = {
                                clipboard.setText(AnnotatedString(state.summary?.oneLiner ?: ""))
                                showCopyMenu = false
                            })
                            DropdownMenuItem(text = { Text("复制完整摘要") }, onClick = {
                                clipboard.setText(AnnotatedString(state.summary?.fullSummary ?: ""))
                                showCopyMenu = false
                            })
                            DropdownMenuItem(text = { Text("复制纯文本") }, onClick = {
                                state.summary?.let { clipboard.setText(AnnotatedString(buildPlainText(it))) }
                                showCopyMenu = false
                            })
                            DropdownMenuItem(text = { Text("复制 Markdown") }, onClick = {
                                state.summary?.let { clipboard.setText(AnnotatedString(buildMarkdown(it))) }
                                showCopyMenu = false
                            })
                        }
                    }
                    IconButton(onClick = { onNavigateToTranscript(null) }) {
                        Icon(Icons.Default.Description, contentDescription = "查看全文")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onNavigateToChat,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 对话")
                    }
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
                Text(state.error ?: "摘要尚未生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(paddingValues)) {
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
                SummaryTab.HIGHLIGHTS -> HighlightsList(
                    highlights = summary.highlights,
                    onTimestampClick = onNavigateToTranscript,
                    onShareHighlight = { highlight ->
                        shareText(buildString {
                            appendLine(state.episode?.title ?: "digest.it")
                            appendLine("${formatTimestamp(highlight.timestampMs)} ${highlight.quote}")
                            if (highlight.context.isNotBlank()) {
                                appendLine()
                                append(highlight.context)
                            }
                        })
                    }
                )
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
            Text(
                fullSummary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Composable
private fun HighlightsList(
    highlights: List<Highlight>,
    onTimestampClick: (Long) -> Unit,
    onShareHighlight: (Highlight) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(highlights) { _, highlight ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { onTimestampClick(highlight.timestampMs) },
                            label = { Text(formatTimestamp(highlight.timestampMs)) }
                        )
                        SuggestionChip(
                            onClick = { onShareHighlight(highlight) },
                            label = { Text("分享片段") },
                            icon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                    }
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
