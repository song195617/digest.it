package com.digestit.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.model.SearchResult
import com.digestit.domain.model.SearchSourceType
import com.digestit.ui.common.AppWidthSize
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.MetricCard
import com.digestit.ui.common.ScreenContentFrame
import com.digestit.ui.common.SectionHeader
import com.digestit.ui.common.formatDateTime
import com.digestit.ui.common.formatDuration
import com.digestit.ui.theme.platformColor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToSummary: (String) -> Unit,
    onNavigateToTranscript: (String, Long?) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val effect by viewModel.effects.collectAsState()
    val widthSize = com.digestit.ui.common.rememberAppWidthSize()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(effect) {
        when (val e = effect) {
            is HomeEffect.NavigateToProcessing -> {
                showUrlDialog = false
                onNavigateToProcessing(e.jobId)
                viewModel.onEffectConsumed()
            }
            is HomeEffect.NavigateToSummary -> {
                onNavigateToSummary(e.episodeId)
                viewModel.onEffectConsumed()
            }
            is HomeEffect.NavigateToTranscript -> {
                onNavigateToTranscript(e.episodeId, e.timestampMs)
                viewModel.onEffectConsumed()
            }
            is HomeEffect.ShowError -> {
                snackbarHostState.showSnackbar(e.message)
                viewModel.onEffectConsumed()
            }
            null -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("digest.it", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加链接")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        ScreenContentFrame(
            paddingValues = paddingValues,
            maxWidth = 1200.dp,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HomeHeroCard(
                        state = state,
                        widthSize = widthSize,
                        onSearchChange = viewModel::onSearchQueryChange,
                        onFilterChange = viewModel::onFilterChange,
                        onShowAddDialog = { showUrlDialog = true },
                    )
                }

                if (state.searchQuery.isNotBlank()) {
                    item {
                        SectionHeader(
                            eyebrow = "Search",
                            title = if (state.searchResults.isEmpty()) "没有找到匹配结果" else "搜索结果",
                            detail = "支持标题、摘要和全文检索。",
                        )
                    }
                    if (state.searchResults.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(
                            items = state.searchResults,
                            key = { "${it.episodeId}-${it.sourceType}-${it.timestampMs}" }
                        ) { result ->
                            SearchResultCard(
                                result = result,
                                onResultClick = viewModel::onSearchResultClick,
                            )
                        }
                    }
                } else if (state.episodes.isEmpty()) {
                    item { EmptyState() }
                } else {
                    section(
                        title = "处理中",
                        detail = "任务会在后台持续推进，可随时进入详情查看进度。",
                        items = state.processingEpisodes,
                    ) { episode ->
                        DismissibleEpisodeCard(
                            episode = episode,
                            onClick = { viewModel.onEpisodeClick(episode) },
                            onDelete = { viewModel.deleteEpisode(episode.id) },
                            onRetry = null,
                            onToggleFavorite = { viewModel.toggleFavorite(episode) }
                        )
                    }
                    section(
                        title = "最近完成",
                        detail = "优先展示最近可继续阅读和继续发问的内容。",
                        items = state.completedEpisodes,
                    ) { episode ->
                        DismissibleEpisodeCard(
                            episode = episode,
                            onClick = { viewModel.onEpisodeClick(episode) },
                            onDelete = { viewModel.deleteEpisode(episode.id) },
                            onRetry = null,
                            onToggleFavorite = { viewModel.toggleFavorite(episode) }
                        )
                    }
                    section(
                        title = "失败任务",
                        detail = "保留失败原因并支持一键重试。",
                        items = state.failedEpisodes,
                    ) { episode ->
                        DismissibleEpisodeCard(
                            episode = episode,
                            onClick = { viewModel.onEpisodeClick(episode) },
                            onDelete = { viewModel.deleteEpisode(episode.id) },
                            onRetry = { viewModel.retryEpisode(episode.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(episode) }
                        )
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        AddUrlDialog(
            urlInput = state.urlInput,
            isSubmitting = state.isSubmitting,
            error = state.error,
            onUrlChange = viewModel::onUrlInputChange,
            onSubmit = viewModel::onSubmitUrl,
            onDismiss = { if (!state.isSubmitting) showUrlDialog = false },
            onPasteFromClipboard = {
                clipboardManager.getText()?.text?.let(viewModel::onUrlInputChange)
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    detail: String,
    items: List<Episode>,
    itemContent: @Composable (Episode) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        SectionHeader(
            title = title,
            detail = detail,
        )
    }
    items(items, key = { it.id }) { episode -> itemContent(episode) }
}

@Composable
private fun HomeHeroCard(
    state: HomeState,
    widthSize: AppWidthSize,
    onSearchChange: (String) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onShowAddDialog: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (widthSize == AppWidthSize.Compact) {
                SectionHeader(
                    eyebrow = "Knowledge digest",
                    title = "把播客和视频压缩成可阅读的知识工作台",
                    detail = "导入链接后，自动生成转录、摘要和可追问的 AI 上下文。",
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    SectionHeader(
                        eyebrow = "Knowledge digest",
                        title = "把播客和视频压缩成可阅读的知识工作台",
                        detail = "导入链接后，自动生成转录、摘要和可追问的 AI 上下文。",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = onShowAddDialog) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导入新内容")
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题、摘要或全文片段") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    HomeFilter.ALL -> "全部"
                                    HomeFilter.FAVORITES -> "收藏"
                                    HomeFilter.RECENT -> "最近查看"
                                }
                            )
                        }
                    )
                }
            }

            HomeMetrics(state = state, widthSize = widthSize)
        }
    }
}

