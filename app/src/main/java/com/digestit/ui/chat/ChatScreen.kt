package com.digestit.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSessionInfo
import com.digestit.domain.model.MessageRole
import com.digestit.ui.common.formatDateTime
import com.digestit.ui.common.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    episodeId: String,
    onNavigateToTranscript: (Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size + 1)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 对话") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::startNewSession) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新会话")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                state.retryMessageText?.let {
                    RetryBanner(onRetry = viewModel::retryLastMessage)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问关于这期内容的问题...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = viewModel::sendMessage,
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(paddingValues)
        ) {
            item {
                SessionStrip(
                    sessions = state.sessions,
                    activeSessionId = state.sessionId,
                    onSelectSession = viewModel::selectSession,
                    onCreateSession = viewModel::startNewSession,
                )
            }

            if (state.messages.isEmpty()) {
                item {
                    Text(
                        "试试这些问题：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.suggestedQuestions) { question ->
                            SuggestionChip(
                                onClick = { viewModel.sendPresetMessage(question) },
                                label = { Text(question) }
                            )
                        }
                    }
                }
            }

            items(state.messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onRetryQuestion = { viewModel.sendPresetMessage(message.content) },
                    onTimestampClick = onNavigateToTranscript,
                )
            }
        }
    }
}

@Composable
private fun SessionStrip(
    sessions: List<ChatSessionInfo>,
    activeSessionId: String?,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "会话上下文",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = onCreateSession) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新会话")
                }
            }
            items(sessions, key = { it.sessionId }) { session ->
                FilterChip(
                    selected = session.sessionId == activeSessionId,
                    onClick = { onSelectSession(session.sessionId) },
                    label = {
                        Text(
                            if (session.previewText.isNotBlank()) {
                                session.previewText.take(10)
                            } else {
                                formatDateTime(session.createdAt)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RetryBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "发送失败，可以直接重试上一句",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetryQuestion: () -> Unit,
    onTimestampClick: (Long) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.isStreaming) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isUser) {
                        SuggestionChip(onClick = onRetryQuestion, label = { Text("再问一次") })
                    } else {
                        SuggestionChip(
                            onClick = onCopy,
                            label = { Text("复制回答") },
                            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                    }
                }
                if (message.referencedTimestamps.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(message.referencedTimestamps) { timestamp ->
                            AssistChip(
                                onClick = { onTimestampClick(timestamp) },
                                label = { Text(formatTimestamp(timestamp), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}
