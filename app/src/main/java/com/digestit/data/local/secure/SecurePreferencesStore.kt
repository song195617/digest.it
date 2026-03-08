package com.digestit.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferencesStore @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val PREFS_NAME = "secure_user_prefs"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CUSTOM_AI_API_KEY = "custom_ai_api_key"
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val claudeApiKeyState = MutableStateFlow(readString(KEY_CLAUDE_API_KEY))
    private val geminiApiKeyState = MutableStateFlow(readString(KEY_GEMINI_API_KEY))
    private val customAiApiKeyState = MutableStateFlow(readString(KEY_CUSTOM_AI_API_KEY))
    private val deepseekApiKeyState = MutableStateFlow(readString(KEY_DEEPSEEK_API_KEY))

    val claudeApiKey: StateFlow<String> = claudeApiKeyState
    val geminiApiKey: StateFlow<String> = geminiApiKeyState
    val customAiApiKey: StateFlow<String> = customAiApiKeyState
    val deepseekApiKey: StateFlow<String> = deepseekApiKeyState

    fun setClaudeApiKey(value: String) = writeString(KEY_CLAUDE_API_KEY, value, claudeApiKeyState)

    fun setGeminiApiKey(value: String) = writeString(KEY_GEMINI_API_KEY, value, geminiApiKeyState)

    fun setCustomAiApiKey(value: String) = writeString(KEY_CUSTOM_AI_API_KEY, value, customAiApiKeyState)

    fun setDeepseekApiKey(value: String) = writeString(KEY_DEEPSEEK_API_KEY, value, deepseekApiKeyState)

    private fun readString(key: String): String = prefs.getString(key, "") ?: ""

    private fun writeString(
        key: String,
        value: String,
        state: MutableStateFlow<String>
    ) {
        prefs.edit().putString(key, value).apply()
        state.value = value
    }
}
