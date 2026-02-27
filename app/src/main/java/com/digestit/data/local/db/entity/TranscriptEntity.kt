package com.digestit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digestit.domain.model.Transcript
import com.digestit.domain.model.TranscriptSegment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey val episodeId: String,
    val fullText: String,
    val segmentsJson: String,
    val language: String,
    val wordCount: Int
) {
    fun toDomain(): Transcript {
        val type = object : TypeToken<List<TranscriptSegmentDto>>() {}.type
        val segmentDtos: List<TranscriptSegmentDto> = Gson().fromJson(segmentsJson, type)
        return Transcript(
            episodeId = episodeId,
            fullText = fullText,
            segments = segmentDtos.map { TranscriptSegment(it.startMs, it.endMs, it.text) },
            language = language,
            wordCount = wordCount
        )
    }

    data class TranscriptSegmentDto(val startMs: Long, val endMs: Long, val text: String)

    companion object {
        fun fromDomain(t: Transcript): TranscriptEntity {
            val dtos = t.segments.map { TranscriptSegmentDto(it.startMs, it.endMs, it.text) }
            return TranscriptEntity(
                episodeId = t.episodeId,
                fullText = t.fullText,
                segmentsJson = Gson().toJson(dtos),
                language = t.language,
                wordCount = t.wordCount
            )
        }
    }
}
