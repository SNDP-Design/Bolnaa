package com.wisprflow.android.ai

import android.util.Log
import com.wisprflow.android.data.models.FlowTone
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

        if (tone == FlowTone.VERBATIM) {
            return@withContext basicRuleClean(rawText)
        }

        val groqKey = groqKeyProvider()
        val openAiKey = openAiKeyProvider()

        // 1. Try Groq (Ultra-fast Llama-3.1-8b ~150ms)
        if (groqKey.isNotBlank()) {
            val groqResult = callChatApi(
                url = GROQ_CHAT_URL,
                apiKey = groqKey,
                model = "llama-3.1-8b-instant",
                rawText = rawText,
                tone = tone,
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
                tone = tone,
                customVocabulary = customVocabulary
            )
            if (openAiResult != null) return@withContext openAiResult
        }

        // 3. Fallback to smart local rule-based cleanup
        return@withContext localRuleBasedClean(rawText)
    }

    private fun callChatApi(
        url: String,
        apiKey: String,
        model: String,
        rawText: String,
        tone: FlowTone,
        customVocabulary: String
    ): String? {
        try {
            var systemPrompt = tone.systemPrompt
            if (customVocabulary.isNotBlank()) {
                systemPrompt += "\nCustom vocabulary to prioritize: $customVocabulary"
            }

            val requestBodyObj = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
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

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<ChatResponse>(body)
                    val result = parsed.choices.firstOrNull()?.message?.content?.trim()
                    if (!result.isNullOrBlank()) {
                        // Strip any outer wrapping quotes if the model wrapped it
                        return result.removeSurrounding("\"").removeSurrounding("'")
                    }
                } else {
                    Log.w(TAG, "Chat API failed: ${response.code} $body")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error invoking Chat API formatter", e)
        }
        return null
    }

    /**
     * Local offline rule-based cleanup when no API key or network is available.
     * Removes filler words like 'um', 'uh', 'you know', 'er', 'ah', duplicates, etc.
     */
    private fun localRuleBasedClean(text: String): String {
        var cleaned = text

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
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return cleaned
    }
}
