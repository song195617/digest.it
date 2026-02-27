package com.digestit.domain.model

import java.time.Instant

enum class Platform(val displayName: String) {
    BILIBILI("哔哩哔哩"),
    XIAOYUZHOU("小宇宙"),
    UNKNOWN("未知平台")
}

enum class ProcessingStatus {
    QUEUED,
    EXTRACTING,
    TRANSCRIBING,
    SUMMARIZING,
    COMPLETED,
    FAILED
}

data class Episode(
    val id: String,
    val platform: Platform,
    val originalUrl: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val durationSeconds: Int,
    val createdAt: Instant,
    val processingStatus: ProcessingStatus,
    val errorMessage: String? = null
)

data class ProcessingJob(
    val jobId: String,
    val episodeId: String?,
    val status: ProcessingStatus,
    val progress: Float,
    val currentStep: String,
    val errorMessage: String?
)

data class Transcript(
    val episodeId: String,
    val fullText: String,
    val segments: List<TranscriptSegment>,
    val language: String,
    val wordCount: Int
)

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class Summary(
    val episodeId: String,
    val oneLiner: String,
    val keyPoints: List<String>,
    val topics: List<String>,
    val highlights: List<Highlight>,
    val fullSummary: String
)

data class Highlight(
    val quote: String,
    val timestampMs: Long,
    val context: String
)

data class ChatSession(
    val id: String,
    val episodeId: String,
    val createdAt: Instant,
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant,
    val isStreaming: Boolean = false,
    val referencedTimestamps: List<Long> = emptyList()
)

enum class MessageRole { USER, ASSISTANT }
