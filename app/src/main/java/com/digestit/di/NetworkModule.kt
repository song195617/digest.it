package com.digestit.di

import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.data.remote.api.DigestItApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(prefs: UserPreferencesDataStore): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Rewrite base URL dynamically so settings changes take effect immediately
                val backendUrl = runBlocking { prefs.backendUrl.first() }.trimEnd('/')
                val parsed = android.net.Uri.parse(backendUrl)
                val scheme = parsed.scheme ?: "http"
                val host = parsed.host ?: "localhost"
                // port() rejects -1; derive default from scheme when not explicit
                val port = when {
                    parsed.port != -1 -> parsed.port
                    scheme == "https" -> 443
                    else -> 80
                }
                val originalUrl = chain.request().url
                val newUrl = originalUrl.newBuilder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(newUrl).build())
            }
            .addInterceptor { chain ->
                val provider = runBlocking { prefs.aiProvider.first() }
                val aiApiKey = when (provider) {
                    "gemini"            -> runBlocking { prefs.geminiApiKey.first() }
                    "openai_compatible" -> runBlocking { prefs.customAiApiKey.first() }
                    "deepseek"          -> runBlocking { prefs.deepseekApiKey.first() }
                    else                -> runBlocking { prefs.claudeApiKey.first() }
                }
                // DeepSeek is OpenAI-compatible; tell the backend to treat it that way
                val backendProvider = if (provider == "deepseek") "openai_compatible" else provider
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("X-API-Key", aiApiKey)
                    .addHeader("X-AI-Provider", backendProvider)
                when (provider) {
                    "openai_compatible" -> {
                        val model   = runBlocking { prefs.customAiModel.first() }
                        val baseUrl = runBlocking { prefs.customAiBaseUrl.first() }
                        if (model.isNotBlank())   requestBuilder.addHeader("X-AI-Model", model)
                        if (baseUrl.isNotBlank()) requestBuilder.addHeader("X-AI-Base-URL", baseUrl)
                    }
                    "deepseek" -> {
                        val model   = runBlocking { prefs.deepseekModel.first() }
                        val baseUrl = runBlocking { prefs.deepseekBaseUrl.first() }
                        if (model.isNotBlank())   requestBuilder.addHeader("X-AI-Model", model)
                        if (baseUrl.isNotBlank()) requestBuilder.addHeader("X-AI-Base-URL", baseUrl)
                    }
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        // Base URL is a placeholder; the dynamic URL interceptor rewrites it per request
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): DigestItApiService =
        retrofit.create(DigestItApiService::class.java)
}
