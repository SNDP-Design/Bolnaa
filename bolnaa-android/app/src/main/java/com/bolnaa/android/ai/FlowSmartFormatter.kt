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
STRICT LANGUAGE & ALPHABET RULES:
1. Understand ONLY Hindi, English, and Hinglish. Reject or ignore any other foreign languages.
2. If the spoken text is in Hindi or Hinglish, ALWAYS write it in English letters / Roman alphabet (Hinglish).
   - Example: If the speech is "क्या कर रहे हो", output "Kya kar rahe ho".
   - Example: If the speech is "आज बहुत काम है भाई", output "Aaj bahut kaam hai bhai".
   - Example: If the speech is "Main theek hoon, aap batao", output "Main theek hoon, aap batao".
3. NEVER output any non-English/non-Latin scripts. NEVER output Arabic, Urdu, Japanese, Korean, Chinese, Russian, or Devanagari characters.
4. If the raw transcript contains silence hallucinations in foreign languages (like Japanese, Korean, Arabic, Urdu subtitle artifacts), REMOVE THEM COMPLETELY and output nothing.
5. The final output must strictly consist ONLY of standard English letters (A-Z, a-z), numbers, and standard punctuation.
6. Output ONLY the formatted text with no introductory phrases, quotes, or meta commentary.
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
        val temperature: Float = 0.0f,
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
        val sanitized = ScriptSanitizer.sanitizeToEnglishLettersOnly(rawText)
        if (sanitized.isBlank()) return@withContext ""

        val groqKey = groqKeyProvider()
        val openAiKey = openAiKeyProvider()

        if (tone == FlowTone.VERBATIM) {
            val cleaned = basicRuleClean(sanitized)
            return@withContext ScriptSanitizer.sanitizeToEnglishLettersOnly(cleaned)
        }

        // 1. Try Groq (Ultra-fast Llama-3.1-8b ~150ms)
        if (groqKey.isNotBlank()) {
            val groqResult = callChatApi(
                url = GROQ_CHAT_URL,
                apiKey = groqKey,
                model = "llama-3.1-8b-instant",
                rawText = sanitized,
                systemInstruction = "${tone.systemPrompt}\n\n$HINGLISH_SCRIPT_RULE",
                customVocabulary = customVocabulary
            )
            if (groqResult != null) return@withContext ScriptSanitizer.sanitizeToEnglishLettersOnly(groqResult)
        }

        // 2. Try OpenAI (gpt-4o-mini)
        if (openAiKey.isNotBlank()) {
            val openAiResult = callChatApi(
                url = OPENAI_CHAT_URL,
                apiKey = openAiKey,
                model = "gpt-4o-mini",
                rawText = sanitized,
                systemInstruction = "${tone.systemPrompt}\n\n$HINGLISH_SCRIPT_RULE",
                customVocabulary = customVocabulary
            )
            if (openAiResult != null) return@withContext ScriptSanitizer.sanitizeToEnglishLettersOnly(openAiResult)
        }

        // 3. Fallback to smart local rule-based cleanup + transliteration
        return@withContext ScriptSanitizer.sanitizeToEnglishLettersOnly(localRuleBasedClean(sanitized))
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
                ),
                temperature = 0.0f
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
                        // Sanitize to English letters only
                        clean = ScriptSanitizer.sanitizeToEnglishLettersOnly(clean)
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
        var cleaned = ScriptSanitizer.sanitizeToEnglishLettersOnly(text)

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
        var cleaned = ScriptSanitizer.sanitizeToEnglishLettersOnly(text)
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return cleaned
    }
}
