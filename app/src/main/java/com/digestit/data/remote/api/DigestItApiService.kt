package com.digestit.data.remote.api

import com.digestit.data.remote.dto.BackendHealthResponse
import com.digestit.data.remote.dto.EpisodeResponse
import com.digestit.data.remote.dto.JobStatusResponse
import com.digestit.data.remote.dto.SubmitUrlRequest
import com.digestit.data.remote.dto.SummaryResponse
import com.digestit.data.remote.dto.TranscriptResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DigestItApiService {

    @POST("v1/jobs")
    suspend fun submitUrl(@Body request: SubmitUrlRequest): JobStatusResponse

    @GET("v1/jobs/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): JobStatusResponse

    @GET("v1/episodes")
    suspend fun getEpisodes(): List<EpisodeResponse>

    @GET("v1/episodes/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: String): EpisodeResponse

    @POST("v1/episodes/{episodeId}/retry")
    suspend fun retryEpisode(@Path("episodeId") episodeId: String): JobStatusResponse

    @GET("v1/episodes/{episodeId}/transcript")
    suspend fun getTranscript(@Path("episodeId") episodeId: String): TranscriptResponse

    @GET("v1/episodes/{episodeId}/summary")
    suspend fun getSummary(@Path("episodeId") episodeId: String): SummaryResponse

    @GET("v1/health")
    suspend fun getHealth(): BackendHealthResponse

    @DELETE("v1/episodes/{episodeId}")
    suspend fun deleteEpisode(@Path("episodeId") episodeId: String)
}
