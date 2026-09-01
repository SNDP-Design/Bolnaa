package com.bolnaa.android.ai

import android.content.Context
import android.util.Log
import com.bolnaa.android.data.PreferencesManager
import com.bolnaa.android.data.models.FlowTone
import com.bolnaa.android.data.models.SttEngine
import kotlinx.coroutines.flow.first
import java.io.File

class FlowTranscriptionEngine(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "FlowTranscriptionEngine"
        private const val HINGLISH_WHISPER_PROMPT =
            "Voice dictation in English and Hinglish (English letters only). E.g. Hello, Namaste, kya haal hai, main theek hoon, let's meet tomorrow."
    }

    val groqClient = GroqWhisperClient()
    val localClient = LocalSpeechClient(context)
    val smartFormatter = FlowSmartFormatter(
        groqKeyProvider = { "" },
        openAiKeyProvider = { "" }
    )

    suspend fun processAudioFile(
        audioFile: File,
        prompt: String = ""
    ): Result<String> {
        val preferredEngine = preferencesManager.sttEngine.first()
        val tone = preferencesManager.flowTone.first()
        val isAiCleanupEnabled = preferencesManager.isAiCleanupEnabled.first()
        val customVocab = preferencesManager.customVocabulary.first()

        Log.d(TAG, "Processing audio file (${audioFile.length()} bytes) with engine: $preferredEngine")

        val effectivePrompt = if (prompt.isNotBlank()) {
            "$prompt. Write exclusively in English letters (English and Hinglish)."
        } else {
            HINGLISH_WHISPER_PROMPT
        }

        // 1. Perform Speech-to-Text
        val rawTranscriptionResult: Result<String> = when (preferredEngine) {
            SttEngine.GROQ -> {
                groqClient.transcribeAudio(audioFile, effectivePrompt)
            }
            SttEngine.OPENAI -> {
                groqClient.transcribeAudio(audioFile, effectivePrompt)
            }
            SttEngine.LOCAL -> {
                Result.failure(IllegalStateException("Local SpeechRecognizer requires direct microphone stream."))
            }
        }

        // Clean up temporary audio file
        try {
            audioFile.delete()
        } catch (e: Exception) {
            // Ignore
        }

        if (rawTranscriptionResult.isFailure) {
            return rawTranscriptionResult
        }

        val rawText = rawTranscriptionResult.getOrNull() ?: ""
        if (rawText.isBlank()) {
            return Result.success("")
        }

        // Sanitize raw text before sending to LLM (filters foreign hallucinations & transliterates Hindi)
        val sanitizedRaw = ScriptSanitizer.sanitizeToEnglishLettersOnly(rawText)
        if (sanitizedRaw.isBlank()) {
            return Result.success("")
        }

        // 2. Perform Bolnaa Smart AI Formatting
        val formattedText = if (isAiCleanupEnabled) {
            smartFormatter.formatTranscription(
                rawText = sanitizedRaw,
                tone = tone,
                customVocabulary = customVocab
            )
        } else {
            sanitizedRaw
        }

        // Final safety guarantee: strictly English letters and punctuation
        val finalResult = ScriptSanitizer.sanitizeToEnglishLettersOnly(formattedText)

        return Result.success(finalResult)
    }

    suspend fun formatRawText(rawText: String): String {
        val tone = preferencesManager.flowTone.first()
        val customVocab = preferencesManager.customVocabulary.first()
        val formatted = smartFormatter.formatTranscription(rawText, tone, customVocab)
        return ScriptSanitizer.sanitizeToEnglishLettersOnly(formatted)
    }
}
