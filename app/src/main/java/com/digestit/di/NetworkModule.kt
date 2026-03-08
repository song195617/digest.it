package com.digestit.di

import com.digestit.BuildConfig
import com.digestit.data.local.datastore.UserPreferencesDataStore
import com.digestit.data.remote.api.DigestItApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val snapshot = prefs.currentNetworkSettings.value
                val backendUrl = snapshot.backendUrl.trimEnd('/')
                val parsed = android.net.Uri.parse(backendUrl)
                val scheme = parsed.scheme ?: "http"
                val host = parsed.host ?: "localhost"
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
                val snapshot = prefs.currentNetworkSettings.value
                val provider = snapshot.aiProvider
                val aiApiKey = when (provider) {
                    "gemini" -> snapshot.geminiApiKey
                    "openai_compatible" -> snapshot.customAiApiKey
                    "deepseek" -> snapshot.deepseekApiKey
                    else -> snapshot.claudeApiKey
                }
                val backendProvider = if (provider == "deepseek") "openai_compatible" else provider
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("X-AI-Provider", backendProvider)
                if (aiApiKey.isNotBlank()) {
                    requestBuilder.addHeader("X-API-Key", aiApiKey)
                }
                when (provider) {
                    "openai_compatible" -> {
                        if (snapshot.customAiModel.isNotBlank()) {
                            requestBuilder.addHeader("X-AI-Model", snapshot.customAiModel)
                        }
                        if (snapshot.customAiBaseUrl.isNotBlank()) {
                            requestBuilder.addHeader("X-AI-Base-URL", snapshot.customAiBaseUrl)
                        }
                    }
                    "deepseek" -> {
                        if (snapshot.deepseekModel.isNotBlank()) {
                            requestBuilder.addHeader("X-AI-Model", snapshot.deepseekModel)
                        }
                        if (snapshot.deepseekBaseUrl.isNotBlank()) {
                            requestBuilder.addHeader("X-AI-Base-URL", snapshot.deepseekBaseUrl)
                        }
                    }
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
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
