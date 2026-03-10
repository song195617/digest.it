package com.digestit.domain.usecase

import com.digestit.domain.model.Platform
import javax.inject.Inject

class DetectPlatformUseCase @Inject constructor() {

    data class ParsedUrl(
        val platform: Platform,
        val normalizedUrl: String,
        val contentId: String?
    )

    operator fun invoke(rawUrl: String): ParsedUrl {
        val original = rawUrl.trim()
        val url = extractSupportedUrl(original) ?: original
        return when {
            BILIBILI_WEB.containsMatchIn(url) -> {
                val bvid = BILIBILI_WEB.find(url)?.groupValues?.get(1)
                ParsedUrl(Platform.BILIBILI, "https://www.bilibili.com/video/$bvid", bvid)
            }
            BILIBILI_SHORT.containsMatchIn(url) -> {
                ParsedUrl(Platform.BILIBILI, url, null)
            }
            BILIBILI_DEEP_LINK.containsMatchIn(url) -> {
                val aid = BILIBILI_DEEP_LINK.find(url)?.groupValues?.get(1)
                ParsedUrl(Platform.BILIBILI, "https://www.bilibili.com/video/av$aid", aid)
            }
            XIAOYUZHOU_WEB.containsMatchIn(url) -> {
                val episodeId = XIAOYUZHOU_WEB.find(url)?.groupValues?.get(1)
                ParsedUrl(Platform.XIAOYUZHOU, url, episodeId)
            }
            XIAOYUZHOU_DEEP_LINK.containsMatchIn(url) -> {
                val episodeId = XIAOYUZHOU_DEEP_LINK.find(url)?.groupValues?.get(1)
                ParsedUrl(
                    Platform.XIAOYUZHOU,
                    "https://www.xiaoyuzhoufm.com/episode/$episodeId",
                    episodeId
                )
            }
            else -> ParsedUrl(Platform.UNKNOWN, url, null)
        }
    }

    companion object {
        private val SHARE_URL = Regex("""(?:https?|bilibili|xiaoyuzhou)://[^\s]+""")
        private val BILIBILI_WEB = Regex("""bilibili\.com/video/(BV[\w]+)""")
        private val BILIBILI_SHORT = Regex("""b23\.tv/""")
        private val BILIBILI_DEEP_LINK = Regex("""bilibili://video/(\d+)""")
        private val XIAOYUZHOU_WEB = Regex("""xiaoyuzhoufm\.com/episode/([a-f0-9]+)""")
        private val XIAOYUZHOU_DEEP_LINK = Regex("""xiaoyuzhou://episode/([a-f0-9]+)""")
        private const val TRAILING_PUNCTUATION = ".,;:!?)]}>'\"，。；：！？）】》」』、"

        fun extractSupportedUrl(rawText: String): String? {
            val trimmed = rawText.trim()
            if (trimmed.isBlank()) return null
            return SHARE_URL.find(trimmed)
                ?.value
                ?.trimEnd { it in TRAILING_PUNCTUATION }
                ?.takeIf { it.isNotBlank() }
        }
    }
}
