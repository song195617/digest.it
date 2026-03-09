package com.digestit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) viewModel.onSavedSuccessConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断与设置") },
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
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EffectiveSettingsCard(state)

            Text("AI 摘要提供商", style = MaterialTheme.typography.titleMedium)

            val providerOptions = listOf(
                "deepseek" to "DeepSeek",
                "claude" to "Claude (Anthropic)",
                "gemini" to "Gemini (Google)",
                "openai_compatible" to "自定义 OpenAI 兼容",
            )
            var providerExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
                OutlinedTextField(
                    value = providerOptions.firstOrNull { it.first == state.aiProvider }?.second ?: state.aiProvider,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("提供商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    providerOptions.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            viewModel.onAiProviderChange(value)
                            providerExpanded = false
                        })
                    }
                }
            }

            when (state.aiProvider) {
                "deepseek" -> DeepSeekSection(state, viewModel)
                "claude" -> PasswordField("Claude API Key", "sk-ant-...", state.claudeKey, viewModel::onClaudeKeyChange)
                "gemini" -> PasswordField("Gemini API Key", "AIza...", state.geminiApiKey, viewModel::onGeminiApiKeyChange)
                "openai_compatible" -> OpenAiCompatSection(state, viewModel)
            }

            HorizontalDivider()

            Text("后端服务器", style = MaterialTheme.typography.titleMedium)
            BackendUrlField(
                value = state.backendUrl,
                history = state.backendUrlHistory,
                onValueChange = viewModel::onBackendUrlChange,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::testBackendConnection,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTestingBackend
                ) {
                    if (state.isTestingBackend) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试后端连接")
                    }
                }
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("保存")
                }
            }
            if (state.savedSuccess) Text("已保存 ✓", color = MaterialTheme.colorScheme.primary)
            state.backendTestError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.backendHealth?.let { health ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("后端诊断", style = MaterialTheme.typography.titleSmall)
                        StatusRow("API", health.api.status, health.api.detail)
                        StatusRow("数据库", health.db.status, health.db.detail)
                        StatusRow("Redis", health.redis.status, health.redis.detail)
                        StatusRow("Celery", health.celery.status, health.celery.detail)
                        StatusRow("Whisper", health.whisper.status, "${health.whisper.mode} · ${health.whisper.model}")
                        if (health.whisper.fallback) {
                            Text(
                                "已从 ${health.whisper.configuredMode} 回退到 ${health.whisper.mode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        health.whisper.errorMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectiveSettingsCard(state: SettingsState) {
    val effectiveModel = when (state.aiProvider) {
        "deepseek" -> state.deepseekModel
        "openai_compatible" -> state.customAiModel
        "claude" -> "Claude default"
        "gemini" -> "Gemini default"
        else -> "未设置"
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前生效配置", style = MaterialTheme.typography.titleSmall)
            Text("Provider: ${state.aiProvider}")
            Text("Model: ${effectiveModel.ifBlank { "未设置" }}")
            Text("Backend: ${state.backendUrl.ifBlank { "未设置" }}")
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, detail: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.3f))
        Text(status, color = when (status) {
            "ok" -> MaterialTheme.colorScheme.primary
            "degraded" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        })
        Text(detail ?: "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepSeekSection(state: SettingsState, vm: SettingsViewModel) {
    PasswordField("DeepSeek API Key", "sk-...", state.deepseekApiKey, vm::onDeepseekApiKeyChange)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.deepseekBaseUrl,
            onValueChange = vm::onDeepseekBaseUrlChange,
            label = { Text("API Base URL") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = vm::resetDeepseekBaseUrl) {
            Icon(Icons.Default.Refresh, contentDescription = "重置为默认地址")
        }
    }

    ModelSelector(
        model = state.deepseekModel,
        availableModels = state.availableModels,
        isLoading = state.isLoadingModels,
        error = state.modelsError,
        onModelChange = vm::onDeepseekModelChange,
        onFetch = vm::fetchModels
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenAiCompatSection(state: SettingsState, vm: SettingsViewModel) {
    OutlinedTextField(
        value = state.customAiBaseUrl,
        onValueChange = vm::onCustomAiBaseUrlChange,
        label = { Text("Base URL") },
        placeholder = { Text("https://api.openai.com") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    PasswordField("API Key", "", state.customAiApiKey, vm::onCustomAiApiKeyChange)
    ModelSelector(
        model = state.customAiModel,
        availableModels = state.availableModels,
        isLoading = state.isLoadingModels,
        error = state.modelsError,
        onModelChange = vm::onCustomAiModelChange,
        onFetch = vm::fetchModels
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    model: String,
    availableModels: List<String>,
    isLoading: Boolean,
    error: String?,
    onModelChange: (String) -> Unit,
    onFetch: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (availableModels.isEmpty()) {
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text("模型 ID") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    label = { Text("模型 ID") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    availableModels.forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = { onModelChange(id); dropdownExpanded = false })
                    }
                }
            }
        }

        FilledTonalIconButton(onClick = onFetch, enabled = !isLoading) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("获取", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (availableModels.isNotEmpty()) {
        Text("已获取 ${availableModels.size} 个模型", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackendUrlField(
    value: String,
    history: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && history.isNotEmpty(),
        onExpandedChange = { if (history.isNotEmpty()) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = false
            },
            label = { Text("后端 URL") },
            placeholder = { Text("http://10.0.2.2:8000") },
            trailingIcon = {
                if (history.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded && history.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            history.forEach { url ->
                DropdownMenuItem(
                    text = { Text(url, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onValueChange(url)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, placeholder: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
