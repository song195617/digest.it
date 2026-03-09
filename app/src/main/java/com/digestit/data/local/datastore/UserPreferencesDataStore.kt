package com.digestit.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.digestit.data.local.secure.SecurePreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class NetworkSettingsSnapshot(
    val backendUrl: String = "https://api.digestit.app",
    val aiProvider: String = "claude",
    val claudeApiKey: String = "",
    val geminiApiKey: String = "",
    val customAiBaseUrl: String = "",
    val customAiModel: String = "",
    val customAiApiKey: String = "",
    val deepseekApiKey: String = "",
    val deepseekBaseUrl: String = UserPreferencesDataStore.DEEPSEEK_DEFAULT_URL,
    val deepseekModel: String = UserPreferencesDataStore.DEEPSEEK_DEFAULT_MODEL,
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferencesStore: SecurePreferencesStore
) {
    companion object {
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_CUSTOM_AI_BASE_URL = stringPreferencesKey("custom_ai_base_url")
        private val KEY_CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
        private val KEY_DEEPSEEK_BASE_URL = stringPreferencesKey("deepseek_base_url")
        private val KEY_DEEPSEEK_MODEL = stringPreferencesKey("deepseek_model")
        private val KEY_NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
        private val KEY_BACKEND_URL_HISTORY = stringPreferencesKey("backend_url_history")
        private const val URL_HISTORY_MAX = 5
        private const val URL_HISTORY_SEP = "\n"

        const val DEEPSEEK_DEFAULT_URL = "https://api.deepseek.com"
        const val DEEPSEEK_DEFAULT_MODEL = "deepseek-chat"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkSettingsState = MutableStateFlow(NetworkSettingsSnapshot())

    val claudeApiKey: Flow<String> = securePreferencesStore.claudeApiKey
    val backendUrl: Flow<String> = context.dataStore.data.map { it[KEY_BACKEND_URL] ?: "https://api.digestit.app" }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[KEY_AI_PROVIDER] ?: "claude" }
    val geminiApiKey: Flow<String> = securePreferencesStore.geminiApiKey
    val customAiBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_BASE_URL] ?: "" }
    val customAiModel: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_MODEL] ?: "" }
    val customAiApiKey: Flow<String> = securePreferencesStore.customAiApiKey
    val deepseekApiKey: Flow<String> = securePreferencesStore.deepseekApiKey
    val deepseekBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_DEEPSEEK_BASE_URL] ?: DEEPSEEK_DEFAULT_URL }
    val deepseekModel: Flow<String> = context.dataStore.data.map { it[KEY_DEEPSEEK_MODEL] ?: DEEPSEEK_DEFAULT_MODEL }
    val notificationPermissionRequested: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_PERMISSION_REQUESTED] ?: false }

    val backendUrlHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL_HISTORY]
            ?.split(URL_HISTORY_SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    val currentNetworkSettings: StateFlow<NetworkSettingsSnapshot> = networkSettingsState

    init {
        scope.launch {
            combine(
                backendUrl,
                aiProvider,
                claudeApiKey,
                geminiApiKey,
                customAiBaseUrl,
                customAiModel,
                customAiApiKey,
                deepseekApiKey,
                deepseekBaseUrl,
                deepseekModel
            ) { values: Array<String> ->
                NetworkSettingsSnapshot(
                    backendUrl = values[0],
                    aiProvider = values[1],
                    claudeApiKey = values[2],
                    geminiApiKey = values[3],
                    customAiBaseUrl = values[4],
                    customAiModel = values[5],
                    customAiApiKey = values[6],
                    deepseekApiKey = values[7],
                    deepseekBaseUrl = values[8],
                    deepseekModel = values[9]
                )
            }.collect { networkSettingsState.value = it }
        }
    }

    suspend fun setClaudeApiKey(key: String) = securePreferencesStore.setClaudeApiKey(key)

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[KEY_BACKEND_URL] = url }
    }

    suspend fun addBackendUrlToHistory(url: String) {
        if (url.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_BACKEND_URL_HISTORY]
                ?.split(URL_HISTORY_SEP)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val updated = (listOf(url) + current.filter { it != url })
                .take(URL_HISTORY_MAX)
            prefs[KEY_BACKEND_URL_HISTORY] = updated.joinToString(URL_HISTORY_SEP)
        }
    }

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setGeminiApiKey(key: String) = securePreferencesStore.setGeminiApiKey(key)

    suspend fun setCustomAiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_BASE_URL] = url }
    }

    suspend fun setCustomAiModel(model: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_MODEL] = model }
    }

    suspend fun setCustomAiApiKey(key: String) = securePreferencesStore.setCustomAiApiKey(key)

    suspend fun setDeepseekApiKey(key: String) = securePreferencesStore.setDeepseekApiKey(key)

    suspend fun setDeepseekBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_DEEPSEEK_BASE_URL] = url }
    }

    suspend fun setDeepseekModel(model: String) {
        context.dataStore.edit { it[KEY_DEEPSEEK_MODEL] = model }
    }

    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_PERMISSION_REQUESTED] = requested }
    }
}
