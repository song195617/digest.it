package com.digestit.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.digestit.domain.model.Platform
import com.digestit.domain.usecase.DetectPlatformUseCase
import com.digestit.domain.usecase.SubmitUrlUseCase
import com.digestit.worker.JobPollingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareState(
    val url: String = "",
    val platform: Platform = Platform.UNKNOWN,
    val isSubmitting: Boolean = false,
    val isStarted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val submitUrl: SubmitUrlUseCase,
    private val detectPlatform: DetectPlatformUseCase,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    fun onUrlReceived(url: String) {
        val parsed = detectPlatform(url)
        _state.update { it.copy(url = url, platform = parsed.platform) }
    }

    fun onConfirm() {
        val url = _state.value.url
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = submitUrl(url)) {
                is SubmitUrlUseCase.Result.Success -> {
                    val jobId = result.job.jobId
                    val request = OneTimeWorkRequestBuilder<JobPollingWorker>()
                        .setInputData(workDataOf(JobPollingWorker.KEY_JOB_ID to jobId))
                        .build()
                    workManager.enqueue(request)
                    _state.update { it.copy(isSubmitting = false, isStarted = true) }
                }
                is SubmitUrlUseCase.Result.UnsupportedPlatform -> {
                    _state.update { it.copy(isSubmitting = false, error = "不支持该链接") }
                }
                is SubmitUrlUseCase.Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
                }
            }
        }
    }
}
