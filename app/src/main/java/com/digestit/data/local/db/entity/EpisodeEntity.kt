package com.digestit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digestit.domain.model.Episode
import com.digestit.domain.model.Platform
import com.digestit.domain.model.ProcessingStatus
import java.time.Instant

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val originalUrl: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val durationSeconds: Int,
    val createdAt: Long,
    val processingStatus: String,
    val errorMessage: String?,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long? = null,
    val audioUrl: String? = null,
) {
    fun toDomain() = Episode(
        id = id,
        platform = Platform.valueOf(platform),
        originalUrl = originalUrl,
        title = title,
        author = author,
        coverUrl = coverUrl,
        durationSeconds = durationSeconds,
        createdAt = Instant.ofEpochMilli(createdAt),
        processingStatus = ProcessingStatus.valueOf(processingStatus),
        errorMessage = errorMessage,
        isFavorite = isFavorite,
        lastOpenedAt = lastOpenedAt?.let(Instant::ofEpochMilli),
        audioUrl = audioUrl,
    )

    companion object {
        fun fromDomain(e: Episode) = EpisodeEntity(
            id = e.id,
            platform = e.platform.name,
            originalUrl = e.originalUrl,
            title = e.title,
            author = e.author,
            coverUrl = e.coverUrl,
            durationSeconds = e.durationSeconds,
            createdAt = e.createdAt.toEpochMilli(),
            processingStatus = e.processingStatus.name,
            errorMessage = e.errorMessage,
            isFavorite = e.isFavorite,
            lastOpenedAt = e.lastOpenedAt?.toEpochMilli(),
            audioUrl = e.audioUrl,
        )
    }
}
