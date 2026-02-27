package com.digestit.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_CUSTOM_AI_BASE_URL = stringPreferencesKey("custom_ai_base_url")
        private val KEY_CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
        private val KEY_CUSTOM_AI_API_KEY = stringPreferencesKey("custom_ai_api_key")
    }

    val openAiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    val claudeApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLAUDE_API_KEY] ?: ""
    }

    val backendUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL] ?: "https://api.digestit.app"
    }

    val aiProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_PROVIDER] ?: "claude"
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val customAiBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AI_BASE_URL] ?: ""
    }

    val customAiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AI_MODEL] ?: ""
    }

    val customAiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AI_API_KEY] ?: ""
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { it[KEY_OPENAI_API_KEY] = key }
    }

    suspend fun setClaudeApiKey(key: String) {
        context.dataStore.edit { it[KEY_CLAUDE_API_KEY] = key }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[KEY_BACKEND_URL] = url }
    }

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setCustomAiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_BASE_URL] = url }
    }

    suspend fun setCustomAiModel(model: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_MODEL] = model }
    }

    suspend fun setCustomAiApiKey(key: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_API_KEY] = key }
    }
}
