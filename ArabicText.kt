package org.nationalsounds.app.text

/** Small helpers for working with Arabic text and diacritics (tashkeel). */
object ArabicText {

    // Fathatan .. Sukun, extended Arabic marks and the dagger alef.
    private val diacriticsRegex = Regex("[\\u064B-\\u065F\\u0670]")

    const val FATHA = "َ"
    const val DAMMA = "ُ"
    const val KASRA = "ِ"
    const val SUKUN = "ْ"
    const val SHADDA = "ّ"
    const val FATHATAN = "ً"
    const val DAMMATAN = "ٌ"
    const val KASRATAN = "ٍ"

    /** The marks offered by the on-screen diacritics row: mark to display label. */
    val keypad: List<Pair<String, String>> = listOf(
        FATHA to "فتحة",
        DAMMA to "ضمة",
        KASRA to "كسرة",
        SUKUN to "سكون",
        SHADDA to "شدّة",
        FATHATAN to "تنوين فتح",
        DAMMATAN to "تنوين ضم",
        KASRATAN to "تنوين كسر",
    )

    fun stripDiacritics(text: String): String = diacriticsRegex.replace(text, "")

    fun hasDiacritics(text: String): Boolean = diacriticsRegex.containsMatchIn(text)

    fun isArabicLetter(c: Char): Boolean = c in 'ء'..'ي' || c in 'ٱ'..'ۓ'

    fun isLatinLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
}
