package com.digestit.ui.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.ProcessingStatus
import com.digestit.ui.common.AdaptiveColumns
import com.digestit.ui.common.AppWidthSize
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.ScreenContentFrame
import com.digestit.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    jobId: String,
    viewModel: ProcessingViewModel = hiltViewModel(),
    onProcessingComplete: (String) -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val effect by viewModel.effects.collectAsState()

    LaunchedEffect(jobId) { viewModel.observeJob(jobId) }

    LaunchedEffect(effect) {
        when (val e = effect) {
            is ProcessingEffect.ProcessingComplete -> {
                onProcessingComplete(e.episodeId)
                viewModel.onEffectConsumed()
            }
            is ProcessingEffect.NavigateToProcessing -> {
                onNavigateToProcessing(e.jobId)
                viewModel.onEffectConsumed()
            }
            is ProcessingEffect.ProcessingFailed -> viewModel.onEffectConsumed()
            null -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("内容处理中") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        ScreenContentFrame(
            paddingValues = paddingValues,
            maxWidth = 1100.dp,
        ) { widthSize ->
            AdaptiveColumns(
                widthSize = widthSize,
                modifier = Modifier.fillMaxSize(),
                leading = { modifier ->
                    ProcessingHero(
                        status = state.status,
                        progress = state.progress,
                        currentStep = state.currentStep,
                        errorMessage = state.errorMessage,
                        modifier = modifier,
                    )
                },
                trailing = { modifier ->
                    ProcessingSidebar(
                        status = state.status,
                        isRetrying = state.isRetrying,
                        episodeId = state.episodeId,
                        onNavigateHome = onNavigateHome,
                        onNavigateBack = onNavigateBack,
                        onRetry = viewModel::retry,
                        modifier = modifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun ProcessingHero(
    status: ProcessingStatus,
    progress: Float,
    currentStep: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    EditorialCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SectionHeader(
                eyebrow = "AI Pipeline",
                title = when (status) {
                    ProcessingStatus.FAILED -> "任务中断，需要重新发起"
                    ProcessingStatus.COMPLETED -> "处理完成，准备进入摘要"
                    else -> "正在把内容整理成可阅读版本"
                },
                detail = "我们会依次提取、转录并总结内容。这个过程可能持续几分钟。",
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(132.dp),
                    strokeWidth = 10.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = status.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            EditorialCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabelPill(
                        text = when (status) {
                            ProcessingStatus.FAILED -> "需要处理"
                            ProcessingStatus.COMPLETED -> "已完成"
                            else -> "当前阶段"
                        }
                    )
                    Text(
                        text = currentStep,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    StepIndicators(currentStatus = status)
                    errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingSidebar(
    status: ProcessingStatus,
    isRetrying: Boolean,
    episodeId: String?,
    onNavigateHome: () -> Unit,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    eyebrow = "当前状态",
                    title = status.label(),
                    detail = when (status) {
                        ProcessingStatus.FAILED -> "任务已停止，你可以立即重试，或先回首页查看其他内容。"
                        else -> "你可以离开当前页面，任务会继续在后台执行。"
                    },
                )
                OutlinedButton(
                    onClick = onNavigateHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回首页")
                }
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("后台继续处理")
                }
                if (status == ProcessingStatus.FAILED && episodeId != null) {
                    Button(
                        onClick = onRetry,
                        enabled = !isRetrying,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("立即重试")
                        }
                    }
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "处理说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "系统会先识别可用音轨，再进行 AI 转录，并生成结构化摘要与高光片段。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StepIndicators(currentStatus: ProcessingStatus) {
    val steps = listOf(
        ProcessingStatus.EXTRACTING to "提取素材",
        ProcessingStatus.TRANSCRIBING to "语音转录",
        ProcessingStatus.SUMMARIZING to "生成摘要",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, (stepStatus, label) ->
            val isDone = when (currentStatus) {
                ProcessingStatus.TRANSCRIBING -> index == 0
                ProcessingStatus.SUMMARIZING -> index <= 1
                ProcessingStatus.COMPLETED -> true
                else -> false
            }
            val isCurrent = currentStatus == stepStatus
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = when {
                            isDone -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    isDone -> MaterialTheme.colorScheme.onPrimary
                                    isCurrent -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = when {
                            isDone -> "已完成"
                            isCurrent -> "进行中"
                            else -> "等待中"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ProcessingStatus.label(): String = when (this) {
    ProcessingStatus.QUEUED -> "排队中"
    ProcessingStatus.EXTRACTING -> "正在提取"
    ProcessingStatus.TRANSCRIBING -> "正在转录"
    ProcessingStatus.SUMMARIZING -> "正在总结"
    ProcessingStatus.COMPLETED -> "已完成"
    ProcessingStatus.FAILED -> "处理失败"
}
