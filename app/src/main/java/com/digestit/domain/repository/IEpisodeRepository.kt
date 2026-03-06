package com.digestit.domain.repository

import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingJob
import com.digestit.domain.model.Summary
import com.digestit.domain.model.Transcript
import kotlinx.coroutines.flow.Flow

interface IEpisodeRepository {
    fun getAllEpisodes(): Flow<List<Episode>>
    suspend fun refreshEpisodes()
    suspend fun getEpisode(episodeId: String): Episode?
    suspend fun submitUrl(url: String): ProcessingJob
    suspend fun getJobStatus(jobId: String): ProcessingJob
    suspend fun getTranscript(episodeId: String): Transcript?
    suspend fun getSummary(episodeId: String): Summary?
    suspend fun deleteEpisode(episodeId: String)
}
