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
    val savedSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.openAiApiKey, prefs.claudeApiKey, prefs.backendUrl) { openAi, claude, url ->
                Triple(openAi, claude, url)
            }.collect { (openAi, claude, url) ->
                _state.update { it.copy(openAiKey = openAi, claudeKey = claude, backendUrl = url) }
            }
        }
    }

    fun onOpenAiKeyChange(key: String) { _state.update { it.copy(openAiKey = key) } }
    fun onClaudeKeyChange(key: String) { _state.update { it.copy(claudeKey = key) } }
    fun onBackendUrlChange(url: String) { _state.update { it.copy(backendUrl = url) } }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            prefs.setOpenAiApiKey(_state.value.openAiKey)
            prefs.setClaudeApiKey(_state.value.claudeKey)
            prefs.setBackendUrl(_state.value.backendUrl)
            _state.update { it.copy(isSaving = false, savedSuccess = true) }
        }
    }

    fun onSavedSuccessConsumed() { _state.update { it.copy(savedSuccess = false) } }
}
