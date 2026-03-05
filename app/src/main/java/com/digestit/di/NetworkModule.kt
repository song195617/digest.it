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
                val originalUrl = chain.request().url
                val newUrl = originalUrl.newBuilder()
                    .scheme(parsed.scheme ?: originalUrl.scheme)
                    .host(parsed.host ?: originalUrl.host)
                    .port(if ((parsed.port) != -1) parsed.port else -1)
                    .build()
                chain.proceed(chain.request().newBuilder().url(newUrl).build())
            }
            .addInterceptor { chain ->
                val provider = runBlocking { prefs.aiProvider.first() }
                val aiApiKey = when (provider) {
                    "gemini" -> runBlocking { prefs.geminiApiKey.first() }
                    "openai_compatible" -> runBlocking { prefs.customAiApiKey.first() }
                    else -> runBlocking { prefs.claudeApiKey.first() }
                }
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("X-API-Key", aiApiKey)
                    .addHeader("X-AI-Provider", provider)
                if (provider == "openai_compatible") {
                    val model = runBlocking { prefs.customAiModel.first() }
                    val baseUrl = runBlocking { prefs.customAiBaseUrl.first() }
                    if (model.isNotBlank()) requestBuilder.addHeader("X-AI-Model", model)
                    if (baseUrl.isNotBlank()) requestBuilder.addHeader("X-AI-Base-URL", baseUrl)
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
