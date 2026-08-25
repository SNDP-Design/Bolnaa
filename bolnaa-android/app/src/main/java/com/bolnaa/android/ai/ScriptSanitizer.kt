package com.bolnaa.android.ai

/**
 * ScriptSanitizer enforces that all transcribed and formatted speech strictly uses
 * English letters (Latin alphabet), numbers, and standard punctuation.
 *
 * It filters out Whisper hallucinations in foreign languages (Japanese, Korean, Arabic, Urdu, Chinese, Russian, etc.)
 * and ensures Hindi speech is converted into Roman script (Hinglish).
 */
object ScriptSanitizer {

    // Regex ranges for non-Latin foreign scripts (excluding Devanagari which is handled by transliterator)
    private val ARABIC_URDU_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val CJK_JAPANESE_KOREAN_REGEX = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F]")
    private val CYRILLIC_REGEX = Regex("[\\u0400-\\u04FF]")
    private val HEBREW_REGEX = Regex("[\\u0590-\\u05FF]")
    private val THAI_REGEX = Regex("[\\u0E00-\\u0E7F]")

    // Common Whisper silence/noise hallucination phrases
    private val HALLUCINATION_PHRASES = listOf(
        "subtitles by",
        "subscribe to",
        "thank you for watching",
        "thanks for watching",
        "amara.org",
        "community member",
        "mbc",
        "english subtitles",
        "sous-titres",
        "subtitulado",
        "legendado por"
    )

    /**
     * Checks if the text consists primarily of foreign hallucinated scripts (Arabic, Japanese, Korean, etc.)
     */
    fun isForeignScriptHallucination(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // Check if text contains Arabic, Japanese, Korean, Cyrillic, Thai, Hebrew
        val foreignCharCount = trimmed.count { ch ->
            val s = ch.toString()
            ARABIC_URDU_REGEX.matches(s) ||
                    CJK_JAPANESE_KOREAN_REGEX.matches(s) ||
                    CYRILLIC_REGEX.matches(s) ||
                    HEBREW_REGEX.matches(s) ||
                    THAI_REGEX.matches(s)
        }

        // If more than 30% of characters are foreign non-Latin scripts, it's a hallucination
        if (foreignCharCount > 0 && (foreignCharCount.toFloat() / trimmed.length.toFloat()) > 0.25f) {
            return true
        }

        // Check against known hallucination phrases
        val lower = trimmed.lowercase()
        for (phrase in HALLUCINATION_PHRASES) {
            if (lower.contains(phrase) && trimmed.length < phrase.length + 20) {
                return true
            }
        }

        return false
    }

    /**
     * Sanitizes input:
     * 1. If it is a foreign script hallucination from silence, returns empty string.
     * 2. If it contains Devanagari Hindi, transliterates to English letters (Hinglish).
     * 3. Strips any remaining non-Latin characters, keeping only English letters, digits, and punctuation.
     */
    fun sanitizeToEnglishLettersOnly(text: String): String {
        if (text.isBlank()) return ""

        // Step 1: Discard foreign hallucinations
        if (isForeignScriptHallucination(text)) {
            return ""
        }

        // Step 2: Transliterate Devanagari Hindi to English letters (Hinglish)
        var result = if (DevanagariTransliterator.hasDevanagari(text)) {
            DevanagariTransliterator.transliterate(text)
        } else {
            text
        }

        // Step 3: Remove any residual foreign script characters (Arabic, Asian, Cyrillic, etc.)
        val sb = StringBuilder()
        for (ch in result) {
            val code = ch.code
            // Allow standard ASCII printable characters (32..126) and Latin Extended (for accented letters if any)
            if (code in 32..126 || code in 160..591 || ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(ch)
            }
        }

        var cleaned = sb.toString().trim()

        // Clean redundant whitespace
        cleaned = cleaned.replace(Regex("[ \\t]+"), " ")

        return cleaned
    }
}