@Composable
private fun HomeMetrics(state: HomeState, widthSize: AppWidthSize) {
    val metrics = listOf(
        Triple(state.episodes.size.toString(), "总内容", "已归档到本地"),
        Triple(state.processingEpisodes.size.toString(), "进行中", "后台继续处理"),
        Triple(state.completedEpisodes.size.toString(), "已完成", "可直接继续阅读"),
        Triple(state.episodes.count { it.isFavorite }.toString(), "收藏", "高频回看内容"),
    )
    if (widthSize == AppWidthSize.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { (value, label, supporting) ->
                        MetricCard(
                            value = value,
                            label = label,
                            supporting = supporting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            metrics.forEach { (value, label, supporting) ->
                MetricCard(
                    value = value,
                    label = label,
                    supporting = supporting,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onResultClick: (SearchResult) -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResultClick(result) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LabelPill(
                text = when (result.sourceType) {
                    SearchSourceType.TITLE -> result.subtitle
                    SearchSourceType.SUMMARY -> "摘要 · ${result.subtitle}"
                    SearchSourceType.TRANSCRIPT -> "全文 · ${result.subtitle}"
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = result.previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleEpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    val maxRevealPx = (cardWidthPx / 3f).takeIf { it > 0f } ?: 140f
    val revealThresholdPx = maxRevealPx * 0.5f
    val isDeleteActionVisible = offsetPx <= -maxRevealPx * 0.8f

    fun animateOffset(target: Float) {
        scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = tween(durationMillis = 180)
            ) { value, _ ->
                offsetPx = value
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                cardWidthPx = size.width.toFloat()
                offsetPx = offsetPx.coerceIn(-size.width / 3f, 0f)
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier.padding(end = 20.dp)
            ) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = isDeleteActionVisible
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }

        EpisodeCard(
            episode = episode,
            onClick = {
                if (offsetPx < 0f) {
                    animateOffset(0f)
                } else {
                    onClick()
                }
            },
            onRetry = onRetry,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(-maxRevealPx, 0f)
                    },
                    onDragStopped = { velocity ->
                        val shouldRevealDelete = offsetPx <= -revealThresholdPx || velocity < -1200f
                        animateOffset(if (shouldRevealDelete) -maxRevealPx else 0f)
                    }
                )
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                animateOffset(0f)
            },
            title = { Text("确认删除") },
            text = { Text("删除后会移除该条内容和相关缓存，是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        animateOffset(0f)
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    onRetry: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemPlatformColor = platformColor[episode.platform.name] ?: Color.Gray
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (episode.coverUrl != null) {
                    AsyncImage(
                        model = episode.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelPill(
                            text = episode.platform.displayName,
                            containerColor = itemPlatformColor.copy(alpha = 0.18f),
                            contentColor = itemPlatformColor,
                        )
                        StatusChip(episode.processingStatus)
                    }
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
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
                        LabelPill(text = formatDuration(episode.durationSeconds))
                        LabelPill(
                            text = if (episode.lastOpenedAt != null) {
                                "最近查看 ${formatDateTime(episode.lastOpenedAt)}"
                            } else {
                                "创建于 ${formatDateTime(episode.createdAt)}"
                            }
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (episode.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "收藏"
                    )
                }
            }
            if (episode.processingStatus == ProcessingStatus.FAILED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    episode.errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    onRetry?.let {
                        TextButton(onClick = it) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ProcessingStatus) {
    val (text, color) = when (status) {
        ProcessingStatus.COMPLETED -> "已完成" to Color(0xFF2F855A)
        ProcessingStatus.FAILED -> "失败" to Color(0xFFC53030)
        ProcessingStatus.TRANSCRIBING -> "转录中" to Color(0xFF2B6CB0)
        ProcessingStatus.EXTRACTING -> "提取中" to Color(0xFF7B4A8B)
        ProcessingStatus.SUMMARIZING -> "生成摘要" to Color(0xFFB7791F)
        ProcessingStatus.QUEUED -> "等待中" to Color(0xFF6B7280)
    }
    LabelPill(
        text = text,
        containerColor = color.copy(alpha = 0.16f),
        contentColor = color,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    EditorialCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(
                eyebrow = "Start here",
                title = "还没有内容库",
                detail = "点击右下角的导入按钮，粘贴小宇宙或哔哩哔哩链接，也可以直接从对应 App 分享到 digest.it。",
            )
        }
    }
}

@Composable
private fun AddUrlDialog(
    urlInput: String,
    isSubmitting: Boolean,
    error: String?,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onPasteFromClipboard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "粘贴小宇宙单集或哔哩哔哩视频链接，系统会自动开始处理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("内容链接") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextButton(onClick = onPasteFromClipboard, enabled = !isSubmitting) { Text("读取剪贴板") }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !isSubmitting && urlInput.isNotBlank()) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("开始处理")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("取消") }
        }
    )
}
