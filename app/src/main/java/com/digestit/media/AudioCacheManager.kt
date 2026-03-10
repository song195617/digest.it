package com.digestit.media

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_CACHE_BYTES: Long = 1024L * 1024L * 1024L
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "audio_cache").apply { mkdirs() }
    }

    private val databaseProvider: StandaloneDatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    private val cache: SimpleCache by lazy {
        SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), databaseProvider)
    }

    fun buildDataSourceFactory(playbackContext: Context): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(playbackContext)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCacheSizeBytes(): Long = runCatching { cache.cacheSpace }.getOrElse { cacheDirSize(cacheDir) }

    fun clearCache() {
        val keys = cache.keys.toList()
        keys.forEach(cache::removeResource)
    }

    private fun cacheDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
