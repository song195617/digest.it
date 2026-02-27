package com.digestit.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showOpenAiKey by remember { mutableStateOf(false) }
    var showClaudeKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showCustomAiKey by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    val providerOptions = listOf(
        "claude" to "Claude",
        "gemini" to "Gemini",
        "openai_compatible" to "自定义 OpenAI 兼容"
    )

    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) {
            viewModel.onSavedSuccessConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API 配置", style = MaterialTheme.typography.titleMedium)
            Text(
                "你需要自己的 API Key 来使用转录和 AI 功能。费用直接从你的账户扣除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("AI 摘要提供商", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded,
                onExpandedChange = { providerDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = providerOptions.firstOrNull { it.first == state.aiProvider }?.second ?: state.aiProvider,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("提供商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded,
                    onDismissRequest = { providerDropdownExpanded = false }
                ) {
                    providerOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onAiProviderChange(value)
                                providerDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            when (state.aiProvider) {
                "claude" -> {
                    OutlinedTextField(
                        value = state.claudeKey,
                        onValueChange = viewModel::onClaudeKeyChange,
                        label = { Text("Claude API Key") },
                        placeholder = { Text("sk-ant-...") },
                        visualTransformation = if (showClaudeKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                                Icon(
                                    if (showClaudeKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "gemini" -> {
                    OutlinedTextField(
                        value = state.geminiApiKey,
                        onValueChange = viewModel::onGeminiApiKeyChange,
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showGeminiKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "openai_compatible" -> {
                    OutlinedTextField(
                        value = state.customAiBaseUrl,
                        onValueChange = viewModel::onCustomAiBaseUrlChange,
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.customAiModel,
                        onValueChange = viewModel::onCustomAiModelChange,
                        label = { Text("模型名称（可选）") },
                        placeholder = { Text("deepseek-chat") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.customAiApiKey,
                        onValueChange = viewModel::onCustomAiApiKeyChange,
                        label = { Text("API Key") },
                        visualTransformation = if (showCustomAiKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCustomAiKey = !showCustomAiKey }) {
                                Icon(
                                    if (showCustomAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.openAiKey,
                onValueChange = viewModel::onOpenAiKeyChange,
                label = { Text("OpenAI API Key（用于语音转录）") },
                placeholder = { Text("sk-...") },
                visualTransformation = if (showOpenAiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                        Icon(
                            if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            Text("后端服务器", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.backendUrl,
                onValueChange = viewModel::onBackendUrlChange,
                label = { Text("后端 URL") },
                placeholder = { Text("https://api.digestit.app") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存")
                }
            }

            if (state.savedSuccess) {
                Text("已保存", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
