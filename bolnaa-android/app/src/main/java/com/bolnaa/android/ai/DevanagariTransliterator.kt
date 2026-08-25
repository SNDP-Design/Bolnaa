package com.bolnaa.android.ai

/**
 * High-accuracy Devanagari to Roman script (Hinglish / English letters) Transliterator.
 * Converts Hindi spoken transcripts written in Devanagari script into natural English letters (Hinglish).
 * E.g. "क्या कर रहे हो" -> "Kya kar rahe ho"
 *      "नमस्ते आप कैसे हैं" -> "Namaste aap kaise hain"
 */
object DevanagariTransliterator {

    private val DEVANAGARI_REGEX = Regex("[\\u0900-\\u097F]")

    fun hasDevanagari(text: String): Boolean {
        return DEVANAGARI_REGEX.containsMatchIn(text)
    }

    private val COMMON_WORD_MAP = mapOf(
        "क्या" to "kya",
        "है" to "hai",
        "हैं" to "hain",
        "हूँ" to "hoon",
        "हूं" to "hoon",
        "हो" to "ho",
        "था" to "tha",
        "थी" to "thi",
        "थे" to "the",
        "आप" to "aap",
        "तुम" to "tum",
        "तू" to "tu",
        "हम" to "hum",
        "मैं" to "main",
        "मुझे" to "mujhe",
        "मुझको" to "mujhko",
        "मुझसे" to "mujhse",
        "मेरा" to "mera",
        "मेरी" to "meri",
        "मेरे" to "mere",
        "तुम्हारा" to "tumhara",
        "तुम्हारी" to "tumhari",
        "तुम्हारे" to "tumhare",
        "आपका" to "aapka",
        "आपकी" to "aapki",
        "आपके" to "aapke",
        "उसका" to "uska",
        "उसकी" to "uski",
        "उसके" to "uske",
        "उनका" to "unka",
        "उनकी" to "unki",
        "उनके" to "unke",
        "यह" to "yeh",
        "ये" to "ye",
        "वह" to "woh",
        "वो" to "wo",
        "कैसे" to "kaise",
        "कैसा" to "kaisa",
        "कैसी" to "kaisi",
        "क्यों" to "kyun",
        "कहाँ" to "kahan",
        "कहा" to "kaha",
        "यहाँ" to "yahan",
        "वहाँ" to "wahan",
        "कब" to "kab",
        "अब" to "ab",
        "जब" to "jab",
        "तब" to "tab",
        "सब" to "sab",
        "कुछ" to "kuch",
        "कोई" to "koi",
        "और" to "aur",
        "लेकिन" to "lekin",
        "मगर" to "magar",
        "क्योंकि" to "kyunki",
        "इसलिए" to "isliye",
        "कर" to "kar",
        "करना" to "karna",
        "करो" to "karo",
        "करते" to "karte",
        "करता" to "karta",
        "करती" to "karti",
        "करेंगे" to "karenge",
        "करूँगा" to "karoonga",
        "करूंगा" to "karoonga",
        "रहा" to "raha",
        "रही" to "rahi",
        "रहे" to "rahe",
        "नमस्ते" to "namaste",
        "धन्यवाद" to "dhanyawad",
        "शुक्रिया" to "shukriya",
        "भाई" to "bhai",
        "दोस्त" to "dost",
        "अच्छा" to "achha",
        "अच्छी" to "achhi",
        "अच्छे" to "achhe",
        "बहुत" to "bahut",
        "काम" to "kaam",
        "दिन" to "din",
        "रात" to "raat",
        "कल" to "kal",
        "आज" to "aaj",
        "घर" to "ghar",
        "बात" to "baat",
        "बातें" to "baatein",
        "ठीक" to "theek",
        "जाओ" to "jao",
        "आओ" to "aao",
        "सुनो" to "suno",
        "बोलो" to "bolo",
        "बताओ" to "batao",
        "सकते" to "sakte",
        "सकता" to "sakta",
        "सकती" to "sakti",
        "चाहिए" to "chahiye",
        "नहीं" to "nahi",
        "ना" to "na",
        "हाँ" to "haan",
        "हां" to "haan",
        "ज़्यादा" to "zyada",
        "ज्यादा" to "zyada",
        "थोड़ा" to "thoda",
        "थोडा" to "thoda",
        "कम" to "kam",
        "वाला" to "wala",
        "वाली" to "wali",
        "वाले" to "wale"
    )

