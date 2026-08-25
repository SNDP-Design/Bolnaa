package com.bolnaa.android.ai

import android.util.Log
import com.bolnaa.android.data.models.FlowTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class FlowSmartFormatter(
    private val groqKeyProvider: () -> String,
    private val openAiKeyProvider: () -> String
) {

    companion object {
        private const val TAG = "FlowSmartFormatter"
        private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions"

        private const val HINGLISH_SCRIPT_RULE = """
CRITICAL SCRIPT & LANGUAGE RULE:
1. If the spoken text is in Hindi or Hinglish, ALWAYS write/format it in English letters / Roman alphabet (Hinglish).
   - Example: If the speech is "क्या कर रहे हो", output "Kya kar rahe ho".
   - Example: If the speech is "आज बहुत काम है भाई", output "Aaj bahut kaam hai bhai".
   - Example: If the speech is "Main theek hoon, aap batao", output "Main theek hoon, aap batao".
2. NEVER output Devanagari script (e.g. do NOT output 'क्या', 'है', 'कर' in Devanagari). ALWAYS write in English alphabet (Hinglish).
3. Pure English words and sentences must remain in natural, polished English.
4. Output ONLY the formatted text with no introductory phrases, quotes, or meta commentary.
"""
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float = 0.2f,
        val max_tokens: Int = 1000
    )

    @Serializable
    private data class ChatChoice(val message: ChatMessage)

    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

    suspend fun formatTranscription(
        rawText: String,
        tone: FlowTone,
        customVocabulary: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext rawText

        val groqKey = groqKeyProvider()
        val openAiKey = openAiKeyProvider()

        if (tone == FlowTone.VERBATIM) {
            val cleaned = basicRuleClean(rawText)
            if (DevanagariTransliterator.hasDevanagari(cleaned)) {
                // If LLM is available, use fast Llama-3.1-8b for verbatim transliteration
                if (groqKey.isNotBlank()) {
                    val verbatimPrompt = "Transcribe the following speech word-for-word into English letters / Roman script (Hinglish). Do NOT add or remove words. NEVER output Devanagari script. Output ONLY the transliterated text."
                    val result = callChatApi(GROQ_CHAT_URL, groqKey, "llama-3.1-8b-instant", cleaned, verbatimPrompt, customVocabulary)
                    if (result != null) return@withContext result
                } else if (openAiKey.isNotBlank()) {
                    val verbatimPrompt = "Transcribe the following speech word-for-word into English letters / Roman script (Hinglish). Do NOT add or remove words. NEVER output Devanagari script. Output ONLY the transliterated text."
                    val result = callChatApi(OPENAI_CHAT_URL, openAiKey, "gpt-4o-mini", cleaned, verbatimPrompt, customVocabulary)
                    if (result != null) return@withContext result
                }
                // Fallback to offline rule-based transliterator
                return@withContext DevanagariTransliterator.transliterate(cleaned)
            }
            return@withContext cleaned
        }

        // 1. Try Groq (Ultra-fast Llama-3.1-8b ~150ms)
        if (groqKey.isNotBlank()) {
            val groqResult = callChatApi(
                url = GROQ_CHAT_URL,
                apiKey = groqKey,
                model = "llama-3.1-8b-instant",
                rawText = rawText,
                systemInstruction = "${tone.systemPrompt}\n\n$HINGLISH_SCRIPT_RULE",
                customVocabulary = customVocabulary
            )
            if (groqResult != null) return@withContext groqResult
        }

        // 2. Try OpenAI (gpt-4o-mini)
        if (openAiKey.isNotBlank()) {
            val openAiResult = callChatApi(
                url = OPENAI_CHAT_URL,
                apiKey = openAiKey,
                model = "gpt-4o-mini",
                rawText = rawText,
                systemInstruction = "${tone.systemPrompt}\n\n$HINGLISH_SCRIPT_RULE",
                customVocabulary = customVocabulary
            )
            if (openAiResult != null) return@withContext openAiResult
        }

        // 3. Fallback to smart local rule-based cleanup + transliteration
        return@withContext localRuleBasedClean(rawText)
    }

    private fun callChatApi(
        url: String,
        apiKey: String,
        model: String,
        rawText: String,
        systemInstruction: String,
        customVocabulary: String
    ): String? {
        try {
            var fullSystemPrompt = systemInstruction
            if (customVocabulary.isNotBlank()) {
                fullSystemPrompt += "\nCustom vocabulary to prioritize: $customVocabulary"
            }

            val requestBodyObj = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = fullSystemPrompt),
                    ChatMessage(role = "user", content = rawText)
                )
            )

            val requestJson = json.encodeToString(ChatRequest.serializer(), requestBodyObj)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            var apiResult: String? = null
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<ChatResponse>(body)
                    val result = parsed.choices.firstOrNull()?.message?.content?.trim()
                    if (!result.isNullOrBlank()) {
                        // Strip outer quotes if any
                        var clean = result.removeSurrounding("\"").removeSurrounding("'")
                        // If any Devanagari remains, transliterate to English letters
                        if (DevanagariTransliterator.hasDevanagari(clean)) {
                            clean = DevanagariTransliterator.transliterate(clean)
                        }
                        apiResult = clean
                    } else {
                        apiResult = null
                    }
                } else {
                    Log.w(TAG, "Chat API failed: ${response.code} $body")
                }
                Unit
            }
            if (apiResult != null) return apiResult
        } catch (e: Exception) {
            Log.w(TAG, "Error invoking Chat API formatter", e)
        }
        return null
    }

    /**
     * Local offline rule-based cleanup when no API key or network is available.
     * Transliterates any Devanagari to English letters, and removes filler words.
     */
    private fun localRuleBasedClean(text: String): String {
        // First, transliterate any Devanagari to English letters
        var cleaned = DevanagariTransliterator.transliterate(text)

        // Common speech fillers regex
        val fillerPatterns = listOf(
            "\\b(um|uh|err|er|ah|like,|you know,|basically,|so basically)\\b",
            "\\b(um+|uh+|umm+)\\b"
        )

        for (pattern in fillerPatterns) {
            cleaned = cleaned.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }

        // Deduplicate repeated consecutive words (e.g. "the the" -> "the")
        cleaned = cleaned.replace(Regex("\\b(\\w+)\\s+\\1\\b", RegexOption.IGNORE_CASE), "$1")

        // Clean extra spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // Capitalize first letter
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // Add trailing period if missing and does not end with punctuation
        if (cleaned.isNotEmpty() && !cleaned.endsWith(".") && !cleaned.endsWith("?") && !cleaned.endsWith("!")) {
            cleaned += "."
        }

        return cleaned
    }

    private fun basicRuleClean(text: String): String {
        var cleaned = text.trim()
        if (DevanagariTransliterator.hasDevanagari(cleaned)) {
            cleaned = DevanagariTransliterator.transliterate(cleaned)
        }
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return cleaned
    }
}
