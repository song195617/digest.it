package com.digestit.ui.home

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.digestit.domain.model.Platform
import com.digestit.domain.model.ProcessingStatus
import com.digestit.ui.theme.platformColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToSummary: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val effect by viewModel.effects.collectAsState()
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(effect) {
        when (val e = effect) {
            is HomeEffect.NavigateToProcessing -> {
                onNavigateToProcessing(e.jobId)
                viewModel.onEffectConsumed()
            }
            is HomeEffect.NavigateToSummary -> {
                onNavigateToSummary(e.episodeId)
                viewModel.onEffectConsumed()
            }
            else -> {}
        }
    }

    Scaffold(
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
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (state.episodes.isEmpty()) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.episodes, key = { it.id }) { episode ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.deleteEpisode(episode.id)
                                    true
                                } else false
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
                            EpisodeCard(episode = episode, onClick = { viewModel.onEpisodeClick(episode) })
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
            onSubmit = { viewModel.onSubmitUrl(); showUrlDialog = false },
            onDismiss = { showUrlDialog = false }
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    val platformColor = platformColor[episode.platform.name] ?: Color.Gray
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
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
                        color = platformColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            episode.platform.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = platformColor,
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加内容") },
        text = {
            Column {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("粘贴小宇宙或哔哩哔哩链接") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !isSubmitting && urlInput.isNotBlank()) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("开始处理")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
