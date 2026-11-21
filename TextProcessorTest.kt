package org.nationalsounds.app.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {

    @Test
    fun splitsSentencesAndAssignsPunctuationPauses() {
        val segments = TextProcessor.segment("بدأت الرحلة. هل وصلنا؟ نعم!", 450, 1f)
        assertEquals(3, segments.size)
        assertEquals("بدأت الرحلة.", segments[0].text)
        assertEquals(650, segments[0].pauseAfterMs)
        assertEquals(700, segments[1].pauseAfterMs)
        assertEquals(0, segments[2].pauseAfterMs) // last segment never pauses
    }

    @Test
    fun paragraphBreakAddsExtraPause() {
        val segments = TextProcessor.segment("الجملة الأولى.\nالجملة الثانية.", 450, 1f)
        assertEquals(2, segments.size)
        assertEquals(650 + 450, segments[0].pauseAfterMs)
    }

    @Test
    fun pauseScaleIsApplied() {
        val segments = TextProcessor.segment("الجملة الأولى. الجملة الثانية.", 450, 2f)
        assertEquals(1300, segments[0].pauseAfterMs)
    }

    @Test
    fun decimalNumbersDoNotSplitSentences() {
        val segments = TextProcessor.segment("بلغت النسبة 3.5 بالمئة في المنطقة.", 450, 1f)
        assertEquals(1, segments.size)
    }

    @Test
    fun longSentencesAreSplitWithinTheLimit() {
        val sentence = (1..40).joinToString("، ") { "كلمة$it" }
        val segments = TextProcessor.segment(sentence, 100, 1f)
        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.text.length <= 100 })
    }

    @Test
    fun emptyInputGivesNoSegments() {
        assertTrue(TextProcessor.segment("   \n  ", 450, 1f).isEmpty())
    }

    @Test
    fun analyzeCountsWordsAndLatinLetters() {
        val stats = TextProcessor.analyze("تعيش الأسود في السافانا abc", 1f, 1f)
        assertEquals(5, stats.words)
        assertEquals(3, stats.latinLetters)
    }

    @Test
    fun stripDiacriticsRemovesAllMarks() {
        assertEquals("كتب", ArabicText.stripDiacritics("كَتَبَ"))
        assertFalse(ArabicText.hasDiacritics("كتب"))
        assertTrue(ArabicText.hasDiacritics("كَتَبَ"))
    }

    @Test
    fun chunkForRemoteKeepsAllTextInOrder() {
        val text = (1..200).joinToString(" ") { "جملة رقم $it." }
        val chunks = TextProcessor.chunkForRemote(text, 300)
        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString(""))
    }
}
