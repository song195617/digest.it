package com.digestit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digestit.domain.model.Highlight
import com.digestit.domain.model.Summary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val episodeId: String,
    val oneLiner: String,
    val keyPointsJson: String,
    val topicsJson: String,
    val highlightsJson: String,
    val fullSummary: String,
) {
    fun toDomain(): Summary {
        val gson = Gson()
        val listType = object : TypeToken<List<String>>() {}.type
        val highlightType = object : TypeToken<List<Highlight>>() {}.type
        return Summary(
            episodeId = episodeId,
            oneLiner = oneLiner,
            keyPoints = gson.fromJson(keyPointsJson, listType) ?: emptyList<String>(),
            topics = gson.fromJson(topicsJson, listType) ?: emptyList<String>(),
            highlights = gson.fromJson(highlightsJson, highlightType) ?: emptyList<Highlight>(),
            fullSummary = fullSummary,
        )
    }

    companion object {
        fun fromDomain(summary: Summary): SummaryEntity {
            val gson = Gson()
            return SummaryEntity(
                episodeId = summary.episodeId,
                oneLiner = summary.oneLiner,
                keyPointsJson = gson.toJson(summary.keyPoints),
                topicsJson = gson.toJson(summary.topics),
                highlightsJson = gson.toJson(summary.highlights),
                fullSummary = summary.fullSummary,
            )
        }
    }
}
