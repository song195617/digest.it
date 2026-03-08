package com.digestit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.data.local.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

data class SettingsState(
    val claudeKey: String = "",
    val backendUrl: String = "",
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false,
    val aiProvider: String = "claude",
    val geminiApiKey: String = "",
    val customAiBaseUrl: String = "",
    val customAiModel: String = "",
    val customAiApiKey: String = "",
    val deepseekApiKey: String = "",
    val deepseekBaseUrl: String = UserPreferencesDataStore.DEEPSEEK_DEFAULT_URL,
    val deepseekModel: String = UserPreferencesDataStore.DEEPSEEK_DEFAULT_MODEL,
    val availableModels: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.claudeApiKey,
                prefs.backendUrl,
                prefs.aiProvider,
                prefs.geminiApiKey,
            ) { claude, url, provider, gemini ->
                listOf(claude, url, provider, gemini)
            }.combine(
                combine(prefs.customAiBaseUrl, prefs.customAiModel, prefs.customAiApiKey) { a, b, c -> Triple(a, b, c) }
            ) { first, custom -> Pair(first, custom) }
                .combine(
                    combine(prefs.deepseekApiKey, prefs.deepseekBaseUrl, prefs.deepseekModel) { a, b, c -> Triple(a, b, c) }
                ) { (first, custom), ds ->
                    _state.update {
                        it.copy(
                            claudeKey = first[0],
                            backendUrl = first[1],
                            aiProvider = first[2],
                            geminiApiKey = first[3],
                            customAiBaseUrl = custom.first,
                            customAiModel = custom.second,
                            customAiApiKey = custom.third,
                            deepseekApiKey = ds.first,
                            deepseekBaseUrl = ds.second,
                            deepseekModel = ds.third,
                        )
                    }
                }.collect {}
        }
    }

    fun onClaudeKeyChange(key: String) { _state.update { it.copy(claudeKey = key) } }
    fun onBackendUrlChange(url: String) { _state.update { it.copy(backendUrl = url) } }
    fun onAiProviderChange(provider: String) {
        _state.update { it.copy(aiProvider = provider, availableModels = emptyList(), modelsError = null) }
    }
    fun onGeminiApiKeyChange(key: String) { _state.update { it.copy(geminiApiKey = key) } }
    fun onCustomAiBaseUrlChange(url: String) { _state.update { it.copy(customAiBaseUrl = url) } }
    fun onCustomAiModelChange(model: String) { _state.update { it.copy(customAiModel = model) } }
    fun onCustomAiApiKeyChange(key: String) { _state.update { it.copy(customAiApiKey = key) } }
    fun onDeepseekApiKeyChange(key: String) { _state.update { it.copy(deepseekApiKey = key) } }
    fun onDeepseekBaseUrlChange(url: String) { _state.update { it.copy(deepseekBaseUrl = url) } }
    fun onDeepseekModelChange(model: String) { _state.update { it.copy(deepseekModel = model) } }
    fun onSavedSuccessConsumed() { _state.update { it.copy(savedSuccess = false) } }

    fun resetDeepseekBaseUrl() {
        _state.update { it.copy(deepseekBaseUrl = UserPreferencesDataStore.DEEPSEEK_DEFAULT_URL) }
    }

    fun fetchModels() {
        val s = _state.value
        val (baseUrl, apiKey) = when (s.aiProvider) {
            "deepseek" -> s.deepseekBaseUrl to s.deepseekApiKey
            "openai_compatible" -> s.customAiBaseUrl to s.customAiApiKey
            else -> return
        }
        if (apiKey.isBlank()) {
            _state.update { it.copy(modelsError = "请先填写 API Key") }
            return
        }
        _state.update { it.copy(isLoadingModels = true, modelsError = null, availableModels = emptyList()) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val url = baseUrl.trimEnd('/') + "/models"
                    val client = OkHttpClient()
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build()
                    val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
                    val data = JSONObject(body).getJSONArray("data")
                    (0 until data.length()).map { data.getJSONObject(it).getString("id") }.sorted()
                }
            }.fold(
                onSuccess = { models -> _state.update { it.copy(isLoadingModels = false, availableModels = models) } },
                onFailure = { e -> _state.update { it.copy(isLoadingModels = false, modelsError = e.message ?: "请求失败") } }
            )
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            val s = _state.value
            prefs.setClaudeApiKey(s.claudeKey)
            prefs.setBackendUrl(s.backendUrl)
            prefs.setAiProvider(s.aiProvider)
            prefs.setGeminiApiKey(s.geminiApiKey)
            prefs.setCustomAiBaseUrl(s.customAiBaseUrl)
            prefs.setCustomAiModel(s.customAiModel)
            prefs.setCustomAiApiKey(s.customAiApiKey)
            prefs.setDeepseekApiKey(s.deepseekApiKey)
            prefs.setDeepseekBaseUrl(s.deepseekBaseUrl)
            prefs.setDeepseekModel(s.deepseekModel)
            _state.update { it.copy(isSaving = false, savedSuccess = true) }
        }
    }
}
