package com.digestit.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digestit.domain.model.Platform
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.LabelPill
import com.digestit.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    viewModel: ShareViewModel,
    onDismiss: () -> Unit,
    onJobStarted: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isStarted) {
        if (state.isStarted) onJobStarted()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                SectionHeader(
                    eyebrow = "Import",
                    title = "添加到 digest.it",
                    detail = "导入后将自动提取内容、生成转录和 AI 摘要。",
                )

                val platformLabel = when (state.platform) {
                    Platform.BILIBILI -> "哔哩哔哩 视频"
                    Platform.XIAOYUZHOU -> "小宇宙 播客"
                    Platform.UNKNOWN -> state.validationTitle
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelPill(text = platformLabel)
                    LabelPill(
                        text = when (state.validationKind) {
                            ShareValidationKind.VALID -> "可导入"
                            ShareValidationKind.EMPTY -> "等待识别"
                            ShareValidationKind.UNSUPPORTED -> "暂不支持"
                            ShareValidationKind.INVALID -> "无法解析"
                        },
                        containerColor = if (state.validationKind == ShareValidationKind.VALID) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (state.validationKind == ShareValidationKind.VALID) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }

                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.validationMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.validationKind == ShareValidationKind.VALID) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        if (state.url.isNotBlank()) {
                            Text(
                                text = state.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = viewModel::onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSubmitting && state.validationKind == ShareValidationKind.VALID
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("开始处理", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
