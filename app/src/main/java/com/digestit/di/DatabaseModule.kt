package com.digestit.di

import android.content.Context
import androidx.room.Room
import com.digestit.data.local.db.AppDatabase
import com.digestit.data.local.db.dao.ChatDao
import com.digestit.data.local.db.dao.EpisodeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "digestit.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEpisodeDao(db: AppDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()
}
