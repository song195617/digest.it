package com.digestit.ui.processing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.digestit.domain.model.ProcessingStatus
import com.digestit.worker.JobPollingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessingState(
    val currentStep: String = "初始化…",
    val progress: Float = 0f,
    val status: ProcessingStatus = ProcessingStatus.QUEUED,
    val errorMessage: String? = null
)

sealed class ProcessingEffect {
    data class ProcessingComplete(val episodeId: String) : ProcessingEffect()
    object ProcessingFailed : ProcessingEffect()
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(ProcessingState())
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private val _effects = MutableStateFlow<ProcessingEffect?>(null)
    val effects: StateFlow<ProcessingEffect?> = _effects.asStateFlow()

    fun observeJob(jobId: String) {
        viewModelScope.launch {
            // Tag-based lookup so the backend jobId and WorkManager UUID don't need to match
            workManager.getWorkInfosByTagFlow(jobId).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getFloat(JobPollingWorker.KEY_PROGRESS, 0f)
                        val step = info.progress.getString(JobPollingWorker.KEY_STEP) ?: "处理中…"
                        val status = info.progress.getString(JobPollingWorker.KEY_STATUS)
                            ?.let { runCatching { ProcessingStatus.valueOf(it) }.getOrNull() }
                            ?: ProcessingStatus.QUEUED
                        _state.update { it.copy(progress = progress, currentStep = step, status = status, errorMessage = null) }
                    }
                    WorkInfo.State.ENQUEUED -> {
                        // Worker is retrying after a network error
                        _state.update { it.copy(currentStep = "连接中断，正在重试…") }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val episodeId = info.outputData.getString(JobPollingWorker.KEY_EPISODE_ID)
                        if (episodeId != null) {
                            _effects.value = ProcessingEffect.ProcessingComplete(episodeId)
                        } else {
                            _effects.value = ProcessingEffect.ProcessingFailed
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString("error") ?: "处理失败"
                        _state.update { it.copy(errorMessage = error) }
                        _effects.value = ProcessingEffect.ProcessingFailed
                    }
                    else -> {}
                }
            }
        }
    }

    fun onEffectConsumed() {
        _effects.value = null
    }
}
