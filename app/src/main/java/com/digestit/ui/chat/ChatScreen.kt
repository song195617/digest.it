package com.digestit.ui.chat

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.digestit.domain.model.ChatMessage
import com.digestit.domain.model.ChatSessionInfo
import com.digestit.domain.model.MessageRole
import com.digestit.ui.common.AdaptiveColumns
import com.digestit.ui.common.AppWidthSize
import com.digestit.ui.common.EditorialCard
import com.digestit.ui.common.ScreenContentFrame
import com.digestit.ui.common.SectionHeader
import com.digestit.ui.common.formatDateTime
import com.digestit.ui.common.formatTimestamp
import com.mikepenz.markdown.m3.Markdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    episodeId: String,
    onNavigateToTranscript: (Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val widthSize = com.digestit.ui.common.rememberAppWidthSize()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            ChatComposer(
                inputText = state.inputText,
                isLoading = state.isLoading,
                retryMessageText = state.retryMessageText,
                onInputChange = viewModel::onInputChange,
                onRetry = viewModel::retryLastMessage,
                onSend = viewModel::sendMessage,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        ScreenContentFrame(
            paddingValues = paddingValues,
            maxWidth = 1220.dp,
        ) { screenWidth ->
            if (screenWidth == AppWidthSize.Compact) {
                ChatCompactLayout(
                    state = state,
                    listState = listState,
                    clipboard = clipboard,
                    onNavigateToTranscript = onNavigateToTranscript,
                    onCreateSession = viewModel::startNewSession,
                    onSelectSession = viewModel::selectSession,
                    onSendPresetMessage = viewModel::sendPresetMessage,
                )
            } else {
                AdaptiveColumns(
                    widthSize = screenWidth,
                    modifier = Modifier.fillMaxSize(),
                    leading = { modifier ->
                        ChatSidebar(
                            sessions = state.sessions,
                            activeSessionId = state.sessionId,
                            suggestedQuestions = viewModel.suggestedQuestions,
                            onCreateSession = viewModel::startNewSession,
                            onSelectSession = viewModel::selectSession,
                            onSendPresetMessage = viewModel::sendPresetMessage,
                            modifier = modifier,
                        )
                    },
                    trailing = { modifier ->
                        ChatConversationPane(
                            state = state,
                            listState = listState,
                            clipboard = clipboard,
                            onNavigateToTranscript = onNavigateToTranscript,
                            onRetryQuestion = viewModel::sendPresetMessage,
                            modifier = modifier,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatCompactLayout(
    state: ChatState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onNavigateToTranscript: (Long) -> Unit,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onSendPresetMessage: (String) -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SessionStrip(
                    sessions = state.sessions,
                    activeSessionId = state.sessionId,
                    onSelectSession = onSelectSession,
                    onCreateSession = onCreateSession,
                )
            }
            item {
                ChatIntroCard(
                    showSuggestions = state.messages.isEmpty(),
                    suggestedQuestions = emptyList(),
                    onSendPresetMessage = onSendPresetMessage,
                )
            }
            if (state.messages.isEmpty()) {
                item {
                    SuggestionRow(
                        questions = listOf(
                            "这期内容的核心观点是什么？",
                            "用3句话帮我总结",
                            "有哪些值得关注的数据或案例？",
                        ),
                        onSendPresetMessage = onSendPresetMessage,
                    )
                }
            }
            items(state.messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onRetryQuestion = onSendPresetMessage,
                    onTimestampClick = onNavigateToTranscript,
                )
            }
        }
    }
}

@Composable
private fun ChatSidebar(
    sessions: List<ChatSessionInfo>,
    activeSessionId: String?,
    suggestedQuestions: List<String>,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onSendPresetMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorialCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    eyebrow = "AI Workspace",
                    title = "围绕这期内容继续深问",
                    detail = "把摘要、原文和时间戳串联在一起，快速追溯证据。",
                )
                Button(onClick = onCreateSession, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建会话")
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "会话上下文",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sessions.isEmpty()) {
                    Text(
                        text = "当前还没有历史会话，发送第一条消息后会自动创建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.forEach { session ->
                        FilterChip(
                            selected = session.sessionId == activeSessionId,
                            onClick = { onSelectSession(session.sessionId) },
                            label = {
                                Text(
                                    if (session.previewText.isNotBlank()) {
                                        session.previewText.take(18)
                                    } else {
                                        formatDateTime(session.createdAt)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        EditorialCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "快速问题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                suggestedQuestions.forEach { question ->
                    OutlinedButton(
                        onClick = { onSendPresetMessage(question) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(question)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatConversationPane(
    state: ChatState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onNavigateToTranscript: (Long) -> Unit,
    onRetryQuestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorialCard(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ChatIntroCard(
                    showSuggestions = state.messages.isEmpty(),
                    suggestedQuestions = emptyList(),
                    onSendPresetMessage = onRetryQuestion,
                )
            }
            items(state.messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onRetryQuestion = onRetryQuestion,
                    onTimestampClick = onNavigateToTranscript,
                )
            }
        }
    }
}

@Composable
private fun ChatIntroCard(
    showSuggestions: Boolean,
    suggestedQuestions: List<String>,
    onSendPresetMessage: (String) -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                eyebrow = "Context-aware chat",
                title = "AI 会引用摘要与原文中的时间点",
                detail = "点击时间戳可以直接跳到对应原文位置和音频片段。",
            )
            if (showSuggestions && suggestedQuestions.isNotEmpty()) {
                SuggestionRow(
                    questions = suggestedQuestions,
                    onSendPresetMessage = onSendPresetMessage,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    questions: List<String>,
    onSendPresetMessage: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(questions) { question ->
            SuggestionChip(
                onClick = { onSendPresetMessage(question) },
                label = { Text(question) },
            )
        }
    }
}

@Composable
private fun ChatComposer(
    inputText: String,
    isLoading: Boolean,
    retryMessageText: String?,
    onInputChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSend: () -> Unit,
) {
    Column {
        retryMessageText?.let {
            RetryBanner(onRetry = onRetry)
        }
        EditorialCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("继续问观点、案例、数据或某个具体时间点") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(52.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
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
            text = "会话上下文",
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
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "发送失败，可以直接重试上一句",
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
    onRetryQuestion: (String) -> Unit,
    onTimestampClick: (Long) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        EditorialCard(
            modifier = if (isUser) {
                Modifier.widthIn(max = 420.dp)
            } else {
                Modifier.fillMaxWidth(0.92f)
            },
            containerColor = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentPadding = PaddingValues(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Markdown(message.content)
                }
                if (message.isStreaming) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isUser) {
                        SuggestionChip(
                            onClick = { onRetryQuestion(message.content) },
                            label = { Text("再问一次", style = MaterialTheme.typography.labelSmall) },
                        )
                    } else if (!message.isStreaming) {
                        SuggestionChip(
                            onClick = onCopy,
                            label = { Text("复制", style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                }
                if (message.referencedTimestamps.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(message.referencedTimestamps) { timestamp ->
                            AssistChip(
                                onClick = { onTimestampClick(timestamp) },
                                label = { Text(formatTimestamp(timestamp), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }
        }
    }
}
