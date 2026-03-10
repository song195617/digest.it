package com.digestit.ui.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.digestit.domain.model.Platform
import com.digestit.domain.model.ProcessingStatus
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

enum class ShareValidationKind {
    VALID,
    EMPTY,
    UNSUPPORTED,
    INVALID
}

data class ShareState(
    val url: String = "",
    val platform: Platform = Platform.UNKNOWN,
    val validationKind: ShareValidationKind = ShareValidationKind.EMPTY,
    val validationTitle: String = "未检测到链接",
    val validationMessage: String = "请从小宇宙或哔哩哔哩分享单条内容链接。",
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
        val trimmed = url.trim()
        val extractedUrl = DetectPlatformUseCase.extractSupportedUrl(trimmed) ?: trimmed
        val parsed = detectPlatform(trimmed)
        _state.value = when {
            trimmed.isBlank() -> ShareState(url = trimmed)
            parsed.platform != Platform.UNKNOWN -> ShareState(
                url = parsed.normalizedUrl,
                platform = parsed.platform,
                validationKind = ShareValidationKind.VALID,
                validationTitle = "已识别链接",
                validationMessage = when (parsed.platform) {
                    Platform.BILIBILI -> "检测到哔哩哔哩视频链接，可以直接开始处理。"
                    Platform.XIAOYUZHOU -> "检测到小宇宙单集链接，可以直接开始处理。"
                    Platform.UNKNOWN -> ""
                }
            )
            looksLikeUrl(extractedUrl) -> ShareState(
                url = extractedUrl,
                platform = Platform.UNKNOWN,
                validationKind = ShareValidationKind.UNSUPPORTED,
                validationTitle = "暂不支持该链接",
                validationMessage = "目前仅支持哔哩哔哩视频和小宇宙单集链接。"
            )
            else -> ShareState(
                url = extractedUrl,
                platform = Platform.UNKNOWN,
                validationKind = ShareValidationKind.INVALID,
                validationTitle = "无法解析分享内容",
                validationMessage = "请确认你分享的是完整链接，而不是纯文本或页面标题。"
            )
        }
    }

    fun onConfirm() {
        val url = _state.value.url
        if (_state.value.validationKind != ShareValidationKind.VALID) {
            _state.update { it.copy(error = it.validationMessage) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = submitUrl(url)) {
                is SubmitUrlUseCase.Result.Success -> {
                    val job = result.job
                    if (job.status != ProcessingStatus.COMPLETED && job.status != ProcessingStatus.FAILED) {
                        val request = OneTimeWorkRequestBuilder<JobPollingWorker>()
                            .setInputData(workDataOf(JobPollingWorker.KEY_JOB_ID to job.jobId))
                            .addTag(job.jobId)
                            .apply {
                                job.episodeId?.let { addTag("episode:$it") }
                            }
                            .build()
                        workManager.enqueue(request)
                    }
                    _state.update { it.copy(isSubmitting = false, isStarted = true, error = null) }
                }
                is SubmitUrlUseCase.Result.UnsupportedPlatform -> {
                    _state.update { it.copy(isSubmitting = false, error = "当前只支持小宇宙和哔哩哔哩链接") }
                }
                is SubmitUrlUseCase.Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
                }
            }
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        return runCatching {
            val uri = Uri.parse(value)
            uri.scheme in setOf("http", "https", "bilibili", "xiaoyuzhou")
        }.getOrDefault(false)
    }
}
