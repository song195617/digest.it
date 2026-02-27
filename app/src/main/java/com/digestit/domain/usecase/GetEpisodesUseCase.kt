package com.digestit.domain.usecase

import com.digestit.domain.model.Episode
import com.digestit.domain.repository.IEpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEpisodesUseCase @Inject constructor(
    private val repository: IEpisodeRepository
) {
    operator fun invoke(): Flow<List<Episode>> = repository.getAllEpisodes()
}
