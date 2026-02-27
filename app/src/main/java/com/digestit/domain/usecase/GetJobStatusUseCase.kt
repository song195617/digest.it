package com.digestit.domain.usecase

import com.digestit.domain.model.ProcessingJob
import com.digestit.domain.repository.IEpisodeRepository
import javax.inject.Inject

class GetJobStatusUseCase @Inject constructor(
    private val repository: IEpisodeRepository
) {
    suspend operator fun invoke(jobId: String): ProcessingJob =
        repository.getJobStatus(jobId)
}
