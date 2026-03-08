package com.digestit.data.repository

import com.digestit.data.local.db.dao.EpisodeDao
import com.digestit.data.local.db.entity.EpisodeEntity
import com.digestit.data.local.db.entity.TranscriptEntity
import com.digestit.data.remote.api.DigestItApiService
import com.digestit.data.remote.dto.SubmitUrlRequest
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingJob
import com.digestit.domain.model.Summary
import com.digestit.domain.model.Transcript
import com.digestit.domain.repository.IEpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EpisodeRepository @Inject constructor(
    private val api: DigestItApiService,
    private val episodeDao: EpisodeDao
) : IEpisodeRepository {

    override fun getAllEpisodes(): Flow<List<Episode>> =
        episodeDao.getAllEpisodes().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshEpisodes() {
        try {
            val remoteEpisodes = api.getEpisodes()
            remoteEpisodes.forEach { episodeDao.insertEpisode(EpisodeEntity.fromDomain(it.toDomain())) }
        } catch (_: Exception) {
        }
    }

    override suspend fun getEpisode(episodeId: String): Episode? {
        return try {
            val remote = api.getEpisode(episodeId).toDomain()
            episodeDao.insertEpisode(EpisodeEntity.fromDomain(remote))
            remote
        } catch (_: Exception) {
            episodeDao.getEpisode(episodeId)?.toDomain()
        }
    }

    override suspend fun submitUrl(url: String): ProcessingJob {
        val response = api.submitUrl(SubmitUrlRequest(url))
        response.episodeId?.let { episodeId ->
            try {
                val episode = api.getEpisode(episodeId).toDomain()
                episodeDao.insertEpisode(EpisodeEntity.fromDomain(episode))
            } catch (_: Exception) {
            }
        }
        return response.toDomain()
    }

    override suspend fun getJobStatus(jobId: String): ProcessingJob {
        val response = api.getJobStatus(jobId)
        response.episodeId?.let { episodeId ->
            try {
                val episode = api.getEpisode(episodeId).toDomain()
                episodeDao.insertEpisode(EpisodeEntity.fromDomain(episode))
            } catch (_: Exception) {
            }
        }
        return response.toDomain()
    }

    override suspend fun getTranscript(episodeId: String): Transcript? {
        val cached = episodeDao.getTranscript(episodeId)
        if (cached != null) return cached.toDomain()
        return try {
            val remote = api.getTranscript(episodeId).toDomain()
            episodeDao.insertTranscript(TranscriptEntity.fromDomain(remote))
            remote
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getSummary(episodeId: String): Summary? {
        return try {
            api.getSummary(episodeId).toDomain()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteEpisode(episodeId: String) {
        api.deleteEpisode(episodeId)
        episodeDao.deleteEpisode(episodeId)
    }
}
