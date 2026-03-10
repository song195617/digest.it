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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.ui.common.AdaptiveColumns
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.ScreenContentFrame
import com.digestit.ui.common.SectionHeader
import com.digestit.ui.common.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    episodeId: String,
    initialTimestampMs: Long?,
    viewModel: TranscriptViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(episodeId, initialTimestampMs) { viewModel.load(episodeId, initialTimestampMs) }

    LaunchedEffect(state.pendingScrollTargetParagraphStartMs, state.paragraphs) {
        val targetStartMs = state.pendingScrollTargetParagraphStartMs ?: return@LaunchedEffect
        val itemIndex = state.paragraphs.indexOfFirst { it.paragraphStartMs == targetStartMs }
        if (itemIndex >= 0) {
            val listItemIndex = itemIndex
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listItemIndex }
            if (!isVisible) {
                listState.animateScrollToItem(listItemIndex)
            }
        }
        viewModel.onPendingScrollHandled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("转录全文") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.transcript == null -> {
                ScreenContentFrame(paddingValues = paddingValues, maxWidth = 960.dp) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EditorialCard {
                            Text(
                                text = state.error ?: "转录文本未找到",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> {
                ScreenContentFrame(
                    paddingValues = paddingValues,
                    maxWidth = 1200.dp,
                ) { widthSize ->
                    AdaptiveColumns(
                        widthSize = widthSize,
                        modifier = Modifier.fillMaxSize(),
                        leading = { modifier ->
                            TranscriptLeadPane(
                                searchQuery = state.searchQuery,
                                searchMatchCount = state.searchMatchCount,
                                currentMatchPosition = state.currentMatchPosition,
                                wordCount = state.transcript?.wordCount ?: 0,
                                paragraphCount = state.paragraphs.size,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onPrevious = viewModel::goToPreviousMatch,
                                onNext = viewModel::goToNextMatch,
                                modifier = modifier,
                            )
                        },
                        trailing = { modifier ->
                            EditorialCard(
                                modifier = modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(state.paragraphs, key = { it.paragraphStartMs }) { paragraph ->
                                        TranscriptParagraphItem(
                                            paragraph = paragraph,
                                            activeSentenceStartMs = state.activeSentenceStartMs,
                                            onTimestampClick = { viewModel.focusTimestamp(paragraph.paragraphStartMs) },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptLeadPane(
    searchQuery: String,
    searchMatchCount: Int,
    currentMatchPosition: Int,
    wordCount: Int,
    paragraphCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    eyebrow = "Transcript Reader",
                    title = "像阅读文档一样浏览全文",
                    detail = "支持关键词命中跳转、时间点定位和播放联动高亮。",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelPill(text = "$wordCount 字")
                    LabelPill(text = "$paragraphCount 段")
                    LabelPill(
                        text = if (searchQuery.isBlank()) "未搜索" else "命中 $searchMatchCount",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "检索原文",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索观点、案例或关键词") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            Row {
                                IconButton(onClick = onPrevious, enabled = searchMatchCount > 1) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                                }
                                IconButton(onClick = onNext, enabled = searchMatchCount > 1) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                                }
                            }
                        }
                    },
                    singleLine = true,
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        "输入关键词后，会自动定位到第一个命中的句子。"
                    } else if (searchMatchCount == 0) {
                        "没有找到匹配内容。"
                    } else {
                        "当前定位 $currentMatchPosition / $searchMatchCount"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                shape = RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
    ) {
        SuggestionChip(
            onClick = onTimestampClick,
            label = { Text(formatTimestamp(paragraph.paragraphStartMs), style = MaterialTheme.typography.labelSmall) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = paragraphText,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 30.sp,
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
                        background = colors.secondaryContainer.copy(alpha = 0.92f),
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
