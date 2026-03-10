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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.digestit.domain.model.Episode
import com.digestit.domain.model.Highlight
import com.digestit.domain.model.Summary
import com.digestit.ui.common.AdaptiveColumns
import com.digestit.ui.common.AppWidthSize
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.ScreenContentFrame
import com.digestit.ui.common.SectionHeader
import com.digestit.ui.common.formatDateTime
import com.digestit.ui.common.formatDuration
import com.digestit.ui.common.formatTimestamp
import com.mikepenz.markdown.m3.Markdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    episodeId: String,
    viewModel: SummaryViewModel = hiltViewModel(),
    onNavigateToTranscript: (Long?) -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val widthSize = com.digestit.ui.common.rememberAppWidthSize()
    var showCopyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享摘要"))
    }

    fun buildPlainText(summaryText: Summary): String {
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

    fun buildMarkdown(summaryText: Summary): String {
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("内容摘要") },
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
                        DropdownMenu(
                            expanded = showCopyMenu,
                            onDismissRequest = { showCopyMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制一句话") },
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.summary?.oneLiner ?: ""))
                                    showCopyMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制完整摘要") },
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.summary?.fullSummary ?: ""))
                                    showCopyMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制纯文本") },
                                onClick = {
                                    state.summary?.let { clipboard.setText(AnnotatedString(buildPlainText(it))) }
                                    showCopyMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制 Markdown") },
                                onClick = {
                                    state.summary?.let { clipboard.setText(AnnotatedString(buildMarkdown(it))) }
                                    showCopyMenu = false
                                }
                            )
                        }
                    }
                    if (widthSize == AppWidthSize.Compact) {
                        IconButton(onClick = { onNavigateToTranscript(null) }) {
                            Icon(Icons.Default.Description, contentDescription = "查看全文")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (widthSize == AppWidthSize.Compact && state.summary != null) {
                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(onClick = onNavigateToChat, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("继续向 AI 追问")
                    }
                }
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.summary == null -> {
                ScreenContentFrame(paddingValues = paddingValues, maxWidth = 920.dp) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EditorialCard {
                            Text(
                                text = state.error ?: "摘要尚未生成",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> {
                val summary = state.summary ?: return@Scaffold
                ScreenContentFrame(
                    paddingValues = paddingValues,
                    maxWidth = 1180.dp,
                ) { screenWidth ->
                    AdaptiveColumns(
                        widthSize = screenWidth,
                        modifier = Modifier.fillMaxSize(),
                        leading = { modifier ->
                            SummaryLeadPane(
                                episode = state.episode,
                                summary = summary,
                                selectedTab = state.selectedTab,
                                onSelectTab = viewModel::selectTab,
                                modifier = modifier,
                            )
                        },
                        trailing = { modifier ->
                            SummaryMainPane(
                                widthSize = screenWidth,
                                episode = state.episode,
                                summary = summary,
                                selectedTab = state.selectedTab,
                                onNavigateToTranscript = onNavigateToTranscript,
                                onNavigateToChat = onNavigateToChat,
                                onShareHighlight = { highlight ->
                                    shareText(
                                        buildString {
                                            appendLine(state.episode?.title ?: "digest.it")
                                            appendLine("${formatTimestamp(highlight.timestampMs)} ${highlight.quote}")
                                            if (highlight.context.isNotBlank()) {
                                                appendLine()
                                                append(highlight.context)
                                            }
                                        }
                                    )
                                },
                                modifier = modifier,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryLeadPane(
    episode: Episode?,
    summary: Summary,
    selectedTab: SummaryTab,
    onSelectTab: (SummaryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                episode?.let {
                    SummaryHeader(episode = it)
                }
                SectionHeader(
                    eyebrow = "一句话理解",
                    title = summary.oneLiner,
                    detail = "把长内容压缩成能直接消费的核心判断。",
                )
                if (summary.topics.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        summary.topics.take(2).forEach { topic ->
                            LabelPill(text = topic)
                        }
                    }
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "阅读视图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                SummaryTab.entries.forEach { tab ->
                    val title = when (tab) {
                        SummaryTab.KEY_POINTS -> "核心要点"
                        SummaryTab.FULL_SUMMARY -> "详细摘要"
                        SummaryTab.HIGHLIGHTS -> "精彩片段"
                    }
                    val selected = selectedTab == tab
                    Button(
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !selected,
                    ) {
                        Text(title)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(episode: Episode) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (episode.coverUrl != null) {
            AsyncImage(
                model = episode.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .align(Alignment.Top)
                    .let { modifier ->
                        modifier
                    },
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.author.isNotBlank()) {
                Text(
                    text = episode.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelPill(text = episode.platform.displayName)
                LabelPill(text = formatDuration(episode.durationSeconds))
            }
            Text(
                text = "创建于 ${formatDateTime(episode.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryMainPane(
    widthSize: AppWidthSize,
    episode: Episode?,
    summary: Summary,
    selectedTab: SummaryTab,
    onNavigateToTranscript: (Long?) -> Unit,
    onNavigateToChat: () -> Unit,
    onShareHighlight: (Highlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (widthSize != AppWidthSize.Compact) {
            EditorialCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = episode?.title ?: "本期内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "从摘要继续进入原文或发起 AI 深问。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { onNavigateToTranscript(null) }) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("看原文")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onNavigateToChat) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 对话")
                    }
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == SummaryTab.KEY_POINTS,
                        onClick = { },
                        enabled = false,
                        text = { Text("核心要点") },
                    )
                    Tab(
                        selected = selectedTab == SummaryTab.FULL_SUMMARY,
                        onClick = { },
                        enabled = false,
                        text = { Text("详细摘要") },
                    )
                    Tab(
                        selected = selectedTab == SummaryTab.HIGHLIGHTS,
                        onClick = { },
                        enabled = false,
                        text = { Text("精彩片段") },
                    )
                }
                when (selectedTab) {
                    SummaryTab.KEY_POINTS -> KeyPointsList(summary.keyPoints)
                    SummaryTab.FULL_SUMMARY -> FullSummaryContent(summary.fullSummary)
                    SummaryTab.HIGHLIGHTS -> HighlightsList(
                        highlights = summary.highlights,
                        onTimestampClick = { onNavigateToTranscript(it) },
                        onShareHighlight = onShareHighlight,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyPointsList(keyPoints: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        keyPoints.forEachIndexed { index, point ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FullSummaryContent(fullSummary: String) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(18.dp),
    ) {
        Markdown(fullSummary)
    }
}

@Composable
private fun HighlightsList(
    highlights: List<Highlight>,
    onTimestampClick: (Long) -> Unit,
    onShareHighlight: (Highlight) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        highlights.forEach { highlight ->
            EditorialCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { onTimestampClick(highlight.timestampMs) },
                            label = { Text(formatTimestamp(highlight.timestampMs)) },
                        )
                        SuggestionChip(
                            onClick = { onShareHighlight(highlight) },
                            label = { Text("分享片段") },
                            icon = { Icon(Icons.Default.Share, contentDescription = null) },
                        )
                    }
                    Text(
                        text = "\"${highlight.quote}\"",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (highlight.context.isNotBlank()) {
                        Text(
                            text = highlight.context,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
