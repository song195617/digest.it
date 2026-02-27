package com.digestit.di

import com.digestit.data.repository.ChatRepository
import com.digestit.data.repository.EpisodeRepository
import com.digestit.domain.repository.IChatRepository
import com.digestit.domain.repository.IEpisodeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEpisodeRepository(impl: EpisodeRepository): IEpisodeRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepository): IChatRepository
}