    private val VOWELS = mapOf(
        "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo", "ऋ" to "ri",
        "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au", "ऍ" to "e", "ऑ" to "o"
    )

    private val MATRAS = mapOf(
        "ा" to "a", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo", "ृ" to "ri",
        "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au", "ॅ" to "e", "ॉ" to "o"
    )

    private val CONSONANTS = mapOf(
        "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng",
        "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny",
        "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n",
        "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n",
        "प" to "p", "फ" to "ph", "ब" to "b", "भ" to "bh", "म" to "m",
        "य" to "y", "र" to "r", "ल" to "l", "व" to "v", "श" to "sh", "ष" to "sh", "स" to "s", "ह" to "h",
        "क़" to "q", "ख़" to "kh", "ग़" to "gh", "ज़" to "z", "ड़" to "r", "ढ़" to "rh", "फ़" to "f"
    )

    private val SPECIAL = mapOf(
        "ं" to "n", "ँ" to "n", "ः" to "h", "।" to ".", "॥" to "."
    )

    fun transliterate(text: String): String {
        if (!hasDevanagari(text)) return text

        val words = text.split(" ")
        val resultWords = ArrayList<String>(words.size)

        for (rawWord in words) {
            if (!hasDevanagari(rawWord)) {
                resultWords.add(rawWord)
                continue
            }

            // Extract punctuation prefix/suffix
            val cleanWord = rawWord.trim { it in ",.!?:;\"'()[]{}।॥" }
            val prefix = rawWord.takeWhile { it in ",.!?:;\"'()[]{}" }
            val suffix = rawWord.takeLastWhile { it in ",.!?:;\"'()[]{}" }

            val mapped = COMMON_WORD_MAP[cleanWord]
            if (mapped != null) {
                resultWords.add(prefix + mapped + suffix)
                continue
            }

            val sb = StringBuilder()
            val n = cleanWord.length
            var i = 0
            while (i < n) {
                // Check 2-char nukta combinations first
                if (i + 1 < n) {
                    val twoChar = cleanWord.substring(i, i + 2)
                    if (CONSONANTS.containsKey(twoChar)) {
                        val cons = CONSONANTS[twoChar]!!
                        i += 2
                        handleConsonantFollowing(cleanWord, i, cons, sb)
                        if (i < n && (cleanWord[i] == '्' || MATRAS.containsKey(cleanWord.substring(i, i + 1)))) {
                            i++
                        }
                        continue
                    }
                }

                val oneChar = cleanWord.substring(i, i + 1)
                if (VOWELS.containsKey(oneChar)) {
                    sb.append(VOWELS[oneChar])
                    i++
                } else if (CONSONANTS.containsKey(oneChar)) {
                    val cons = CONSONANTS[oneChar]!!
                    i++
                    handleConsonantFollowing(cleanWord, i, cons, sb)
                    if (i < n && (cleanWord[i] == '्' || MATRAS.containsKey(cleanWord.substring(i, i + 1)))) {
                        i++
                    }
                } else if (MATRAS.containsKey(oneChar)) {
                    sb.append(MATRAS[oneChar])
                    i++
                } else if (SPECIAL.containsKey(oneChar)) {
                    sb.append(SPECIAL[oneChar])
                    i++
                } else if (oneChar == "्") {
                    i++
                } else {
                    sb.append(oneChar)
                    i++
                }
            }

            val transliterated = sb.toString()
            resultWords.add(prefix + transliterated + suffix)
        }

        var result = resultWords.joinToString(" ")
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return result
    }

    private fun handleConsonantFollowing(word: String, nextIndex: Int, cons: String, sb: StringBuilder) {
        val n = word.length
        if (nextIndex < n) {
            val nextOneChar = word.substring(nextIndex, nextIndex + 1)
            val nextCh = word[nextIndex]
            if (nextCh == '्') {
                // Halant -> no inherent 'a'
                sb.append(cons)
            } else if (MATRAS.containsKey(nextOneChar)) {
                val matraVal = if (nextOneChar == "ा") "a" else (MATRAS[nextOneChar] ?: "")
                sb.append(cons).append(matraVal)
            } else if (CONSONANTS.containsKey(nextOneChar) || VOWELS.containsKey(nextOneChar) || SPECIAL.containsKey(nextOneChar)) {
                sb.append(cons).append("a")
            } else {
                sb.append(cons)
            }
        } else {
            // End of word -> Schwa deletion in Hindi (no trailing 'a')
            sb.append(cons)
        }
    }
}
