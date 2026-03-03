package com.digestit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digestit.data.local.datastore.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val openAiKey: String = "",
    val claudeKey: String = "",
    val backendUrl: String = "",
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false,
    val aiProvider: String = "claude",
    val geminiApiKey: String = "",
    val customAiBaseUrl: String = "",
    val customAiModel: String = "",
    val customAiApiKey: String = "",
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
                prefs.openAiApiKey,
                prefs.claudeApiKey,
                prefs.backendUrl,
                prefs.aiProvider,
                prefs.geminiApiKey,
            ) { openAi, claude, url, provider, gemini ->
                listOf(openAi, claude, url, provider, gemini)
            }.combine(
                combine(prefs.customAiBaseUrl, prefs.customAiModel, prefs.customAiApiKey) { baseUrl, model, key ->
                    Triple(baseUrl, model, key)
                }
            ) { first, second ->
                _state.update {
                    it.copy(
                        openAiKey = first[0],
                        claudeKey = first[1],
                        backendUrl = first[2],
                        aiProvider = first[3],
                        geminiApiKey = first[4],
                        customAiBaseUrl = second.first,
                        customAiModel = second.second,
                        customAiApiKey = second.third,
                    )
                }
            }.collect {}
        }
    }

    fun onOpenAiKeyChange(key: String) { _state.update { it.copy(openAiKey = key) } }
    fun onClaudeKeyChange(key: String) { _state.update { it.copy(claudeKey = key) } }
    fun onBackendUrlChange(url: String) { _state.update { it.copy(backendUrl = url) } }
    fun onAiProviderChange(provider: String) { _state.update { it.copy(aiProvider = provider) } }
    fun onGeminiApiKeyChange(key: String) { _state.update { it.copy(geminiApiKey = key) } }
    fun onCustomAiBaseUrlChange(url: String) { _state.update { it.copy(customAiBaseUrl = url) } }
    fun onCustomAiModelChange(model: String) { _state.update { it.copy(customAiModel = model) } }
    fun onCustomAiApiKeyChange(key: String) { _state.update { it.copy(customAiApiKey = key) } }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            // Snapshot all values before any DataStore write, because each write triggers
            // the combine collector in init which would overwrite _state with stale values.
            val snapshot = _state.value
            prefs.setOpenAiApiKey(snapshot.openAiKey)
            prefs.setClaudeApiKey(snapshot.claudeKey)
            prefs.setBackendUrl(snapshot.backendUrl)
            prefs.setAiProvider(snapshot.aiProvider)
            prefs.setGeminiApiKey(snapshot.geminiApiKey)
            prefs.setCustomAiBaseUrl(snapshot.customAiBaseUrl)
            prefs.setCustomAiModel(snapshot.customAiModel)
            prefs.setCustomAiApiKey(snapshot.customAiApiKey)
            _state.update { it.copy(isSaving = false, savedSuccess = true) }
        }
    }

    fun onSavedSuccessConsumed() { _state.update { it.copy(savedSuccess = false) } }
}
