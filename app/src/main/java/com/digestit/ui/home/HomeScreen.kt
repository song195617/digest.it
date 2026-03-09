package com.digestit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.model.SearchResult
import com.digestit.domain.model.SearchSourceType
import com.digestit.ui.common.formatDateTime
import com.digestit.ui.theme.platformColor

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
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("digest.it", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索标题、摘要或全文") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { viewModel.onFilterChange(filter) },
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
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加链接")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                state.searchQuery.isNotBlank() -> {
                    SearchResultsContent(
                        results = state.searchResults,
                        onResultClick = viewModel::onSearchResultClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.episodes.isEmpty() -> {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        section("处理中", state.processingEpisodes) { episode ->
                            DismissibleEpisodeCard(
                                episode = episode,
                                onClick = { viewModel.onEpisodeClick(episode) },
                                onDelete = { viewModel.deleteEpisode(episode.id) },
                                onRetry = null,
                                onToggleFavorite = { viewModel.toggleFavorite(episode) }
                            )
                        }
                        section("最近完成", state.completedEpisodes) { episode ->
                            DismissibleEpisodeCard(
                                episode = episode,
                                onClick = { viewModel.onEpisodeClick(episode) },
                                onDelete = { viewModel.deleteEpisode(episode.id) },
                                onRetry = null,
                                onToggleFavorite = { viewModel.toggleFavorite(episode) }
                            )
                        }
                        section("失败任务", state.failedEpisodes) { episode ->
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
    items: List<Episode>,
    itemContent: @Composable (Episode) -> Unit,
) {
    if (items.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    items(items, key = { it.id }) { episode -> itemContent(episode) }
}

@Composable
private fun SearchResultsContent(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("没有匹配结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("搜索结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(results, key = { "${it.episodeId}-${it.sourceType}-${it.timestampMs}" }) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResultClick(result) },
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (result.sourceType) {
                            SearchSourceType.TITLE -> result.subtitle
                            SearchSourceType.SUMMARY -> "摘要 · ${result.subtitle}"
                            SearchSourceType.TRANSCRIPT -> "全文 · ${result.subtitle}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(result.previewText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        EpisodeCard(
            episode = episode,
            onClick = onClick,
            onRetry = onRetry,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    onRetry: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
) {
    val itemPlatformColor = platformColor[episode.platform.name] ?: Color.Gray
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (episode.coverUrl != null) {
                    AsyncImage(
                        model = episode.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = itemPlatformColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                episode.platform.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = itemPlatformColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusChip(episode.processingStatus)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (episode.author.isNotBlank()) {
                        Text(
                            episode.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (episode.lastOpenedAt != null) {
                            "最近查看 ${formatDateTime(episode.lastOpenedAt)}"
                        } else {
                            "创建于 ${formatDateTime(episode.createdAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            it,
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
        ProcessingStatus.COMPLETED -> "已完成" to Color(0xFF4CAF50)
        ProcessingStatus.FAILED -> "失败" to Color(0xFFF44336)
        ProcessingStatus.TRANSCRIBING -> "转录中" to Color(0xFF2196F3)
        ProcessingStatus.EXTRACTING -> "提取中" to Color(0xFF9C27B0)
        ProcessingStatus.SUMMARIZING -> "生成摘要" to Color(0xFFFF9800)
        ProcessingStatus.QUEUED -> "等待中" to Color.Gray
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("还没有内容", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "点击右下角 + 按钮粘贴小宇宙或哔哩哔哩链接\n也可以在对应 App 中分享链接到 digest.it",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        title = { Text("添加内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("粘贴小宇宙或哔哩哔哩链接") },
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
