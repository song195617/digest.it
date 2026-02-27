package com.digestit.data.remote.dto

import com.digestit.domain.model.*
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class SubmitUrlRequest(val url: String)

data class JobStatusResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("episode_id") val episodeId: String?,
    val status: String,
    val progress: Float,
    @SerializedName("current_step") val currentStep: String,
    @SerializedName("error_message") val errorMessage: String?
) {
    fun toDomain() = ProcessingJob(
        jobId = jobId,
        episodeId = episodeId,
        status = ProcessingStatus.valueOf(status),
        progress = progress,
        currentStep = currentStep,
        errorMessage = errorMessage
    )
}

data class EpisodeResponse(
    val id: String,
    val platform: String,
    @SerializedName("original_url") val originalUrl: String,
    val title: String,
    val author: String,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("processing_status") val processingStatus: String,
    @SerializedName("error_message") val errorMessage: String?
) {
    fun toDomain() = Episode(
        id = id,
        platform = Platform.valueOf(platform),
        originalUrl = originalUrl,
        title = title,
        author = author,
        coverUrl = coverUrl,
        durationSeconds = durationSeconds,
        createdAt = Instant.parse(createdAt),
        processingStatus = ProcessingStatus.valueOf(processingStatus),
        errorMessage = errorMessage
    )
}

data class TranscriptSegmentDto(
    @SerializedName("start_ms") val startMs: Long,
    @SerializedName("end_ms") val endMs: Long,
    val text: String
)

data class TranscriptResponse(
    @SerializedName("episode_id") val episodeId: String,
    @SerializedName("full_text") val fullText: String,
    val segments: List<TranscriptSegmentDto>,
    val language: String,
    @SerializedName("word_count") val wordCount: Int
) {
    fun toDomain() = Transcript(
        episodeId = episodeId,
        fullText = fullText,
        segments = segments.map { TranscriptSegment(it.startMs, it.endMs, it.text) },
        language = language,
        wordCount = wordCount
    )
}

data class HighlightDto(
    val quote: String,
    @SerializedName("timestamp_ms") val timestampMs: Long,
    val context: String
)

data class SummaryResponse(
    @SerializedName("episode_id") val episodeId: String,
    @SerializedName("one_liner") val oneLiner: String,
    @SerializedName("key_points") val keyPoints: List<String>,
    val topics: List<String>,
    val highlights: List<HighlightDto>,
    @SerializedName("full_summary") val fullSummary: String
) {
    fun toDomain() = Summary(
        episodeId = episodeId,
        oneLiner = oneLiner,
        keyPoints = keyPoints,
        topics = topics,
        highlights = highlights.map { Highlight(it.quote, it.timestampMs, it.context) },
        fullSummary = fullSummary
    )
}

data class ChatRequest(
    @SerializedName("episode_id") val episodeId: String,
    @SerializedName("session_id") val sessionId: String?,
    val message: String
)

data class ChatStreamChunk(
    val delta: String,
    val done: Boolean,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("referenced_timestamps") val referencedTimestamps: List<Long>?
)
