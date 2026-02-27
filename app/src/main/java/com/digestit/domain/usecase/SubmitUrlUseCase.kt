package com.digestit.domain.usecase

import com.digestit.domain.model.Platform
import com.digestit.domain.model.ProcessingJob
import com.digestit.domain.repository.IEpisodeRepository
import javax.inject.Inject

class SubmitUrlUseCase @Inject constructor(
    private val repository: IEpisodeRepository,
    private val detectPlatform: DetectPlatformUseCase
) {
    sealed class Result {
        data class Success(val job: ProcessingJob) : Result()
        data class UnsupportedPlatform(val url: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(rawUrl: String): Result {
        val parsed = detectPlatform(rawUrl)
        if (parsed.platform == Platform.UNKNOWN) {
            return Result.UnsupportedPlatform(rawUrl)
        }
        return try {
            val job = repository.submitUrl(parsed.normalizedUrl)
            Result.Success(job)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
}
