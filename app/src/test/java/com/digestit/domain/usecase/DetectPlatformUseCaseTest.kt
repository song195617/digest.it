package com.digestit.domain.usecase

import com.digestit.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectPlatformUseCaseTest {
    private val useCase = DetectPlatformUseCase()

    @Test
    fun `normalizes bilibili url`() {
        val parsed = useCase("https://www.bilibili.com/video/BV1xx411c7mD?p=1")

        assertEquals(Platform.BILIBILI, parsed.platform)
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", parsed.normalizedUrl)
    }

    @Test
    fun `converts xiaoyuzhou deep link to web url`() {
        val parsed = useCase("xiaoyuzhou://episode/64c7f123abcd123456789abc")

        assertEquals(Platform.XIAOYUZHOU, parsed.platform)
        assertEquals(
            "https://www.xiaoyuzhoufm.com/episode/64c7f123abcd123456789abc",
            parsed.normalizedUrl
        )
    }

    @Test
    fun `extracts bilibili short link from share text`() {
        val parsed = useCase("【一口气了解伊朗经济-哔哩哔哩】 https://b23.tv/37gE8mi")

        assertEquals(Platform.BILIBILI, parsed.platform)
        assertEquals("https://b23.tv/37gE8mi", parsed.normalizedUrl)
    }

    @Test
    fun `trims trailing punctuation after extracted url`() {
        val parsed = useCase("看这个：https://b23.tv/37gE8mi】")

        assertEquals(Platform.BILIBILI, parsed.platform)
        assertEquals("https://b23.tv/37gE8mi", parsed.normalizedUrl)
    }
}
