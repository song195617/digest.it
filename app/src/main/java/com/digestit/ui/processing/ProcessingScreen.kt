package com.digestit.ui.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
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
            is ProcessingEffect.ProcessingFailed -> {
                viewModel.onEffectConsumed()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("处理中") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                state.currentStep,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            StepIndicators(currentStatus = state.status)

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage ?: "处理失败",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "处理可能需要数分钟，你可以返回首页继续等待。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onNavigateHome, modifier = Modifier.weight(1f)) {
                    Text("返回首页")
                }
                OutlinedButton(onClick = onNavigateBack, modifier = Modifier.weight(1f)) {
                    Text("后台继续处理")
                }
            }
            if (state.status == ProcessingStatus.FAILED && state.episodeId != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::retry,
                    enabled = !state.isRetrying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isRetrying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("失败后重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicators(currentStatus: ProcessingStatus) {
    val steps = listOf(
        ProcessingStatus.EXTRACTING to "提取音频",
        ProcessingStatus.TRANSCRIBING to "AI 转录",
        ProcessingStatus.SUMMARIZING to "生成摘要"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        steps.forEachIndexed { index, (stepStatus, label) ->
            val isDone = when (currentStatus) {
                ProcessingStatus.TRANSCRIBING -> index == 0
                ProcessingStatus.SUMMARIZING -> index <= 1
                ProcessingStatus.COMPLETED -> true
                else -> false
            }
            val isCurrent = currentStatus == stepStatus
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.primary
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDone) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
