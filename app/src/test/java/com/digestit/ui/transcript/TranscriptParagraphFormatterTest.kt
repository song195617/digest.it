package com.digestit.ui.transcript

import com.digestit.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TranscriptParagraphFormatterTest {
    @Test
    fun `buildParagraphs groups nearby sentences into paragraph blocks`() {
        val paragraphs = TranscriptParagraphFormatter.buildParagraphs(
            listOf(
                TranscriptSegment(0L, 1_000L, "第一句介绍背景，"),
                TranscriptSegment(1_050L, 2_000L, "第二句继续补充，"),
                TranscriptSegment(2_050L, 3_000L, "第三句把这一段说完。"),
                TranscriptSegment(6_000L, 7_000L, "另起一段重新展开讨论。"),
            )
        )

        assertEquals(2, paragraphs.size)
        assertEquals(0L, paragraphs[0].paragraphStartMs)
        assertEquals(3, paragraphs[0].sentences.size)
        assertEquals(6_000L, paragraphs[1].paragraphStartMs)
        assertEquals(1, paragraphs[1].sentences.size)
    }

    @Test
    fun `findSentenceForTimestamp returns containing sentence or nearest fallback`() {
        val segments = listOf(
            TranscriptSegment(1_000L, 2_000L, "第一句。"),
            TranscriptSegment(3_000L, 4_000L, "第二句。"),
        )

        val containing = TranscriptParagraphFormatter.findSentenceForTimestamp(segments, 1_500L)
        val nearest = TranscriptParagraphFormatter.findSentenceForTimestamp(segments, 2_600L)

        assertNotNull(containing)
        assertEquals(1_000L, containing?.startMs)
        assertEquals(3_000L, nearest?.startMs)
    }
}
