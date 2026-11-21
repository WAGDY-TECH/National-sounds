package org.nationalsounds.app.text

import kotlin.math.roundToInt

/** Summary numbers shown under the editor. */
data class TextStats(
    val words: Int,
    val latinLetters: Int,
    val estimatedSeconds: Int,
    val sentences: Int,
)

/**
 * Prepares a long Arabic script for narration.
 *
 * The script is cut into sentences; each sentence becomes one TTS request and a silence of a
 * length that depends on the punctuation is inserted after it. These pauses are a large part of
 * the calm, measured "documentary" delivery.
 */
object TextProcessor {

    /** Documentaries in this app are meant to be at least this long. */
    const val MIN_TARGET_SECONDS = 180

    const val DEFAULT_MAX_LEN = 450

    private const val BASE_WORDS_PER_MINUTE = 140.0
    private const val COMMA_PAUSE_MS = 250
    private const val PARAGRAPH_EXTRA_MS = 450

    data class Segment(val text: String, val pauseAfterMs: Int)

    private val enders = setOf('.', '!', '?', '؟', '…', '؛')
    private val closers = setOf('"', '”', '»', ')', '\'', '’')
    private val invisibleChars = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]")

    private fun pauseFor(c: Char): Int = when (c) {
        '؟', '?' -> 700
        '…' -> 800
        '؛' -> 450
        else -> 650
    }

    /** Cleans whitespace, tatweel and invisible direction marks. */
    fun normalize(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("ـ", "")
            .replace(invisibleChars, "")
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()

    fun analyze(raw: String, rate: Float, pauseScale: Float): TextStats {
        val text = normalize(raw)
        if (text.isEmpty()) return TextStats(0, 0, 0, 0)
        val words = text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }
        val latin = text.count { ArabicText.isLatinLetter(it) }
        val segments = segment(text, DEFAULT_MAX_LEN, pauseScale)
        val speechSeconds = words / (BASE_WORDS_PER_MINUTE * rate.coerceAtLeast(0.1f)) * 60.0
        val pauseSeconds = segments.sumOf { it.pauseAfterMs } / 1000.0
        return TextStats(words, latin, (speechSeconds + pauseSeconds).roundToInt(), segments.size)
    }

    /**
     * Splits [raw] into speakable segments no longer than [maxLen] characters.
     * The pause after the final segment is always 0.
     */
    fun segment(raw: String, maxLen: Int, pauseScale: Float): List<Segment> {
        val text = normalize(raw)
        if (text.isEmpty()) return emptyList()

        val out = ArrayList<Segment>()
        for (paragraph in text.split('\n')) {
            val p = paragraph.trim()
            if (p.isEmpty()) continue
            val before = out.size

            for ((sentence, pause) in splitSentences(p)) {
                if (sentence.none { it.isLetterOrDigit() }) {
                    // Punctuation only: fold its pause into the previous segment.
                    if (out.isNotEmpty()) {
                        val last = out.removeAt(out.lastIndex)
                        out.add(last.copy(pauseAfterMs = last.pauseAfterMs + pause))
                    }
                    continue
                }
                val pieces = splitLong(sentence, maxLen)
                pieces.forEachIndexed { i, piece ->
                    out.add(Segment(piece, if (i == pieces.lastIndex) pause else COMMA_PAUSE_MS))
                }
            }

            if (out.size > before) {
                val last = out.removeAt(out.lastIndex)
                out.add(last.copy(pauseAfterMs = last.pauseAfterMs + PARAGRAPH_EXTRA_MS))
            }
        }

        val scaled = out.map { it.copy(pauseAfterMs = (it.pauseAfterMs * pauseScale).roundToInt()) }
        return scaled.mapIndexed { i, s -> if (i == scaled.lastIndex) s.copy(pauseAfterMs = 0) else s }
    }

    /** Splits a paragraph after sentence-ending punctuation that is followed by whitespace. */
    private fun splitSentences(p: String): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()
        val sb = StringBuilder()
        var i = 0
        while (i < p.length) {
            val c = p[i]
            sb.append(c)
            if (c in enders) {
                while (i + 1 < p.length && (p[i + 1] in enders || p[i + 1] in closers)) {
                    i++
                    sb.append(p[i])
                }
                val next = if (i + 1 < p.length) p[i + 1] else ' '
                if (next.isWhitespace()) {
                    result.add(sb.toString().trim() to pauseFor(c))
                    sb.setLength(0)
                }
            }
            i++
        }
        if (sb.isNotBlank()) result.add(sb.toString().trim() to pauseFor('.'))
        return result
    }

    /** Breaks an over-long sentence at commas, then at spaces. */
    private fun splitLong(s: String, maxLen: Int): List<String> {
        if (s.length <= maxLen) return listOf(s)
        val parts = ArrayList<String>()
        var rest = s
        while (rest.length > maxLen) {
            var cut = rest.lastIndexOfAny(charArrayOf('،', ',', ':'), maxLen - 1)
            if (cut < maxLen / 3) cut = rest.lastIndexOf(' ', maxLen - 1)
            if (cut <= 0) cut = maxLen - 1
            parts.add(rest.substring(0, cut + 1).trim())
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts
    }

    /** Groups text into chunks of roughly [max] characters, cut after sentence punctuation. */
    fun chunkForRemote(text: String, max: Int): List<String> {
        val pieces = text.split(Regex("(?<=[.!?؟؛\\n])"))
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (piece in pieces) {
            if (current.isNotEmpty() && current.length + piece.length > max) {
                chunks.add(current.toString())
                current.setLength(0)
            }
            current.append(piece)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
