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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    LaunchedEffect(state.pendingScrollTargetParagraphStartMs, state.paragraphs) {
        val targetStartMs = state.pendingScrollTargetParagraphStartMs ?: return@LaunchedEffect
        val itemIndex = state.paragraphs.indexOfFirst { it.paragraphStartMs == targetStartMs }
        if (itemIndex >= 0) {
            val listItemIndex = itemIndex + 1
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listItemIndex }
            if (!isVisible) {
                listState.animateScrollToItem(listItemIndex)
            }
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
                                "共 ${state.transcript!!.wordCount} 字 · ${state.paragraphs.size} 段"
                            } else if (state.searchMatchCount == 0) {
                                "命中 0 句"
                            } else {
                                "命中 ${state.searchMatchCount} 句 · 当前 ${state.currentMatchPosition.coerceAtLeast(1)}/${state.searchMatchCount}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(state.paragraphs, key = { it.paragraphStartMs }) { paragraph ->
                        TranscriptParagraphItem(
                            paragraph = paragraph,
                            activeSentenceStartMs = state.activeSentenceStartMs,
                            onTimestampClick = { viewModel.focusTimestamp(paragraph.paragraphStartMs) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptParagraphItem(
    paragraph: TranscriptParagraphUiModel,
    activeSentenceStartMs: Long?,
    onTimestampClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val paragraphText = remember(paragraph, activeSentenceStartMs, colors) {
        buildParagraphAnnotatedText(paragraph, activeSentenceStartMs, colors)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
    ) {
        SuggestionChip(
            onClick = onTimestampClick,
            label = { Text(formatTimestamp(paragraph.paragraphStartMs), style = MaterialTheme.typography.labelSmall) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            paragraphText,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 28.sp,
        )
    }
}

private fun buildParagraphAnnotatedText(
    paragraph: TranscriptParagraphUiModel,
    activeSentenceStartMs: Long?,
    colors: androidx.compose.material3.ColorScheme,
): AnnotatedString {
    return buildAnnotatedString {
        var previousText: String? = null
        paragraph.sentences.forEach { sentence ->
            val normalized = TranscriptParagraphFormatter.normalizedSentenceText(sentence.text)
            if (normalized.isBlank()) return@forEach

            if (previousText != null && shouldInsertWordSpace(previousText!!, normalized)) {
                append(" ")
            }

            if (sentence.startMs == activeSentenceStartMs) {
                withStyle(
                    SpanStyle(
                        color = colors.onSecondaryContainer,
                        background = colors.secondaryContainer.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                ) {
                    append(normalized)
                }
            } else {
                withStyle(SpanStyle(color = colors.onSurface)) {
                    append(normalized)
                }
            }
            previousText = normalized
        }
    }
}

private fun shouldInsertWordSpace(previous: String, next: String): Boolean {
    val previousChar = previous.lastOrNull() ?: return false
    val nextChar = next.firstOrNull() ?: return false
    return previousChar.isLetterOrDigit() && nextChar.isLetterOrDigit()
}
