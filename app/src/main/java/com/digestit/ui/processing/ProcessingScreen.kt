package com.digestit.ui.processing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onNavigateBack: () -> Unit
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
            is ProcessingEffect.ProcessingFailed -> {
                viewModel.onEffectConsumed()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("处理中") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                state.currentStep,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))
            StepIndicators(currentStatus = state.status)

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "错误: ${state.errorMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "处理可能需要数分钟，你可以关闭此页面，完成后会收到通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
