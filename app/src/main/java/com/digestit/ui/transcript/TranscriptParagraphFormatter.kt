package com.digestit.ui.transcript

import com.digestit.domain.model.TranscriptSegment
import kotlin.math.abs

data class TranscriptParagraphUiModel(
    val paragraphStartMs: Long,
    val paragraphEndMs: Long,
    val sentences: List<TranscriptSegment>,
)

internal object TranscriptParagraphFormatter {
    private const val MIN_PARAGRAPH_CHARS = 20
    private const val TARGET_PARAGRAPH_CHARS = 64
    private const val MAX_PARAGRAPH_CHARS = 96
    private const val MEDIUM_PAUSE_MS = 1_200L
    private const val LONG_PAUSE_MS = 2_400L
    private val strongEndings = setOf('。', '！', '？', '；', '…', '.', '!', '?', ';')
    private val softEndings = setOf('，', '、', ',', ':', '：')

    fun buildParagraphs(segments: List<TranscriptSegment>): List<TranscriptParagraphUiModel> {
        if (segments.isEmpty()) return emptyList()

        val paragraphs = mutableListOf<TranscriptParagraphUiModel>()
        val current = mutableListOf<TranscriptSegment>()
        var currentChars = 0

        segments.forEachIndexed { index, segment ->
            current += segment
            currentChars += normalizedSentenceText(segment.text).length

            val next = segments.getOrNull(index + 1)
            if (shouldFlushParagraph(current, currentChars, segment, next)) {
                paragraphs += TranscriptParagraphUiModel(
                    paragraphStartMs = current.first().startMs,
                    paragraphEndMs = current.last().endMs,
                    sentences = current.toList(),
                )
                current.clear()
                currentChars = 0
            }
        }

        return paragraphs
    }

    fun findSentenceForTimestamp(segments: List<TranscriptSegment>, timestampMs: Long): TranscriptSegment? {
        if (segments.isEmpty()) return null
        return segments.firstOrNull { timestampMs in it.startMs..it.endMs }
            ?: segments.minByOrNull { abs(it.startMs - timestampMs) }
    }

    fun buildSentenceParagraphMap(paragraphs: List<TranscriptParagraphUiModel>): Map<Long, Long> {
        return buildMap {
            paragraphs.forEach { paragraph ->
                paragraph.sentences.forEach { sentence ->
                    put(sentence.startMs, paragraph.paragraphStartMs)
                }
            }
        }
    }

    private fun shouldFlushParagraph(
        current: List<TranscriptSegment>,
        currentChars: Int,
        currentSegment: TranscriptSegment,
        nextSegment: TranscriptSegment?,
    ): Boolean {
        if (nextSegment == null) return true

        val currentText = normalizedSentenceText(currentSegment.text)
        val endsStrong = currentText.lastOrNull() in strongEndings
        val endsSoft = currentText.lastOrNull() in softEndings
        val gapMs = (nextSegment.startMs - currentSegment.endMs).coerceAtLeast(0L)

        if (currentChars >= MAX_PARAGRAPH_CHARS) return true
        if (currentText.contains("\n") && currentChars >= MIN_PARAGRAPH_CHARS) return true
        if (gapMs >= LONG_PAUSE_MS && currentChars >= MIN_PARAGRAPH_CHARS) return true
        if (endsStrong && currentChars >= TARGET_PARAGRAPH_CHARS) return true
        if (endsStrong && gapMs >= MEDIUM_PAUSE_MS && currentChars >= MIN_PARAGRAPH_CHARS) return true
        if (endsSoft && currentChars >= TARGET_PARAGRAPH_CHARS) return true

        return false
    }

    fun normalizedSentenceText(text: String): String = text.trim().replace("\n", "")
}
