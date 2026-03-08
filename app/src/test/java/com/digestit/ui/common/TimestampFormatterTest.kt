package com.digestit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampFormatterTest {
    @Test
    fun `formats sub-hour timestamp as mm ss`() {
        assertEquals("12:34", formatTimestamp(754000))
    }

    @Test
    fun `formats long timestamp as hh mm ss`() {
        assertEquals("01:02:03", formatTimestamp(3723000))
    }
}
