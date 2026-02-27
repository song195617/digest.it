package com.digestit.data.remote.api

import com.digestit.data.remote.dto.*
import retrofit2.http.*

interface DigestItApiService {

    @POST("v1/jobs")
    suspend fun submitUrl(@Body request: SubmitUrlRequest): JobStatusResponse

    @GET("v1/jobs/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): JobStatusResponse

    @GET("v1/episodes")
    suspend fun getEpisodes(): List<EpisodeResponse>

    @GET("v1/episodes/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: String): EpisodeResponse

    @GET("v1/transcripts/{episodeId}")
    suspend fun getTranscript(@Path("episodeId") episodeId: String): TranscriptResponse

    @GET("v1/summaries/{episodeId}")
    suspend fun getSummary(@Path("episodeId") episodeId: String): SummaryResponse

    @DELETE("v1/episodes/{episodeId}")
    suspend fun deleteEpisode(@Path("episodeId") episodeId: String)
}
