package com.digestit.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioUrlNormalizerTest {

    @Test
    fun `removes legacy api prefix from relative audio path`() {
        assertEquals("/v1/episodes/ep-1/audio", normalizeAudioPath("/api/v1/episodes/ep-1/audio"))
    }

    @Test
    fun `keeps current v1 audio path unchanged`() {
        assertEquals("/v1/episodes/ep-1/audio", normalizeAudioPath("/v1/episodes/ep-1/audio"))
    }

    @Test
    fun `returns null for blank audio path`() {
        assertNull(normalizeAudioPath("   "))
    }
}
