package com.digestit.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.digestit.data.local.db.entity.EpisodeEntity
import com.digestit.data.local.db.entity.SummaryEntity
import com.digestit.data.local.db.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes ORDER BY createdAt DESC")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes ORDER BY createdAt DESC")
    suspend fun getAllEpisodesOnce(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("UPDATE episodes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE episodes SET lastOpenedAt = :lastOpenedAt WHERE id = :id")
    suspend fun updateLastOpenedAt(id: String, lastOpenedAt: Long)

    @Query("DELETE FROM episodes WHERE id = :id")
    suspend fun deleteEpisode(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE episodeId = :episodeId")
    suspend fun getTranscript(episodeId: String): TranscriptEntity?

    @Query("SELECT * FROM transcripts")
    suspend fun getAllTranscripts(): List<TranscriptEntity>

    @Query("DELETE FROM transcripts WHERE episodeId = :episodeId")
    suspend fun deleteTranscript(episodeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE episodeId = :episodeId")
    suspend fun getSummary(episodeId: String): SummaryEntity?

    @Query("SELECT * FROM summaries")
    suspend fun getAllSummaries(): List<SummaryEntity>

    @Query("DELETE FROM summaries WHERE episodeId = :episodeId")
    suspend fun deleteSummary(episodeId: String)

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()

    @Query("DELETE FROM transcripts")
    suspend fun deleteAllTranscripts()

    @Query("DELETE FROM summaries")
    suspend fun deleteAllSummaries()
}
