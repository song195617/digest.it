package com.digestit.data.repository

import com.digestit.data.local.db.dao.ChatDao
import com.digestit.data.local.db.dao.EpisodeDao
import com.digestit.data.local.db.entity.EpisodeEntity
import com.digestit.data.local.db.entity.SummaryEntity
import com.digestit.data.local.db.entity.TranscriptEntity
import com.digestit.data.remote.api.DigestItApiService
import com.digestit.data.remote.dto.SubmitUrlRequest
import com.digestit.domain.model.BackendHealth
import com.digestit.domain.model.Episode
import com.digestit.domain.model.ProcessingJob
import com.digestit.domain.model.ProcessingStatus
import com.digestit.domain.model.SearchResult
import com.digestit.domain.model.SearchSourceType
import com.digestit.domain.model.Summary
import com.digestit.domain.model.Transcript
import com.digestit.domain.repository.IEpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class EpisodeRepository @Inject constructor(
    private val api: DigestItApiService,
    private val episodeDao: EpisodeDao,
    private val chatDao: ChatDao,
) : IEpisodeRepository {

    override fun getAllEpisodes(): Flow<List<Episode>> =
        episodeDao.getAllEpisodes().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshEpisodes() {
        try {
            val remoteEpisodes = api.getEpisodes().map { it.toDomain() }
            remoteEpisodes.forEach { upsertEpisode(it) }
        } catch (_: Exception) {
        }
    }

    override suspend fun getEpisode(episodeId: String): Episode? {
        return try {
            val remote = api.getEpisode(episodeId).toDomain()
            upsertEpisode(remote)
        } catch (_: Exception) {
            episodeDao.getEpisode(episodeId)?.toDomain()
        }
    }

    override suspend fun submitUrl(url: String): ProcessingJob {
        val response = api.submitUrl(SubmitUrlRequest(url))
        response.episodeId?.let { episodeId -> refreshEpisodeFromRemote(episodeId) }
        return response.toDomain()
    }

    override suspend fun retryEpisode(episodeId: String): ProcessingJob {
        val response = api.retryEpisode(episodeId)
        refreshEpisodeFromRemote(episodeId)
        episodeDao.deleteTranscript(episodeId)
        episodeDao.deleteSummary(episodeId)
        chatDao.clearHistory(episodeId)
        return response.toDomain()
    }

    override suspend fun getJobStatus(jobId: String): ProcessingJob {
        val response = api.getJobStatus(jobId)
        response.episodeId?.let { episodeId -> refreshEpisodeFromRemote(episodeId) }
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
        episodeDao.getSummary(episodeId)?.let { return it.toDomain() }
        return try {
            val remote = api.getSummary(episodeId).toDomain()
            episodeDao.insertSummary(SummaryEntity.fromDomain(remote))
            remote
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun testBackendHealth(): BackendHealth = api.getHealth().toDomain()

    override suspend fun searchContent(query: String): List<SearchResult> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return emptyList()

        val episodes = episodeDao.getAllEpisodesOnce()
        episodes
            .filter { it.processingStatus == ProcessingStatus.COMPLETED.name }
            .forEach { episode ->
                if (episodeDao.getSummary(episode.id) == null) {
                    runCatching { api.getSummary(episode.id).toDomain() }
                        .getOrNull()
                        ?.let { remoteSummary -> episodeDao.insertSummary(SummaryEntity.fromDomain(remoteSummary)) }
                }
                if (episodeDao.getTranscript(episode.id) == null) {
                    runCatching { api.getTranscript(episode.id).toDomain() }
                        .getOrNull()
                        ?.let { remoteTranscript -> episodeDao.insertTranscript(TranscriptEntity.fromDomain(remoteTranscript)) }
                }
            }
        val summaries = episodeDao.getAllSummaries().associateBy { it.episodeId }
        val transcripts = episodeDao.getAllTranscripts().associateBy { it.episodeId }

        return episodes.mapNotNull { episodeEntity ->
            val episode = episodeEntity.toDomain()
            val summary = summaries[episode.id]?.toDomain()
            val transcript = transcripts[episode.id]?.toDomain()

            when {
                titleMatches(episode, normalizedQuery) -> SearchResult(
                    episodeId = episode.id,
                    title = episode.title,
                    subtitle = episode.author.ifBlank { episode.platform.displayName },
                    previewText = episode.originalUrl,
                    sourceType = SearchSourceType.TITLE,
                )

                summary != null && summaryMatches(summary, normalizedQuery) -> SearchResult(
                    episodeId = episode.id,
                    title = episode.title,
                    subtitle = "摘要",
                    previewText = buildSummaryPreview(summary, normalizedQuery),
                    sourceType = SearchSourceType.SUMMARY,
                )

                transcript != null -> {
                    val segment = transcript.segments.firstOrNull { it.text.contains(normalizedQuery, ignoreCase = true) }
                    segment?.let {
                        SearchResult(
                            episodeId = episode.id,
                            title = episode.title,
                            subtitle = "全文",
                            previewText = excerpt(it.text, normalizedQuery),
                            sourceType = SearchSourceType.TRANSCRIPT,
                            timestampMs = it.startMs,
                        )
                    }
                }

                else -> null
            }
        }.sortedWith(
            compareBy<SearchResult> { searchPriority(it.sourceType) }
                .thenByDescending { result ->
                    episodes.firstOrNull { it.id == result.episodeId }?.isFavorite == true
                }
                .thenByDescending { result ->
                    episodes.firstOrNull { it.id == result.episodeId }?.lastOpenedAt ?: Long.MIN_VALUE
                }
                .thenByDescending { result ->
                    episodes.firstOrNull { it.id == result.episodeId }?.createdAt ?: Long.MIN_VALUE
                }
        )
    }

    override suspend fun setFavorite(episodeId: String, isFavorite: Boolean) {
        episodeDao.setFavorite(episodeId, isFavorite)
    }

    override suspend fun markEpisodeOpened(episodeId: String) {
        episodeDao.updateLastOpenedAt(episodeId, Instant.now().toEpochMilli())
    }

    override suspend fun deleteEpisode(episodeId: String) {
        // Delete locally first so the UI updates immediately regardless of backend result
        episodeDao.deleteTranscript(episodeId)
        episodeDao.deleteSummary(episodeId)
        chatDao.clearHistory(episodeId)
        episodeDao.deleteEpisode(episodeId)
        api.deleteEpisode(episodeId)
    }

    private suspend fun refreshEpisodeFromRemote(episodeId: String): Episode? {
        return try {
            val remote = api.getEpisode(episodeId).toDomain()
            upsertEpisode(remote)
        } catch (_: Exception) {
            episodeDao.getEpisode(episodeId)?.toDomain()
        }
    }

    private suspend fun upsertEpisode(remote: Episode): Episode {
        val local = episodeDao.getEpisode(remote.id)?.toDomain()
        val merged = remote.copy(
            isFavorite = local?.isFavorite ?: remote.isFavorite,
            lastOpenedAt = local?.lastOpenedAt ?: remote.lastOpenedAt,
        )
        episodeDao.insertEpisode(EpisodeEntity.fromDomain(merged))
        return merged
    }

    private fun searchPriority(type: SearchSourceType): Int = when (type) {
        SearchSourceType.TITLE -> 0
        SearchSourceType.SUMMARY -> 1
        SearchSourceType.TRANSCRIPT -> 2
    }

    private fun titleMatches(episode: Episode, normalizedQuery: String): Boolean {
        return episode.title.contains(normalizedQuery, ignoreCase = true) ||
            episode.author.contains(normalizedQuery, ignoreCase = true) ||
            episode.platform.displayName.contains(normalizedQuery, ignoreCase = true)
    }

    private fun summaryMatches(summary: Summary, normalizedQuery: String): Boolean {
        return summary.oneLiner.contains(normalizedQuery, ignoreCase = true) ||
            summary.fullSummary.contains(normalizedQuery, ignoreCase = true) ||
            summary.keyPoints.any { it.contains(normalizedQuery, ignoreCase = true) } ||
            summary.highlights.any {
                it.quote.contains(normalizedQuery, ignoreCase = true) ||
                    it.context.contains(normalizedQuery, ignoreCase = true)
            }
    }

    private fun buildSummaryPreview(summary: Summary, normalizedQuery: String): String {
        val candidate = when {
            summary.oneLiner.contains(normalizedQuery, ignoreCase = true) -> summary.oneLiner
            summary.fullSummary.contains(normalizedQuery, ignoreCase = true) -> summary.fullSummary
            else -> summary.keyPoints.firstOrNull { it.contains(normalizedQuery, ignoreCase = true) }
                ?: summary.highlights.firstOrNull {
                    it.quote.contains(normalizedQuery, ignoreCase = true) ||
                        it.context.contains(normalizedQuery, ignoreCase = true)
                }?.let { it.quote.ifBlank { it.context } }
                ?: summary.oneLiner
        }
        return excerpt(candidate, normalizedQuery)
    }

    private fun excerpt(text: String, normalizedQuery: String, radius: Int = 36): String {
        if (text.isBlank()) return ""
        val matchIndex = text.lowercase().indexOf(normalizedQuery)
        if (matchIndex < 0) return text.take(120)
        val start = (matchIndex - radius).coerceAtLeast(0)
        val end = (matchIndex + normalizedQuery.length + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return prefix + text.substring(start, end).trim() + suffix
    }
}
